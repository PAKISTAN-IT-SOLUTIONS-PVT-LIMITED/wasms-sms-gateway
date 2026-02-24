package net.wasms.smsgateway.service.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized worker scheduler. Call scheduleAll() after successful device registration
 * to start all periodic background tasks. Call cancelAll() on device deregistration.
 */
@Singleton
class WorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scheduleAll() {
        HeartbeatWorker.schedule(context)
        SyncWorker.schedule(context)
        DeliveryReportWorker.schedule(context)
        DailyResetWorker.schedule(context)
    }

    fun cancelAll() {
        HeartbeatWorker.cancel(context)
        SyncWorker.cancel(context)
        DeliveryReportWorker.cancel(context)
        DailyResetWorker.cancel(context)
    }
}
