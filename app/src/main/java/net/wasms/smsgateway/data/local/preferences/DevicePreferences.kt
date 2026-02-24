package net.wasms.smsgateway.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.datetime.Instant
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores device metadata and onboarding state using EncryptedSharedPreferences.
 *
 * This includes the unique device UID (generated once and persisted), device name,
 * sync cursors, and FCM token. All values are encrypted at rest.
 */
@Singleton
class DevicePreferences @Inject constructor(
    context: Context
) {
    companion object {
        private const val PREFS_FILE = "wasms_device_prefs"
        private const val KEY_DEVICE_UID = "device_uid"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_LAST_SYNC_CURSOR = "last_sync_cursor"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_FCM_TOKEN = "fcm_token"
    }

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create EncryptedSharedPreferences for device prefs")
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    // --- Properties ---

    /**
     * Unique device identifier generated on first access.
     * Persists across app restarts. Used as the device fingerprint component.
     */
    var deviceUid: String
        get() {
            val existing = prefs.getString(KEY_DEVICE_UID, null)
            if (existing != null) return existing
            return generateDeviceUid()
        }
        private set(value) = prefs.edit().putString(KEY_DEVICE_UID, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var lastSyncCursor: String?
        get() = prefs.getString(KEY_LAST_SYNC_CURSOR, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_CURSOR, value).apply()

    var lastSyncAt: Instant?
        get() {
            val millis = prefs.getLong(KEY_LAST_SYNC_AT, -1L)
            return if (millis > 0) Instant.fromEpochMilliseconds(millis) else null
        }
        set(value) {
            prefs.edit().putLong(
                KEY_LAST_SYNC_AT,
                value?.toEpochMilliseconds() ?: -1L
            ).apply()
        }

    var fcmToken: String?
        get() = prefs.getString(KEY_FCM_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    // --- Methods ---

    /**
     * Generates a new UUID-based device UID, stores it, and returns it.
     * Only called once per installation. Subsequent calls to [deviceUid]
     * return the stored value.
     */
    fun generateDeviceUid(): String {
        val uid = UUID.randomUUID().toString()
        deviceUid = uid
        Timber.d("Generated new device UID: %s", uid)
        return uid
    }

    /**
     * Clears all device preferences. Used during device deregistration
     * to reset the app to a clean state. Note: deviceUid is intentionally
     * NOT cleared to maintain device identity across re-registrations.
     */
    fun clear() {
        val preservedUid = prefs.getString(KEY_DEVICE_UID, null)
        prefs.edit().clear().apply()
        // Preserve the device UID across deregistration
        if (preservedUid != null) {
            prefs.edit().putString(KEY_DEVICE_UID, preservedUid).apply()
        }
        Timber.d("Device preferences cleared (UID preserved)")
    }
}
