package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from GET /device/messages/pending containing a batch of
 * messages assigned to this device for sending.
 */
@Serializable
data class PendingMessagesResponse(
    @SerialName("messages")
    val messages: List<PendingMessage>,

    @SerialName("batch_id")
    val batchId: String? = null,

    @SerialName("remaining_in_queue")
    val remainingInQueue: Int = 0,

    @SerialName("next_poll_delay_ms")
    val nextPollDelayMs: Long = 5000,

    @SerialName("has_more")
    val hasMore: Boolean = false
)

/**
 * A single pending message from the server queue, assigned to this device.
 * The device must ACK receipt and later submit a delivery report.
 */
@Serializable
data class PendingMessage(
    @SerialName("message_id")
    val id: String,

    @SerialName("to")
    val destination: String,

    @SerialName("body")
    val body: String,

    @SerialName("priority")
    val priority: String = "normal",

    @SerialName("sim_slot")
    val simSlot: Int? = null,

    @SerialName("max_parts")
    val maxParts: Int = 1,

    @SerialName("send_after")
    val scheduledAt: String? = null,

    @SerialName("expires_at")
    val expiresAt: String? = null,

    @SerialName("lock_expires_at")
    val lockExpiresAt: String? = null,

    @SerialName("retry_count")
    val retryCount: Int = 0,

    @SerialName("metadata")
    val metadata: MessageMetadataDto? = null
)

/**
 * Optional metadata associated with a pending message (campaign, contact, template).
 */
@Serializable
data class MessageMetadataDto(
    @SerialName("campaign_id")
    val campaignId: String? = null,

    @SerialName("contact_id")
    val contactId: String? = null,

    @SerialName("template_id")
    val templateId: String? = null
)

/**
 * Request to acknowledge that a message has been dispatched to Android SmsManager.
 * This extends the server-side lock on the message.
 */
@Serializable
data class AckRequest(
    @SerialName("status")
    val status: String = "dispatched",

    @SerialName("sim_slot")
    val simSlot: Int? = null,

    @SerialName("dispatched_at")
    val dispatchedAt: String? = null
)

/**
 * Response after acknowledging a message dispatch.
 */
@Serializable
data class AckResponse(
    @SerialName("message_id")
    val messageId: String,

    @SerialName("status")
    val status: String,

    @SerialName("lock_extended_until")
    val lockExtendedUntil: String? = null
)

/**
 * Request to submit a single delivery report for a message.
 * Includes Android SmsManager result codes and carrier information.
 */
@Serializable
data class DeliveryReportRequest(
    @SerialName("status")
    val status: String,

    @SerialName("carrier_status_code")
    val carrierStatusCode: Int? = null,

    @SerialName("carrier_error_code")
    val carrierErrorCode: Int? = null,

    @SerialName("sim_slot")
    val simSlot: Int? = null,

    @SerialName("parts_sent")
    val partsSent: Int = 1,

    @SerialName("parts_delivered")
    val partsDelivered: Int = 0,

    @SerialName("sent_at")
    val sentAt: String? = null,

    @SerialName("delivered_at")
    val deliveredAt: String? = null,

    @SerialName("failure_reason")
    val failureReason: String? = null
)

/**
 * Response after submitting a delivery report.
 * The server may instruct the device to retry via a different SIM slot.
 */
@Serializable
data class DeliveryReportResponse(
    @SerialName("message_id")
    val messageId: String,

    @SerialName("status")
    val status: String,

    @SerialName("recorded_at")
    val recordedAt: String? = null,

    @SerialName("should_retry")
    val shouldRetry: Boolean = false,

    @SerialName("retry_after_seconds")
    val retryAfterSeconds: Int? = null,

    @SerialName("retry_via_sim_slot")
    val retryViaSimSlot: Int? = null
)

/**
 * Request to submit delivery reports for multiple messages in a single request.
 * Maximum 100 reports per request.
 */
@Serializable
data class BulkDeliveryReportRequest(
    @SerialName("reports")
    val reports: List<DeliveryReportItem>
)

/**
 * A single delivery report item within a bulk report request.
 */
@Serializable
data class DeliveryReportItem(
    @SerialName("message_id")
    val messageId: String,

    @SerialName("status")
    val status: String,

    @SerialName("carrier_status_code")
    val carrierStatusCode: Int? = null,

    @SerialName("carrier_error_code")
    val carrierErrorCode: Int? = null,

    @SerialName("sim_slot")
    val simSlot: Int? = null,

    @SerialName("parts_sent")
    val partsSent: Int = 1,

    @SerialName("parts_delivered")
    val partsDelivered: Int = 0,

    @SerialName("sent_at")
    val sentAt: String? = null,

    @SerialName("delivered_at")
    val deliveredAt: String? = null,

    @SerialName("failure_reason")
    val failureReason: String? = null
)

/**
 * Response from the bulk delivery report endpoint.
 * Individual failures are reported in the errors array;
 * the device does NOT need to retry the entire batch.
 */
@Serializable
data class BulkDeliveryReportResponse(
    @SerialName("processed")
    val processed: Int,

    @SerialName("results")
    val results: List<DeliveryReportResponse>,

    @SerialName("errors")
    val errors: List<BulkReportError> = emptyList()
)

/**
 * An individual error within a bulk delivery report response.
 */
@Serializable
data class BulkReportError(
    @SerialName("message_id")
    val messageId: String,

    @SerialName("code")
    val code: String,

    @SerialName("message")
    val message: String
)
