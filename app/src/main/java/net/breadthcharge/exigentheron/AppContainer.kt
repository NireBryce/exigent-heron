package net.breadthcharge.exigentheron

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.data.Settings
import net.breadthcharge.exigentheron.data.SettingsRepository
import net.breadthcharge.exigentheron.domain.Deduplicator
import net.breadthcharge.exigentheron.domain.RuleEngineHolder
import net.breadthcharge.exigentheron.domain.SecretDetector
import net.breadthcharge.exigentheron.domain.SecretDetectorHolder
import net.breadthcharge.exigentheron.speech.AndroidTtsEngine
import net.breadthcharge.exigentheron.speech.AudioBecomingNoisyReceiver
import net.breadthcharge.exigentheron.speech.AudioFocusManager
import net.breadthcharge.exigentheron.speech.LockStateGate
import net.breadthcharge.exigentheron.speech.OutputRouteGate
import net.breadthcharge.exigentheron.speech.SpeechQueue
import net.breadthcharge.exigentheron.speech.TtsEngineStatus

/**
 * Manual DI container: constructs and holds this app's singletons.
 *
 * Manual constructor injection for three singletons — no Hilt (200+ lines of ceremony for ~3 objects).
 */
class AppContainer(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val deduplicator = Deduplicator(clock = System::currentTimeMillis)

    val ruleRepository = RuleRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)

    // A live snapshot of settings, read synchronously below and kept
    // current for the gates/SpeechQueue lambdas — see currentSettings.
    // Blocking on the first value at container-construction time (App's
    // onCreate, main thread): DataStore's first emission
    // is a local Preferences-file read with no network involved, and
    // AndroidTtsEngine/SpeechQueue both need a real value to construct
    // with, not a value that arrives later.
    private val settingsState = MutableStateFlow(runBlocking { settingsRepository.settings.first() })
    private val currentSettings: Settings get() = settingsState.value

    init {
        scope.launch { settingsRepository.settings.collect { settingsState.value = it } }
    }

    // Rebuilt from ruleRepository.rules on every change — a rule edit
    // takes effect on the next notification, not on app restart. Rules
    // are loaded from the repository, empty by default, not hard-coded.
    val ruleEngine = RuleEngineHolder(
        rules = ruleRepository.rules,
        scope = scope,
        onRuleFailure = { id, reason -> SafeLog.error("rule $id failed: $reason") },
    )

    val secretDetector = SecretDetectorHolder(
        otpKeywords = settingsRepository.settings.map { settings ->
            settings.otpKeywords?.toList() ?: SecretDetector.DEFAULT_OTP_KEYWORDS
        },
        scope = scope,
    )

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioFocusManager = AudioFocusManager(appContext)

    val outputRouteGate = OutputRouteGate(
        headsetOnlyEnabled = { currentSettings.headsetOnly },
        connectedOutputTypes = {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        },
        bluetoothDeviceControlEnabled = { currentSettings.bluetoothDeviceControlEnabled },
        // AudioDeviceInfo.getAddress() only returns a real MAC for a
        // Bluetooth device — and only at all — with BLUETOOTH_CONNECT
        // granted; re-checked here rather than assumed from the settings
        // toggle above, since a user can revoke the permission from
        // system settings without this app finding out except by asking
        // again. Falls back to an empty set (⇒ plain type-based check,
        // same as the feature being off) rather than crashing.
        connectedBluetoothAddresses = {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .filter { it.type in OutputRouteGate.BLUETOOTH_HEADSET_TYPES }
                    .mapNotNull { it.address }
                    .toSet()
            } else {
                emptySet()
            }
        },
        // A lambda, deliberately, not `currentSettings::bluetoothDeviceDecision`
        // — that would bind to whatever Settings instance existed at this
        // line's *construction* time, not re-read `currentSettings` (a
        // `get()`) on every call the way every other lambda here does.
        bluetoothDeviceDecision = { address -> currentSettings.bluetoothDeviceDecision(address) },
    )

    val lockStateGate = LockStateGate(
        respectLockState = { currentSettings.respectLockState },
        isKeyguardLocked = keyguardManager::isKeyguardLocked,
    )

    // Persists across an engine rebuild (see rebuildTtsEngine) so the
    // settings screen's error/ready display doesn't reset just because
    // the user picked a different engine.
    private val ttsStatus = MutableStateFlow<TtsEngineStatus>(TtsEngineStatus.Initializing)
    val ttsEngineStatus: StateFlow<TtsEngineStatus> = ttsStatus

    var ttsEngine: AndroidTtsEngine = createTtsEngine(currentSettings.ttsEnginePackage)
        private set

    var speechQueue: SpeechQueue = createSpeechQueue(ttsEngine)
        private set

    // See AudioBecomingNoisyReceiver's own doc comment: this is the
    // mid-utterance half of the output-route gate, distinct from
    // isOutputRouteAllowed above (which only ever sees the route
    // between utterances, never a change during one already playing).
    // `speechQueue` is read here as a property, not captured as a local
    // val, so this keeps calling the *current* queue's stopCurrent()
    // across a rebuildTtsEngine() swap, not a stale reference to the one
    // that existed when this receiver was constructed.
    private val audioBecomingNoisyReceiver =
        AudioBecomingNoisyReceiver(appContext) { speechQueue.stopCurrent() }

    private fun createTtsEngine(enginePackage: String?): AndroidTtsEngine =
        AndroidTtsEngine(appContext, enginePackage) { status -> ttsStatus.value = status }

    private fun createSpeechQueue(engine: AndroidTtsEngine): SpeechQueue = SpeechQueue(
        ttsEngine = engine,
        requestAudioFocus = audioFocusManager::requestFocus,
        abandonAudioFocus = audioFocusManager::abandonFocus,
        isInCall = {
            audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        },
        isBlockedByDnd = {
            !currentSettings.allowDndOverride &&
                notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        },
        isOutputRouteAllowed = outputRouteGate::allows,
        truncationLengthSeconds = { currentSettings.truncationLengthSeconds },
        scope = scope,
    )

    /**
     * Engine changes take effect immediately on the next notification.
     * `TextToSpeech` binding to an engine package is permanent for its lifetime, so a new
     * choice means constructing fresh engine + queue objects rather than patching the old ones.
     * The old engine shuts down only after the new one is live, so utterances in flight
     * stay attached to a queue.
     */
    fun rebuildTtsEngine(enginePackage: String?) {
        val old = ttsEngine
        ttsEngine = createTtsEngine(enginePackage)
        speechQueue = createSpeechQueue(ttsEngine)
        old.shutdown()
    }
}
