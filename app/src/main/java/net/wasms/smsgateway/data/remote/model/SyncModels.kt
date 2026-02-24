package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for a full state sync after an offline period or on first launch.
 * The device sends its last known state so the server can reconcile.
 */
@Serializable
data class FullSyncRequest(
    @SerialName("reason")
    val reason: String = "app_start",

    @SerialName("last_known_state")
    val lastKnownState: LastKnownStateDto
)

/**
 * Device's last known state sent during full sync.
 * Used by the server to determine what the device missed.
 */
@Serializable
data class LastKnownStateDto(
    @SerialName("last_message_id")
    val lastMessageId: String? = null,

    @SerialName("last_heartbeat_at")
    val lastHeartbeatAt: String? = null,

    @SerialName("local_pending_count")
    val localPendingCount: Int = 0,

    @SerialName("local_pending_message_ids")
    val localPendingMessageIds: List<String> = emptyList()
)

/**
 * Response from POST /device/sync/full containing the complete device state.
 * Includes config, pending commands, SIM state, and stale message reconciliation.
 */
@Serializable
data class FullSyncResponse(
    @SerialName("device_status")
    val deviceStatus: String? = null,

    @SerialName("config")
    val config: DeviceConfigResponse? = null,

    @SerialName("pending_commands")
    val commands: List<CommandDto> = emptyList(),

    @SerialName("sims")
    val sims: SimCardsResponse? = null,

    @SerialName("stale_messages")
    val staleMessages: StaleMessagesDto? = null,

    @SerialName("new_pending_count")
    val newPendingCount: Int = 0,

    @SerialName("sync_cursor")
    val syncCursor: String? = null,

    @SerialName("server_time")
    val serverTime: String
)

/**
 * Stale message reconciliation from the server.
 * Tells the device which local messages to keep and which to discard.
 */
@Serializable
data class StaleMessagesDto(
    @SerialName("cancel")
    val cancelIds: List<String> = emptyList(),

    @SerialName("keep")
    val keepIds: List<String> = emptyList()
)

/**
 * Response from GET /device/sync/incremental containing changes since the last cursor.
 * The device should page through results if has_more is true.
 */
@Serializable
data class IncrementalSyncResponse(
    @SerialName("changes")
    val changes: List<SyncChangeDto> = emptyList(),

    @SerialName("new_cursor")
    val cursor: String,

    @SerialName("has_more")
    val hasMore: Boolean = false,

    @SerialName("server_time")
    val serverTime: String
)

/**
 * A single change event within an incremental sync response.
 * The type field determines which additional fields are present.
 *
 * Known change types:
 * - "message_cancelled" : A previously assigned message was cancelled
 * - "config_updated"    : Device config changed, re-fetch from /device/config
 * - "command_issued"    : A new command was issued
 * - "sim_limit_updated" : Per-SIM limits were changed
 */
@Serializable
data class SyncChangeDto(
    @SerialName("type")
    val type: String,

    @SerialName("message_id")
    val messageId: String? = null,

    @SerialName("command")
    val command: CommandDto? = null,

    @SerialName("sim_slot")
    val simSlot: Int? = null,

    @SerialName("new_daily_limit")
    val newDailyLimit: Int? = null,

    @SerialName("timestamp")
    val timestamp: String
)

/**
 * Known sync change type constants for type-safe handling.
 */
object SyncChangeType {
    const val MESSAGE_CANCELLED = "message_cancelled"
    const val CONFIG_UPDATED = "config_updated"
    const val COMMAND_ISSUED = "command_issued"
    const val SIM_LIMIT_UPDATED = "sim_limit_updated"
}
