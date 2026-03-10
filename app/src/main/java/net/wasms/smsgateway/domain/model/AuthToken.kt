package net.wasms.smsgateway.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class AuthToken(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAt: Instant,
    val scopes: List<String>
) {
    val isExpired: Boolean
        get() = Clock.System.now() >= expiresAt

    val isNearExpiry: Boolean
        get() = Clock.System.now() >= expiresAt.minus(kotlin.time.Duration.parse("5m"))

    val bearerHeader: String
        get() = "$tokenType $accessToken"
}
