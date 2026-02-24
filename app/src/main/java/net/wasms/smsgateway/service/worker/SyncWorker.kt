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
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic sync worker — the HTTP polling safety net in the hybrid connection design.
 * Even when WebSocket is connected, this runs every 5 minutes to catch
 * any messages that might have been missed.
 *
 * Also reports pending delivery statuses back to server.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Pull any pending messages from server
            val synced = messageRepository.syncPendingFromServer()
            if (synced > 0) {
                Timber.d("Sync worker: pulled %d messages", synced)
            }

            // Push any unreported delivery statuses to server
            val reported = messageRepository.reportDeliveryStatuses()
            if (reported > 0) {
                Timber.d("Sync worker: reported %d delivery statuses", reported)
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Sync worker failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "wasms_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                5, TimeUnit.MINUTES
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
