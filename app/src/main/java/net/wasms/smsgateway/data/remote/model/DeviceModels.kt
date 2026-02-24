package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to register or update the device's FCM push token.
 * Called after Firebase token refresh.
 */
@Serializable
data class FcmTokenRequest(
    @SerialName("fcm_token")
    val fcmToken: String,

    @SerialName("platform")
    val platform: String = "android"
)

/**
 * Response from the FCM token registration endpoint.
 */
@Serializable
data class FcmTokenResponse(
    @SerialName("registered")
    val registered: Boolean,

    @SerialName("push_enabled")
    val pushEnabled: Boolean
)

/**
 * Response from GET /device/me containing the current device profile.
 */
@Serializable
data class DeviceProfileResponse(
    @SerialName("device_id")
    val id: String,

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("device_name")
    val deviceName: String,

    @SerialName("status")
    val status: String,

    @SerialName("registered_at")
    val registeredAt: String? = null,

    @SerialName("last_heartbeat_at")
    val lastHeartbeatAt: String? = null,

    @SerialName("sims")
    val simCards: List<SimCardDto> = emptyList(),

    @SerialName("stats")
    val stats: DeviceStatsDto? = null
)

/**
 * Device statistics included in the device profile response.
 */
@Serializable
data class DeviceStatsDto(
    @SerialName("today_sent")
    val todaySent: Int = 0,

    @SerialName("today_failed")
    val todayFailed: Int = 0,

    @SerialName("today_received")
    val todayReceived: Int = 0,

    @SerialName("queue_depth")
    val queueDepth: Int = 0
)

/**
 * Response from DELETE /device/{deviceId} for deregistration.
 */
@Serializable
data class DeregisterResponse(
    @SerialName("device_id")
    val deviceId: String,

    @SerialName("deregistered_at")
    val deregisteredAt: String,

    @SerialName("pending_messages_reassigned")
    val pendingMessagesReassigned: Int = 0
)

/**
 * Heartbeat request containing device health telemetry.
 * Sent at the server-configured interval (default 60s over HTTP, 30s over WebSocket).
 */
@Serializable
data class HeartbeatRequest(
    @SerialName("timestamp")
    val timestamp: String? = null,

    @SerialName("battery")
    val battery: BatteryDto,

    @SerialName("connectivity")
    val connectivity: ConnectivityDto,

    @SerialName("memory")
    val memory: MemoryDto? = null,

    @SerialName("sms_queue")
    val smsQueue: SmsQueueDto? = null,

    @SerialName("sims")
    val sims: List<SimHeartbeatDto> = emptyList(),

    @SerialName("app_version")
    val appVersion: String,

    @SerialName("uptime_seconds")
    val uptimeSeconds: Long
)

/**
 * Battery telemetry within a heartbeat.
 */
@Serializable
data class BatteryDto(
    @SerialName("level")
    val level: Int,

    @SerialName("is_charging")
    val isCharging: Boolean,

    @SerialName("temperature_celsius")
    val temperatureCelsius: Float? = null
)

/**
 * Network connectivity telemetry within a heartbeat.
 */
@Serializable
data class ConnectivityDto(
    @SerialName("type")
    val type: String,

    @SerialName("signal_strength_dbm")
    val signalStrengthDbm: Int? = null,

    @SerialName("is_roaming")
    val isRoaming: Boolean = false
)

/**
 * Memory usage telemetry within a heartbeat.
 */
@Serializable
data class MemoryDto(
    @SerialName("available_mb")
    val availableMb: Int,

    @SerialName("total_mb")
    val totalMb: Int
)

/**
 * Local SMS queue depth within a heartbeat.
 */
@Serializable
data class SmsQueueDto(
    @SerialName("pending_in_app")
    val pendingInApp: Int,

    @SerialName("pending_in_smsmanager")
    val pendingInSmsManager: Int
)

/**
 * Per-SIM status within a heartbeat.
 */
@Serializable
data class SimHeartbeatDto(
    @SerialName("slot")
    val slot: Int,

    @SerialName("active")
    val active: Boolean,

    @SerialName("carrier")
    val carrier: String? = null,

    @SerialName("signal_strength_dbm")
    val signalStrengthDbm: Int? = null
)

/**
 * Heartbeat response from the server.
 * Contains next heartbeat interval and device status directives.
 */
@Serializable
data class HeartbeatResponse(
    @SerialName("acknowledged")
    val acknowledged: Boolean = true,

    @SerialName("server_time")
    val serverTime: String,

    @SerialName("device_status")
    val deviceStatus: String? = null,

    @SerialName("pending_commands")
    val pendingCommands: Int = 0,

    @SerialName("next_heartbeat_seconds")
    val nextHeartbeatSeconds: Int? = null,

    @SerialName("config_updated")
    val configUpdated: Boolean = false
)

/**
 * Device operational configuration from GET /device/config.
 * Controls sending rates, SIM rotation, health monitoring, and polling intervals.
 */
@Serializable
data class DeviceConfigResponse(
    @SerialName("config_version")
    val configVersion: String? = null,

    @SerialName("sending")
    val sending: SendingConfigDto? = null,

    @SerialName("sim_rotation")
    val simRotation: SimRotationConfigDto? = null,

    @SerialName("health")
    val health: HealthConfigDto? = null,

    @SerialName("incoming")
    val incoming: IncomingConfigDto? = null,

    @SerialName("polling")
    val polling: PollingConfigDto? = null
) {
    // Convenience accessors that flatten the nested structure for domain mapping
    val maxSmsPerMinute: Int get() = sending?.rateLimitPerMinute ?: 10
    val maxSmsPerHour: Int get() = sending?.rateLimitPerHour ?: 200
    val simRotationEnabled: Boolean get() = simRotation != null
    val simCooldownSeconds: Int get() = simRotation?.cooldownDurationSeconds ?: 300
    val heartbeatIntervalSeconds: Int get() = health?.heartbeatIntervalSeconds ?: 60
    val batchSize: Int get() = sending?.batchSize ?: 10
    val retryMaxAttempts: Int get() = sending?.retryMaxAttempts ?: 3
    val retryBackoffSeconds: List<Int>
        get() {
            val base = sending?.retryBackoffBaseSeconds ?: 60
            val multiplier = sending?.retryBackoffMultiplier ?: 2.0
            return (0 until retryMaxAttempts).map { attempt ->
                (base * Math.pow(multiplier, attempt.toDouble())).toInt()
            }
        }
    val quietHoursStart: Int? get() = null // Extended in future server config
    val quietHoursEnd: Int? get() = null
    val autoReplyEnabled: Boolean get() = incoming?.autoReplyEnabled ?: false
    val incomingForwardEnabled: Boolean get() = incoming?.forwardIncoming ?: true
}

/**
 * Sending rate and retry configuration within device config.
 */
@Serializable
data class SendingConfigDto(
    @SerialName("rate_limit_per_minute")
    val rateLimitPerMinute: Int = 10,

    @SerialName("rate_limit_per_hour")
    val rateLimitPerHour: Int = 200,

    @SerialName("rate_limit_per_day")
    val rateLimitPerDay: Int = 2000,

    @SerialName("min_delay_between_sms_ms")
    val minDelayBetweenSmsMs: Long = 3000,

    @SerialName("max_delay_between_sms_ms")
    val maxDelayBetweenSmsMs: Long = 8000,

    @SerialName("randomize_delay")
    val randomizeDelay: Boolean = true,

    @SerialName("batch_size")
    val batchSize: Int = 10,

    @SerialName("retry_max_attempts")
    val retryMaxAttempts: Int = 3,

    @SerialName("retry_backoff_base_seconds")
    val retryBackoffBaseSeconds: Int = 60,

    @SerialName("retry_backoff_multiplier")
    val retryBackoffMultiplier: Double = 2.0
)

/**
 * SIM rotation strategy configuration within device config.
 */
@Serializable
data class SimRotationConfigDto(
    @SerialName("strategy")
    val strategy: String = "round_robin",

    @SerialName("primary_sim_slot")
    val primarySimSlot: Int = 0,

    @SerialName("weights")
    val weights: Map<String, Int> = emptyMap(),

    @SerialName("cooldown_after_failures")
    val cooldownAfterFailures: Int = 5,

    @SerialName("cooldown_duration_seconds")
    val cooldownDurationSeconds: Int = 300
)

/**
 * Health monitoring configuration within device config.
 */
@Serializable
data class HealthConfigDto(
    @SerialName("heartbeat_interval_seconds")
    val heartbeatIntervalSeconds: Int = 60,

    @SerialName("min_battery_level")
    val minBatteryLevel: Int = 15,

    @SerialName("pause_on_low_battery")
    val pauseOnLowBattery: Boolean = true,

    @SerialName("pause_on_no_wifi")
    val pauseOnNoWifi: Boolean = false,

    @SerialName("pause_on_roaming")
    val pauseOnRoaming: Boolean = true
)

/**
 * Incoming SMS handling configuration within device config.
 */
@Serializable
data class IncomingConfigDto(
    @SerialName("forward_incoming")
    val forwardIncoming: Boolean = true,

    @SerialName("auto_reply_enabled")
    val autoReplyEnabled: Boolean = true,

    @SerialName("ai_reply_timeout_seconds")
    val aiReplyTimeoutSeconds: Int = 30
)

/**
 * Polling interval configuration within device config.
 */
@Serializable
data class PollingConfigDto(
    @SerialName("active_interval_ms")
    val activeIntervalMs: Long = 5000,

    @SerialName("idle_interval_ms")
    val idleIntervalMs: Long = 30000,

    @SerialName("idle_threshold_minutes")
    val idleThresholdMinutes: Int = 10
)
