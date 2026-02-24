package net.wasms.smsgateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage as AndroidSmsMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import net.wasms.smsgateway.data.local.db.dao.DeliveryReportDao
import net.wasms.smsgateway.data.local.model.DeliveryReportEntity
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * BroadcastReceiver for carrier delivery reports (DLR).
 *
 * Receives the delivery PendingIntent created by [SmsEngine] when the carrier
 * reports that a message has been delivered to the recipient's handset (or that
 * delivery failed). Extracts the delivery status from the SMS PDU in the intent
 * extras and updates the local database accordingly.
 *
 * Also creates [DeliveryReportEntity] records that will be synced back to the
 * WaSMS server for analytics and delivery rate tracking.
 */
@AndroidEntryPoint
class SmsDeliveryReceiver : BroadcastReceiver() {

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var deliveryReportDao: DeliveryReportDao

    /**
     * In-memory tracker for multipart delivery confirmations.
     * Key: messageId, Value: set of delivered part indices.
     */
    private companion object {
        private val deliveryTracker = mutableMapOf<String, MutableSet<Int>>()
        private val lock = Any()

        fun recordPartDelivered(messageId: String, partIndex: Int, totalParts: Int): Boolean {
            synchronized(lock) {
                val parts = deliveryTracker.getOrPut(messageId) { mutableSetOf() }
                parts.add(partIndex)
                return if (parts.size >= totalParts) {
                    deliveryTracker.remove(messageId)
                    true
                } else {
                    false
                }
            }
        }

        fun clearTracking(messageId: String) {
            synchronized(lock) {
                deliveryTracker.remove(messageId)
            }
        }
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsEngine.EXTRA_MESSAGE_ID)
        val partIndex = intent.getIntExtra(SmsEngine.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsEngine.EXTRA_TOTAL_PARTS, 1)

        if (messageId.isNullOrBlank()) {
            Timber.e("SmsDeliveryReceiver: Received broadcast with no messageId")
            return
        }

        Timber.d("SmsDeliveryReceiver: messageId=$messageId, part=$partIndex/$totalParts")

        // Extract delivery status from the PDU
        val deliveryStatus = extractDeliveryStatus(intent)

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                handleDeliveryReport(messageId, partIndex, totalParts, deliveryStatus)
            } catch (e: Exception) {
                Timber.e(e, "SmsDeliveryReceiver: Error handling delivery for $messageId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleDeliveryReport(
        messageId: String,
        partIndex: Int,
        totalParts: Int,
        deliveryStatus: DeliveryStatus
    ) {
        // Create a delivery report entity for server sync
        val reportEntity = DeliveryReportEntity(
            id = UUID.randomUUID().toString(),
            messageId = messageId,
            simSlot = 0, // Will be enriched if needed
            resultCode = deliveryStatus.rawStatus,
            errorMessage = if (deliveryStatus == DeliveryStatus.FAILED) {
                "Carrier reported delivery failure"
            } else null,
            reportedToServer = false,
            createdAt = Clock.System.now()
        )

        try {
            deliveryReportDao.insert(reportEntity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert delivery report for message $messageId")
        }

        when (deliveryStatus) {
            DeliveryStatus.COMPLETE -> {
                val allDelivered = recordPartDelivered(messageId, partIndex, totalParts)

                if (allDelivered) {
                    Timber.i("SmsDeliveryReceiver: All $totalParts parts delivered for $messageId")
                    try {
                        messageRepository.markDelivered(messageId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to markDelivered for message $messageId")
                    }
                } else {
                    Timber.d(
                        "SmsDeliveryReceiver: Part $partIndex/$totalParts delivered for $messageId"
                    )
                }
            }

            DeliveryStatus.FAILED -> {
                Timber.w("SmsDeliveryReceiver: Delivery FAILED for message $messageId, part $partIndex")
                clearTracking(messageId)
                try {
                    messageRepository.markFailed(
                        messageId = messageId,
                        errorMessage = "Carrier reported delivery failure",
                        canRetry = false // Carrier-level failure is typically permanent
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to markFailed for message $messageId")
                }
            }

            DeliveryStatus.PENDING -> {
                // Delivery is still pending at the carrier -- do nothing, wait for final status
                Timber.d("SmsDeliveryReceiver: Delivery pending for $messageId, part $partIndex")
            }

            DeliveryStatus.UNKNOWN -> {
                Timber.w("SmsDeliveryReceiver: Unknown delivery status for $messageId, part $partIndex")
                // Treat unknown as pending -- don't change state, wait for another callback
            }
        }
    }

    /**
     * Extract the delivery status from the intent PDU extras.
     *
     * The delivery intent contains the status report PDU from the carrier.
     * We parse it using Android's SmsMessage.createFromPdu() to get the status.
     */
    private fun extractDeliveryStatus(intent: Intent): DeliveryStatus {
        val format = intent.getStringExtra("format") ?: AndroidSmsMessage.FORMAT_3GPP
        val pdus = intent.extras?.get("pdus") as? Array<*>

        if (pdus.isNullOrEmpty()) {
            Timber.w("SmsDeliveryReceiver: No PDUs in delivery intent")
            return DeliveryStatus.UNKNOWN
        }

        return try {
            val pdu = pdus[0] as? ByteArray ?: return DeliveryStatus.UNKNOWN
            val smsMessage = AndroidSmsMessage.createFromPdu(pdu, format)

            if (smsMessage == null) {
                Timber.w("SmsDeliveryReceiver: Failed to create SmsMessage from PDU")
                return DeliveryStatus.UNKNOWN
            }

            mapCarrierStatus(smsMessage.status)
        } catch (e: Exception) {
            Timber.e(e, "SmsDeliveryReceiver: Error parsing delivery PDU")
            DeliveryStatus.UNKNOWN
        }
    }

    /**
     * Map the carrier's TP-Status value to our delivery status enum.
     *
     * TP-Status values (3GPP TS 23.040):
     * - 0x00: Short message received by the SME (STATUS_COMPLETE)
     * - 0x01: Short message forwarded to the SME but unable to confirm delivery
     * - 0x02: Short message replaced by the SC
     * - 0x20-0x3F: Temporary failure, still trying
     * - 0x40-0x7F: Permanent failure
     */
    private fun mapCarrierStatus(status: Int): DeliveryStatus {
        return when {
            status == AndroidSmsMessage.STATUS_COMPLETE -> DeliveryStatus.COMPLETE
            status == AndroidSmsMessage.STATUS_FAILED -> DeliveryStatus.FAILED
            status == AndroidSmsMessage.STATUS_PENDING -> DeliveryStatus.PENDING
            status in 0x00..0x03 -> DeliveryStatus.COMPLETE
            status in 0x20..0x3F -> DeliveryStatus.PENDING  // Temporary error, still trying
            status in 0x40..0x7F -> DeliveryStatus.FAILED   // Permanent failure
            else -> DeliveryStatus.UNKNOWN
        }
    }

    /**
     * Internal delivery status representation.
     */
    private enum class DeliveryStatus(val rawStatus: Int) {
        COMPLETE(0),
        PENDING(1),
        FAILED(2),
        UNKNOWN(-1)
    }
}
