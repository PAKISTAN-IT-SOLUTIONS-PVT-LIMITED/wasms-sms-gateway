package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SIM card information DTO used across multiple endpoints.
 * Maps between the device's SubscriptionInfo and the server's SIM records.
 */
@Serializable
data class SimCardDto(
    @SerialName("slot")
    val slot: Int,

    @SerialName("iccid")
    val iccId: String? = null,

    @SerialName("imsi")
    val imsi: String? = null,

    @SerialName("carrier")
    val carrierName: String,

    @SerialName("phone_number")
    val phoneNumber: String? = null,

    @SerialName("country_code")
    val countryCode: String? = null,

    @SerialName("is_data_capable")
    val isDataCapable: Boolean = true,

    @SerialName("is_sms_capable")
    val isSmSCapable: Boolean = true,

    @SerialName("status")
    val status: String = "active"
)

/**
 * Request to register or update the full set of SIM cards in the device.
 * PUT semantics: the full state replaces the previous state.
 */
@Serializable
data class SimCardsRequest(
    @SerialName("sims")
    val simCards: List<SimCardDto>
)

/**
 * Response from PUT /device/sims containing server-side SIM state.
 */
@Serializable
data class SimCardsResponse(
    @SerialName("sims")
    val sims: List<SimServerStateDto>
)

/**
 * Server-side SIM state returned after SIM registration.
 * Includes daily usage counters and limits.
 */
@Serializable
data class SimServerStateDto(
    @SerialName("slot")
    val slot: Int,

    @SerialName("server_sim_id")
    val serverSimId: String? = null,

    @SerialName("status")
    val status: String,

    @SerialName("daily_limit")
    val dailyLimit: Int = 200,

    @SerialName("daily_sent")
    val dailySent: Int = 0,

    @SerialName("daily_remaining")
    val dailyRemaining: Int = 200
)

/**
 * Request to report delivery statistics for a specific SIM slot.
 * Sent periodically during active sending.
 */
@Serializable
data class SimStatsRequest(
    @SerialName("period_start")
    val periodStart: String? = null,

    @SerialName("period_end")
    val periodEnd: String? = null,

    @SerialName("sent_count")
    val totalSent: Int,

    @SerialName("delivered_count")
    val totalDelivered: Int = 0,

    @SerialName("failed_count")
    val totalFailed: Int,

    @SerialName("avg_delivery_time_ms")
    val avgDeliveryTimeMs: Long? = null,

    @SerialName("carrier_errors")
    val carrierErrors: Map<String, Int> = emptyMap()
)

/**
 * Response from the SIM stats endpoint.
 * Includes the server's health assessment and recommendations.
 */
@Serializable
data class SimStatsResponse(
    @SerialName("recorded")
    val recorded: Boolean = true,

    @SerialName("sim_health_score")
    val healthScore: Float = 1.0f,

    @SerialName("recommendations")
    val recommendations: SimRecommendationsDto? = null
)

/**
 * Server recommendations for SIM usage based on health analysis.
 */
@Serializable
data class SimRecommendationsDto(
    @SerialName("reduce_rate")
    val reduceRate: Boolean = false,

    @SerialName("cooldown_suggested")
    val cooldownSuggested: Boolean = false
)

/**
 * Response from GET /device/sims/config containing SIM rotation configuration.
 * This is a subset of the full device config, provided separately for
 * more frequent refresh during active sending.
 */
@Serializable
data class SimConfigResponse(
    @SerialName("strategy")
    val strategy: String = "round_robin",

    @SerialName("primary_sim_slot")
    val primarySimSlot: Int = 0,

    @SerialName("weights")
    val weights: Map<String, Int> = emptyMap(),

    @SerialName("per_sim_limits")
    val perSimLimits: Map<String, SimLimitDto> = emptyMap(),

    @SerialName("cooldown_rules")
    val cooldownRules: CooldownRulesDto? = null,

    @SerialName("blocked_carriers")
    val blockedCarriers: List<String> = emptyList(),

    @SerialName("preferred_carrier_for_prefix")
    val preferredCarrierForPrefix: Map<String, Int> = emptyMap()
)

/**
 * Per-SIM sending rate limits.
 */
@Serializable
data class SimLimitDto(
    @SerialName("per_minute")
    val perMinute: Int = 5,

    @SerialName("per_hour")
    val perHour: Int = 100,

    @SerialName("per_day")
    val perDay: Int = 200
)

/**
 * Cooldown rules for SIM rotation when failures occur.
 */
@Serializable
data class CooldownRulesDto(
    @SerialName("consecutive_failures_trigger")
    val consecutiveFailuresTrigger: Int = 5,

    @SerialName("cooldown_duration_seconds")
    val cooldownDurationSeconds: Int = 300,

    @SerialName("carrier_error_codes_trigger_cooldown")
    val carrierErrorCodesTriggerCooldown: List<Int> = emptyList()
)
