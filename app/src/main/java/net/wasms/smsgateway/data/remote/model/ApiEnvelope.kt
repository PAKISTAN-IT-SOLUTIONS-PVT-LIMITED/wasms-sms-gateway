package net.wasms.smsgateway.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic API response envelope.
 * All WaSMS API responses wrap the payload in a "data" key.
 * Error responses use the "error" key instead.
 */
@Serializable
data class ApiResponse<T>(
    @SerialName("data")
    val data: T? = null,

    @SerialName("error")
    val error: ApiError? = null,

    @SerialName("meta")
    val meta: Map<String, String>? = null
)

/**
 * Standard error response format from the WaSMS API.
 */
@Serializable
data class ApiError(
    @SerialName("code")
    val code: String,

    @SerialName("message")
    val message: String,

    @SerialName("details")
    val details: Map<String, String>? = null,

    @SerialName("request_id")
    val requestId: String? = null,

    @SerialName("retry_after_seconds")
    val retryAfterSeconds: Int? = null
)
