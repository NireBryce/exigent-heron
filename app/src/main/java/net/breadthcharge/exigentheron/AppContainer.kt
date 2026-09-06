package net.breadthcharge.exigentheron

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.breadthcharge.exigentheron.data.RuleRepository
import net.breadthcharge.exigentheron.data.SettingsRepository
import net.breadthcharge.exigentheron.domain.Deduplicator
import net.breadthcharge.exigentheron.domain.RuleEngineHolder
import net.breadthcharge.exigentheron.domain.SecretDetector
import net.breadthcharge.exigentheron.speech.AndroidTtsEngine
import net.breadthcharge.exigentheron.speech.AudioFocusManager
import net.breadthcharge.exigentheron.speech.SpeechQueue

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
