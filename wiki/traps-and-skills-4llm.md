# Traps & skills (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Scope](#scope)
- [A wiki table that mirrored the issue tracker, stale within the hour](#a-wiki-table-that-mirrored-the-issue-tracker-stale-within-the-hour)
- [Stale `./gradlew` references in AGENTS.md](#stale-gradlew-references-in-agentsmd)
- [A logcat tag guessed from a class name](#a-logcat-tag-guessed-from-a-class-name)
- [A ReDoS test that passed for the wrong reason](#a-redos-test-that-passed-for-the-wrong-reason)
- [scope.cancel() without join() bled one test into the next](#scopecancel-without-join-bled-one-test-into-the-next)
- [SafeLog.error masked a real test exception](#safelogerror-masked-a-real-test-exception)
- [git reset --hard wiped real uncommitted edits](#git-reset---hard-wiped-real-uncommitted-edits)
- [A concurrency test that pinned a race the producer usually won](#a-concurrency-test-that-pinned-a-race-the-producer-usually-won)
- [`$!` tracked the emulator launcher, not the emulator](#-tracked-the-emulator-launcher-not-the-emulator)
- [`adb wait-for-device` waits forever](#adb-wait-for-device-waits-forever)
- [fwcd.kotlin's Gradle classpath resolver breaks on AGP 9](#fwcdkotlins-gradle-classpath-resolver-breaks-on-agp-9)

## Scope

Full write-ups for the entries summarized in
[traps-and-skills.md](traps-and-skills.md). Adapted from
`NireBryce/nixos-configs`'s own `traps-and-skills.md`: specific incident
here, reusable rule in the skill, and an entry must be a real, dated
thing that happened.

Kept in full because the *diagnosis* is the reusable part, and several of
these were only found by a chain of reasoning that a one-line summary
loses.

## A wiki table that mirrored the issue tracker, stale within the hour

**2026-09-08.** [open-threads.md](open-threads.md)'s "Tracked as GitHub
issues" section had said *"None yet"* since 2026-09-05. That went false
on 2026-09-07 when the first issues were filed, and nothing noticed for a
day — on the page whose entire job is tracking what's tracked.

The fix made it worse in a more interesting way: it was replaced with a
table of all seven open issues, number, title and label. Two of them were
closed roughly an hour later by CI work landing in the same session. The
section's own new text even said "it is a snapshot, not a mirror" — while
being a mirror.

**The lesson isn't "remember to update the table."** A table of open
issues is state owned by a system outside this repo, so no discipline
applied inside the repo can hold it true, and `check_wiki.py` cannot
check it either — it has no idea what the tracker says. The shape was
wrong, not the diligence.

What replaced it: the `gh issue list` command to get the live answer,
plus only the thing `gh` *can't* tell you — which issue corresponds to
which thread on that page. That is "index over restatement" applied to
state rather than prose: link to the authority, keep only what the
authority doesn't know.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md). Its "dated
status snapshot" guidance covers a fact that goes stale slowly; this is
the sharper case where the fact is owned elsewhere and can change with no
commit here at all.

## Stale `./gradlew` references in AGENTS.md

**2026-09-05.** `AGENTS.md` §6's Phase 0 and Phase 1 acceptance criteria
said `./gradlew assembleDebug` / `./gradlew testDebugUnitTest`, correct
when that section was written. A later, separate commit decided there
would be no `gradlew` in this repo at all — `flake.nix`'s shellHook says
so explicitly — and had no reason to touch §6 while making that call.

The two stale mentions sat there until a session reading that section
closely for an unrelated reason happened to notice the contradiction.
Fixed the same day.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 3 (a
cross-reference surviving after the thing it names changes) — this
incident is that skill's own worked example.

## A logcat tag guessed from a class name

**2026-09-05.** While writing `signing-guard-pretooluse.sh`'s suggested
fix for an unscoped `adb logcat`, the suggestion named `SafeLog` as the
tag to scope to (`adb logcat -s SafeLog`) — plausible, since that is the
class's name, but never checked against `SafeLog.kt`. The real tag is
`"ExigentHeron"` (`SafeLog.kt`'s own `TAG` constant).

Caught the same session, while writing
[architecture.md](architecture.md) from the real source file instead of
from memory of having written the hook a few messages earlier.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1 — a
class name and its logging tag are two different strings, and only one of
them was ever read from the file.

## A ReDoS test that passed for the wrong reason

**2026-09-05.** `RuleEngineTest`'s first "catastrophic backtracking times
out and suppresses rather than hanging" used the textbook ReDoS shape
`^(a+)+$` against 27 `a`s. It passed. It also asserted essentially
nothing: `RuleEngine.evaluate()` correctly returns `Suppress` both when a
rule's match times out *and* when a rule simply doesn't match, and this
input doesn't match that pattern (the trailing `!` blocks the `$`
anchor) — so a fast, ordinary non-match produced the exact `Suppress` the
test checked for, in under a millisecond, having exercised none of the
timeout path its own name claimed.

Caught only because the suite's timing (`0.053s`) was implausibly fast
for something meant to demonstrate exponential backtracking, and
re-deriving *why* — rather than accepting a green check — led to
measuring the pattern directly. OpenJDK memoizes exactly this shape; the
fix was a backreference pattern, verified separately to still be slow.
Measurements in [history-4llm.md](history-4llm.md).

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1 —
"this is the standard ReDoS example" was exactly as unverified a fact as
a logcat tag guessed from a class name, just wearing security-test
clothing. A passing assertion is not evidence the path it names was
reached; a suspiciously fast "slow path" test is a checkable red flag.

## scope.cancel() without join() bled one test into the next

**2026-09-05.** `SpeechQueueTest`'s first version tore down each test
with `scope.cancel()` as its last line. `cancel()` only *requests*
cancellation — it does not wait for the cancelled coroutine to stop.
`SpeechQueue`'s consumer runs on `Dispatchers.Default`, one pool shared
by the entire JVM test process, not scoped per test.

The suite ran five times clean, then on a sixth:
`Exception in thread "DefaultDispatcher-worker-1" ... Method e in
android.util.Log not mocked`, from inside `SpeechQueue.consume()`'s own
error-handling path — meaning a still-finishing coroutine from one test
genuinely overlapped a later one's setup or teardown, hit a real
exception, and crashed asynchronously without failing the JUnit run that
triggered it.

Fixed with a `CoroutineScope.shutdown()` helper calling
`coroutineContext[Job]?.cancelAndJoin()`. 13 clean runs after, where the
original showed the race roughly 1 run in 3.

**General form:** no existing skill covers this — cancellation requested
vs. a coroutine having actually stopped is a real distinction in
kotlinx.coroutines specifically, not a fact-hygiene case. Worth a skill
of its own if this repo writes more coroutine tests with shared-dispatcher
teardown; not written yet, because one incident isn't enough to know the
general shape.

## SafeLog.error masked a real test exception

**2026-09-05.** The same run demonstrated a second, separate problem once
the first was understood: `SafeLog.error()` calls `android.util.Log.e()`,
and Android SDK stub methods throw `RuntimeException("... not mocked")`
when invoked under a plain JVM unit test (no Robolectric — deliberately
not in §2's dependency list). `SpeechQueue.consume()`'s
`catch (e: Exception)` branch calling `SafeLog.error("...", e)` therefore
crashed *itself*, on a different exception than the one it was reporting
— which is how the race above surfaced as an opaque "Log not mocked"
message instead of whatever `speakOne()` had actually thrown.

Fixed with AGP's own
`android.testOptions.unitTests.isReturnDefaultValues = true`
(`app/build.gradle.kts`) — not Robolectric, no new dependency; stub calls
return a default instead of throwing.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 3 in
spirit — "this code path is never exercised by a JVM test" was true and
silently stopped being true once `speech/`/`listener/` tests started
covering Android-facing code. Next time a test targets code outside
`domain/`, check whether it can reach a real SDK stub before assuming a
JVM run is a clean signal either way.

## git reset --hard wiped real uncommitted edits

**2026-09-05.** While manually testing `git-guard-pretooluse.sh`'s new
`gh pr create` check, a temporary file was added, staged, and committed
by itself to exercise the hook against a real diff. Real, already-written
edits to `submit-a-pr/SKILL.md` and `git-guard-pretooluse.sh` itself were
sitting uncommitted in the same working tree — not staged, just present.

"Clean up the test commit" was done with `git reset --hard HEAD~1`, which
does two things at once: moves the branch pointer back, *and* discards
every uncommitted change in the tree, staged or not. That is exactly the
hook's own existing check ("discards uncommitted changes and moves the
branch, losing any commits not reachable elsewhere") — which did not stop
it, because the mistake was recognizing "this is a `git reset --hard`" as
risk-relevant *in the abstract*, not registering that this specific tree,
right then, had real unstaged work in it.

Recovered by redoing both edits from the same message's prior content —
recoverable only because they were simple enough to reconstruct. A longer
or more exploratory edit would not have been.

Safe versions, used for the retry: commit real work immediately after
writing it, before any test/cleanup step that touches the tree wholesale;
undo a throwaway commit with `git reset --soft`; and for anything that
doesn't need the real worktree's history, use a fully separate throwaway
`git init` elsewhere — which is what the hook's own test suite moved to.

**General form:** the hook already names the right question ("confirm
nothing unsaved is about to be dropped"). This was reading a warning as
background noise about the command in general rather than a question
about the actual current state of the tree. No skill states this yet;
worth folding into one on destructive git commands mid-task if the
pattern recurs.

## A concurrency test that pinned a race the producer usually won

**2026-09-08.** `SpeechQueueTest`'s "a burst of more than 5 pending items
collapses to one summary utterance" enqueued ten items with no gate and
asserted the queue spoke exactly `"10 new notifications."`. It carried a
comment explaining why no gate was needed: `enqueue()` is a fast,
non-suspending `trySend()` and none of the ten calls yields.

That is true of the *producer* — and says nothing about the consumer,
which `SpeechQueue` runs on `Dispatchers.Default`, a different thread,
free to receive and batch the first few items while the remaining
enqueues are still in flight. A batch of 5 or fewer is spoken
item-by-item instead of collapsed, which is exactly what the assertion
would then see.

It passed locally every time — a fast machine's producer wins that race
comfortably — and failed on CI's slower, more contended runner
(`107 tests completed, 1 failed`, run 34184923666), on an unrelated
docs-only PR whose diff touched nothing under `app/src/`.

The fix parks the consumer inside a primer utterance using the same
`onSpeak` gate the audio-focus test above it already used, so all ten
land in the channel before the consumer can receive any. **The assertion
was not weakened to accommodate the race.**

Worth noting what could *not* be done: the failure was never reproduced
locally, including four runs of the pre-fix test pinned to two cores
under background load. The diagnosis rests on reading the code and on
CI's failure, not on a local repro — and the fix removes the race whether
or not that interleaving is the one CI hit.

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), category 1,
and a close cousin of the ReDoS entry above: a comment asserting *why* a
test is safe is a claim like any other, and this one was load-bearing,
confidently worded, and wrong about the half of the system it didn't
mention. A green concurrency test on one machine is evidence about that
machine's scheduling, not about the property the test names.

This race was, separately, diagnosed and fixed **twice** in this repo,
independently, because an open branch holding one of the fixes was never
tracked — see [history-4llm.md](history-4llm.md).

## `$!` tracked the emulator launcher, not the emulator

**2026-09-08.** `scripts/test.sh` boots a headless emulator when no
device is attached, and stops it again only if it was the one that
started it — so it captured `$!` after `emulator -avd ... &` and used
that PID for both "is it still alive?" and "is it dead yet?".

Neither worked, because `emulator` is a launcher: it spawns
`qemu-system-x86_64-headless` as a separate process and can exit on its
own. `kill -0 "$EMULATOR_PID"` therefore returned false while the
emulator was very much running, so teardown printed "stopping the
emulator this script started" and returned success against a live qemu
that then held the AVD's lock files for the next run. The liveness check
had the mirror-image bug: it would have declared a healthy emulator dead.

Fixed by matching the real process by its own argument —
`pgrep -f "qemu-system.*-avd $AVD"` — and never tracking `$!` at all.

Found by asserting on the world rather than on the script's own output:
the run printed its "stopping" line and exited 0, and only
`pgrep -f qemu-system` afterwards showed the emulator still up. A script
saying it did something is not evidence that it did.

## `adb wait-for-device` waits forever

**2026-09-08.** The same script used `adb wait-for-device` before polling
`sys.boot_completed`. That call has no timeout: when an emulator started
but never registered with adb — a stale AVD lock from a previously killed
run — the script sat silently for eleven minutes with no output at all,
indistinguishable from a slow boot.

Two things were wrong beyond the missing bound. The emulator's own stdout
went to `/dev/null`, so the one artifact that would have explained it was
discarded; and the wait had no way to notice the emulator had gone away.

Now the boot wait is bounded (`BOOT_TIMEOUT`, default 600s), the
emulator's output is kept in `build/emulator.log`, and a failure prints
that log's tail rather than a bare timeout.

**General form:** a blocking call with no timeout turns "this failed"
into "this is still going", and discarding a subprocess's output removes
the only evidence of which one it was.

## fwcd.kotlin's Gradle classpath resolver breaks on AGP 9

**2026-09-06.** The Kotlin extension's LSP reported "unresolved
references" on every AndroidX/Compose import in `SettingsRepository.kt`,
with hover/autocomplete otherwise appearing to work.

The VS Code-side environment turned out to be fine — JDK 21 and the
Nix-built Android SDK were both correctly on `JAVA_HOME`/`ANDROID_HOME`
once VS Code itself was launched from a `direnv`-loaded terminal rather
than a desktop launcher. (That was a separate real issue along the way:
`mkhl.direnv` only feeds new terminals/tasks it creates, not the
already-running extension host's `process.env`.)

The actual resolver failure was one layer down. KLS caches its resolved
classpath in a per-workspace SQLite database
(`~/.config/Code/User/workspaceStorage/<hash>/fwcd.kotlin/kls_database.db`)
and only recomputes it when the build files change, so a broken result
computed once — e.g. while `JAVA_HOME` was still missing — gets replayed
forever, silently, with nothing in the visible log indicating staleness.

Deleting that file forced a fresh resolution, which surfaced the real
error in the Kotlin extension's own gradle daemon log
(`~/.gradle/daemon/*/daemon-*.out.log`, **not** the "Kotlin Language
Server" output channel, which never shows the underlying Gradle failure):

```
Execution failed for task ':app:kotlinLSPProjectDeps'
> Could not find method getBootClasspath() for arguments []
  on object of type com.android.build.gradle.internal.dsl.ApplicationExtensionImpl$AgpDecorated.
```

KLS's built-in Gradle resolver calls the legacy `android.bootClasspath`
getter to find `android.jar`; AGP's new-style extension (AGP 9.3.0 here,
no `org.jetbrains.kotlin.android` — AGP 9 has built-in Kotlin support) no
longer exposes it under that name, so the task always throws and KLS
falls back to a stdlib-only classpath.

Separately, this project had no `gradle/wrapper/gradle-wrapper.properties`
committed (by design, per `flake.nix` — no `gradlew` script or jar
either), which meant Tooling-API clients (`vscjava.vscode-gradle`, and
KLS's own resolver) fell back to *their own* bundled default Gradle
version (9.2.0) instead of the Nix-pinned one (9.7.1) — too old for AGP
9.3.0, which needs ≥9.5.0. A second, independent build failure.

Fixed with two changes, neither reintroducing a runnable `./gradlew`:

1. `gradle/wrapper/gradle-wrapper.properties` committed with just the
   version metadata, so Tooling-API clients discover the right
   distribution.
2. A `kls-classpath` script at the project root — KLS's documented escape
   hatch for supplying a classpath directly, bypassing its own resolver —
   that resolves `:app`'s `debugCompileClasspath` via a throwaway
   `--init-script` (`kls-classpath-init.gradle.kts`) instead of
   `android.bootClasspath`, and appends `android.jar` found by globbing
   `$ANDROID_HOME/platforms/android-*/android.jar` (the Nix-built SDK
   names the directory `android-37.0`, not `android-37`, for
   `compileSdk = 37`).

**General form:** skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — a cache that
silently replays a stale result on failure turns "fix the underlying
cause" into "also remember to clear the cache," and the log channel a
tool advertises isn't necessarily where its real errors land. Check a
tool's on-disk daemon/cache logs directly whenever its visible output
looks suspiciously clean right up to the point of failure.

**Known residual caveat (2026-09-07):** once KLS can see the real
classpath, it surfaces a second, separate limitation — a "`class
kotlin.properties.ReadOnlyProperty was compiled with an incompatible
version of Kotlin`"-style diagnostic on delegate-shaped usages (e.g.
`preferencesDataStore` in `SettingsRepository.kt`). This is genuine
Kotlin-metadata version skew, not a leftover of the classpath bug: KLS's
installed language server embeds Kotlin 2.1.0 for its analysis, while
this project builds with Kotlin 2.4.10 (`gradle/libs.versions.toml`), so
metadata emitted by the newer compiler is unreadable by the older one. It
does not block the classpath resolution `kls-classpath` provides and is
not addressable project-side; it clears only once `fwcd.kotlin` ships an
embedded compiler at or above the project's Kotlin version. Matches an
open upstream bug —
[fwcd/kotlin-language-server#609](https://github.com/fwcd/kotlin-language-server/issues/609)
— with no workaround as of 2026-09-07; 0.2.36 (installed here) is still
the extension's latest published release. Tracked locally as
[exigent-heron#24](https://github.com/NireBryce/exigent-heron/issues/24).
