package net.wasms.smsgateway.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import net.wasms.smsgateway.service.AppLifecycleManager
import net.wasms.smsgateway.service.SmsSenderService
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Home dashboard screen.
 *
 * Combines three reactive flows (queue stats, device status, SIM cards) into a single
 * [HomeUiState] using combine. This keeps the UI layer simple — it only observes one StateFlow.
 *
 * The ViewModel never exposes repository details to the UI. All data is mapped to
 * presentation-friendly formats here.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageRepository: MessageRepository,
    private val deviceRepository: DeviceRepository,
    private val connectionRepository: ConnectionRepository,
    private val appLifecycleManager: AppLifecycleManager,
) : ViewModel() {

    init {
        // Ensures all subsystems (WebSocket, service, workers) are running.
        // Idempotent — safe on every HomeScreen visit, cold start, or process recreation.
        appLifecycleManager.ensureStarted()
    }

    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        messageRepository.observeQueueStats(),
        deviceRepository.observeDeviceStatus(),
        deviceRepository.observeSimCards(),
        connectionRepository.observeConnectionState(),
        combine(_isRefreshing, messageRepository.observeMessagesPerHour()) { a, b -> a to b },
    ) { queueStats, deviceStatus, simCards, connectionState, refreshAndRate ->
        val (isRefreshing, messagesPerHour) = refreshAndRate
        val activeSim = simCards.firstOrNull { it.isActive }
        val queueTotal = queueStats.pending + queueStats.sending

        HomeUiState(
            deviceStatus = deviceStatus,
            sentToday = queueStats.sentToday,
            queueRemaining = queueTotal,
            activeSim = activeSim?.displayName,
            deliveryRate = queueStats.deliveryRate,
            messagesPerHour = messagesPerHour,
            campaignName = null,
            creditBalance = if (queueTotal > 0) "$queueTotal queued" else "0 queued",
            connectionState = connectionState,
            isLoading = false,
            isRefreshing = isRefreshing,
            error = null,
        )
    }
        .catch { throwable ->
            Timber.e(throwable, "Error combining home screen flows")
            emit(
                HomeUiState(
                    isLoading = false,
                    error = throwable.message ?: "An unexpected error occurred",
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    /**
     * Manual pull-to-refresh. Syncs pending messages from the server and
     * reports any outstanding delivery statuses.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.update { true }
            try {
                messageRepository.syncPendingFromServer()
                messageRepository.reportDeliveryStatuses()
            } catch (e: Exception) {
                Timber.e(e, "Refresh failed")
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    /**
     * Pause SMS sending. The device stays connected but stops processing the queue.
     * Sends a PAUSE intent to the foreground SmsSenderService.
     */
    fun pauseSending() {
        SmsSenderService.sendCommand(context, SmsSenderService.ACTION_PAUSE)
        Timber.i("Pause sending requested")
    }

    /**
     * Resume SMS sending after a pause.
     * Sends a RESUME intent to the foreground SmsSenderService.
     */
    fun resumeSending() {
        SmsSenderService.sendCommand(context, SmsSenderService.ACTION_RESUME)
        Timber.i("Resume sending requested")
    }
}
