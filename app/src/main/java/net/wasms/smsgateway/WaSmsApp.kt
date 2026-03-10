package net.wasms.smsgateway

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class WaSmsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Install crash reporter FIRST — before anything else can crash
        installCrashReporter()

        // SQLCipher native library must be loaded before any database access.
        // Without this, Room + SupportOpenHelperFactory will crash with UnsatisfiedLinkError.
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            // Log but don't crash — DatabaseModule will fall back to unencrypted DB
            android.util.Log.e("WaSmsApp", "Failed to load sqlcipher native library", e)
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.INFO
            )
            .build()

    /**
     * Installs a global uncaught exception handler that:
     * 1. Captures the full stack trace + device info
     * 2. Saves to internal storage as crash_log.txt
     * 3. Delegates to the default handler (so the system still terminates properly)
     */
    private fun installCrashReporter() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashLog = buildCrashLog(thread, throwable)
                val crashFile = File(filesDir, CRASH_LOG_FILE)
                crashFile.writeText(crashLog)
                android.util.Log.e("WaSmsApp", "FATAL: Crash log saved to ${crashFile.absolutePath}")
            } catch (e: Exception) {
                android.util.Log.e("WaSmsApp", "Failed to write crash log", e)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Builds a detailed crash log string with device info, timestamp, and full stack trace.
     */
    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US).format(Date())
        val appVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "${pInfo.versionName} (${pInfo.longVersionCode})"
        } catch (e: Exception) {
            "unknown"
        }

        return buildString {
            appendLine("=== WaSMS Gateway Crash Report ===")
            appendLine()
            appendLine("Timestamp: $timestamp")
            appendLine("App Version: $appVersion")
            appendLine("Package: $packageName")
            appendLine()
            appendLine("--- Device Info ---")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build: ${Build.DISPLAY}")
            appendLine()
            appendLine("--- Thread ---")
            appendLine("Name: ${thread.name}")
            appendLine("ID: ${thread.id}")
            appendLine("Priority: ${thread.priority}")
            appendLine()
            appendLine("--- Exception ---")
            appendLine("Type: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            appendLine("--- Stack Trace ---")
            append(stackTrace)
            appendLine()
            appendLine("=== End of Crash Report ===")
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_service_desc)
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.notification_channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_channel_alerts_desc)
        }

        val digestChannel = NotificationChannel(
            CHANNEL_DIGEST,
            getString(R.string.notification_channel_digest),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_digest_desc)
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertChannel, digestChannel))
    }

    companion object {
        const val CHANNEL_SERVICE = "wasms_service"
        const val CHANNEL_ALERTS = "wasms_alerts"
        const val CHANNEL_DIGEST = "wasms_digest"
        const val CRASH_LOG_FILE = "crash_log.txt"
    }
}
