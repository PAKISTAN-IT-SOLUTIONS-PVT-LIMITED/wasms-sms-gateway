package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to register a new device via QR code or credentials.
 * This is the only unauthenticated device endpoint.
 */
@Serializable
data class RegistrationRequest(
    @SerialName("device_code")
    val deviceCode: String? = null,

    @SerialName("device_password")
    val devicePassword: String? = null,

    @SerialName("team_id")
    val teamId: String,

    @SerialName("device_name")
    val deviceName: String,

    @SerialName("device_fingerprint")
    val deviceUid: String,

    @SerialName("device_meta")
    val deviceMeta: DeviceMetaDto
)

/**
 * Device hardware metadata sent during registration.
 */
@Serializable
data class DeviceMetaDto(
    @SerialName("manufacturer")
    val manufacturer: String,

    @SerialName("model")
    val model: String,

    @SerialName("os_version")
    val osVersion: String,

    @SerialName("app_version")
    val appVersion: String,

    @SerialName("sim_count")
    val simCount: Int
)

/**
 * Request to refresh an expired access token using a refresh token.
 * Implements refresh token rotation -- the old token is invalidated immediately.
 */
@Serializable
data class RefreshTokenRequest(
    @SerialName("grant_type")
    val grantType: String = "refresh_token",

    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("device_id")
    val deviceId: String
)

/**
 * OAuth token response returned by both register and token/refresh endpoints.
 * Wrapped in a "data" envelope by the server.
 */
@Serializable
data class AuthTokenResponse(
    @SerialName("device_id")
    val deviceId: String? = null,

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("access_token")
    val accessToken: String,

    @SerialName("refresh_token")
    val refreshToken: String,

    @SerialName("token_type")
    val tokenType: String,

    @SerialName("expires_in")
    val expiresIn: Long,

    @SerialName("scopes")
    val scopes: List<String> = emptyList()
)
