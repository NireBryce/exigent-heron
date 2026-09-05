#!/usr/bin/env bash
# PreToolUse hook (Bash matcher). Deterministic guard against destructive git
# actions -- ones that discard commits, working-tree changes, stashes, or
# branches with no straightforward undo. Pattern-matches the command before
# it runs and asks for confirmation rather than hard-denying, because every
# pattern here has a real legitimate use; this is a mechanical backstop for
# "confirm first on anything hard to reverse," not a replacement for
# judgment. Adapted from nixos-configs' .claude/hooks/git-guard-pretooluse.sh
# (same logic, generalized -- that version also gated on a specific
# protected-branch skill this repo doesn't have).
#
# 2026-09-05: added a second class of checks -- direct commit/merge/push to
# main (or master), and gh pr merge -- backing skill submit-a-pr's "branch,
# then PR, then a human merges" workflow. Not from nixos-configs; this
# repo's own addition once it needed one.
#
# Known limits: this is pattern-matching on the command string, not a git
# parser. It does not follow shell variables/aliases, does not know what a
# rebase or push will actually touch, and a short-option cluster it doesn't
# special-case can slip through. The "current branch" checks below run `git
# rev-parse` in this hook process's own cwd, which usually matches what the
# command is about to act on but won't chase a `cd` inside the same command
# string. Extend the patterns below rather than assuming every destructive
# shape is covered.
set -euo pipefail

input=$(cat)
command=$(jq -r '.tool_input.command // empty' <<<"$input")

# Only look at commands that actually invoke git or gh.
if ! grep -qE '(^|[;&|]|[[:space:]])(git|gh)([[:space:]]|$)' <<<"$command"; then
    exit 0
fi

# Current branch in this hook's own cwd -- used by the main/master checks
# below. Empty if not in a repo or detached; the checks that use it just
# won't match in that case.
current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)

# A short-option cluster or long flag carrying -f/--force (e.g. -f, -uf,
# -fd, --force), but not the safe long forms that refuse to overwrite work
# they haven't seen.
force_flag_re='(^|[[:space:]])-[a-zA-Z]*f[a-zA-Z]*([[:space:]]|$)|(^|[[:space:]])--force([[:space:]]|$)'
safe_force_re='--force-with-lease|--force-if-includes'

reason=""

# git push --force / -f
if [ -z "$reason" ] && grep -qE '\bpush\b' <<<"$command" \
    && grep -qE -- "$force_flag_re" <<<"$command" \
    && ! grep -qE -- "$safe_force_re" <<<"$command"; then
    reason="This looks like a force push (--force/-f, not --force-with-lease) -- it can overwrite remote history and silently discard someone else's commits. Confirm this is intended, or use --force-with-lease so it fails instead of overwriting unseen work."
fi

# git push --delete / -d <ref>, or the :branch colon-refspec deletion form
if [ -z "$reason" ] && grep -qE '\bpush\b' <<<"$command" \
    && grep -qE -- '--delete\b|(^|[[:space:]])-d([[:space:]]|$)|[[:space:]]:[A-Za-z]' <<<"$command"; then
    reason="This looks like it deletes a remote branch or tag. Confirm the ref is actually meant to go -- hard to undo once someone else has fetched it."
fi

# git reset --hard
if [ -z "$reason" ] && grep -qE '\breset\b' <<<"$command" && grep -qE -- '--hard\b' <<<"$command"; then
    reason="'git reset --hard' discards uncommitted changes and moves the branch, losing any commits not reachable elsewhere. Confirm nothing unsaved is about to be dropped."
fi

# git clean with -f/--force (deletes untracked files; add -d/-x and it also
# takes directories and gitignored files)
if [ -z "$reason" ] && grep -qE '\bclean\b' <<<"$command" && grep -qE -- "$force_flag_re" <<<"$command"; then
    reason="'git clean -f' permanently deletes untracked files (with -d or -x it also takes directories and gitignored files) -- there's no undo. Confirm nothing not yet committed is about to be taken."
fi

# git checkout/switch forcing away local modifications
if [ -z "$reason" ] && grep -qE '\b(checkout|switch)\b' <<<"$command" && grep -qE -- "$force_flag_re" <<<"$command"; then
    reason="A forced checkout/switch discards local modifications to tracked files with no undo. Confirm nothing uncommitted is about to be lost."
fi
if [ -z "$reason" ] && grep -qE '\bcheckout\b[[:space:]]+(--[[:space:]]+)?\.([[:space:]]|$)' <<<"$command"; then
    reason="'git checkout -- .' (or 'git checkout .') discards all uncommitted changes in the working tree with no undo. Confirm that's actually intended."
fi

# git branch -D (force delete)
if [ -z "$reason" ] && grep -qE '\bbranch\b' <<<"$command" \
    && grep -qE -- '(^|[[:space:]])-[a-zA-Z]*D[a-zA-Z]*([[:space:]]|$)' <<<"$command"; then
    reason="'-D' force-deletes a branch even if it has commits not merged anywhere else. Confirm the branch's commits are actually safe to lose."
fi

# history rewriting
if [ -z "$reason" ] && grep -qE '\b(filter-branch|filter-repo)\b' <<<"$command"; then
    reason="This rewrites repository history wholesale. Confirm this is really intended -- it changes commit hashes for everything downstream and can't be undone once pushed."
fi

# git stash drop / clear
if [ -z "$reason" ] && grep -qE '\bstash\b' <<<"$command" && grep -qE '\b(drop|clear)\b' <<<"$command"; then
    reason="This permanently discards stashed changes with no undo. Confirm the stash isn't still needed."
fi

# git commit directly on main/master -- this repo lands changes via PR
# (skill submit-a-pr), not a direct commit on the branch a PR would target.
if [ -z "$reason" ] && grep -qE '\bcommit\b' <<<"$command" \
    && { [ "$current_branch" = "main" ] || [ "$current_branch" = "master" ]; }; then
    reason="This would commit directly on '$current_branch'. This repo lands changes via a branch + PR (skill submit-a-pr), not a direct commit there -- confirm this one is really meant to land straight on '$current_branch', or branch first (skill use-a-worktree)."
fi

# git merge into main/master, whether by being checked out there already or
# by naming it explicitly. Excludes "gh ... merge" (gh pr merge is handled
# separately below, with its own reason).
if [ -z "$reason" ] && grep -qE '\bmerge\b' <<<"$command" && ! grep -qE '\bgh\b' <<<"$command" \
    && { [ "$current_branch" = "main" ] || [ "$current_branch" = "master" ] \
         || grep -qE '(^|[[:space:]:])(main|master)([[:space:]:]|$)' <<<"$command"; }; then
    reason="This looks like a local merge into main/master. This repo lands changes via PR (skill submit-a-pr), not a direct local merge -- confirm this is really intended."
fi

# git push naming main/master explicitly (git push origin main, HEAD:main,
# main:main, ...), or a bare "git push"/"git push -u"/"git push --force..."
# with no remote or branch named at all, while main/master is what's
# checked out -- that pushes whatever's checked out.
if [ -z "$reason" ] && grep -qE '\bpush\b' <<<"$command" && ! grep -qE '\bgh\b' <<<"$command"; then
    push_flagged=""
    if grep -qE '(^|[[:space:]:])(main|master)([[:space:]:]|$)' <<<"$command"; then
        push_flagged=1
    elif { [ "$current_branch" = "main" ] || [ "$current_branch" = "master" ]; } \
        && grep -qE '(^|[;&|])[[:space:]]*git[[:space:]]+push([[:space:]]+(-[A-Za-z]+|--[A-Za-z-]+|origin|upstream))*[[:space:]]*($|[;&|])' <<<"$command"; then
        push_flagged=1
    fi
    if [ -n "$push_flagged" ]; then
        reason="This looks like a push to '$current_branch'. This repo lands changes via PR (skill submit-a-pr) -- push a feature branch and open a PR instead, or confirm this direct push is really intended."
    fi
fi

# gh pr merge
if [ -z "$reason" ] && grep -qE '\bgh\b' <<<"$command" && grep -qE '\bpr\b' <<<"$command" && grep -qE '\bmerge\b' <<<"$command"; then
    reason="This merges a pull request. Skill submit-a-pr has a human decide that, not the agent unprompted -- confirm this merge is actually wanted right now rather than left for review."
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
