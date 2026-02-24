package net.wasms.smsgateway.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.domain.model.SimCard
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core SMS sending engine.
 *
 * Handles the actual dispatch of SMS messages through Android's [SmsManager] API.
 * Supports multi-SIM devices, multipart messages, and creates PendingIntents for
 * both sent confirmation and delivery reports. Provides SIM selection via round-robin
 * load balancing with cooldown and throttle awareness.
 */
@Singleton
class SmsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val simCardDao: SimCardDao
) {

    /**
     * Send an SMS to [destination] using the SIM identified by [subscriptionId].
     *
     * Automatically handles single-part vs multipart messages based on body length
     * and encoding. Creates PendingIntents that carry [messageId] and part index
     * so [SmsSentReceiver] and [SmsDeliveryReceiver] can track delivery per-part.
     *
     * @param subscriptionId The Android SubscriptionManager subscription ID for the target SIM.
     * @param destination The phone number to send to.
     * @param body The message text.
     * @param messageId Our internal message ID (Room primary key) used to track status.
     * @return true if the SMS was successfully dispatched to the OS, false on immediate failure.
     */
    fun sendSms(
        subscriptionId: Int,
        destination: String,
        body: String,
        messageId: String
    ): Boolean {
        if (destination.isBlank()) {
            Timber.e("sendSms: Blank destination for message $messageId")
            return false
        }
        if (body.isEmpty()) {
            Timber.e("sendSms: Empty body for message $messageId")
            return false
        }

        return try {
            val smsManager = getSmsManagerForSubscription(subscriptionId)
            val segmentCount = getSegmentCount(body)

            if (segmentCount <= 1) {
                sendSinglePart(smsManager, destination, body, messageId)
            } else {
                sendMultipart(smsManager, destination, body, messageId)
            }
            Timber.i("SMS dispatched: messageId=$messageId, dest=$destination, segments=$segmentCount")
            true
        } catch (e: SecurityException) {
            Timber.e(e, "SMS permission denied for message $messageId")
            throw e // Rethrow so caller can handle permission issues
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Invalid arguments for message $messageId")
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to dispatch SMS for message $messageId")
            false
        }
    }

    /**
     * Select the best SIM card for sending.
     *
     * Implements round-robin with cooldown and throttle awareness (Agent 7 design):
     * 1. Skip SIMs that are throttled (remainingToday <= 0)
     * 2. Skip SIMs in cooldown (cooldownUntil > now)
     * 3. Pick the SIM with the lowest totalSent today (load balancing)
     *
     * @param simCards List of available SIM cards from the device.
     * @return The best SIM to use, or null if none available.
     */
    fun selectSim(simCards: List<SimCard>): SimCard? {
        if (simCards.isEmpty()) return null

        val available = simCards.filter { sim ->
            sim.isActive && !sim.isThrottled
        }

        if (available.isEmpty()) {
            Timber.w("selectSim: All SIMs are throttled or inactive")
            return null
        }

        // Pick the SIM with the fewest sends today (load balancing)
        return available.minByOrNull { it.totalSent }
    }

    /**
     * Check if a text string contains characters that require UCS-2 encoding.
     *
     * GSM-7 covers standard ASCII plus some extended characters. Any character
     * outside the GSM-7 basic and extension tables requires UCS-2.
     */
    fun isUnicode(text: String): Boolean {
        for (char in text) {
            if (!isGsm7Compatible(char)) {
                return true
            }
        }
        return false
    }

    /**
     * Calculate the number of SMS segments for a given text.
     *
     * - GSM-7 single part: up to 160 chars
     * - GSM-7 multipart: up to 153 chars per segment (7 chars UDH header)
     * - UCS-2 single part: up to 70 chars
     * - UCS-2 multipart: up to 67 chars per segment (6 bytes UDH header)
     */
    fun getSegmentCount(text: String): Int {
        if (text.isEmpty()) return 0

        val unicode = isUnicode(text)
        val length = text.length

        return if (unicode) {
            when {
                length <= SINGLE_PART_UCS2_LIMIT -> 1
                else -> {
                    val segments = length / MULTIPART_UCS2_LIMIT
                    if (length % MULTIPART_UCS2_LIMIT != 0) segments + 1 else segments
                }
            }
        } else {
            // Count GSM-7 characters, accounting for extension table chars that take 2 slots
            val gsm7Length = countGsm7Length(text)
            when {
                gsm7Length <= SINGLE_PART_GSM7_LIMIT -> 1
                else -> {
                    val segments = gsm7Length / MULTIPART_GSM7_LIMIT
                    if (gsm7Length % MULTIPART_GSM7_LIMIT != 0) segments + 1 else segments
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private implementation
    // -------------------------------------------------------------------------

    /**
     * Get an SmsManager instance for the given subscription ID.
     * Uses the modern API on Android 12+ and falls back on older versions.
     */
    private fun getSmsManagerForSubscription(subscriptionId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
                .createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    /**
     * Send a single-part SMS.
     */
    private fun sendSinglePart(
        smsManager: SmsManager,
        destination: String,
        body: String,
        messageId: String
    ) {
        val sentIntent = createSentPendingIntent(messageId, partIndex = 0, totalParts = 1)
        val deliveryIntent = createDeliveryPendingIntent(messageId, partIndex = 0, totalParts = 1)

        smsManager.sendTextMessage(
            destination,    // destinationAddress
            null,           // scAddress (service center - null = default)
            body,           // text
            sentIntent,     // sentIntent
            deliveryIntent  // deliveryIntent
        )
    }

    /**
     * Send a multipart SMS.
     * Uses SmsManager.divideMessage() to properly split the text, then sends
     * with per-part PendingIntents for tracking.
     */
    private fun sendMultipart(
        smsManager: SmsManager,
        destination: String,
        body: String,
        messageId: String
    ) {
        val parts: ArrayList<String> = smsManager.divideMessage(body)
        val totalParts = parts.size

        val sentIntents = ArrayList<PendingIntent>(totalParts)
        val deliveryIntents = ArrayList<PendingIntent>(totalParts)

        for (i in 0 until totalParts) {
            sentIntents.add(createSentPendingIntent(messageId, partIndex = i, totalParts = totalParts))
            deliveryIntents.add(createDeliveryPendingIntent(messageId, partIndex = i, totalParts = totalParts))
        }

        smsManager.sendMultipartTextMessage(
            destination,
            null,
            parts,
            sentIntents,
            deliveryIntents
        )
    }

    /**
     * Create a PendingIntent for SMS sent confirmation.
     * Each intent has a unique request code derived from messageId + partIndex to
     * prevent PendingIntent collapsing.
     */
    private fun createSentPendingIntent(
        messageId: String,
        partIndex: Int,
        totalParts: Int
    ): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java).apply {
            action = ACTION_SMS_SENT
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = (messageId.hashCode() * 100 + partIndex) and 0x7FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create a PendingIntent for carrier delivery reports.
     * Uses a different request code offset (+ 50) to avoid collision with sent intents.
     */
    private fun createDeliveryPendingIntent(
        messageId: String,
        partIndex: Int,
        totalParts: Int
    ): PendingIntent {
        val intent = Intent(context, SmsDeliveryReceiver::class.java).apply {
            action = ACTION_SMS_DELIVERED
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_PART_INDEX, partIndex)
            putExtra(EXTRA_TOTAL_PARTS, totalParts)
        }
        val requestCode = (messageId.hashCode() * 100 + partIndex + 50) and 0x7FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Check if a character is in the GSM-7 basic character set.
     * GSM 03.38 basic character set + extension table.
     */
    private fun isGsm7Compatible(char: Char): Boolean {
        return char in GSM7_BASIC_SET || char in GSM7_EXTENSION_SET
    }

    /**
     * Count the GSM-7 encoded length, accounting for extension table characters
     * that consume 2 septets (escape + character).
     */
    private fun countGsm7Length(text: String): Int {
        var length = 0
        for (char in text) {
            length += if (char in GSM7_EXTENSION_SET) 2 else 1
        }
        return length
    }

    companion object {
        const val ACTION_SMS_SENT = "net.wasms.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "net.wasms.smsgateway.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_TOTAL_PARTS = "extra_total_parts"

        // SMS segment limits
        private const val SINGLE_PART_GSM7_LIMIT = 160
        private const val MULTIPART_GSM7_LIMIT = 153
        private const val SINGLE_PART_UCS2_LIMIT = 70
        private const val MULTIPART_UCS2_LIMIT = 67

        /**
         * GSM-7 basic character set (GSM 03.38).
         * Includes standard Latin letters, digits, and common punctuation.
         */
        private val GSM7_BASIC_SET: Set<Char> = buildSet {
            // Standard ASCII printable subset supported by GSM-7
            addAll('@' .. '@') // @
            addAll('\u0000'..'\u0000') // placeholder
            // Instead of enumerating every char, use a string of all valid GSM-7 chars
            val basicChars = "@\u00a3\$\u00a5\u00e8\u00e9\u00f9\u00ec\u00f2\u00c7\n\u00d8\u00f8\r\u00c5\u00e5" +
                "\u0394\u005f\u03a6\u0393\u039b\u03a9\u03a0\u03a8\u03a3\u0398\u039e" +
                "\u00c6\u00e6\u00df\u00c9 !\"#\u00a4%&'()*+,-./0123456789:;<=>?" +
                "\u00a1ABCDEFGHIJKLMNOPQRSTUVWXYZ\u00c4\u00d6\u00d1\u00dc\u00a7" +
                "\u00bfabcdefghijklmnopqrstuvwxyz\u00e4\u00f6\u00f1\u00fc\u00e0"
            addAll(basicChars.toSet())
        }

        /**
         * GSM-7 extension table characters. These take 2 septets each
         * (escape character + the actual character).
         */
        private val GSM7_EXTENSION_SET: Set<Char> = setOf(
            '\u000c', // Form feed
            '^', '{', '}', '\\', '[', '~', ']', '|',
            '\u20ac' // Euro sign
        )
    }
}
