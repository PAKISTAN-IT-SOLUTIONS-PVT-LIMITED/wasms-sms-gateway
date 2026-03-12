package net.wasms.smsgateway.domain.model

import kotlinx.datetime.Instant

data class Device(
    val id: String,
    val teamId: String,
    val teamName: String? = null,
    val deviceName: String,
    val deviceUid: String,
    val status: DeviceStatus,
    val appVersion: String,
    val lastSeenAt: Instant?,
    val simCards: List<SimCard>,
    val isActive: Boolean
)
