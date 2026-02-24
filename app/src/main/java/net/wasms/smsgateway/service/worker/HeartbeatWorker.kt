package net.wasms.smsgateway.service.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.service.HealthReporter
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic heartbeat worker — sends device health to server every 60 seconds.
 * Uses WorkManager to survive process death and respect Doze mode.
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val healthReporter: HealthReporter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val health = healthReporter.collectHealth()
            deviceRepository.sendHeartbeat(health)
            Timber.d("Heartbeat sent: battery=%d%%, signal=%d, queue=%d",
                health.batteryLevel, health.signalStrength, health.queueDepth)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Heartbeat failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "wasms_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                1, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
