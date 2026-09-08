#!/usr/bin/env bash
# Every check this repo knows how to run, plus the emulator lifecycle the
# device suites need. `.justfile` is the interface; this is where the
# conditionals and the reasoning live.
#
# Assumes the dev shell's PATH (gradle, adb, emulator) — `nix develop`,
# or direnv having done it for you. It says so rather than guessing if a
# tool is missing.
#
# The device suites are the reason this exists. Running them by hand
# means knowing to boot an AVD, knowing that `adb devices` reporting a
# device is not the same as that device being *booted*, and remembering
# to shut down afterwards only if you were the one who started it. All
# three are handled below.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

AVD="${AVD:-dev}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"
# Deliberately not $TMPDIR: inside `nix develop` that is a per-shell
# directory removed when the shell exits, so the one log you would want
# to read after a failed run is gone by the time you look. build/ is
# gitignored, discoverable, and cleaned by `gradle clean` like everything
# else here.
EMULATOR_LOG="$PWD/build/emulator.log"
BOOT_GRACE=60
SYSTEM_IMAGE="system-images;android-37.0;google_apis;x86_64"
DOMAIN=app/src/main/java/net/breadthcharge/exigentheron/domain
STARTED_EMULATOR=""
DEVICE_READY=""

say()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }

need() {
    command -v "$1" >/dev/null 2>&1 ||
        fail "$1 is not on PATH — run inside 'nix develop' (or let direnv do it)"
}

# ---------------------------------------------------------------- device

device_attached() { [ -n "$(adb devices | sed -n '2p')" ]; }

# `adb devices` lists a device the moment the emulator process registers,
# long before Android is usable — installing onto one that isn't booted
# fails in ways that look like a broken build rather than a race.
#
# Deliberately not `adb wait-for-device`: that blocks forever with no
# timeout, so an emulator that starts and never registers hangs the whole
# run with no output. Caught doing exactly that — eleven minutes of
# silence on a qemu process that was alive but never came up. This polls
# instead, and gives up with the emulator's own log rather than a bare
# timeout message.
await_boot() {
    local waited=0
    while [ "$waited" -lt "$BOOT_TIMEOUT" ]; do
        if device_attached &&
            [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
            return 0
        fi
        # Only after a grace period: the qemu process does not exist for
        # the first moments, and there is nothing to conclude from that.
        if [ -n "$STARTED_EMULATOR" ] && [ "$waited" -ge "$BOOT_GRACE" ] && ! emulator_running; then
            emulator_log_tail
            fail "the emulator exited before finishing boot"
        fi
        sleep 5
        waited=$((waited + 5))
    done
    emulator_log_tail
    fail "no booted device after ${BOOT_TIMEOUT}s"
}

emulator_log_tail() {
    [ -s "$EMULATOR_LOG" ] || return 0
    echo "--- last 20 lines of $EMULATOR_LOG ---" >&2
    tail -20 "$EMULATOR_LOG" >&2
}

# Matches the qemu process by its own -avd argument rather than tracking
# `$!`. The `emulator` binary is a launcher that spawns qemu and can exit
# on its own, so `$!` reports the launcher: `kill -0` on it returned
# false while the emulator was still very much running, and teardown
# declared success on a live emulator. Caught exactly that.
emulator_running() { pgrep -f "qemu-system.*-avd $AVD" >/dev/null 2>&1; }

stop_emulator() {
    [ -n "$STARTED_EMULATOR" ] || return 0
    say "stopping the emulator this script started"
    adb emu kill >/dev/null 2>&1 || true
    # `adb emu kill` is a request, not a guarantee.
    for _ in $(seq 1 20); do
        emulator_running || return 0
        sleep 1
    done
    pkill -f "qemu-system.*-avd $AVD" 2>/dev/null || true
    # SIGTERM is not instant either — confirm it actually went, so the
    # next run doesn't inherit the AVD's lock files from a process that
    # was still shutting down when this returned.
    for _ in $(seq 1 15); do
        emulator_running || return 0
        sleep 1
    done
    echo "warning: an emulator for '$AVD' is still running" >&2
}

ensure_device() {
    # Idempotent: `device` runs two suites that each want a device, and
    # neither should re-check or re-boot one.
    [ -z "$DEVICE_READY" ] || return 0
    need adb
    if device_attached; then
        say "using the device already attached"
        await_boot
        DEVICE_READY=1
        return 0
    fi

    need emulator
    need avdmanager
    if ! avdmanager list avd 2>/dev/null | grep -q "Name: $AVD$"; then
        say "creating the '$AVD' AVD (the dev shell ships the system image)"
        echo no | avdmanager create avd -n "$AVD" -k "$SYSTEM_IMAGE" -d pixel_6 >/dev/null
    fi

    mkdir -p "$(dirname "$EMULATOR_LOG")"
    say "booting the '$AVD' emulator (log: $EMULATOR_LOG)"
    # Headless: this is meant to run unattended, including over ssh.
    # Output goes to a file rather than /dev/null so a failure to boot is
    # diagnosable — discarding it is what made the hang above opaque.
    emulator -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect >"$EMULATOR_LOG" 2>&1 &
    STARTED_EMULATOR=1
    # Only ever kills an emulator this script started — a device you were
    # already using, or an emulator you booted yourself to watch, is left
    # alone.
    trap stop_emulator EXIT
    await_boot
    DEVICE_READY=1
}

# ----------------------------------------------------------------- suites

suite_unit() {
    need gradle
    say "JVM unit tests"
    gradle testDebugUnitTest
}

suite_build() {
    need gradle
    say "debug build"
    gradle assembleDebug
}

suite_lint() {
    need gradle
    say "Android Lint (both variants, as CI runs them)"
    gradle lintDebug lintRelease
}

# The two structural rules CI enforces by grep. Kept here as well as in
# check.yml so a violation is catchable before pushing, not only after —
# the same reasoning that puts wiki-lint in front of a PR.
suite_structure() {
    say "structural rules"

    local log_files
    log_files=$(grep -rl 'android\.util\.Log' app/src --include='*.kt' | wc -l)
    [ "$log_files" -eq 1 ] ||
        fail "android.util.Log is referenced in $log_files files; exactly one (SafeLog.kt) is allowed"
    echo "  android.util.Log confined to one file"

    local android_imports
    android_imports=$(grep -rln '^import android\.' "$DOMAIN" --include='*.kt' || true)
    [ -z "$android_imports" ] ||
        fail "domain/ must have zero Android imports; found them in: $android_imports"
    echo "  domain/ has no Android imports"
}

suite_wiki() {
    need python3
    say "wiki claims"
    python3 wiki/scripts/check_wiki.py check
}

suite_instrumented() {
    need gradle
    ensure_device
    say "instrumented tests"
    gradle connectedDebugAndroidTest
}

suite_acceptance() {
    ensure_device
    say "listener acceptance"
    ./scripts/listener-acceptance.sh
}

suite_device() {
    ensure_device
    suite_instrumented
    suite_acceptance
}

# Everything CI runs, then everything CI can't. Ordered cheapest-first so
# a structural violation or a failing unit test stops the run before the
# several minutes an emulator costs.
suite_all() {
    suite_structure
    suite_wiki
    suite_build
    suite_unit
    suite_lint
    suite_device
}

usage() {
    cat <<'USAGE'
usage: scripts/test.sh <suite>

  unit          JVM unit tests — no device needed
  build         assembleDebug
  lint          Android Lint, debug and release
  structure     the two rules CI greps for
  wiki          check_wiki.py
  instrumented  androidTest on a device (boots an emulator if none is attached)
  acceptance    the listener end-to-end script (same)
  device        instrumented + acceptance
  all           everything, cheapest first

AVD=<name> picks a different emulator image (default: dev).
BOOT_TIMEOUT=<seconds> bounds the wait for it to boot (default: 600).
USAGE
}

case "${1:-}" in
    unit)         suite_unit ;;
    build)        suite_build ;;
    lint)         suite_lint ;;
    structure)    suite_structure ;;
    wiki)         suite_wiki ;;
    instrumented) suite_instrumented ;;
    acceptance)   suite_acceptance ;;
    device)       suite_device ;;
    all)          suite_all ;;
    -h|--help)    usage; exit 0 ;;
    "")           usage >&2; fail "no suite given" ;;
    *)            usage >&2; fail "unknown suite: $1" ;;
esac

say "PASS: ${1}"
