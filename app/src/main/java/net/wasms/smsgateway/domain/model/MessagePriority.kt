package net.wasms.smsgateway.domain.model

/**
 * Message priority levels. Transactional messages (OTP, confirmations)
 * always go before campaign/bulk messages in the queue.
 */
enum class MessagePriority(val value: Int) {
    URGENT(0),
    TRANSACTIONAL(1),
    CAMPAIGN(2),
    BULK(3);

    companion object {
        fun fromValue(value: Int): MessagePriority =
            entries.firstOrNull { it.value == value } ?: BULK
    }
}
