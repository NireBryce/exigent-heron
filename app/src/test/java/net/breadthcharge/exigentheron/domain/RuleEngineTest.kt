package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RuleEngineTest {

    private fun payload(
        packageName: String = "com.example.chat",
        title: String? = "Alex",
        body: String? = "Hey, are you around?",
    ) = NotificationPayload(
        key = "k",
        packageName = packageName,
        postTime = 0,
        title = title,
        body = body,
        isGroupSummary = false,
        isOngoing = false,
        visibility = 1,
        contentHash = "irrelevant",
    )

    private fun rule(
        id: String,
        packageNames: Set<String> = emptySet(),
        titlePattern: String? = null,
        bodyPattern: String? = null,
        action: RuleAction = RuleAction.SPEAK,
        template: String? = null,
        priority: Int = 0,
        enabled: Boolean = true,
    ) = Rule(id, enabled, packageNames, titlePattern, bodyPattern, action, template, priority)

    @Test
    fun `default-deny -- nothing matches, nothing speaks`(): Unit = runBlocking {
        val engine = RuleEngine(rules = emptyList())

        val decision = engine.evaluate(payload())

        assertThat(decision).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `an app not covered by any rule stays silent`(): Unit = runBlocking {
        val engine = RuleEngine(rules = listOf(rule(id = "r1", packageNames = setOf("com.other.app"))))

        assertThat(engine.evaluate(payload(packageName = "com.example.chat")))
            .isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `empty packageNames matches every app`(): Unit = runBlocking {
        val engine = RuleEngine(rules = listOf(rule(id = "r1", packageNames = emptySet())))

        assertThat(engine.evaluate(payload())).isInstanceOf(Decision.Speak::class.java)
    }

    @Test
    fun `higher priority rule wins even when listed second`(): Unit = runBlocking {
        val engine = RuleEngine(
            rules = listOf(
                rule(id = "low", action = RuleAction.SUPPRESS, priority = 0),
                rule(id = "high", action = RuleAction.SPEAK, priority = 10),
            ),
        )

        val decision = engine.evaluate(payload())

        assertThat(decision).isInstanceOf(Decision.Speak::class.java)
    }

    @Test
    fun `disabled rule is skipped`(): Unit = runBlocking {
        val engine = RuleEngine(
            rules = listOf(rule(id = "r1", action = RuleAction.SPEAK, enabled = false)),
        )

        assertThat(engine.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `title and body patterns must both match when both are set`(): Unit = runBlocking {
        val engine = RuleEngine(
            rules = listOf(rule(id = "r1", titlePattern = "^Alex$", bodyPattern = "^nope$")),
        )

        assertThat(engine.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `template renders title and body placeholders`(): Unit = runBlocking {
        val engine = RuleEngine(rules = listOf(rule(id = "r1", template = "Message from {title}")))

        val decision = engine.evaluate(payload(title = "Alex")) as Decision.Speak

        assertThat(decision.text).isEqualTo("Message from Alex")
    }

    @Test
    fun `no template falls back to a plain title colon body render`(): Unit = runBlocking {
        val engine = RuleEngine(rules = listOf(rule(id = "r1")))

        val decision = engine.evaluate(payload(title = "Alex", body = "hi")) as Decision.Speak

        assertThat(decision.text).isEqualTo("Alex: hi")
    }

    @Test
    fun `ANNOUNCE_ONLY and SUPPRESS actions produce the matching Decision type`(): Unit = runBlocking {
        val announceEngine = RuleEngine(rules = listOf(rule(id = "r1", action = RuleAction.ANNOUNCE_ONLY)))
        val suppressEngine = RuleEngine(rules = listOf(rule(id = "r1", action = RuleAction.SUPPRESS)))

        assertThat(announceEngine.evaluate(payload())).isInstanceOf(Decision.AnnounceOnly::class.java)
        assertThat(suppressEngine.evaluate(payload())).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `an invalid regex is reported and treated as non-matching, not a crash`(): Unit = runBlocking {
        val failures = mutableListOf<Pair<String, String>>()
        val engine = RuleEngine(
            rules = listOf(
                rule(id = "broken", titlePattern = "(unclosed", priority = 10),
                rule(id = "fallback", action = RuleAction.SPEAK, priority = 0),
            ),
            onRuleFailure = { id, reason -> failures += id to reason },
        )

        val decision = engine.evaluate(payload())

        assertThat(decision).isInstanceOf(Decision.Speak::class.java) // fell through to "fallback"
        assertThat(failures).hasSize(1)
        assertThat(failures.single().first).isEqualTo("broken")
    }

    @Test
    fun `a backreference pattern is rejected at compile time, not run into a timeout`(): Unit = runBlocking {
        // Historical note: this test used to feed `^(a+)+\1b$` — a
        // backreference disables OpenJDK's backtracking memoization
        // (JDK-6328855) and is genuinely exponential (measured 24 chars
        // ≈ 277ms, 26 ≈ 1.1s, 28 ≈ 4.5s, doubling roughly every 2 chars)
        // — to exercise evaluate()'s withTimeoutOrNull path. Phase 3
        // closed that specific gap by rejecting backreferences in
        // RuleValidator at rule-compile-time instead of letting them run
        // at all, so the pattern below now fails here, before matching
        // ever starts — see RuleEngine's doc comment. The interruption
        // mechanism itself (InterruptibleCharSequence + runInterruptible)
        // is covered separately in InterruptibleCharSequenceTest, since
        // OpenJDK's memoization otherwise makes a genuinely slow
        // *non*-backreference pattern hard to construct at all.
        val failures = mutableListOf<Pair<String, String>>()
        val engine = RuleEngine(
            rules = listOf(rule(id = "evil", bodyPattern = """^(a+)+\1b$""")),
            onRuleFailure = { id, reason -> failures += id to reason },
        )

        val decision = engine.evaluate(payload(body = "a".repeat(24) + "!"))

        assertThat(decision).isInstanceOf(Decision.Suppress::class.java)
        assertThat(failures).hasSize(1)
        assertThat(failures.single().first).isEqualTo("evil")
        assertThat(failures.single().second).contains("backreferences")
    }
}
