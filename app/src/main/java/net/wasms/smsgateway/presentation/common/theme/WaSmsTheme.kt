package net.wasms.smsgateway.presentation.common.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// Material 3 Color Schemes
// Primary: Green (#128C7E) matching wasms.net website branding
// =============================================================================

private val LightColorScheme = lightColorScheme(
    primary = Green50,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Slate10,
    tertiary = Teal50,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Color.White,
    onBackground = Slate20,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Slate95,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = Slate60,
    outlineVariant = Slate80,
    surfaceContainerLowest = Color.White,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = Green70,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Slate70,
    onSecondary = Slate10,
    secondaryContainer = Slate30,
    onSecondaryContainer = Slate90,
    tertiary = Teal70,
    onTertiary = Teal10,
    tertiaryContainer = Teal30,
    onTertiaryContainer = Teal90,
    error = Red70,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Slate10,
    onBackground = Slate90,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Slate30,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = Slate50,
    outlineVariant = Slate30,
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

// =============================================================================
// Custom Status Colors — accessible via WaSmsTheme.statusColors
// =============================================================================

data class StatusColors(
    val sending: Color,
    val sendingContainer: Color,
    val idle: Color,
    val idleContainer: Color,
    val paused: Color,
    val pausedContainer: Color,
    val offline: Color,
    val offlineContainer: Color,
    val delivered: Color,
    val deliveredContainer: Color,
    val failed: Color,
    val failedContainer: Color,
    val online: Color,
    val onlineContainer: Color,
    val error: Color,
    val errorContainer: Color,
)

private val LightStatusColors = StatusColors(
    sending = StatusSending,
    sendingContainer = StatusSendingLight,
    idle = StatusIdle,
    idleContainer = StatusIdleLight,
    paused = StatusPausedAmber,
    pausedContainer = StatusPausedAmberLight,
    offline = StatusOffline,
    offlineContainer = StatusOfflineLight,
    delivered = StatusDelivered,
    deliveredContainer = StatusDeliveredLight,
    failed = StatusFailed,
    failedContainer = StatusFailedLight,
    online = StatusOnline,
    onlineContainer = StatusOnlineLight,
    error = StatusError,
    errorContainer = StatusErrorLight,
)

private val DarkStatusColors = StatusColors(
    sending = StatusSendingDark,
    sendingContainer = Green20,
    idle = StatusIdleDark,
    idleContainer = Slate20,
    paused = StatusPausedAmberDark,
    pausedContainer = Color(0xFF78350F),
    offline = StatusOfflineDark,
    offlineContainer = Red20,
    delivered = StatusDeliveredDark,
    deliveredContainer = Color(0xFF052E16),
    failed = StatusFailedDark,
    failedContainer = Red20,
    online = StatusOnlineDark,
    onlineContainer = Color(0xFF052E16),
    error = StatusErrorDark,
    errorContainer = Color(0xFF431407),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

// =============================================================================
// Theme Accessor
// =============================================================================

object WaSmsTheme {
    val statusColors: StatusColors
        @Composable
        get() = LocalStatusColors.current
}

// =============================================================================
// Theme Composable
// =============================================================================

@Composable
fun WaSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use dark green for status bar to match WaSMS branding
            window.statusBarColor = if (darkTheme) {
                colorScheme.surface.toArgb()
            } else {
                Green40.toArgb() // #075E54 dark green
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
