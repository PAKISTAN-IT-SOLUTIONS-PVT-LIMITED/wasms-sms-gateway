package net.wasms.smsgateway.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import net.wasms.smsgateway.WaSmsApp
import net.wasms.smsgateway.presentation.common.theme.WaSmsTheme
import net.wasms.smsgateway.presentation.navigation.WaSmsNavHost
import java.io.File

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Check for crash log from previous session
        val crashFile = File(filesDir, WaSmsApp.CRASH_LOG_FILE)
        if (crashFile.exists()) {
            val crashLog = crashFile.readText()
            if (crashLog.isNotBlank()) {
                val intent = CrashReportActivity.createIntent(this, crashLog)
                startActivity(intent)
                finish()
                return
            }
        }

        enableEdgeToEdge()

        setContent {
            WaSmsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WaSmsNavHost()
                }
            }
        }
    }
}
