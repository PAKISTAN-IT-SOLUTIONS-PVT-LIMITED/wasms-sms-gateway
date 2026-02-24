package net.wasms.smsgateway.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import net.wasms.smsgateway.data.remote.websocket.ReverbWebSocketClient
import net.wasms.smsgateway.data.remote.websocket.WebSocketEvent
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Processes WebSocket events from the Reverb server and dispatches actions.
 *
 * This is the "glue" between the WebSocket connection and the rest of the app.
 * Events flow: Server -> WebSocket -> EventProcessor -> Repositories/Services
 *
 * Lifecycle: Started by AppLifecycleManager after WebSocket connects.
 * Stopped when WebSocket disconnects or app shuts down.
 */
@Singleton
class WebSocketEventProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketClient: ReverbWebSocketClient,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val smsMessageDao: SmsMessageDao
) {

    private var processingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start collecting WebSocket events and dispatching them.
     * Safe to call multiple times; cancels any existing job first.
     */
    fun start() {
        processingJob?.cancel()
        processingJob = scope.launch {
            Timber.i("WebSocketEventProcessor: Started")
            while (true) {
                try {
                    webSocketClient.observeEvents().collect { event ->
                        handleEvent(event)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WebSocketEventProcessor: Event collection failed, restarting")
                }
            }
        }
    }

    /**
     * Stop processing WebSocket events.
     */
    fun stop() {
        Timber.i("WebSocketEventProcessor: Stopped")
        processingJob?.cancel()
        processingJob = null
    }

    // -------------------------------------------------------------------------
    // Event dispatch
    // -------------------------------------------------------------------------

    private fun handleEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.NewMessages -> handleNewMessages(event)
            is WebSocketEvent.ConfigUpdated -> handleConfigUpdated()
            is WebSocketEvent.Command -> handleCommand(event)
            is WebSocketEvent.PauseSending -> handlePause(event)
            is WebSocketEvent.ResumeSending -> handleResume(event)
            is WebSocketEvent.MessagesCancelled -> handleMessagesCancelled(event)
            is WebSocketEvent.Error -> handleError(event)
            is WebSocketEvent.Pong -> { /* keepalive ack, nothing to do */ }
        }
    }

    // -------------------------------------------------------------------------
    // Individual event handlers
    // -------------------------------------------------------------------------

    private fun handleNewMessages(event: WebSocketEvent.NewMessages) {
        Timber.d("WebSocketEventProcessor: NewMessages (count=%d, batchId=%s)", event.count, event.batchId)
        scope.launch {
            try {
                val synced = messageRepository.syncPendingFromServer()
                Timber.d("WebSocketEventProcessor: Synced %d messages from server", synced)
            } catch (e: Exception) {
                Timber.e(e, "WebSocketEventProcessor: Failed to sync pending messages")
            }
        }
    }

    private fun handleConfigUpdated() {
        Timber.d("WebSocketEventProcessor: ConfigUpdated")
        scope.launch {
            try {
                deviceRepository.fetchConfig()
                Timber.d("WebSocketEventProcessor: Config refreshed")
            } catch (e: Exception) {
                Timber.e(e, "WebSocketEventProcessor: Failed to fetch config")
            }
        }
    }

    private fun handleCommand(event: WebSocketEvent.Command) {
        Timber.i("WebSocketEventProcessor: Command (id=%s, type=%s)", event.commandId, event.type)
        when (event.type) {
            "pause" -> SmsSenderService.sendCommand(context, SmsSenderService.ACTION_PAUSE)
            "resume" -> SmsSenderService.sendCommand(context, SmsSenderService.ACTION_RESUME)
            "update_config" -> scope.launch {
                try {
                    deviceRepository.fetchConfig()
                } catch (e: Exception) {
                    Timber.e(e, "WebSocketEventProcessor: Failed to fetch config for command %s", event.commandId)
                }
            }
            else -> Timber.w("WebSocketEventProcessor: Unknown command type: %s", event.type)
        }
    }

    private fun handlePause(event: WebSocketEvent.PauseSending) {
        Timber.i("WebSocketEventProcessor: PauseSending (reason=%s, duration=%ds)", event.reason, event.durationSeconds)
        SmsSenderService.sendCommand(context, SmsSenderService.ACTION_PAUSE)
    }

    private fun handleResume(event: WebSocketEvent.ResumeSending) {
        Timber.i("WebSocketEventProcessor: ResumeSending (reason=%s)", event.reason)
        SmsSenderService.sendCommand(context, SmsSenderService.ACTION_RESUME)
    }

    private fun handleMessagesCancelled(event: WebSocketEvent.MessagesCancelled) {
        Timber.i("WebSocketEventProcessor: MessagesCancelled (count=%d)", event.messageIds.size)
        scope.launch {
            val now = Clock.System.now()
            for (messageId in event.messageIds) {
                try {
                    smsMessageDao.updateState(messageId, SmsState.CANCELLED, null, now)
                    Timber.d("WebSocketEventProcessor: Cancelled message %s", messageId)
                } catch (e: Exception) {
                    Timber.e(e, "WebSocketEventProcessor: Failed to cancel message %s", messageId)
                }
            }
        }
    }

    private fun handleError(event: WebSocketEvent.Error) {
        Timber.e("WebSocketEventProcessor: Error (code=%s, recoverable=%b): %s", event.code, event.isRecoverable, event.message)
        if (!event.isRecoverable) {
            Timber.w("WebSocketEventProcessor: Non-recoverable error, manual intervention may be needed")
        }
    }
}
