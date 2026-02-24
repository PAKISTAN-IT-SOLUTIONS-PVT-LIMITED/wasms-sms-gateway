package net.wasms.smsgateway.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.data.local.model.SimCardEntity
import net.wasms.smsgateway.domain.model.SimCard
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Smart SIM rotation engine implementing the Agent 7 design.
 *
 * Manages SIM card selection for outgoing SMS using a round-robin strategy
 * with awareness of:
 * - Daily send limits (throttling)
 * - Cooldown periods between sends (carrier protection)
 * - Carrier-level load balancing
 * - Health score tracking (success/failure ratios)
 * - Automatic cooldown on consecutive failures
 *
 * All state is persisted in Room via [SimCardDao], so rotation state survives
 * app restarts.
 */
@Singleton
class SimRotationEngine @Inject constructor(
    private val simCardDao: SimCardDao
) {

    /**
     * Tracks the index of the last-used SIM for true round-robin rotation.
     * Atomic to handle concurrent access from the send loop.
     */
    private val lastUsedIndex = AtomicInteger(-1)

    /**
     * Tracks consecutive failure counts per SIM for automatic cooldown.
     * Key: SIM id, Value: consecutive failure count.
     */
    private val consecutiveFailures = mutableMapOf<String, Int>()
    private val failureLock = Any()

    /**
     * Get the next available SIM card for sending.
     *
     * Selection logic:
     * 1. Gets all active SIM cards from the database
     * 2. Filters out throttled SIMs (daily limit reached)
     * 3. Filters out SIMs in cooldown (cooldownUntil > now)
     * 4. Applies round-robin rotation among the remaining SIMs
     * 5. Prefers SIMs with lower send counts for load balancing
     *
     * @return The next SIM card to use, or null if no SIM is available.
     */
    suspend fun getNextSim(): SimCard? {
        val now = Clock.System.now()
        val allSims = simCardDao.getAll()

        if (allSims.isEmpty()) {
            Timber.w("SimRotationEngine: No SIM cards registered")
            return null
        }

        val available = allSims.filter { sim ->
            sim.isActive && !isDailyLimitReached(sim) && !isInCooldown(sim, now)
        }

        if (available.isEmpty()) {
            Timber.w(
                "SimRotationEngine: No SIMs available. Total=${allSims.size}, " +
                    "active=${allSims.count { it.isActive }}, " +
                    "throttled=${allSims.count { isDailyLimitReached(it) }}, " +
                    "cooling=${allSims.count { isInCooldown(it, now) }}"
            )
            return null
        }

        // Round-robin: pick the next SIM in rotation order
        val selected = selectRoundRobin(available)
        if (selected != null) {
            Timber.d(
                "SimRotationEngine: Selected SIM ${selected.id} " +
                    "(slot=${selected.slot}, carrier=${selected.carrierName}, " +
                    "sent=${selected.totalSent}/${selected.dailyLimit})"
            )
        }

        return selected?.toDomain()
    }

    /**
     * Apply a cooldown to a SIM card after sending.
     *
     * Prevents the same SIM from being used again for [seconds] duration.
     * This helps avoid carrier-level rate limiting and distributes sends
     * across multiple SIMs.
     *
     * @param simId The SIM card ID.
     * @param seconds The cooldown duration in seconds.
     */
    suspend fun applyCooldown(simId: String, seconds: Int) {
        if (seconds <= 0) return

        val cooldownUntil = Clock.System.now().plus(seconds.seconds)
        try {
            simCardDao.setCooldown(simId, cooldownUntil)
            Timber.d("SimRotationEngine: Applied ${seconds}s cooldown to SIM $simId")
        } catch (e: Exception) {
            Timber.e(e, "SimRotationEngine: Failed to apply cooldown to SIM $simId")
        }
    }

    /**
     * Report the result of a send attempt for a SIM card.
     *
     * Updates the SIM's send counts and health score. If multiple consecutive
     * failures are detected, applies an automatic cooldown to give the SIM
     * time to recover (carrier congestion, network issues, etc.).
     *
     * @param simId The SIM card ID.
     * @param success true if the SMS was dispatched successfully, false if it failed.
     */
    suspend fun reportSendResult(simId: String, success: Boolean) {
        try {
            val sim = simCardDao.getAll().find { it.id == simId }
            if (sim == null) {
                Timber.w("SimRotationEngine: SIM $simId not found for result reporting")
                return
            }

            val newTotalSent: Int
            val newTotalFailed: Int

            if (success) {
                newTotalSent = sim.totalSent + 1
                newTotalFailed = sim.totalFailed

                // Reset consecutive failures on success
                synchronized(failureLock) {
                    consecutiveFailures.remove(simId)
                }
            } else {
                newTotalSent = sim.totalSent
                newTotalFailed = sim.totalFailed + 1

                // Track consecutive failures
                val currentFailures = synchronized(failureLock) {
                    val count = (consecutiveFailures[simId] ?: 0) + 1
                    consecutiveFailures[simId] = count
                    count
                }

                // Apply automatic cooldown on consecutive failures
                if (currentFailures >= CONSECUTIVE_FAILURE_THRESHOLD) {
                    val cooldownSeconds = calculateFailureCooldown(currentFailures)
                    Timber.w(
                        "SimRotationEngine: SIM $simId has $currentFailures consecutive failures, " +
                            "applying ${cooldownSeconds}s cooldown"
                    )
                    applyCooldown(simId, cooldownSeconds)

                    // Reset counter after applying cooldown
                    synchronized(failureLock) {
                        consecutiveFailures[simId] = 0
                    }
                }
            }

            // Update stats in database
            simCardDao.updateStats(simId, newTotalSent, newTotalFailed)

            // Update health score
            val totalAttempts = newTotalSent + newTotalFailed
            if (totalAttempts > 0) {
                val healthScore = newTotalSent.toFloat() / totalAttempts
                // We update the full entity to persist health score
                val updatedSim = sim.copy(
                    totalSent = newTotalSent,
                    totalFailed = newTotalFailed,
                    healthScore = healthScore,
                    lastUsedAt = Clock.System.now()
                )
                simCardDao.upsert(updatedSim)
            }

        } catch (e: Exception) {
            Timber.e(e, "SimRotationEngine: Failed to report send result for SIM $simId")
        }
    }

    /**
     * Reset daily send/failure counts for all SIM cards.
     *
     * Should be called at midnight via WorkManager to start fresh counters
     * for each new day.
     */
    suspend fun resetDailyCounts() {
        try {
            simCardDao.resetDailyCounts()
            synchronized(failureLock) {
                consecutiveFailures.clear()
            }
            lastUsedIndex.set(-1)
            Timber.i("SimRotationEngine: Daily counts reset for all SIMs")
        } catch (e: Exception) {
            Timber.e(e, "SimRotationEngine: Failed to reset daily counts")
        }
    }

    /**
     * Observe SIM cards that are currently available for sending.
     *
     * Returns a Flow of SIM cards that are:
     * - Active
     * - Not throttled (daily limit not reached)
     * - Not in cooldown
     *
     * Useful for the UI to show which SIMs are ready.
     */
    fun observeAvailableSims(): Flow<List<SimCard>> {
        return simCardDao.observeActive().map { entities ->
            val now = Clock.System.now()
            entities
                .filter { !isDailyLimitReached(it) && !isInCooldown(it, now) }
                .map { it.toDomain() }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Check if a SIM's daily send limit has been reached.
     */
    private fun isDailyLimitReached(sim: SimCardEntity): Boolean {
        return sim.totalSent >= sim.dailyLimit
    }

    /**
     * Check if a SIM is currently in cooldown.
     */
    private fun isInCooldown(sim: SimCardEntity, now: Instant): Boolean {
        val cooldownUntil = sim.cooldownUntil ?: return false
        return now < cooldownUntil
    }

    /**
     * Select a SIM using round-robin from the available list.
     *
     * True round-robin: we track the last index and pick the next one.
     * If all SIMs have similar send counts, this distributes evenly.
     * If one SIM has significantly fewer sends, we prefer it (load balancing).
     */
    private fun selectRoundRobin(available: List<SimCardEntity>): SimCardEntity? {
        if (available.isEmpty()) return null
        if (available.size == 1) return available[0]

        // Check if there's a significant load imbalance
        val minSent = available.minOf { it.totalSent }
        val maxSent = available.maxOf { it.totalSent }

        // If load imbalance > threshold, prefer the least-used SIM
        if (maxSent - minSent > LOAD_IMBALANCE_THRESHOLD) {
            return available.minByOrNull { it.totalSent }
        }

        // Otherwise, strict round-robin
        val nextIndex = (lastUsedIndex.get() + 1) % available.size
        lastUsedIndex.set(nextIndex)
        return available[nextIndex]
    }

    /**
     * Calculate cooldown duration based on the number of consecutive failures.
     * Uses exponential backoff: 30s, 60s, 120s, 300s (max).
     */
    private fun calculateFailureCooldown(consecutiveFailureCount: Int): Int {
        val backoffSteps = listOf(30, 60, 120, 300)
        val index = (consecutiveFailureCount / CONSECUTIVE_FAILURE_THRESHOLD - 1)
            .coerceIn(0, backoffSteps.lastIndex)
        return backoffSteps[index]
    }

    companion object {
        /**
         * Number of consecutive failures before automatic cooldown is applied.
         */
        private const val CONSECUTIVE_FAILURE_THRESHOLD = 3

        /**
         * If one SIM has sent this many more messages than another,
         * prefer the less-used SIM over strict round-robin.
         */
        private const val LOAD_IMBALANCE_THRESHOLD = 5
    }
}
