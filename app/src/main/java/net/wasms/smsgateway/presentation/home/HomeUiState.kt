package net.wasms.smsgateway.presentation.home

import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.model.DeviceStatus

/**
 * UI state for the Home screen.
 *
 * Follows Agent 15's 3-layer information hierarchy:
 * - Layer 1 (glanceable): deviceStatus, sentToday, queueRemaining, activeSim
 * - Layer 2 (summary): deliveryRate, messagesPerHour, campaignName, creditBalance
 * - All other detail lives in the Queue and Settings screens (Layer 3)
 */
data class HomeUiState(
    // Layer 1 — Glanceable (no scrolling needed)
    val deviceStatus: DeviceStatus = DeviceStatus.OFFLINE,
    val sentToday: Int = 0,
    val queueRemaining: Int = 0,
    val activeSim: String? = null,

    // Layer 2 — Summary (scrollable cards)
    val deliveryRate: Float = 0f,
    val messagesPerHour: Int = 0,
    val campaignName: String? = null,
    val creditBalance: String? = null,

    // Connection
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,

    // Meta
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)
