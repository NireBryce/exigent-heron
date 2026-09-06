package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RuleEngineHolderTest {

    // Dispatchers.Unconfined, not a TestScope/runTest virtual clock:
    // RuleEngine.evaluate() races a real withTimeoutOrNull against a
    // real runInterruptible(Dispatchers.Default) hop (see RuleEngine's
    // doc comment) — mixing that with virtual time makes the timeout
    // fire spuriously, since the virtual clock has no reason to wait for
    // a real background dispatch. runBlocking + Unconfined keeps
    // everything on real wall-clock time (matching RuleEngineTest's own
    // convention) while still making the holder's internal
    // `rules.collect { ... }` resume synchronously the moment a test
    // writes a new `rules.value` — no `runCurrent()`/advanceUntilIdle()
    // needed.
    private fun testScope() = CoroutineScope(Dispatchers.Unconfined)

    private fun payload(packageName: String = "com.example.chat") = NotificationPayload(
        key = "k",
        packageName = packageName,
        postTime = 0,
        title = "Alex",
        body = "hi",
        isGroupSummary = false,
        isOngoing = false,
        visibility = 1,
        contentHash = "irrelevant",
    )

    private fun rule(id: String, packageNames: Set<String> = emptySet(), action: RuleAction = RuleAction.SPEAK) =
        Rule(id = id, enabled = true, packageNames = packageNames, action = action)

    @Test
    fun `starts with no rules -- default-deny before the first emission`(): Unit = runBlocking {
        val rules = MutableStateFlow<List<Rule>>(emptyList())
        val holder = RuleEngineHolder(rules, testScope())

        assertThat(holder.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `a rule added later is picked up without recreating the holder`(): Unit = runBlocking {
        val rules = MutableStateFlow<List<Rule>>(emptyList())
        val holder = RuleEngineHolder(rules, testScope())
        assertThat(holder.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)

        rules.value = listOf(rule("r1"))

        assertThat(holder.evaluate(payload())).isInstanceOf(Decision.Speak::class.java)
    }

    @Test
    fun `a rule edit -- e_g_ disabling it -- also takes effect live`(): Unit = runBlocking {
        val rules = MutableStateFlow(listOf(rule("r1")))
        val holder = RuleEngineHolder(rules, testScope())
        assertThat(holder.evaluate(payload())).isInstanceOf(Decision.Speak::class.java)

        rules.value = listOf(rule("r1").copy(enabled = false))

        assertThat(holder.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)
    }
}
