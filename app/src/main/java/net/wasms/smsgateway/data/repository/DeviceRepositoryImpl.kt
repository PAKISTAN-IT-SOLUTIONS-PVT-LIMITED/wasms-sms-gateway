package net.wasms.smsgateway.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import net.wasms.smsgateway.data.local.db.dao.DeviceConfigDao
import net.wasms.smsgateway.data.local.db.dao.SimCardDao
import net.wasms.smsgateway.data.local.model.DeviceConfigEntity
import net.wasms.smsgateway.data.local.model.SimCardEntity
import net.wasms.smsgateway.data.local.preferences.DevicePreferences
import net.wasms.smsgateway.data.local.preferences.TokenManager
import net.wasms.smsgateway.data.remote.api.DeviceApi
import net.wasms.smsgateway.data.remote.model.BatteryDto
import net.wasms.smsgateway.data.remote.model.ConnectivityDto
import net.wasms.smsgateway.data.remote.model.DeviceMetaDto
import net.wasms.smsgateway.data.remote.model.FcmTokenRequest
import net.wasms.smsgateway.data.remote.model.HeartbeatRequest
import net.wasms.smsgateway.data.remote.model.RefreshTokenRequest
import net.wasms.smsgateway.data.remote.model.RegistrationRequest
import net.wasms.smsgateway.data.remote.model.SimCardDto
import net.wasms.smsgateway.data.remote.model.SimCardsRequest
import net.wasms.smsgateway.data.remote.model.SmsQueueDto
import net.wasms.smsgateway.domain.model.AuthToken
import net.wasms.smsgateway.domain.model.Device
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.DeviceHealth
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.model.SimCard
import net.wasms.smsgateway.domain.repository.DeviceRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val api: DeviceApi,
    private val tokenManager: TokenManager,
    private val devicePrefs: DevicePreferences,
    private val simCardDao: SimCardDao,
    private val configDao: DeviceConfigDao
) : DeviceRepository {

    /**
     * Registers the device with the WaSMS server using a QR registration code.
     * On success, stores the OAuth token and device metadata.
     */
    override suspend fun registerDevice(registrationCode: String, deviceName: String): AuthToken {
        val request = RegistrationRequest(
            grantType = "device_code",
            code = registrationCode,
            deviceName = deviceName,
            deviceUid = devicePrefs.deviceUid,
            deviceMeta = DeviceMetaDto(
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                osVersion = "Android ${android.os.Build.VERSION.RELEASE}",
                appVersion = getAppVersion(),
                simCount = 0 // Updated after SIM detection
            )
        )

        val response = api.register(request)
        val tokenData = response.body()?.data
            ?: throw IllegalStateException("Registration failed: empty response")

        tokenManager.saveToken(tokenData)
        devicePrefs.deviceName = deviceName
        devicePrefs.isOnboardingComplete = true

        Timber.i("Device registered successfully. Device ID: %s", tokenData.deviceId)

        return tokenManager.getToken()
            ?: throw IllegalStateException("Token was saved but could not be read back")
    }

    /**
     * Refreshes the OAuth access token using the stored refresh token.
     * Both the access token and refresh token are rotated.
     */
    override suspend fun refreshToken(): AuthToken {
        val currentRefreshToken = tokenManager.refreshToken
            ?: throw IllegalStateException("No refresh token available")
        val deviceId = tokenManager.deviceId
            ?: throw IllegalStateException("No device ID available")

        val request = RefreshTokenRequest(
            grantType = "refresh_token",
            refreshToken = currentRefreshToken,
            deviceId = deviceId
        )

        val response = api.refreshToken(request)
        val tokenData = response.body()?.data
            ?: throw IllegalStateException("Token refresh failed: empty response")
        tokenManager.saveToken(tokenData)

        Timber.d("Token refreshed successfully")

        return tokenManager.getToken()
            ?: throw IllegalStateException("Refreshed token was saved but could not be read back")
    }

    /**
     * Deregisters the device from the server and clears all local auth/device state.
     * The server will reassign any pending messages to other devices.
     */
    override suspend fun deregisterDevice() {
        val deviceId = tokenManager.deviceId
            ?: throw IllegalStateException("No device ID to deregister")

        try {
            api.deregister(deviceId)
            Timber.i("Device deregistered from server: %s", deviceId)
        } finally {
            // Always clear local state, even if the server call fails
            // (the device should be treated as unregistered locally)
            tokenManager.clearToken()
            devicePrefs.clear()
            Timber.d("Local auth and device state cleared")
        }
    }

    /**
     * Updates the FCM token on the server for push notifications.
     */
    override suspend fun updateFcmToken(token: String) {
        val request = FcmTokenRequest(
            fcmToken = token,
            platform = "android"
        )

        api.updateFcmToken(request)
        devicePrefs.fcmToken = token
        Timber.d("FCM token updated on server")
    }

    /**
     * Sends a heartbeat with device health metrics to the server.
     * The server uses this to track device availability and health.
     */
    override suspend fun sendHeartbeat(health: DeviceHealth) {
        val request = HeartbeatRequest(
            timestamp = Clock.System.now().toString(),
            battery = BatteryDto(
                level = health.batteryLevel,
                isCharging = health.isCharging
            ),
            connectivity = ConnectivityDto(
                type = if (health.isWifiConnected) "wifi" else "cellular",
                signalStrengthDbm = health.signalStrength
            ),
            smsQueue = SmsQueueDto(
                pendingInApp = health.queueDepth,
                pendingInSmsManager = 0
            ),
            sims = emptyList(), // SIM status sent separately
            appVersion = getAppVersion(),
            uptimeSeconds = health.uptimeSeconds
        )

        api.sendHeartbeat(request)
        Timber.d("Heartbeat sent: battery=%d%%, queue=%d", health.batteryLevel, health.queueDepth)
    }

    /**
     * Registers/updates the SIM card configuration on the server and locally.
     * The server returns per-SIM daily limits and usage stats.
     */
    override suspend fun updateSimCards(simCards: List<SimCard>) {
        val request = SimCardsRequest(
            simCards = simCards.map { sim ->
                SimCardDto(
                    slot = sim.slot,
                    iccId = sim.iccId,
                    carrierName = sim.carrierName,
                    phoneNumber = sim.phoneNumber,
                    countryCode = sim.countryCode
                )
            }
        )

        val response = api.updateSimCards(request)
        val responseData = response.body()?.data
            ?: throw IllegalStateException("SIM update failed: empty response")

        // Merge server response with local SIM data and upsert into Room
        val entities = responseData.sims.map { serverSim ->
            val localSim = simCards.find { it.slot == serverSim.slot }
            SimCardEntity(
                id = serverSim.serverSimId ?: "sim-slot-${serverSim.slot}",
                subscriptionId = localSim?.subscriptionId ?: 0,
                slot = serverSim.slot,
                carrierName = localSim?.carrierName ?: "",
                phoneNumber = localSim?.phoneNumber,
                iccId = localSim?.iccId,
                countryCode = localSim?.countryCode,
                isActive = serverSim.status == "active",
                dailyLimit = serverSim.dailyLimit,
                totalSent = serverSim.dailySent,
                totalFailed = localSim?.totalFailed ?: 0,
                healthScore = localSim?.healthScore ?: 1.0f,
                lastUsedAt = null,
                cooldownUntil = null
            )
        }

        simCardDao.upsertAll(entities)
        Timber.d("SIM cards updated: %d SIMs synced", entities.size)
    }

    /**
     * Fetches the device configuration from the server and persists it in Room.
     * Returns the domain model for immediate use.
     */
    override suspend fun fetchConfig(): DeviceConfig {
        val response = api.getConfig()
        val configData = response.body()?.data
            ?: throw IllegalStateException("Config fetch failed: empty response")

        val config = DeviceConfig(
            maxSmsPerMinute = configData.sending?.rateLimitPerMinute ?: DeviceConfig.DEFAULT.maxSmsPerMinute,
            maxSmsPerHour = configData.sending?.rateLimitPerHour ?: DeviceConfig.DEFAULT.maxSmsPerHour,
            simRotationEnabled = true,
            simCooldownSeconds = configData.simRotation?.cooldownDurationSeconds ?: DeviceConfig.DEFAULT.simCooldownSeconds,
            heartbeatIntervalSeconds = configData.health?.heartbeatIntervalSeconds ?: DeviceConfig.DEFAULT.heartbeatIntervalSeconds,
            batchSize = configData.sending?.batchSize ?: DeviceConfig.DEFAULT.batchSize,
            retryMaxAttempts = configData.sending?.retryMaxAttempts ?: DeviceConfig.DEFAULT.retryMaxAttempts,
            retryBackoffSeconds = generateBackoffSequence(
                baseSeconds = configData.sending?.retryBackoffBaseSeconds ?: 60,
                multiplier = configData.sending?.retryBackoffMultiplier ?: 2.0,
                maxAttempts = configData.sending?.retryMaxAttempts ?: 3
            ),
            quietHoursStart = null,
            quietHoursEnd = null,
            autoReplyEnabled = configData.incoming?.autoReplyEnabled ?: false,
            incomingForwardEnabled = configData.incoming?.forwardIncoming ?: true
        )

        // Persist to Room
        val entity = DeviceConfigEntity.fromDomain(config, updatedAt = Clock.System.now())
        configDao.upsert(entity)

        Timber.d("Device config fetched and stored: rate=%d/min, batch=%d", config.maxSmsPerMinute, config.batchSize)
        return config
    }

    /**
     * Returns the current device profile. Currently constructed from local state.
     * A full implementation would call GET /device/me.
     */
    override suspend fun getDevice(): Device? {
        if (!isRegistered()) return null

        val simCards = simCardDao.getAll().map { it.toDomain() }

        return Device(
            id = tokenManager.deviceId ?: return null,
            teamId = tokenManager.teamId ?: "",
            deviceName = devicePrefs.deviceName ?: "Unknown Device",
            deviceUid = devicePrefs.deviceUid,
            status = DeviceStatus.ONLINE,
            appVersion = getAppVersion(),
            lastSeenAt = Clock.System.now(),
            simCards = simCards,
            isActive = true
        )
    }

    /**
     * Observes the device status by combining connection state and queue state.
     * Returns a Flow that emits [DeviceStatus] whenever the underlying state changes.
     */
    override fun observeDeviceStatus(): Flow<DeviceStatus> {
        // Combine SIM card availability and config state to derive device status
        return simCardDao.observeActive().combine(configDao.observe()) { activeSims, config ->
            when {
                !tokenManager.isAuthenticated() -> DeviceStatus.OFFLINE
                activeSims.isEmpty() -> DeviceStatus.ERROR
                config == null -> DeviceStatus.IDLE
                else -> DeviceStatus.ONLINE
            }
        }
    }

    /**
     * Observes SIM card state from the local Room database.
     */
    override fun observeSimCards(): Flow<List<SimCard>> {
        return simCardDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Observes device configuration from the local Room database.
     * Falls back to default config if none is stored.
     */
    override fun observeDeviceConfig(): Flow<DeviceConfig> {
        return configDao.observe().map { entity ->
            entity?.toDomain() ?: DeviceConfig.DEFAULT
        }
    }

    /**
     * Returns true if the device has valid authentication credentials.
     */
    override suspend fun isRegistered(): Boolean {
        return tokenManager.isAuthenticated()
    }

    // --- Private helpers ---

    private fun getAppVersion(): String {
        return try {
            net.wasms.smsgateway.BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    /**
     * Generates a geometric backoff sequence: [base, base*mult, base*mult^2, ...].
     */
    private fun generateBackoffSequence(
        baseSeconds: Int,
        multiplier: Double,
        maxAttempts: Int
    ): List<Int> {
        return (0 until maxAttempts).map { attempt ->
            (baseSeconds * Math.pow(multiplier, attempt.toDouble())).toInt()
        }
    }
}
