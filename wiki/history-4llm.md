# History (dense)

_Last modified: 2026-09-08_

## Contents

- [Scope](#scope)
- [Document-layer changes](#document-layer-changes)
- [Domain decisions](#domain-decisions)
- [Speech and audio decisions](#speech-and-audio-decisions)
- [Persistence and UI decisions](#persistence-and-ui-decisions)
- [Process, tooling, and licensing](#process-tooling-and-licensing)
- [Process failures worth keeping](#process-failures-worth-keeping)

## Scope

The full dated record of decisions made while implementing that
`AGENTS.md` does not narrate. `git log` is the accurate record of what
changed; this is the reasoning, kept where a commit message could not
carry it. [history.md](history.md) carries the subset a contributor is
most likely to trip over.

Superseded entries are kept, not deleted — this repo's convention is that
a bug recorded in a comment stays in the file, and the same applies here.

## Document-layer changes

**`AGENTS.md` rewritten from build spec to standing contract**
(2026-09-08). It opened with "Read this whole document before writing a
single file," worked in phases, and told an agent what to build — all
addressed to someone starting from nothing, which stopped being the
audience once all six phases were verified. It now states what the app
must keep satisfying.

Three constraints shaped the rewrite:

1. Its section numbers are cited from ~50 other files (Kotlin doc
   comments, CI, both hooks, `SECURITY.md`, every wiki page — ~230
   citations), so §0–§8 and §4.1–§4.10 are **stable by policy**, stated
   in the file itself: add, don't renumber.
2. Several files quote its sentences **verbatim** — `AndroidTtsEngine` on
   §4.8's "do not silently accept the system default", CI on §4.6's grep,
   the signing guard on §5's "Never commit a keystore or password",
   `architecture.md` on §4.1's "a stable hash of title+body". Those
   phrasings were preserved word-for-word rather than paraphrased.
3. It is always in an agent's context, so the rewrite cut for size:
   ~21.4KB → ~18KB, taken almost entirely from text duplicating the code
   it described (the `NotificationPayload` and `Rule` declarations,
   `SafeLog`'s signatures, the OTP keyword list, §5's manifest XML) and
   from phase narration [status.md](status.md) already held. What was
   *not* cut: the rules themselves, and the short rationale attached to a
   rule that exists to stop someone helpfully reversing it. §4 is still
   47% of the file and close to irreducible.

The rewrite surfaced two spec/code disagreements, both recorded rather
than quietly resolved: §4.5's user-editable OTP pattern list was
specified but not built (resolved the same day), and
`Settings.truncationLengthSeconds` had shipped with no spec section at
all (folded into §4.7).

**`AGENTS.md` §3 and `architecture.md` swapped roles** (2026-09-08). §3
used to carry a target package tree and a data-flow diagram, and
`architecture.md` tracked how far the real tree had caught up, explicitly
declining to keep a second copy. That split cost more than it paid — full
reasoning in [architecture-4llm.md](architecture-4llm.md). Recorded in
[styleguide.md](styleguide.md) as the second standing exception to "index
over restatement," after [testing.md](testing.md).

**`AGENTS.md` §3's "no logic worth testing" claim softened, and the
boundary given a CI grep** (2026-09-08). Two problems with one sentence.
First, the rule it stated — `domain/` has zero Android imports — was the
load-bearing assumption of the entire test strategy and was enforced by
review alone; `check.yml` now greps `^import android.` under `domain/`,
in the same shape as the existing `android.util.Log` check. Second,
"everything else is Android framework glue that is a pain to test and
should therefore contain no logic worth testing" read as a statement of
fact about the Android side, and wasn't one: `AppContainer` was deciding
what a revoked `BLUETOOTH_CONNECT` should mean, and Phase 4's
output-route bug lived in a seam every JVM test passed straight through.
The sentence is now framed as the goal the boundary serves, with
`app/src/androidTest/` named as the tool for the rest. **The rule itself
did not change and is not weaker.**

**`BUILD_PLAN.md` removed** (2026-09-08). All six phases built and
verified, so its forward-looking build order had nothing left to guide.
Its acceptance criteria live on as plain text in [status.md](status.md)'s
Spec column, [testing.md](testing.md)'s device-test steps, and code
comments citing "Phase N" directly; the phase rule (build and install at
each boundary, don't start N+1 before N's criteria pass) moved into
`AGENTS.md` §6 inline. `check_wiki.py` no longer scans it. That entry,
not a restored copy, is the record of what it said.

**Package name resolved.** §3's tree used `com.<yourdomain>.notifreader`
as a placeholder; the real package is `net.breadthcharge.exigentheron`.
Not a deviation — the placeholder filled in. Noted so a future reader
diffing the spec's tree against the real one doesn't wonder.

## Domain decisions

**`SecretDetector`'s downgrade mechanics, spelled out** (2026-09-05).
§4.5 says OTP-shaped content gets "suppress or downgrade to
announce-only" but doesn't say what text an announce-only downgrade
should carry. The literal-seeming answer — reuse the original
`Decision.Speak.text` — would defeat the downgrade entirely, since that
text is exactly what looked like a secret. `SecretDetector.scan`
synthesizes a generic "New notification from X" from the title alone; and
if the incoming decision is already `AnnounceOnly` with text that *itself*
still contains the flagged body (a user `Rule.template` embedding
`{body}` does this), it downgrades one step further to `Suppress` rather
than let it through disguised as an announcement. See
`SecretDetectorTest`'s `announce-only decision that still embeds the
flagged body` case.

**`RuleEngine`'s regex-timeout mitigation is real but narrower than §4.4
implies** (2026-09-05), verified rather than assumed:

- `withTimeoutOrNull(100.milliseconds)` bounds how long `evaluate()`
  waits; it does not stop the match. Confirmed directly — interrupting a
  thread mid-match on a genuinely slow pattern does not stop it, because
  `java.util.regex` has no cooperative-cancellation checks.
- The "crafted message causes catastrophic backtracking" scenario is
  narrower than the textbook framing on a modern JVM. OpenJDK memoizes
  failed backtracking positions (JDK-6328855), making classic
  nested-quantifier shapes linear-time. **Measured directly on OpenJDK
  21.0.12**: `(a+)+$`, `(a+)+b`, `(a|a)+$`, `(a|aa)+$`, and `(.*)+b` — the
  five textbook ReDoS examples — all resolved in ~0ms against adversarial
  input up to 40 characters.
- That memoization is **disabled whenever the pattern has a
  backreference**: `^(a+)+\1b$` measured 24 chars ≈ 277ms, 26 ≈ 1.1s,
  28 ≈ 4.5s — doubling roughly every 2 characters, genuinely exponential
  and still not interruptible.
- Net effect: the 100ms timeout mainly protects the *caller* from a
  backreference pattern, not the app from its CPU cost — a
  matched-but-timed-out rule leaves a real thread burning in
  `Dispatchers.Default` after `evaluate()` has moved on. The 2000-char
  input cap is what keeps that bounded per attack.

**Regex DoS mitigation: `InterruptibleCharSequence` + reject
backreferences, not RE2J** (2026-09-05, Phase 3). The gap
`RuleEngine`'s own doc comment already named got a real fix rather than
staying a documented caveat. Two options were weighed:

- **Chosen**: wrap regex input in `InterruptibleCharSequence` (checks
  `Thread.currentThread().isInterrupted` in `charAt`, throws) and run
  matching inside `kotlinx.coroutines.runInterruptible` (which calls
  `Thread.interrupt()` on cancellation) — the standard Java mitigation
  for un-cancellable `java.util.regex` matches. Plus `RuleValidator`
  rejecting backreferences outright at compile time, in both the editor
  and defensively in `RuleEngine.compileOrNull`, since even a cleanly
  interrupted match costs the full 100ms on every notification from that
  app, forever. No new dependency.
- **Considered and rejected**: `google/re2j` — linear-time by
  construction (RE2 supports neither backreferences nor lookaround),
  which would have let the timeout/interruption machinery be deleted
  entirely. Its license (BSD-3-Clause, confirmed by fetching the actual
  `LICENSE`) would have been fine, but it is a dependency outside §2's
  closed list, needs a NOTICE file, and drops lookahead/lookbehind for
  every future rule author — not just ones who'd have written a
  backreference.
- Consequence for tests: `RuleEngineTest`'s "catastrophic backtracking
  times out and suppresses rather than hanging" used a backreference
  pattern specifically *because* nothing else on this JVM reliably stays
  slow. That pattern is now rejected at compile time instead of ever
  running, so the test was rewritten to assert the new behavior rather
  than a timeout path this same change made unreachable for that input.
  `InterruptibleCharSequenceTest` covers the interruption mechanism
  directly instead.

**`RuleEngineHolder` rebuilds `RuleEngine` reactively** (2026-09-05,
Phase 3). §2 specifies Coroutines + Flow but not how a persisted rule
change reaches the running engine. Rebuilding from whatever
`RuleRepository.rules` currently emits is cheap (regex compilation over
~30 rules) and makes a rule edit take effect on the next notification;
the alternative would have made the editor feel broken. Lives in
`domain/` despite reacting to a `Flow` — `Flow`/`CoroutineScope` are
coroutines, not Android, so §3's rule is intact.
`SecretDetectorHolder` (2026-09-08) mirrors this exactly, for OTP
keywords.

## Speech and audio decisions

**`SpeechQueue` depends on function references, not `AudioFocusManager`
or `AudioManager`** (2026-09-05). `AudioFocusManager`'s constructor calls
`context.getSystemService(...)` immediately, making it — and anything
holding one — impossible to construct in a JVM test. `SpeechQueue` takes
`requestAudioFocus: () -> Boolean`, `abandonAudioFocus: () -> Unit`, and
`isInCall: () -> Boolean`; `AppContainer` wires the real ones. This is
what makes `SpeechQueueTest` possible without a second fake class beyond
`TtsEngine`'s.

**The in-call check moved ahead of the audio-focus request**
(2026-09-05). The first `speakOne()` requested focus, then checked
`isInCall()` and bailed — meaning every notification during a call
requested and immediately abandoned focus for an utterance it was never
going to speak. Caught by re-reading the method before running anything,
not by a test.

**`AppContainer.ttsEngine`/`speechQueue` became `var`s with
`rebuildTtsEngine`** (2026-09-06, Phase 4), rather than reconstructing
the whole container. §4.8 requires switching the TTS engine to "take
effect", but `AndroidTtsEngine` binds one `TextToSpeech` to one engine
package for its lifetime and `SpeechQueue` holds its engine by
constructor reference — neither can change engines in place. Rebuilding
just those two, shutting the old engine down only once the new one is
live, is the smallest change short of restarting the app.

**Queue-collapse-on-burst subsumed the old `DROP_OLDEST` regression
test** (2026-09-06, Phase 4). Implementing "drain whatever's already
buffered into one batch, collapse if >5" means the consumer no longer
looks at requests one at a time — it looks at the *channel's current
contents* before any reach the fake `TtsEngine`. That removed the
property the old overflow test depended on (gating one item's `onSpeak()`
to force the rest to overflow individually was only reliable when
processing was one-at-a-time); a burst large enough to overflow the
32-item channel is now deterministically also large enough to collapse,
and the surviving count depends on how much the consumer drains
concurrently with the test's sends. The old test was **removed rather
than forced to pass with a flaky workaround** — see `SpeechQueueTest.kt`'s
comment where it used to be. `DROP_OLDEST` itself is unchanged; it is
just no longer independently observable through `SpeechQueue`'s output.

**`AppContainer`'s gate lambdas split into `speech/GatePolicy.kt`**
(2026-09-08), following the precedent
`listener/NotificationExtractionPolicy.kt` set: policy as a pure function
over plain values, caller left as glue thin enough that reading it is
enough to believe it. `GatePolicy` mirrors
`NotificationManager.INTERRUPTION_FILTER_ALL` the same way
`SecretDetector` mirrors `VISIBILITY_PRIVATE`/`VISIBILITY_SECRET`, and
carries the same drift risk — mitigated by an instrumented test, which a
JVM test structurally cannot do.

**`SpeechGates` groups the queue's three gate lambdas** (2026-09-08).
Worth recording because it is **not** the fix it looks like: the three
`() -> Boolean` checks still sit adjacent inside `SpeechGates`, so
transposing two still compiles. Named arguments are what actually prevent
that, at both call sites; PR #51's test-side builder already covered the
test half. What this buys is a smaller constructor and one documented
home for the three. Landed with that understood, not as a claimed
compile-time guarantee.

## Persistence and UI decisions

**`SettingsRepository` scaffolded empty on purpose** (2026-09-05, Phase
3). `BUILD_PLAN.md` listed it under Phase 3, but no concrete setting
existed yet — headset-only, lock gate, DND, engine picker, announce-only
are all Phase 4 (§4.8–§4.10). Decided with the user: wire the DataStore
file now, so Phase 4 adds preference keys rather than plumbing, but add
no placeholder field just to have one — a fake field would violate §0's
YAGNI rule for no gain.

**No navigation-compose for the Phase 3 rule screens** (2026-09-05).
Three screens (main, rule list, rule editor) don't justify a dependency
outside §2's list. `MainActivity` holds a small `Screen` sealed interface
and a manual `when`; the installed-app picker is a `Dialog` launched from
inside the editor rather than a fourth destination, specifically to avoid
passing a picker result back across a screen boundary with no navigation
library to do it.

**Phase 4's "templates" and "announce-only mode" bullets were already
done, from Phase 1** (2026-09-06). `BUILD_PLAN.md` listed both under
Phase 4, but `Rule.template`/`RuleEngine.render` and
`RuleAction.ANNOUNCE_ONLY` landed in Phase 1 and had been exposed in the
editor since Phase 3 — confirmed by reading both files directly rather
than assuming the phase list was accurate. Phase 4 didn't re-touch
either; nothing was re-implemented.

**`AppContainer`'s Phase 2 hardcoded rule targeted
`com.google.android.apps.messaging`** (2026-09-05). `BUILD_PLAN.md` said
"rules hardcoded to one package" without naming one; Google Messages was
a common default to test against, not a meaningful choice. Superseded in
Phase 3 the same day.

**App name stays the repo codename, deliberately** (2026-09-05).
`res/values/strings.xml`'s `app_name` was flagged as possibly-an-oversight
before Phase 3 shipped it as the visible launcher label. Confirmed with
the user while planning Phase 3: keep `"exigent-heron"`.

**`RuleEditorViewModel.save()`'s form-only checks moved into
`RuleFormValidator`** (2026-09-06, post-Phase-5). A coverage review found
this was the one place in the UI layer breaking the pattern every other
screen follows — decision logic pulled into a pure, injectable function
rather than left inline in a `ViewModel` where it can't be unit-tested
without instantiating one. The regex half was already out
(`RuleValidator`); empty-app-selection and non-numeric-priority weren't.
`RuleFormValidator.validate(...)` now does both, returning parsed field
values alongside the error set so `save()` stays a thin caller.
`RuleFormValidatorTest` covers all four fields individually and in
combination (86 → 96 tests).

## Process, tooling, and licensing

**Project license: Apache-2.0** (2026-09-07). The repo sat public on
GitHub with no LICENSE file — legally "all rights reserved" despite the
visibility; nobody but the owner had any reuse rights. Chose Apache-2.0
over MIT for the express patent grant and the explicit default
contribution terms (a PR lands under the license without extra
paperwork), and because it is the license every shipped runtime
dependency already carries, so the tree speaks one license.
`THIRD_PARTY_NOTICES.md` records what the APK ships with — relevant
because `app/build.gradle.kts` strips the `META-INF/{AL2.0,LGPL2.1}`
license files the dependencies ship with (the standard template
exclusion, fine while sideloaded for personal use; the notices file
closes that gap if the APK is ever distributed). JUnit is the one
non-Apache dependency (EPL-1.0) and is test-only, never shipped.

**Code style is an `.editorconfig`, not ktlint or detekt** (2026-09-08).
A readability pass found the formatting genuinely inconsistent — three
files whose import blocks had drifted out of the ASCII order the other 22
use, and 13 lines over the ~120 columns everything else sits under. Two
ways to stop that: an `.editorconfig`, which Android Studio and IntelliJ
honour with nothing added to the build, or the ktlint Gradle plugin,
which actually enforces it in CI. **ktlint is the better tool and was
recommended; `.editorconfig` alone is what landed**, because §0 makes a
new dependency a question to ask rather than a call to make, §2's list is
meant to be the whole list, and the question hadn't been answered when
the work went in. The trade is named in `.editorconfig`'s own header
comment: this binds whatever editor opens the file, not CI, so an agent
writing through a plain filesystem write is unconstrained by it. ktlint
reads the same file and would enforce exactly what it already says, so
switching later is additive, not a rewrite. Recorded so the choice reads
as a deliberate deferral rather than an oversight.

## Process failures worth keeping

**The same test race was diagnosed and fixed twice, independently**
(noticed 2026-09-08). `SpeechQueueTest`'s burst-collapse test raced its
own consumer. It was fixed on the `ci-hardening` branch on 2026-09-06
(commit `6acca26`, gating on a `"gate"` utterance) and again on `main` as
`456f174` (gating on a `"primer"` utterance) — the same technique,
different names, neither aware of the other, because the branch was never
merged. Only the `main` one is live; the branch's copy became a merge
conflict that existed solely to be discarded.

The reason this went unnoticed for two days is that **nothing tracked the
open PR**: [open-threads.md](open-threads.md) said "no issues filed" at
the time, and an open branch with real work in it had no entry anywhere.
Recorded as the argument for that page listing open *PRs*, not just
issues, if this happens twice.

Related: much of the CI-hardening work (SHA-pinning, CodeQL) was first
worked out on that same `ci-hardening` branch (PR #17, 2026-09-06) and
was **re-derived against current `main` rather than rebased**, since the
branch's other commit had been overtaken.
