package net.wasms.smsgateway.domain.model

enum class DeviceStatus(val label: String) {
    ONLINE("Online"),
    SENDING("Sending"),
    IDLE("Idle"),
    PAUSED("Paused"),
    OFFLINE("Offline"),
    ERROR("Error");

    val isActive: Boolean get() = this == ONLINE || this == SENDING || this == IDLE
}
