package net.wasms.smsgateway.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import net.wasms.smsgateway.domain.model.SimCard

@Entity(tableName = "sim_cards")
data class SimCardEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "subscription_id")
    val subscriptionId: Int,

    @ColumnInfo(name = "slot")
    val slot: Int,

    @ColumnInfo(name = "carrier_name")
    val carrierName: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String?,

    @ColumnInfo(name = "icc_id")
    val iccId: String?,

    @ColumnInfo(name = "country_code")
    val countryCode: String?,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "daily_limit", defaultValue = "50")
    val dailyLimit: Int = 50,

    @ColumnInfo(name = "total_sent", defaultValue = "0")
    val totalSent: Int = 0,

    @ColumnInfo(name = "total_failed", defaultValue = "0")
    val totalFailed: Int = 0,

    @ColumnInfo(name = "health_score", defaultValue = "1.0")
    val healthScore: Float = 1.0f,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Instant?,

    @ColumnInfo(name = "cooldown_until")
    val cooldownUntil: Instant?
) {
    fun toDomain(): SimCard = SimCard(
        id = id,
        subscriptionId = subscriptionId,
        slot = slot,
        carrierName = carrierName,
        phoneNumber = phoneNumber,
        iccId = iccId,
        countryCode = countryCode,
        isActive = isActive,
        dailyLimit = dailyLimit,
        totalSent = totalSent,
        totalFailed = totalFailed,
        healthScore = healthScore
    )

    companion object {
        fun fromDomain(simCard: SimCard, lastUsedAt: Instant? = null, cooldownUntil: Instant? = null): SimCardEntity =
            SimCardEntity(
                id = simCard.id,
                subscriptionId = simCard.subscriptionId,
                slot = simCard.slot,
                carrierName = simCard.carrierName,
                phoneNumber = simCard.phoneNumber,
                iccId = simCard.iccId,
                countryCode = simCard.countryCode,
                isActive = simCard.isActive,
                dailyLimit = simCard.dailyLimit,
                totalSent = simCard.totalSent,
                totalFailed = simCard.totalFailed,
                healthScore = simCard.healthScore,
                lastUsedAt = lastUsedAt,
                cooldownUntil = cooldownUntil
            )
    }
}
