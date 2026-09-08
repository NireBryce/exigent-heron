#!/usr/bin/env bash
# Phase 2's listener acceptance criteria (wiki/testing.md), minus the human.
#
# What it automates: granting notification access, posting real
# notifications through the platform, and reading back what the listener
# decided. What it cannot automate is the part that needs ears — that a
# duck-and-recover actually sounded right, or that speech came out of the
# headset rather than the speaker. Those stay in wiki/testing.md.
#
# It deliberately asserts only on SafeLog.decision lines, which carry a
# package, a rule id, and an action — never notification content. Scoping
# logcat to the app's own tag is the same rule skill signing-and-log-hygiene
# states for running it by hand: a bare `adb logcat` captures
# notification-shaped text from every app on the device.
set -euo pipefail

PKG=net.breadthcharge.exigentheron.debug
LISTENER=net.breadthcharge.exigentheron.listener.NotificationTtsListener
# `cmd notification post` posts as the shell package, not as this script.
SOURCE_PKG=com.android.shell
TAG=ExigentHeron

fail() { echo "FAIL: $*" >&2; exit 1; }

[ -n "$(adb devices | sed -n '2p')" ] || fail "no device or emulator attached"

echo "==> installing the debug build"
gradle installDebug >/dev/null

echo "==> granting notification access (no UI tap needed)"
adb shell cmd notification allow_listener "$PKG/$LISTENER" >/dev/null
# Read the grant back from the secure setting rather than from a
# `cmd notification` subcommand: `allow_listener` exists on API 31+, but
# there is no matching query subcommand on every level (API 37 has none),
# whereas this setting is where the grant actually lands.
#
# Polled, not read once: the write is asynchronous, and reading it
# immediately after `allow_listener` returns is a race this script lost
# on its second consecutive run.
granted=0
for _ in $(seq 1 15); do
    if adb shell settings get secure enabled_notification_listeners | tr ':' '\n' | grep -q "$PKG"; then
        granted=1
        break
    fi
    sleep 1
done
[ "$granted" -eq 1 ] || fail "notification access was not granted"

# The listener only binds once the app has been launched at least once.
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3

# Each phase clears the log buffer and then counts, rather than filtering
# by timestamp: `logcat -t <time>` matches against the *device's* clock,
# which need not agree with this host's, and a silently-empty window
# looks exactly like a listener that never fired.
reset_log() { adb logcat -c; }
decision_count() {
    adb logcat -d -s "$TAG" 2>/dev/null | grep -c "decision pkg=$SOURCE_PKG" || true
}

post() { adb shell cmd notification post -S bigtext -t "$1" "$3" "$2" >/dev/null; }

# Wait for the count to reach $1 and then hold steady, rather than
# sleeping a fixed interval and hoping. The listener runs its routing on
# a background coroutine, and the OS can take its time rebinding the
# service after a cached-process kill, so a fixed sleep is the difference
# between this script being trustworthy and being intermittently wrong.
# Holding steady afterwards is what catches an over-count (a duplicate
# that was not collapsed) rather than only an under-count.
await_decisions() {
    local want=$1 seen=0 stable=0
    for _ in $(seq 1 30); do
        seen=$(decision_count)
        if [ "$seen" -ge "$want" ]; then
            stable=$((stable + 1))
            [ "$stable" -ge 3 ] && break
        fi
        sleep 1
    done
    echo "$seen"
}

echo "==> a single notification reaches the pipeline once"
reset_log
post "Acceptance one" "first body" tag-single
N=$(await_decisions 1)
[ "$N" -ge 1 ] || fail "no decision logged for a posted notification"

echo "==> three identical notifications inside the dedup window collapse to one"
reset_log
for _ in 1 2 3; do post "Acceptance dup" "same body" tag-dup; sleep 1; done
N=$(await_decisions 1)
[ "$N" -eq 1 ] || fail "expected exactly 1 decision for 3 identical notifications, got $N"

echo "==> three distinct notifications are not collapsed"
reset_log
for i in 1 2 3; do post "Acceptance distinct $i" "body $i" "tag-distinct-$i"; sleep 1; done
N=$(await_decisions 3)
[ "$N" -eq 3 ] || fail "expected 3 decisions for 3 distinct notifications, got $N"

adb shell cmd notification disallow_listener "$PKG/$LISTENER" >/dev/null
echo
echo "PASS: extraction and deduplication behave as Phase 2 specifies."
echo "Still needs ears, and stays in wiki/testing.md: duck-and-recover,"
echo "headset-only routing, and the engine picker."
