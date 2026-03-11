package net.wasms.smsgateway.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wasms.smsgateway.WaSmsApp
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme
import java.io.File

/**
 * Launcher activity that checks for crash logs BEFORE starting the main app.
 *
 * This is a plain ComponentActivity (NOT @AndroidEntryPoint) so it works
 * even when Hilt initialization has failed. This is critical because if
 * WaSmsApp.onCreate()/super.onCreate() crashes, @AndroidEntryPoint activities
 * would also crash, making crash logs invisible to the user.
 *
 * Flow:
 * 1. Android starts WaSmsApp.onCreate() → may crash (caught) or succeed
 * 2. This activity checks for crash_log.txt or WaSmsApp.hiltFailed
 * 3. If crash detected → show crash report (Share/Copy/Dismiss)
 * 4. If no crash → forward to MainActivity (which uses @AndroidEntryPoint)
 */
class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Check if Hilt failed during Application initialization
        val app = application as? WaSmsApp
        val hiltFailed = app?.hiltFailed == true

        // Check for crash log from previous session or current failed init
        val crashLog = intent.getStringExtra(EXTRA_CRASH_LOG) ?: readCrashLog()

        if (crashLog == null && !hiltFailed) {
            // No crash — proceed to main app
            startMainActivity()
            return
        }

        val displayLog = crashLog ?: "Hilt DI initialization failed. Check logcat for details.\n\nFilter: WaSMS_BOOT"

        setContent {
            WaSmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashReportScreen(
                        crashLog = displayLog,
                        onShare = { shareCrashLog(displayLog) },
                        onCopy = { copyCrashLog(displayLog) },
                        onDismiss = {
                            deleteCrashLog()
                            if (hiltFailed) {
                                // Can't start MainActivity without Hilt — just finish
                                Toast.makeText(this, "Please restart the app", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                startMainActivity()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun readCrashLog(): String? {
        return try {
            val crashFile = File(filesDir, WaSmsApp.CRASH_LOG_FILE)
            if (crashFile.exists()) {
                val text = crashFile.readText()
                if (text.isNotBlank()) text else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun shareCrashLog(log: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "WaSMS Gateway Crash Report")
            putExtra(Intent.EXTRA_TEXT, log)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Crash Report"))
    }

    private fun copyCrashLog(log: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("WaSMS Crash Report", log)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Crash report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun deleteCrashLog() {
        try {
            val crashFile = File(filesDir, WaSmsApp.CRASH_LOG_FILE)
            if (crashFile.exists()) crashFile.delete()
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"

        fun createIntent(context: Context, crashLog: String): Intent {
            return Intent(context, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }
}

@Composable
private fun CrashReportScreen(
    crashLog: String,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color(0xFFEF4444),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "App Crash Detected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                )
                Text(
                    text = "Please share this report so we can fix the issue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Crash log display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E),
            ),
        ) {
            Text(
                text = crashLog,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
                color = Color(0xFFE0E0E0),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }

            OutlinedButton(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dismiss")
            }
        }
    }
}
