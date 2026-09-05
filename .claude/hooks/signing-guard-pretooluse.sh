#!/usr/bin/env bash
# PreToolUse hook (Bash matcher). Deterministic guard against printing this
# repo's two categories of sensitive material into the transcript:
#
#   1. Release signing credentials -- RELEASE_STORE_PASSWORD,
#      RELEASE_KEY_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_STORE_FILE, read from
#      env vars only (app/build.gradle.kts, AGENTS.md §5: "Never commit a
#      keystore or password").
#   2. Live notification content via adb logcat -- AGENTS.md's Phase
#      acceptance criteria require "Zero notification content in logcat"
#      (§6), and §4.6 (SafeLog) exists specifically so the app itself can't
#      log a notification body. An unscoped `adb logcat` still captures
#      every other app's and the system's own notification-related lines
#      from the whole device, which is real content from Elly's own phone,
#      not just this app's output.
#
# Adapted from nixos-configs' .claude/hooks/secrets-guard-pretooluse.sh (same
# ask-not-deny shape, applied to this repo's own secrets instead of sops).
set -euo pipefail

input=$(cat)
command=$(jq -r '.tool_input.command // empty' <<<"$input")

reason=""

# A literal (non-$VAR) value passed to a keystore CLI's password flags --
# means a real password was typed directly into the command line, not
# referenced from the env.
if grep -qE -- '-(storepass|keypass)\b[[:space:]]+[^$[:space:]]' <<<"$command" \
    || grep -qE -- '--(store-password|key-password)(=|[[:space:]]+)[^$[:space:]]' <<<"$command"; then
    reason="This passes a keystore password as a literal value rather than \$RELEASE_STORE_PASSWORD/\$RELEASE_KEY_PASSWORD -- it prints the real password into the transcript. Use the env var instead. See AGENTS.md §5."
fi

# echo/printf that directly expands one of the release signing env vars.
if [ -z "$reason" ] && grep -qE '\b(echo|printf)\b[^|;&]*\$\{?RELEASE_(STORE_PASSWORD|KEY_PASSWORD|KEY_ALIAS|STORE_FILE)\}?\b' <<<"$command"; then
    reason="This echoes a release signing credential directly into the transcript. If you need to confirm it's set, use \`[ -n \"\$RELEASE_STORE_PASSWORD\" ] && echo set\` instead of printing the value."
fi

# A bare env/printenv dump (no filtering) could include the RELEASE_* vars
# along with everything else in the shell's environment.
if [ -z "$reason" ] && grep -qE '(^|[;&|][[:space:]]*)(env|printenv)([[:space:]]|$)' <<<"$command" \
    && ! grep -qE '\|' <<<"$command"; then
    reason="A bare env/printenv dump prints every environment variable, including RELEASE_STORE_PASSWORD/RELEASE_KEY_PASSWORD if set. Filter it, e.g. \`env | grep -v RELEASE_\`, or check a single non-secret var by name instead."
fi

# adb logcat with no scoping (-s <tag>, --pid, or a grep/awk filter downstream)
# -- captures live notification-shaped content from the whole device, not
# just this app under test.
if [ -z "$reason" ] && grep -qE '\badb\b[^|;&]*\blogcat\b' <<<"$command" \
    && ! grep -qE -- '-s\b|--pid\b|-e\b' <<<"$command" \
    && ! grep -qE '\|' <<<"$command"; then
    reason="Unscoped 'adb logcat' captures live notification-shaped content from every app on the device, not just this one under test -- AGENTS.md requires zero notification content surfacing anywhere, including here. Scope it: \`adb logcat -s ExigentHeron\` (SafeLog's actual tag, per SafeLog.kt), or pipe through \`grep <appid>\`."
fi

if [ -n "$reason" ]; then
    jq -n --arg reason "$reason" '{
        hookSpecificOutput: {
            hookEventName: "PreToolUse",
            permissionDecision: "ask",
            permissionDecisionReason: $reason
        }
    }'
fi

exit 0
