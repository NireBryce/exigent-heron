---
name: submit-a-pr
description: How to land a code change in this repo via a pull request instead of committing, merging, or pushing directly to main.
---

# Landing a change via pull request

## Applies to

The point in any task where a change in this repo — code, `AGENTS.md`,
`wiki/`, `.claude/` itself — is finished and tested and would otherwise get
committed straight to `main`, merged there locally, or pushed there
directly. Not for read-only work, and not mid-task: this is the last step,
after skill `use-a-worktree` gets you a branch to actually work in and the
change is ready. Doesn't extend to a third-party repo — same boundary
`propose-issue` draws for filing issues.

## Why

Every commit in this repo so far (`git log --oneline`, checked 2026-09-05)
landed straight on `main` — there was no branch-then-PR step before this
skill existed. There's no local copy of nixos-configs' own agent
git/github workflow doc to adapt verbatim here, but the shape is the same
principle this repo's other adapted skills already lean on
(`use-a-worktree`, `propose-issue`): an agent proposes a change, a human
decides whether it lands. `git-guard-pretooluse.sh` backs this
mechanically — it now asks before a direct commit, a local merge, or a
push that would land on `main`/`master`, and before `gh pr merge` — but
that hook is a backstop for a slip, not the primary mechanism. Follow the
steps below rather than relying on it to catch you.

## Steps

1. **Work on a branch**, per skill `use-a-worktree` — a worktree off
   `origin/main`, named for the change itself, not `main`.
2. **Commit there** as normal.
3. **Push the branch**, not `main`:
   ```sh
   git push -u origin <branch>
   ```
4. **Open the PR**:
   ```sh
   gh pr create --repo NireBryce/exigent-heron \
     --title "..." --body "..."
   ```
   Body: what changed and why — the same substance a good commit message
   would carry. End it with:
   ```
   🤖 Generated with [Claude Code](https://claude.com/claude-code)
   ```
5. **Do not merge it yourself.** Prompt the user with `AskUserQuestion`,
   putting the PR's URL directly in the question text (not only in an
   option label, so it's visible no matter which option they pick) —
   something like "PR #`<n>` is ready: `<url>` — merge it now?" with
   options along the lines of "Merge now" / "Leave it open, I'll review on
   GitHub". Only run `gh pr merge <number>` if they pick the merge option
   — and expect `git-guard-pretooluse.sh` to ask you to confirm that too;
   that's the backstop doing its job, not a bug to work around.
6. **Clean up the worktree** once the PR is merged or abandoned, per
   `use-a-worktree`'s own cleanup step.

## See also

- [`use-a-worktree`](../use-a-worktree/SKILL.md) — the branch this
  skill's PR is built from.
- [`propose-issue`](../propose-issue/SKILL.md) — the same "ask before an
  outward-facing action" shape, applied to filing an issue instead of
  opening a PR.
- [`git-guard-pretooluse.sh`](../../hooks/git-guard-pretooluse.sh) — the
  mechanical backstop for a direct commit/merge/push to `main`/`master` or
  a `gh pr merge`.
