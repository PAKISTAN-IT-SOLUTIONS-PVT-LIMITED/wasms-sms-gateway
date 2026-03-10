package net.wasms.smsgateway.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.wasms.smsgateway.presentation.home.HomeScreen
import net.wasms.smsgateway.presentation.onboarding.OnboardingScreen
import net.wasms.smsgateway.presentation.queue.QueueScreen
import net.wasms.smsgateway.presentation.settings.SettingsScreen

/**
 * Root navigation host for the WaSMS app.
 *
 * Navigation logic:
 * - If device is NOT registered -> start at Onboarding
 * - If device IS registered -> start at Home
 *
 * The bottom navigation bar is only visible on the main screens (Home, Queue, Settings),
 * NOT during onboarding.
 *
 * Transition: Simple fade to keep the "glance and go" feel smooth.
 */
@Composable
fun WaSmsNavHost(
    navViewModel: NavigationViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    var isRegistered by remember { mutableStateOf<Boolean?>(null) }

    // Check registration status once on launch
    LaunchedEffect(Unit) {
        isRegistered = navViewModel.isDeviceRegistered()
    }

    // Wait until we know the registration status
    val startDestination = when (isRegistered) {
        true -> Screen.Home.route
        false -> Screen.Onboarding.route
        null -> return // Still loading — show nothing (splash screen covers this)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Bottom bar visible only on main screens, not onboarding
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Queue.route,
        Screen.Settings.route,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                WaSmsBottomBar(
                    currentRoute = currentRoute,
                    onItemClick = { screen ->
                        navController.navigate(screen.route) {
                            // Pop up to the start destination to avoid building up a large stack
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(200))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
        ) {
            // Onboarding flow
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                )
            }

            // Home dashboard
            composable(Screen.Home.route) {
                HomeScreen()
            }

            // Message queue
            composable(Screen.Queue.route) {
                QueueScreen()
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onDeviceDisconnected = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

// =============================================================================
// Bottom Navigation Bar
// =============================================================================

@Composable
private fun WaSmsBottomBar(
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        BottomNavItem.entries.forEach { item ->
            val selected = currentRoute == item.screen.route

            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(item.screen) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}
