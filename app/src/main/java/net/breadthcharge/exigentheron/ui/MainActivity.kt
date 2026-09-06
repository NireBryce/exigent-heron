package net.breadthcharge.exigentheron.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import net.breadthcharge.exigentheron.App
import net.breadthcharge.exigentheron.AppContainer
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.data.Settings
import net.breadthcharge.exigentheron.domain.Rule
import net.breadthcharge.exigentheron.ui.rules.RuleEditorScreen
import net.breadthcharge.exigentheron.ui.rules.RuleListScreen
import net.breadthcharge.exigentheron.ui.settings.SettingsScreen

/**
 * No navigation-compose dependency (AGENTS.md §2's list doesn't have
 * one, and three screens don't need one) — [Screen] plus a manual
 * `when` in [MainActivity] is the whole nav stack.
 */
private sealed interface Screen {
    data object Main : Screen
    data object RuleList : Screen
    data class RuleEditor(val ruleId: String?) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {

    // A plain field, not `remember { }` — this needs to survive and be
    // updated from onResume(), not just recomposition. Reading `.value`
    // inside MainScreen() still subscribes it to recomposition normally.
    private val accessGranted = mutableStateOf(false)

    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by remember { mutableStateOf<Screen>(Screen.Main) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val current = screen) {
                        is Screen.Main -> Scaffold { innerPadding ->
                            MainScreen(
                                granted = accessGranted.value,
                                container = container,
                                onManageRules = { screen = Screen.RuleList },
                                onSettings = { screen = Screen.Settings },
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                        is Screen.RuleList -> {
                            BackHandler { screen = Screen.Main }
                            RuleListScreen(
                                ruleRepository = container.ruleRepository,
                                onAddRule = { screen = Screen.RuleEditor(ruleId = null) },
                                onEditRule = { id -> screen = Screen.RuleEditor(ruleId = id) },
                            )
                        }
                        is Screen.RuleEditor -> {
                            BackHandler { screen = Screen.RuleList }
                            var existingRule by remember(current.ruleId) { mutableStateOf<Rule?>(null) }
                            var loaded by remember(current.ruleId) { mutableStateOf(current.ruleId == null) }
                            if (current.ruleId != null && !loaded) {
                                LoadRule(
                                    ruleRepository = container.ruleRepository,
                                    ruleId = current.ruleId,
                                    onLoaded = { rule -> existingRule = rule; loaded = true },
                                )
                            }
                            if (loaded) {
                                RuleEditorScreen(
                                    ruleRepository = container.ruleRepository,
                                    existingRule = existingRule,
                                    onDone = { screen = Screen.RuleList },
                                )
                            }
                        }
                        is Screen.Settings -> {
                            BackHandler { screen = Screen.Main }
                            SettingsScreen(container = container, onDone = { screen = Screen.Main })
                        }
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
private fun LoadRule(ruleRepository: RuleRepository, ruleId: String, onLoaded: (Rule?) -> Unit) {
    LaunchedEffect(ruleId) {
        val rule = ruleRepository.rules.first().firstOrNull { it.id == ruleId }
        onLoaded(rule)
    }
}

@Composable
private fun MainScreen(
    granted: Boolean,
    container: AppContainer,
    onManageRules: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsState(initial = Settings())
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "exigent-heron")
        Text(text = if (granted) "Notification access: granted" else "Notification access: not granted")
        // AGENTS.md §4.8: "Show the active engine on the main screen.
        // The user should never have to wonder."
        Text(text = "TTS engine: ${settings.ttsEnginePackage ?: "system default"}")
        if (!granted) {
            Button(onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("Enable notification access")
            }
        }
        Button(onClick = onManageRules) { Text("Manage rules") }
        Button(onClick = onSettings) { Text("Settings") }
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
