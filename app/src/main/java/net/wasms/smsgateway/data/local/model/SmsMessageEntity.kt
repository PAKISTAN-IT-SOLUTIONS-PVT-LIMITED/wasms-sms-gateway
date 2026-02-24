package net.wasms.smsgateway.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import net.wasms.smsgateway.domain.model.MessagePriority
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState

@Entity(
    tableName = "sms_messages",
    indices = [
        Index(value = ["server_id"], unique = true),
        Index(value = ["destination"]),
        Index(value = ["state"]),
        Index(value = ["created_at"])
    ]
)
data class SmsMessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "server_id")
    val serverId: String?,

    @ColumnInfo(name = "destination")
    val destination: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "state")
    val state: SmsState,

    @ColumnInfo(name = "priority")
    val priority: MessagePriority,

    @ColumnInfo(name = "sim_slot")
    val simSlot: Int?,

    @ColumnInfo(name = "part_count", defaultValue = "1")
    val partCount: Int = 1,

    @ColumnInfo(name = "parts_delivered", defaultValue = "0")
    val partsDelivered: Int = 0,

    @ColumnInfo(name = "error_message")
    val errorMessage: String?,

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries", defaultValue = "3")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: Instant?,

    @ColumnInfo(name = "sent_at")
    val sentAt: Instant?,

    @ColumnInfo(name = "delivered_at")
    val deliveredAt: Instant?,

    @ColumnInfo(name = "failed_at")
    val failedAt: Instant?,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant
) {
    fun toDomain(): SmsMessage = SmsMessage(
        id = id,
        serverId = serverId,
        destination = destination,
        body = body,
        state = state,
        priority = priority,
        simSlot = simSlot,
        partCount = partCount,
        partsDelivered = partsDelivered,
        errorMessage = errorMessage,
        retryCount = retryCount,
        maxRetries = maxRetries,
        scheduledAt = scheduledAt,
        sentAt = sentAt,
        deliveredAt = deliveredAt,
        failedAt = failedAt,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(message: SmsMessage): SmsMessageEntity = SmsMessageEntity(
            id = message.id,
            serverId = message.serverId,
            destination = message.destination,
            body = message.body,
            state = message.state,
            priority = message.priority,
            simSlot = message.simSlot,
            partCount = message.partCount,
            partsDelivered = message.partsDelivered,
            errorMessage = message.errorMessage,
            retryCount = message.retryCount,
            maxRetries = message.maxRetries,
            scheduledAt = message.scheduledAt,
            sentAt = message.sentAt,
            deliveredAt = message.deliveredAt,
            failedAt = message.failedAt,
            createdAt = message.createdAt
        )
    }
}
