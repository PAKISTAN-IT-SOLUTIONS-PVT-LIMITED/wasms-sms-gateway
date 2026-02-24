package net.wasms.smsgateway.domain.model

import kotlinx.datetime.Instant

data class DeviceHealth(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isWifiConnected: Boolean,
    val signalStrength: Int,
    val networkType: String,
    val queueDepth: Int,
    val uptimeSeconds: Long,
    val timestamp: Instant
) {
    val batteryStatus: BatteryStatus
        get() = when {
            isCharging -> BatteryStatus.CHARGING
            batteryLevel > 50 -> BatteryStatus.GOOD
            batteryLevel > 20 -> BatteryStatus.LOW
            else -> BatteryStatus.CRITICAL
        }

    val signalStatus: SignalStatus
        get() = when {
            signalStrength >= 3 -> SignalStatus.STRONG
            signalStrength >= 2 -> SignalStatus.MODERATE
            signalStrength >= 1 -> SignalStatus.WEAK
            else -> SignalStatus.NONE
        }
}

enum class BatteryStatus { GOOD, LOW, CRITICAL, CHARGING }
enum class SignalStatus { STRONG, MODERATE, WEAK, NONE }
