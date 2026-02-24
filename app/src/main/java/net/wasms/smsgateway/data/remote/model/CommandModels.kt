package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /device/commands containing pending remote commands.
 */
@Serializable
data class CommandsResponse(
    @SerialName("commands")
    val commands: List<CommandDto>
)

/**
 * A remote command issued by the server or admin.
 *
 * Supported command types:
 * - "pause"          : Stop pulling and sending messages
 * - "resume"         : Resume normal operations
 * - "disconnect"     : Gracefully disconnect and stop all activity
 * - "update_config"  : Re-fetch config from /device/config
 * - "send_test_sms"  : Send a test SMS to verify connectivity
 * - "clear_queue"    : Drop all locally queued messages
 */
@Serializable
data class CommandDto(
    @SerialName("command_id")
    val id: String,

    @SerialName("type")
    val type: String,

    @SerialName("issued_at")
    val issuedAt: String? = null,

    @SerialName("expires_at")
    val expiresAt: String? = null,

    @SerialName("params")
    val params: Map<String, String> = emptyMap()
)

/**
 * Request to acknowledge execution of a remote command.
 */
@Serializable
data class CommandAckRequest(
    @SerialName("status")
    val status: String,

    @SerialName("executed_at")
    val executedAt: String? = null,

    @SerialName("result")
    val result: String? = null,

    @SerialName("error")
    val error: String? = null
)

/**
 * Response after acknowledging a command.
 */
@Serializable
data class CommandAckResponse(
    @SerialName("command_id")
    val commandId: String,

    @SerialName("acknowledged")
    val acknowledged: Boolean = true
)

/**
 * Known command type constants for type-safe handling.
 */
object CommandType {
    const val PAUSE = "pause"
    const val RESUME = "resume"
    const val DISCONNECT = "disconnect"
    const val UPDATE_CONFIG = "update_config"
    const val SEND_TEST_SMS = "send_test_sms"
    const val CLEAR_QUEUE = "clear_queue"
}
