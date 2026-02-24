package net.wasms.smsgateway.data.remote.websocket

/**
 * Sealed class representing all possible events received over the WebSocket connection.
 * Events are parsed from Laravel Reverb broadcast messages on the device's private channel.
 */
sealed class WebSocketEvent {

    /**
     * Server has new messages available for this device.
     * The device should pull pending messages from the HTTP API.
     *
     * @param count Number of new messages available.
     * @param batchId Optional batch identifier for correlation.
     */
    data class NewMessages(
        val count: Int,
        val batchId: String? = null
    ) : WebSocketEvent()

    /**
     * Device configuration was updated on the server.
     * The device should re-fetch config from GET /device/config.
     */
    data object ConfigUpdated : WebSocketEvent()

    /**
     * A remote command was issued for this device.
     * The device should execute it and ACK via POST /device/commands/{commandId}/ack.
     *
     * @param commandId The server-side command identifier.
     * @param type The command type (pause, resume, update_config, etc.).
     * @param payload Optional key-value parameters for the command.
     */
    data class Command(
        val commandId: String,
        val type: String,
        val payload: Map<String, String> = emptyMap()
    ) : WebSocketEvent()

    /**
     * Server instructs the device to pause all sending operations.
     * The device should stop pulling messages and halt the send queue.
     *
     * @param reason Human-readable reason for the pause.
     * @param durationSeconds Optional duration after which the device may auto-resume.
     */
    data class PauseSending(
        val reason: String? = null,
        val durationSeconds: Int? = null
    ) : WebSocketEvent()

    /**
     * Server instructs the device to resume sending operations.
     *
     * @param reason Human-readable reason for the resume.
     */
    data class ResumeSending(
        val reason: String? = null
    ) : WebSocketEvent()

    /**
     * Heartbeat acknowledgment from the server (pong response to our ping).
     *
     * @param serverTime The server's current timestamp.
     */
    data class Pong(
        val serverTime: String? = null
    ) : WebSocketEvent()

    /**
     * One or more messages assigned to this device were cancelled.
     * The device should remove them from its local queue.
     *
     * @param messageIds List of message IDs to cancel.
     */
    data class MessagesCancelled(
        val messageIds: List<String>
    ) : WebSocketEvent()

    /**
     * An error occurred on the WebSocket connection.
     * This may be a protocol error, authentication failure, or server error.
     *
     * @param message Human-readable error description.
     * @param code Optional error code for programmatic handling.
     * @param isRecoverable Whether the client should attempt reconnection.
     */
    data class Error(
        val message: String,
        val code: String? = null,
        val isRecoverable: Boolean = true
    ) : WebSocketEvent()
}
