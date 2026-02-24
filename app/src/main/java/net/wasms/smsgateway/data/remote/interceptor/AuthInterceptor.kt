package net.wasms.smsgateway.data.remote.interceptor

import kotlinx.serialization.json.Json
import net.wasms.smsgateway.data.local.preferences.TokenManager
import net.wasms.smsgateway.data.remote.model.ApiResponse
import net.wasms.smsgateway.data.remote.model.AuthTokenResponse
import net.wasms.smsgateway.data.remote.model.RefreshTokenRequest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that handles automatic Bearer token injection
 * and proactive/reactive token refresh.
 *
 * Behavior:
 * 1. Skips auth for /register and /token/refresh endpoints (unauthenticated).
 * 2. If the token is near expiry (within 5 minutes), refreshes BEFORE the request.
 * 3. If a request returns HTTP 401, refreshes the token and retries the request ONCE.
 * 4. Thread-safe: only one thread refreshes the token at a time using a synchronized lock.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val json: Json
) : Interceptor {

    /** Lock object ensuring only one thread refreshes the token at a time. */
    private val refreshLock = Any()

    /** Tracks whether a refresh is currently in progress to avoid duplicate refreshes. */
    @Volatile
    private var isRefreshing = false

    companion object {
        /** Paths that do not require (and must not send) an Authorization header. */
        private val EXCLUDED_PATH_SUFFIXES = listOf(
            "/register",
            "/token/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for registration and token refresh endpoints
        if (isExcludedPath(originalRequest)) {
            return chain.proceed(originalRequest)
        }

        // Proactive refresh: if token is near expiry, refresh before making the request
        if (tokenManager.isAuthenticated() && tokenManager.isTokenNearExpiry()) {
            Timber.d("Token near expiry, attempting proactive refresh")
            refreshTokenSynchronized(chain)
        }

        // Add current Bearer token to request
        val authenticatedRequest = addAuthHeader(originalRequest)
        val response = chain.proceed(authenticatedRequest)

        // Reactive refresh: if we get a 401, try refreshing once and retry
        if (response.code == 401 && tokenManager.isAuthenticated()) {
            Timber.d("Received 401, attempting reactive token refresh")
            response.close()

            val refreshed = refreshTokenSynchronized(chain)
            if (refreshed) {
                // Retry original request with new token
                val retryRequest = addAuthHeader(originalRequest)
                return chain.proceed(retryRequest)
            }

            // Refresh failed -- clear token (device must re-register)
            Timber.w("Token refresh failed after 401, clearing auth state")
            tokenManager.clearToken()
        }

        return response
    }

    /**
     * Adds the "Authorization: Bearer {token}" header using the current access token.
     */
    private fun addAuthHeader(request: Request): Request {
        val token = tokenManager.accessToken ?: return request
        val tokenType = tokenManager.tokenType

        return request.newBuilder()
            .header("Authorization", "$tokenType $token")
            .build()
    }

    /**
     * Checks if the request path matches an endpoint that should not carry auth.
     */
    private fun isExcludedPath(request: Request): Boolean {
        val path = request.url.encodedPath
        return EXCLUDED_PATH_SUFFIXES.any { suffix -> path.endsWith(suffix) }
    }

    /**
     * Performs a synchronized token refresh. Only one thread will actually perform
     * the refresh; other threads waiting on the lock will use the newly refreshed token.
     *
     * @return true if the token was successfully refreshed
     */
    private fun refreshTokenSynchronized(chain: Interceptor.Chain): Boolean {
        synchronized(refreshLock) {
            // Double-check: another thread may have already refreshed while we waited
            if (!tokenManager.isTokenNearExpiry() && !tokenManager.isTokenExpired()) {
                Timber.d("Token already refreshed by another thread")
                return true
            }

            if (isRefreshing) {
                Timber.d("Refresh already in progress, skipping")
                return false
            }

            isRefreshing = true
            try {
                return performTokenRefresh(chain)
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * Executes the actual token refresh HTTP call using the current OkHttp chain.
     * This uses a fresh OkHttp request (not through Retrofit) to avoid interceptor recursion.
     * The /token/refresh path is excluded from auth, so this request will not re-enter this
     * interceptor's auth logic.
     */
    private fun performTokenRefresh(chain: Interceptor.Chain): Boolean {
        val refreshToken = tokenManager.refreshToken ?: return false
        val deviceId = tokenManager.deviceId ?: return false

        val refreshRequest = RefreshTokenRequest(
            grantType = "refresh_token",
            refreshToken = refreshToken,
            deviceId = deviceId
        )

        val requestBody = json.encodeToString(RefreshTokenRequest.serializer(), refreshRequest)
        val mediaType = "application/json".toMediaType()

        // Build the refresh request URL relative to the original request's base URL
        val originalUrl = chain.request().url
        val refreshUrl = originalUrl.newBuilder()
            .encodedPath(buildRefreshPath(originalUrl.encodedPath))
            .build()

        val httpRequest = Request.Builder()
            .url(refreshUrl)
            .post(requestBody.toRequestBody(mediaType))
            .header("Content-Type", "application/json")
            .build()

        return try {
            val response = chain.proceed(httpRequest)
            if (response.isSuccessful) {
                val body = response.body?.string()
                response.close()

                if (body != null) {
                    val apiResponse = json.decodeFromString<ApiResponse<AuthTokenResponse>>(body)
                    val tokenData = apiResponse.data
                    if (tokenData != null) {
                        tokenManager.saveToken(tokenData)
                        Timber.d("Token refresh successful")
                        true
                    } else {
                        Timber.e("Token refresh response had null data")
                        false
                    }
                } else {
                    Timber.e("Token refresh response body was null")
                    false
                }
            } else {
                Timber.e("Token refresh failed with HTTP %d", response.code)
                response.close()
                false
            }
        } catch (e: IOException) {
            Timber.e(e, "Token refresh failed with IO error")
            false
        } catch (e: Exception) {
            Timber.e(e, "Token refresh failed with unexpected error")
            false
        }
    }

    /**
     * Builds the refresh endpoint path from the current request's base path.
     * Extracts the API version prefix and appends device/token/refresh.
     *
     * Example: /api/v1/device/messages/pending -> /api/v1/device/token/refresh
     */
    private fun buildRefreshPath(currentPath: String): String {
        val deviceIndex = currentPath.indexOf("device/")
        return if (deviceIndex > 0) {
            currentPath.substring(0, deviceIndex) + "device/token/refresh"
        } else {
            // Fallback: assume standard API path
            "/api/v1/device/token/refresh"
        }
    }
}
