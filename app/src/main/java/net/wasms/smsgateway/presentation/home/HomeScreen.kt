package net.wasms.smsgateway.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.presentation.common.components.MiniStatCard
import net.wasms.smsgateway.presentation.common.components.StatCard
import net.wasms.smsgateway.presentation.common.components.StatusIndicator
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme

/**
 * Home dashboard — the primary screen of the WaSMS app.
 *
 * Implements Agent 15's 3-layer information hierarchy:
 * - Layer 1 (visible without scrolling): status, sent today, queue, active SIM
 * - Layer 2 (scrollable summary cards): delivery rate, messages/hour, campaign, credits
 * - Layer 3 (other screens): Queue list, Settings details
 *
 * Design philosophy: "Glance and go" — the user checks this screen to confirm
 * their phone is doing its job. Minimal cognitive load.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error via snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                status = uiState.deviceStatus,
                onPauseClick = viewModel::pauseSending,
                onResumeClick = viewModel::resumeSending,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // =========================================================
                // Layer 1 — Glanceable (4 chunks max per Miller's Law)
                // =========================================================
                Spacer(modifier = Modifier.height(8.dp))

                GlanceableHeader(uiState)

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================
                // Layer 2 — Summary cards (below the fold)
                // =========================================================
                SummaryCards(uiState)

                // Connection status footer
                ConnectionFooter(uiState.connectionState)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// =============================================================================
// Layer 1: Glanceable Header
// =============================================================================

@Composable
private fun GlanceableHeader(state: HomeUiState) {
    // Status indicator — most important info
    StatusIndicator(
        status = state.deviceStatus,
        dotSize = 16.dp,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Big number: Messages sent today
    Column {
        Text(
            text = "${state.sentToday}",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "messages sent today",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Queue + Active SIM in a row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MiniStatCard(
            title = "Queue",
            value = "${state.queueRemaining}",
            modifier = Modifier.weight(1f),
        )
        MiniStatCard(
            title = "Active SIM",
            value = state.activeSim ?: "None",
            modifier = Modifier.weight(1f),
        )
    }
}

// =============================================================================
// Layer 2: Summary Cards
// =============================================================================

@Composable
private fun SummaryCards(state: HomeUiState) {
    Text(
        text = "Summary",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    // Delivery rate
    val deliveryPercent = "%.1f%%".format(state.deliveryRate * 100)
    val deliveryColor = when {
        state.deliveryRate >= 0.95f -> WaSmsTheme.statusColors.delivered
        state.deliveryRate >= 0.85f -> WaSmsTheme.statusColors.paused
        else -> WaSmsTheme.statusColors.failed
    }
    StatCard(
        title = "Delivery Rate",
        value = deliveryPercent,
        valueColor = deliveryColor,
        subtitle = if (state.sentToday > 0) "Based on ${state.sentToday} messages today" else null,
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Messages per hour + Campaign in a row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            title = "Messages/Hour",
            value = "${state.messagesPerHour}",
            subtitle = "Current rate",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            title = if (state.campaignName != null) "Campaign" else "Credits",
            value = state.campaignName ?: state.creditBalance ?: "--",
            subtitle = if (state.campaignName != null) "Active" else "Balance",
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
}

// =============================================================================
// Connection Footer
// =============================================================================

@Composable
private fun ConnectionFooter(connectionState: ConnectionState) {
    val (text, color) = when (connectionState) {
        ConnectionState.CONNECTED -> "Connected to server" to WaSmsTheme.statusColors.online
        ConnectionState.CONNECTING -> "Connecting..." to WaSmsTheme.statusColors.paused
        ConnectionState.RECONNECTING -> "Reconnecting..." to WaSmsTheme.statusColors.paused
        ConnectionState.DISCONNECTED -> "Disconnected" to WaSmsTheme.statusColors.offline
        ConnectionState.FAILED -> "Connection failed" to WaSmsTheme.statusColors.failed
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .padding(end = 0.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = color)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

// =============================================================================
// Top App Bar
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    status: DeviceStatus,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "WaSMS Gateway",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            if (status == DeviceStatus.PAUSED) {
                IconButton(onClick = onResumeClick) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume sending",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else if (status.isActive) {
                IconButton(onClick = onPauseClick) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause sending",
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
