package net.wasms.smsgateway.presentation.common.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// WaSMS Color System
// Primary: Blue (#2563EB) — NOT green (avoids habituation per Agent 15)
// =============================================================================

// Primary Blue
val Blue10 = Color(0xFF001A40)
val Blue20 = Color(0xFF002D6E)
val Blue30 = Color(0xFF00429E)
val Blue40 = Color(0xFF1659C7)
val Blue50 = Color(0xFF2563EB) // Primary
val Blue60 = Color(0xFF5A8AEF)
val Blue70 = Color(0xFF89AEF5)
val Blue80 = Color(0xFFB5D0FA)
val Blue90 = Color(0xFFD9E6FD)
val Blue95 = Color(0xFFEDF2FE)
val Blue99 = Color(0xFFF8FAFF)

// Secondary (Slate)
val Slate10 = Color(0xFF0F172A)
val Slate20 = Color(0xFF1E293B)
val Slate30 = Color(0xFF334155)
val Slate40 = Color(0xFF475569)
val Slate50 = Color(0xFF64748B)
val Slate60 = Color(0xFF94A3B8)
val Slate70 = Color(0xFFB0BEC5)
val Slate80 = Color(0xFFCBD5E1)
val Slate90 = Color(0xFFE2E8F0)
val Slate95 = Color(0xFFF1F5F9)
val Slate99 = Color(0xFFF8FAFC)

// Tertiary (Indigo)
val Indigo10 = Color(0xFF1A0050)
val Indigo20 = Color(0xFF2D007A)
val Indigo30 = Color(0xFF3730A3)
val Indigo40 = Color(0xFF4338CA)
val Indigo50 = Color(0xFF4F46E5)
val Indigo60 = Color(0xFF6366F1)
val Indigo70 = Color(0xFF818CF8)
val Indigo80 = Color(0xFFA5B4FC)
val Indigo90 = Color(0xFFC7D2FE)
val Indigo95 = Color(0xFFE0E7FF)

// Error (Red)
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red30 = Color(0xFF93000A)
val Red40 = Color(0xFFBA1A1A)
val Red50 = Color(0xFFDC2626)
val Red60 = Color(0xFFEF4444)
val Red70 = Color(0xFFF87171)
val Red80 = Color(0xFFFCA5A5)
val Red90 = Color(0xFFFEE2E2)
val Red95 = Color(0xFFFEF2F2)

// =============================================================================
// Status Colors — used by StatusIndicator and throughout the app
// =============================================================================

// Sending (Blue — matches primary brand)
val StatusSending = Blue50
val StatusSendingLight = Blue90
val StatusSendingDark = Blue40

// Idle (Gray)
val StatusIdle = Slate50
val StatusIdleLight = Slate90
val StatusIdleDark = Slate40

// Paused (Amber)
val StatusPausedAmber = Color(0xFFF59E0B)
val StatusPausedAmberLight = Color(0xFFFEF3C7)
val StatusPausedAmberDark = Color(0xFFD97706)

// Offline (Red)
val StatusOffline = Red50
val StatusOfflineLight = Red90
val StatusOfflineDark = Red40

// Delivered (Green)
val StatusDelivered = Color(0xFF16A34A)
val StatusDeliveredLight = Color(0xFFDCFCE7)
val StatusDeliveredDark = Color(0xFF15803D)

// Failed (Red — shares with offline but semantically distinct)
val StatusFailed = Color(0xFFEF4444)
val StatusFailedLight = Color(0xFFFEE2E2)
val StatusFailedDark = Color(0xFFDC2626)

// Online (Green — device connected)
val StatusOnline = Color(0xFF22C55E)
val StatusOnlineLight = Color(0xFFDCFCE7)
val StatusOnlineDark = Color(0xFF16A34A)

// Error status (Orange-red)
val StatusError = Color(0xFFEA580C)
val StatusErrorLight = Color(0xFFFFF7ED)
val StatusErrorDark = Color(0xFFC2410C)

// =============================================================================
// Light Theme Surfaces
// =============================================================================
val SurfaceLight = Color(0xFFFAFAFA)
val SurfaceContainerLight = Color(0xFFF5F5F5)
val SurfaceContainerHighLight = Color(0xFFEEEEEE)
val SurfaceContainerHighestLight = Color(0xFFE0E0E0)
val OnSurfaceLight = Color(0xFF1C1B1F)
val OnSurfaceVariantLight = Color(0xFF49454F)

// =============================================================================
// Dark Theme Surfaces
// =============================================================================
val SurfaceDark = Color(0xFF121212)
val SurfaceContainerDark = Color(0xFF1E1E1E)
val SurfaceContainerHighDark = Color(0xFF2C2C2C)
val SurfaceContainerHighestDark = Color(0xFF383838)
val OnSurfaceDark = Color(0xFFE6E1E5)
val OnSurfaceVariantDark = Color(0xFFCAC4D0)
