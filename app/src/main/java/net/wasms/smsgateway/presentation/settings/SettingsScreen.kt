package net.wasms.smsgateway.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wasms.smsgateway.domain.model.ConnectionState
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.domain.model.SimCard
import net.wasms.smsgateway.presentation.common.components.StatusIndicator
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme

/**
 * Settings screen — Layer 3 of the information hierarchy.
 *
 * Organized into progressive disclosure sections:
 * - Account: Team, device name, device ID
 * - SIM Management: Active SIMs with daily limits
 * - Sending: Pause/resume, speed display
 * - Connection: WebSocket status, last sync
 * - About: Version, server URL
 * - Danger Zone: Disconnect device
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDeviceDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    if (showDisconnectDialog) {
        DisconnectConfirmationDialog(
            onConfirm = {
                showDisconnectDialog = false
                viewModel.disconnectDevice(onDeviceDisconnected)
            },
            onDismiss = { showDisconnectDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Account Section
            AccountSection(uiState)

            // SIM Management Section
            SimManagementSection(uiState.simCards)

            // Sending Section
            SendingSection(
                status = uiState.deviceStatus,
                sendSpeed = uiState.sendSpeed,
                onPause = viewModel::pauseSending,
                onResume = viewModel::resumeSending,
            )

            // Connection Section
            ConnectionSection(
                connectionState = uiState.connectionState,
                deviceStatus = uiState.deviceStatus,
            )

            // About Section
            AboutSection(
                appVersion = uiState.appVersion,
            )

            // Danger Zone
            DangerZoneSection(
                onDisconnectClick = { showDisconnectDialog = true },
                isDisconnecting = uiState.isDisconnecting,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// Account Section
// =============================================================================

@Composable
private fun AccountSection(state: SettingsUiState) {
    SettingsSection(title = "Account", icon = Icons.Filled.PhoneAndroid) {
        SettingsRow(label = "Team", value = state.teamName)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SettingsRow(label = "Device Name", value = state.deviceName)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SettingsRow(label = "Device ID", value = state.deviceId)
    }
}

// =============================================================================
// SIM Management Section
// =============================================================================

@Composable
private fun SimManagementSection(simCards: List<SimCard>) {
    SettingsSection(title = "SIM Management", icon = Icons.Filled.SimCard) {
        if (simCards.isEmpty()) {
            Text(
                text = "No SIM cards detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            simCards.forEachIndexed { index, sim ->
                SimCardRow(sim = sim)
                if (index < simCards.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SimCardRow(sim: SimCard) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sim.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Limit: ${sim.dailyLimit}/day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Sent: ${sim.totalSent}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Left: ${sim.remainingToday}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sim.isThrottled) {
                        WaSmsTheme.statusColors.failed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        // Active/inactive indicator
        val statusColor = if (sim.isActive) {
            WaSmsTheme.statusColors.online
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = if (sim.isActive) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = statusColor,
        )
    }
}

// =============================================================================
// Sending Section
// =============================================================================

@Composable
private fun SendingSection(
    status: DeviceStatus,
    sendSpeed: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    SettingsSection(title = "Sending", icon = Icons.Filled.Speed) {
        // Pause/Resume toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Message Sending",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (status == DeviceStatus.PAUSED) "Paused" else "Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == DeviceStatus.PAUSED) {
                        WaSmsTheme.statusColors.paused
                    } else {
                        WaSmsTheme.statusColors.online
                    },
                )
            }
            if (status == DeviceStatus.PAUSED) {
                OutlinedButton(onClick = onResume) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Resume")
                }
            } else {
                OutlinedButton(onClick = onPause) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Send speed display
        SettingsRow(label = "Send Speed", value = sendSpeed)
    }
}

// =============================================================================
// Connection Section
// =============================================================================

@Composable
private fun ConnectionSection(
    connectionState: ConnectionState,
    deviceStatus: DeviceStatus,
) {
    val (statusText, statusColor) = when (connectionState) {
        ConnectionState.CONNECTED -> "Connected" to WaSmsTheme.statusColors.online
        ConnectionState.CONNECTING -> "Connecting..." to WaSmsTheme.statusColors.paused
        ConnectionState.RECONNECTING -> "Reconnecting..." to WaSmsTheme.statusColors.paused
        ConnectionState.DISCONNECTED -> "Disconnected" to WaSmsTheme.statusColors.offline
        ConnectionState.FAILED -> "Connection Failed" to WaSmsTheme.statusColors.failed
    }

    SettingsSection(title = "Connection", icon = Icons.Filled.Wifi) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WebSocket Status",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            StatusIndicator(
                status = deviceStatus,
                dotSize = 10.dp,
                showLabel = false,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// =============================================================================
// About Section
// =============================================================================

@Composable
private fun AboutSection(
    appVersion: String,
) {
    SettingsSection(title = "About") {
        SettingsRow(label = "App Version", value = appVersion)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SettingsRow(label = "Server", value = "wasms.net")
    }
}

// =============================================================================
// Danger Zone
// =============================================================================

@Composable
private fun DangerZoneSection(
    onDisconnectClick: () -> Unit,
    isDisconnecting: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = WaSmsTheme.statusColors.failedContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "Danger Zone",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WaSmsTheme.statusColors.failed,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Disconnecting will remove this device from your WaSMS account. You will need to re-register to use it again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDisconnectClick,
                enabled = !isDisconnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WaSmsTheme.statusColors.failed,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isDisconnecting) "Disconnecting..." else "Disconnect Device")
            }
        }
    }
}

// =============================================================================
// Disconnect Confirmation Dialog
// =============================================================================

@Composable
private fun DisconnectConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Disconnect Device?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "This will remove this device from your WaSMS account. " +
                    "Any pending messages in the queue will be reassigned or cancelled. " +
                    "You will need to scan a new QR code to reconnect.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = WaSmsTheme.statusColors.failed,
                ),
            ) {
                Text("Disconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// =============================================================================
// Reusable Settings Components
// =============================================================================

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
