package net.breadthcharge.exigentheron

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.data.Settings
import net.breadthcharge.exigentheron.data.SettingsRepository
import net.breadthcharge.exigentheron.domain.Deduplicator
import net.breadthcharge.exigentheron.domain.RuleEngineHolder
import net.breadthcharge.exigentheron.domain.SecretDetector
import net.breadthcharge.exigentheron.speech.AndroidTtsEngine
import net.breadthcharge.exigentheron.speech.AudioFocusManager
import net.breadthcharge.exigentheron.speech.LockStateGate
import net.breadthcharge.exigentheron.speech.OutputRouteGate
import net.breadthcharge.exigentheron.speech.SpeechQueue
import net.breadthcharge.exigentheron.speech.TtsEngineStatus

/**
 * Manual DI container: constructs and holds this app's singletons.
 *
 * No Hilt, no framework — see AGENTS.md §2.
 */
class AppContainer(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val deduplicator = Deduplicator(clock = System::currentTimeMillis)

    val ruleRepository = RuleRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)

    // A live snapshot of settings, read synchronously below and kept
    // current for the gates/SpeechQueue lambdas — see currentSettings.
    // Blocking on the first value at container-construction time (App's
    // onCreate, main thread) is the "simplest, correct" option the
    // BUILD_PLAN.md Phase 4 note calls for: DataStore's first emission
    // is a local Preferences-file read with no network involved, and
    // AndroidTtsEngine/SpeechQueue both need a real value to construct
    // with, not a value that arrives later.
    private val settingsState = MutableStateFlow(runBlocking { settingsRepository.settings.first() })
    private val currentSettings: Settings get() = settingsState.value

    init {
        scope.launch { settingsRepository.settings.collect { settingsState.value = it } }
    }

    // Rebuilt from ruleRepository.rules on every change (Phase 3) — a
    // rule edit takes effect on the next notification, not on next app
    // restart. Phase 2's phase2HardcodedRules is gone: this repo's
    // rules, empty by default, are the real rule set now.
    val ruleEngine = RuleEngineHolder(
        rules = ruleRepository.rules,
        scope = scope,
        onRuleFailure = { id, reason -> SafeLog.error("rule $id failed: $reason") },
    )

    val secretDetector = SecretDetector()

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
        scope = scope,
    )

    /**
     * AGENTS.md §4.8: "switching takes effect" — `AndroidTtsEngine`
     * binds its `TextToSpeech` to one engine package for its whole
     * lifetime, so taking a new choice means constructing a fresh
     * engine (and, since [SpeechQueue] holds its `TtsEngine` by
     * constructor reference too, a fresh queue on top of it) rather than
     * mutating the old one in place. The old engine is shut down only
     * after the new one is live, so nothing in flight is left stranded
     * mid-utterance without a queue to belong to.
     */
    fun rebuildTtsEngine(enginePackage: String?) {
        val old = ttsEngine
        ttsEngine = createTtsEngine(enginePackage)
        speechQueue = createSpeechQueue(ttsEngine)
        old.shutdown()
    }
}
