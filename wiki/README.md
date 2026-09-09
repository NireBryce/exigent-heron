# Wiki

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Two forms of every page](#two-forms-of-every-page)
- [I want to...](#i-want-to)
- [Pages](#pages)
- [Keeping this honest](#keeping-this-honest)

Notes on what this app actually is and how to work on it, kept separate
from [`AGENTS.md`](../AGENTS.md), which states what the app must keep
satisfying and deliberately doesn't track whether that's true right now.

If you are new here, read [overview.md](overview.md), then
[testing.md](testing.md) when you want to run something.

## Two forms of every page

Each subject has two files:

| File | Written for | Holds |
|---|---|---|
| `<page>.md` | a human contributor | what you need to do the task, in the order you need it |
| `<page>-4llm.md` | an agent loading context | the same subject's dense detail: full reasoning chains, dated deviations, incident post-mortems, provenance |

A fact lives in exactly one of the two. The article states the current
answer and links; the companion holds the history and the argument behind
it. The companion is not a longer draft of the article — it is the part
that was deliberately kept out of it.

If you are reading as a human and want the whole story behind a decision,
the `-4llm` page is where it went. It is dense on purpose and will not
read pleasantly.

## I want to...

| Goal | Start here |
|---|---|
| understand what this app is | [overview.md](overview.md) |
| build, install, or test it | [testing.md](testing.md) |
| find a class or follow the pipeline | [architecture.md](architecture.md) |
| know what's verified vs. only written | [status.md](status.md) |
| check if a bug is already known | [open-threads.md](open-threads.md), skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md) |
| avoid a mistake already made here | [traps-and-skills.md](traps-and-skills.md) |
| know why something is the way it is | [history.md](history.md) |
| land a change | skill [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md), then skill [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) |
| add or edit a wiki page | [styleguide.md](styleguide.md) |

## Pages

- [Overview](overview.md) — what the app is, its one hard constraint, and
  the path a notification takes. ([dense](overview-4llm.md))
- [Architecture](architecture.md) — package tree, data flow, what each
  package holds. ([dense](architecture-4llm.md))
- [Status](status.md) — what is built and what has actually been run.
  ([dense](status-4llm.md))
- [Testing](testing.md) — build, install, and exercise the app.
  ([dense](testing-4llm.md))
- [Open threads](open-threads.md) — known gaps and where issues live.
  ([dense](open-threads-4llm.md))
- [Traps & skills](traps-and-skills.md) — mistakes made here, and the
  lesson each one carries. ([dense](traps-and-skills-4llm.md))
- [History](history.md) — why the code looks like this.
  ([dense](history-4llm.md))
- [Wiki style guide](styleguide.md) — house rules for these pages.
  ([dense](styleguide-4llm.md))
- [Wiki index, dense form](README-4llm.md) — this page's companion.

## Keeping this honest

Two rules, both short:

1. **Whichever change makes a page stale fixes it in the same change.**
   Skill [`wiki-sync`](../.claude/skills/wiki-sync/SKILL.md) is the
   checklist; skill [`submit-a-pr`](../.claude/skills/submit-a-pr/SKILL.md)
   runs it before a branch is pushed.
2. **Dates are absolute, and a "verified" claim names the command that
   was run.** Skill [`fact-hygiene`](../.claude/skills/fact-hygiene/SKILL.md)
   is the general form.

Run the mechanical checks before you land anything that touches this
directory:

```sh
just wiki-lint          # or: python3 wiki/scripts/check_wiki.py check
```

They catch broken links, broken anchors, stale `## Contents` blocks,
unknown skill/recipe/task names, a missing date line, and a phase claimed
built whose files don't exist. They cannot tell that a sentence became
untrue — that part is yours.
