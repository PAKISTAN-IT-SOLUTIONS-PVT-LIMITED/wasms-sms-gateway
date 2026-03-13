package net.wasms.smsgateway.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.model.SmsMessageEntity
import net.wasms.smsgateway.domain.model.SmsState

@Dao
interface SmsMessageDao {

    @Query("SELECT * FROM sms_messages WHERE state = :state ORDER BY priority ASC, created_at ASC")
    fun observeByState(state: SmsState): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SmsMessageEntity>>

    /**
     * Observe today's queue counts aggregated by state category.
     * Returns a [QueueCountsResult] with pending, sending, sent, delivered, and failed totals.
     * "Today" is calculated from midnight UTC of the current day, provided as [todayStart].
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN state IN ('created', 'queued', 'assigned_device') THEN 1 ELSE 0 END), 0) AS pending,
            COALESCE(SUM(CASE WHEN state IN ('dispatched_to_sim', 'sent') THEN 1 ELSE 0 END), 0) AS sending,
            COALESCE(SUM(CASE WHEN state = 'sent' THEN 1 ELSE 0 END), 0) AS sent,
            COALESCE(SUM(CASE WHEN state = 'delivered' THEN 1 ELSE 0 END), 0) AS delivered,
            COALESCE(SUM(CASE WHEN state IN ('failed_attempt', 'failed_permanent', 'expired', 'rejected') THEN 1 ELSE 0 END), 0) AS failed
        FROM sms_messages
        WHERE created_at >= :todayStart
        """
    )
    fun observeQueueCounts(todayStart: Long): Flow<QueueCountsResult>

    @Query(
        """
        SELECT * FROM sms_messages
        WHERE state = 'assigned_device'
        ORDER BY priority ASC, created_at ASC
        LIMIT :batchSize
        """
    )
    suspend fun getNextBatch(batchSize: Int): List<SmsMessageEntity>

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: String): SmsMessageEntity?

    @Query("SELECT * FROM sms_messages WHERE server_id = :serverId")
    suspend fun getByServerId(serverId: String): SmsMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<SmsMessageEntity>)

    @Query(
        """
        UPDATE sms_messages
        SET state = :state, error_message = :errorMessage, failed_at = CASE WHEN :state IN ('failed_attempt', 'failed_permanent') THEN :updatedAt ELSE failed_at END
        WHERE id = :id
        """
    )
    suspend fun updateState(id: String, state: SmsState, errorMessage: String?, updatedAt: Instant)

    @Query("UPDATE sms_messages SET state = 'dispatched_to_sim', sim_slot = :simSlot, sent_at = :sentAt WHERE id = :id")
    suspend fun markSent(id: String, simSlot: Int, sentAt: Instant)

    @Query("UPDATE sms_messages SET state = 'delivered', delivered_at = :deliveredAt WHERE id = :id")
    suspend fun markDelivered(id: String, deliveredAt: Instant)

    @Query("UPDATE sms_messages SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: String)

    @Query("DELETE FROM sms_messages WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Instant)

    @Query("SELECT COUNT(*) FROM sms_messages WHERE state = :state")
    suspend fun countByState(state: SmsState): Int

    /**
     * Observe the count of messages sent (dispatched, delivered) since a given timestamp.
     * Used to calculate messages-per-hour on the home dashboard.
     */
    @Query(
        """
        SELECT COUNT(*) FROM sms_messages
        WHERE state IN ('dispatched_to_sim', 'sent', 'delivered')
        AND sent_at >= :sinceEpochMillis
        """
    )
    fun observeCountSentSince(sinceEpochMillis: Long): Flow<Int>
}

/**
 * Projection class for the queue counts aggregation query.
 * Room maps column aliases to these fields automatically.
 */
data class QueueCountsResult(
    val pending: Int,
    val sending: Int,
    val sent: Int,
    val delivered: Int,
    val failed: Int
)
