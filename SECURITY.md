# Security & Hardening Checklist — Phase 5

Evidence-gathering for `BUILD_PLAN.md` Phase 5 and `AGENTS.md` §8's
"Definition of done." Every item below is either **[x] ticked with
evidence** (a command actually run this session, and what it printed or
what file it produced — not an assertion) or **[ ] pending device
access**, with the exact manual steps to complete it once a device is
available. Nothing here is marked done without having been run.

Session context: sandboxed, no Android device or emulator attached.
Confirmed with `adb --version` — `adb: command not found` — and the task
brief's own expectation. All device-only items below are marked pending
accordingly; do not treat this file's "pending" items as a promise they
were tested and passed.

Build environment used for everything runnable here: `nix develop`
(pins JDK 21, Kotlin, Gradle 9.7.1, and the Android SDK — see
`flake.nix`), then `gradle <task>` — no `gradlew` in this repo, see
`README.md`.

---

## 1. Logging violations grep (`AGENTS.md` §4.6)

**[x]** `android.util.Log` appears in exactly one file.

```
$ grep -rn "android.util.Log" app/src/
app/src/main/java/net/breadthcharge/exigentheron/SafeLog.kt:3:import android.util.Log
app/src/main/java/net/breadthcharge/exigentheron/SafeLog.kt:6: * The only permitted entry point to [android.util.Log] in this codebase —
app/src/main/java/net/breadthcharge/exigentheron/SafeLog.kt:12: * CI (or a pre-commit grep) should assert `android.util.Log` appears in
```

Only one path (`SafeLog.kt`) shows up under `grep -l`; the other two
hits in the output above are its own doc comment.

**[x]** The CI grep §4.6 asks for already exists —
`.github/workflows/check.yml`'s "assert android.util.Log usage is
confined to one file" step (added 2026-09-05 per
`wiki/open-threads.md`), which runs the same `grep -rl` on every PR and
push to `main` and fails the build if the hit count isn't exactly 1. No
new mechanism added this phase — a second one would just be a second
copy that could drift from the first. Confirmed by reading the workflow
file directly rather than trusting the wiki's account of it.

**[x]** `SafeLog`'s API surface has no free-text logging method. Read
`app/src/main/java/net/breadthcharge/exigentheron/SafeLog.kt` in full:
it exposes exactly

```kotlin
fun decision(pkg: String, ruleId: String?, action: String)
fun lifecycle(msg: String)
fun error(msg: String, t: Throwable? = null)
```

No `SafeLog.d(String)` or equivalent taking arbitrary text. `lifecycle`
and `error` take a `msg: String`, but every call site was grepped (next
item) and none passes notification title/body — they're all static
strings or ids.

**[x]** Grepped every call site of `SafeLog` and every other
`Log.d/e/w/i/v`/`println`/`print(` in `app/src/`:

```
$ grep -rn "Log\.\(d\|e\|w\|i\|v\)\|println\|print(" app/src/ | grep -v SafeLog.kt
app/src/main/java/net/breadthcharge/exigentheron/AppContainer.kt:64:
    onRuleFailure = { id, reason -> SafeLog.error("rule $id failed: $reason") },
app/src/main/java/net/breadthcharge/exigentheron/listener/NotificationTtsListener.kt:60,68,72:
    SafeLog.decision(payload.packageName, ruleId = null, action = "...")
app/src/main/java/net/breadthcharge/exigentheron/speech/AndroidTtsEngine.kt:43,54,80:
    SafeLog.error(reason) / SafeLog.error("TTS utterance error: id=$utteranceId code=$errorCode")
app/src/main/java/net/breadthcharge/exigentheron/speech/SpeechQueue.kt:79:
    SafeLog.error("speech queue: utterance failed", e)
```

Every hit is a `SafeLog.*` call (there is no other `Log.*`/`println`
call anywhere in `app/src/`, confirmed by the empty complement of the
grep above); every argument is a package name, a rule id, a decision
label, a TTS utterance id/error code, or a static string — never
`payload.title`/`payload.body`/`NotificationPayload` itself. This is
also structurally reinforced by item below: `NotificationPayload` has no
`toString()` that would leak content even if someone did try to
interpolate the whole object into a log call.

**[x]** `NotificationPayload.toString()` test exists and passes (also
covered under §3 below): `NotificationPayloadTest.kt`, one test,
asserting the rendered string contains neither a title marker nor a
body marker. Ran as part of the full suite (§3), green.

No violations found; no fix needed for this item.

---

## 2. Debug injector isolation (`AGENTS.md`/`BUILD_PLAN.md` Phase 1)

**[x]** `FakeNotifications` lives at
`app/src/debug/java/net/breadthcharge/exigentheron/debug/FakeNotifications.kt`
— the `debug/` source set, AGP's default convention (no custom
`sourceSets {}` block in `app/build.gradle.kts` — confirmed by reading
the file in full; AGP wires `src/main`, `src/debug`, `src/release` by
directory name with no configuration needed, so isolation is structural,
not a setting someone could accidentally remove).

**[x]** Compiled the release variant and inspected its class output
directly rather than trusting the source-set convention alone:

```
$ nix develop --command gradle :app:compileReleaseKotlin
BUILD SUCCESSFUL in 11s

$ find app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes -iname "*.class" | wc -l
139
$ find app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes -path "*debug*"
(no output)
$ find app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes -iname "*Fake*"
(no output)
```

139 classes in the release compile output, none under a `debug` package,
no `FakeNotifications` class present. For comparison, the debug variant's
own compile output does contain it:

```
$ find app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes -iname "*Fake*"
app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/net/breadthcharge/exigentheron/debug/FakeNotifications.class
```

`gradle assembleRelease`/`bundleRelease` were not run — no signing
config exists in this sandbox (`RELEASE_STORE_FILE` etc. are unset env
vars per `app/build.gradle.kts`'s `hasReleaseSigning` check, and that's
correct behavior per `AGENTS.md` §5, not a bug) — but
`compileReleaseKotlin` is the task that actually decides which Kotlin
sources end up as classes in that variant, and it proves the point
without needing a keystore: the debug source set's code is provably
absent from what release compiles.

---

## 3. Merged release manifest (`AGENTS.md` §5)

**[x]** Ran the release-manifest merge task and read its actual output
(not the source `AndroidManifest.xml` from memory):

```
$ nix develop --command gradle :app:processReleaseMainManifest
BUILD SUCCESSFUL in 1s
```

Merged file:
`app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
Relevant excerpts:

```xml
<uses-sdk android:minSdkVersion="31" android:targetSdkVersion="37" />

<!-- no <uses-permission android:name="android.permission.INTERNET"/> anywhere in the file -->

<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="false"
    ...>

    <activity
        android:name="net.breadthcharge.exigentheron.ui.MainActivity"
        android:exported="true"
        android:filterTouchesWhenObscured="true">
        <intent-filter>...MAIN/LAUNCHER...</intent-filter>
    </activity>

    <service
        android:name="net.breadthcharge.exigentheron.listener.NotificationTtsListener"
        android:exported="true"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
        <intent-filter>...NotificationListenerService...</intent-filter>
    </service>

    <provider android:name="androidx.startup.InitializationProvider" android:exported="false">...</provider>

    <receiver
        android:name="androidx.profileinstaller.ProfileInstallReceiver"
        android:exported="true"
        android:permission="android.permission.DUMP">
        ...INSTALL_PROFILE / SKIP_FILE / SAVE_PROFILE / BENCHMARK_OPERATION...
    </receiver>
</application>
```

Checklist against `AGENTS.md` §5, item by item:

- **`INTERNET` absent** — confirmed, not present anywhere in the merged
  file.
- **`allowBackup="false"`** — confirmed.
- **`fullBackupContent="false"`** — confirmed.
- **`dataExtractionRules` present and excludes everything** — confirmed
  attribute present; `app/src/main/res/xml/data_extraction_rules.xml`
  read directly: both `<cloud-backup>` and `<device-transfer>` exclude
  `domain="root"` (everything).
- **Listener service is the only exported non-launcher app component,
  with `BIND_NOTIFICATION_LISTENER_SERVICE`** — true for every component
  this app's own source declares. One exception found and worth
  recording rather than glossing over: **`androidx.profileinstaller.
  ProfileInstallReceiver`** is merged in by an AndroidX library (not this
  app's manifest, not this app's code — it doesn't appear in
  `app/src/main/AndroidManifest.xml` at all) with `exported="true"`. It
  is gated by `android:permission="android.permission.DUMP"`, a
  signature|privileged-level system permission no third-party app can
  hold, and its intent-filter actions
  (`INSTALL_PROFILE`/`SKIP_FILE`/`SAVE_PROFILE`/`BENCHMARK_OPERATION`)
  are ahead-of-time compilation profile plumbing, not a text-accepting
  API — it does not match the shape §5 warns against ("a
  `BroadcastReceiver` that accepts text to speak"). Standard for any app
  using `androidx.profileinstaller`/`androidx.startup` (pulled in
  transitively by Compose/`core-ktx`); not something this app added or
  can remove without dropping those dependencies. No fix applied — it's
  not the violation §5 is aimed at, but it's the honest answer to "is
  every component other than the listener `exported=false`," so it's
  recorded here rather than silently excluded from the count.
- **Every other component `exported="false"`** — `androidx.startup.
  InitializationProvider` is `exported="false"`, confirmed. No other
  provider, service, or receiver in the merged file besides the four
  listed above.
- **No `BroadcastReceiver` accepting arbitrary text** — grepped
  `app/src/main/AndroidManifest.xml` and the merged release manifest for
  `<receiver`: only the AndroidX one above exists, and it doesn't accept
  arbitrary text (see above). This app declares zero receivers of its
  own.
- **`isMinifyEnabled`/`isShrinkResources` true for release** — read
  directly from `app/build.gradle.kts`'s `release {}` block: both `true`.

No violations found; no fix needed for this item.

---

## 4. Locked-device / in-call / work-profile tests (`BUILD_PLAN.md` Phase 5)

**[ ] Not run this session — needs a physical device.** No emulator or
device is attached to this sandbox (`adb` itself isn't installed here).
Manual steps for whoever has a device, referencing the actual gates in
this codebase rather than a generic script:

**Locked-device test** (`speech/LockStateGate.kt`, wired in
`AppContainer.lockStateGate`, checked in
`listener/NotificationTtsListener.kt`'s `route()` before
`SpeechQueue.enqueue()`):
1. Confirm Settings → "Don't speak while locked" is on (defaulted on).
2. Lock the device (power button / timeout).
3. Trigger a notification from an allowlisted app.
4. Confirm nothing is spoken. Unlock and trigger another; confirm it now
   speaks. `LockStateGateTest.kt` already covers the pure decision logic
   (`respectLockState() && isKeyguardLocked()` ⇒ suppress) on the JVM —
   this step is only to confirm `KeyguardManager.isKeyguardLocked()`
   itself reports correctly on a real locked device, which no JVM test
   can do.

**In-call test** (the `isInCall` check built into
`AppContainer.createSpeechQueue`, consumed by `SpeechQueue`):
1. Start or receive a phone call (or a VoIP call that puts
   `AudioManager.mode` into `MODE_IN_COMMUNICATION`).
2. While the call is active, trigger a notification from an allowlisted
   app.
3. Confirm nothing is spoken during the call, and that a notification
   triggered immediately after hanging up does speak. `SpeechQueueTest.kt`
   covers the pure skip-when-`isInCall`-is-true logic already; this step
   confirms `AudioManager.mode` actually reports `MODE_IN_CALL`/
   `MODE_IN_COMMUNICATION` during a real call on real hardware.

**Work-profile test:** `AGENTS.md` doesn't specify any work-profile-
specific behavior beyond "this should still work" — there's no separate
work-profile code path to verify, so there's no feature to invent a test
for here. Honest manual step: on a device with a work profile
provisioned, confirm the app installs into the personal profile, the
notification-listener grant flow in `MainActivity` completes normally,
notifications from personal-profile apps are read as they are on any
other device, and the app doesn't crash or misbehave due to the work
profile's presence (e.g. `PackageManager.queryIntentActivities` in the
installed-app picker still returning a sane list rather than erroring on
cross-profile visibility rules). No claim beyond "installs and behaves
normally with a work profile present" is being tested, because no more
specific claim exists in the spec to test.

---

## 5. `AGENTS.md` §8 "Definition of done" — remaining items

- **Speaks the right notifications, once each, no duplicates over a
  normal day** — `Deduplicator`'s five required cases (§4.3) are all
  covered by `DeduplicatorTest.kt` (6 tests, all passing — see §3 grep
  above / full run below). The "normal day of use" half is a duration
  claim no unit test can make; see the OEM-killing item below for the
  same limitation applied to uptime specifically.
- **Never speaks an OTP** — `SecretDetectorTest.kt`, 13 tests, all
  passing: covers a bank OTP, a delivery code, a normal message with a
  year, a normal message with a price (the four §4.5 cases), plus the
  hardcoded floor ("never speak a bare 6-digit number as the entire
  body") and the `VISIBILITY_PRIVATE`/`VISIBILITY_SECRET` downgrade.
- **Never speaks when the headset gate or lock gate says no** — covered
  structurally in §3/§4 above (`OutputRouteGateTest.kt` 8 tests,
  `LockStateGateTest.kt` 3 tests, both passing on the JVM); the on-device
  half is §4's pending item.
- **Survives a day without being killed, or fails visibly if the OEM
  kills it** — cannot be verified from this sandbox at all: it requires
  real elapsed time on real hardware, which no build task or unit test
  can substitute for. Per `AGENTS.md`'s own closing paragraph, the
  actionable item if this turns out to fail on a real device is: **do
  not** fight it with wakelocks or a foreground-service notification —
  instead, walk the user to the OEM's battery-optimization exemption
  screen for this app (Settings → Apps → exigent-heron → Battery →
  Unrestricted, wording varies by OEM — Samsung and Xiaomi are the
  specifically-named usual culprits) and document that as the fix,
  rather than adding a persistent-notification/wakelock workaround this
  spec doesn't call for.
- **`INTERNET` absent from the release manifest** — done, see §3.
- **Zero notification content in logcat** — structural argument covered
  in full in §1; not re-derived here. The device-side half (`adb logcat
  | grep <appid>` during a real day of use) is the same category as the
  uptime item above — nothing to run without a device over a day, and
  `wiki/testing.md`'s "Watching logcat safely" section already documents
  the safe way to do that check once a device is available (`adb logcat
  -s ExigentHeron`, not an unscoped `adb logcat`).
- **Unit tests pass and cover the pure domain layer** — re-ran the full
  suite this session, after the phase's own investigation (no source
  changes were made — see "What was found" below):

  ```
  $ nix develop --command gradle testDebugUnitTest --rerun-tasks
  BUILD SUCCESSFUL in 17s
  24 actionable tasks: 24 executed
  ```

  86 tests, 0 failures, 0 errors, 0 skipped across 14 test classes
  (counted from the JUnit XML under
  `app/build/test-results/testDebugUnitTest/`):
  `InterruptibleCharSequenceTest` (3), `DeduplicatorTest` (6),
  `ContentHashTest` (3), `SpeechQueueTest` (8),
  `NotificationPayloadTest` (1), `OutputRouteGateTest` (8),
  `TextSanitizerTest` (6), `NotificationExtractionPolicyTest` (7),
  `RuleValidatorTest` (6), `SecretDetectorTest` (13), `RuleCodecTest` (8),
  `RuleEngineTest` (11), `LockStateGateTest` (3), `RuleEngineHolderTest`
  (3). All of `domain/`'s pure classes (`NotificationPayload`,
  `Deduplicator`, `RuleEngine`, `SecretDetector`, `RuleValidator`,
  `RuleCodec`, `RuleEngineHolder`, `TextSanitizer`, `ContentHash`,
  `InterruptibleCharSequence`) have a dedicated test class; the two
  Android-facing classes with logic worth testing off-device
  (`SpeechQueue`, the gates) do too.
- **`SECURITY.md` checklist complete** — this file.

## What was found and fixed this phase

Nothing. Every grep, manifest read, and build task above came back
clean on the first run — no logging violation, no exported component
beyond the documented listener service and the standard AndroidX
receiver, no debug code reachable from release. No source changes were
made in this phase; this file and the `wiki/` updates alongside it are
the only diff.
