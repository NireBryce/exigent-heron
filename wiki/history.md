# History

## Contents

- [This repo's history so far](#this-repos-history-so-far)
- [Decisions made while implementing, beyond AGENTS.md's own text](#decisions-made-while-implementing-beyond-agentsmds-own-text)

## This repo's history so far

`git log` is the accurate record at this size; this page exists for the
*reasoning* behind a decision once one needs more context than a commit
message carries, not as a running paraphrase of the log — see
[styleguide.md](styleguide.md)'s "index over restatement."

## Decisions made while implementing, beyond AGENTS.md's own text

- **Package name resolved**: `AGENTS.md` §3's tree uses
  `com.<yourdomain>.notifreader` as a placeholder; the real package is
  `net.breadthcharge.exigentheron` (`app/build.gradle.kts`). Not a
  deviation, just the placeholder filled in — noted here rather than
  silently, since a future reader diffing the spec's tree against the
  real one would otherwise wonder whether it was intentional.
- **`SecretDetector`'s downgrade mechanics, spelled out** (2026-09-05):
  `AGENTS.md` §4.5 says OTP-shaped content gets "suppress or downgrade to
  announce-only" but doesn't say what text an announce-only downgrade
  should actually carry. The literal-seeming answer — reuse the original
  `Decision.Speak.text` — would defeat the downgrade entirely, since that
  text is exactly what looked like a secret. `SecretDetector.scan`
  instead synthesizes a generic "New notification from X" from the title
  alone, and if the incoming decision is already `AnnounceOnly` with text
  that *itself* still contains the flagged body (a user `Rule.template`
  embedding `{body}` would do this), downgrades one step further to
  `Suppress` rather than let it through disguised as an announcement. See
  `SecretDetector.kt`'s own doc comment and `SecretDetectorTest`'s
  `announce-only decision that still embeds the flagged body` case.
- **`RuleEngine`'s regex-timeout mitigation is real but narrower than
  `AGENTS.md` §4.4 implies** (2026-09-05), verified rather than assumed:
  - `withTimeoutOrNull(100.milliseconds)` bounds how long `evaluate()`
    waits; it does not stop the match itself. Confirmed directly —
    interrupting a thread mid-match on a genuinely slow pattern does not
    stop it (`java.util.regex` has no cooperative-cancellation checks).
  - The "crafted message causes catastrophic backtracking" scenario the
    spec warns about is narrower than the textbook framing suggests on
    a modern JVM. OpenJDK memoizes failed backtracking positions
    (JDK-6328855), which makes classic nested-quantifier shapes like
    `(a+)+$` linear-time rather than exponential — measured directly:
    `(a+)+$`, `(a+)+b`, `(a|a)+$`, `(a|aa)+$`, and `(.*)+b`, the five
    textbook ReDoS examples, all resolved in ~0ms against adversarial
    input up to 40 characters on this JVM (OpenJDK 21.0.12). That
    memoization is disabled whenever the pattern has a backreference —
    `^(a+)+\1b$` measured 24 chars ≈ 277ms, 26 ≈ 1.1s, 28 ≈ 4.5s,
    doubling roughly every 2 characters, genuinely exponential and still
    not interruptible.
  - Net effect: the 100ms timeout mainly protects the *caller* from a
    backreference pattern, not the app from the CPU cost of one — a
    matched-but-timed-out rule leaves a real thread burning in
    `Dispatchers.Default` after `evaluate()` has moved on. The 2000-char
    input cap is what keeps that bounded per attack. See
    `RuleEngine.kt`'s own doc comment for the same explanation kept next
    to the code it describes, and
    [traps-and-skills.md](traps-and-skills.md) for how the *first*
    attempt at testing this got it wrong.
- **`SpeechQueue` depends on function references, not `AudioFocusManager`
  or `AudioManager` directly** (2026-09-05): `AudioFocusManager`'s
  constructor calls `context.getSystemService(...)` immediately, which
  makes it — and anything holding one — impossible to construct in a
  JVM test. `SpeechQueue` instead takes `requestAudioFocus: () -> Boolean`,
  `abandonAudioFocus: () -> Unit`, and `isInCall: () -> Boolean`;
  `AppContainer` wires the real ones (`audioFocusManager::requestFocus`,
  a real `AudioManager.mode` check). This is what makes
  `SpeechQueueTest` possible at all without a second fake class beyond
  `TtsEngine`'s — see `SpeechQueue.kt`'s own doc comment.
- **The in-call check moved ahead of the audio-focus request**
  (2026-09-05): the first version of `SpeechQueue.speakOne()` requested
  focus, then checked `isInCall()` and bailed. That's backwards — it
  meant every notification arriving during a call would request (and
  immediately abandon) audio focus for an utterance it was never going
  to speak. Caught by re-reading the method before running anything, not
  by a test; reordered so the in-call check runs first and focus is
  never touched at all when it's going to skip anyway.
- **`AppContainer`'s Phase 2 hardcoded rule targeted `com.google.android.apps.messaging`**
  (2026-09-05): `BUILD_PLAN.md` said "rules hardcoded to one package"
  without naming one. Google Messages was just a common default app to
  test against, not a meaningful choice. Superseded in Phase 3, same day:
  `phase2HardcodedRules` is gone, replaced by real `RuleRepository`
  persistence and a rule editor — see the entries below.
- **App name stays the repo codename, deliberately** (2026-09-05):
  `res/values/strings.xml`'s `app_name` was flagged in
  [open-threads.md](open-threads.md) as possibly-an-oversight before
  Phase 3 (the UI phase) shipped it as the visible launcher label.
  Confirmed with the user while planning Phase 3: keep
  `"exigent-heron"` — intentional, not a placeholder left behind.
- **Regex DoS mitigation: `InterruptibleCharSequence` + reject
  backreferences, not RE2J** (2026-09-05, Phase 3): the gap
  `RuleEngine`'s own doc comment already named — a backreference pattern
  is genuinely exponential *and* the 100ms `withTimeoutOrNull` doesn't
  actually stop the underlying match, only abandons the caller — got a
  real fix this phase rather than staying a documented caveat. Two
  options were weighed:
  - **Chosen**: wrap regex input in `InterruptibleCharSequence` (checks
    `Thread.currentThread().isInterrupted` in `charAt`, throws) and run
    matching inside `kotlinx.coroutines.runInterruptible` (which calls
    `Thread.interrupt()` on cancellation) — the standard Java mitigation
    for un-cancellable `java.util.regex` matches. Plus: `RuleValidator`
    now rejects backreferences outright at rule-compile-time (both in the
    rule editor and defensively in `RuleEngine.compileOrNull`), since even
    a cleanly-interrupted match still costs the full 100ms on every
    notification from that app, forever. No new dependency.
  - **Considered and rejected**: `google/re2j` — linear-time by
    construction (RE2 doesn't support backreferences or lookaround at
    all), which would have let the timeout/interruption machinery be
    deleted entirely. Its license (BSD-3-Clause, confirmed by fetching
    its actual `LICENSE`) is permissive and would have been fine, but it's
    a dependency outside `AGENTS.md` §2's closed list, needs a NOTICE
    file for attribution, and drops lookahead/lookbehind support for
    every future rule author, not just ones who'd have written a
    backreference. Chosen fix closes the actually-documented gap at a
    much smaller cost; see `RuleEngine.kt`'s updated doc comment for the
    same reasoning kept next to the code.
  - One existing test, `RuleEngineTest`'s "catastrophic backtracking
    times out and suppresses rather than hanging", used a backreference
    pattern specifically *because* nothing else on this JVM reliably
    stays slow (see the ReDoS entry above and
    [traps-and-skills.md](traps-and-skills.md)) — that pattern now gets
    rejected at compile time instead of ever running, so the test was
    rewritten to assert the new (correct) behavior rather than the old
    timeout path, which this same change made unreachable for that input.
    `InterruptibleCharSequenceTest` covers the interruption mechanism
    directly instead, since a genuinely slow *non*-backreference pattern
    is now hard to construct at all on this JVM.
- **`RuleEngineHolder` rebuilds `RuleEngine` reactively, not once at
  `AppContainer` construction** (2026-09-05, Phase 3): `AGENTS.md` §2
  specifies Coroutines + Flow but doesn't say how a persisted rule change
  reaches the running `RuleEngine`. Rebuilding a `RuleEngine` from
  whatever `RuleRepository.rules` currently emits (cheap — just regex
  compilation over ~30 rules) is what makes a rule edit take effect on
  the next notification rather than requiring an app restart or
  force-stop; the alternative (construct once, ignore later edits until
  restart) would have made the rule editor feel broken. Lives in
  `domain/` despite reacting to a `Flow` — `Flow`/`CoroutineScope` are
  coroutines, not Android, so this doesn't violate §3's "zero Android
  imports" rule.
- **`SettingsRepository` scaffolded empty on purpose** (2026-09-05,
  Phase 3): `BUILD_PLAN.md` lists it under Phase 3, but no concrete
  setting exists yet — headset-only/lock-gate/DND/engine-picker/
  announce-only are all Phase 4 (`AGENTS.md` §4.8-§4.10). Decided with
  the user: wire the DataStore file now (so Phase 4 only adds preference
  keys, not plumbing) but add no placeholder field just to have one — a
  fake field would violate `AGENTS.md` §0's YAGNI rule for no real gain.
- **No navigation-compose dependency for the Phase 3 rule screens**
  (2026-09-05): three screens (main, rule list, rule editor) don't
  justify a new dependency outside `AGENTS.md` §2's list. `MainActivity`
  holds a small `Screen` sealed interface and a manual `when` instead;
  the installed-app picker is a `Dialog` launched from inside the rule
  editor rather than a fourth nav destination, specifically to avoid
  needing to pass a picker result back across a screen boundary with no
  navigation library to do it.
- **Phase 4's "templates" and "announce-only mode" bullets were already
  done, from Phase 1** (2026-09-06): `BUILD_PLAN.md` lists both under
  Phase 4, but `Rule.template`/`RuleEngine.render` and
  `RuleAction.ANNOUNCE_ONLY` landed in Phase 1 and have been exposed in
  the rule editor since Phase 3 — confirmed by reading both files
  directly rather than assuming the phase list was still accurate.
  Phase 4 didn't re-touch either; nothing here was re-implemented.
- **`AppContainer.ttsEngine`/`speechQueue` became `var`s, with a
  `rebuildTtsEngine` method, rather than reconstructing the whole
  container** (2026-09-06, Phase 4): `AGENTS.md` §4.8 requires switching
  the TTS engine to "take effect," but `AndroidTtsEngine` binds one
  `TextToSpeech` instance to one engine package for its lifetime, and
  `SpeechQueue` holds its `TtsEngine` by constructor reference — neither
  can be told to change engines in place. Rebuilding just those two
  (shutting the old engine down only once the new one is live) is the
  smallest change that makes a settings-screen choice actually apply,
  short of restarting the whole app. The container's first engine choice
  is read synchronously (`runBlocking` on `settingsRepository.settings.first()`)
  at construction time — a local Preferences-file read, not a network
  call — since `App.onCreate()` has no later point to construct these
  from before something might need to speak.
- **`SpeechQueue`'s queue-collapse-on-burst subsumes its old DROP_OLDEST
  regression test** (2026-09-06, Phase 4): implementing "drain whatever's
  already buffered into one batch, collapse if >5" means the consumer no
  longer looks at requests one at a time — it looks at the *channel's
  current contents* before any of them reach the fake `TtsEngine`. That
  removed the property the old overflow test depended on (gating one
  item's `onSpeak()` to force the rest to overflow individually was only
  reliable when processing was one-at-a-time); a burst large enough to
  overflow the 32-item channel is now, deterministically, also large
  enough to collapse, and the exact surviving count depends on how much
  the real consumer drains concurrently with the test's sends rather than
  being a fixed number. The old test was removed rather than forced to
  pass with a flaky workaround — see `SpeechQueueTest.kt`'s own comment
  where it used to be. `DROP_OLDEST` itself is unchanged (it's `Channel`'s
  own guarantee); it's just no longer independently observable through
  `SpeechQueue`'s output once collapse always fires first for a burst
  that size.
- **`RuleEditorViewModel.save()`'s form-only checks moved into
  `RuleFormValidator`** (2026-09-06, post-Phase-5): a coverage review
  found this was the one place in the UI layer that broke the pattern
  every other screen already follows — decision logic pulled into a
  pure, injectable function (`RuleValidator`, `RuleCodec`,
  `shouldDropNotification`) rather than left inline in a `ViewModel`
  where it can't be unit-tested without instantiating one. The regex
  half was already out (`RuleValidator`); the empty-app-selection and
  non-numeric-priority checks weren't. `RuleFormValidator.validate(...)`
  now does both, returning parsed field values alongside the error set
  so `save()` stays a thin caller. `RuleFormValidatorTest` covers all
  four fields individually and in combination (86 → 96 tests).
- **CI's third-party GitHub Actions pinned to a commit SHA, and a CodeQL
  workflow added** (2026-09-06, post-Phase-5): `check.yml` and
  `update-flake-lock.yml` both referenced `DeterminateSystems/nix-installer-action@main`
  — a floating branch ref, not a version — and `update-flake-lock.yml`
  likewise referenced `DeterminateSystems/update-flake-lock@main`. A
  compromised or force-pushed upstream on either would run arbitrary
  code in this repo's CI with no diff to review first; `update-flake-lock.yml`
  in particular runs with `contents: write`/`pull-requests: write`,
  making that more consequential there, not less. Both now pin to the
  commit SHA behind their current release tag (v22, v28 respectively),
  with the tag named in a trailing comment for a human to read — the
  same "bump deliberately" policy `libs.versions.toml` already states
  for Gradle dependencies, just applied to Actions refs instead. New
  `.github/workflows/codeql.yml` runs CodeQL's Kotlin/Java analysis
  (free for this repo, which is public) with `build-mode: manual`, not
  `autobuild` — CodeQL's autobuild heuristic expects a committed
  `gradlew` to run, and this repo deliberately has none (see `check.yml`),
  so it drives the same `nix develop --command gradle assembleDebug`
  `check.yml` already uses instead of inventing a second build path.
- Nothing else yet beyond the above. This page grows as real decisions
  get made that `AGENTS.md` doesn't already narrate — a library swapped
  for another, a phase's scope adjusted, something specified that turned
  out not to work as written. See skill
  [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) for when a change is
  the kind that belongs here.
