package net.breadthcharge.exigentheron.domain

/**
 * PURE. No Android imports — see AGENTS.md §3.
 *
 * `java.util.regex.Matcher` (which `kotlin.text.Regex` wraps) has no
 * cooperative-cancellation checks of its own — interrupting the thread
 * running a match does nothing by default, as documented directly on
 * [RuleEngine]. This is the standard workaround: wrap the input so the
 * regex engine's own character-by-character reads become the
 * cancellation check. `runInterruptible` (used by [RuleEngine.evaluate])
 * calls `Thread.interrupt()` on cancellation; once that flag is set,
 * the next [get] call here aborts the match instead of letting it run
 * to completion (or to catastrophic-backtracking's un-completion).
 */
internal class InterruptibleCharSequence(private val source: CharSequence) : CharSequence {

    override val length: Int get() = source.length

    override fun get(index: Int): Char {
        if (Thread.currentThread().isInterrupted) throw InterruptedMatchException()
        return source[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        InterruptibleCharSequence(source.subSequence(startIndex, endIndex))

    override fun toString(): String = source.toString()
}

/**
 * Unchecked on purpose — [CharSequence.get] can't declare a checked
 * exception. Caught locally around the match call in [RuleEngine]; by
 * the time it's thrown, the surrounding `withTimeoutOrNull` has already
 * moved on, so this exists only to stop the [Thread] from continuing to
 * burn CPU on a match nobody is waiting for anymore.
 */
internal class InterruptedMatchException : RuntimeException()
