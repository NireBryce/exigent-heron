package net.breadthcharge.exigentheron.ui.settings

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.breadthcharge.exigentheron.AppContainer
import net.breadthcharge.exigentheron.data.Settings
import net.breadthcharge.exigentheron.speech.TtsEngineStatus

/**
 * AGENTS.md §4.8/§4.9: the headset-only, lock-state, and DND-override
 * toggles, plus the engine picker with a visible active engine and
 * init/language error — all in one screen since there are only the four
 * of them (AGENTS.md §0's YAGNI: no tabs or sections for a screen this
 * small).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val settings by container.settingsRepository.settings.collectAsState(initial = Settings())
    val ttsStatus by container.ttsEngineStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var showEnginePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Output routing", style = MaterialTheme.typography.titleMedium)
            SettingToggle(
                label = "Headset only",
                checked = settings.headsetOnly,
                onCheckedChange = { scope.launch { container.settingsRepository.setHeadsetOnly(it) } },
            )
            SettingToggle(
                label = "Don't speak while locked",
                checked = settings.respectLockState,
                onCheckedChange = { scope.launch { container.settingsRepository.setRespectLockState(it) } },
            )
            SettingToggle(
                label = "Speak even during Do Not Disturb",
                checked = settings.allowDndOverride,
                onCheckedChange = { scope.launch { container.settingsRepository.setAllowDndOverride(it) } },
            )

            Text("TTS engine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                text = "Active: ${settings.ttsEnginePackage ?: "system default"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when (val status = ttsStatus) {
                    TtsEngineStatus.Initializing -> "Status: initializing…"
                    TtsEngineStatus.Ready -> "Status: ready"
                    is TtsEngineStatus.Failed -> "Status: error — ${status.reason}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ttsStatus is TtsEngineStatus.Failed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            TextButton(onClick = { showEnginePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Choose engine")
            }

            TextButton(onClick = onDone, modifier = Modifier.padding(top = 24.dp)) { Text("Done") }
        }
    }

    if (showEnginePicker) {
        EnginePickerDialog(
            engines = container.ttsEngine.listEngines(),
            currentPackage = settings.ttsEnginePackage,
            onChoose = { packageName ->
                scope.launch { container.settingsRepository.setTtsEnginePackage(packageName) }
                container.rebuildTtsEngine(packageName)
                showEnginePicker = false
            },
            onDismiss = { showEnginePicker = false },
        )
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EnginePickerDialog(
    engines: List<TextToSpeech.EngineInfo>,
    currentPackage: String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose TTS engine") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(engines, key = { it.name }) { engine ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = engine.name == currentPackage,
                            onClick = { onChoose(engine.name) },
                        )
                        Column {
                            Text(engine.label)
                            Text(engine.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
