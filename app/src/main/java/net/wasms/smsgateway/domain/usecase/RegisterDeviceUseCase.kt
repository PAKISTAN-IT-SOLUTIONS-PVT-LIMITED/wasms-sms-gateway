package net.wasms.smsgateway.domain.usecase

import net.wasms.smsgateway.domain.model.AuthToken
import net.wasms.smsgateway.domain.repository.DeviceRepository
import javax.inject.Inject

/**
 * Registers the device with the server using a QR code registration code.
 * Called during onboarding after QR scan.
 */
class RegisterDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository
) {
    sealed class Result {
        data class Success(val token: AuthToken) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(registrationCode: String, deviceName: String): Result {
        return try {
            val token = deviceRepository.registerDevice(registrationCode, deviceName)
            Result.Success(token)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Registration failed")
        }
    }
}
