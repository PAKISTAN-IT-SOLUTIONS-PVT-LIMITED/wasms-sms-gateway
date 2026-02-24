package net.wasms.smsgateway.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the WaSMS app.
 *
 * Three primary destinations accessible via bottom navigation: Home, Queue, Settings.
 * Onboarding is a one-time flow shown before the main app if the device is not registered.
 */
sealed class Screen(val route: String) {

    data object Onboarding : Screen("onboarding")

    data object Home : Screen("home")

    data object Queue : Screen("queue")

    data object Settings : Screen("settings")
}

/**
 * Bottom navigation items — limited to 3 per Agent 15's cognitive load rule.
 * "Maximum 4 info chunks on home screen" — the nav bar itself counts as structure,
 * so we keep it to 3 tabs.
 */
enum class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        screen = Screen.Home,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    QUEUE(
        screen = Screen.Queue,
        label = "Queue",
        selectedIcon = Icons.Filled.ListAlt,
        unselectedIcon = Icons.Outlined.ListAlt,
    ),
    SETTINGS(
        screen = Screen.Settings,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}
