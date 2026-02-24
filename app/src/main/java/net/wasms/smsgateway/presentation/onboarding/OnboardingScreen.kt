package net.wasms.smsgateway.presentation.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme

/**
 * 4-step onboarding flow per Agent 3's UX architecture.
 *
 * Uses "Endowed Progress" (Agent 15): Steps shown as "2 of 5" because step 1
 * (creating account on web) is already done. This reduces perceived remaining effort.
 *
 * Steps:
 * 1. Welcome — "Scan QR Code" primary action
 * 2. QR Scanner — camera placeholder + manual token fallback
 * 3. Permissions — SMS, Phone, Notifications with WHY explanations
 * 4. Success — celebration with proceed button
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            // Progress indicator
            Spacer(modifier = Modifier.height(16.dp))
            OnboardingProgress(
                currentStep = uiState.displayStep,
                totalSteps = uiState.displayTotalSteps,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step content with animated transitions
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "onboarding_step",
                modifier = Modifier.weight(1f),
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        onScanQrClick = viewModel::goToScanner,
                    )
                    OnboardingStep.SCANNER -> ScannerStep(
                        isRegistering = uiState.isRegistering,
                        onCodeSubmit = viewModel::registerDevice,
                    )
                    OnboardingStep.PERMISSIONS -> PermissionsStep(
                        uiState = uiState,
                        onPermissionsResult = viewModel::onPermissionsResult,
                        onContinue = viewModel::onPermissionsComplete,
                        onCheckPermissions = viewModel::checkPermissions,
                    )
                    OnboardingStep.SUCCESS -> SuccessStep(
                        onGetStarted = {
                            viewModel.completeOnboarding()
                            onOnboardingComplete()
                        },
                    )
                }
            }
        }
    }
}

// =============================================================================
// Progress Indicator — "Step X of 5" with Endowed Progress
// =============================================================================

@Composable
private fun OnboardingProgress(
    currentStep: Int,
    totalSteps: Int,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

// =============================================================================
// Step 1: Welcome
// =============================================================================

@Composable
private fun WelcomeStep(
    onScanQrClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Connect Your Device",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Scan the QR code from your WaSMS web dashboard to link this phone as an SMS gateway device.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onScanQrClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR Code")
        }
    }
}

// =============================================================================
// Step 2: QR Scanner (placeholder) + Manual Token Entry
// =============================================================================

@Composable
private fun ScannerStep(
    isRegistering: Boolean,
    onCodeSubmit: (String) -> Unit,
) {
    var manualCode by rememberSaveable { mutableStateOf("") }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Scan QR Code",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Live QR scanner with CameraX + ML Kit
        QrScannerView(
            onQrCodeScanned = { code -> onCodeSubmit(code) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Manual entry fallback (Agent 3: "If camera fails, show manual token entry")
        TextButton(
            onClick = { showManualEntry = !showManualEntry },
        ) {
            Text(
                text = if (showManualEntry) "Hide manual entry" else "Enter code manually instead",
            )
        }

        AnimatedVisibility(visible = showManualEntry) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    label = { Text("Registration Code") },
                    placeholder = { Text("e.g., ABC123-XYZ789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRegistering,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onCodeSubmit(manualCode.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = manualCode.isNotBlank() && !isRegistering,
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering...")
                    } else {
                        Text("Connect Device")
                    }
                }
            }
        }
    }
}

// =============================================================================
// Step 3: Permissions Request
// =============================================================================

@Composable
private fun PermissionsStep(
    uiState: OnboardingUiState,
    onPermissionsResult: (Map<String, Boolean>) -> Unit,
    onContinue: () -> Unit,
    onCheckPermissions: () -> Unit,
) {
    // Build the permission list based on Android version
    val permissions = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionsResult(results)
    }

    // Check current state on first composition
    LaunchedEffect(Unit) {
        onCheckPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "App Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "WaSMS needs a few permissions to send messages on your behalf. Here is exactly why each one is needed:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Permission cards with WHY explanations (Agent 15: pre-framing)
        PermissionCard(
            icon = Icons.Filled.Message,
            title = "SMS Permission",
            reason = "To send SMS messages from your phone on behalf of your WaSMS campaigns. Without this, the app cannot function.",
            isGranted = uiState.smsPermissionGranted,
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = Icons.Filled.Phone,
            title = "Phone Permission",
            reason = "To detect your SIM cards and carrier information. This lets us show which SIM is active and manage daily sending limits per SIM.",
            isGranted = uiState.phonePermissionGranted,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "Notification Permission",
                reason = "To show you real-time updates about message delivery and alert you if something needs attention.",
                isGranted = uiState.notificationPermissionGranted,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.allPermissionsGranted) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("All Set — Continue")
            }
        } else {
            Button(
                onClick = { permissionLauncher.launch(permissions) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Grant Permissions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onContinue,
            ) {
                Text(
                    text = "Skip for now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    reason: String,
    isGranted: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                WaSmsTheme.statusColors.deliveredContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) {
                    WaSmsTheme.statusColors.delivered
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isGranted) "Granted" else reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted) {
                        WaSmsTheme.statusColors.delivered
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// =============================================================================
// Step 4: Success Celebration
// =============================================================================

@Composable
private fun SuccessStep(
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Simple celebration icon (Agent 3: "simple animation")
        Icon(
            imageVector = Icons.Filled.Rocket,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your device is connected and ready to send messages. You can start sending campaigns from the web dashboard.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Get Started")
        }
    }
}
