# Open threads

_Last modified: 2026-09-08_

## Contents

- [Tracked as GitHub issues](#tracked-as-github-issues)
- [Left open right now](#left-open-right-now)
- [Not applicable yet](#not-applicable-yet)

Open questions and known gaps, plus anything tracked as a GitHub issue.
Adapted from nixos-configs' `open-threads.md` — same idea, much shorter,
since this is still a young repo. (`git log` is the accurate commit
count if that ever matters — see [history.md](history.md)'s own note on
not keeping a second copy of it here.)

**Before starting work on any of these, or investigating a symptom that
might be one of them:** `gh issue list --repo NireBryce/exigent-heron
--search "<keywords>" --state all` in addition to reading this page — see
skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md).

## Tracked as GitHub issues

Seven open as of **2026-09-08** (`gh issue list --repo
NireBryce/exigent-heron --state all`). This section said "None yet"
until then — true when written on 2026-09-05, false from 2026-09-07 when
the first ones were filed, and nothing caught it: exactly the rot skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) exists for, on
the page whose whole job is tracking what's tracked. Re-derive this list
from `gh` rather than trusting it; it is a snapshot, not a mirror.

| # | Title | Label |
|---|---|---|
| [#31](https://github.com/NireBryce/exigent-heron/issues/31) | Pin third-party GitHub Actions to a commit SHA instead of `@main` | enhancement |
| [#30](https://github.com/NireBryce/exigent-heron/issues/30) | Add CodeQL SAST scanning to CI | enhancement |
| [#29](https://github.com/NireBryce/exigent-heron/issues/29) | Run outstanding on-device acceptance criteria on a physical device | enhancement |
| [#28](https://github.com/NireBryce/exigent-heron/issues/28) | Check that long notifications are not cut off before/during TTS | — |
| [#27](https://github.com/NireBryce/exigent-heron/issues/27) | Check whether Android keeps `NotificationTtsListener` alive | — |
| [#24](https://github.com/NireBryce/exigent-heron/issues/24) | fwcd.kotlin (KLS) false "incompatible version of Kotlin" errors | documentation |
| [#9](https://github.com/NireBryce/exigent-heron/issues/9) | Separate library from app so other applications can use it | — |

#29 and #27 are the tracked forms of the on-device and OEM-kill threads
below; #28 is adjacent to, but not the same as, `AGENTS.md` §4.7's
utterance *time* cap (`Settings.truncationLengthSeconds`) — that one is
built; #28 is about *character*-length limits in `TextToSpeech`.

## Left open right now

- **`app_name` (`res/values/strings.xml`) stays `"exigent-heron"`** —
  resolved 2026-09-05, while planning Phase 3 (the UI phase that ships it
  as the visible launcher label): confirmed with the user, deliberate,
  not an oversight. Listed here as a closed thread, not an open one — see
  [history.md](history.md).
- **The android.util.Log CI grep `AGENTS.md` §4.6 asks for** ("Add a lint
  check or a simple CI grep asserting `android.util.Log` appears in
  exactly one file") was missing from `.github/workflows/check.yml` until
  **2026-09-05**, when it was added while building this wiki. Listed here
  as a closed thread, not an open one — kept as the record of when it
  actually landed, since `AGENTS.md` itself doesn't track that.
- **`.github/workflows/check.yml` gained `lintDebug`/`lintRelease`**
  (**2026-09-06**, post-Phase-5): Android Lint is built into AGP, already
  in use, so running it cost no new dependency — it just wasn't wired
  into anything before. Findings upload to the repo's code-scanning tab
  via `github/codeql-action/upload-sarif` (needs `security-events: write`
  added to the job's `permissions:`, since this repo's default
  `GITHUB_TOKEN` doesn't carry it). `lintRelease` runs in CI despite
  `assembleRelease` not being able to (no signing config there, see this
  file's other entries) — lint only analyzes, never packages or signs,
  so it needs nothing `assembleRelease` needs. The six findings present
  when this landed (four dependency-version-bump suggestions already
  handled by `update-flake-lock`'s review process, `ObsoleteSdkInt` on
  `mipmap-anydpi-v26`, `MonochromeLauncherIcon`) were left as-is — cosmetic,
  not worth a round-trip on their own; CodeQL's own SAST scanning and
  pinning the `@main`-referenced third-party actions to a commit SHA were
  considered and left for the user to decide on separately, not added
  here.
- **`kotlinx-coroutines-core` is imported directly in `RuleEngine.kt`**
  — resolved 2026-09-07: `gradle/libs.versions.toml` now declares
  `kotlinx-coroutines-core` explicitly (sharing a `kotlinxCoroutines`
  version ref with `-test`, since the two need to stay in lockstep) and
  `app/build.gradle.kts` has an `implementation(libs.kotlinx.coroutines.core)`
  line. `domain/`'s only non-Kotlin-stdlib import no longer rests on a
  transitive graph nothing in this repo's build files actually names.
  Listed here as a closed thread.
- **`RuleEngine`'s backreference-regex gap** — resolved 2026-09-05, Phase
  3: `RuleValidator` now rejects backreferences outright at rule-save
  time (and defensively in `RuleEngine.compileOrNull`), and matching runs
  inside `InterruptibleCharSequence` + `runInterruptible` so a
  non-backreference timeout now actually stops the thread instead of
  leaking it. Listed here as a closed thread — see
  [history.md](history.md) for the reasoning (including the RE2J
  alternative considered and rejected) and `RuleEngine.kt`'s own doc
  comment.
- **`SafeLog.decision`'s `ruleId` parameter is always `null` in practice**
  — resolved 2026-09-07: `Decision` (`domain/Decision.kt`) now carries a
  common `ruleId: String?`, populated by `RuleEngine.toDecision`/the
  match-timeout `Suppress` and threaded through `SecretDetector`'s
  downgrades so a downgraded decision keeps the id of the rule that
  originally matched. `NotificationTtsListener.route()` passes
  `decision.ruleId` instead of a hardcoded `null`. `RuleEngineTest` gained
  two cases covering the matched/unmatched sides of it. Listed here as a
  closed thread.
- **`AGENTS.md` §4.5's user-editable OTP keyword list** — noticed
  2026-09-08 while rewriting `AGENTS.md`, resolved the same day: `Settings` now carries `otpKeywords: Set<String>? = null`,
  backed by `OTP_KEYWORDS_KEY` in `SettingsRepository` (with a setter).
  `SecretDetectorHolder` mirrors `RuleEngineHolder` and rebuilds a
  `SecretDetector` on every `settings.otpKeywords` emission (null = use
  defaults). Wired in `AppContainer` and called from `NotificationTtsListener`.
  `SettingsScreen` shows the keyword list, allows add/remove, and a "reset
  to defaults" button; empty list shows a warning (keyword detection off,
  floor still holds). `SecretDetectorTest` gained cases for empty list and
  hardcoded-floor-with-empty-list; `SecretDetectorHolderTest` verifies
  rebuild-on-emission. Listed here as a closed thread.
- **Phase 2's on-device acceptance criteria are unconfirmed** (see
  [status.md](status.md)): no device was available the session that
  built the listener/speech stack. Everything JVM-testable is tested and
  green; "a notification is spoken once", "three duplicates in 10s still
  speak once", and "music ducks and recovers" are still only true by
  code review, not by having actually been run. [testing.md](testing.md)
  has the steps — worth running before treating Phase 2 as more than
  provisionally done.
- **Phase 3's on-device acceptance criteria are unconfirmed** (see
  [status.md](status.md)), same reason: no device available this session.
  "A rule added via the UI survives force-stop" and "an invalid pattern
  errors at save time rather than crashing later" are true by code review
  (the JVM tests cover `RuleValidator`/`RuleCodec`/`RuleEngineHolder`
  directly) but not yet by having actually run the app.
  [testing.md](testing.md) has the steps.

- **Phase 4's on-device acceptance criteria**: "headset-only blocks
  speech with no headset connected" was verified 2026-09-07 on the `nix
  develop` emulator (see [status.md](status.md)) — still not a physical
  device. "Engine picker switching takes effect" remains true by code
  review only, not yet exercised. "Ten notifications in five seconds
  collapse to one summary" is covered directly by a passing JVM unit
  test (`SpeechQueueTest`), which is as much of this criterion as
  doesn't need a device. [testing.md](testing.md) has the on-device
  steps for what's left.

- **Phase 5's on-device matrix**: locked-device and in-call were
  verified 2026-09-07 on the emulator against real
  `KeyguardManager`/`AudioManager` state (see [status.md](status.md));
  work-profile was checked too, but only against a non-DPC secondary
  profile, not a fully provisioned enterprise one — `SECURITY.md` §4's
  own reasoning for why there's no more specific feature to test there
  still applies. **Still not a physical device** for any of the three —
  OEM battery-killing, a real cellular radio, and real MDM enrollment
  remain untested. `SECURITY.md` §4 has the exact repro steps and every
  caveat on what an emulator can and can't prove here.

## Not applicable yet

`AGENTS.md` §8's "if OEM background-killing turns out to break it" clause
— `NotificationTtsListener` exists as of Phase 2, but nothing to
investigate until it's actually *running*, unattended, on a real device
long enough to observe whether an OEM kills it. Not forgotten, just not
yet reachable — see the on-device thread above.
