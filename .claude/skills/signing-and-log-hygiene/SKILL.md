---
name: signing-and-log-hygiene
description: How to avoid printing release signing credentials or live notification content into the conversation in this repo, hook-enforced where the pattern is checkable, and what to do when one leaks anyway.
---

# Handling signing credentials and logcat without leaking them

## Applies to

Any command that touches the release keystore or its credentials
(`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
`RELEASE_KEY_PASSWORD` — see `app/build.gradle.kts` and AGENTS.md §5), or
that runs `adb logcat` against a device or emulator with this app installed.

## Why this exists

This repo has two hard rules already written down in AGENTS.md, not
invented here:

- §5: "Never commit a keystore or password" — signing reads from env vars
  only.
- §8 (definition of done): "Zero notification content in logcat under
  `adb logcat | grep <appid>` during a full day" — the whole reason
  `SafeLog` (§4.6) exists is to make it structurally impossible for the app
  to log a notification body. `BUILD_PLAN.md`'s own per-phase acceptance
  criteria carry the same bar for the on-device checks in Phases 2 and 5.

Both are about content that must never end up somewhere it can spread
further. A Claude session quoting either one into the conversation
transcript is exactly that: a real signing password, or a real
notification's title/body from Elly's own phone, now sitting in a place
neither AGENTS.md rule was written to allow. The risk isn't hypothetical —
it's the same class of accident nixos-configs' `secrets-hygiene` skill
documents for its own secrets (a bare `sops -d` printing an entire secrets
file when only an exit code was needed): a command that answers the
question you actually had ("is this set?", "did the app leak?") by
printing far more than needed.

## Enforced mechanically, not just by memory

Two hooks in `.claude/settings.json` (project-scoped, committed, applies
every session in this repo):

- **`.claude/hooks/signing-guard-pretooluse.sh`** (`PreToolUse`, `Bash`
  matcher) — pattern-matches the command before it runs. Triggers
  `permissionDecision: "ask"` on: a literal (non-`$VAR`) keystore password
  passed to `-storepass`/`-keypass`, an `echo`/`printf` that expands a
  `RELEASE_*` credential directly, a bare unfiltered `env`/`printenv`, or
  an `adb logcat` with no scoping (`-s`, `--pid`, or a filter).
- **`.claude/hooks/signing-guard-posttooluse.sh`** (`PostToolUse`, `Bash`
  matcher) — scans the command's actual output afterward for a literal
  `RELEASE_STORE_PASSWORD=`/`RELEASE_KEY_PASSWORD=`/`RELEASE_KEY_ALIAS=`
  value or a PEM private-key block, regardless of which command produced
  it. A hit blocks and tells the model to name the leak rather than quote
  it.

What the hooks can't do: recognize notification *content* by shape the way
they can recognize a password or a PEM block — there's no syntax to grep
for in "New message from Alex: see you at 7". That risk is handled at the
source instead: scope `adb logcat` to this app's own tag/package before
running it, per the pre-hook's suggestion, rather than trying to catch it
after the fact.

## If one leaks anyway

1. **Stop before quoting or summarizing it further.** Don't re-paste the
   value "for clarity" or include it in a fix description.
2. **Tell Elly directly** that it leaked and where (which command, which
   credential or which notification).
3. **Recommend rotation** for a signing credential — a new keystore
   password, or regenerating the keystore itself if the file path or its
   contents were exposed, not just this app's env vars. A leaked
   notification body has no "rotate" step; the fix is making sure it
   doesn't get repeated further (don't log it, don't paste it back into a
   commit message or issue).
