package net.wasms.smsgateway.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.wasms.smsgateway.data.local.preferences.TokenManager
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import net.wasms.smsgateway.service.worker.WorkerScheduler
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the app's startup sequence after device registration.
 *
 * Entry points that call [ensureStarted]:
 * - HomeViewModel on first load
 * - BootReceiver after device reboot
 * - FCM wakeup when server detects device offline
 *
 * Startup sequence:
 * 1. Verify authentication (token exists)
 * 2. Detect and sync SIM cards
 * 3. Connect WebSocket for real-time events
 * 4. Start WebSocketEventProcessor to dispatch events
 * 5. Sync pending messages from server
 * 6. Start foreground SmsSenderService
 * 7. Schedule periodic background workers
 *
 * Idempotent: safe to call from multiple entry points simultaneously.
 * The [AtomicBoolean] guard ensures the startup sequence runs exactly once.
 */
@Singleton
class AppLifecycleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: TokenManager,
    private val connectionRepository: ConnectionRepository,
    private val deviceRepository: DeviceRepository,
    private val messageRepository: MessageRepository,
    private val simDetector: SimDetector,
    private val webSocketEventProcessor: WebSocketEventProcessor,
    private val workerScheduler: WorkerScheduler,
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Ensures all subsystems are started. Idempotent — calling multiple times
     * from different entry points is safe and expected.
     *
     * Returns immediately if already started or if the device is not authenticated.
     */
    fun ensureStarted() {
        if (!tokenManager.isAuthenticated()) {
            Timber.d("AppLifecycleManager: Not authenticated, skipping startup")
            return
        }

        if (!started.compareAndSet(false, true)) {
            Timber.d("AppLifecycleManager: Already started, skipping")
            return
        }

        Timber.i("AppLifecycleManager: Starting subsystems")
        scope.launch { runStartupSequence() }
    }

    private suspend fun runStartupSequence() {
        // 1. Detect SIM cards and sync with server
        try {
            val sims = simDetector.detectAndSync()
            Timber.i("AppLifecycleManager: Detected %d SIM card(s)", sims.size)
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: SIM detection failed (non-fatal)")
        }

        // 2. Fetch latest config from server
        try {
            deviceRepository.fetchConfig()
            Timber.d("AppLifecycleManager: Config fetched")
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: Config fetch failed (non-fatal)")
        }

        // 3. Connect WebSocket
        try {
            connectionRepository.connect()
            Timber.i("AppLifecycleManager: WebSocket connected")
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: WebSocket connect failed (non-fatal, FCM fallback active)")
        }

        // 4. Start event processor to bridge WebSocket events → services
        webSocketEventProcessor.start()

        // 5. Sync pending messages from server
        try {
            val count = messageRepository.syncPendingFromServer()
            Timber.i("AppLifecycleManager: Synced %d pending message(s)", count)
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: Message sync failed (non-fatal)")
        }

        // 6. Start foreground SMS sender service
        try {
            SmsSenderService.start(context)
            Timber.i("AppLifecycleManager: SmsSenderService started")
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: Failed to start SmsSenderService")
        }

        // 7. Schedule periodic workers (heartbeat, sync, delivery reports, daily reset)
        try {
            workerScheduler.scheduleAll()
            Timber.i("AppLifecycleManager: Workers scheduled")
        } catch (e: Exception) {
            Timber.e(e, "AppLifecycleManager: Worker scheduling failed")
        }

        Timber.i("AppLifecycleManager: Startup sequence complete")
    }

    /**
     * Gracefully shuts down all subsystems. Called when the user logs out
     * or the device is deregistered.
     */
    fun shutdown() {
        Timber.i("AppLifecycleManager: Shutting down")

        webSocketEventProcessor.stop()

        scope.launch {
            try {
                connectionRepository.disconnect()
            } catch (e: Exception) {
                Timber.e(e, "AppLifecycleManager: WebSocket disconnect failed")
            }
        }

        SmsSenderService.sendCommand(context, SmsSenderService.ACTION_STOP)
        workerScheduler.cancelAll()

        started.set(false)
        Timber.i("AppLifecycleManager: Shutdown complete")
    }
}
