package net.wasms.smsgateway.presentation.queue

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
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Queue screen.
 *
 * Observes recent messages from the repository and allows filtering by state.
 * Layer 3 of Agent 15's hierarchy — intentional exploration of individual messages.
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(QueueFilter.ALL)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<QueueUiState> = combine(
        messageRepository.observeRecentMessages(limit = 100),
        _selectedFilter,
        _isRefreshing,
    ) { messages, filter, isRefreshing ->
        val filtered = when (filter) {
            QueueFilter.ALL -> messages
            QueueFilter.PENDING -> messages.filter { it.state.isPending }
            QueueFilter.SENT -> messages.filter {
                it.state == SmsState.SENT || it.state == SmsState.DELIVERED
            }
            QueueFilter.FAILED -> messages.filter {
                it.state == SmsState.FAILED_PERMANENT ||
                    it.state == SmsState.FAILED_ATTEMPT ||
                    it.state == SmsState.EXPIRED ||
                    it.state == SmsState.REJECTED
            }
        }

        QueueUiState(
            messages = filtered,
            selectedFilter = filter,
            isLoading = false,
            isRefreshing = isRefreshing,
            totalCount = messages.size,
        )
    }
        .catch { throwable ->
            Timber.e(throwable, "Error loading queue messages")
            emit(
                QueueUiState(
                    isLoading = false,
                    error = throwable.message ?: "Failed to load messages",
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = QueueUiState(),
        )

    fun setFilter(filter: QueueFilter) {
        _selectedFilter.update { filter }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.update { true }
            try {
                messageRepository.syncPendingFromServer()
                messageRepository.reportDeliveryStatuses()
            } catch (e: Exception) {
                Timber.e(e, "Queue refresh failed")
            } finally {
                _isRefreshing.update { false }
            }
        }
    }
}

// =============================================================================
// Queue UI State
// =============================================================================

enum class QueueFilter(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    SENT("Sent"),
    FAILED("Failed"),
}

data class QueueUiState(
    val messages: List<SmsMessage> = emptyList(),
    val selectedFilter: QueueFilter = QueueFilter.ALL,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = messages.isEmpty() && !isLoading
}
