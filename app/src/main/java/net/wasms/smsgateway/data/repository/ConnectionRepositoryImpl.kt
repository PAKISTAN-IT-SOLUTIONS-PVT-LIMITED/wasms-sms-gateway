package net.wasms.smsgateway.data.repository

import kotlinx.coroutines.flow.Flow
import net.wasms.smsgateway.data.local.preferences.TokenManager
import net.wasms.smsgateway.data.remote.websocket.ReverbWebSocketClient
import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ConnectionRepository] that delegates WebSocket operations
 * to [ReverbWebSocketClient] and guards connection attempts behind authentication state.
 *
 * The WebSocket connection is the primary real-time channel for message dispatch
 * notifications. FCM serves as a wakeup fallback, and HTTP polling is the safety net.
 */
@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val webSocketClient: ReverbWebSocketClient,
    private val tokenManager: TokenManager
) : ConnectionRepository {

    /**
     * Observes the WebSocket connection state.
     * The state flow emits [ConnectionState] values as the connection transitions
     * between DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, and FAILED.
     */
    override fun observeConnectionState(): Flow<ConnectionState> {
        return webSocketClient.connectionState
    }

    /**
     * Connects to the WebSocket server using the current auth credentials.
     * Requires the device to be authenticated (valid token stored).
     * No-op if already connected.
     *
     * @throws IllegalStateException if the device is not authenticated
     */
    override suspend fun connect() {
        if (!tokenManager.isAuthenticated()) {
            Timber.w("Cannot connect WebSocket: device not authenticated")
            throw IllegalStateException("Device must be authenticated before connecting")
        }

        if (webSocketClient.isConnected()) {
            Timber.d("WebSocket already connected, skipping connect()")
            return
        }

        val token = tokenManager.accessToken
            ?: throw IllegalStateException("No access token available for WebSocket connection")
        val deviceId = tokenManager.deviceId
            ?: throw IllegalStateException("No device ID available for WebSocket connection")

        Timber.i("Initiating WebSocket connection for device %s", deviceId)
        webSocketClient.connect(token, deviceId)
    }

    /**
     * Gracefully disconnects from the WebSocket server.
     * Safe to call even if not connected.
     */
    override suspend fun disconnect() {
        Timber.i("Disconnecting WebSocket")
        webSocketClient.disconnect()
    }

    /**
     * Disconnects and reconnects to the WebSocket server.
     * Tears down the current connection and establishes a new one
     * with fresh auth credentials.
     *
     * @throws IllegalStateException if the device is not authenticated
     */
    override suspend fun reconnect() {
        if (!tokenManager.isAuthenticated()) {
            Timber.w("Cannot reconnect WebSocket: device not authenticated")
            throw IllegalStateException("Device must be authenticated before reconnecting")
        }

        Timber.i("Reconnecting WebSocket")
        webSocketClient.disconnect()

        val token = tokenManager.accessToken
            ?: throw IllegalStateException("No access token available for WebSocket reconnection")
        val deviceId = tokenManager.deviceId
            ?: throw IllegalStateException("No device ID available for WebSocket reconnection")

        webSocketClient.connect(token, deviceId)
    }

    /**
     * Returns true if the WebSocket connection is currently active.
     */
    override fun isConnected(): Boolean {
        return webSocketClient.isConnected()
    }
}
