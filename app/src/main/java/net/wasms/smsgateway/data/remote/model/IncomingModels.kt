package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to forward a single received SMS from the device to the server.
 * The idempotency key prevents duplicate processing if the device retries.
 */
@Serializable
data class IncomingSmsRequest(
    @SerialName("from")
    val from: String,

    @SerialName("body")
    val body: String,

    @SerialName("sim_slot")
    val simSlot: Int,

    @SerialName("received_at")
    val receivedAt: String,

    @SerialName("idempotency_key")
    val idempotencyKey: String? = null
)

/**
 * Request to forward multiple received SMS messages in a single request.
 * Maximum 50 messages per request.
 */
@Serializable
data class BulkIncomingSmsRequest(
    @SerialName("messages")
    val messages: List<IncomingSmsRequest>
)

/**
 * Response after forwarding a single incoming SMS.
 * May include an auto-reply that the device should queue for sending.
 */
@Serializable
data class IncomingSmsResponse(
    @SerialName("incoming_id")
    val incomingId: String? = null,

    @SerialName("from")
    val from: String? = null,

    @SerialName("processed")
    val processed: Boolean = true,

    @SerialName("actions_taken")
    val actionsTaken: List<IncomingActionDto> = emptyList(),

    @SerialName("auto_reply")
    val autoReply: AutoReplyDto? = null
)

/**
 * An action the server took upon receiving the incoming SMS (e.g., opt-out processing).
 */
@Serializable
data class IncomingActionDto(
    @SerialName("type")
    val type: String,

    @SerialName("detail")
    val detail: String? = null
)

/**
 * Auto-reply instruction from the server.
 * The device should queue this reply for sending after the specified delay.
 */
@Serializable
data class AutoReplyDto(
    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("reply_body")
    val replyBody: String,

    @SerialName("send_via_sim_slot")
    val sendViaSimSlot: Int? = null,

    @SerialName("delay_seconds")
    val delaySeconds: Int = 0
)

/**
 * Response from GET /device/incoming/{incomingId}/reply for polling
 * AI-generated auto-replies that require server-side processing time.
 */
@Serializable
data class AutoReplyResponse(
    @SerialName("incoming_id")
    val incomingId: String? = null,

    @SerialName("reply_status")
    val replyStatus: String,

    @SerialName("reply_body")
    val body: String? = null,

    @SerialName("send_via_sim_slot")
    val sendViaSimSlot: Int? = null,

    @SerialName("delay_seconds")
    val delay: Int = 0,

    @SerialName("retry_after_seconds")
    val retryAfterSeconds: Int? = null
) {
    val isReady: Boolean get() = replyStatus == "ready"
    val isProcessing: Boolean get() = replyStatus == "processing"
    val hasNoReply: Boolean get() = replyStatus == "none"
}

/**
 * Response from the bulk incoming SMS endpoint.
 */
@Serializable
data class BulkIncomingSmsResponse(
    @SerialName("processed")
    val processed: Int,

    @SerialName("results")
    val results: List<BulkIncomingResultDto> = emptyList(),

    @SerialName("errors")
    val errors: List<BulkIncomingErrorDto> = emptyList()
)

/**
 * Result for a single message within a bulk incoming SMS response.
 */
@Serializable
data class BulkIncomingResultDto(
    @SerialName("incoming_id")
    val incomingId: String,

    @SerialName("from")
    val from: String,

    @SerialName("status")
    val status: String,

    @SerialName("auto_reply")
    val autoReply: AutoReplyDto? = null
)

/**
 * Error for a single message within a bulk incoming SMS response.
 */
@Serializable
data class BulkIncomingErrorDto(
    @SerialName("from")
    val from: String? = null,

    @SerialName("code")
    val code: String,

    @SerialName("message")
    val message: String
)
