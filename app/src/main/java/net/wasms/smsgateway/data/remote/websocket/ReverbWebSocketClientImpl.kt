package net.wasms.smsgateway.data.remote.websocket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.wasms.smsgateway.BuildConfig
import net.wasms.smsgateway.domain.model.ConnectionState
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [ReverbWebSocketClient] for Laravel Reverb.
 *
 * Reverb speaks the Pusher WebSocket protocol (protocol version 7). The message flow is:
 *
 * 1. Client opens wss:// connection
 * 2. Server sends `pusher:connection_established` with a `socket_id`
 * 3. Client sends `pusher:subscribe` for `private-device.{deviceId}`
 * 4. Server sends `pusher_internal:subscription_succeeded`
 * 5. Server broadcasts application events on the channel
 * 6. Client sends `pusher:ping` every 30s; server replies `pusher:pong`
 *
 * Reconnection uses exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s max,
 * with +/- 30% random jitter to prevent thundering herd after server restarts.
 */
@Singleton
class ReverbWebSocketClientImpl @Inject constructor(
    private val json: Json
) : ReverbWebSocketClient {

    companion object {
        private const val TAG = "ReverbWS"

        // Reconnection backoff parameters
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val MAX_BACKOFF_EXPONENT = 5  // 2^5 = 32, capped to 30s
        private const val JITTER_FACTOR = 0.3

        // Heartbeat interval (Pusher protocol ping)
        private const val PING_INTERVAL_MS = 30_000L

        // Pusher protocol event names
        private const val PUSHER_CONNECTION_ESTABLISHED = "pusher:connection_established"
        private const val PUSHER_SUBSCRIBE = "pusher:subscribe"
        private const val PUSHER_SUBSCRIPTION_SUCCEEDED = "pusher_internal:subscription_succeeded"
        private const val PUSHER_PING = "pusher:ping"
        private const val PUSHER_PONG = "pusher:pong"
        private const val PUSHER_ERROR = "pusher:error"

        // WaSMS application event names (broadcast from Laravel)
        private const val EVENT_NEW_MESSAGES = "new-messages"
        private const val EVENT_CONFIG_UPDATED = "config-updated"
        private const val EVENT_COMMAND = "command"
        private const val EVENT_PAUSE = "pause"
        private const val EVENT_RESUME = "resume"
        private const val EVENT_MESSAGES_CANCELLED = "messages-cancelled"

        // WebSocket close codes
        private const val CLOSE_NORMAL = 1000
        private const val CLOSE_AUTH_FAILED = 4001
        private const val CLOSE_RATE_LIMITED = 4429
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val eventChannel = Channel<WebSocketEvent>(Channel.BUFFERED)

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var socketId: String? = null

    private var currentToken: String? = null
    private var currentDeviceId: String? = null
    private var isManuallyDisconnected = false

    /**
     * OkHttp client configured for WebSocket use.
     * - No read timeout (WebSocket is a persistent connection)
     * - No OkHttp ping interval (we use Pusher protocol pings)
     * - No automatic retry (we manage reconnection ourselves)
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // =========================================================================
    // Public API (ReverbWebSocketClient interface)
    // =========================================================================

    override fun connect(token: String, deviceId: String) {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) {
            Timber.tag(TAG).d("Already connected or connecting, ignoring connect()")
            return
        }

        currentToken = token
        currentDeviceId = deviceId
        isManuallyDisconnected = false
        reconnectAttempt = 0

        doConnect()
    }

    override fun disconnect() {
        Timber.tag(TAG).d("Disconnecting (manual)")
        isManuallyDisconnected = true

        reconnectJob?.cancel()
        reconnectJob = null
        pingJob?.cancel()
        pingJob = null

        webSocket?.close(CLOSE_NORMAL, "Client disconnecting")
        webSocket = null
        socketId = null

        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun isConnected(): Boolean =
        _connectionState.value == ConnectionState.CONNECTED

    override fun observeEvents(): Flow<WebSocketEvent> =
        eventChannel.receiveAsFlow()

    override fun getSocketId(): String? = socketId

    // =========================================================================
    // Connection establishment
    // =========================================================================

    private fun doConnect() {
        _connectionState.value = ConnectionState.CONNECTING
        Timber.tag(TAG).d("Connecting to Reverb (attempt %d)...", reconnectAttempt)

        val wsUrl = buildString {
            append("wss://")
            append(BuildConfig.WS_HOST)
            append(BuildConfig.WS_PATH)
            append("?protocol=7&client=android&version=1.0.0&flash=false")
        }

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $currentToken")
            .build()

        webSocket = client.newWebSocket(request, createListener())
    }

    private fun createListener(): WebSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Timber.tag(TAG).d("TCP connection opened, waiting for Pusher handshake")
            // Do not mark CONNECTED yet -- wait for pusher:connection_established
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                handleMessage(text)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error handling message: %s", text.take(200))
                emitEvent(WebSocketEvent.Error(
                    message = "Message parse error: ${e.message}",
                    isRecoverable = true
                ))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Timber.tag(TAG).d("Server closing: code=%d reason=%s", code, reason)
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Timber.tag(TAG).d("Connection closed: code=%d reason=%s", code, reason)
            handleDisconnection(code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.tag(TAG).e(t, "Connection failure: %s", response?.message)
            handleDisconnection(
                code = -1,
                reason = t.message ?: "Connection failure"
            )
        }
    }

    // =========================================================================
    // Pusher protocol message handling
    // =========================================================================

    private fun handleMessage(text: String) {
        val messageJson = json.parseToJsonElement(text).jsonObject
        val event = messageJson["event"]?.jsonPrimitive?.content ?: return

        when (event) {
            PUSHER_CONNECTION_ESTABLISHED -> onConnectionEstablished(messageJson)
            PUSHER_SUBSCRIPTION_SUCCEEDED -> onSubscriptionSucceeded(messageJson)
            PUSHER_PONG                   -> onPong()
            PUSHER_ERROR                  -> onPusherError(messageJson)
            else                          -> onApplicationEvent(event, messageJson)
        }
    }

    /**
     * Reverb accepted our WebSocket connection. Extract socket_id and subscribe.
     *
     * Server message format:
     * ```json
     * {
     *   "event": "pusher:connection_established",
     *   "data": "{\"socket_id\":\"12345.67890\",\"activity_timeout\":30}"
     * }
     * ```
     */
    private fun onConnectionEstablished(message: JsonObject) {
        val dataString = message["data"]?.jsonPrimitive?.content ?: return
        val data = json.parseToJsonElement(dataString).jsonObject

        socketId = data["socket_id"]?.jsonPrimitive?.content
        Timber.tag(TAG).d("Pusher handshake complete, socket_id=%s", socketId)

        // Subscribe to the device's private channel
        subscribeToDeviceChannel()

        // Mark connected, reset backoff, start keepalive
        _connectionState.value = ConnectionState.CONNECTED
        reconnectAttempt = 0
        startPingLoop()
    }

    /**
     * Subscribe to `private-device.{deviceId}` using the Pusher subscribe message.
     *
     * For private channels in Reverb, the auth value is the device's Bearer token.
     * Reverb validates this against the BroadcastServiceProvider channel authorization.
     */
    private fun subscribeToDeviceChannel() {
        val deviceId = currentDeviceId ?: return
        val channelName = "private-device.$deviceId"

        val subscribePayload = buildJsonObject {
            put("event", PUSHER_SUBSCRIBE)
            put("data", buildJsonObject {
                put("channel", channelName)
                put("auth", "$currentToken")
            }.toString())
        }

        sendRaw(subscribePayload.toString())
        Timber.tag(TAG).d("Subscribing to channel: %s", channelName)
    }

    private fun onSubscriptionSucceeded(message: JsonObject) {
        val channel = message["channel"]?.jsonPrimitive?.content
        Timber.tag(TAG).d("Subscription succeeded: %s", channel)
    }

    private fun onPong() {
        Timber.tag(TAG).v("Pong received")
        emitEvent(WebSocketEvent.Pong(serverTime = null))
    }

    private fun onPusherError(message: JsonObject) {
        val dataString = message["data"]?.jsonPrimitive?.content
        val errorMsg = if (dataString != null) {
            try {
                val data = json.parseToJsonElement(dataString).jsonObject
                val code = data["code"]?.jsonPrimitive?.content
                val msg = data["message"]?.jsonPrimitive?.content ?: "Unknown"
                "Pusher error $code: $msg"
            } catch (_: Exception) {
                "Pusher error: $dataString"
            }
        } else {
            "Unknown Pusher error"
        }

        Timber.tag(TAG).e(errorMsg)
        emitEvent(WebSocketEvent.Error(
            message = errorMsg,
            code = "pusher_error",
            isRecoverable = true
        ))
    }

    // =========================================================================
    // WaSMS application event handling
    // =========================================================================

    /**
     * Handle application-level events broadcast on the device's private channel.
     *
     * Events from Laravel arrive as:
     * ```json
     * {
     *   "event": "new-messages",
     *   "channel": "private-device.abc123",
     *   "data": "{\"count\":5,\"batch_id\":\"uuid\"}"
     * }
     * ```
     *
     * Note: the "data" value is a JSON string (Pusher protocol convention).
     */
    private fun onApplicationEvent(event: String, message: JsonObject) {
        val data = parseEventData(message)

        when (event) {
            EVENT_NEW_MESSAGES -> {
                val count = data?.get("count")?.jsonPrimitive?.intOrNull ?: 1
                val batchId = data?.get("batch_id")?.jsonPrimitive?.content
                emitEvent(WebSocketEvent.NewMessages(count = count, batchId = batchId))
                Timber.tag(TAG).d("New messages event: count=%d", count)
            }

            EVENT_CONFIG_UPDATED -> {
                emitEvent(WebSocketEvent.ConfigUpdated)
                Timber.tag(TAG).d("Config updated event")
            }

            EVENT_COMMAND -> {
                val commandId = data?.get("command_id")?.jsonPrimitive?.content
                val type = data?.get("type")?.jsonPrimitive?.content
                if (commandId != null && type != null) {
                    val params = extractStringMap(data["params"]?.jsonObject)
                    emitEvent(WebSocketEvent.Command(
                        commandId = commandId,
                        type = type,
                        payload = params
                    ))
                    Timber.tag(TAG).d("Command event: type=%s id=%s", type, commandId)
                }
            }

            EVENT_PAUSE -> {
                val reason = data?.get("reason")?.jsonPrimitive?.content
                val duration = data?.get("duration_seconds")?.jsonPrimitive?.intOrNull
                emitEvent(WebSocketEvent.PauseSending(
                    reason = reason,
                    durationSeconds = duration
                ))
                Timber.tag(TAG).d("Pause event: reason=%s duration=%s", reason, duration)
            }

            EVENT_RESUME -> {
                val reason = data?.get("reason")?.jsonPrimitive?.content
                emitEvent(WebSocketEvent.ResumeSending(reason = reason))
                Timber.tag(TAG).d("Resume event: reason=%s", reason)
            }

            EVENT_MESSAGES_CANCELLED -> {
                val ids = data?.get("message_ids")?.jsonArray?.map {
                    it.jsonPrimitive.content
                } ?: emptyList()
                if (ids.isNotEmpty()) {
                    emitEvent(WebSocketEvent.MessagesCancelled(messageIds = ids))
                    Timber.tag(TAG).d("Messages cancelled event: %d messages", ids.size)
                }
            }

            else -> {
                Timber.tag(TAG).w("Unrecognized event: %s", event)
            }
        }
    }

    /**
     * Parse the "data" field from a Pusher protocol message.
     * The data is typically a JSON string that needs double-parsing.
     */
    private fun parseEventData(message: JsonObject): JsonObject? {
        val dataElement = message["data"] ?: return null
        return try {
            // Pusher protocol: data is a JSON string
            val dataString = dataElement.jsonPrimitive.content
            json.parseToJsonElement(dataString).jsonObject
        } catch (_: Exception) {
            try {
                // Fallback: data might be a direct JSON object
                dataElement.jsonObject
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Extract a Map<String, String> from a nullable JsonObject.
     */
    private fun extractStringMap(obj: JsonObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.entries.associate { (key, value) ->
            key to value.jsonPrimitive.content
        }
    }

    // =========================================================================
    // Heartbeat (Pusher protocol ping/pong keepalive)
    // =========================================================================

    /**
     * Start sending Pusher protocol pings every 30 seconds.
     * This keeps the WebSocket alive through NAT gateways and load balancers,
     * and lets the server detect stale connections.
     */
    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    sendPing()
                } else {
                    break
                }
            }
        }
    }

    private fun sendPing() {
        val pingMessage = buildJsonObject {
            put("event", PUSHER_PING)
            put("data", JsonPrimitive(""))
        }
        sendRaw(pingMessage.toString())
        Timber.tag(TAG).v("Ping sent")
    }

    // =========================================================================
    // Reconnection with exponential backoff + jitter
    // =========================================================================

    /**
     * Handle a disconnection event. Determines whether to reconnect based on
     * the close code and current state.
     *
     * Close code handling:
     * - 1000 (normal):     Server requested close, do not reconnect
     * - 4001 (auth fail):  Token expired/invalid, emit error for token refresh
     * - 4429 (rate limit): Parse retry-after and schedule reconnect
     * - Other/failure:     Exponential backoff reconnection
     */
    private fun handleDisconnection(code: Int, reason: String) {
        pingJob?.cancel()
        pingJob = null
        webSocket = null
        socketId = null

        when {
            isManuallyDisconnected -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                Timber.tag(TAG).d("Not reconnecting (manual disconnect)")
            }

            code == CLOSE_NORMAL -> {
                _connectionState.value = ConnectionState.DISCONNECTED
                Timber.tag(TAG).d("Not reconnecting (clean server close)")
            }

            code == CLOSE_AUTH_FAILED -> {
                _connectionState.value = ConnectionState.FAILED
                emitEvent(WebSocketEvent.Error(
                    message = "Authentication failed: $reason",
                    code = "auth_failed",
                    isRecoverable = false
                ))
                Timber.tag(TAG).e("Auth failed (4001). Token refresh required before reconnecting.")
            }

            code == CLOSE_RATE_LIMITED -> {
                _connectionState.value = ConnectionState.RECONNECTING
                val retryMs = parseRetryAfterMs(reason)
                scheduleReconnect(retryMs)
                Timber.tag(TAG).w("Rate limited (4429). Reconnecting in %d ms", retryMs)
            }

            else -> {
                _connectionState.value = ConnectionState.RECONNECTING
                val delayMs = calculateBackoffDelay()
                reconnectAttempt++
                scheduleReconnect(delayMs)
                Timber.tag(TAG).d(
                    "Abnormal close (code=%d). Reconnecting in %d ms (attempt %d)",
                    code, delayMs, reconnectAttempt
                )
            }
        }
    }

    /**
     * Calculate reconnection delay using exponential backoff with jitter.
     *
     * Progression: 1s, 2s, 4s, 8s, 16s, 30s (capped)
     * Each value includes +/- 30% random jitter to prevent thundering herd
     * when multiple devices reconnect simultaneously after a server restart.
     */
    private fun calculateBackoffDelay(): Long {
        val exponent = minOf(reconnectAttempt, MAX_BACKOFF_EXPONENT)
        val exponentialDelay = BASE_DELAY_MS * (1L shl exponent)
        val cappedDelay = minOf(exponentialDelay, MAX_DELAY_MS)
        val jitter = (Math.random() * cappedDelay * JITTER_FACTOR).toLong()
        return cappedDelay + jitter
    }

    private fun scheduleReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!isManuallyDisconnected) {
                Timber.tag(TAG).d("Reconnecting (attempt %d)...", reconnectAttempt)
                doConnect()
            }
        }
    }

    /**
     * Parse retry-after seconds from a WebSocket close reason string.
     * Expected format: "Rate limited. Retry after 15 seconds."
     * Falls back to 30 seconds if parsing fails.
     */
    private fun parseRetryAfterMs(reason: String): Long {
        return try {
            val seconds = Regex("(\\d+)").find(reason)?.value?.toLongOrNull()
            (seconds ?: 30L) * 1000L
        } catch (_: Exception) {
            30_000L
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Send raw text over the WebSocket. Logs a warning if the socket is closed.
     */
    private fun sendRaw(text: String) {
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            Timber.tag(TAG).w("Failed to send (socket null or closed)")
        }
    }

    /**
     * Emit a WebSocketEvent to the event channel for downstream consumers.
     */
    private fun emitEvent(event: WebSocketEvent) {
        scope.launch {
            eventChannel.send(event)
        }
    }
}
