package net.wasms.smsgateway.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellSignalStrength
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.datetime.Clock
import net.wasms.smsgateway.data.local.db.dao.SmsMessageDao
import net.wasms.smsgateway.domain.model.DeviceHealth
import net.wasms.smsgateway.domain.model.SmsState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects device health metrics for heartbeat reporting.
 *
 * Gathers battery level, charging state, network connectivity, signal strength,
 * queue depth, and device uptime. This data is sent to the WaSMS server via
 * heartbeat messages so the server can make intelligent routing decisions
 * (e.g., avoid sending messages to a device with low battery or no signal).
 */
@Singleton
class HealthReporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsMessageDao: SmsMessageDao
) {

    /**
     * Collect a snapshot of the device's current health.
     *
     * All metrics are gathered from Android system services. Any individual
     * metric that fails to collect defaults to a safe value rather than
     * throwing an exception.
     */
    suspend fun collectHealth(): DeviceHealth {
        return DeviceHealth(
            batteryLevel = getBatteryLevel(),
            isCharging = getChargingState(),
            isWifiConnected = isWifiConnected(),
            signalStrength = getSignalStrength(),
            networkType = getNetworkType(),
            queueDepth = getQueueDepth(),
            uptimeSeconds = getUptimeSeconds(),
            timestamp = Clock.System.now()
        )
    }

    // -------------------------------------------------------------------------
    // Battery
    // -------------------------------------------------------------------------

    /**
     * Get the current battery level as a percentage (0-100).
     * Uses [BatteryManager] system service (available since API 21).
     */
    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to get battery level")
            -1
        }
    }

    /**
     * Check if the device is currently charging.
     * Checks for AC, USB, or wireless charging sources.
     */
    private fun getChargingState(): Boolean {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryManager?.isCharging ?: false
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to get charging state")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    /**
     * Check if the device is connected to a WiFi network.
     */
    private fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to check WiFi state")
            false
        }
    }

    /**
     * Get the current signal strength on a 0-4 scale.
     *
     * Maps from Android's SignalStrength levels:
     * - 0: SIGNAL_STRENGTH_NONE_OR_UNKNOWN
     * - 1: SIGNAL_STRENGTH_POOR
     * - 2: SIGNAL_STRENGTH_MODERATE
     * - 3: SIGNAL_STRENGTH_GOOD
     * - 4: SIGNAL_STRENGTH_GREAT
     */
    private fun getSignalStrength(): Int {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signalStrength = tm.signalStrength
                if (signalStrength != null) {
                    signalStrength.level.coerceIn(0, 4)
                } else {
                    0
                }
            } else {
                // Fallback for pre-P devices (shouldn't hit this since minSdk=26,
                // but just in case)
                0
            }
        } catch (e: SecurityException) {
            Timber.w("HealthReporter: Phone permission required for signal strength")
            0
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to get signal strength")
            0
        }
    }

    /**
     * Determine the current network type as a human-readable string.
     *
     * Returns one of: "wifi", "5g", "4g", "3g", "2g", "ethernet", "none"
     */
    private fun getNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "none"
            val network = cm.activeNetwork ?: return "none"
            val capabilities = cm.getNetworkCapabilities(network) ?: return "none"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    getCellularNetworkGeneration()
                }
                else -> "none"
            }
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to get network type")
            "none"
        }
    }

    /**
     * Determine the cellular network generation (2g/3g/4g/5g) from TelephonyManager.
     */
    private fun getCellularNetworkGeneration(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return "cellular"

            @Suppress("DEPRECATION")
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5g"
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_IWLAN -> "4g"
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3g"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM -> "2g"
                TelephonyManager.NETWORK_TYPE_UNKNOWN -> "none"
                else -> "cellular"
            }
        } catch (e: SecurityException) {
            Timber.w("HealthReporter: Phone permission required for network generation")
            "cellular"
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to determine cellular generation")
            "cellular"
        }
    }

    // -------------------------------------------------------------------------
    // Queue
    // -------------------------------------------------------------------------

    /**
     * Get the number of messages pending in the send queue.
     *
     * Counts messages in CREATED, QUEUED, and ASSIGNED_DEVICE states.
     */
    private suspend fun getQueueDepth(): Int {
        return try {
            val created = smsMessageDao.countByState(SmsState.CREATED)
            val queued = smsMessageDao.countByState(SmsState.QUEUED)
            val assigned = smsMessageDao.countByState(SmsState.ASSIGNED_DEVICE)
            created + queued + assigned
        } catch (e: Exception) {
            Timber.w(e, "HealthReporter: Failed to get queue depth")
            0
        }
    }

    // -------------------------------------------------------------------------
    // Uptime
    // -------------------------------------------------------------------------

    /**
     * Get the device uptime in seconds since last boot.
     *
     * Uses [SystemClock.elapsedRealtime] which includes time spent in deep sleep
     * and is not affected by wall clock changes.
     */
    private fun getUptimeSeconds(): Long {
        return SystemClock.elapsedRealtime() / 1000
    }
}
