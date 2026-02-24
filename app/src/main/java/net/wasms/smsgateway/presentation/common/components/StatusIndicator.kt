package net.wasms.smsgateway.presentation.common.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.wasms.smsgateway.domain.model.DeviceStatus
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme

/**
 * Pulsing status dot with label.
 *
 * The dot pulses when the device is actively sending. Static for all other states.
 * Used on Home screen header and Settings > Connection section.
 */
@Composable
fun StatusIndicator(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
    dotSize: Dp = 12.dp,
    showLabel: Boolean = true,
    labelStyle: @Composable () -> Unit = {},
) {
    val color = statusColor(status)
    val shouldPulse = status == DeviceStatus.SENDING || status == DeviceStatus.ONLINE

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PulsingDot(
            color = color,
            size = dotSize,
            pulse = shouldPulse,
        )
        if (showLabel) {
            Text(
                text = status.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

/**
 * Animated pulsing dot. Pulses between 100% and 30% opacity when [pulse] is true.
 */
@Composable
fun PulsingDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    pulse: Boolean = false,
) {
    val alpha: Float = if (pulse) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        animatedAlpha
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(color = color, shape = CircleShape),
    )
}

/**
 * Maps DeviceStatus to the correct theme-aware status color.
 */
@Composable
fun statusColor(status: DeviceStatus): Color {
    val colors = WaSmsTheme.statusColors
    return when (status) {
        DeviceStatus.SENDING -> colors.sending
        DeviceStatus.IDLE -> colors.idle
        DeviceStatus.PAUSED -> colors.paused
        DeviceStatus.OFFLINE -> colors.offline
        DeviceStatus.ONLINE -> colors.online
        DeviceStatus.ERROR -> colors.error
    }
}

/**
 * Compact status chip — dot + label in a rounded container, suitable for lists.
 */
@Composable
fun StatusChip(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
) {
    val color = statusColor(status)
    val containerColor = statusContainerColor(status)

    Row(
        modifier = modifier
            .background(containerColor, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PulsingDot(
            color = color,
            size = 8.dp,
            pulse = status == DeviceStatus.SENDING,
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
private fun statusContainerColor(status: DeviceStatus): Color {
    val colors = WaSmsTheme.statusColors
    return when (status) {
        DeviceStatus.SENDING -> colors.sendingContainer
        DeviceStatus.IDLE -> colors.idleContainer
        DeviceStatus.PAUSED -> colors.pausedContainer
        DeviceStatus.OFFLINE -> colors.offlineContainer
        DeviceStatus.ONLINE -> colors.onlineContainer
        DeviceStatus.ERROR -> colors.errorContainer
    }
}
