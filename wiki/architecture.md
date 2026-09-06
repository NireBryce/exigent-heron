# Architecture

## Contents

- [What exists right now](#what-exists-right-now)
- [Target tree](#target-tree)
- [Deviations from AGENTS.md §3](#deviations-from-agentsmd-3)

`AGENTS.md` §3 already specifies the target package layout in full — this
page doesn't restate it, only tracks how far the real tree has caught up
and notes anywhere the two have actually diverged. Same relationship
nixos-configs' `architecture.md` has to its own module-tree docs, just
against a spec file instead of the tree itself being the source of truth.

## What exists right now

As of **2026-09-05** (Phase 3 complete except on-device verification, see
[status.md](status.md)), under `app/src/main/java/net/breadthcharge/exigentheron/`:

- `App.kt` — `Application` subclass.
- `AppContainer.kt` — the manual-DI container `AGENTS.md` §2 specifies
  instead of Hilt. Wires `Deduplicator`, `RuleRepository`,
  `SettingsRepository`, `RuleEngineHolder` (rebuilds a `RuleEngine` from
  `ruleRepository.rules` on every emission — Phase 2's
  `phase2HardcodedRules` is gone, see [history.md](history.md)),
  `SecretDetector`, `AudioFocusManager`, `AndroidTtsEngine`, `SpeechQueue`.
- `SafeLog.kt` — the sole permitted entry point to `android.util.Log`
  (`AGENTS.md` §4.6). Logcat tag is `"ExigentHeron"`, not the class name —
  worth knowing before scoping an `adb logcat` (see
  [testing.md](testing.md) and skill
  [`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md)).
  Exposes exactly `decision(pkg, ruleId, action)`, `lifecycle(msg)`,
  `error(msg, t?)` — no arbitrary-string overload, by design.
- `ui/MainActivity.kt` — enable-access button (`AGENTS.md` §4.10) plus,
  as of Phase 3, a manual `Screen` sealed interface (`Main` / `RuleList` /
  `RuleEditor`) switched in a `when` — no navigation-compose dependency,
  since one isn't in `AGENTS.md` §2's list and three screens don't need
  one.
- `ui/rules/` (new, Phase 3) — `RuleListScreen.kt` (list + enable toggle +
  delete, backed by `RuleListViewModel`), `RuleEditorScreen.kt` (form +
  save-time validation via `RuleValidator`, backed by
  `RuleEditorViewModel`; the app picker is a `Dialog` launched from inside
  this screen, not a separate nav destination), `InstalledApps.kt`
  (`PackageManager.queryIntentActivities` against the `<queries>` block
  added to `AndroidManifest.xml` this phase — see "Deviations" below).
  ViewModels are constructed via `androidx.lifecycle.viewmodel.viewModelFactory`
  (already part of the existing `lifecycle-viewmodel-compose` dependency),
  not Hilt.
- `domain/` — pure Kotlin, no Android imports, per `AGENTS.md` §3:
  `NotificationPayload.kt`, `Rule.kt` (with `RuleAction`), `Decision.kt`,
  `Deduplicator.kt`, `RuleEngine.kt`, `SecretDetector.kt`, `SpeechRequest.kt`.
  Phase 3 added four more, all still Android-import-free: `RuleValidator.kt`
  (single source of truth for "is this pattern acceptable" — rejects
  backreferences outright, used by both the rule editor and
  `RuleEngine.compileOrNull`), `InterruptibleCharSequence.kt` (makes
  `runInterruptible`'s `Thread.interrupt()` actually abort a runaway
  match — see [history.md](history.md)), `RuleCodec.kt` (pure JSON
  encode/decode/list-editing that `data/RuleRepository.kt` wraps),
  `RuleEngineHolder.kt` (rebuilds a live `RuleEngine` from a
  `Flow<List<Rule>>` so a rule edit takes effect without an app restart).
  Plus two files from Phase 1/2 not in §3's tree — see "Deviations" below:
  `ContentHash.kt` and `TextSanitizer.kt`. `RuleEngine.kt`'s doc comment
  is worth reading directly rather than summarized here — it documents a
  real, verified limitation of its own regex-timeout mitigation, and how
  Phase 3 closed most of it (see [history.md](history.md)).
- `listener/` — `NotificationTtsListener.kt` (routing only, per §3),
  `NotificationExtractor.kt` (the Android-facing half of §4.2's
  extraction), and `NotificationExtractionPolicy.kt` — a third file not
  in §3's tree, again see "Deviations": the pure, Android-import-free
  half of §4.2's drop conditions, split out specifically so it's
  JVM-testable without a device.
- `data/` (new, Phase 3) — `RuleRepository.kt` (Preferences DataStore, one
  JSON blob under `stringPreferencesKey("rules_json")`, thin wrapper over
  `domain/RuleCodec.kt`) and `SettingsRepository.kt` (scaffold only — the
  DataStore file exists and is held, no fields yet; Phase 4 gives it a
  shape, see [history.md](history.md)).
- `speech/` — `TtsEngine.kt` (interface), `AndroidTtsEngine.kt` (real
  impl, system default engine for now — see its own doc comment on why
  engine selection waits for Phase 4), `AudioFocusManager.kt`,
  `SpeechQueue.kt`. `OutputRouteGate.kt` doesn't exist yet — Phase 4.

Under `app/src/debug/java/net/breadthcharge/exigentheron/`:

- `debug/FakeNotifications.kt` — the Phase 1 fake-notification injector
  (`BUILD_PLAN.md`). `debug/` source set only, per spec; see
  [testing.md](testing.md) for how to invoke it. Now shares
  `domain/ContentHash.kt`'s hashing rather than keeping its own copy —
  that duplication was the plan from the start, see its own comment.

Under `app/src/test/java/net/breadthcharge/exigentheron/`: one test class
per testable class above, 73 tests total — see [status.md](status.md) for
the current pass count. `listener/NotificationExtractionPolicyTest.kt`
and `speech/SpeechQueueTest.kt` are the first tests in this repo to
exercise Android-facing (if Android-import-light or -free) code rather
than pure `domain/` — see [traps-and-skills.md](traps-and-skills.md) for
two real problems that surfaced specifically because of that.

## Target tree

See `AGENTS.md` §3 for the full tree and the data-flow diagram
(`onNotificationPosted` → extractor → deduplicator → rule engine → secret
detector → output gate → speech queue). Not reproduced here — that's the
one copy, and this page is a link to it, not a second one that can drift
from it separately.

## Deviations from AGENTS.md §3

`AGENTS.md` §3's tree uses `com.<yourdomain>.notifreader` as a
placeholder package name; the real one is `net.breadthcharge.exigentheron`
(see `app/build.gradle.kts`'s `namespace` and `applicationId`) — not a
deviation, just the placeholder resolved.

**2026-09-05, three files not in §3's tree**, all real deviations, all
in service of the same goal §3 itself states — testable logic living
outside Android framework glue:

- `domain/ContentHash.kt` — §4.1 says `NotificationPayload.contentHash`
  is "a stable hash of title+body" but never says where it's computed.
  Putting it in `domain/` (pure JVM, `java.security` not Android) is what
  let `FakeNotifications` (debug/) and `NotificationExtractor` (main,
  real notifications) share one implementation instead of two that could
  drift apart — see [history.md](history.md).
- `domain/TextSanitizer.kt` — §4.2's control/zero-width/bidi stripping is
  specified under `NotificationExtractor`, but the stripping itself has
  no Android dependency, so it lives in `domain/` and gets a real JVM
  test the same way the rest of `domain/` does.
- `listener/NotificationExtractionPolicy.kt` — §4.2's drop conditions
  (ongoing, group summary, own package, empty title+body), as a pure
  function over plain values rather than a `StatusBarNotification`.
  Deliberately *not* moved into `domain/` — it's extraction policy, not
  rule/secret/dedup business logic — but it has zero Android imports for
  the same testability reason as the two above.

**2026-09-05, one manifest addition not in `AGENTS.md` §5's snippet**:
`AndroidManifest.xml` gained a `<queries>` block (`ACTION_MAIN` /
`CATEGORY_LAUNCHER`) for the Phase 3 installed-app picker's
`PackageManager.queryIntentActivities` call. Not a deviation from §5's
hardening intent — it's a visibility declaration, not a permission grant,
doesn't touch `INTERNET` or `QUERY_ALL_PACKAGES`, and §5's snippet predates
Phase 3 needing to query other apps at all — but worth recording since §5
itself doesn't mention it.

If a real divergence from the spec tree happens later that *isn't* in
this same spirit (a class that ends up somewhere other than its
specified package for no principled reason, a component the spec didn't
anticipate), record it here with a date and why, per skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — and say in the
same change whether `AGENTS.md` itself should be corrected instead of the
code, per its own §0 instinct to flag disagreement rather than build
around it silently.
