package net.breadthcharge.exigentheron

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.breadthcharge.exigentheron.domain.Deduplicator
import net.breadthcharge.exigentheron.domain.Rule
import net.breadthcharge.exigentheron.domain.RuleAction
import net.breadthcharge.exigentheron.domain.RuleEngine
import net.breadthcharge.exigentheron.domain.SecretDetector
import net.breadthcharge.exigentheron.speech.AndroidTtsEngine
import net.breadthcharge.exigentheron.speech.AudioFocusManager
import net.breadthcharge.exigentheron.speech.SpeechQueue

/**
 * Manual DI container: constructs and holds this app's singletons.
 *
 * No Hilt, no framework — see AGENTS.md §2. `SettingsRepository` and
 * `RuleRepository` (Phase 3, DataStore-backed) still don't exist —
 * [ruleEngine] below is built from [phase2HardcodedRules] until they do.
 */
class AppContainer(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val deduplicator = Deduplicator(clock = System::currentTimeMillis)

    val ruleEngine = RuleEngine(
        rules = phase2HardcodedRules,
        onRuleFailure = { id, reason -> SafeLog.error("rule $id failed: $reason") },
    )

    val secretDetector = SecretDetector()

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusManager = AudioFocusManager(appContext)
    private val ttsEngine = AndroidTtsEngine(appContext) { status ->
        SafeLog.lifecycle("tts engine status: $status")
    }

    val speechQueue = SpeechQueue(
        ttsEngine = ttsEngine,
        requestAudioFocus = audioFocusManager::requestFocus,
        abandonAudioFocus = audioFocusManager::abandonFocus,
        isInCall = {
            audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        },
        scope = scope,
    )
}

// Phase 2: "Rules hardcoded to one package" (BUILD_PLAN.md). Google
// Messages here is just a common default app to test against — swap the
// package name for whatever's actually installed on your device. Real
// persistence and a rule editor arrive in Phase 3.
private val phase2HardcodedRules = listOf(
    Rule(
        id = "phase2-hardcoded",
        enabled = true,
        packageNames = setOf("com.google.android.apps.messaging"),
        action = RuleAction.SPEAK,
    ),
)
