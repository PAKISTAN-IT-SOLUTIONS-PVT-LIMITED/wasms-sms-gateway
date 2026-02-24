package net.wasms.smsgateway.domain.model

data class SimCard(
    val id: String,
    val subscriptionId: Int,
    val slot: Int,
    val carrierName: String,
    val phoneNumber: String?,
    val iccId: String?,
    val countryCode: String?,
    val isActive: Boolean,
    val dailyLimit: Int,
    val totalSent: Int,
    val totalFailed: Int,
    val healthScore: Float
) {
    val remainingToday: Int get() = (dailyLimit - totalSent).coerceAtLeast(0)
    val isThrottled: Boolean get() = remainingToday <= 0
    val displayName: String get() = "SIM ${slot + 1} ($carrierName)"
}
