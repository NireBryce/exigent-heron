# Open threads

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

None yet — `NireBryce/exigent-heron` has issues enabled with a real label
set (checked 2026-09-05, see skill
[`propose-issue`](../.claude/skills/propose-issue/SKILL.md)) but nothing
filed. Nothing below has been promoted to an issue; it's all still at the
"noticed, not yet worth a round-trip" stage per that skill's Calibrate
section.

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
- **`kotlinx-coroutines-core` is imported directly in `RuleEngine.kt`**
  (`Dispatchers`, `withContext`, `withTimeoutOrNull` — needed for
  `AGENTS.md` §4.4's timeout requirement) but isn't in §2's explicit
  dependency list, which names `kotlinx-coroutines-test` as test-only. It
  compiles and runs today because `androidx.lifecycle:lifecycle-runtime-ktx`
  and Compose runtime both already pull it in transitively — not a new
  dependency added, just an existing one used directly from `domain/`
  code. Fine as long as that transitive graph holds; worth an explicit
  `implementation(libs.kotlinx.coroutines.core)` line if that ever feels
  fragile, rather than leaving `domain/`'s only non-Kotlin-stdlib import
  resting on something no build file actually declares.
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
  (2026-09-05): `NotificationTtsListener.route()` calls
  `SafeLog.decision(pkg, ruleId = null, action = ...)` because
  `Decision.Speak`/`AnnounceOnly` don't carry the id of the `Rule` that
  produced them — only `Decision.Suppress.reason` embeds "rule `<id>`"
  as unstructured text, which isn't something to parse back out (that's
  exactly the kind of string-parsing-for-structured-data `SafeLog`'s own
  design avoids elsewhere). Cheap to fix by giving `Decision` an optional
  `ruleId: String?` — not done yet since it's cosmetic (logging
  completeness, not a spec requirement) and would touch Phase 1's
  already-tested `Decision`/`RuleEngine`/`SecretDetector` mid–Phase 2.
  Worth doing whenever a UI phase wants to show which rule fired for a
  given decision, if not before.
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

- **Phase 4's on-device acceptance criteria are unconfirmed** (see
  [status.md](status.md)), same reason: no device available this session.
  "Headset-only blocks speech with no headset connected" and "engine
  picker switching takes effect" are true by code review only; "ten
  notifications in five seconds collapse to one summary" is covered
  directly by a passing JVM unit test (`SpeechQueueTest`), which is as
  much of this criterion as doesn't need a device.
  [testing.md](testing.md) has the on-device steps for all three.

## Not applicable yet

`AGENTS.md` §8's "if OEM background-killing turns out to break it" clause
— `NotificationTtsListener` exists as of Phase 2, but nothing to
investigate until it's actually *running*, unattended, on a real device
long enough to observe whether an OEM kills it. Not forgotten, just not
yet reachable — see the on-device thread above.
