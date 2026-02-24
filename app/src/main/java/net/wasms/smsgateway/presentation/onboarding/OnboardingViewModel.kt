package net.wasms.smsgateway.presentation.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.service.AppLifecycleManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Manages the 4-step onboarding flow.
 *
 * Step progression uses "Endowed Progress" (Agent 15): displayed as "Step 2 of 5"
 * even though step 1 ("account creation") already happened on the web dashboard.
 * This makes users feel they are further along than they are, reducing abandonment.
 *
 * Steps:
 * 1. Welcome + Scan QR (or manual token entry)
 * 2. QR Scanner / manual entry
 * 3. Permissions (SMS, Phone, Notifications) with pre-framing
 * 4. Success celebration
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val appLifecycleManager: AppLifecycleManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Attempt to register the device with the given code (from QR scan or manual entry).
     */
    fun registerDevice(code: String) {
        if (code.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a registration code") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true, error = null) }
            try {
                val deviceName = android.os.Build.MODEL
                deviceRepository.registerDevice(
                    registrationCode = code,
                    deviceName = deviceName,
                )
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        registrationCode = code,
                        currentStep = OnboardingStep.PERMISSIONS,
                    )
                }
                Timber.i("Device registered successfully")
            } catch (e: Exception) {
                Timber.e(e, "Device registration failed")
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        error = e.message ?: "Registration failed. Please try again.",
                    )
                }
            }
        }
    }

    /**
     * Move from the welcome screen to the QR scanner step.
     */
    fun goToScanner() {
        _uiState.update { it.copy(currentStep = OnboardingStep.SCANNER) }
    }

    /**
     * Check which permissions are already granted and update state accordingly.
     */
    fun checkPermissions() {
        val smsGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val phoneGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val notificationGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required pre-Android 13
        }

        _uiState.update {
            it.copy(
                smsPermissionGranted = smsGranted,
                phonePermissionGranted = phoneGranted,
                notificationPermissionGranted = notificationGranted,
            )
        }
    }

    /**
     * Called after the permission request results come back from the system.
     */
    fun onPermissionsResult(permissions: Map<String, Boolean>) {
        _uiState.update { current ->
            current.copy(
                smsPermissionGranted = permissions[Manifest.permission.SEND_SMS]
                    ?: current.smsPermissionGranted,
                phonePermissionGranted = permissions[Manifest.permission.READ_PHONE_STATE]
                    ?: current.phonePermissionGranted,
                notificationPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    permissions[Manifest.permission.POST_NOTIFICATIONS]
                        ?: current.notificationPermissionGranted
                } else {
                    true
                },
            )
        }
    }

    /**
     * Move to the permissions step (called when user has all required permissions).
     */
    fun onPermissionsComplete() {
        _uiState.update { it.copy(currentStep = OnboardingStep.SUCCESS) }
    }

    /**
     * Complete onboarding and kick off all subsystems.
     *
     * Registration state IS the onboarding state — [DeviceRepository.isRegistered]
     * returns true after successful registration, which NavHost uses for start destination.
     *
     * [AppLifecycleManager.ensureStarted] triggers the full startup sequence:
     * SIM detection → WebSocket connect → foreground service → workers.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                appLifecycleManager.ensureStarted()
                Timber.i("Onboarding completed, subsystems starting")
            } catch (e: Exception) {
                Timber.e(e, "Error completing onboarding")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// =============================================================================
// Onboarding State
// =============================================================================

enum class OnboardingStep {
    WELCOME,
    SCANNER,
    PERMISSIONS,
    SUCCESS,
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val registrationCode: String? = null,
    val isRegistering: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val phonePermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val error: String? = null,
) {
    /** Endowed Progress: display step as "Step X of 5" (step 1 = web signup already done). */
    val displayStep: Int
        get() = when (currentStep) {
            OnboardingStep.WELCOME -> 2
            OnboardingStep.SCANNER -> 3
            OnboardingStep.PERMISSIONS -> 4
            OnboardingStep.SUCCESS -> 5
        }

    val displayTotalSteps: Int get() = 5

    val allPermissionsGranted: Boolean
        get() = smsPermissionGranted && phonePermissionGranted && notificationPermissionGranted
}
