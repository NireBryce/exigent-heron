# Testing

_Last modified: 2026-09-08_

## Contents

- [The short list](#the-short-list)
- [Building and installing](#building-and-installing)
- [Getting an emulator](#getting-an-emulator)
- [Unit tests](#unit-tests)
- [Instrumented tests](#instrumented-tests)
- [The listener acceptance script](#the-listener-acceptance-script)
- [Watching logcat safely](#watching-logcat-safely)
- [Manual checks that still need a human](#manual-checks-that-still-need-a-human)
- [Checking the manifest](#checking-the-manifest)
- [Detecting an OEM kill](#detecting-an-oem-kill)
- [See also](#see-also)

How to actually build, install, and exercise this app.

## The short list

```sh
direnv allow          # or: nix develop — everything below needs the dev shell
just                  # list every recipe
just test             # JVM unit tests. Fast, no device.
just build            # debug APK
just test-instrumented  # androidTest — needs a device or emulator
just test-acceptance    # posts real notifications, checks decisions
just test-device        # both of the above
just test-all           # everything, cheapest first
just structure          # the two rules CI greps for
just wiki-lint          # wiki claims vs the repo
just lint               # Android Lint, debug and release
```

`just` is the interface; [`scripts/test.sh`](../scripts/test.sh) is what
it runs and works standalone if you prefer.

**The device recipes handle the emulator themselves.** They boot a
headless one when none is attached, wait for `sys.boot_completed` rather
than for `adb devices` to merely list something, and stop only an
emulator they started. A device you were already using is left alone.

## Building and installing

There is no `gradlew` in this repo — use the `gradle` the Nix dev shell
puts on `PATH`.

```sh
just build                                  # or: gradle assembleDebug
nix develop --command gradle installDebug   # needs a running emulator/device
```

## Getting an emulator

The dev shell provides one — no Android Studio needed. Inside
`nix develop`:

```sh
avdmanager create avd -n dev -k "system-images;android-37.0;google_apis;x86_64" -d pixel_6
just emulator     # boots it headless and leaves it running
```

`just emulator` is for iterating; unlike the test recipes it does not
tear down what it starts. You need `/dev/kvm` access (be in the `kvm`
group) for reasonable boot times.

## Unit tests

```sh
just test         # or: gradle testDebugUnitTest
```

118 tests as of 2026-09-08, all passing — [status.md](status.md) has the
current count.

A few of the `speech/` and `listener/` tests exercise real background
coroutines with real time. **If one looks flaky, re-run it standalone a
few times before assuming it's a fluke** — a real race has already hidden
behind exactly that assumption here, see
[traps-and-skills.md](traps-and-skills.md).

## Instrumented tests

```sh
just test-instrumented    # or: gradle connectedDebugAndroidTest
```

19 tests, verified 2026-09-08 on the emulator. They exist for what a JVM
test structurally cannot reach:

- `data/RuleRepositoryTest`, `data/SettingsRepositoryTest` — DataStore
  round-trips and defaults. **These do not prove cross-process
  persistence** — the force-stop check below is still the only thing that
  does.
- `speech/AndroidTtsEngineTest` — that `stop()` really unblocks a
  suspended `speak()`. A fake can only show that `SpeechQueue` *calls*
  it, and two features depend on the call achieving something.
- `speech/GatePolicyFrameworkConstantsTest` — that the framework
  constants the pure code mirrors still match. Drift wouldn't fail to
  compile; it would silently misclassify.
- `ui/rules/InstalledAppsTest` — a regression test for the `<queries>`
  manifest block.

## The listener acceptance script

```sh
just test-acceptance      # or: ./scripts/listener-acceptance.sh
```

Automates Phase 2's acceptance criteria: grants notification access with
`cmd notification allow_listener` (no UI tap), posts real notifications
through the platform, and reads back the decision lines. Verified
2026-09-08 — passes on the emulator, run three times.

It asserts on decision lines only — a package, a rule id, an action,
never notification content.

## Watching logcat safely

**`SafeLog`'s tag is `"ExigentHeron"`**, not the class name. Scope to it:

```sh
adb logcat -s ExigentHeron
```

An unscoped `adb logcat` captures notification-shaped content from every
app on the device. `AGENTS.md`'s Definition of Done requires zero
notification content in logcat, and that bar applies to what ends up in
an agent session's transcript too. Skill
[`signing-and-log-hygiene`](../.claude/skills/signing-and-log-hygiene/SKILL.md)
has the hook that nudges toward this.

## Manual checks that still need a human

Install, launch, tap **Enable notification access**, grant it. Then add a
rule via **Manage rules → +** for an app on the device — the app is
default-deny, so nothing is spoken until a rule exists.

**Speech and dedup** — trigger a notification from that app; confirm it's
spoken once. Trigger the same one 3× within 10s; confirm one utterance,
not three.

**Ducking** — start music, trigger a notification; confirm it ducks and
recovers. *Still unrun; needs ears.*

**Persistence** — add a rule, force-stop the app (Settings → Apps →
exigent-heron → Force stop), relaunch, reopen Manage rules; the rule
should still be listed.

**Invalid patterns** — in the editor, enter an unbalanced paren
(`(unclosed`) or a backreference (`\1`) and save; confirm an inline error
and no save, rather than a crash later when a notification arrives.

**App picker** — tap "Choose apps"; the installed-app list should be
non-empty. An empty list on a real device would mean the `<queries>`
declaration isn't doing its job.

**Headset-only** — with it on (the default), disconnect any headset and
trigger a notification; nothing should be spoken. Reconnect and try
again. *Verified on the emulator 2026-09-07.*

**Engine picker** — Settings → Choose engine, pick a different installed
engine, trigger a notification; confirm the new voice. *Still unrun.*

**Burst collapse** — ten notifications in five seconds should produce one
"10 new notifications." utterance. Covered by a JVM test; the on-device
version is this.

**Locked / in-call / work profile** — Phase 5's matrix. Locked and
in-call were confirmed on the emulator 2026-09-07; work profile only
against a non-DPC secondary profile. Exact repro steps are in
`SECURITY.md` §4. When a physical device is finally used, record the
outcome in both `SECURITY.md` and [status.md](status.md), not just one.

## Checking the manifest

Phase 0's criterion — confirm `INTERNET` is absent, don't assume it:

```sh
nix develop --command gradle :app:processDebugMainManifest
```

Then **read the merged manifest directly** at
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`.
Task success does not prove the permission is absent — a merge can
succeed with an unwanted permission pulled in from a dependency, which is
exactly what this check is for.

## Detecting an OEM kill

No code change is needed to detect one; this is a "run it for a day on
real hardware and read the log" task. Tracked as issue #27.

1. Install the debug build on a **physical device** — the emulator does
   not exercise real OEM battery optimization. Grant notification access
   and add at least one enabled rule, so the listener has a reason to
   stay bound.
2. Leave the device alone, unplugged from a debugger, for a day of normal
   use.
3. Look for a `listener disconnected` line with no `listener connected`
   soon after — a deliberate rebind reconnects almost immediately; a kill
   either doesn't reconnect or only does so on the next
   notification/reboot:

   ```sh
   adb logcat -s ExigentHeron -d | grep -i listener
   ```

4. If a kill shows up: per `AGENTS.md` §8 the fix is **not** a wakelock,
   a foreground-service notification, or any other keep-alive trick. Walk
   the user to that OEM's battery-optimization exemption screen
   (Settings → Apps → exigent-heron → Battery → Unrestricted; Samsung and
   Xiaomi are the usual culprits) and document that as the fix. Record
   the outcome on issue #27.

## See also

- [testing-4llm.md](testing-4llm.md) — the dense companion: what each
  suite does and does not prove, and the debug injector.
- [status.md](status.md) — what has actually been run.
- [architecture.md](architecture.md) — what the code does.
