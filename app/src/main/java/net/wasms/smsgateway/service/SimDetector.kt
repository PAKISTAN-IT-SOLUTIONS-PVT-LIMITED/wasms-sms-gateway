package net.wasms.smsgateway.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.wasms.smsgateway.domain.model.SimCard
import net.wasms.smsgateway.domain.repository.DeviceRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects physical SIM cards in the device using Android SubscriptionManager.
 * Maps them to domain SimCard objects and syncs with the server.
 *
 * Called:
 * - After onboarding permissions are granted
 * - On app startup (AppLifecycleManager)
 * - When SIM state changes (future: broadcast receiver)
 */
@Singleton
class SimDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository
) {

    /**
     * Detects active SIM cards on the device, maps them to [SimCard] domain objects,
     * and syncs the result with the server via [DeviceRepository].
     *
     * @return List of detected SIM cards, or emptyList() if permission is missing or detection fails.
     */
    suspend fun detectAndSync(): List<SimCard> {
        try {
            // Step 1: Check READ_PHONE_STATE permission
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.w("SimDetector: READ_PHONE_STATE permission not granted, cannot detect SIMs")
                return emptyList()
            }

            // Step 2: Get SubscriptionManager
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            if (subscriptionManager == null) {
                Timber.w("SimDetector: SubscriptionManager not available on this device")
                return emptyList()
            }

            // Step 3: Get active subscription list
            val subscriptionInfoList: List<SubscriptionInfo> = try {
                subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            } catch (e: SecurityException) {
                Timber.e(e, "SimDetector: SecurityException when reading active subscriptions")
                return emptyList()
            }

            if (subscriptionInfoList.isEmpty()) {
                Timber.i("SimDetector: No active SIM cards detected")
                return emptyList()
            }

            Timber.d("SimDetector: Found %d active SIM card(s)", subscriptionInfoList.size)

            // Step 4: Map SubscriptionInfo to SimCard domain model
            val simCards = subscriptionInfoList.map { info ->
                SimCard(
                    id = "sim_${info.subscriptionId}",
                    subscriptionId = info.subscriptionId,
                    slot = info.simSlotIndex,
                    carrierName = info.carrierName?.toString()
                        ?: info.displayName?.toString()
                        ?: "SIM ${info.simSlotIndex + 1}",
                    phoneNumber = info.number,
                    iccId = info.iccId,
                    countryCode = info.countryIso?.uppercase(),
                    isActive = true,
                    dailyLimit = 50,
                    totalSent = 0,
                    totalFailed = 0,
                    healthScore = 1.0f
                )
            }

            // Step 5: Sync with server via repository
            try {
                deviceRepository.updateSimCards(simCards)
                Timber.i("SimDetector: Successfully synced %d SIM card(s) with server", simCards.size)
            } catch (e: Exception) {
                Timber.e(e, "SimDetector: Failed to sync SIM cards with server")
                // Still return the detected SIMs even if sync fails
            }

            // Step 6: Return the list
            return simCards
        } catch (e: SecurityException) {
            Timber.e(e, "SimDetector: SecurityException during SIM detection")
            return emptyList()
        } catch (e: Exception) {
            Timber.e(e, "SimDetector: Unexpected error during SIM detection")
            return emptyList()
        }
    }

    /**
     * Quick non-suspend check that returns the number of active SIM cards
     * without triggering a server sync.
     *
     * @return Number of active SIM cards, or 0 if permission is missing or detection fails.
     */
    fun getSimCount(): Int {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.w("SimDetector: READ_PHONE_STATE permission not granted for getSimCount")
                return 0
            }

            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
            if (subscriptionManager == null) {
                Timber.w("SimDetector: SubscriptionManager not available for getSimCount")
                return 0
            }

            val count = try {
                subscriptionManager.activeSubscriptionInfoList?.size ?: 0
            } catch (e: SecurityException) {
                Timber.e(e, "SimDetector: SecurityException in getSimCount")
                0
            }

            Timber.d("SimDetector: getSimCount = %d", count)
            return count
        } catch (e: Exception) {
            Timber.e(e, "SimDetector: Unexpected error in getSimCount")
            return 0
        }
    }
}
