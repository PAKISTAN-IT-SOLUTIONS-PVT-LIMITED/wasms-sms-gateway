package net.wasms.smsgateway.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import net.wasms.smsgateway.domain.model.DeviceConfig

@Entity(tableName = "device_config")
data class DeviceConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "max_sms_per_minute")
    val maxSmsPerMinute: Int,

    @ColumnInfo(name = "max_sms_per_hour")
    val maxSmsPerHour: Int,

    @ColumnInfo(name = "sim_rotation_enabled")
    val simRotationEnabled: Boolean,

    @ColumnInfo(name = "sim_cooldown_seconds")
    val simCooldownSeconds: Int,

    @ColumnInfo(name = "heartbeat_interval_seconds")
    val heartbeatIntervalSeconds: Int,

    @ColumnInfo(name = "batch_size")
    val batchSize: Int,

    @ColumnInfo(name = "retry_max_attempts")
    val retryMaxAttempts: Int,

    @ColumnInfo(name = "retry_backoff_seconds")
    val retryBackoffSeconds: List<Int>,

    @ColumnInfo(name = "quiet_hours_start")
    val quietHoursStart: Int?,

    @ColumnInfo(name = "quiet_hours_end")
    val quietHoursEnd: Int?,

    @ColumnInfo(name = "auto_reply_enabled")
    val autoReplyEnabled: Boolean,

    @ColumnInfo(name = "incoming_forward_enabled")
    val incomingForwardEnabled: Boolean,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant
) {
    fun toDomain(): DeviceConfig = DeviceConfig(
        maxSmsPerMinute = maxSmsPerMinute,
        maxSmsPerHour = maxSmsPerHour,
        simRotationEnabled = simRotationEnabled,
        simCooldownSeconds = simCooldownSeconds,
        heartbeatIntervalSeconds = heartbeatIntervalSeconds,
        batchSize = batchSize,
        retryMaxAttempts = retryMaxAttempts,
        retryBackoffSeconds = retryBackoffSeconds,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        autoReplyEnabled = autoReplyEnabled,
        incomingForwardEnabled = incomingForwardEnabled
    )

    companion object {
        fun fromDomain(config: DeviceConfig, updatedAt: Instant): DeviceConfigEntity =
            DeviceConfigEntity(
                id = 1,
                maxSmsPerMinute = config.maxSmsPerMinute,
                maxSmsPerHour = config.maxSmsPerHour,
                simRotationEnabled = config.simRotationEnabled,
                simCooldownSeconds = config.simCooldownSeconds,
                heartbeatIntervalSeconds = config.heartbeatIntervalSeconds,
                batchSize = config.batchSize,
                retryMaxAttempts = config.retryMaxAttempts,
                retryBackoffSeconds = config.retryBackoffSeconds,
                quietHoursStart = config.quietHoursStart,
                quietHoursEnd = config.quietHoursEnd,
                autoReplyEnabled = config.autoReplyEnabled,
                incomingForwardEnabled = config.incomingForwardEnabled,
                updatedAt = updatedAt
            )
    }
}
