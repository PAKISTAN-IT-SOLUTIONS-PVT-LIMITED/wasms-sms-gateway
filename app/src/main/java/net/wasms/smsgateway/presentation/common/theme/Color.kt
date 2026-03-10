package net.wasms.smsgateway.presentation.common.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// WaSMS Color System — matching wasms.net website branding
// Primary: Green (#128C7E) — WhatsApp green
// =============================================================================

// Primary Green (WaSMS brand)
val Green10 = Color(0xFF00201D)
val Green20 = Color(0xFF003731)
val Green30 = Color(0xFF005047)
val Green40 = Color(0xFF075E54) // primaryDark
val Green50 = Color(0xFF128C7E) // primary
val Green60 = Color(0xFF25D366) // accent / light green
val Green70 = Color(0xFF5EDDA0)
val Green80 = Color(0xFFA0E8C5)
val Green90 = Color(0xFFB2DFDB) // primaryContainer
val Green95 = Color(0xFFE0F2F1)
val Green99 = Color(0xFFF0FFF4)

// Secondary (Slate — for text and surfaces)
val Slate10 = Color(0xFF0F172A)
val Slate20 = Color(0xFF1E293B) // text primary
val Slate30 = Color(0xFF334155)
val Slate40 = Color(0xFF475569)
val Slate50 = Color(0xFF64748B)
val Slate60 = Color(0xFF94A3B8)
val Slate70 = Color(0xFFB0BEC5)
val Slate80 = Color(0xFFCBD5E1)
val Slate90 = Color(0xFFE2E8F0)
val Slate95 = Color(0xFFF1F5F9)
val Slate99 = Color(0xFFF8FAFC)

// Tertiary (Teal)
val Teal10 = Color(0xFF001F1A)
val Teal20 = Color(0xFF003D32)
val Teal30 = Color(0xFF005B4B)
val Teal40 = Color(0xFF007A65)
val Teal50 = Color(0xFF009980)
val Teal60 = Color(0xFF26B89B)
val Teal70 = Color(0xFF4DD0B5)
val Teal80 = Color(0xFF80E8D0)
val Teal90 = Color(0xFFB3F0E5)
val Teal95 = Color(0xFFE0F7F3)

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

// Sending (Green — matches primary brand)
val StatusSending = Green50
val StatusSendingLight = Green90
val StatusSendingDark = Green40

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

// Delivered (Green — accent green)
val StatusDelivered = Color(0xFF25D366)
val StatusDeliveredLight = Color(0xFFDCFCE7)
val StatusDeliveredDark = Color(0xFF128C7E)

// Failed (Red — shares with offline but semantically distinct)
val StatusFailed = Color(0xFFEF4444)
val StatusFailedLight = Color(0xFFFEE2E2)
val StatusFailedDark = Color(0xFFDC2626)

// Online (Green — device connected)
val StatusOnline = Color(0xFF25D366)
val StatusOnlineLight = Color(0xFFDCFCE7)
val StatusOnlineDark = Color(0xFF128C7E)

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
val OnSurfaceLight = Color(0xFF1E293B)
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
