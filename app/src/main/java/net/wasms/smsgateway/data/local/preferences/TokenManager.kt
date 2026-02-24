package net.wasms.smsgateway.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.remote.model.AuthTokenResponse
import net.wasms.smsgateway.domain.model.AuthToken
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages OAuth token storage using AndroidX EncryptedSharedPreferences.
 *
 * All token data is encrypted at rest:
 * - Keys encrypted with AES256-SIV (deterministic AEAD)
 * - Values encrypted with AES256-GCM (AEAD)
 *
 * Thread-safe: all reads/writes go through SharedPreferences which
 * handles its own synchronization internally.
 */
@Singleton
class TokenManager @Inject constructor(
    context: Context
) {
    companion object {
        private const val PREFS_FILE = "wasms_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TEAM_ID = "team_id"

        /** Buffer before actual expiry to trigger proactive refresh. */
        private val NEAR_EXPIRY_BUFFER = 5.minutes
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
        Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to cleartext")
        // Fallback for devices with broken Keystore (rare but possible)
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    // --- Properties ---

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        private set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        private set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var tokenType: String
        get() = prefs.getString(KEY_TOKEN_TYPE, "Bearer") ?: "Bearer"
        private set(value) = prefs.edit().putString(KEY_TOKEN_TYPE, value).apply()

    var expiresAt: Instant?
        get() {
            val millis = prefs.getLong(KEY_EXPIRES_AT, -1L)
            return if (millis > 0) Instant.fromEpochMilliseconds(millis) else null
        }
        private set(value) {
            prefs.edit().putLong(
                KEY_EXPIRES_AT,
                value?.toEpochMilliseconds() ?: -1L
            ).apply()
        }

    var scopes: List<String>
        get() = prefs.getString(KEY_SCOPES, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        private set(value) = prefs.edit().putString(KEY_SCOPES, value.joinToString(",")).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        private set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var teamId: String?
        get() = prefs.getString(KEY_TEAM_ID, null)
        private set(value) = prefs.edit().putString(KEY_TEAM_ID, value).apply()

    // --- Methods ---

    /**
     * Stores all token fields from the API response.
     * Calculates [expiresAt] as now + expiresIn seconds.
     */
    fun saveToken(response: AuthTokenResponse) {
        val now = Clock.System.now()
        val expiry = now.plus(response.expiresIn.seconds)

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putString(KEY_REFRESH_TOKEN, response.refreshToken)
            .putString(KEY_TOKEN_TYPE, response.tokenType)
            .putLong(KEY_EXPIRES_AT, expiry.toEpochMilliseconds())
            .putString(KEY_SCOPES, response.scopes.joinToString(","))
            .putString(KEY_DEVICE_ID, response.deviceId)
            .putString(KEY_TEAM_ID, response.userId)
            .apply()

        Timber.d("Token saved. Expires at: %s, Device: %s", expiry, response.deviceId)
    }

    /**
     * Returns the current token as a domain model, or null if no token is stored.
     */
    fun getToken(): AuthToken? {
        val access = accessToken ?: return null
        val refresh = refreshToken ?: return null
        val expires = expiresAt ?: return null

        return AuthToken(
            accessToken = access,
            refreshToken = refresh,
            tokenType = tokenType,
            expiresAt = expires,
            scopes = scopes
        )
    }

    /**
     * Removes all authentication data from encrypted storage.
     */
    fun clearToken() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_SCOPES)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_TEAM_ID)
            .apply()

        Timber.d("Token cleared")
    }

    /**
     * Returns true if an access token and refresh token are stored.
     */
    fun isAuthenticated(): Boolean {
        return accessToken != null && refreshToken != null
    }

    /**
     * Returns true if the access token has expired.
     */
    fun isTokenExpired(): Boolean {
        val expires = expiresAt ?: return true
        return Clock.System.now() >= expires
    }

    /**
     * Returns true if the access token will expire within [NEAR_EXPIRY_BUFFER] (5 minutes).
     * Used to trigger proactive token refresh before the request fails with 401.
     */
    fun isTokenNearExpiry(): Boolean {
        val expires = expiresAt ?: return true
        return Clock.System.now() >= expires.minus(NEAR_EXPIRY_BUFFER)
    }
}
