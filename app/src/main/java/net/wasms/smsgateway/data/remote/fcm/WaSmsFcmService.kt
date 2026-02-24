package net.wasms.smsgateway.data.remote.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import net.wasms.smsgateway.service.AppLifecycleManager
import net.wasms.smsgateway.service.SmsSenderService
import timber.log.Timber
import javax.inject.Inject

/**
 * FCM Service — acts as the "wakeup signal" in the hybrid connection design (Agent 8).
 *
 * FCM does NOT carry message data. It only carries a wakeup signal.
 * On receiving the signal, we:
 * 1. Reconnect WebSocket if disconnected
 * 2. Trigger a message sync from server
 * 3. Ensure the foreground service is running
 */
@AndroidEntryPoint
class WaSmsFcmService : FirebaseMessagingService() {

    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var connectionRepository: ConnectionRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var appLifecycleManager: AppLifecycleManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        Timber.d("FCM token refreshed")
        serviceScope.launch {
            try {
                deviceRepository.updateFcmToken(token)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update FCM token on server")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Timber.d("FCM message received: %s", message.data)

        val action = message.data["action"] ?: return

        when (action) {
            ACTION_WAKEUP -> handleWakeup()
            ACTION_NEW_MESSAGES -> handleNewMessages()
            ACTION_CONFIG_UPDATE -> handleConfigUpdate()
            ACTION_COMMAND -> handleCommand(message.data)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            else -> Timber.w("Unknown FCM action: %s", action)
        }
    }

    /**
     * Primary wakeup handler. Ensures all subsystems are running, then syncs messages.
     * This is the most common FCM message — server sends it when:
     * - WebSocket is disconnected and new messages are queued
     * - Device hasn't been seen for > heartbeat interval
     *
     * Uses [AppLifecycleManager.ensureStarted] which is idempotent — if subsystems
     * are already running, this is a no-op. If the app was killed, it restarts everything.
     */
    private fun handleWakeup() {
        Timber.d("FCM wakeup received — ensuring subsystems and syncing")

        // Ensure WebSocket, service, workers are all running
        appLifecycleManager.ensureStarted()

        serviceScope.launch {
            // Sync pending messages regardless of WebSocket state
            try {
                val count = messageRepository.syncPendingFromServer()
                if (count > 0) {
                    Timber.d("Synced %d messages after FCM wakeup", count)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync messages after FCM wakeup")
            }
        }
    }

    private fun handleNewMessages() {
        Timber.d("FCM new-messages signal")
        serviceScope.launch {
            try {
                val count = messageRepository.syncPendingFromServer()
                if (count > 0) {
                    ensureServiceRunning()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync on new-messages FCM")
            }
        }
    }

    private fun handleConfigUpdate() {
        Timber.d("FCM config-update signal")
        serviceScope.launch {
            try {
                deviceRepository.fetchConfig()
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch config on FCM signal")
            }
        }
    }

    private fun handleCommand(data: Map<String, String>) {
        val commandId = data["command_id"] ?: return
        val commandType = data["command_type"] ?: return
        Timber.d("FCM command: %s (id=%s)", commandType, commandId)
        // Commands are processed by the foreground service or WorkManager
        ensureServiceRunning()
    }

    private fun handlePause() {
        Timber.d("FCM pause signal")
        SmsSenderService.sendCommand(this, SmsSenderService.ACTION_PAUSE)
    }

    private fun handleResume() {
        Timber.d("FCM resume signal")
        SmsSenderService.sendCommand(this, SmsSenderService.ACTION_RESUME)
    }

    private fun ensureServiceRunning() {
        appLifecycleManager.ensureStarted()
    }

    companion object {
        const val ACTION_WAKEUP = "wakeup"
        const val ACTION_NEW_MESSAGES = "new_messages"
        const val ACTION_CONFIG_UPDATE = "config_update"
        const val ACTION_COMMAND = "command"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
    }
}
