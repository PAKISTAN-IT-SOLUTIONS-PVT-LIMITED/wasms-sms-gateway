package net.wasms.smsgateway.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import net.wasms.smsgateway.data.local.db.dao.DeliveryReportDao
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import net.wasms.smsgateway.data.local.model.DeliveryReportEntity
import net.wasms.smsgateway.data.local.model.SmsMessageEntity
import net.wasms.smsgateway.data.remote.api.DeviceApi
import net.wasms.smsgateway.data.remote.model.BulkDeliveryReportRequest
import net.wasms.smsgateway.data.remote.model.DeliveryReportItem
import net.wasms.smsgateway.domain.model.MessagePriority
import net.wasms.smsgateway.domain.model.QueueStats
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val api: DeviceApi,
    private val messageDao: SmsMessageDao,
    private val reportDao: DeliveryReportDao
) : MessageRepository {

    companion object {
        /** Maximum number of unreported delivery reports to send in one batch. */
        private const val REPORT_BATCH_SIZE = 100

        /** Maximum number of unreported delivery reports to fetch at once. */
        private const val UNREPORTED_FETCH_LIMIT = 100
    }

    /**
     * Observes aggregated queue statistics for today (UTC).
     * Maps the Room DAO's QueueCountsResult into the domain QueueStats model.
     */
    override fun observeQueueStats(): Flow<QueueStats> {
        val todayStart = todayStartEpochMillis()
        return messageDao.observeQueueCounts(todayStart).map { counts ->
            QueueStats(
                pending = counts.pending,
                sending = counts.sending,
                sentToday = counts.sent,
                deliveredToday = counts.delivered,
                failedToday = counts.failed,
                totalToday = counts.sent + counts.delivered + counts.failed
            )
        }
    }

    /**
     * Observes the count of messages sent in the last 60 minutes.
     * Used for the "messages/hour" stat on the home dashboard.
     *
     * Note: This uses a fixed "since" timestamp computed at subscription time.
     * The Flow will still emit reactively when new messages are sent/delivered,
     * because Room re-queries when the sms_messages table changes.
     */
    override fun observeMessagesPerHour(): Flow<Int> {
        val oneHourAgo = Clock.System.now()
            .minus(1, DateTimeUnit.HOUR, TimeZone.UTC)
            .toEpochMilliseconds()
        return messageDao.observeCountSentSince(oneHourAgo)
    }

    /**
     * Observes messages currently in pending states (created, queued, assigned).
     */
    override fun observePendingMessages(): Flow<List<SmsMessage>> {
        return messageDao.observeByState(SmsState.ASSIGNED_DEVICE).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Observes the most recent messages across all states.
     */
    override fun observeRecentMessages(limit: Int): Flow<List<SmsMessage>> {
        return messageDao.observeRecent(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Returns the next batch of messages assigned to this device, ready for dispatch.
     */
    override suspend fun getNextBatch(batchSize: Int): List<SmsMessage> {
        return messageDao.getNextBatch(batchSize).map { it.toDomain() }
    }

    /**
     * Updates the state of a message in the local database.
     * Optionally attaches an error message for failure states.
     */
    override suspend fun updateState(messageId: String, state: SmsState, errorMessage: String?) {
        messageDao.updateState(
            id = messageId,
            state = state,
            errorMessage = errorMessage,
            updatedAt = Clock.System.now()
        )
        Timber.d("Message %s state updated to %s", messageId, state.value)
    }

    /**
     * Marks a message as dispatched to SmsManager on a specific SIM slot.
     * Also creates a delivery report entry for later server reporting.
     */
    override suspend fun markSent(messageId: String, simSlot: Int) {
        val now = Clock.System.now()
        messageDao.markSent(
            id = messageId,
            simSlot = simSlot,
            sentAt = now
        )

        // Create a delivery report that will be sent to the server
        val report = DeliveryReportEntity(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            simSlot = simSlot,
            resultCode = 0, // RESULT_ERROR_NONE (dispatched successfully)
            errorMessage = null,
            reportedToServer = false,
            createdAt = now
        )
        reportDao.insert(report)

        Timber.d("Message %s marked sent on SIM %d", messageId, simSlot)
    }

    /**
     * Marks a message as delivered. Creates a delivery report for server reporting.
     */
    override suspend fun markDelivered(messageId: String) {
        val now = Clock.System.now()
        messageDao.markDelivered(id = messageId, deliveredAt = now)

        // Get the message to know which SIM slot it was sent on
        val message = messageDao.getById(messageId)
        val report = DeliveryReportEntity(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            simSlot = message?.simSlot ?: 0,
            resultCode = 0,
            errorMessage = null,
            reportedToServer = false,
            createdAt = now
        )
        reportDao.insert(report)

        Timber.d("Message %s marked delivered", messageId)
    }

    /**
     * Marks a message as failed. Increments retry count if the message can be retried.
     * Creates a delivery report for server reporting.
     */
    override suspend fun markFailed(messageId: String, errorMessage: String, canRetry: Boolean) {
        val now = Clock.System.now()
        val state = if (canRetry) SmsState.FAILED_ATTEMPT else SmsState.FAILED_PERMANENT

        messageDao.updateState(
            id = messageId,
            state = state,
            errorMessage = errorMessage,
            updatedAt = now
        )

        if (canRetry) {
            messageDao.incrementRetry(messageId)
        }

        // Create failure delivery report
        val message = messageDao.getById(messageId)
        val report = DeliveryReportEntity(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            simSlot = message?.simSlot ?: 0,
            resultCode = 1, // RESULT_ERROR_GENERIC_FAILURE
            errorMessage = errorMessage,
            reportedToServer = false,
            createdAt = now
        )
        reportDao.insert(report)

        Timber.d(
            "Message %s marked %s: %s (canRetry=%b)",
            messageId, state.value, errorMessage, canRetry
        )
    }

    /**
     * Syncs pending messages from the server into the local Room database.
     *
     * Calls GET /device/messages/pending, maps the DTOs to Room entities,
     * and inserts them (replacing duplicates by server_id).
     *
     * @return the number of new messages inserted
     */
    override suspend fun syncPendingFromServer(): Int {
        val response = api.getPendingMessages()
        val responseData = response.body()?.data

        if (responseData == null) {
            Timber.e("Pending messages fetch failed: null response body")
            return 0
        }

        val messages = responseData.messages
        if (messages.isEmpty()) {
            Timber.d("No pending messages from server")
            return 0
        }

        val now = Clock.System.now()
        val entities = messages.map { dto ->
            SmsMessageEntity(
                id = UUID.randomUUID().toString(),
                serverId = dto.id,
                destination = dto.destination,
                body = dto.body,
                state = SmsState.ASSIGNED_DEVICE,
                priority = mapPriority(dto.priority),
                simSlot = dto.simSlot,
                partCount = dto.maxParts,
                partsDelivered = 0,
                errorMessage = null,
                retryCount = dto.retryCount,
                maxRetries = 3,
                scheduledAt = dto.scheduledAt?.let { parseInstant(it) },
                sentAt = null,
                deliveredAt = null,
                failedAt = null,
                createdAt = now
            )
        }

        messageDao.insertAll(entities)

        Timber.i("Synced %d pending messages from server", entities.size)
        return entities.size
    }

    /**
     * Reports unreported delivery statuses to the server in a bulk request.
     *
     * Fetches unreported delivery reports from Room, sends them to the server
     * via POST /device/messages/reports, and marks them as reported on success.
     *
     * @return the number of reports successfully submitted
     */
    override suspend fun reportDeliveryStatuses(): Int {
        val unreported = reportDao.getUnreported(UNREPORTED_FETCH_LIMIT)

        if (unreported.isEmpty()) {
            Timber.d("No unreported delivery statuses")
            return 0
        }

        // Look up the server_id for each message and build report items
        val reportItems = unreported.mapNotNull { report ->
            val message = messageDao.getById(report.messageId)
            val serverId = message?.serverId ?: return@mapNotNull null

            DeliveryReportItem(
                messageId = serverId,
                status = mapResultCodeToStatus(report.resultCode, message.state),
                carrierStatusCode = report.resultCode,
                simSlot = report.simSlot,
                sentAt = message.sentAt?.toString(),
                deliveredAt = message.deliveredAt?.toString(),
                failureReason = report.errorMessage
            )
        }

        if (reportItems.isEmpty()) {
            Timber.d("No valid reports to submit (missing server IDs)")
            return 0
        }

        val request = BulkDeliveryReportRequest(reports = reportItems)
        val response = api.submitBulkDeliveryReports(request)
        val responseData = response.body()?.data

        // Mark all submitted reports as reported
        val reportedIds = unreported.map { it.id }
        reportDao.markReported(reportedIds)

        val processed = responseData?.processed ?: reportItems.size
        val errors = responseData?.errors?.size ?: 0

        Timber.i(
            "Delivery statuses reported: %d processed, %d errors",
            processed,
            errors
        )

        return processed
    }

    /**
     * Clears completed (terminal state) messages older than the specified number of days.
     */
    override suspend fun clearCompletedOlderThan(daysAgo: Int) {
        val cutoff = Clock.System.now().minus(daysAgo, DateTimeUnit.DAY, TimeZone.UTC)
        messageDao.deleteOlderThan(cutoff)
        reportDao.deleteOlderThan(cutoff)
        Timber.d("Cleared completed messages older than %d days", daysAgo)
    }

    // --- Private helpers ---

    /**
     * Returns the start of today (UTC) in epoch milliseconds for the Room query.
     */
    private fun todayStartEpochMillis(): Long {
        val today = Clock.System.todayIn(TimeZone.UTC)
        val startOfDay = today.atStartOfDayIn(TimeZone.UTC)
        return startOfDay.toEpochMilliseconds()
    }

    /**
     * Maps API priority string to domain MessagePriority.
     */
    private fun mapPriority(priority: String): MessagePriority {
        return when (priority.lowercase()) {
            "urgent" -> MessagePriority.URGENT
            "high", "transactional" -> MessagePriority.TRANSACTIONAL
            "normal", "campaign" -> MessagePriority.CAMPAIGN
            "low", "bulk" -> MessagePriority.BULK
            else -> MessagePriority.CAMPAIGN
        }
    }

    /**
     * Maps Android SmsManager result code and message state to API status string.
     */
    private fun mapResultCodeToStatus(resultCode: Int, state: SmsState): String {
        return when {
            state == SmsState.DELIVERED -> "delivered"
            state == SmsState.EXPIRED -> "expired"
            state == SmsState.REJECTED -> "rejected"
            resultCode == 0 && state == SmsState.DISPATCHED_TO_SIM -> "delivered"
            else -> "failed"
        }
    }

    /**
     * Parses an ISO 8601 instant string, returning null on failure.
     */
    private fun parseInstant(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            Timber.w("Failed to parse instant: %s", value)
            null
        }
    }
}
