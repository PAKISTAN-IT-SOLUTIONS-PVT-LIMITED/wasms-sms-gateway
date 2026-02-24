package net.wasms.smsgateway.domain.model

data class DeviceConfig(
    val maxSmsPerMinute: Int,
    val maxSmsPerHour: Int,
    val simRotationEnabled: Boolean,
    val simCooldownSeconds: Int,
    val heartbeatIntervalSeconds: Int,
    val batchSize: Int,
    val retryMaxAttempts: Int,
    val retryBackoffSeconds: List<Int>,
    val quietHoursStart: Int?,
    val quietHoursEnd: Int?,
    val autoReplyEnabled: Boolean,
    val incomingForwardEnabled: Boolean
) {
    companion object {
        val DEFAULT = DeviceConfig(
            maxSmsPerMinute = 1,
            maxSmsPerHour = 50,
            simRotationEnabled = true,
            simCooldownSeconds = 30,
            heartbeatIntervalSeconds = 60,
            batchSize = 10,
            retryMaxAttempts = 3,
            retryBackoffSeconds = listOf(10, 60, 300),
            quietHoursStart = null,
            quietHoursEnd = null,
            autoReplyEnabled = false,
            incomingForwardEnabled = true
        )
    }
}
