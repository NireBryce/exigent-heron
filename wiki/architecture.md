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

As of **2026-09-05** (Phase 1 complete, see [status.md](status.md)), under
`app/src/main/java/net/breadthcharge/exigentheron/`:

- `App.kt` — `Application` subclass.
- `AppContainer.kt` — the manual-DI container `AGENTS.md` §2 specifies
  instead of Hilt. Deliberately empty right now (just holds
  `appContext`); gets wired up incrementally as later phases introduce
  things worth holding (`SettingsRepository`, `RuleRepository`, the speech
  stack).
- `SafeLog.kt` — the sole permitted entry point to `android.util.Log`
  (`AGENTS.md` §4.6). Logcat tag is `"ExigentHeron"`, not the class name —
  worth knowing before scoping an `adb logcat` (see
  [testing.md](testing.md) and skill
  [`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md)).
  Exposes exactly `decision(pkg, ruleId, action)`, `lifecycle(msg)`,
  `error(msg, t?)` — no arbitrary-string overload, by design.
- `ui/MainActivity.kt` — launcher activity, currently a blank screen.
- `domain/` — pure Kotlin, no Android imports, per `AGENTS.md` §3:
  `NotificationPayload.kt`, `Rule.kt` (with `RuleAction`), `Decision.kt`,
  `Deduplicator.kt`, `RuleEngine.kt`, `SecretDetector.kt`. Each carries a
  doc comment covering its own contract; `RuleEngine.kt`'s is worth
  reading directly rather than summarized here — it documents a real,
  verified limitation of its own regex-timeout mitigation (see
  [history.md](history.md)).

Under `app/src/debug/java/net/breadthcharge/exigentheron/`:

- `debug/FakeNotifications.kt` — the Phase 1 fake-notification injector
  (`AGENTS.md` §6). `debug/` source set only, per spec; see
  [testing.md](testing.md) for how to invoke it.

Under `app/src/test/java/net/breadthcharge/exigentheron/domain/`: one
test class per `domain/` class above (`NotificationPayloadTest`,
`DeduplicatorTest`, `SecretDetectorTest`, `RuleEngineTest`) — 31 tests
total, see [status.md](status.md) for the current pass count.

Nothing under `listener/`, `speech/`, or `data/` yet — those arrive in
Phases 2–4 per `AGENTS.md` §6.

## Target tree

See `AGENTS.md` §3 for the full tree and the data-flow diagram
(`onNotificationPosted` → extractor → deduplicator → rule engine → secret
detector → output gate → speech queue). Not reproduced here — that's the
one copy, and this page is a link to it, not a second one that can drift
from it separately.

## Deviations from AGENTS.md §3

None yet. `AGENTS.md` §3's tree uses `com.<yourdomain>.notifreader` as a
placeholder package name; the real one is `net.breadthcharge.exigentheron`
(see `app/build.gradle.kts`'s `namespace` and `applicationId`) — not a
deviation, just the placeholder resolved. If a real divergence from the
spec tree happens later (a class that ends up somewhere other than its
specified package, a component the spec didn't anticipate), record it
here with a date and why, per skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md) — and say in the
same change whether `AGENTS.md` itself should be corrected instead of the
code, per its own §0 instinct to flag disagreement rather than build
around it silently.
