package net.breadthcharge.exigentheron.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat

/**
 * "No UI beyond an enable-access button" (BUILD_PLAN.md Phase 2). The
 * rule list, settings, and permission-flow polish arrive in Phases 3–4.
 */
class MainActivity : ComponentActivity() {

    // A plain field, not `remember { }` — this needs to survive and be
    // updated from onResume(), not just recomposition. Reading `.value`
    // inside MainScreen() still subscribes it to recomposition normally.
    private val accessGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Scaffold { innerPadding ->
                        MainScreen(granted = accessGranted.value, modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-checked here specifically so returning from the system
        // settings screen (below) reflects a just-granted permission
        // without needing a manual refresh — AGENTS.md §4.10.
        accessGranted.value = isNotificationAccessGranted(this)
    }
}

@Composable
private fun MainScreen(granted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "exigent-heron")
        Text(text = if (granted) "Notification access: granted" else "Notification access: not granted")
        if (!granted) {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Enable notification access")
            }
        }
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
