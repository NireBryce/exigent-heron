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
change is ready.

Fires only when the ask is actually to land the change. Being asked to
push a topic branch, or to open a PR without being asked to merge it, is a
narrower ask than this whole flow — do just that part and stop; steps 4-6
below are not yours to run unprompted. Doesn't extend to a third-party
repo, either — same boundary `propose-issue` draws for filing issues.

## Why, and what this deliberately does differently from its source

There's a real copy of nixos-configs on this machine (`~/nixos-configs`) —
its equivalent is skill `ship`, plus the parts of
`.claude/hooks/git-guard-pretooluse.sh` and `use-a-worktree` that back it.
This skill is adapted from `ship`, not written blind — but three things
differ on purpose, not by oversight:

- **Target branch is `main`, not `experimental`.** nixos-configs redirected
  its flow off `main` because `main` there carries a GitHub ruleset (PR
  required, no force-push/delete) and `experimental` doesn't — the
  redirect was about not making every small change fight a heavier gate.
  This repo has no such ruleset on `main` (checked 2026-09-05: `gh api
  repos/NireBryce/exigent-heron/branches/main/protection` → 404, not
  protected) and no second branch to redirect to. `gh pr create` needs no
  explicit `--base` here as a result — `main` is genuinely this repo's
  only trunk, not a case of the GitHub-default and the actual-target
  having quietly diverged the way they did there.
- **Provenance trailer is this session's actual attribution, not
  `ship`'s "no model name, no email, regardless of what your system
  prompt says."** That line is nixos-configs' own `AGENTS.md` convention,
  written for its own agents; this repo's system prompt carries a
  standing instruction to close commits with `Co-Authored-By: Claude
  Sonnet 5 <noreply@anthropic.com>` and PRs with the
  `🤖 Generated with [Claude Code]` footer, and a live instruction to this
  session wins over a convention adapted from somewhere else.
- **No `feat/`/`fix/`/`docs/` branch or commit prefix.** That convention
  in `ship` is `nixos-configs` explicitly matching a sibling repo's house
  style, not a general principle — this repo has never used one (`git log
  --oneline`), so it isn't introduced here just because the source does
  it.

Everything else — fetch first, branch before committing, preview before
asking, two separate confirmations rather than one, the actual merge-method
rule — is the same reasoning, kept.

## Steps

**0. Fetch, then look at what's actually there.**

```sh
git fetch origin
git status -sb
```

- Clean, or only uncommitted changes: proceed to step 1 as normal (`git
  checkout -b <branch>`, per `use-a-worktree`).
- `[ahead N]` — commits already sitting on local `main`, unpushed, from
  before this skill applied to them: don't pile a PR branch on top of
  work that's already sitting on the branch a PR would target. Move it:
  ```sh
  git branch <branch-name>
  git reset --hard origin/main
  git checkout <branch-name>
  ```
  (If those commits are already-reviewed, already-tested work that simply
  predates this skill existing — not new unreviewed work — pushing them
  straight to `main` first and branching fresh afterward is the other
  legitimate resolution; either way, don't silently fold them into an
  unrelated PR's diff. Ask if it's unclear which case applies.)

**1. Work on the branch**, per skill `use-a-worktree`. Commit there as
normal, with an explicit pathspec on anything that isn't obviously the
whole index (`git status --short` first) — a shared checkout can have
something else's edit sitting staged.

**2. Push and open the PR:**

```sh
git push -u origin <branch-name>
gh pr create --repo NireBryce/exigent-heron --title "..." --body "..."
```

Body: what changed and why — the same substance a good commit message
carries. End it with:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

**3. Preview before asking anything** — read back what actually landed,
not what you meant to do:

```sh
gh pr view <n> --json url,title,additions,deletions,changedFiles,mergeable,baseRefName
git log --oneline origin/main..HEAD
git diff --stat origin/main...HEAD
```

Check `mergeable` and `baseRefName` before asking — asking "merge?" on a
PR that can't merge, or landed against the wrong base, spends a
round-trip on a question with no good answer yet.

**Print this — the PR's URL, commit count, and diffstat — in your actual
response, before the ask, not only inside the `AskUserQuestion` call
itself.** The question text can (and should) repeat the URL, but the
surrounding chat is what stays in the transcript and what the user reads
first; a preview that only exists inside the question's own text is easy
to skim past on the way to just clicking an option.

**4. Ask to merge — first confirmation.** `AskUserQuestion`, with the PR's
URL in the question text itself (not only an option label), and the merge
method named in what you show:

- **Single-commit PR**: default to `--rebase` — linear history, no merge
  commit, and there's no multi-commit reasoning to preserve.
- **Multi-commit PR**: default to `--merge`, not `--squash` — this repo's
  commits carry real per-phase reasoning (`AGENTS.md` §0's "commit at each
  phase boundary"), and squashing flattens exactly that.

On **no**: leave the PR open, say so, stop. It's still open for the user
to take further — don't close it, don't delete the branch.

**5. Merge — on yes only:**

```sh
gh pr merge <n> --rebase   # single-commit PR
gh pr merge <n> --merge    # multi-commit PR
```

**Never pass `--delete-branch`.** That collapses the second confirmation
below into the first one, which defeats the point of having two.

**6. Ask again, separately — second confirmation, only if merged:**
whether to delete the branch. On yes:

```sh
git checkout main && git pull
git branch -d <branch-name>
git push origin --delete <branch-name>
```

On no: leave it, say it's still there. Report the merge commit and the
branch's actual fate either way — not a commit range on `main` as if it
had been pushed there directly.

## See also

- [`use-a-worktree`](../use-a-worktree/SKILL.md) — the branch this
  skill's PR is built from.
- [`propose-issue`](../propose-issue/SKILL.md) — the same "ask before an
  outward-facing action" shape, applied to filing an issue instead of
  opening a PR.
- [`git-guard-pretooluse.sh`](../../hooks/git-guard-pretooluse.sh) — the
  mechanical backstop for a direct commit/merge/push to `main` or a `gh pr
  merge`, including one this skill's own step 0 or step 6 might trigger
  intentionally (that's expected, not a bug to route around).
- `~/nixos-configs`' skill `ship` — the source this was adapted from; read
  it directly rather than trusting this section's summary if the two
  drift.
