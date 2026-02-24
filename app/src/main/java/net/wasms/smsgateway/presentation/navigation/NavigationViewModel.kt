package net.wasms.smsgateway.presentation.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import net.wasms.smsgateway.domain.repository.DeviceRepository
import javax.inject.Inject

/**
 * Lightweight ViewModel used by [WaSmsNavHost] to determine the start destination.
 *
 * Checks whether the device is registered (has valid auth tokens).
 * - If registered -> navigate to Home
 * - If not registered -> navigate to Onboarding
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    /**
     * Check if this device has been registered with the WaSMS server.
     * Called once during initial composition to determine start destination.
     */
    suspend fun isDeviceRegistered(): Boolean {
        return try {
            deviceRepository.isRegistered()
        } catch (_: Exception) {
            false
        }
    }
}
