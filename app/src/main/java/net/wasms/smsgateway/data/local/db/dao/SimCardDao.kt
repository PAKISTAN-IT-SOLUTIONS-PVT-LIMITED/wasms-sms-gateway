package net.wasms.smsgateway.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.model.SimCardEntity

@Dao
interface SimCardDao {

    @Query("SELECT * FROM sim_cards ORDER BY slot ASC")
    fun observeAll(): Flow<List<SimCardEntity>>

    @Query("SELECT * FROM sim_cards WHERE is_active = 1 ORDER BY slot ASC")
    fun observeActive(): Flow<List<SimCardEntity>>

    @Query("SELECT * FROM sim_cards ORDER BY slot ASC")
    suspend fun getAll(): List<SimCardEntity>

    @Query("SELECT * FROM sim_cards WHERE slot = :slot")
    suspend fun getBySlot(slot: Int): SimCardEntity?

    /**
     * Get the next available SIM card for sending.
     * A SIM is available if it is active AND its cooldown period has expired.
     * Ordered by totalSent ASC to distribute load evenly across SIMs (round-robin by usage).
     */
    @Query(
        """
        SELECT * FROM sim_cards
        WHERE is_active = 1
            AND (cooldown_until IS NULL OR cooldown_until < :now)
        ORDER BY total_sent ASC
        LIMIT 1
        """
    )
    suspend fun getNextAvailable(now: Instant): SimCardEntity?

    @Upsert
    suspend fun upsert(simCard: SimCardEntity)

    @Upsert
    suspend fun upsertAll(simCards: List<SimCardEntity>)

    @Query("UPDATE sim_cards SET total_sent = :totalSent, total_failed = :totalFailed WHERE id = :id")
    suspend fun updateStats(id: String, totalSent: Int, totalFailed: Int)

    @Query("UPDATE sim_cards SET cooldown_until = :cooldownUntil WHERE id = :id")
    suspend fun setCooldown(id: String, cooldownUntil: Instant)

    @Query("UPDATE sim_cards SET total_sent = 0, total_failed = 0")
    suspend fun resetDailyCounts()
}
