# Architecture

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [What "pure" means here](#what-pure-means-here)
- [The rule everything else follows from](#the-rule-everything-else-follows-from)
- [Package tree](#package-tree)
- [Data flow](#data-flow)
- [What each package holds](#what-each-package-holds)
- [Test source sets](#test-source-sets)
- [Adding a class](#adding-a-class)
- [See also](#see-also)

Where the code lives and how it fits together. **This page is the
canonical tree** — [`AGENTS.md`](../AGENTS.md) §3 keeps a six-bullet
summary and points here.

## What "pure" means here

The tree below marks `domain/` and two other files **PURE**. It is worth
being exact about that, because it does not mean what the word usually
means.

**Pure here means one thing: no Android framework dependency.** A pure
file can be constructed and run by a plain JVM unit test — no emulator,
no instrumentation, no Robolectric. That is the entire definition.

It does **not** mean side-effect-free, stateless, or deterministic. Real
examples from `domain/`, all of them pure by this definition:

- `Deduplicator` keeps a mutable access-ordered `LinkedHashMap` and
  mutates it on every call — `isDuplicate()` is a question that changes
  the answer to the next one.
- `RuleEngine` launches coroutines, races a 100ms timeout, and depends on
  `Thread.interrupt()` actually landing mid-regex.
- Both `*Holder` classes own a `MutableStateFlow` and a long-lived
  `CoroutineScope`.

What *is* allowed in pure code: the Kotlin stdlib, the JDK
(`java.security.MessageDigest`, `java.util.regex`), kotlinx.coroutines,
and kotlinx.serialization. What is not: anything under `android.`.

**Two consequences worth knowing before you write pure code.**

*Framework constants have to be copied, not imported.* `SecretDetector`
can't `import android.app.Notification`, so it mirrors
`VISIBILITY_PRIVATE`/`VISIBILITY_SECRET` as its own integers; `GatePolicy`
does the same for `INTERRUPTION_FILTER_ALL`. A drift between the copy and
the framework wouldn't fail to compile — it would silently misclassify —
which is why an instrumented test asserts they still match.

*Framework values arrive as plain arguments or function references.* Pure
code never reaches for a `Context` or a system service; the caller reads
those and passes the result in. That's why the gates take
`() -> Boolean` rather than an `AudioManager`.

**How it's enforced, and what that misses.** CI greps `domain/` for
`^import android.` — anchored, so `kotlinx.coroutines` passes. The
anchor is also the limit: a fully-qualified reference with no import
line, or an Android type arriving through a non-`domain/` parameter,
would slip past it. Neither has happened; the rule is upheld by writing
the code this way, and the grep is the backstop.

Note that **pure and `domain/` are not the same set.**
`listener/NotificationExtractionPolicy.kt` and `speech/GatePolicy.kt` are
both pure and both deliberately outside `domain/` — they're extraction
and gate policy, not rule/secret/dedup logic. Purity is a property of a
file; `domain/` is a claim about what a file is *about*.

## The rule everything else follows from

**`domain/` has zero Android imports.** `RuleEngine`, `SecretDetector`,
and `Deduplicator` are plain Kotlin, unit-testable on the JVM with no
Robolectric, no instrumentation, no emulator. CI enforces it with a grep
for `^import android.` under `domain/`.

`kotlinx.coroutines` is fine in `domain/` — it is not Android. That's why
`RuleEngine` and both holder classes can live there.

The corollary, which comes up constantly: **when logic is specified
inside an Android class but doesn't actually need Android, split it out.**
`NotificationExtractionPolicy`, `TextSanitizer`, `ContentHash`,
`InterruptibleCharSequence`, `RuleFormValidator`, and `GatePolicy` all
exist because of that.

Everything outside `domain/` aims to be glue thin enough that reading it
is enough to believe it. That's a goal, not a fact — the worst pipeline
bug so far lived in a seam every JVM test passed straight through.
`app/src/androidTest/` is the tool for where the goal doesn't hold.

## Package tree

Single Gradle module (`:app`). Under
`app/src/main/java/net/breadthcharge/exigentheron/`:

```
net.breadthcharge.exigentheron/
├── App.kt                            # Application subclass; owns AppContainer
├── AppContainer.kt                   # manual DI: constructs and holds singletons
├── SafeLog.kt                        # the ONLY file permitted to touch android.util.Log
│
├── listener/
│   ├── NotificationTtsListener.kt      # NotificationListenerService — routing only
│   ├── NotificationExtractor.kt        # StatusBarNotification -> NotificationPayload
│   └── NotificationExtractionPolicy.kt # pure drop conditions
│
├── domain/                           # PURE. zero Android imports.
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
│   ├── SecretDetectorHolder.kt       # rebuilds a SecretDetector from Flow<List<String>>
│   ├── Deduplicator.kt               # injected clock; LRU + TTL
│   ├── ContentHash.kt                # stable hash of title+body
│   ├── TextSanitizer.kt              # control/zero-width/bidi stripping
│   └── InterruptibleCharSequence.kt  # makes a runaway regex actually abortable
│
├── speech/
│   ├── SpeechQueue.kt                # single-consumer actor over a bounded Channel
│   ├── TtsEngine.kt                  # interface — what makes SpeechQueue testable
│   ├── AndroidTtsEngine.kt           # real impl over android.speech.tts.TextToSpeech
│   ├── AudioFocusManager.kt
│   ├── AudioBecomingNoisyReceiver.kt # a route change *during* an utterance
│   ├── OutputRouteGate.kt            # headset-only enforcement
│   ├── GatePolicy.kt                 # PURE — the DND/Bluetooth gate decisions
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

## Data flow

One path, implemented literally in `NotificationTtsListener.route()`:

```
onNotificationPosted(sbn)
  → NotificationExtractor.extract(sbn)      → NotificationPayload?
  → Deduplicator.isDuplicate(payload)       → drop if true
  → RuleEngine.evaluate(payload, rules)     → Decision
  → SecretDetector.scan(decision)           → possibly downgrade
  → OutputRouteGate.allows()                → drop if false
  → LockStateGate.allows()                  → drop if false
  → SpeechQueue.enqueue(SpeechRequest)
  → AudioFocusManager.request() → TtsEngine.speak() → abandon focus when queue drains
```

**The ordering is a requirement** (`AGENTS.md` §3). Dedup before rules so
a repost costs no regex work; `SecretDetector` after the rules and able
only to downgrade; the gates last, immediately before enqueue.

The listener does routing only — one call per step, no branching — and
hops off the binder thread before `route()`.

**`OutputRouteGate` is checked twice**, at enqueue and again per
utterance, because a headset can disconnect while items are still
queued. `AudioBecomingNoisyReceiver` covers the case neither check sees:
a route change mid-utterance. All three exist because the enqueue-only
version shipped and was wrong.

## What each package holds

Per-class reasoning lives in each class's own doc comment — that's the
copy that stays correct. This is a map to get you to the right file.

- **`App.kt` / `AppContainer.kt`** — manual DI (`AGENTS.md` §2 specifies
  this instead of Hilt). Wires the deduplicator, both repositories, both
  holders, the audio focus manager, and the gates. `ttsEngine` and
  `speechQueue` are `var`s: `rebuildTtsEngine(enginePackage)` swaps in a
  fresh pair so a settings-screen engine choice actually takes effect.
- **`SafeLog.kt`** — the sole entry point to `android.util.Log`. Exactly
  `decision(pkg, ruleId, action)`, `lifecycle(msg)`, `error(msg, t?)`;
  no arbitrary-string overload. **Logcat tag is `"ExigentHeron"`**, not
  the class name — check before scoping an `adb logcat`.
- **`domain/`** — the rule engine, secret detection, dedup, and the pure
  validators/codecs. The two `*Holder` classes rebuild their subject from
  a `Flow` so an edit in the UI takes effect on the next notification
  rather than on the next app start.
- **`listener/`** — the service (routing only), the Android-facing half
  of extraction, and the pure drop-condition half.
- **`data/`** — Preferences DataStore. Rules are one JSON blob under
  `rules_json`; `Settings` carries headset-only, lock-state,
  DND-override, engine package, the two Bluetooth address sets, a
  truncation cap, and the OTP keyword list. Allow and Deny are mutually
  exclusive *by construction* — `setBluetoothDeviceDecision` always
  clears the other set before writing, so no reachable state has an
  address in both.
- **`speech/`** — the queue and everything that decides whether it may
  speak. Both gates take their Android-facing checks as function
  references, the same pattern `SpeechQueue`'s constructor already used;
  that's what keeps them testable.
- **`ui/`** — Compose screens plus a hand-rolled `Screen` sealed
  interface switched in a `when`. **No navigation-compose dependency** —
  four screens don't need one, and it isn't in `AGENTS.md` §2's list.
  ViewModels come from `viewModelFactory`, not Hilt.

## Test source sets

| Source set | Needs a device | Covers |
|---|---|---|
| `app/src/test/` | no | one class per testable class; almost all of `domain/` |
| `app/src/androidTest/` | yes | what a JVM test structurally cannot reach |
| `app/src/debug/` | no | `FakeNotifications`, the debug-only injector |

The instrumented set is small and deliberate: DataStore round-trips, that
`AndroidTtsEngine.stop()` really unblocks a suspended `speak()`, that the
framework constants `SecretDetector` and `GatePolicy` mirror still match,
and a regression test for the `<queries>` manifest block.
[testing.md](testing.md) has how to run them and what they don't prove.

Current test counts live in [status.md](status.md) rather than here.

## Adding a class

1. Can it be pure? Then it goes in `domain/`, no Android imports.
2. Does it decide something? Pull the decision into a pure function and
   leave the framework read behind — `NotificationExtractionPolicy` and
   `GatePolicy` are the two worked examples.
3. Does it need an Android type at construction time? Take a function
   reference instead, so a JVM test can supply one.
4. If it ends up somewhere unexpected anyway, record why in
   [architecture-4llm.md](architecture-4llm.md), dated, and say whether
   `AGENTS.md` should be corrected instead of the code — its §0 asks for
   disagreement out loud rather than worked around.

## See also

- [architecture-4llm.md](architecture-4llm.md) — the dense companion:
  per-file evolution with dates, and every recorded divergence from the
  spec's original tree.
- [overview.md](overview.md) — the shorter orientation.
- [testing.md](testing.md) — running any of this.
