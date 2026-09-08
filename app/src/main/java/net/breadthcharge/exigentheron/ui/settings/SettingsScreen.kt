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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
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
 *
 * Each section is its own private composable below, taking plain values
 * and callbacks rather than the [AppContainer]. What stays in this
 * function is only what genuinely spans sections: the Bluetooth
 * permission state, which both gates the per-device list *and* has to be
 * reconciled against the persisted toggle, and the `scope.launch`
 * wiring that turns each callback into a `SettingsRepository` write. A
 * section needing neither then reads as what it is — some form fields
 * and where their values go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingsRepository = container.settingsRepository
    val settings by settingsRepository.settings.collectAsState(initial = Settings())
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
        if (granted) scope.launch { settingsRepository.setBluetoothDeviceControlEnabled(true) }
    }
    // The permission can be revoked from system settings without this
    // screen finding out except by asking again — if that leaves
    // "enabled" true with no permission behind it, correct it here
    // rather than leaving a setting that reads as on but does nothing
    // (OutputRouteGate itself already degrades safely either way).
    LaunchedEffect(bluetoothPermissionGranted, settings.bluetoothDeviceControlEnabled) {
        if (settings.bluetoothDeviceControlEnabled && !bluetoothPermissionGranted) {
            settingsRepository.setBluetoothDeviceControlEnabled(false)
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
            OutputRoutingSection(
                headsetOnly = settings.headsetOnly,
                respectLockState = settings.respectLockState,
                allowDndOverride = settings.allowDndOverride,
                onHeadsetOnly = { scope.launch { settingsRepository.setHeadsetOnly(it) } },
                onRespectLockState = { scope.launch { settingsRepository.setRespectLockState(it) } },
                onAllowDndOverride = { scope.launch { settingsRepository.setAllowDndOverride(it) } },
            )

            BluetoothDeviceSection(
                enabled = settings.bluetoothDeviceControlEnabled,
                permissionGranted = bluetoothPermissionGranted,
                bondedDevices = bondedDevices,
                decisionFor = settings::bluetoothDeviceDecision,
                onEnabledChange = { enable ->
                    if (!enable) {
                        scope.launch { settingsRepository.setBluetoothDeviceControlEnabled(false) }
                    } else if (bluetoothPermissionGranted) {
                        scope.launch { settingsRepository.setBluetoothDeviceControlEnabled(true) }
                    } else {
                        requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                onDecision = { address, decision ->
                    scope.launch { settingsRepository.setBluetoothDeviceDecision(address, decision) }
                },
            )

            SpeechLengthSection(
                truncationLengthSeconds = settings.truncationLengthSeconds,
                onPersist = { scope.launch { settingsRepository.setTruncationLengthSeconds(it) } },
            )

            OtpKeywordSection(
                persistedKeywords = settings.otpKeywords,
                onPersist = { scope.launch { settingsRepository.setOtpKeywords(it) } },
            )

            TtsEngineSection(
                enginePackage = settings.ttsEnginePackage,
                status = ttsStatus,
                onChooseEngine = { showEnginePicker = true },
            )

            TextButton(onClick = onDone, modifier = Modifier.padding(top = 24.dp)) { Text("Done") }
        }
    }

    if (showEnginePicker) {
        EnginePickerDialog(
            engines = container.ttsEngine.listEngines(),
            currentPackage = settings.ttsEnginePackage,
            onChoose = { packageName ->
                scope.launch { settingsRepository.setTtsEnginePackage(packageName) }
                container.rebuildTtsEngine(packageName)
                showEnginePicker = false
            },
            onDismiss = { showEnginePicker = false },
        )
    }
}

@Composable
private fun OutputRoutingSection(
    headsetOnly: Boolean,
    respectLockState: Boolean,
    allowDndOverride: Boolean,
    onHeadsetOnly: (Boolean) -> Unit,
    onRespectLockState: (Boolean) -> Unit,
    onAllowDndOverride: (Boolean) -> Unit,
) {
    Text("Output routing", style = MaterialTheme.typography.titleMedium)
    SettingToggle(label = "Headset only", checked = headsetOnly, onCheckedChange = onHeadsetOnly)
    SettingToggle(
        label = "Don't speak while locked",
        checked = respectLockState,
        onCheckedChange = onRespectLockState,
    )
    SettingToggle(
        label = "Speak even during Do Not Disturb",
        checked = allowDndOverride,
        onCheckedChange = onAllowDndOverride,
    )
}

/**
 * The per-device allow/deny list and the toggle gating it. The
 * explanation stays visible whether or not the feature is on — it's what
 * tells the user why they'd want it, and it names the permission before
 * the system dialog appears rather than after.
 */
@Composable
private fun BluetoothDeviceSection(
    enabled: Boolean,
    permissionGranted: Boolean,
    bondedDevices: List<BondedBluetoothDevice>,
    decisionFor: (String) -> BluetoothDeviceDecision,
    onEnabledChange: (Boolean) -> Unit,
    onDecision: (String, BluetoothDeviceDecision) -> Unit,
) {
    SettingToggle(
        label = "Per-device Bluetooth control",
        checked = enabled,
        onCheckedChange = onEnabledChange,
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
    if (!enabled || !permissionGranted) return

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
                decision = decisionFor(device.address),
                onDecision = { decision -> onDecision(device.address, decision) },
            )
        }
    }
}

/**
 * [truncationLengthSeconds] is the persisted value; the field keeps its
 * own copy so a half-typed entry isn't fought over by recomposition. A
 * blank field persists null ("no limit"); anything that isn't a number
 * is displayed but not persisted, leaving the last good value in place
 * rather than clearing it.
 */
@Composable
private fun SpeechLengthSection(truncationLengthSeconds: Int?, onPersist: (Int?) -> Unit) {
    var input by remember(truncationLengthSeconds) {
        mutableStateOf(truncationLengthSeconds?.toString() ?: "")
    }

    Text(
        "Speech length",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        "Stop speaking a notification after this many seconds, even mid-sentence. " +
            "Leave blank for no limit.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = input,
        onValueChange = { typed ->
            input = typed
            val seconds = typed.toIntOrNull()
            if (typed.isEmpty() || seconds != null) onPersist(seconds)
        },
        label = { Text("Max seconds") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * [persistedKeywords] is null when the user has never edited the list,
 * meaning [SecretDetector.DEFAULT_OTP_KEYWORDS] are in force — so the
 * editable copy here starts from those defaults rather than from an
 * empty list, per `AGENTS.md` §4.5 ("users get a real copy of the
 * defaults to modify, not an invisible built-in list"). "Reset to
 * defaults" persists null to get back to exactly that state.
 *
 * An empty list is a legitimate choice, not an error: keyword-proximity
 * detection is then off and the hardcoded bare-6-digit floor is all that
 * remains, which §4.5 requires this screen to show rather than leave
 * silent.
 */
@Composable
private fun OtpKeywordSection(persistedKeywords: Set<String>?, onPersist: (Set<String>?) -> Unit) {
    var keywords by remember(persistedKeywords) {
        mutableStateOf(persistedKeywords?.toList() ?: SecretDetector.DEFAULT_OTP_KEYWORDS)
    }
    var newKeywordInput by remember { mutableStateOf("") }

    Text(
        "OTP detection keywords",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        "When a notification body contains a digit run (4-8 digits) near any of these keywords, " +
            "the message is silenced rather than announced. Leave empty to disable keyword-based " +
            "detection (the hardcoded check for bare 6-digit bodies still applies). Defaults " +
            "shown below; edit to customize.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (keywords.isEmpty()) {
        Text(
            "Keyword detection is off (list is empty).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        for (keyword in keywords) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(keyword, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        keywords = keywords - keyword
                        onPersist(keywords.toSet())
                    },
                ) {
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
                if (newKeywordInput.isNotBlank() && newKeywordInput !in keywords) {
                    keywords = keywords + newKeywordInput
                    onPersist(keywords.toSet())
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
            keywords = SecretDetector.DEFAULT_OTP_KEYWORDS
            onPersist(null)
            newKeywordInput = ""
        },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text("Reset to defaults")
    }
}

@Composable
private fun TtsEngineSection(enginePackage: String?, status: TtsEngineStatus, onChooseEngine: () -> Unit) {
    Text(
        "TTS engine",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp),
    )
    Text(
        text = "Active: ${enginePackage ?: "system default"}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = when (status) {
            TtsEngineStatus.Initializing -> "Status: initializing…"
            TtsEngineStatus.Ready -> "Status: ready"
            is TtsEngineStatus.Failed -> "Status: error — ${status.reason}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (status is TtsEngineStatus.Failed) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
    TextButton(onClick = onChooseEngine, modifier = Modifier.padding(top = 8.dp)) {
        Text("Choose engine")
    }
}

@Composable
private fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Allow/Deny as two independent-looking buttons that are never actually
 * independent: tapping the one already selected resets to
 * [BluetoothDeviceDecision.UNSET], tapping the other switches straight to
 * it — `SettingsRepository`'s
 * [setBluetoothDeviceDecision][net.breadthcharge.exigentheron.data.SettingsRepository.setBluetoothDeviceDecision]
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
        DecisionButton(
            label = "Allow",
            target = BluetoothDeviceDecision.ALLOWED,
            current = decision,
            selectedColor = MaterialTheme.colorScheme.primary,
            onDecision = onDecision,
        )
        DecisionButton(
            label = "Deny",
            target = BluetoothDeviceDecision.DENIED,
            current = decision,
            selectedColor = MaterialTheme.colorScheme.error,
            onDecision = onDecision,
        )
    }
}

/** Selects [target], or clears back to [BluetoothDeviceDecision.UNSET] when it's already [current]. */
@Composable
private fun DecisionButton(
    label: String,
    target: BluetoothDeviceDecision,
    current: BluetoothDeviceDecision,
    selectedColor: Color,
    onDecision: (BluetoothDeviceDecision) -> Unit,
) {
    val selected = current == target
    TextButton(onClick = { onDecision(if (selected) BluetoothDeviceDecision.UNSET else target) }) {
        Text(label, color = if (selected) selectedColor else Color.Unspecified)
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
