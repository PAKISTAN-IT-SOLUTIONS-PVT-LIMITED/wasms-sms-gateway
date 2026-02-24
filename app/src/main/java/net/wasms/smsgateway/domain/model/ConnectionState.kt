package net.wasms.smsgateway.domain.model

/**
 * WebSocket connection state for the hybrid connection (Agent 8 design).
 * WebSocket is primary, FCM is wakeup fallback, HTTP polling is safety net.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED;

    val isConnected: Boolean get() = this == CONNECTED
    val shouldReconnect: Boolean get() = this in setOf(DISCONNECTED, FAILED)
}
