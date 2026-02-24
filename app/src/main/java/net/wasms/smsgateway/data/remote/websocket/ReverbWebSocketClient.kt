package net.wasms.smsgateway.data.remote.websocket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.wasms.smsgateway.domain.model.ConnectionState

/**
 * WebSocket client interface for Laravel Reverb (Pusher protocol compatible).
 *
 * Responsibilities:
 * - Maintain a persistent WebSocket connection to the Reverb server
 * - Subscribe to the device's private channel for real-time events
 * - Handle reconnection with exponential backoff
 * - Emit typed WebSocketEvent instances for the service layer
 * - Send heartbeat pings to keep the connection alive
 *
 * The implementation uses the Pusher WebSocket protocol spoken by Reverb.
 * Connection URL: wss://{BuildConfig.WS_HOST}{BuildConfig.WS_PATH}
 */
interface ReverbWebSocketClient {

    /** Current connection state as a hot StateFlow. */
    val connectionState: StateFlow<ConnectionState>

    /**
     * Connect to the WebSocket server with the given auth credentials.
     *
     * @param token The Bearer access token for authentication.
     * @param deviceId The device ID for private channel subscription.
     */
    fun connect(token: String, deviceId: String)

    /** Gracefully disconnect from the WebSocket server and cancel reconnection. */
    fun disconnect()

    /** Whether the WebSocket is currently connected. */
    fun isConnected(): Boolean

    /** Observe incoming WebSocket events (new messages, commands, config changes, etc.). */
    fun observeEvents(): Flow<WebSocketEvent>

    /** Get the Reverb-assigned socket ID (available after connection_established). */
    fun getSocketId(): String?
}
