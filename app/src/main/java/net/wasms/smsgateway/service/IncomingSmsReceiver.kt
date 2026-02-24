package net.wasms.smsgateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import android.telephony.SubscriptionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import net.wasms.smsgateway.data.local.model.SmsMessageEntity
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.MessagePriority
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * BroadcastReceiver for incoming SMS messages.
 *
 * Listens for `android.provider.Telephony.SMS_RECEIVED` broadcasts. When an SMS
 * arrives, it extracts the sender, body, timestamp, and receiving SIM information.
 * The message is then stored locally for forwarding to the WaSMS server.
 *
 * Handles multipart incoming messages by reassembling parts from the same sender
 * that arrive in the same broadcast.
 *
 * If auto-reply is enabled in the device config, the receiver can queue a reply
 * message for automatic sending.
 */
@AndroidEntryPoint
class IncomingSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var smsMessageDao: SmsMessageDao

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Timber.d("IncomingSmsReceiver: SMS_RECEIVED broadcast")

        val messages = extractMessages(intent)
        if (messages.isEmpty()) {
            Timber.w("IncomingSmsReceiver: No messages extracted from intent")
            return
        }

        // Determine which SIM received the message
        val subscriptionId = extractSubscriptionId(intent)

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                processIncomingMessages(messages, subscriptionId)
            } catch (e: Exception) {
                Timber.e(e, "IncomingSmsReceiver: Error processing incoming messages")
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Extract SMS messages from the broadcast intent.
     *
     * Reassembles multipart messages from the same sender into a single
     * [IncomingSms] object with the concatenated body.
     */
    private fun extractMessages(intent: Intent): List<IncomingSms> {
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return emptyList()
        val format = intent.getStringExtra("format") ?: AndroidSmsMessage.FORMAT_3GPP

        // Group PDU parts by sender address to reassemble multipart messages
        val senderToPartsMap = mutableMapOf<String, MutableList<AndroidSmsMessage>>()

        for (pdu in pdus) {
            val bytes = pdu as? ByteArray ?: continue
            try {
                val smsMessage = AndroidSmsMessage.createFromPdu(bytes, format)
                if (smsMessage != null) {
                    val sender = smsMessage.originatingAddress ?: "unknown"
                    senderToPartsMap.getOrPut(sender) { mutableListOf() }.add(smsMessage)
                }
            } catch (e: Exception) {
                Timber.e(e, "IncomingSmsReceiver: Failed to parse PDU")
            }
        }

        return senderToPartsMap.map { (sender, parts) ->
            // Concatenate all parts from the same sender into one message body
            val fullBody = parts.joinToString("") { it.messageBody ?: "" }
            val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            IncomingSms(
                sender = sender,
                body = fullBody,
                timestamp = Instant.fromEpochMilliseconds(timestamp)
            )
        }
    }

    /**
     * Extract the subscription ID (SIM slot) from the incoming SMS intent.
     *
     * On API 26+ (our minSdk), the subscription ID is available as an intent extra.
     * Falls back to [SubscriptionManager.getDefaultSmsSubscriptionId] if unavailable.
     */
    private fun extractSubscriptionId(intent: Intent): Int {
        // Try the standard extra name
        val subId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )
        } else {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }

        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return subId
        }

        // Fallback: try "subscription" extra used by some OEMs
        val legacySubId = intent.getIntExtra("subscription", -1)
        if (legacySubId != -1) return legacySubId

        // Fallback: try "android.telephony.extra.SUBSCRIPTION_INDEX"
        val telSubId = intent.getIntExtra(
            "android.telephony.extra.SUBSCRIPTION_INDEX", -1
        )
        if (telSubId != -1) return telSubId

        // Last resort: use the default SMS subscription
        return try {
            SubscriptionManager.getDefaultSmsSubscriptionId()
        } catch (e: Exception) {
            Timber.w(e, "IncomingSmsReceiver: Failed to get default SMS subscription")
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    /**
     * Process incoming messages: store for forwarding and handle auto-reply.
     */
    private suspend fun processIncomingMessages(
        messages: List<IncomingSms>,
        subscriptionId: Int
    ) {
        // Load device config to check forwarding and auto-reply settings
        val config = try {
            deviceRepository.observeDeviceConfig().first()
        } catch (e: Exception) {
            Timber.w(e, "IncomingSmsReceiver: Failed to load config, using defaults")
            DeviceConfig.DEFAULT
        }

        for (message in messages) {
            Timber.i(
                "IncomingSmsReceiver: From=${message.sender}, " +
                    "BodyLen=${message.body.length}, SIM=$subscriptionId"
            )

            // Store incoming message for forwarding to server
            if (config.incomingForwardEnabled) {
                storeForForwarding(message, subscriptionId)
            }

            // Auto-reply is handled server-side: the server receives the incoming
            // message, evaluates auto-reply rules, and sends back a message through
            // the normal send queue if a reply is warranted. We don't handle
            // auto-reply logic locally.
        }
    }

    /**
     * Store an incoming SMS in the local database for forwarding to the server.
     *
     * Incoming messages are stored with state [SmsState.CREATED] and will be
     * picked up by the sync worker that reports them to the WaSMS server.
     *
     * We reuse the SmsMessageEntity table with a special convention:
     * - The `destination` field contains the sender's number (for incoming)
     * - The `serverId` is null until synced to the server
     * - The `state` is CREATED (pending server sync)
     *
     * The message ID is prefixed with "in_" to distinguish from outgoing messages.
     */
    private suspend fun storeForForwarding(
        message: IncomingSms,
        subscriptionId: Int
    ) {
        val entity = SmsMessageEntity(
            id = "in_${UUID.randomUUID()}",
            serverId = null,
            destination = message.sender, // For incoming, "destination" = sender
            body = message.body,
            state = SmsState.CREATED,
            priority = MessagePriority.TRANSACTIONAL, // Incoming messages are high priority for sync
            simSlot = resolveSimSlot(subscriptionId),
            partCount = 1,
            partsDelivered = 0,
            errorMessage = null,
            retryCount = 0,
            maxRetries = 3,
            scheduledAt = null,
            sentAt = null,
            deliveredAt = null,
            failedAt = null,
            createdAt = message.timestamp
        )

        try {
            smsMessageDao.insert(entity)
            Timber.d("IncomingSmsReceiver: Stored incoming SMS from ${message.sender} for forwarding")
        } catch (e: Exception) {
            Timber.e(e, "IncomingSmsReceiver: Failed to store incoming SMS")
        }
    }

    /**
     * Resolve a subscription ID to a SIM slot index.
     * Returns null if the subscription ID cannot be resolved.
     */
    private fun resolveSimSlot(subscriptionId: Int): Int? {
        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return null
        // The slot index is typically subscriptionId - 1 or directly from SubscriptionInfo,
        // but since we may not have the SubscriptionInfo here, we store the subscriptionId
        // and let the SimCardDao resolve it later. For now, return null and let the
        // forwarding sync worker fill this in.
        return null
    }

    /**
     * Data class representing an incoming SMS after PDU parsing and multipart assembly.
     */
    private data class IncomingSms(
        val sender: String,
        val body: String,
        val timestamp: Instant
    )
}
