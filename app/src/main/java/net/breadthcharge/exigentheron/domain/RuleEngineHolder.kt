package net.breadthcharge.exigentheron.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * ANDROID-FREE. `Flow`/`CoroutineScope` are kotlinx.coroutines, not Android.
 * No Android imports.
 *
 * Keeps a live [RuleEngine] rebuilt from whatever [rules] currently
 * emits, so a rule edit takes effect on the next notification instead
 * of requiring an app restart.
 * Reconstructing a [RuleEngine] is cheap — just regex compilation over
 * ~30 rules are typical (a small dataset for a single-user sideload) — so rebuilding on every emission needs no
 * debouncing.
 */
class RuleEngineHolder(
    rules: Flow<List<Rule>>,
    scope: CoroutineScope,
    private val onRuleFailure: (ruleId: String, reason: String) -> Unit = { _, _ -> },
) {
    private val engine = MutableStateFlow(RuleEngine(emptyList(), onRuleFailure))

    init {
        scope.launch {
            rules.collect { engine.value = RuleEngine(it, onRuleFailure) }
        }
    }

    suspend fun evaluate(payload: NotificationPayload): Decision = engine.value.evaluate(payload)
}
