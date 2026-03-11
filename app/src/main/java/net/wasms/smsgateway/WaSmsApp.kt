package net.wasms.smsgateway

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
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

    /** Set to true if Hilt initialization failed — Activities should check this. */
    var hiltFailed = false
        private set

    override fun onCreate() {
        Log.d(TAG, "BOOT STEP 1/6: Installing crash reporter")
        installCrashReporter()

        Log.d(TAG, "BOOT STEP 2/6: Loading SQLCipher native library")
        try {
            System.loadLibrary("sqlcipher")
            Log.d(TAG, "BOOT STEP 2/6: SQLCipher loaded OK")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "BOOT STEP 2/6: SQLCipher FAILED (will use unencrypted DB)", e)
        }

        Log.d(TAG, "BOOT STEP 3/6: Calling super.onCreate() (Hilt DI)")
        try {
            super.onCreate()
            Log.d(TAG, "BOOT STEP 3/6: Hilt DI initialized OK")
        } catch (e: Exception) {
            Log.e(TAG, "BOOT STEP 3/6: FATAL — Hilt DI FAILED", e)
            hiltFailed = true
            saveCrashLog(Thread.currentThread(), e)
            // Don't throw — let CrashReportActivity show the error
            return
        }

        Log.d(TAG, "BOOT STEP 4/6: Planting Timber")
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Log.d(TAG, "BOOT STEP 5/6: Creating notification channels")
        try {
            createNotificationChannels()
        } catch (e: Exception) {
            Log.e(TAG, "BOOT STEP 5/6: Notification channels FAILED", e)
        }

        Log.d(TAG, "BOOT STEP 6/6: onCreate() COMPLETE — app started successfully")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) Log.DEBUG
                else Log.INFO
            )
            .build()

    private fun installCrashReporter() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Saves crash info to file AND logcat so it's always accessible.
     */
    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(thread, throwable)
            // Always log to logcat (visible via logcat viewer apps)
            Log.e(TAG, "========== CRASH REPORT START ==========")
            crashLog.lines().forEach { line -> Log.e(TAG, line) }
            Log.e(TAG, "========== CRASH REPORT END ==========")

            // Write to internal files dir
            val dir = try {
                filesDir
            } catch (e: Exception) {
                File(applicationInfo.dataDir, "files").also { it.mkdirs() }
            }
            val crashFile = File(dir, CRASH_LOG_FILE)
            crashFile.writeText(crashLog)
            Log.e(TAG, "Crash log saved to: ${crashFile.absolutePath}")

            // Also write to external files dir (user can access via file manager)
            try {
                val extDir = getExternalFilesDir(null)
                if (extDir != null) {
                    val extCrashFile = File(extDir, CRASH_LOG_FILE)
                    extCrashFile.writeText(crashLog)
                    Log.e(TAG, "Crash log also saved to: ${extCrashFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not write external crash log", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log entirely", e)
        }
    }

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
        private const val TAG = "WaSMS_BOOT"
        const val CHANNEL_SERVICE = "wasms_service"
        const val CHANNEL_ALERTS = "wasms_alerts"
        const val CHANNEL_DIGEST = "wasms_digest"
        const val CRASH_LOG_FILE = "crash_log.txt"
    }
}
