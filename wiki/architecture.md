# Architecture

_Last modified: 2026-09-08_

## Contents

- [Module and package layout](#module-and-package-layout)
- [The critical structural rule](#the-critical-structural-rule)
- [Data flow](#data-flow)
- [What each package holds](#what-each-package-holds)
- [Where this diverged from the spec's original tree](#where-this-diverged-from-the-specs-original-tree)

**This page is the tree.** As of **2026-09-08** it holds the canonical
package layout and data-flow diagram for this app;
[`AGENTS.md`](../AGENTS.md) §3 carries a six-bullet summary of the same
split and points here for the rest.

That's a deliberate swap of the two documents' previous roles, and it is
the second exception to this wiki's "index over restatement" rule
([styleguide.md](styleguide.md)) after [testing.md](testing.md). The
reason: a *target* tree and a *real* tree are the same shape, so keeping
them in two files meant every new file had to be reconciled against a
spec tree that was written before the app existed and could never be
right about a file the spec hadn't anticipated. Five such files already
existed, plus a package (`ui/permission/`) the spec named and the code
never grew — all recorded below. One tree, kept honest against
`app/src`, is the fix. What stays in `AGENTS.md` is the part a tree
can't express: which of these boundaries are *requirements* rather than
observations, which is what its §3 summary and §4's per-component specs
are for.

## Module and package layout

Single Gradle module (`:app`) — multi-module isn't worth the build-file
overhead at this size. Under
`app/src/main/java/net/breadthcharge/exigentheron/`:

```
net.breadthcharge.exigentheron/
├── App.kt                            # Application subclass; owns AppContainer
├── AppContainer.kt                   # manual DI: constructs and holds singletons
├── SafeLog.kt                        # the ONLY file permitted to touch android.util.Log
│
├── listener/
│   ├── NotificationTtsListener.kt      # NotificationListenerService — THIN, routing only
│   ├── NotificationExtractor.kt        # StatusBarNotification -> NotificationPayload
│   └── NotificationExtractionPolicy.kt # pure drop conditions; see "Where this diverged"
│
├── domain/                           # PURE. zero Android imports. See the rule below.
│   ├── NotificationPayload.kt        # toString() emits key + package only, never content
│   ├── SpeechRequest.kt
│   ├── Rule.kt                       # @Serializable, with RuleAction
│   ├── Decision.kt                   # sealed: Speak | AnnounceOnly | Suppress, each with ruleId
│   ├── RuleEngine.kt                 # default-deny; first match by priority wins
│   ├── RuleEngineHolder.kt           # rebuilds a RuleEngine from Flow<List<Rule>>
│   ├── RuleValidator.kt              # is this pattern acceptable at all
│   ├── RuleFormValidator.kt          # the rest of the editor's save-time gate
│   ├── RuleCodec.kt                  # JSON encode/decode/list-editing
│   ├── SecretDetector.kt             # can only ever downgrade a Decision
│   ├── SecretDetectorHolder.kt       # rebuilds a SecretDetector from Flow<List<String>> (OTP keywords)
│   ├── Deduplicator.kt               # injected clock; LRU + TTL
│   ├── ContentHash.kt                # see "Where this diverged"
│   ├── TextSanitizer.kt              # see "Where this diverged"
│   └── InterruptibleCharSequence.kt  # see "Where this diverged"
│
├── speech/
│   ├── SpeechQueue.kt                # single-consumer actor over a bounded Channel
│   ├── TtsEngine.kt                  # interface — what makes SpeechQueue testable
│   ├── AndroidTtsEngine.kt           # real impl over android.speech.tts.TextToSpeech
│   ├── AudioFocusManager.kt
│   ├── AudioBecomingNoisyReceiver.kt # a route change *during* an utterance
│   ├── OutputRouteGate.kt            # headset-only enforcement
│   └── LockStateGate.kt              # don't-speak-while-locked
│
├── data/
│   ├── SettingsRepository.kt         # DataStore-backed, exposes Flow<Settings>
│   ├── RuleRepository.kt             # one JSON blob; thin wrapper over domain/RuleCodec
│   └── BluetoothDevices.kt           # bonded-device list; BLUETOOTH_CONNECT-gated
│
└── ui/
    ├── MainActivity.kt               # hosts the notification-access grant flow
    ├── rules/                        # RuleListScreen, RuleEditorScreen, InstalledApps
    └── settings/                     # SettingsScreen
```

Plus two source sets outside `main/`:

- `app/src/debug/java/net/breadthcharge/exigentheron/debug/FakeNotifications.kt`
  — the Phase 1 fake-notification injector (see [status.md](status.md)).
  `debug/` source set only, per `AGENTS.md` §5; see
  [testing.md](testing.md) for how to invoke it. Shares
  `domain/ContentHash.kt`'s hashing rather than keeping its own copy —
  that duplication was the plan from the start, see its own comment.
- `app/src/test/java/net/breadthcharge/exigentheron/` — one test class per
  testable class above, mirroring the same package structure.
  `listener/NotificationExtractionPolicyTest.kt` and
  `speech/SpeechQueueTest.kt` are the first tests here to exercise
  Android-facing (if Android-import-light or -free) code rather than pure
  `domain/` — see [traps-and-skills.md](traps-and-skills.md) for two real
  problems that surfaced specifically because of that. 105 tests as of
  **2026-09-07**; [status.md](status.md) has the current pass count
  rather than a second copy of the number here.

## The critical structural rule

**`domain/` has zero Android imports.** `RuleEngine`, `SecretDetector`,
and `Deduplicator` are pure Kotlin, unit-testable on the JVM with no
Robolectric, no instrumentation, no emulator. This is what makes the
project testable at all — everything else is Android framework glue that
is a pain to test and should therefore contain no logic worth testing.

This is a *requirement*, not an observation about how the code happens to
be arranged, which is why `AGENTS.md` §3 states it too rather than only
linking here. Its practical consequence shows up repeatedly below: where
logic was specified inside an Android class but didn't actually need
Android, it got split out anyway.

`domain/`'s one non-stdlib dependency is `kotlinx-coroutines-core`, used
by `RuleEngine` for `runInterruptible`/`withTimeoutOrNull`. That was
resting on a transitive graph nothing in the build files named until
**2026-09-07** — see [open-threads.md](open-threads.md).

## Data flow

One path, implemented literally in `NotificationTtsListener.route()`:

```
onNotificationPosted(sbn)
  → NotificationExtractor.extract(sbn)      → NotificationPayload?
  → Deduplicator.isDuplicate(payload)       → drop if true
  → RuleEngine.evaluate(payload, rules)     → Decision
  → SecretDetector.scan(decision)           → possibly downgrade to AnnounceOnly/Suppress
  → OutputRouteGate.allows()                → drop if false
  → LockStateGate.allows()                  → drop if false
  → SpeechQueue.enqueue(SpeechRequest)
  → AudioFocusManager.request() → TtsEngine.speak() → abandon focus when queue drains
```

The listener service does routing only — each step is exactly one call,
no branching logic of its own — and hops off the binder thread before
`route()`, since rule matching can spend its full timeout budget and
`onNotificationPosted` has to return promptly.

**The ordering is a requirement, not an implementation detail**, and
`AGENTS.md` §3 keeps a one-line version of it for that reason. Dedup runs
before the rule engine so a repost costs no regex work; `SecretDetector`
runs after it and can only ever *downgrade* what the rules decided, never
upgrade it (§4.5); the output and lock gates run last, immediately before
enqueue.

`OutputRouteGate` is checked twice — here at enqueue time, and again
inside `SpeechQueue` before every utterance, because a headset connected
when a notification is posted can disconnect before a busy queue reaches
it. `AudioBecomingNoisyReceiver` covers the third case neither check can
see: a route change *during* an utterance. See `AGENTS.md` §4.7 and
[status.md](status.md) for the session that found both gaps.

## What each package holds

The per-file detail below is a map, not a substitute for the doc comments
— several of these classes document their own reasoning far better than a
summary can, and those are the copies that stay correct.

- `App.kt` / `AppContainer.kt` — `AppContainer` is the manual-DI container
  `AGENTS.md` §2 specifies instead of Hilt. Wires `Deduplicator`,
  `RuleRepository`, `SettingsRepository`, `RuleEngineHolder` (rebuilds a
  `RuleEngine` from `ruleRepository.rules` on every emission — Phase 2's
  `phase2HardcodedRules` is gone, see [history.md](history.md)),
  `SecretDetector`, `AudioFocusManager`, `OutputRouteGate`, `LockStateGate`.
  `ttsEngine`/`speechQueue` are `var`s (not `val`) as of Phase 4:
  `rebuildTtsEngine(enginePackage)` swaps in a fresh `AndroidTtsEngine` +
  `SpeechQueue` pair so a settings-screen engine choice actually takes
  effect (`AGENTS.md` §4.8) instead of being fixed for the container's
  lifetime — see [history.md](history.md).
- `SafeLog.kt` — the sole permitted entry point to `android.util.Log`
  (`AGENTS.md` §4.6). Logcat tag is `"ExigentHeron"`, not the class name —
  worth knowing before scoping an `adb logcat` (see
  [testing.md](testing.md) and skill
  [`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md)).
  Exposes exactly `decision(pkg, ruleId, action)`, `lifecycle(msg)`,
  `error(msg, t?)` — no arbitrary-string overload, by design.
- `domain/` — pure Kotlin, no Android imports, per the rule above. Phase 1
  landed `NotificationPayload.kt`, `Rule.kt` (with `RuleAction`),
  `Decision.kt`, `Deduplicator.kt`, `RuleEngine.kt`, `SecretDetector.kt`,
  `SpeechRequest.kt`. Phase 3 added four more, all still
  Android-import-free: `RuleValidator.kt` (single source of truth for "is
  this pattern acceptable" — rejects backreferences outright, used by both
  the rule editor and `RuleEngine.compileOrNull`),
  `InterruptibleCharSequence.kt` (makes `runInterruptible`'s
  `Thread.interrupt()` actually abort a runaway match — see
  [history.md](history.md)), `RuleCodec.kt` (pure JSON
  encode/decode/list-editing that `data/RuleRepository.kt` wraps), and
  `RuleEngineHolder.kt` (rebuilds a live `RuleEngine` from a
  `Flow<List<Rule>>` so a rule edit takes effect without an app restart),
  and as of **2026-09-08** `SecretDetectorHolder.kt` (rebuilds a live
  `SecretDetector` from a `Flow<List<String>>` of OTP keywords, so a
  keyword edit in settings takes effect without an app restart).
  Post-Phase-5, `RuleFormValidator.kt` joined them, pulling
  `RuleEditorViewModel.save()`'s remaining form-only checks (empty app
  selection, non-numeric priority) out into the same pure/testable shape,
  so the whole save-time gate is unit-tested rather than only its regex
  half. `RuleEngine.kt`'s doc comment is worth reading directly rather
  than summarized here — it documents a real, verified limitation of its
  own regex-timeout mitigation, and how Phase 3 closed most of it (see
  [history.md](history.md)).
- `listener/` — `NotificationTtsListener.kt` (routing only; as of Phase 4,
  `route()` also checks `outputRouteGate.allows()` and
  `lockStateGate.allows()` right before `SpeechQueue.enqueue()`, logging a
  `SafeLog.decision(..., action="suppress")` on either gate's no — the
  same convention `Decision.Suppress` already used),
  `NotificationExtractor.kt` (the Android-facing half of `AGENTS.md`
  §4.2's extraction), and `NotificationExtractionPolicy.kt` (the pure
  half — see "Where this diverged" below).
- `data/` — `RuleRepository.kt` (Preferences DataStore, one JSON blob
  under `stringPreferencesKey("rules_json")`, thin wrapper over
  `domain/RuleCodec.kt`), `SettingsRepository.kt` (Phase 3 scaffold,
  given real fields Phase 4: `Settings(headsetOnly, respectLockState,
  allowDndOverride, ttsEnginePackage)`, one `booleanPreferencesKey`/
  `stringPreferencesKey` each, exposed as `Flow<Settings>` plus per-field
  setters — no round-trip JVM test, same reasoning as `RuleRepository`'s
  lack of one: it's a thin DataStore wrapper with no logic of its own;
  gained three more fields **2026-09-07** — `bluetoothDeviceControlEnabled`
  plus two `stringSetPreferencesKey` address sets, see `AGENTS.md` §4.9;
  gained a fourth, `truncationLengthSeconds: Int?`, the same day — null
  by default (no limit), an `intPreferencesKey` when set; as of **2026-09-08**
  gained `otpKeywords: Set<String>?` backed by `stringSetPreferencesKey`,
  null = use `SecretDetector.DEFAULT_OTP_KEYWORDS`, see `AGENTS.md` §4.5),
  and `BluetoothDevices.kt` (`loadBondedBluetoothDevices` — reads
  `BluetoothAdapter.getBondedDevices()`, `BLUETOOTH_CONNECT`-gated,
  returns an empty list rather than throwing when it isn't granted).
  `AGENTS.md` §4.9 requires Allow and Deny to be mutually exclusive per
  device *by construction* rather than by UI discipline; as built that
  lives in `SettingsRepository.setBluetoothDeviceDecision`, which always
  clears the other address set before writing — so there is no reachable
  state, from the UI or otherwise, where one address sits in both.
- `speech/` — `TtsEngine.kt` (interface), `AndroidTtsEngine.kt` (real
  impl; as of Phase 4 takes an optional `enginePackage` and uses it with
  `TextToSpeech(context, listener, engineName)`, checks
  `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED` after a successful init rather
  than treating init-success alone as ready, and exposes `listEngines()`
  for the settings picker), `AudioFocusManager.kt`, `SpeechQueue.kt`
  (Phase 4: takes an `isBlockedByDnd` function reference alongside
  `isInCall`, and its consumer now drains whatever else is already
  buffered into a batch before deciding whether to speak it item-by-item
  or collapse it to one "`<n>` new notifications." summary — `AGENTS.md`
  §4.7's queue-collapse-on-burst; **2026-09-07**: when
  `Settings.truncationLengthSeconds` is set, races `TtsEngine.speak()`
  against a timeout of that many seconds and calls `TtsEngine.stop()`
  explicitly on timeout — cancelling the coroutine alone wouldn't stop
  the real `TextToSpeech` engine mid-utterance, only the wait on its
  completion callback), `OutputRouteGate.kt` (Phase 4 — headset-only
  enforcement against `AudioManager.getDevices`), `LockStateGate.kt`
  (Phase 4 — the separate don't-speak-while-locked toggle against
  `KeyguardManager.isKeyguardLocked()`), and
  `AudioBecomingNoisyReceiver.kt` (**2026-09-07** — `AGENTS.md` §4.7's
  `ACTION_AUDIO_BECOMING_NOISY` handling, see "Data flow" above). Both
  gates take their Android-facing checks as function references, the same
  pattern `SpeechQueue`'s own constructor already used for `isInCall` —
  see each file's own doc comment.
- `ui/` — `MainActivity.kt` carries the enable-access button (`AGENTS.md`
  §4.10) and the active TTS engine (§4.8's "the user should never have to
  wonder," Phase 4), plus a manual `Screen` sealed interface (`Main` /
  `RuleList` / `RuleEditor` / `Settings`, the last added Phase 4) switched
  in a `when` — no navigation-compose dependency, since one isn't in
  `AGENTS.md` §2's list and four screens don't need one.
  `ui/settings/SettingsScreen.kt` (Phase 4) has the headset-only,
  don't-speak-while-locked, and speak-during-DND toggles, plus the engine
  picker (`AndroidTtsEngine.listEngines()`) and a live
  ready/initializing/error status line wired to
  `AppContainer.ttsEngineStatus`. `ui/rules/` (Phase 3) has
  `RuleListScreen.kt` (list + enable toggle + delete, backed by
  `RuleListViewModel`), `RuleEditorScreen.kt` (form + save-time
  validation, backed by `RuleEditorViewModel`; the app picker is a
  `Dialog` launched from inside this screen, not a separate nav
  destination), and `InstalledApps.kt`
  (`PackageManager.queryIntentActivities` against the `<queries>` block
  added to `AndroidManifest.xml` this phase — see below). ViewModels are
  constructed via `androidx.lifecycle.viewmodel.viewModelFactory` (already
  part of the existing `lifecycle-viewmodel-compose` dependency), not
  Hilt.

## Where this diverged from the spec's original tree

Kept as history, not as a live reconciliation: before **2026-09-08**,
`AGENTS.md` §3 carried a target tree and this page tracked how far the
real one had caught up to it. Now this page *is* the tree. These entries
stay because the reasoning behind each still matters — and because the
last one is a genuine spec/code disagreement, which `AGENTS.md` §0 asks
be said out loud rather than built around silently.

`AGENTS.md` §3's original tree used `com.<yourdomain>.notifreader` as a
placeholder package name; the real one is `net.breadthcharge.exigentheron`
(see `app/build.gradle.kts`'s `namespace` and `applicationId`) — never a
deviation, just the placeholder resolved.

**2026-09-05, three files the spec tree didn't have**, all in service of
the same goal the spec itself states — testable logic living outside
Android framework glue:

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

A fourth, `domain/InterruptibleCharSequence.kt`, followed in Phase 3 for
the same reason; `domain/RuleFormValidator.kt` post-Phase-5. Neither was
in the spec tree either, and neither needs its own entry beyond the
description above — the pattern is the same one all three of these
established.

**2026-09-05, one manifest addition not in `AGENTS.md` §5's snippet**:
`AndroidManifest.xml` gained a `<queries>` block (`ACTION_MAIN` /
`CATEGORY_LAUNCHER`) for the Phase 3 installed-app picker's
`PackageManager.queryIntentActivities` call. Not a deviation from §5's
hardening intent — it's a visibility declaration, not a permission grant,
doesn't touch `INTERNET` or `QUERY_ALL_PACKAGES`, and §5's snippet predates
Phase 3 needing to query other apps at all — but worth recording since §5
itself doesn't mention it.

**2026-09-07, a real permission addition, not just a visibility
declaration this time**: `AndroidManifest.xml` gained
`<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />`
for the per-device Bluetooth allow/deny feature (`AGENTS.md` §4.9). This
is this app's first and only runtime permission — everything else here
has been genuinely zero-permission-request until now. Requested only
when the user turns the feature on in Settings, not at launch; see
`SettingsScreen.kt`'s permission-launcher wiring and
`Settings.bluetoothDeviceControlEnabled`'s own doc comment for why it
defaults off.

**A package the spec named and the code never grew**: §3's original tree
had `ui/permission/` for the notification-access grant flow. It's in
`ui/MainActivity.kt` instead — the flow is one button and one
`NotificationManagerCompat.getEnabledListenerPackages` check, which
didn't earn a package of its own. Recorded here rather than silently
dropped when this page absorbed the tree; if a second permission flow
ever lands (`BLUETOOTH_CONNECT`'s launcher already lives in
`SettingsScreen.kt`, a second such site), the spec's original instinct
gets better, not worse.

If a real divergence happens later that *isn't* in this same spirit — a
class that ends up somewhere other than its natural package for no
principled reason, a component nothing anticipated — record it here with
a date and why, per skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md), and say in the
same change whether `AGENTS.md` itself should be corrected instead of the
code, per its own §0 instinct to flag disagreement rather than build
around it silently.
