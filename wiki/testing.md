# Testing

## Contents

- [Building and installing](#building-and-installing)
- [Checking the manifest](#checking-the-manifest)
- [Watching logcat safely](#watching-logcat-safely)
- [Once there's a listener to test (Phase 2+)](#once-theres-a-listener-to-test-phase-2)
- [Hardening-pass device matrix (Phase 5)](#hardening-pass-device-matrix-phase-5)

How to actually build, install, and exercise this app — as opposed to
[architecture.md](architecture.md), which describes the code. The one page
in this wiki allowed to hold real procedural content rather than just
links (see [styleguide.md](styleguide.md)), because the source here is the
act of running the thing, not a file to point at.

## Building and installing

No `gradlew` in this repo — use the `gradle` on `PATH` inside the Nix dev
shell (`flake.nix`'s own shellHook says this too):

```sh
nix develop --command gradle assembleDebug
nix develop --command gradle installDebug   # needs a running emulator/device
```

`gradle testDebugUnitTest` runs whatever JVM unit tests exist under
`app/src/test` — as of **2026-09-05** (Phase 1) that's 31 tests across
`domain/`, all passing; see [status.md](status.md) for the current count
rather than trusting this number as it ages.

## Checking the manifest

Phase 0's own acceptance criterion — confirm `INTERNET` is absent, don't
assume it:

```sh
nix develop --command gradle :app:processDebugMainManifest
```

then read the merged manifest it writes under
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`
directly, rather than trusting that the task succeeding proves the
permission is absent — it doesn't; a merge can succeed with an unwanted
permission merged in from a dependency, which is exactly the class of
thing this check exists to catch.

## Watching logcat safely

`SafeLog`'s tag is `"ExigentHeron"` (see [architecture.md](architecture.md)
— not the class name, which is a mistake that's already happened once
here, see [traps-and-skills.md](traps-and-skills.md)). Scope to it rather
than running `adb logcat` bare:

```sh
adb logcat -s ExigentHeron
```

An unscoped `adb logcat` captures notification-shaped content from every
app on the device, not just this one — AGENTS.md's Definition of Done
requires zero notification content in logcat, and that bar applies to
what ends up in a Claude session's transcript too. See skill
[`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md)
for the hook that nudges toward this automatically.

## Once there's a listener to test (Phase 2+)

Not yet — `NotificationTtsListener` doesn't exist until Phase 2 (see
[status.md](status.md)). Once it does, `AGENTS.md` §6's own per-phase
acceptance criteria are the actual test script to run (a notification
spoken once, three duplicates in 10 seconds still speaking once, a music
duck-and-recover, etc.) — not restated here to avoid a second copy that
can drift from §6's.

`FakeNotifications` (`app/src/debug/java/.../debug/FakeNotifications.kt`,
landed Phase 1) is the debug-only injector for exercising the domain
pipeline (`Deduplicator` → `RuleEngine` → `SecretDetector`) without a
device or a real listener. Nothing wires it to anything yet — there's no
UI or listener to feed it into until Phase 2/3 — so for now it's reached
from a unit test or a scratch `main()`, e.g.:

```kotlin
val engine = RuleEngine(rules = listOf(/* ... */))
runBlocking {
    FakeNotifications.scenarios().forEach { payload ->
        println(engine.evaluate(payload)) // evaluate() is suspend
    }
}
```

This section is the place to note the real invocation once Phase 2/3
gives it a caller worth describing (a debug menu item, a test harness
run against the real listener, etc.) rather than this placeholder.

## Hardening-pass device matrix (Phase 5)

`AGENTS.md` §6 Phase 5 requires testing "on a locked device, in a call,
and with a work profile present if available." Nothing to track here yet
— this section exists so that when Phase 5 actually runs, the real
devices/configurations tested (not just "tested," per skill
[`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md)) get recorded
here rather than only in a commit message.
