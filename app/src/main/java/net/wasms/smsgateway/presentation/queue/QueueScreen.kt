package net.wasms.smsgateway.presentation.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.wasms.smsgateway.domain.model.SmsMessage
import net.wasms.smsgateway.domain.model.SmsState
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme

/**
 * Queue monitor screen — Layer 3 of the information hierarchy.
 *
 * Shows a list of recent messages with masked destinations, state badges,
 * and timestamps. Supports filtering by state and pull-to-refresh.
 *
 * Anti-pattern avoided: NOT showing this on the Home screen (Agent 15:
 * "A scrolling feed of sent...sent...sent creates information noise").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Message Queue",
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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter chips
                FilterChipRow(
                    selectedFilter = uiState.selectedFilter,
                    totalCount = uiState.totalCount,
                    onFilterSelected = viewModel::setFilter,
                )

                if (uiState.isEmpty) {
                    EmptyQueueState(
                        filter = uiState.selectedFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.id },
                        ) { message ->
                            MessageItem(message = message)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Filter Chips
// =============================================================================

@Composable
private fun FilterChipRow(
    selectedFilter: QueueFilter,
    totalCount: Int,
    onFilterSelected: (QueueFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(QueueFilter.entries.toList()) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = if (filter == QueueFilter.ALL) "${filter.label} ($totalCount)" else filter.label,
                    )
                },
            )
        }
    }
}

// =============================================================================
// Message List Item
// =============================================================================

@Composable
private fun MessageItem(message: SmsMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Destination (masked for privacy)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = maskPhoneNumber(message.destination),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.errorMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = WaSmsTheme.statusColors.failed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // State badge
            SmsStateBadge(state = message.state)
        }
    }
}

/**
 * State badge for message items.
 */
@Composable
private fun SmsStateBadge(state: SmsState) {
    val (label, color) = when (state) {
        SmsState.CREATED -> "Created" to MaterialTheme.colorScheme.onSurfaceVariant
        SmsState.QUEUED -> "Queued" to WaSmsTheme.statusColors.idle
        SmsState.ASSIGNED_DEVICE -> "Assigned" to WaSmsTheme.statusColors.sending
        SmsState.DISPATCHED_TO_SIM -> "Sending" to WaSmsTheme.statusColors.sending
        SmsState.SENT -> "Sent" to WaSmsTheme.statusColors.online
        SmsState.DELIVERED -> "Delivered" to WaSmsTheme.statusColors.delivered
        SmsState.FAILED_ATTEMPT -> "Retrying" to WaSmsTheme.statusColors.paused
        SmsState.FAILED_PERMANENT -> "Failed" to WaSmsTheme.statusColors.failed
        SmsState.EXPIRED -> "Expired" to WaSmsTheme.statusColors.error
        SmsState.REJECTED -> "Rejected" to WaSmsTheme.statusColors.failed
        SmsState.CANCELLED -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = when (state) {
        SmsState.DELIVERED -> WaSmsTheme.statusColors.deliveredContainer
        SmsState.SENT -> WaSmsTheme.statusColors.onlineContainer
        SmsState.DISPATCHED_TO_SIM, SmsState.ASSIGNED_DEVICE -> WaSmsTheme.statusColors.sendingContainer
        SmsState.FAILED_PERMANENT, SmsState.REJECTED -> WaSmsTheme.statusColors.failedContainer
        SmsState.FAILED_ATTEMPT -> WaSmsTheme.statusColors.pausedContainer
        SmsState.EXPIRED -> WaSmsTheme.statusColors.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// =============================================================================
// Empty State
// =============================================================================

@Composable
private fun EmptyQueueState(
    filter: QueueFilter,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (filter) {
                    QueueFilter.ALL -> "No messages in queue"
                    QueueFilter.PENDING -> "No pending messages"
                    QueueFilter.SENT -> "No sent messages"
                    QueueFilter.FAILED -> "No failed messages"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Messages will appear here when campaigns are active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// =============================================================================
// Utility Functions
// =============================================================================

/**
 * Mask phone number for privacy display.
 * "+923001234567" becomes "+92 3** *** **67"
 */
private fun maskPhoneNumber(phone: String): String {
    val cleaned = phone.replace(Regex("[^+\\d]"), "")
    if (cleaned.length < 6) return cleaned

    val prefix = cleaned.take(3)
    val suffix = cleaned.takeLast(2)
    val middleLength = cleaned.length - 5
    val masked = "*".repeat(middleLength)

    // Format with spaces for readability
    return buildString {
        append(prefix)
        append(" ")
        var i = 0
        for (ch in masked) {
            append(ch)
            i++
            if (i % 3 == 0 && i < masked.length) append(" ")
        }
        append(suffix)
    }
}

/**
 * Format an Instant to a human-readable time string.
 */
private fun formatTimestamp(instant: Instant): String {
    return try {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        val month = local.monthNumber.toString().padStart(2, '0')
        val day = local.dayOfMonth.toString().padStart(2, '0')
        "$day/$month $hour:$minute"
    } catch (_: Exception) {
        "--"
    }
}
