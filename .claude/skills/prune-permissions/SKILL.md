---
name: prune-permissions
description: How to remove dead entries from .claude/settings.local.json's permission allowlist in this repo.
---

# Pruning `.claude/settings.local.json`

## Applies to

Use when asked to clean up, prune, or audit this repo's permission
allowlist, or as a periodic housekeeping pass. Not for *adding* entries to
reduce prompts — that's the built-in `fewer-permission-prompts` skill, which
only appends and doesn't touch what's already there.

`.claude/settings.local.json` is git-tracked despite the "local" name (check
with `git ls-files`), so pruning it is a real repo change like any other:
commit it rather than editing the working tree and leaving it uncommitted.

## Why this exists

The file only ever grows. Every tool call that needs a new permission
pattern gets one appended automatically — including patterns for commands
that were themselves part of auditing this same file. Nothing prunes it
back down on its own, so left alone it accumulates stale, never-fires-again
cruft. (Adapted from nixos-configs' `prune-permissions` skill, which found
the same pattern there: 31 entries down to 21 on its first real pass.)

## Heuristic: dead vs. standing

**Prune candidates** — each of these dies the moment the session that
created it ends, because what it names cannot recur:

- A `Bash(...)` entry with **no trailing `*`** that embeds a one-off literal
  payload — a full regex, a specific multi-command pipeline, an exact
  argument list. Permission matching here is by prefix; no wildcard means
  it only matches that exact command string again, which is not something
  anyone retypes on purpose.
- Any path under `/nix/store/<hash>-...` — content-addressed, so a new
  `nix develop`/eval gets a new hash (e.g. a new androidSdk or gradle
  derivation path from flake.nix) and the old permission never matches
  again.
- A specific file under `/tmp/` (`/tmp/diag-log.txt`, not `/tmp/**`) —
  ephemeral by construction; the file is gone once that debugging session
  ends, and even if a similarly-named file reappears it won't be the same
  one this rule was written for.

**Keep** — these are standing, reusable patterns, not frozen snapshots of
one past command:

- `WebFetch(domain:...)` entries — a domain doesn't expire.
- `Bash(<command> *)` prefix-wildcards (`gradle *`, `git commit *`, `gh pr
  *`, `adb *`) — genuinely general, matches any future invocation of that
  command shape.
- Broad `Read`/`Edit` globs (`//tmp/**`, `//nix/store/**`, a skill
  directory's `/**`) — scoped but not one-off; still useful next session.

When an entry doesn't cleanly fit either bucket, don't guess — say what it
is and ask, the same as any other judgment call worth deferring rather than
resolving by pattern-matching alone.

## Steps

1. Read `.claude/settings.local.json`.
2. Classify every entry in `permissions.allow` against the heuristic above.
3. List the prune candidates before removing anything, so the removal is
   reviewable rather than silent.
4. Rewrite the file with the dead entries removed, keeping the rest in
   their original order — don't reshuffle or "tidy" surviving entries as
   part of the same change.
5. Validate it's still well-formed JSON (`python3 -c "import json;
   json.load(open('.claude/settings.local.json'))"` or equivalent) before
   calling it done.
6. Commit it if the change is meant to stick — an uncommitted prune just
   gets overwritten by the next auto-appended entry with nothing to show
   for it.
