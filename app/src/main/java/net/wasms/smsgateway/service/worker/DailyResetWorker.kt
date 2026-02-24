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
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Daily maintenance worker — runs once per day at midnight:
 * 1. Reset SIM daily send counters
 * 2. Clean up completed messages older than 7 days
 */
@HiltWorker
class DailyResetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val simCardDao: SimCardDao,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Reset SIM daily send/fail counters
            simCardDao.resetDailyCounts()
            Timber.d("Daily reset: SIM counters cleared")

            // Clean up old completed messages
            messageRepository.clearCompletedOlderThan(daysAgo = 7)
            Timber.d("Daily reset: old messages cleaned")

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Daily reset worker failed")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "wasms_daily_reset"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyResetWorker>(
                24, TimeUnit.HOURS
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
