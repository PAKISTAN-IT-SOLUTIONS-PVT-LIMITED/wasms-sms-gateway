package net.wasms.smsgateway.domain.repository

import kotlinx.coroutines.flow.Flow
import net.wasms.smsgateway.domain.model.AuthToken
import net.wasms.smsgateway.domain.model.Device
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.DeviceHealth
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.model.SimCard

interface DeviceRepository {
    fun observeDeviceStatus(): Flow<DeviceStatus>
    fun observeSimCards(): Flow<List<SimCard>>
    fun observeDeviceConfig(): Flow<DeviceConfig>
    suspend fun registerDevice(registrationCode: String, deviceName: String): AuthToken
    suspend fun refreshToken(): AuthToken
    suspend fun deregisterDevice()
    suspend fun updateFcmToken(token: String)
    suspend fun sendHeartbeat(health: DeviceHealth)
    suspend fun updateSimCards(simCards: List<SimCard>)
    suspend fun fetchConfig(): DeviceConfig
    suspend fun getDevice(): Device?
    suspend fun isRegistered(): Boolean
}
