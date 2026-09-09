# Installation runbook — putting this on a phone

Personal sideload instructions, not a wiki page: this doesn't track build
phases or get linted by `check_wiki.py`, and it isn't kept in sync the way
`wiki/testing.md` is. For build/test *development* workflow (unit tests,
emulator, instrumented tests) see [wiki/testing.md](wiki/testing.md)
instead — this file is specifically "I want this app on my phone."

## 1. Prerequisites (build machine)

```sh
direnv allow     # or: nix develop
```
Pins JDK 21, Gradle 9.7.1, Kotlin, and Android SDK/build-tools 37.0.0 — no
separate Android Studio install needed. No `gradlew` is committed; `gradle`
comes from the dev shell's `PATH`.

## 2. Decide: debug or signed release build

- **Debug** (`applicationId` suffix `.debug`) — fastest, self-signed with
  the Android debug key, fine for personal sideloading. Includes
  `FakeNotifications`, a dev-only test injector (harmless — it's not
  reachable without deliberately calling it, and it's absent from release
  builds by construction; see `SECURITY.md` §2).
- **Release** — smaller (minified + shrunk), no debug scaffolding, but
  **requires your own keystore**: none is committed, by design (`AGENTS.md`
  §5).

If going release, generate a keystore once and keep it **off this repo**:
```sh
keytool -genkeypair -v -keystore ~/keys/exigent-heron-release.jks \
  -alias exigentheron -keyalg RSA -keysize 2048 -validity 10000
```
Back this keystore up somewhere durable (password manager + an offline
copy). Losing it means a future update can't be installed over the
existing app — Android refuses to update an app with a differently-signed
APK, so you'd need to uninstall (losing locally-stored rules; nothing
syncs, see §7) and reinstall fresh.

## 3. Build the APK

```sh
# debug:
nix develop --command gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# release (signed) — env vars feed the signing config in app/build.gradle.kts:
export RELEASE_STORE_FILE=~/keys/exigent-heron-release.jks
export RELEASE_STORE_PASSWORD='...'
export RELEASE_KEY_ALIAS=exigentheron
export RELEASE_KEY_PASSWORD='...'
nix develop --command gradle :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```
`just build` also works (debug only — see `.justfile`).

Run `just lint` and `just test` first if you want the same checks CI runs
— not required, but catches issues before they're on your phone.

## 4. Get the APK onto the phone

- **USB + adb**: `adb install app/build/outputs/apk/<variant>/app-<variant>.apk`
- **adb over Wi-Fi**: `adb pair <ip>:<port>`, then `adb connect`, then the
  same `adb install`
- **Manual sideload**: copy the APK to the phone (USB file transfer, or a
  local file share you control — not a public link; this APK should stay
  private) and open it from a file manager

## 5. Allow the install

Android blocks the install until you allow it for that source: Settings →
Apps → *(whichever app you used to open the APK, e.g. Files)* → **Install
unknown apps** → toggle on for that one app. Turn it back off afterward if
you'd rather not leave it enabled generally.

## 6. First launch — grant only what's needed

1. Open exigent-heron. It shows **"Notification access: not granted"** —
   tap **Enable notification access**, find exigent-heron in the system
   list, and turn it on. This is the permission that actually matters:
   granting it lets the app see the content of every notification on the
   phone (that's inherent to `NotificationListenerService`, not a bug in
   this app) — it just never persists or transmits that content beyond
   its own rules/TTS pipeline (no `INTERNET` permission exists in the
   manifest at all).
2. **Don't** grant Bluetooth unless you want per-device output routing —
   it's off by default, and it's the app's only other runtime permission,
   requested only if you deliberately flip that setting on in Settings.
3. In the app's own **Settings**, check `headsetOnly` and
   `respectLockState` — both default **on**, so the app stays silent until
   you deliberately relax them. Leave them on until you've confirmed
   behavior with a rule or two.
4. Add your first rule under **Manage rules** (per-app allow, optional
   content regex, action) — the app is default-deny, so nothing is spoken
   until a rule exists.

## 7. After a normal day of use, sanity-check it

```sh
adb logcat -s ExigentHeron
```
You should see only `decision`/`lifecycle` lines — package names, rule
ids, actions (`Speak`/`AnnounceOnly`/`suppress`) — never a notification
title or body. If actual message content ever shows up in that stream,
stop using the build and report it rather than continuing. (An unscoped
`adb logcat` would show notification-shaped content from *every* app on
the device, not just this one — always scope to `-s ExigentHeron`; see
[wiki/testing.md](wiki/testing.md#watching-logcat-safely).)

## 8. Updating later

Rebuild the same way and reinstall (`adb install -r ...` for an in-place
update). A release build **must** be signed with the same keystore every
time (§2) — a signature mismatch fails the update rather than silently
downgrading anything.

## See also

- [`SECURITY.md`](SECURITY.md) — the hardening checklist this runbook's
  security posture claims are drawn from, with the commands that were
  actually run to verify each one.
- [`wiki/testing.md`](wiki/testing.md) — the development-workflow build/test
  guide (emulator, unit/instrumented tests, manual QA checklist).
