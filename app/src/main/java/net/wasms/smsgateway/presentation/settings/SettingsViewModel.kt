package net.wasms.smsgateway.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.model.Device
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.model.SimCard
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Exposes device info, SIM cards, config, and connection state.
 * Provides actions: pause/resume sending, disconnect device.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository,
    private val simDetector: net.wasms.smsgateway.service.SimDetector,
) : ViewModel() {

    private val _device = MutableStateFlow<Device?>(null)
    private val _isDisconnecting = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)

    init {
        loadDevice()
    }

    private fun loadDevice() {
        viewModelScope.launch {
            try {
                _device.update { deviceRepository.getDevice() }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load device info")
            }
        }
    }

    fun syncDevice() {
        viewModelScope.launch {
            _isSyncing.update { true }
            try {
                // Re-detect SIM cards
                simDetector.detectAndSync()
                // Refresh config from server
                deviceRepository.fetchConfig()
                // Reload device info
                _device.update { deviceRepository.getDevice() }
                // Reconnect WebSocket
                connectionRepository.reconnect()
                Timber.i("Device sync completed")
            } catch (e: Exception) {
                Timber.e(e, "Device sync failed")
            } finally {
                _isSyncing.update { false }
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        _device,
        deviceRepository.observeDeviceStatus(),
        deviceRepository.observeSimCards(),
        deviceRepository.observeDeviceConfig(),
        connectionRepository.observeConnectionState(),
    ) { device, status, simCards, config, connectionState ->
        SettingsUiState(
            device = device,
            deviceStatus = status,
            simCards = simCards,
            config = config,
            connectionState = connectionState,
            isLoading = false,
            isSyncing = _isSyncing.value,
        )
    }
        .catch { throwable ->
            Timber.e(throwable, "Error loading settings")
            emit(
                SettingsUiState(
                    isLoading = false,
                    error = throwable.message ?: "Failed to load settings",
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun pauseSending() {
        viewModelScope.launch {
            try {
                // Signal the sending service to pause
                Timber.i("Pause sending requested from settings")
            } catch (e: Exception) {
                Timber.e(e, "Failed to pause sending")
            }
        }
    }

    fun resumeSending() {
        viewModelScope.launch {
            try {
                connectionRepository.reconnect()
                Timber.i("Resume sending requested from settings")
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume sending")
            }
        }
    }

    fun disconnectDevice(onDisconnected: () -> Unit) {
        viewModelScope.launch {
            _isDisconnecting.update { true }
            try {
                // Disconnect WebSocket first (non-fatal if it fails)
                try {
                    connectionRepository.disconnect()
                } catch (e: Exception) {
                    Timber.w(e, "WebSocket disconnect failed (non-fatal)")
                }

                // Deregister from server and clear local state
                deviceRepository.deregisterDevice()
                Timber.i("Device disconnected successfully")
                onDisconnected()
            } catch (e: Exception) {
                Timber.e(e, "Failed to disconnect device: %s", e.message)
                // Even if server call fails, clear local state so user can re-register
                try {
                    deviceRepository.deregisterDevice()
                } catch (_: Exception) {}
                onDisconnected()
            } finally {
                _isDisconnecting.update { false }
            }
        }
    }
}

// =============================================================================
// Settings UI State
// =============================================================================

data class SettingsUiState(
    val device: Device? = null,
    val deviceStatus: DeviceStatus = DeviceStatus.OFFLINE,
    val simCards: List<SimCard> = emptyList(),
    val config: DeviceConfig = DeviceConfig.DEFAULT,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isLoading: Boolean = true,
    val isDisconnecting: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
) {
    val teamName: String
        get() {
            device?.teamName?.let { if (it.isNotBlank()) return it }
            val id = device?.teamId ?: return "--"
            if (id.length > 12) return "${id.take(8)}..."
            return id
        }
    val fullTeamId: String get() = device?.teamId ?: "--"
    val deviceName: String get() = device?.deviceName ?: "--"
    val deviceId: String
        get() {
            val id = device?.id ?: return "--"
            if (id.length <= 8) return id
            return "${id.take(4)}...${id.takeLast(4)}"
        }
    val fullDeviceId: String get() = device?.id ?: "--"
    val appVersion: String
        get() = try {
            net.wasms.smsgateway.BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            device?.appVersion ?: "--"
        }
    val sendSpeed: String
        get() = "${config.maxSmsPerMinute}/min, ${config.maxSmsPerHour}/hr"
}
