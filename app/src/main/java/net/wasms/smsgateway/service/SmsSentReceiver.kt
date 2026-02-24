package net.wasms.smsgateway.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.MessageRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver for SMS sent confirmation.
 *
 * Receives the PendingIntent created by [SmsEngine] when Android confirms
 * (or fails to confirm) that a message was handed off to the carrier.
 * Updates the message state in the local database accordingly.
 *
 * For multipart messages, each part fires a separate broadcast. The receiver
 * tracks which parts have been confirmed and only finalizes the message state
 * when all parts are accounted for.
 */
@AndroidEntryPoint
class SmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var messageRepository: MessageRepository

    /**
     * In-memory tracker for multipart message part confirmations.
     * Key: messageId, Value: set of confirmed part indices.
     *
     * Thread-safe via synchronized access. Entries are cleaned up once all
     * parts are confirmed or a failure occurs.
     */
    private companion object {
        private val partTracker = mutableMapOf<String, MutableSet<Int>>()
        private val lock = Any()

        /**
         * Record a successfully sent part. Returns true if all parts are now confirmed.
         */
        fun recordPartSent(messageId: String, partIndex: Int, totalParts: Int): Boolean {
            synchronized(lock) {
                val parts = partTracker.getOrPut(messageId) { mutableSetOf() }
                parts.add(partIndex)
                return if (parts.size >= totalParts) {
                    partTracker.remove(messageId)
                    true
                } else {
                    false
                }
            }
        }

        /**
         * Clear tracking for a message (on failure).
         */
        fun clearTracking(messageId: String) {
            synchronized(lock) {
                partTracker.remove(messageId)
            }
        }
    }

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(SmsEngine.EXTRA_MESSAGE_ID)
        val partIndex = intent.getIntExtra(SmsEngine.EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(SmsEngine.EXTRA_TOTAL_PARTS, 1)

        if (messageId.isNullOrBlank()) {
            Timber.e("SmsSentReceiver: Received broadcast with no messageId")
            return
        }

        Timber.d(
            "SmsSentReceiver: messageId=$messageId, part=$partIndex/$totalParts, resultCode=$resultCode"
        )

        // Use goAsync() to hold the wake lock while we do async work
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                handleSentResult(messageId, partIndex, totalParts, resultCode)
            } catch (e: Exception) {
                Timber.e(e, "SmsSentReceiver: Error handling sent result for $messageId")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleSentResult(
        messageId: String,
        partIndex: Int,
        totalParts: Int,
        resultCode: Int
    ) {
        if (resultCode == Activity.RESULT_OK) {
            // This part was sent successfully
            val allPartsSent = recordPartSent(messageId, partIndex, totalParts)

            if (allPartsSent) {
                // All parts confirmed sent -- update to SENT state
                Timber.i("SmsSentReceiver: All $totalParts parts sent for message $messageId")
                messageRepository.updateState(messageId, SmsState.SENT)
            } else {
                Timber.d(
                    "SmsSentReceiver: Part $partIndex/$totalParts sent for message $messageId"
                )
            }
        } else {
            // Part failed -- mark message as failed
            val errorMessage = mapResultCodeToMessage(resultCode)
            val canRetry = isRetryable(resultCode)

            Timber.w(
                "SmsSentReceiver: Part $partIndex FAILED for message $messageId: $errorMessage (retryable=$canRetry)"
            )

            // Clean up part tracking since we're failing the whole message
            clearTracking(messageId)

            messageRepository.markFailed(
                messageId = messageId,
                errorMessage = errorMessage,
                canRetry = canRetry
            )
        }
    }

    /**
     * Map Android SMS result codes to human-readable error messages.
     */
    private fun mapResultCodeToMessage(resultCode: Int): String {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                "Generic failure: The SMS was not sent. Check SIM or carrier."
            SmsManager.RESULT_ERROR_NO_SERVICE ->
                "No cellular service available. Check signal and SIM."
            SmsManager.RESULT_ERROR_NULL_PDU ->
                "Null PDU error: Internal messaging error."
            SmsManager.RESULT_ERROR_RADIO_OFF ->
                "Radio/airplane mode is on. Cannot send SMS."
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED ->
                "SMS sending limit exceeded. Carrier rate limit hit."
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED ->
                "Short code not allowed on this device/SIM."
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED ->
                "Short code sending is permanently blocked."
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE ->
                "Fixed Dialing Number check failed. Number not in FDN list."
            SmsManager.RESULT_RADIO_NOT_AVAILABLE ->
                "Radio not available. Modem may be busy or powered down."
            SmsManager.RESULT_NETWORK_REJECT ->
                "Network rejected the message."
            SmsManager.RESULT_INVALID_ARGUMENTS ->
                "Invalid arguments passed to SMS API."
            SmsManager.RESULT_INVALID_STATE ->
                "SIM/modem in invalid state for sending."
            SmsManager.RESULT_NO_MEMORY ->
                "No memory available on SIM or device for SMS."
            SmsManager.RESULT_INVALID_SMS_FORMAT ->
                "Invalid SMS format."
            SmsManager.RESULT_SYSTEM_ERROR ->
                "Android system error while sending SMS."
            SmsManager.RESULT_MODEM_ERROR ->
                "Modem error while sending SMS."
            SmsManager.RESULT_NETWORK_ERROR ->
                "Network error while sending SMS."
            SmsManager.RESULT_ENCODING_ERROR ->
                "Encoding error: Message could not be encoded."
            SmsManager.RESULT_INVALID_SMSC_ADDRESS ->
                "Invalid SMS center address."
            SmsManager.RESULT_OPERATION_NOT_ALLOWED ->
                "SMS operation not allowed."
            SmsManager.RESULT_INTERNAL_ERROR ->
                "Internal error in SMS subsystem."
            SmsManager.RESULT_NO_RESOURCES ->
                "No resources available for SMS sending."
            SmsManager.RESULT_CANCELLED ->
                "SMS sending was cancelled."
            SmsManager.RESULT_REQUEST_NOT_SUPPORTED ->
                "SMS request not supported on this device."
            SmsManager.RESULT_NO_BLUETOOTH_SERVICE ->
                "No Bluetooth MAP service for SMS relay."
            SmsManager.RESULT_INVALID_BLUETOOTH_ADDRESS ->
                "Invalid Bluetooth address for SMS relay."
            SmsManager.RESULT_BLUETOOTH_DISCONNECTED ->
                "Bluetooth disconnected during SMS relay."
            SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING ->
                "Unexpected event stopped SMS sending."
            SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY ->
                "SMS blocked: emergency call in progress."
            SmsManager.RESULT_SMS_SEND_RETRY_FAILED ->
                "SMS send retry failed."
            SmsManager.RESULT_REMOTE_EXCEPTION ->
                "Remote exception in SMS service."
            SmsManager.RESULT_NO_DEFAULT_SMS_APP ->
                "No default SMS app configured."
            SmsManager.RESULT_USER_NOT_ALLOWED ->
                "User profile not allowed to send SMS."
            SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE ->
                "RIL: Radio not available."
            SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY ->
                "RIL: SMS send failed, retryable."
            else ->
                "Unknown error (code: $resultCode)"
        }
    }

    /**
     * Determine if a given result code represents a transient error that
     * can be retried, versus a permanent failure.
     */
    private fun isRetryable(resultCode: Int): Boolean {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            SmsManager.RESULT_ERROR_NO_SERVICE,
            SmsManager.RESULT_ERROR_RADIO_OFF,
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED,
            SmsManager.RESULT_RADIO_NOT_AVAILABLE,
            SmsManager.RESULT_NETWORK_REJECT,
            SmsManager.RESULT_NETWORK_ERROR,
            SmsManager.RESULT_MODEM_ERROR,
            SmsManager.RESULT_SYSTEM_ERROR,
            SmsManager.RESULT_NO_RESOURCES,
            SmsManager.RESULT_INTERNAL_ERROR,
            SmsManager.RESULT_RIL_RADIO_NOT_AVAILABLE,
            SmsManager.RESULT_RIL_SMS_SEND_FAIL_RETRY,
            SmsManager.RESULT_SMS_SEND_RETRY_FAILED,
            SmsManager.RESULT_UNEXPECTED_EVENT_STOP_SENDING,
            SmsManager.RESULT_SMS_BLOCKED_DURING_EMERGENCY -> true

            // Non-retryable: permanent configuration or device issues
            SmsManager.RESULT_ERROR_NULL_PDU,
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE,
            SmsManager.RESULT_INVALID_ARGUMENTS,
            SmsManager.RESULT_INVALID_SMS_FORMAT,
            SmsManager.RESULT_ENCODING_ERROR,
            SmsManager.RESULT_INVALID_SMSC_ADDRESS,
            SmsManager.RESULT_OPERATION_NOT_ALLOWED,
            SmsManager.RESULT_REQUEST_NOT_SUPPORTED,
            SmsManager.RESULT_CANCELLED,
            SmsManager.RESULT_NO_DEFAULT_SMS_APP,
            SmsManager.RESULT_USER_NOT_ALLOWED -> false

            else -> true // Unknown errors default to retryable
        }
    }
}
