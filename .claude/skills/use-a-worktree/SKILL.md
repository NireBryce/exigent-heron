---
name: use-a-worktree
description: How to work in an isolated git worktree instead of the shared checkout in this repo.
---

# Working in your own worktree

## Applies to

The first time in a session you're about to run a git command that changes
what's checked out or what a branch points at — `git checkout -b`, `git
commit`, `git merge`, `git branch -f`/`-d`, `git worktree` itself — while
another Claude session (or a person) might be working in the same checkout
at the same time. Not for read-only work: answering a question by reading
files, running `gradle assembleDebug`/`testDebugUnitTest` or `nix flake
check` against whatever's already checked out, or grepping around. Nothing
in this skill needs to happen before *that*.

Skip it, and stay on the shared checkout, when:

- **The user names the shared checkout explicitly**, or says to work there
  directly.
- **You're reasonably sure nothing else is touching this checkout right
  now** — a solo session on a solo repo, the common case here.
- **The task is about the shared checkout's own state** — "what's checked
  out right now," "clean up stray worktrees," continuing a branch that's
  already checked out there from earlier in the same conversation.
- **You already made a worktree earlier in this same conversation** for
  the branch you're continuing — one per logical task/branch, not one per
  tool call.

## Why

Two things committing, checking out, or force-deleting branches in the same
working directory at once step on each other with no warning: a file
mid-edit gets reverted by the other side's checkout, a branch a commit was
about to land on gets swapped out from under it, a `git branch -f` fails
because the branch turns out to be checked out from the *other* session's
point of view. None of that requires the two lines of work to actually
touch the same files — sharing the checkout is enough.

A dedicated worktree per task removes the whole class of surprise: nobody
else's `checkout`/`commit`/`branch -d` can touch files you're looking at,
because they're a different working directory with a different `HEAD`,
backed by the same `.git` (so branches, objects, and `git worktree list`
are still shared and visible to both).

## How

**Create one**, based on whatever the task's target branch is (`main` is
this repo's only branch as of Phase 0 — adjust once others exist):

```sh
git -C /home/elly/projects/android/exigent-heron fetch origin
git -C /home/elly/projects/android/exigent-heron worktree add <scratchpad>/wt-<branch> -b <branch> origin/main
```

`<scratchpad>` is the scratchpad directory named in your own system
prompt — session-scoped, already the convention for temporary filesystem
state, and not something to invent a different location for. `<branch>`
should be the real branch name the task will end up shipping under, not a
throwaway label, so the worktree directory and the branch stay obviously
paired.

Then `cd` into it and work exactly as you would from the main checkout —
`gradle assembleDebug`/`testDebugUnitTest`, `nix develop`, `git commit`
all work identically; it's a full, independent working directory, not a
partial or read-only view. Once the change is ready to land, hand off to
skill [`submit-a-pr`](../submit-a-pr/SKILL.md) rather than pushing or
merging into `main` directly from here. Note that `local.properties`
is regenerated per-checkout by `flake.nix`'s `nix develop` shellHook (it's
gitignored and machine-specific), so run `nix develop` once inside the new
worktree before building there. **Verify you're actually in it**
(`git status -sb` or `pwd`) before running anything state-changing — a
harness's bash tool can reset the shell's cwd between calls, so a
multi-step task needs the `cd` re-asserted or paths given absolutely, not
assumed to persist.

**Checking a specific commit/branch without touching any branch pointer**
(e.g. verifying each commit in a multi-commit PR builds green): use
`--detach` instead of `-b`, since you're not going to commit there:

```sh
git worktree add -q --detach <scratchpad>/wt-check <sha-or-ref>
```

**Clean up when done** — shipped or abandoned, don't leave it dangling:

```sh
git worktree remove --force <path>
```

`git branch -d <branch>` fails with "used by worktree at ..." while the
worktree still exists — remove the worktree first, not the other way
around. `git worktree list` (from any worktree, they all see the same
list) shows everything outstanding; worth a glance at the start of a
session for an orphaned one left by an earlier interrupted task. Don't
remove one you don't recognize without checking it first (`git -C <path>
status`, and note its `mtime`) — it may belong to another session running
right now.
