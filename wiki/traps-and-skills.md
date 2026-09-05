# Traps & skills

## Contents

- [Stale `./gradlew` references in AGENTS.md](#stale-gradlew-references-in-agentsmd)
- [A logcat tag guessed from a class name instead of read from the source](#a-logcat-tag-guessed-from-a-class-name-instead-of-read-from-the-source)
- [A ReDoS test that passed for the wrong reason](#a-redos-test-that-passed-for-the-wrong-reason)
- [scope.cancel() without join() let one test's coroutine bleed into the next](#scopecancel-without-join-let-one-tests-coroutine-bleed-into-the-next)
- [SafeLog.error masked a real test exception](#safelogerror-masked-a-real-test-exception)

Mistakes that have actually happened building this app, each linked to the
skill that holds the general form of the lesson. Adapted from
nixos-configs' own `traps-and-skills.md` — same idea (specific incident
here, reusable rule in the skill), same requirement that an entry be a
real, dated thing that happened, not a hypothetical.

## Stale `./gradlew` references in AGENTS.md

**2026-09-05.** `AGENTS.md` §6's Phase 0 and Phase 1 acceptance criteria
said `./gradlew assembleDebug` / `./gradlew testDebugUnitTest`, correct
when that spec section was first written. A later, separate commit ("Phase
0: Kotlin/Android project skeleton, Nix dev shell...") decided there would
be no `gradlew` in this repo at all — `flake.nix`'s own shellHook says so
explicitly — and had no reason to touch §6 while making that call. The two
stale mentions sat there until a session reading that section closely
enough for an unrelated reason (adapting another repo's wiki skills)
happened to notice the contradiction. Fixed the same day.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 3 (a
cross-reference surviving after the thing it names changes) — this
incident is that skill's own worked example.

## A logcat tag guessed from a class name instead of read from the source

**2026-09-05.** While writing `signing-guard-pretooluse.sh`'s suggested fix
for an unscoped `adb logcat`, the suggestion named `SafeLog` as the tag to
scope to (`adb logcat -s SafeLog`) — plausible, since that's the class's
name, but never actually checked against `SafeLog.kt`. The real tag is
`"ExigentHeron"` (`SafeLog.kt`'s own `TAG` constant). Caught the same
session, while writing [architecture.md](architecture.md) from the real
source file instead of from memory of writing the hook a few messages
earlier.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1 (a
specific external fact stated with more confidence than what was actually
checked) — a class name and its logging tag are two different strings,
and only one of them was ever read from the file.

## A ReDoS test that passed for the wrong reason

**2026-09-05.** `RuleEngineTest`'s first version of "catastrophic
backtracking times out and suppresses rather than hanging" used the
textbook ReDoS shape `^(a+)+$` against 27 `a`s — the standard example
from every regex-DoS writeup. It passed. It also asserted essentially
nothing: `RuleEngine.evaluate()` correctly returns `Suppress` both when a
rule's match times out *and* when a rule just doesn't match, and this
input doesn't match that pattern (the trailing `!` blocks the `$` anchor)
— so a fast, ordinary non-match produced the exact same `Suppress` the
test was checking for, in under a millisecond, having exercised none of
the timeout path the test's own name claimed to cover. Caught only
because the test's individual timing (`0.053s` for the whole suite) was
implausibly fast for something meant to demonstrate exponential
backtracking, and re-deriving *why* — rather than accepting a green
check mark — led to actually measuring the pattern directly. It turned
out OpenJDK memoizes exactly this shape (see
[history.md](history.md)'s `RuleEngine` entry); the fix was a
backreference pattern, verified separately to still be slow.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1 —
"this is the standard ReDoS example" was exactly as unverified a fact as
a logcat tag guessed from a class name, just wearing security-test
clothing. A passing assertion is not, on its own, evidence the code path
it names was ever reached; a suspiciously fast "slow path" test is a
specific, checkable signal worth treating as a red flag rather than a
lucky green.

## scope.cancel() without join() let one test's coroutine bleed into the next

**2026-09-05.** `SpeechQueueTest`'s first version tore down each test
with `scope.cancel()` as its last line. `cancel()` only *requests*
cancellation — it doesn't wait for the cancelled coroutine to actually
stop. `SpeechQueue`'s consumer runs on `Dispatchers.Default`, one thread
pool shared by the entire JVM test process, not scoped per test. Ran the
suite five times in a row clean, then on a sixth run: `Exception in
thread "DefaultDispatcher-worker-1" ... Method e in android.util.Log not
mocked`, coming from inside `SpeechQueue.consume()`'s own error-handling
path — meaning a still-finishing coroutine from one test genuinely
overlapped a later one's setup or teardown, hit a real exception, and
the crash surfaced asynchronously without failing the JUnit run that
triggered it. Fixed with a `CoroutineScope.shutdown()` test helper that
calls `coroutineContext[Job]?.cancelAndJoin()` instead — 13 clean runs
after, where the original showed the race roughly 1 run in 3.

**General form:** no existing skill covers this one — cancellation being
requested vs. a coroutine actually having stopped is a real distinction
in kotlinx.coroutines specifically, not a fact-hygiene case about an
unverified claim. Worth a skill of its own if this repo starts writing
more coroutine tests with shared-dispatcher teardown; not written yet
because one incident isn't enough to know the general shape.

## SafeLog.error masked a real test exception

**2026-09-05.** The same run above also demonstrated a second, separate
problem once the first was understood: `SafeLog.error()` calls
`android.util.Log.e()`, and Android SDK stub methods throw
`RuntimeException("... not mocked")` when actually invoked under a plain
JVM unit test (no Robolectric — not in this repo's dependency list, see
`AGENTS.md` §2). `SpeechQueue.consume()`'s `catch (e: Exception)` branch
calling `SafeLog.error("...", e)` to log a real failure therefore crashed
*itself*, on a different exception than the one it was trying to report
— which is how the race above surfaced as an opaque "Log not mocked"
message instead of whatever `speakOne()` had actually thrown. Fixed with
AGP's own `android.testOptions.unitTests.isReturnDefaultValues = true`
(`app/build.gradle.kts`) — not Robolectric, no new dependency, stub
calls return a default value instead of throwing.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 3 in
spirit (a thing that was true — "this code path is never exercised by a
JVM test" — silently stopped being true once `speech/`/`listener/` tests
started exercising Android-facing code, and nothing caught the mismatch
until it crashed) — worth remembering the next time a test targets code
outside `domain/`: check whether it can reach a real Android SDK stub
call before assuming a JVM test run is a clean signal either way.
