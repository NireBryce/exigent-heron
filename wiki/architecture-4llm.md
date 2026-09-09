# Architecture (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Status of this page](#status-of-this-page)
- [Per-file detail with dates](#per-file-detail-with-dates)
- [Divergences from the spec's original tree](#divergences-from-the-specs-original-tree)
- [Framework constants mirrored in pure code](#framework-constants-mirrored-in-pure-code)
- [Recording a new divergence](#recording-a-new-divergence)

Companion to [architecture.md](architecture.md), which holds the
canonical tree, the data flow, and the purity rule. This page holds the
dated evolution and the recorded deviations.

## Status of this page

Before **2026-09-08**, `AGENTS.md` §3 carried a *target* package tree and
`architecture.md` tracked how far the real tree had caught up to it. That
split cost more than it paid: a target tree and a real tree are the same
shape, so every new file had to be reconciled against a tree written
before the app existed, and the spec's copy could never be right about a
file the spec had not anticipated. By the time of the swap there were
five such files plus a package (`ui/permission/`) the spec named and the
code never grew.

Now [architecture.md](architecture.md) *is* the tree, and §3 keeps a
six-bullet summary plus the two things a tree cannot express and that
genuinely are requirements: `domain/`'s zero-Android-imports rule, and
the pipeline ordering. This is the second standing exception to the
wiki's "index over restatement" rule, after [testing.md](testing.md), and
it was granted **to end a restatement, not to add one**. See
[styleguide-4llm.md](styleguide-4llm.md).

## Per-file detail with dates

**`AppContainer.kt`**
- Wires `Deduplicator`, `RuleRepository`, `SettingsRepository`,
  `RuleEngineHolder`, `SecretDetectorHolder`, `AudioFocusManager`,
  `OutputRouteGate`, `LockStateGate`.
- Phase 2's `phase2HardcodedRules` (targeting
  `com.google.android.apps.messaging`, an arbitrary common default) is
  **gone** — superseded in Phase 3 by real `RuleRepository` persistence
  and the rule editor.
- Holders exposed as `ruleEngine` / `secretDetector` — named for the
  pipeline stage, not the wrapper class, matching §3's pipeline.
  `secretDetector` was `secretDetectorHolder` until **2026-09-08**, the
  one place the two disagreed.
- `ttsEngine`/`speechQueue` became `var`s in Phase 4 with
  `rebuildTtsEngine(enginePackage)`: `AndroidTtsEngine` binds one
  `TextToSpeech` to one engine package for its lifetime and `SpeechQueue`
  holds its engine by constructor reference, so neither can switch in
  place. Rebuilding just those two (shutting the old engine down only
  once the new one is live) is the smallest thing that makes §4.8's
  "takes effect" true.
- First engine choice is read synchronously (`runBlocking` on
  `settingsRepository.settings.first()`) — a local Preferences read, not
  a network call — because `App.onCreate()` has no later point before
  something might need to speak.

**`domain/`, by phase**
- Phase 1: `NotificationPayload`, `Rule` (+`RuleAction`), `Decision`,
  `Deduplicator`, `RuleEngine`, `SecretDetector`, `SpeechRequest`.
- Phase 3: `RuleValidator` (single source of truth for "is this pattern
  acceptable"; rejects backreferences outright; used by both the editor
  and `RuleEngine.compileOrNull`), `InterruptibleCharSequence`,
  `RuleCodec`, `RuleEngineHolder`.
- Post-Phase-5: `RuleFormValidator` — pulled `RuleEditorViewModel.save()`'s
  remaining form-only checks (empty app selection, non-numeric priority)
  into the same pure shape the regex half already had.
- **2026-09-08**: `SecretDetectorHolder` — rebuilds from a
  `Flow<List<String>>` of OTP keywords so a settings edit applies without
  an app restart.
- `Decision` carries a common `ruleId: String?` as of **2026-09-07**,
  populated by `RuleEngine.toDecision` and the match-timeout `Suppress`,
  threaded through `SecretDetector`'s downgrades so a downgraded decision
  keeps the id of the rule that originally matched.
  `NotificationTtsListener.route()` passes `decision.ruleId` rather than
  a hardcoded `null`.
- `domain/`'s one non-stdlib dependency is `kotlinx-coroutines-core`
  (`runInterruptible`/`withTimeoutOrNull`). It rested on an unnamed
  transitive graph until **2026-09-07**, when `libs.versions.toml`
  declared it explicitly, sharing a version ref with `-test`.

**`data/SettingsRepository.kt`** — field history, since it accreted:
- Phase 3: scaffolded empty on purpose. DataStore file wired, no
  placeholder field (that would violate `AGENTS.md` §0's YAGNI rule).
- Phase 4: `headsetOnly`, `respectLockState`, `allowDndOverride`,
  `ttsEnginePackage`.
- **2026-09-07**: `bluetoothDeviceControlEnabled` + two
  `stringSetPreferencesKey` address sets (§4.9); then
  `truncationLengthSeconds: Int?` (null = no limit).
- **2026-09-08**: `otpKeywords: Set<String>?` (null =
  `SecretDetector.DEFAULT_OTP_KEYWORDS`).
- No JVM round-trip test, by the same reasoning as `RuleRepository`: a
  thin DataStore wrapper with no logic of its own. Both got instrumented
  round-trip tests **2026-09-08** instead.
- §4.9 requires Allow/Deny mutual exclusion *by construction*. As built
  that lives in `setBluetoothDeviceDecision`, which always clears the
  other address set before writing.

**`speech/SpeechQueue.kt`** — layered fixes, in order:
- Phase 4: takes `isBlockedByDnd` alongside `isInCall`; the consumer
  drains whatever else is buffered into a batch before deciding to speak
  item-by-item or collapse to one "`<n>` new notifications." summary
  (§4.7's queue-collapse-on-burst).
- **2026-09-07**: when `Settings.truncationLengthSeconds` is set, races
  `TtsEngine.speak()` against that timeout and calls `TtsEngine.stop()`
  explicitly on timeout — cancelling the coroutine alone would only
  abandon the wait on the completion callback, not stop the real
  `TextToSpeech` mid-utterance.
- **2026-09-08**: the three gate lambdas grouped into a `SpeechGates`
  parameter, and the consumer's dispatcher made injectable so a test can
  confine it off the process-wide `Dispatchers.Default` pool.
  `SpeechGates` is **not** the type-safety fix it looks like: the three
  `() -> Boolean` fields still sit adjacent, so transposing two still
  compiles. Named arguments are what prevent that.

**`speech/`, other**
- `AndroidTtsEngine` (Phase 4): takes an optional `enginePackage`, uses
  `TextToSpeech(context, listener, engineName)`, checks
  `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED` after a *successful* init
  rather than treating init-success as ready, exposes `listEngines()`.
- `OutputRouteGate` (Phase 4): headset-only against
  `AudioManager.getDevices`.
- `LockStateGate` (Phase 4): against `KeyguardManager.isKeyguardLocked()`.
- `AudioBecomingNoisyReceiver` (**2026-09-07**): §4.7's
  `ACTION_AUDIO_BECOMING_NOISY` handling.
- `GatePolicy` (**2026-09-08**): pure, no Android imports; holds the DND
  and Bluetooth-address decisions `AppContainer` previously made inline
  in the lambdas it handed the gates. The framework reads stayed behind —
  the same split `NotificationExtractionPolicy` makes against
  `NotificationExtractor`.

**`ui/`**
- `MainActivity` carries the enable-access button (§4.10) and the active
  TTS engine (§4.8's "the user should never have to wonder", Phase 4),
  plus a manual `Screen` sealed interface (`Main` / `RuleList` /
  `RuleEditor` / `Settings`) switched in a `when`.
- `SettingsScreen` (Phase 4): headset-only, lock-state, DND toggles; the
  engine picker; a live ready/initializing/error status line wired to
  `AppContainer.ttsEngineStatus`; the `BLUETOOTH_CONNECT` permission
  launcher; and (2026-09-08) the OTP keyword list with add/remove and
  reset-to-defaults, warning when emptied.
- `ui/rules/` (Phase 3): `RuleListScreen`, `RuleEditorScreen`,
  `InstalledApps`. The app picker is a `Dialog` launched from inside the
  editor, **not** a nav destination — specifically to avoid passing a
  result back across a screen boundary with no navigation library.

**Test source sets**
- `app/src/test/` — one class per testable class, mirroring package
  structure. `NotificationExtractionPolicyTest` and `SpeechQueueTest` were
  the first to exercise Android-facing code rather than pure `domain/`;
  two real problems surfaced specifically because of that, see
  [traps-and-skills.md](traps-and-skills.md). 118 tests as of
  **2026-09-08** — [status.md](status.md) holds the current count.
- `app/src/androidTest/` — added **2026-09-08**. Four classes, 19 tests:
  both DataStore round-trips (neither repository had any test),
  `AndroidTtsEngineTest` (that `stop()` really unblocks a suspended
  `speak()` — a fake can only show `SpeechQueue` *calls* it),
  `GatePolicyFrameworkConstantsTest`, `InstalledAppsTest`.
- `app/src/debug/` — `FakeNotifications.kt`, the Phase 1 injector, debug
  source set only per §5. Shares `domain/ContentHash.kt` rather than
  keeping its own copy; that sharing was the point of putting the hash in
  `domain/`.

## Divergences from the spec's original tree

Kept as history. The last entry is a genuine spec/code disagreement,
which §0 asks be said out loud rather than built around.

**Package name.** §3's original tree used `com.<yourdomain>.notifreader`
as a placeholder; the real name is `net.breadthcharge.exigentheron`
(`app/build.gradle.kts`'s `namespace`/`applicationId`). Never a
deviation — the placeholder resolved. Recorded so a future reader diffing
the two doesn't wonder.

**2026-09-05, three files the spec tree didn't have**, all serving the
goal the spec itself states (testable logic outside framework glue):

- `domain/ContentHash.kt` — §4.1 says `contentHash` is "a stable hash of
  title+body" but never says where it is computed. Putting it in
  `domain/` is what let `FakeNotifications` (debug) and
  `NotificationExtractor` (main) share one implementation instead of two
  that could drift.
- `domain/TextSanitizer.kt` — §4.2's control/zero-width/bidi stripping is
  specified under `NotificationExtractor` but has no Android dependency,
  so it lives in `domain/` and gets a real JVM test.
- `listener/NotificationExtractionPolicy.kt` — §4.2's drop conditions
  (ongoing, group summary, own package, empty title+body) as a pure
  function over plain values rather than over a `StatusBarNotification`.
  Deliberately **not** moved into `domain/` — it is extraction policy,
  not rule/secret/dedup business logic — but it has zero Android imports
  for the same testability reason.

`domain/InterruptibleCharSequence.kt` (Phase 3) and
`domain/RuleFormValidator.kt` (post-Phase-5) followed the same pattern;
neither needs its own entry beyond that.

**2026-09-05, a manifest addition not in §5's snippet.**
`AndroidManifest.xml` gained a `<queries>` block (`ACTION_MAIN` /
`CATEGORY_LAUNCHER`) for the Phase 3 installed-app picker's
`PackageManager.queryIntentActivities` call under Android 11+ package
visibility. Not a deviation from §5's hardening intent — a visibility
declaration, not a permission grant, and it touches neither `INTERNET`
nor `QUERY_ALL_PACKAGES` — but §5's snippet predates Phase 3 needing to
query other apps at all, so it is recorded here.

**2026-09-07, a real permission addition.** `AndroidManifest.xml` gained
`BLUETOOTH_CONNECT` for the per-device Bluetooth allow/deny feature
(§4.9). This is the app's first and only runtime permission — everything
else was genuinely zero-permission-request until then. Requested only
when the user turns the feature on, not at launch.

**A package the spec named and the code never grew.** §3's original tree
had `ui/permission/` for the notification-access grant flow. It is in
`ui/MainActivity.kt` instead — one button and one
`NotificationManagerCompat.getEnabledListenerPackages` check, which did
not earn a package. Recorded rather than silently dropped; if a second
permission flow ever lands (`BLUETOOTH_CONNECT`'s launcher in
`SettingsScreen.kt` is already a second such site), the spec's original
instinct gets better, not worse.

## Framework constants mirrored in pure code

Two pure classes mirror framework integer constants rather than importing
them, because importing would break the `domain/`-purity rule (and, for
`GatePolicy`, the same reasoning applied by analogy):

| Pure class | Mirrors |
|---|---|
| `domain/SecretDetector.kt` | `Notification.VISIBILITY_PRIVATE`, `VISIBILITY_SECRET` |
| `speech/GatePolicy.kt` | `NotificationManager.INTERRUPTION_FILTER_ALL` |

Drift here would not fail to compile; it would silently misclassify. The
mitigation is `androidTest`'s `GatePolicyFrameworkConstantsTest`, which a
JVM test structurally cannot do.

## Recording a new divergence

If a class ends up somewhere other than its natural package for no
principled reason, or a component nothing anticipated appears, record it
here with a date and the reason, per skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — and say in
the same change whether `AGENTS.md` itself should be corrected instead of
the code, per its §0 instinct to flag disagreement rather than build
around it silently.
