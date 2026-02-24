package net.wasms.smsgateway.data.remote.api

import net.wasms.smsgateway.data.remote.model.AckRequest
import net.wasms.smsgateway.data.remote.model.AckResponse
import net.wasms.smsgateway.data.remote.model.ApiResponse
import net.wasms.smsgateway.data.remote.model.AuthTokenResponse
import net.wasms.smsgateway.data.remote.model.AutoReplyResponse
import net.wasms.smsgateway.data.remote.model.BulkDeliveryReportRequest
import net.wasms.smsgateway.data.remote.model.BulkDeliveryReportResponse
import net.wasms.smsgateway.data.remote.model.BulkIncomingSmsRequest
import net.wasms.smsgateway.data.remote.model.BulkIncomingSmsResponse
import net.wasms.smsgateway.data.remote.model.CommandAckRequest
import net.wasms.smsgateway.data.remote.model.CommandAckResponse
import net.wasms.smsgateway.data.remote.model.CommandsResponse
import net.wasms.smsgateway.data.remote.model.DeliveryReportRequest
import net.wasms.smsgateway.data.remote.model.DeliveryReportResponse
import net.wasms.smsgateway.data.remote.model.DeregisterResponse
import net.wasms.smsgateway.data.remote.model.DeviceConfigResponse
import net.wasms.smsgateway.data.remote.model.DeviceProfileResponse
import net.wasms.smsgateway.data.remote.model.FcmTokenRequest
import net.wasms.smsgateway.data.remote.model.FcmTokenResponse
import net.wasms.smsgateway.data.remote.model.FullSyncRequest
import net.wasms.smsgateway.data.remote.model.FullSyncResponse
import net.wasms.smsgateway.data.remote.model.HeartbeatRequest
import net.wasms.smsgateway.data.remote.model.HeartbeatResponse
import net.wasms.smsgateway.data.remote.model.IncomingSmsRequest
import net.wasms.smsgateway.data.remote.model.IncomingSmsResponse
import net.wasms.smsgateway.data.remote.model.IncrementalSyncResponse
import net.wasms.smsgateway.data.remote.model.PendingMessagesResponse
import net.wasms.smsgateway.data.remote.model.RefreshTokenRequest
import net.wasms.smsgateway.data.remote.model.RegistrationRequest
import net.wasms.smsgateway.data.remote.model.SimCardsRequest
import net.wasms.smsgateway.data.remote.model.SimCardsResponse
import net.wasms.smsgateway.data.remote.model.SimConfigResponse
import net.wasms.smsgateway.data.remote.model.SimStatsRequest
import net.wasms.smsgateway.data.remote.model.SimStatsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for all 21 WaSMS device API endpoints.
 *
 * Base URL: BuildConfig.API_BASE_URL (e.g., "https://wasms.net/api/v1")
 * All paths are relative to /device/.
 *
 * Authentication: Bearer token (OAuth 2.0) via OkHttp interceptor,
 * except for the register endpoint which is unauthenticated.
 *
 * All responses are wrapped in an ApiResponse<T> envelope with a "data" key.
 */
interface DeviceApi {

    // =========================================================================
    // Registration & Auth
    // =========================================================================

    /**
     * Register a new device via QR code (device_code) or credentials (device_password).
     * This is the ONLY unauthenticated endpoint.
     *
     * Rate limit: 5/min per IP.
     */
    @POST("device/register")
    suspend fun register(
        @Body request: RegistrationRequest
    ): Response<ApiResponse<AuthTokenResponse>>

    /**
     * Refresh the access token using a refresh token.
     * Implements refresh token rotation: the old token is invalidated immediately.
     *
     * Rate limit: 10/min per device.
     */
    @POST("device/token/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<ApiResponse<AuthTokenResponse>>

    /**
     * Register or update the device's FCM push token.
     * Called after Firebase onNewToken callback.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 5/min per device.
     */
    @POST("device/fcm")
    suspend fun updateFcmToken(
        @Body request: FcmTokenRequest
    ): Response<ApiResponse<FcmTokenResponse>>

    /**
     * Deregister the device. Revokes all tokens.
     * Server reassigns any pending messages to the queue.
     *
     * Auth: Bearer token, scope device:manage.
     * Rate limit: 3/min per device.
     */
    @DELETE("device/{deviceId}")
    suspend fun deregister(
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<DeregisterResponse>>

    /**
     * Get the current device profile and status.
     *
     * Auth: Bearer token, scope device:connect.
     */
    @GET("device/me")
    suspend fun getProfile(): Response<ApiResponse<DeviceProfileResponse>>

    // =========================================================================
    // Message Dispatch
    // =========================================================================

    /**
     * Pull a batch of pending SMS messages assigned to this device.
     * Messages are locked to this device for a configurable timeout (default 5 min).
     *
     * Auth: Bearer token, scope device:messages:pull.
     * Rate limit: 30/min per device (adaptive via next_poll_delay_ms).
     *
     * @param limit Number of messages to pull (1-50, default 10).
     * @param priority Filter: "all", "high", "normal", "low".
     * @param simSlot Only messages routable via this SIM slot.
     */
    @GET("device/messages/pending")
    suspend fun getPendingMessages(
        @Query("limit") limit: Int? = null,
        @Query("priority") priority: String? = null,
        @Query("sim_slot") simSlot: Int? = null
    ): Response<ApiResponse<PendingMessagesResponse>>

    /**
     * Acknowledge that a message has been dispatched to Android SmsManager.
     * This extends the server-side lock on the message.
     *
     * Auth: Bearer token, scope device:messages:pull.
     * Rate limit: 60/min per device.
     */
    @POST("device/messages/{messageId}/ack")
    suspend fun acknowledgeMessage(
        @Path("messageId") messageId: String,
        @Body request: AckRequest
    ): Response<ApiResponse<AckResponse>>

    /**
     * Submit a single delivery report for a message.
     * Includes Android SmsManager result codes and carrier information.
     *
     * Auth: Bearer token, scope device:messages:report.
     * Rate limit: 120/min per device.
     */
    @POST("device/messages/{messageId}/report")
    suspend fun submitDeliveryReport(
        @Path("messageId") messageId: String,
        @Body request: DeliveryReportRequest
    ): Response<ApiResponse<DeliveryReportResponse>>

    /**
     * Submit delivery reports for multiple messages in a single request.
     * Maximum 100 reports per request.
     * Individual failures are reported in the errors array.
     *
     * Auth: Bearer token, scope device:messages:report.
     * Rate limit: 20/min per device.
     */
    @POST("device/messages/reports")
    suspend fun submitBulkDeliveryReports(
        @Body request: BulkDeliveryReportRequest
    ): Response<ApiResponse<BulkDeliveryReportResponse>>

    // =========================================================================
    // Incoming SMS
    // =========================================================================

    /**
     * Forward a single received SMS from the device to the server.
     * May return an auto-reply the device should queue for sending.
     *
     * Auth: Bearer token, scope device:incoming:forward.
     * Rate limit: 60/min per device.
     */
    @POST("device/incoming")
    suspend fun forwardIncomingSms(
        @Body request: IncomingSmsRequest
    ): Response<ApiResponse<IncomingSmsResponse>>

    /**
     * Forward multiple received SMS messages in a single request.
     * Maximum 50 messages per request.
     *
     * Auth: Bearer token, scope device:incoming:forward.
     * Rate limit: 10/min per device.
     */
    @POST("device/incoming/bulk")
    suspend fun forwardBulkIncomingSms(
        @Body request: BulkIncomingSmsRequest
    ): Response<ApiResponse<BulkIncomingSmsResponse>>

    /**
     * Poll for an AI-generated auto-reply for a specific incoming message.
     * Used when the server needs processing time (AI Brain query).
     *
     * Auth: Bearer token, scope device:incoming:forward.
     * Rate limit: 30/min per device.
     */
    @GET("device/incoming/{incomingId}/reply")
    suspend fun getAutoReply(
        @Path("incomingId") incomingId: String
    ): Response<ApiResponse<AutoReplyResponse>>

    // =========================================================================
    // Device Health
    // =========================================================================

    /**
     * Send a heartbeat with device health metrics.
     * The server uses this for availability tracking and health scoring.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 60/min per device.
     */
    @POST("device/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<ApiResponse<HeartbeatResponse>>

    /**
     * Fetch the device's operational configuration.
     * The device should cache this and use config_version for change detection.
     * Supports ETag/If-None-Match for 304 Not Modified.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 10/min per device.
     */
    @GET("device/config")
    suspend fun getConfig(): Response<ApiResponse<DeviceConfigResponse>>

    /**
     * Poll for pending remote commands (pause, resume, update_config, etc.).
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 30/min per device.
     */
    @GET("device/commands")
    suspend fun getCommands(): Response<ApiResponse<CommandsResponse>>

    /**
     * Acknowledge execution of a remote command.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 30/min per device.
     */
    @POST("device/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Path("commandId") commandId: String,
        @Body request: CommandAckRequest
    ): Response<ApiResponse<CommandAckResponse>>

    // =========================================================================
    // SIM Management
    // =========================================================================

    /**
     * Register or update the SIM cards present in the device.
     * Called at startup and whenever SIM state changes.
     * PUT semantics: the full state replaces the previous state.
     *
     * Auth: Bearer token, scope device:sims:manage.
     * Rate limit: 10/min per device.
     */
    @PUT("device/sims")
    suspend fun updateSimCards(
        @Body request: SimCardsRequest
    ): Response<ApiResponse<SimCardsResponse>>

    /**
     * Report delivery statistics for a specific SIM slot.
     * Sent periodically during active sending.
     *
     * Auth: Bearer token, scope device:sims:manage.
     * Rate limit: 30/min per device.
     */
    @POST("device/sims/{simSlot}/stats")
    suspend fun reportSimStats(
        @Path("simSlot") simSlot: Int,
        @Body request: SimStatsRequest
    ): Response<ApiResponse<SimStatsResponse>>

    /**
     * Fetch the current SIM rotation configuration.
     * A focused subset of the full device config for more frequent refresh.
     *
     * Auth: Bearer token, scope device:sims:manage.
     * Rate limit: 10/min per device.
     */
    @GET("device/sims/config")
    suspend fun getSimConfig(): Response<ApiResponse<SimConfigResponse>>

    // =========================================================================
    // Sync
    // =========================================================================

    /**
     * Request a full state sync after an offline period or on first launch.
     * Reconciles the device's local state with the server.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 5/min per device.
     */
    @POST("device/sync/full")
    suspend fun fullSync(
        @Body request: FullSyncRequest
    ): Response<ApiResponse<FullSyncResponse>>

    /**
     * Fetch changes since the last sync cursor.
     * If has_more is true, fetch again immediately with the new cursor.
     * Cursors expire after 7 days; if expired, server returns 410 Gone
     * and the device must perform a full sync.
     *
     * Auth: Bearer token, scope device:connect.
     * Rate limit: 30/min per device.
     */
    @GET("device/sync/incremental")
    suspend fun incrementalSync(
        @Query("cursor") cursor: String,
        @Query("types") types: String? = null
    ): Response<ApiResponse<IncrementalSyncResponse>>
}
