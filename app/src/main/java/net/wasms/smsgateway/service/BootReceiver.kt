package net.wasms.smsgateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * BroadcastReceiver for BOOT_COMPLETED and QUICKBOOT_POWERON.
 *
 * After the device boots, delegates to [AppLifecycleManager.ensureStarted] which
 * handles the full startup sequence (SIM detect → WebSocket → service → workers).
 * The lifecycle manager itself checks authentication, so we don't need to duplicate that here.
 *
 * Declared in AndroidManifest.xml with intent filters:
 * - android.intent.action.BOOT_COMPLETED
 * - android.intent.action.QUICKBOOT_POWERON (for OEM fast-boot support)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var appLifecycleManager: AppLifecycleManager

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        Timber.i("BootReceiver: Device boot detected (action=%s)", action)

        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                // Give the system time to stabilize (SIM cards, network, etc.)
                delay(BOOT_DELAY_MS)

                // AppLifecycleManager checks auth, detects SIMs, connects WebSocket,
                // starts service, and schedules workers — all idempotently.
                appLifecycleManager.ensureStarted()
                Timber.i("BootReceiver: AppLifecycleManager.ensureStarted() called")
            } catch (e: Exception) {
                Timber.e(e, "BootReceiver: Error during boot handling")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Delay before starting subsystems after boot (milliseconds). */
        private const val BOOT_DELAY_MS = 5_000L
    }
}
