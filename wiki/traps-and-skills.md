# Traps & skills

_Last modified: 2026-09-08_

## Contents

- [A wiki table that mirrored the issue tracker, stale within the hour](#a-wiki-table-that-mirrored-the-issue-tracker-stale-within-the-hour)
- [Stale `./gradlew` references in AGENTS.md](#stale-gradlew-references-in-agentsmd)
- [A logcat tag guessed from a class name instead of read from the source](#a-logcat-tag-guessed-from-a-class-name-instead-of-read-from-the-source)
- [A ReDoS test that passed for the wrong reason](#a-redos-test-that-passed-for-the-wrong-reason)
- [scope.cancel() without join() let one test's coroutine bleed into the next](#scopecancel-without-join-let-one-tests-coroutine-bleed-into-the-next)
- [SafeLog.error masked a real test exception](#safelogerror-masked-a-real-test-exception)
- [git reset --hard, meant for a throwaway test commit, wiped real uncommitted edits](#git-reset---hard-meant-for-a-throwaway-test-commit-wiped-real-uncommitted-edits)
- [A concurrency test that pinned a race the producer usually won](#a-concurrency-test-that-pinned-a-race-the-producer-usually-won)
- [fwcd.kotlin's Gradle classpath resolver breaks on AGP 9's new extension API](#fwcdkotlins-gradle-classpath-resolver-breaks-on-agp-9s-new-extension-api)

Mistakes that have actually happened building this app, each linked to the
skill that holds the general form of the lesson. Adapted from
nixos-configs' own `traps-and-skills.md` — same idea (specific incident
here, reusable rule in the skill), same requirement that an entry be a
real, dated thing that happened, not a hypothetical.

## A wiki table that mirrored the issue tracker, stale within the hour

**2026-09-08.** `open-threads.md`'s "Tracked as GitHub issues" section
had said *"None yet"* since 2026-09-05. That went false on 2026-09-07,
when the first issues were filed, and nothing noticed for a day — on the
page whose entire job is tracking what's tracked.

The fix made it worse in a more interesting way: it was replaced with a
table of all seven open issues, number, title and label. Two issues in
that table were closed roughly an hour later, by CI work landing in the
same session. The section's own new text even said "it is a snapshot,
not a mirror" — while being a mirror.

**The lesson isn't "remember to update the table."** It's that a table
of open issues is state owned by a system outside this repo, so no
discipline applied inside the repo can hold it true, and
`check_wiki.py` can't check it either — it has no idea what the tracker
says. The shape was wrong, not the diligence.

What replaced it: the `gh issue list` command to get the live answer,
plus only the thing `gh` *can't* tell you — which issue corresponds to
which thread on that page. That's the general form of
[styleguide.md](styleguide.md)'s "index over restatement" applied to
state rather than to prose: link to the authority, keep only what the
authority doesn't know.

**See also:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — the same
discipline stated generally. Its "dated status snapshot" guidance covers
a fact that goes stale slowly; this is the sharper case where the fact
is owned elsewhere and can change with no commit here at all.

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

## git reset --hard, meant for a throwaway test commit, wiped real uncommitted edits

**2026-09-05.** While manually testing `git-guard-pretooluse.sh`'s new
`gh pr create` check, a temporary file was added, staged, and committed
by itself to exercise the hook against a real diff. Real,
already-written edits to `submit-a-pr/SKILL.md` and
`git-guard-pretooluse.sh` itself were sitting uncommitted in the same
working tree at the time — not staged, just present. "Clean up the test
commit" was done with `git reset --hard HEAD~1`, which does two things
at once: moves the branch pointer back a commit, *and* discards every
uncommitted change in the working tree, staged or not. It's exactly the
hook's own existing check for this ("discards uncommitted changes and
moves the branch, losing any commits not reachable elsewhere") — which
did not stop this from happening, because the mistake was recognizing
"this is a `git reset --hard`" as risk-relevant only in the abstract,
not registering that *this specific tree, right now* had real unstaged
work sitting in it. Recovered by redoing both edits from the same
message's own prior content — recoverable here only because the edits
were simple enough to reconstruct from memory of having just written
them; a longer or more exploratory edit would not have been.

The safe versions, used for the retry: commit real work immediately
after writing it, before any test/cleanup step that touches the tree
wholesale; when a throwaway commit needs undoing, `git reset --soft`
(moves the branch pointer, leaves the working tree alone) instead of
`--hard`; and for anything that doesn't need the actual worktree's
history at all, use a fully separate throwaway `git init` elsewhere,
which is what the hook's actual test suite moved to.

**General form:** the git-guard-pretooluse.sh hook already names the
right question ("confirm nothing unsaved is about to be dropped") — this
was a case of reading a hook's warning as background noise about the
command in general rather than a question about the actual, current
state of the tree it was about to run against. No existing skill states
this as its own rule; worth folding into a future skill on running
destructive git commands mid-task if this pattern recurs.

## A concurrency test that pinned a race the producer usually won

**2026-09-08.** `SpeechQueueTest`'s "a burst of more than 5 pending items
collapses to one summary utterance" enqueued ten items with no gate and
asserted the queue spoke exactly `"10 new notifications."`. It carried a
comment explaining why no gate was needed: `enqueue()` is a fast,
non-suspending `trySend()` and none of the ten calls yields. That is true
of the *producer* — and says nothing about the consumer, which
`SpeechQueue` runs on `Dispatchers.Default`, a different thread, free to
receive and batch the first few items while the remaining enqueues are
still in flight. A batch of 5 or fewer is spoken item-by-item instead of
collapsed, which is exactly what the assertion would then see.

It passed locally every time — a fast machine's producer wins that race
comfortably — and failed on CI's slower, more contended runner
(`107 tests completed, 1 failed`, run 34184923666), on an unrelated
docs-only PR whose diff touched nothing under `app/src/`. The fix parks
the consumer inside a primer utterance using the same `onSpeak` gate the
audio-focus test above it already used, so all ten land in the channel
before the consumer can receive any of them, making the batch
deterministically all ten. The assertion was not weakened to accommodate
the race.

Worth noting what could *not* be done: the failure was never reproduced
locally, including four runs of the pre-fix test pinned to two cores
under background load. The diagnosis rests on reading the code and on
CI's own failure, not on a local repro — and the fix removes the race
whether or not that particular interleaving is the one CI hit.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1,
and a close cousin of [the ReDoS
entry](#a-redos-test-that-passed-for-the-wrong-reason) above: a comment
asserting *why* a test is safe is a claim like any other, and this one
was load-bearing, confidently worded, and wrong about the half of the
system it didn't mention. A green concurrency test on one machine is
evidence about that machine's scheduling, not about the property the test
names — if a test's correctness depends on one thread losing a race,
gate the race instead of assuming the timing holds.

## fwcd.kotlin's Gradle classpath resolver breaks on AGP 9's new extension API

**2026-09-06.** The Kotlin extension's LSP (`fwcd.kotlin`) reported
"unresolved references" on every AndroidX/Compose import in
`SettingsRepository.kt`, with hover/autocomplete otherwise appearing to
work. The VS Code-side environment turned out to be fine (JDK 21 and the
Nix-built Android SDK were both correctly on `JAVA_HOME`/`ANDROID_HOME`
once VS Code itself was launched from a `direnv`-loaded terminal rather
than a desktop launcher — a separate, real issue along the way, since
`mkhl.direnv` only feeds new terminals/tasks it creates, not the
already-running extension host's `process.env`). The actual resolver
failure was hiding one layer down: KLS caches its resolved classpath in
a per-workspace SQLite database
(`~/.config/Code/User/workspaceStorage/<hash>/fwcd.kotlin/kls_database.db`)
and only recomputes it when the build files change, so a broken result
computed once (e.g. while `JAVA_HOME` was still missing) gets replayed
forever, silently, with no indication in the visible log that anything
is stale. Deleting that file forced a fresh resolution, which surfaced
the real error in the Kotlin extension's own gradle daemon log
(`~/.gradle/daemon/*/daemon-*.out.log`, not the "Kotlin Language Server"
output channel, which never shows the underlying Gradle failure):

```
Execution failed for task ':app:kotlinLSPProjectDeps'
> Could not find method getBootClasspath() for arguments []
  on object of type com.android.build.gradle.internal.dsl.ApplicationExtensionImpl$AgpDecorated.
```

KLS's built-in Gradle resolver calls the legacy `android.bootClasspath`
getter to find `android.jar`; AGP's new-style extension (in use here
since we're on AGP 9.3.0, this project's plugins block, no
`org.jetbrains.kotlin.android` — AGP 9 has built-in Kotlin support) no
longer exposes it under that name, so the task always throws and KLS
falls back to a stdlib-only classpath. Separately, this project also had
no `gradle/wrapper/gradle-wrapper.properties` committed (by design, per
`flake.nix` — no `gradlew` script/jar either), which meant tooling built
on the Gradle Tooling API (`vscjava.vscode-gradle`, and KLS's own
resolver) fell back to *their own* bundled default Gradle version
(9.2.0) instead of the Nix-pinned one (9.7.1) — too old for AGP 9.3.0
(needs ≥9.5.0), and a second, independent build failure until fixed.
Fixed with two changes, neither of which reintroduces a runnable
`./gradlew`: (1) `gradle/wrapper/gradle-wrapper.properties` committed
with just the version metadata, so Tooling-API clients discover the
right distribution; (2) a `kls-classpath` script at the project root —
KLS's documented escape hatch for supplying a classpath directly,
bypassing its own resolver entirely — that resolves `:app`'s
`debugCompileClasspath` via a throwaway `--init-script`
(`kls-classpath-init.gradle.kts`) instead of `android.bootClasspath`,
and appends `android.jar` found by globbing
`$ANDROID_HOME/platforms/android-*/android.jar` (the Nix-built SDK names
the directory `android-37.0`, not `android-37`, for `compileSdk = 37`).

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — a cache that
silently replays a stale result on failure (rather than surfacing the
failure) turns "fix the underlying cause" into "also remember to clear
the cache," and the log channel a tool advertises isn't necessarily
where its real errors land; worth checking a tool's on-disk daemon/cache
logs directly whenever its own visible output looks suspiciously clean
right up to the point of failure.

**Known residual caveat (2026-09-07):** once KLS can see the real
classpath, it surfaces a second, separate limitation: a
"`class kotlin.properties.ReadOnlyProperty was compiled with an
incompatible version of Kotlin`"-style diagnostic on delegate-shaped
usages (e.g. `preferencesDataStore` in `SettingsRepository.kt`). This is
a genuine Kotlin-metadata version skew, not a leftover of the classpath
bug: KLS's own installed language server
(`~/.config/Code/User/globalStorage/fwcd.kotlin/langServerInstall/server/lib/kotlin-compiler-2.1.0.jar`)
embeds Kotlin 2.1.0 for its analysis, while this project builds with
Kotlin 2.4.10 (`gradle/libs.versions.toml`) — so metadata emitted by the
newer compiler is unreadable by KLS's older one. It doesn't block the
classpath resolution `kls-classpath` provides and isn't something a
project-side fix addresses; it clears only once `fwcd.kotlin` ships with
an embedded compiler at or above the project's Kotlin version. This
matches an open, unresolved upstream bug —
[fwcd/kotlin-language-server#609](https://github.com/fwcd/kotlin-language-server/issues/609)
— with no workaround as of 2026-09-07; 0.2.36 (installed here) is still
the extension's latest published release, so there is no newer build to
pick up a fix from yet. Tracked locally as
[exigent-heron#24](https://github.com/NireBryce/exigent-heron/issues/24).
