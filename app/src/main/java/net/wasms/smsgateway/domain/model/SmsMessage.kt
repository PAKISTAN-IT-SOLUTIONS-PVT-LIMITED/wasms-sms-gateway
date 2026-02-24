package net.wasms.smsgateway.domain.model

import kotlinx.datetime.Instant

data class SmsMessage(
    val id: String,
    val serverId: String?,
    val destination: String,
    val body: String,
    val state: SmsState,
    val priority: MessagePriority,
    val simSlot: Int?,
    val partCount: Int,
    val partsDelivered: Int,
    val errorMessage: String?,
    val retryCount: Int,
    val maxRetries: Int,
    val scheduledAt: Instant?,
    val sentAt: Instant?,
    val deliveredAt: Instant?,
    val failedAt: Instant?,
    val createdAt: Instant
) {
    val isMultipart: Boolean get() = partCount > 1
    val isComplete: Boolean get() = state.isTerminal
    val canRetry: Boolean get() = retryCount < maxRetries && !state.isTerminal
    val deliveryProgress: Float
        get() = if (partCount > 0) partsDelivered.toFloat() / partCount else 0f
}
