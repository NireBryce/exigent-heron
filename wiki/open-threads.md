# Open threads

## Contents

- [Tracked as GitHub issues](#tracked-as-github-issues)
- [Left open right now](#left-open-right-now)
- [Not applicable yet](#not-applicable-yet)

Open questions and known gaps, plus anything tracked as a GitHub issue.
Adapted from nixos-configs' `open-threads.md` — same idea, much shorter,
since this repo is three commits old.

**Before starting work on any of these, or investigating a symptom that
might be one of them:** `gh issue list --repo NireBryce/exigent-heron
--search "<keywords>" --state all` in addition to reading this page — see
skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md).

## Tracked as GitHub issues

None yet — `NireBryce/exigent-heron` has issues enabled with a real label
set (checked 2026-09-05, see skill
[`propose-issue`](../.claude/skills/propose-issue/SKILL.md)) but nothing
filed. Nothing below has been promoted to an issue; it's all still at the
"noticed, not yet worth a round-trip" stage per that skill's Calibrate
section.

## Left open right now

- **`app_name` (`res/values/strings.xml`) is still the literal string
  `"exigent-heron"`** — the repo's own codename, not a real user-facing app
  name. `AGENTS.md` never names the app (it's specified generically as "a
  notification TTS reader"), so this may be intentional-for-now rather
  than an oversight — worth deciding before Phase 3 (the UI phase) rather
  than shipping the codename as the launcher label by default.
- **The android.util.Log CI grep `AGENTS.md` §4.6 asks for** ("Add a lint
  check or a simple CI grep asserting `android.util.Log` appears in
  exactly one file") was missing from `.github/workflows/check.yml` until
  **2026-09-05**, when it was added while building this wiki. Listed here
  as a closed thread, not an open one — kept as the record of when it
  actually landed, since `AGENTS.md` itself doesn't track that.

## Not applicable yet

`AGENTS.md` §8's "if OEM background-killing turns out to break it" clause
— nothing to investigate until there's a running listener service to
observe being killed (Phase 2+). Not forgotten, just not yet reachable.
