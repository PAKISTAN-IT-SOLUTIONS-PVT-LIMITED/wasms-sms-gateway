package net.wasms.smsgateway.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.wasms.smsgateway.R
import net.wasms.smsgateway.WaSmsApp
import net.wasms.smsgateway.domain.model.DeviceConfig
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.domain.repository.DeviceRepository
import net.wasms.smsgateway.domain.repository.MessageRepository
import net.wasms.smsgateway.presentation.MainActivity
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that orchestrates SMS sending.
 *
 * Runs as a persistent foreground service with START_STICKY to survive app kills.
 * Manages the main send loop: fetches batches from [MessageRepository], picks SIMs
 * via [SmsEngine], dispatches each message, and respects rate limiting from [DeviceConfig].
 *
 * Controlled via Intent actions: ACTION_START, ACTION_PAUSE, ACTION_RESUME, ACTION_STOP.
 */
@AndroidEntryPoint
class SmsSenderService : LifecycleService() {

    @Inject lateinit var messageRepository: MessageRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var smsEngine: SmsEngine
    @Inject lateinit var simRotationEngine: SimRotationEngine

    private var sendLoopJob: Job? = null
    private var queueObserverJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        Timber.d("SmsSenderService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                Timber.i("SmsSenderService: ACTION_START received")
                startForegroundService()
                startProcessing()
            }
            ACTION_PAUSE -> {
                Timber.i("SmsSenderService: ACTION_PAUSE received")
                pause()
            }
            ACTION_RESUME -> {
                Timber.i("SmsSenderService: ACTION_RESUME received")
                resume()
            }
            ACTION_STOP -> {
                Timber.i("SmsSenderService: ACTION_STOP received")
                stop()
                return START_NOT_STICKY
            }
            else -> {
                // Service restarted by system after kill (START_STICKY)
                if (!_isRunning.value) {
                    Timber.i("SmsSenderService: Restarted by system, resuming")
                    startForegroundService()
                    startProcessing()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Timber.i("SmsSenderService destroyed")
        releaseWakeLock()
        sendLoopJob?.cancel()
        queueObserverJob?.cancel()
        _isRunning.value = false
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Foreground notification
    // -------------------------------------------------------------------------

    private fun startForegroundService() {
        val notification = buildNotification(getString(R.string.notification_service_idle))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (_isPaused.value) {
            val resumeIntent = Intent(this, SmsSenderService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePending = PendingIntent.getService(
                this, 1, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePending
            ).build()
        } else {
            val pauseIntent = Intent(this, SmsSenderService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePending = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePending
            ).build()
        }

        val stopIntent = Intent(this, SmsSenderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Stop",
            stopPending
        ).build()

        return NotificationCompat.Builder(this, WaSmsApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    // -------------------------------------------------------------------------
    // Wake lock
    // -------------------------------------------------------------------------

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WaSMS::SmsSenderService"
            ).apply {
                acquire(WAKE_LOCK_TIMEOUT)
            }
            Timber.d("WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("WakeLock released")
            }
        }
        wakeLock = null
    }

    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    private fun startProcessing() {
        if (_isRunning.value) {
            Timber.d("SmsSenderService: Already running, ignoring duplicate start")
            return
        }
        _isRunning.value = true
        _isPaused.value = false

        sendLoopJob?.cancel()
        sendLoopJob = lifecycleScope.launch {
            Timber.i("SmsSenderService: Send loop started")
            runSendLoop()
        }

        queueObserverJob?.cancel()
        queueObserverJob = lifecycleScope.launch {
            observeQueueForNotification()
        }
    }

    private suspend fun runSendLoop() {
        while (_isRunning.value) {
            // Check if paused
            if (_isPaused.value) {
                updateNotification("Paused")
                delay(PAUSE_CHECK_INTERVAL_MS)
                continue
            }

            // Load current config
            val config = try {
                deviceRepository.observeDeviceConfig().first()
            } catch (e: Exception) {
                Timber.w(e, "Failed to load device config, using defaults")
                DeviceConfig.DEFAULT
            }

            // Check quiet hours before processing
            if (isInQuietHours(config)) {
                Timber.d("SmsSenderService: In quiet hours, sleeping until end")
                delay(60_000) // Check again in 1 minute
                continue
            }

            // Fetch next batch
            val batch: List<SmsMessage> = try {
                messageRepository.getNextBatch(config.batchSize)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch next batch")
                delay(ERROR_RETRY_DELAY_MS)
                continue
            }

            if (batch.isEmpty()) {
                // Queue is empty, show idle and wait before checking again
                updateNotification(getString(R.string.notification_service_idle))
                delay(IDLE_POLL_INTERVAL_MS)
                continue
            }

            // Process each message in the batch
            for (message in batch) {
                if (!_isRunning.value || _isPaused.value) break

                val sent = processSingleMessage(message, config)

                // Rate limiting: wait based on maxSmsPerMinute with random jitter
                if (sent && config.maxSmsPerMinute > 0) {
                    val baseDelay = 60_000L / config.maxSmsPerMinute
                    val jitter = kotlin.random.Random.nextDouble(0.7, 1.5)
                    val delayMs = (baseDelay * jitter).toLong().coerceIn(3_000L, 120_000L)
                    delay(delayMs)
                }
            }
        }
        Timber.i("SmsSenderService: Send loop exited")
    }

    /**
     * Process a single message: select SIM and dispatch.
     * Returns true if the message was dispatched to the OS, false otherwise.
     */
    private suspend fun processSingleMessage(
        message: SmsMessage,
        config: DeviceConfig
    ): Boolean {
        // Select a SIM card
        val simCard = try {
            simRotationEngine.getNextSim()
        } catch (e: Exception) {
            Timber.e(e, "SIM selection failed for message ${message.id}")
            null
        }

        if (simCard == null) {
            Timber.w("No SIM available for message ${message.id}, will retry later")
            messageRepository.markFailed(
                messageId = message.id,
                errorMessage = "No SIM card available",
                canRetry = true
            )
            return false
        }

        // Update notification
        val pendingCount = try {
            messageRepository.observeQueueStats().first().pending
        } catch (e: Exception) { 0 }
        updateNotification(getString(R.string.notification_service_sending, pendingCount))

        // Update message state to DISPATCHED_TO_SIM
        try {
            messageRepository.updateState(message.id, SmsState.DISPATCHED_TO_SIM)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update state for message ${message.id}")
        }

        // Send via engine
        val dispatched = try {
            smsEngine.sendSms(
                subscriptionId = simCard.subscriptionId,
                destination = message.destination,
                body = message.body,
                messageId = message.id
            )
        } catch (e: SecurityException) {
            Timber.e(e, "SMS permission denied when sending message ${message.id}")
            messageRepository.markFailed(
                messageId = message.id,
                errorMessage = "SMS permission denied",
                canRetry = false
            )
            return false
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error sending message ${message.id}")
            messageRepository.markFailed(
                messageId = message.id,
                errorMessage = "Send error: ${e.message}",
                canRetry = true
            )
            return false
        }

        if (dispatched) {
            // Mark message as sent with the SIM slot used
            try {
                messageRepository.markSent(message.id, simCard.slot)
            } catch (e: Exception) {
                Timber.e(e, "Failed to markSent for message ${message.id}")
            }
            // Report to SIM rotation engine
            simRotationEngine.reportSendResult(simCard.id, success = true)

            // Apply cooldown if configured
            if (config.simRotationEnabled && config.simCooldownSeconds > 0) {
                simRotationEngine.applyCooldown(simCard.id, config.simCooldownSeconds)
            }
        } else {
            messageRepository.markFailed(
                messageId = message.id,
                errorMessage = "SMS dispatch failed at OS level",
                canRetry = true
            )
            simRotationEngine.reportSendResult(simCard.id, success = false)
        }

        return dispatched
    }

    /**
     * Check if the current time falls within the configured quiet hours.
     * Supports overnight ranges (e.g., 22:00 to 06:00).
     */
    private fun isInQuietHours(config: DeviceConfig): Boolean {
        val startHour = config.quietHoursStart ?: return false
        val endHour = config.quietHoursEnd ?: return false
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return if (startHour <= endHour) {
            // Same day range (e.g., 9 to 18)
            currentHour in startHour..endHour
        } else {
            // Overnight range (e.g., 22 to 6)
            currentHour >= startHour || currentHour <= endHour
        }
    }

    /**
     * Observe queue stats to update the notification in real time even when
     * the send loop is idle or between batches.
     */
    private suspend fun observeQueueForNotification() {
        messageRepository.observeQueueStats().collectLatest { stats ->
            if (!_isPaused.value && _isRunning.value) {
                if (stats.pending > 0 || stats.sending > 0) {
                    updateNotification(
                        getString(R.string.notification_service_sending, stats.pending + stats.sending)
                    )
                } else {
                    updateNotification(getString(R.string.notification_service_idle))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Public control methods
    // -------------------------------------------------------------------------

    fun pause() {
        Timber.i("SmsSenderService: Paused")
        _isPaused.value = true
        updateNotification("Paused")
    }

    fun resume() {
        Timber.i("SmsSenderService: Resumed")
        _isPaused.value = false
    }

    fun stop() {
        Timber.i("SmsSenderService: Stopping")
        _isRunning.value = false
        sendLoopJob?.cancel()
        queueObserverJob?.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    companion object {
        const val ACTION_START = "net.wasms.smsgateway.action.START"
        const val ACTION_PAUSE = "net.wasms.smsgateway.action.PAUSE"
        const val ACTION_RESUME = "net.wasms.smsgateway.action.RESUME"
        const val ACTION_STOP = "net.wasms.smsgateway.action.STOP"

        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 60 * 1000L // 10 hours
        private const val PAUSE_CHECK_INTERVAL_MS = 2_000L
        private const val IDLE_POLL_INTERVAL_MS = 5_000L
        private const val ERROR_RETRY_DELAY_MS = 10_000L
        private const val MIN_INTER_SMS_DELAY_MS = 1_000L

        /**
         * Start the service with ACTION_START.
         */
        fun start(context: Context) {
            val intent = Intent(context, SmsSenderService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Send a command to the running service.
         */
        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, SmsSenderService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }
    }
}
