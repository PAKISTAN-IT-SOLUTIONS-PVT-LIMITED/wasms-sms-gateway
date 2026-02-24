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
 * Delivery report batch worker — pushes accumulated delivery reports to server.
 * Runs every 2 minutes. Supplements the real-time WebSocket reporting
 * by catching any reports generated while offline.
 */
@HiltWorker
class DeliveryReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val reported = messageRepository.reportDeliveryStatuses()
            if (reported > 0) {
                Timber.d("Reported %d delivery statuses to server", reported)
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Delivery report worker failed")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "wasms_delivery_reports"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DeliveryReportWorker>(
                2, TimeUnit.MINUTES
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
