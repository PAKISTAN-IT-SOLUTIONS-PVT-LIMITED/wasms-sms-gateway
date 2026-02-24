package net.wasms.smsgateway.domain.model

data class QueueStats(
    val pending: Int,
    val sending: Int,
    val sentToday: Int,
    val deliveredToday: Int,
    val failedToday: Int,
    val totalToday: Int
) {
    val deliveryRate: Float
        get() = if (totalToday > 0) deliveredToday.toFloat() / totalToday else 0f

    val deliveryRatePercent: String
        get() = "%.1f%%".format(deliveryRate * 100)

    val isEmpty: Boolean
        get() = pending == 0 && sending == 0
}
