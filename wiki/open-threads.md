# Open threads

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Before you start](#before-you-start)
- [Genuinely open](#genuinely-open)
- [Waiting on a physical device](#waiting-on-a-physical-device)
- [Not reachable yet](#not-reachable-yet)
- [See also](#see-also)

Known gaps and open questions. Issues themselves live on GitHub — this
page holds only what the tracker can't tell you.

## Before you start

Check the tracker first. It is the authority; this page is not a mirror
of it, deliberately:

```sh
gh issue list --repo NireBryce/exigent-heron --state all
gh issue list --repo NireBryce/exigent-heron --search "<keywords>" --state all
```

Skill [`investigate-bug`](../.claude/skills/investigate-bug/SKILL.md) is
the procedure. Skill
[`propose-issue`](../.claude/skills/propose-issue/SKILL.md) is what to do
when you find something new in passing.

A table of open issues used to live here. It was stale within the hour —
see [traps-and-skills.md](traps-and-skills.md) for why that shape can't
work.

## Genuinely open

**A failing rule reaches only logcat.** (`AGENTS.md` §4.4, issue #48.)
`RuleEngine` already reports both halves — a pattern that won't compile,
and a match that hits the 100ms timeout — through its `onRuleFailure`
callback, and `RuleEngineTest` covers both. What's missing is the last
hop: `AppContainer` wires `onRuleFailure` to `SafeLog.error`, so the
failure never reaches the rule editor or list. A rule silently never
matching, with the reason visible only under `adb logcat`, is the exact
failure mode §4.8 rejects for TTS engines, applied to a different
subsystem.

**`onListenerDisconnected` doesn't reset TTS state.** (`AGENTS.md` §4.10,
issue #49.) It logs the lifecycle line and returns; nothing stops the
engine or drains the queue, so revoking notification access — or the
system rebinding the service, which it does unpredictably — leaves an
in-flight utterance playing to completion. Small to fix
(`container.speechQueue.stopCurrent()` is already the mid-utterance stop
path `AudioBecomingNoisyReceiver` uses), but it's a behaviour change and
wants its own change rather than riding along with something else.

**Long notifications cut off.** (Issue #28.) About *character*-length
limits in `TextToSpeech`. Adjacent to but not the same as §4.7's
utterance *time* cap (`Settings.truncationLengthSeconds`), which is
built.

**Kotlin LSP metadata skew.** (Issue #24.) An upstream limitation with no
project-side fix — see [traps-and-skills.md](traps-and-skills.md).

## Waiting on a physical device

**No physical Android device has ever run this app.** Everything below is
blocked on that, not on code.

- **Phase 2**: duck-and-recover with real music. The other two criteria
  are automated (`just test-acceptance`).
- **Phase 3**: a rule surviving force-stop, and an invalid pattern
  erroring at save time. True by code review; never run.
- **Phase 4**: engine-picker switching taking effect. Headset-only was
  confirmed on the emulator 2026-09-07; burst collapse is unit-tested.
- **Phase 5**: OEM battery-killing, a real cellular radio, real MDM
  enrollment. Locked-device and in-call were confirmed on the emulator;
  work profile only against a non-DPC secondary profile. `SECURITY.md` §4
  has the caveats.
- **A populated Bluetooth device list** — the emulator had no bonded
  devices, so the per-device rows are unit-tested only.

[testing.md](testing.md) has the steps for all of these.

## Not reachable yet

`AGENTS.md` §8's "if OEM background-killing turns out to break it" clause
(issue #27). The listener exists, but there is nothing to investigate
until it has actually run unattended on real hardware long enough to
observe whether an OEM kills it. Not forgotten — see above.

## See also

- [open-threads-4llm.md](open-threads-4llm.md) — the dense companion:
  every thread closed so far, with what actually resolved it.
- [status.md](status.md) — what's verified, and what isn't.
- [testing.md](testing.md) — how to run the unrun checks.
