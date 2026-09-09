# Overview

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [What this is](#what-this-is)
- [The one hard constraint](#the-one-hard-constraint)
- [What happens to a notification](#what-happens-to-a-notification)
- [Where things live](#where-things-live)
- [Working on it](#working-on-it)
- [Which document answers what](#which-document-answers-what)
- [See also](#see-also)

What the app is, what it must never do, and how a notification gets from
the system to a spoken sentence.

## What this is

A personal, sideloaded Android app that reads selected notifications
aloud via on-device TTS, filtered by rules you write.

- Package `net.breadthcharge.exigentheron`, one Gradle module (`:app`).
- Kotlin, Compose, manual DI (no Hilt), Coroutines + Flow, DataStore.
- Apache-2.0 ([LICENSE](../LICENSE)).
- Sideloaded for personal use — not a Play Store app.

Explicitly **out of scope**, and worth knowing before you propose a
feature: cloud sync, LLM summarization, notification history, and reply
actions. [`AGENTS.md`](../AGENTS.md) §1 has the full list.

## The one hard constraint

**Never log notification content. Never request `INTERNET`. No
telemetry.** `AGENTS.md` §0 states it three separate ways, and most of
the structure below exists to serve it rather than to be elegant.

In practice that means:

- `SafeLog` is the only file allowed to touch `android.util.Log`, and it
  has no method that takes an arbitrary string. CI greps for this.
- The manifest has no `INTERNET` permission, and the merged manifest gets
  re-read to confirm it rather than assumed.
- One runtime permission exists in the whole app: `BLUETOOTH_CONNECT`,
  requested only if you turn on per-device Bluetooth control.

## What happens to a notification

One path, implemented literally in
[`NotificationTtsListener.route()`](../app/src/main/java/net/breadthcharge/exigentheron/listener/NotificationTtsListener.kt):

```
onNotificationPosted(sbn)
  → NotificationExtractor.extract()   → NotificationPayload?
  → Deduplicator.isDuplicate()        → drop
  → RuleEngine.evaluate()             → Decision
  → SecretDetector.scan()             → possibly downgrade
  → OutputRouteGate / LockStateGate   → drop
  → SpeechQueue.enqueue()
```

Three things about it are requirements, not accidents:

- **The order.** Dedup before rules, so a repost costs no regex work.
  Secrets after rules, because `SecretDetector` can only ever *downgrade*
  what the rules decided. Gates last, right before enqueue.
- **The listener is routing only** — each step is one call, no branching
  of its own — and it hops off the binder thread first, because rule
  matching can burn its whole timeout and `onNotificationPosted` has to
  return promptly.
- **Default-deny.** No rule matches, nothing is spoken. A fresh install
  is silent until you add a rule.

[architecture.md](architecture.md) has the annotated version.

## Where things live

```
domain/     pure Kotlin, zero Android imports — rules, secrets, dedup
listener/   the NotificationListenerService and extraction
speech/     the queue, the TTS engine, the audio gates
data/       DataStore-backed settings and rules
ui/         Compose screens
```

**`domain/` having zero Android imports is the load-bearing rule here.**
CI enforces it with a grep. It is what lets the logic most worth testing
be tested by plain JVM unit tests — no Robolectric, no emulator. Where
logic was specified inside an Android class but didn't actually need
Android, it got pulled out anyway.

Everything outside `domain/` is meant to be glue thin enough that reading
it is enough to believe it. That is a goal, not a guarantee — instrumented
tests under `app/src/androidTest/` exist for where it doesn't hold.

## Working on it

```sh
direnv allow      # or: nix develop
just              # lists every recipe
just test         # JVM unit tests, no device needed
just build        # debug APK
just test-all     # everything, cheapest first
```

There is **no `gradlew`** in this repo, deliberately — the Nix dev shell
puts a pinned `gradle`, JDK, Kotlin, Android SDK, and an emulator on
`PATH`. See [flake.nix](../flake.nix).

Before you land anything:

- Every change goes via a branch and a PR — never a direct commit to
  `main`. Skill [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md).
- If your change makes a wiki page wrong, fix it in the same change.
  Skill [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md).
- New dependencies are a question to ask, not a call to make —
  `AGENTS.md` §2's list is meant to be the whole list.

[testing.md](testing.md) has the rest.

## Which document answers what

| Question | File |
|---|---|
| what must this app always do? | [`AGENTS.md`](../AGENTS.md) |
| where does this class live, and why there? | [architecture.md](architecture.md) |
| does this actually work yet? | [status.md](status.md) |
| how do I run it? | [testing.md](testing.md) |
| is this a known problem? | [open-threads.md](open-threads.md) |
| why is it built this way? | [history.md](history.md) |
| why does this class do that odd thing? | the class's own doc comment |

That last row matters more than it looks. `RuleEngine.kt`,
`SecretDetector.kt`, and `SpeechQueue.kt` each document real, verified
limitations of their own mitigations, and those comments are the copy
that stays correct. Read them rather than expecting a wiki page to
repeat them.

Most of this codebase, including this wiki, is written by LLM coding
agents under human direction and review — see the root
[README.md](../README.md).

## See also

- [overview-4llm.md](overview-4llm.md) — the dense companion: full
  constraint provenance, the document-layer reasoning, and the tooling
  inventory.
- [architecture.md](architecture.md) — the tree and the data flow.
- [testing.md](testing.md) — how to actually run it.
