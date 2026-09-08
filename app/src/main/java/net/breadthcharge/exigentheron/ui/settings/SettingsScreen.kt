package net.breadthcharge.exigentheron.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.breadthcharge.exigentheron.AppContainer
import net.breadthcharge.exigentheron.data.BondedBluetoothDevice
import net.breadthcharge.exigentheron.data.Settings
import net.breadthcharge.exigentheron.data.loadBondedBluetoothDevices
import net.breadthcharge.exigentheron.domain.SecretDetector
import net.breadthcharge.exigentheron.speech.BluetoothDeviceDecision
import net.breadthcharge.exigentheron.speech.TtsEngineStatus

/**
 * Headset-only gate, lock-state gate, DND-override toggle, TTS engine picker (with error display),
 * per-device Bluetooth allow/deny list, and OTP keyword editor — all in one screen.
 * Three sections don't justify tabs; simpler is better.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by container.settingsRepository.settings.collectAsState(initial = Settings())
    val ttsStatus by container.ttsEngineStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var showEnginePicker by remember { mutableStateOf(false) }

    var bluetoothPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestBluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        bluetoothPermissionGranted = granted
        // Only ever turns the feature *on* here, and only on an actual
        // grant — a denial leaves bluetoothDeviceControlEnabled exactly
        // where it already was (off, since this launcher is only ever
        // triggered from turning the toggle on in the first place).
        if (granted) scope.launch { container.settingsRepository.setBluetoothDeviceControlEnabled(true) }
    }
    // The permission can be revoked from system settings without this
    // screen finding out except by asking again — if that leaves
    // "enabled" true with no permission behind it, correct it here
    // rather than leaving a setting that reads as on but does nothing
    // (OutputRouteGate itself already degrades safely either way).
    LaunchedEffect(bluetoothPermissionGranted, settings.bluetoothDeviceControlEnabled) {
        if (settings.bluetoothDeviceControlEnabled && !bluetoothPermissionGranted) {
            container.settingsRepository.setBluetoothDeviceControlEnabled(false)
        }
    }

    var bondedDevices by remember { mutableStateOf<List<BondedBluetoothDevice>>(emptyList()) }
    LaunchedEffect(settings.bluetoothDeviceControlEnabled, bluetoothPermissionGranted) {
        bondedDevices = if (settings.bluetoothDeviceControlEnabled && bluetoothPermissionGranted) {
            withContext(Dispatchers.IO) { loadBondedBluetoothDevices(context) }
        } else {
            emptyList()
        }
    }

    var otpKeywords by remember(settings.otpKeywords) {
        mutableStateOf(settings.otpKeywords?.toList() ?: SecretDetector.DEFAULT_OTP_KEYWORDS)
    }
    var newKeywordInput by remember { mutableStateOf("") }

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

            SettingToggle(
                label = "Per-device Bluetooth control",
                checked = settings.bluetoothDeviceControlEnabled,
                onCheckedChange = { enable ->
                    if (!enable) {
                        scope.launch { container.settingsRepository.setBluetoothDeviceControlEnabled(false) }
                    } else if (bluetoothPermissionGranted) {
                        scope.launch { container.settingsRepository.setBluetoothDeviceControlEnabled(true) }
                    } else {
                        requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Allow or deny speech for specific paired Bluetooth devices, separately " +
                    "from \"Headset only\" above — Android can't otherwise tell a real " +
                    "headset apart from a car stereo or TV soundbar; they report the same " +
                    "type. Needs access to your paired device list (names and addresses " +
                    "only — nothing leaves this device either way).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (settings.bluetoothDeviceControlEnabled && bluetoothPermissionGranted) {
                if (bondedDevices.isEmpty()) {
                    Text(
                        "No paired devices.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    for (device in bondedDevices) {
                        BluetoothDeviceRow(
                            device = device,
                            decision = settings.bluetoothDeviceDecision(device.address),
                            onDecision = { decision ->
                                scope.launch {
                                    container.settingsRepository.setBluetoothDeviceDecision(device.address, decision)
                                }
                            },
                        )
                    }
                }
            }

            Text("Speech length", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "Stop speaking a notification after this many seconds, even mid-sentence. " +
                    "Leave blank for no limit.",
                style = MaterialTheme.typography.bodySmall,
            )
            var truncationInput by remember(settings.truncationLengthSeconds) {
                mutableStateOf(settings.truncationLengthSeconds?.toString() ?: "")
            }
            OutlinedTextField(
                value = truncationInput,
                onValueChange = { input ->
                    truncationInput = input
                    val seconds = input.toIntOrNull()
                    if (input.isEmpty() || seconds != null) {
                        scope.launch { container.settingsRepository.setTruncationLengthSeconds(seconds) }
                    }
                },
                label = { Text("Max seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 8.dp),
            )

            Text("OTP detection keywords", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            Text(
                "When a notification body contains a digit run (4-8 digits) near any of these keywords, " +
                    "the message is silenced rather than announced. Leave empty to disable keyword-based detection " +
                    "(the hardcoded check for bare 6-digit bodies still applies). Defaults shown below; edit to customize.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (otpKeywords.isEmpty()) {
                Text(
                    "Keyword detection is off (list is empty).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(modifier = Modifier.padding(top = 8.dp)) {
                for (keyword in otpKeywords) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Text(keyword, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            otpKeywords = otpKeywords - keyword
                            scope.launch { container.settingsRepository.setOtpKeywords(otpKeywords.toSet()) }
                        }) {
                            Text("Remove")
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = newKeywordInput,
                    onValueChange = { newKeywordInput = it },
                    label = { Text("New keyword") },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (newKeywordInput.isNotBlank() && newKeywordInput !in otpKeywords) {
                            otpKeywords = otpKeywords + newKeywordInput
                            scope.launch { container.settingsRepository.setOtpKeywords(otpKeywords.toSet()) }
                            newKeywordInput = ""
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Add")
                }
            }
            TextButton(
                onClick = {
                    otpKeywords = SecretDetector.DEFAULT_OTP_KEYWORDS
                    scope.launch { container.settingsRepository.setOtpKeywords(null) }
                    newKeywordInput = ""
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Reset to defaults")
            }

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
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Allow/Deny as two independent-looking buttons that are never actually
 * independent: tapping the one already selected resets to
 * [BluetoothDeviceDecision.UNSET], tapping the other switches straight
 * to it — [SettingsRepository.setBluetoothDeviceDecision][net.breadthcharge.exigentheron.data.SettingsRepository.setBluetoothDeviceDecision]
 * is the only way either gets written, and it always clears the other
 * set first. There is deliberately no third "are you sure" state for
 * "both on at once" — that combination is unreachable by construction,
 * not merely disallowed at save time.
 */
@Composable
private fun BluetoothDeviceRow(
    device: BondedBluetoothDevice,
    decision: BluetoothDeviceDecision,
    onDecision: (BluetoothDeviceDecision) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name)
            Text(device.address, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(
            onClick = {
                onDecision(if (decision == BluetoothDeviceDecision.ALLOWED) BluetoothDeviceDecision.UNSET else BluetoothDeviceDecision.ALLOWED)
            },
        ) {
            Text(
                "Allow",
                color = if (decision == BluetoothDeviceDecision.ALLOWED) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        }
        TextButton(
            onClick = {
                onDecision(if (decision == BluetoothDeviceDecision.DENIED) BluetoothDeviceDecision.UNSET else BluetoothDeviceDecision.DENIED)
            },
        ) {
            Text(
                "Deny",
                color = if (decision == BluetoothDeviceDecision.DENIED) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
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
