package net.wasms.smsgateway.domain.usecase

import net.wasms.smsgateway.domain.repository.ConnectionRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Syncs messages: pulls pending from server + pushes delivery reports.
 * Called by SyncWorker periodically and by FCM wakeup handler.
 */
class SyncMessagesUseCase @Inject constructor(
    private val messageRepository: MessageRepository,
    private val connectionRepository: ConnectionRepository
) {
    data class SyncResult(
        val messagesPulled: Int,
        val reportsSubmitted: Int
    )

    suspend operator fun invoke(): SyncResult {
        // Ensure WebSocket is connected
        if (!connectionRepository.isConnected()) {
            try {
                connectionRepository.reconnect()
            } catch (e: Exception) {
                Timber.w(e, "WebSocket reconnect failed during sync, continuing with HTTP")
            }
        }

        val pulled = try {
            messageRepository.syncPendingFromServer()
        } catch (e: Exception) {
            Timber.e(e, "Failed to pull pending messages")
            0
        }

        val reported = try {
            messageRepository.reportDeliveryStatuses()
        } catch (e: Exception) {
            Timber.e(e, "Failed to report delivery statuses")
            0
        }

        return SyncResult(pulled, reported)
    }
}
