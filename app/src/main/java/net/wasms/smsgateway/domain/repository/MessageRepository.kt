package net.wasms.smsgateway.domain.repository

import kotlinx.coroutines.flow.Flow
import net.wasms.smsgateway.domain.model.QueueStats
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState

interface MessageRepository {
    fun observeQueueStats(): Flow<QueueStats>
    fun observeMessagesPerHour(): Flow<Int>
    fun observePendingMessages(): Flow<List<SmsMessage>>
    fun observeRecentMessages(limit: Int = 50): Flow<List<SmsMessage>>
    suspend fun getNextBatch(batchSize: Int): List<SmsMessage>
    suspend fun updateState(messageId: String, state: SmsState, errorMessage: String? = null)
    suspend fun markSent(messageId: String, simSlot: Int)
    suspend fun markDelivered(messageId: String)
    suspend fun markFailed(messageId: String, errorMessage: String, canRetry: Boolean)
    suspend fun syncPendingFromServer(): Int
    suspend fun reportDeliveryStatuses(): Int
    suspend fun clearCompletedOlderThan(daysAgo: Int)
}
