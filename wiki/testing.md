# Testing

_Last modified: 2026-09-08_

## Contents

- [Building and installing](#building-and-installing)
- [Instrumented tests (`app/src/androidTest/`)](#instrumented-tests-appsrcandroidtest)
- [Checking the manifest](#checking-the-manifest)
- [Watching logcat safely](#watching-logcat-safely)
- [Once there's a listener to test (Phase 2+)](#once-theres-a-listener-to-test-phase-2)
- [Once there's a rule editor to test (Phase 3+)](#once-theres-a-rule-editor-to-test-phase-3)
- [Once there's a settings screen to test (Phase 4+)](#once-theres-a-settings-screen-to-test-phase-4)
- [Hardening-pass device matrix (Phase 5)](#hardening-pass-device-matrix-phase-5)
- [Detecting an OEM kill (issue #27)](#detecting-an-oem-kill-issue-27)

How to actually build, install, and exercise this app — as opposed to
[architecture.md](architecture.md), which describes the code. The one page
in this wiki allowed to hold real procedural content rather than just
links (see [styleguide.md](styleguide.md)), because the source here is the
act of running the thing, not a file to point at.

## Building and installing

No `gradlew` in this repo — use the `gradle` on `PATH` inside the Nix dev
shell (`flake.nix`'s own shellHook says this too):

```sh
just build                                  # or: gradle assembleDebug
nix develop --command gradle installDebug   # needs a running emulator/device
```

As of **2026-09-06** the dev shell itself provides the emulator — no
Android Studio needed to get one (`flake.nix`'s `androidComposition` pulls
in a `google_apis` x86_64 system image alongside the SDK). Inside `nix
develop`:

```sh
avdmanager create avd -n dev -k "system-images;android-37.0;google_apis;x86_64" -d pixel_6
emulator -avd dev
```

Needs `/dev/kvm` access (e.g. being in the `kvm` group) for reasonable
boot times.

## Instrumented tests (`app/src/androidTest/`)

Added **2026-09-08**. These need a device or emulator — that is the whole
point of them, per `AGENTS.md` §3: they cover what a JVM test
structurally cannot reach, which is framework facts and wiring.

```sh
just test-instrumented          # or: gradle connectedDebugAndroidTest
```

`just` recipes are the shortest path to any of this — see
[`.justfile`](../.justfile) for the whole list, and
[`scripts/test.sh`](../scripts/test.sh) for what they run. The device
recipes handle the emulator themselves: they boot a headless one when
none is attached, wait for `sys.boot_completed` rather than for `adb
devices` to merely list something, and stop only an emulator they
started. A device you were already using is left alone.

**19 tests, 0 failures, 0 skipped, verified 2026-09-08** on the
`nix develop` emulator (`dev`, API 37, `google_apis` x86_64) — still not
a physical device, see the Phase 5 matrix below. What they cover:

- `data/RuleRepositoryTest`, `data/SettingsRepositoryTest` — the
  DataStore round-trips neither repository had any test for, plus the
  defaults (headset-only and lock-state on, DND-override off) and the
  allow/deny sets' mutual exclusion. Note what these *don't* prove:
  `preferencesDataStore` is a per-`Context` singleton, so a second
  repository in the same process shares the instance rather than
  re-reading the file. The force-stop step below is still the only thing
  that shows cross-process persistence.
- `speech/AndroidTtsEngineTest` — that `stop()` really does unblock a
  suspended `speak()`. A fake `TtsEngine` can only prove `SpeechQueue`
  *calls* `stop()`; both the `ACTION_AUDIO_BECOMING_NOISY` path and the
  truncation timeout depend on that call actually achieving something.
- `speech/GatePolicyFrameworkConstantsTest` — that the constants
  `SecretDetector` and `GatePolicy` mirror still equal the framework's.
  A drift wouldn't fail to compile; it would silently misclassify.
- `ui/rules/InstalledAppsTest` — a regression test for the `<queries>`
  manifest block, not for the function's own three lines.

### The listener acceptance script

[`scripts/listener-acceptance.sh`](../scripts/listener-acceptance.sh)
automates most of the Phase 2 script below — it grants notification
access with `cmd notification allow_listener` (no UI tap), posts real
notifications through the platform, and reads back `SafeLog.decision`
lines. **Verified 2026-09-08**: passes on the emulator, run three times.

```sh
just test-acceptance            # or: ./scripts/listener-acceptance.sh
```

It asserts on decision lines only — a package, a rule id, an action,
never notification content — and scopes logcat to the app's own tag, the
same rule "Watching logcat safely" states above. What it cannot do is
the part needing ears: duck-and-recover, headset-only routing, and the
engine picker all stay manual below.

`gradle testDebugUnitTest` runs whatever JVM unit tests exist under
`app/src/test` — as of **2026-09-08** that's 118 tests, all passing; see
[status.md](status.md) for the current count rather than trusting this
number as it ages. A couple of the `speech/` and
`listener/` tests exercise real background coroutines with real time —
if one ever seems flaky, re-run it standalone a few times before
assuming it's a fluke; see [traps-and-skills.md](traps-and-skills.md)
for a real one already caught here. `RuleEngineHolderTest` similarly
needs real time rather than `kotlinx-coroutines-test`'s virtual-time
`runTest` — see its own top-of-file comment for why mixing the two made
`RuleEngine.evaluate()`'s timeout fire spuriously.

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

`NotificationTtsListener` exists as of Phase 2 (see
[status.md](status.md)) but its on-device acceptance criteria are
**unconfirmed** — no device was available the session that built it.
Phase 2's own acceptance criteria (originally `BUILD_PLAN.md`'s, removed
once all six phases were built and verified — this page is now the copy
of record) are the actual test script to run: a notification spoken
once, three duplicates in 10 seconds still speaking once, a music
duck-and-recover. To actually run them:

1. `nix develop --command gradle installDebug` with a device or emulator
   attached.
2. Launch the app, tap "Enable notification access", grant it in the
   system settings screen that opens.
3. Tap "Manage rules" → "+" and add a rule for whatever messaging app is
   actually on your device (Phase 3 replaced the old
   `phase2HardcodedRules` stopgap with a real rule editor — see
   [history.md](history.md) — so there's no rule at all, and thus no
   speech, until one is added this way).
4. Trigger a real notification from that app; confirm it's spoken once.
   Trigger the same notification 3× within 10s; confirm it speaks once,
   not three times (`Deduplicator`). Start music, trigger a
   notification; confirm it ducks and recovers (`AudioFocusManager`).

`FakeNotifications` (`app/src/debug/java/.../debug/FakeNotifications.kt`,
landed Phase 1) is the debug-only injector for exercising the domain
pipeline (`Deduplicator` → `RuleEngine` → `SecretDetector`) without a
device or a real listener. Still nothing wires it into the app itself —
no debug menu, no UI for it — so it's reached from a unit test or a
scratch `main()`, e.g.:

```kotlin
val engine = RuleEngine(rules = listOf(/* ... */))
runBlocking {
    FakeNotifications.scenarios().forEach { payload ->
        println(engine.evaluate(payload)) // evaluate() is suspend
    }
}
```

This section is the place to note a real invocation once something
actually calls it (a debug menu item, a harness run against the real
listener) rather than this placeholder.

## Once there's a rule editor to test (Phase 3+)

Phase 3's own acceptance criteria (see [status.md](status.md)) are also
**unconfirmed** on-device this session — see [open-threads.md](open-threads.md).
To run them:

1. From "Manage rules", add a rule, force-stop the app
   (Settings → Apps → exigent-heron → Force stop), relaunch, and open
   "Manage rules" again — the rule should still be listed
   (`RuleRepository`'s DataStore persistence).
2. In the rule editor, enter an invalid title or body pattern — an
   unbalanced paren (`(unclosed`), or a backreference (`\1`) — and tap
   Save; confirm an inline error appears and the rule is not saved,
   rather than the app crashing later when a real notification arrives.
3. Tap "Choose apps" in the editor and confirm the installed-app list is
   non-empty and searchable-by-scrolling — this is the first thing that
   exercises the `<queries>` manifest addition (see
   [architecture.md](architecture.md)); an empty list on a real device
   despite installed launchable apps would mean that declaration isn't
   doing its job.

## Once there's a settings screen to test (Phase 4+)

Phase 4's on-device acceptance criteria (see [status.md](status.md)) are
**unconfirmed** — no device available this session. The queue-collapse
criterion is covered directly by a JVM unit test
(`SpeechQueueTest`'s "a burst of more than 5 pending items collapses to
one summary utterance"); the other two need a real device:

1. Turn on "Headset only" in Settings (defaulted on already), disconnect
   any headset, trigger a notification that would otherwise speak;
   confirm nothing is spoken. Connect a wired or Bluetooth headset and
   trigger another; confirm it speaks.
2. Open Settings → "Choose engine", pick a different installed TTS
   engine than the current one, and trigger a notification; confirm it
   speaks using the newly chosen engine (audibly different voice, or
   check `AppContainer.ttsEngine`'s bound package if inspecting via
   debugger) rather than the one still active from before the switch.
3. Trigger ten notifications within five seconds from an allowlisted
   app; confirm a single "10 new notifications." utterance is heard
   instead of ten read individually — this is Phase 4's own acceptance
   line, restated here as the on-device version of the already-passing
   unit test above.

## Hardening-pass device matrix (Phase 5)

Phase 5 requires testing "on a locked device, in a call, and with a work
profile present if available." **Locked-device and
in-call confirmed on the emulator as of 2026-09-07** (real
`KeyguardManager`/`AudioManager` state, not a mock — see `SECURITY.md`
§4 for exactly how); work-profile behavior confirmed there too, but only
against a non-DPC secondary profile, not a fully provisioned enterprise
one. **Still not a physical device** — no physical Android device has
been used against this app at any point, so anything specific to real
hardware (OEM battery killing, a real cellular radio, a real MDM
enrollment) remains untested; see `SECURITY.md` §4's own caveats before
treating this as done. The exact repro steps live in `SECURITY.md` §4
rather than being duplicated here — when a physical device is actually
used, record the real outcome in both `SECURITY.md` (updating the item,
with what was observed) and this line (dated), not just one of the two.

## Detecting an OEM kill (issue #27)

`NotificationTtsListener.onListenerConnected`/`onListenerDisconnected`
already log via `SafeLog.lifecycle` (see
[architecture.md](architecture.md)) — `onListenerDisconnected` is the
standard `NotificationListenerService` callback that fires whenever the
OS unbinds the service, whether that's a deliberate unbind (the user
revoking notification access) or an OEM kill. Android doesn't expose
which; there's nothing more specific to log until it does.

No code change is needed to *detect* a kill — this is purely a "run it
for a day on real hardware and read the log" task (issue #27 tracks the
underlying "does Android actually keep this alive" question; this is
the concrete repro for it):

1. Install the debug build on a physical device (not the `nix develop`
   emulator — see the Phase 5 matrix above for why that specifically
   doesn't exercise real OEM battery-optimization behavior), grant
   notification access, add at least one enabled rule so the listener
   has a reason to stay bound.
2. Leave the device alone, unplugged from a debugger, for a full day of
   normal use.
3. Pull the log and look for a `listener disconnected` line with no
   `listener connected` following it soon after (a deliberate rebind
   reconnects almost immediately; a kill either doesn't reconnect at all
   or only reconnects on the next notification/reboot):

   ```sh
   adb logcat -s ExigentHeron -d | grep -i listener
   ```

4. If a kill shows up: per `AGENTS.md` §8, the fix is **not** a
   wakelock, a foreground-service notification, or any other
   keep-alive trick — walk the user to that OEM's battery-optimization
   exemption screen for this app (Settings → Apps → exigent-heron →
   Battery → Unrestricted; Samsung and Xiaomi are the usual culprits)
   and document it as the fix. Record the actual outcome (killed or
   not, which OEM/device) on issue #27 rather than leaving it as an
   open question indefinitely.
