#!/usr/bin/env bash
# PostToolUse hook (Bash matcher). Safety net for signing-and-log-hygiene: if
# a release signing credential or a private key shows up in a Bash tool's
# output anyway -- despite the PreToolUse guard
# (signing-guard-pretooluse.sh), or from a command shape that guard doesn't
# cover -- flag it immediately rather than relying on the model noticing
# before quoting the result. See
# .claude/skills/signing-and-log-hygiene/SKILL.md.
#
# Deliberately narrow: matches known secret *shapes* (a literal
# RELEASE_STORE_PASSWORD/RELEASE_KEY_PASSWORD/RELEASE_KEY_ALIAS value, a PEM
# private key block). Does NOT try to pattern-match "notification content"
# in logcat output -- unlike a credential, notification text has no
# recognizable shape to grep for; that risk is handled by the PreToolUse
# guard nudging toward a scoped `adb logcat` in the first place, not by
# scanning output after the fact.
set -euo pipefail

input=$(cat)
# Extract stdout/stderr as RAW text, not `tostring` on the whole object --
# tostring re-serializes to compact JSON and turns real newlines into
# literal `\n`, breaking multi-line checks.
text=$(jq -r '
    if (.tool_response | type) == "object" then
        [(.tool_response.stdout // ""), (.tool_response.stderr // "")] | join("\n")
    elif (.tool_response | type) == "string" then
        .tool_response
    else
        empty
    end
' <<<"$input" 2>/dev/null || true)

hit=""
grep -qP '\bRELEASE_(STORE_PASSWORD|KEY_PASSWORD|KEY_ALIAS|STORE_FILE)=(?!\s*$).+' <<<"$text" && hit="a release signing env var (RELEASE_STORE_PASSWORD/RELEASE_KEY_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_STORE_FILE) with its value"
[ -z "$hit" ] && grep -qE -- '-----BEGIN (OPENSSH|RSA|EC|DSA|PRIVATE) KEY-----' <<<"$text" && hit="a private key block"

if [ -n "$hit" ]; then
    jq -n --arg hit "$hit" '{
        decision: "block",
        reason: ("This tool output looks like it contains " + $hit + " in plaintext. STOP before quoting or summarizing it in your reply: refer to it by name only, tell the user it leaked, and recommend rotating the release keystore/credentials rather than continuing the original task as if nothing happened. See .claude/skills/signing-and-log-hygiene/SKILL.md."),
        hookSpecificOutput: {
            hookEventName: "PostToolUse",
            additionalContext: ("signing-and-log-hygiene guard: possible plaintext credential (" + $hit + ") detected in this tool output. Do not repeat it in your reply; name it only, and flag rotation.")
        }
    }'
fi

exit 0
