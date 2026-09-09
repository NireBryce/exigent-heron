# Testing (dense)

_Last modified: 2026-09-08_

_Text is llm generated with occasional human review_

## Contents

- [Why this page holds procedure at all](#why-this-page-holds-procedure-at-all)
- [Suite inventory and what each cannot prove](#suite-inventory-and-what-each-cannot-prove)
- [Known-fragile tests](#known-fragile-tests)
- [The debug injector](#the-debug-injector)
- [Emulator lifecycle traps](#emulator-lifecycle-traps)
- [Phase acceptance criteria, as originally written](#phase-acceptance-criteria-as-originally-written)

Companion to [testing.md](testing.md), which holds the commands and the
manual scripts.

## Why this page holds procedure at all

[testing.md](testing.md) is the wiki's first standing exception to "index
over restatement" ([styleguide.md](styleguide.md)): it documents *doing*
something, where the real source is the act, not a file to link to. The
same role `NireBryce/nixos-configs`'s `homelab/` usage pages play there.
[architecture.md](architecture.md) is the second exception, for a
different reason.

## Suite inventory and what each cannot prove

| Suite | Command | Needs device | Proves | Does not prove |
|---|---|---|---|---|
| JVM unit | `just test` | no | almost all of `domain/`, plus the gate/queue policy | anything about real framework behaviour |
| Instrumented | `just test-instrumented` | yes | DataStore round-trips, real `TextToSpeech.stop()`, mirrored constants, `<queries>` | cross-process persistence |
| Acceptance | `just test-acceptance` | yes | "spoken once", "three duplicates still once" against real posted notifications | anything audible |
| Structure | `just structure` | no | one `Log` file, no Android imports in `domain/` | that glue holds no logic |
| Lint | `just lint` | no | AGP lint, debug and release | — |
| Wiki | `just wiki-lint` | no | links, anchors, contents, names, dates, phase files | that any sentence is true |

Specific gaps worth restating because they have burned this repo:

- **DataStore instrumented round-trips do not show cross-process
  persistence.** `preferencesDataStore` is a per-`Context` singleton, so a
  second repository in the same process shares the instance rather than
  re-reading the file. Only the manual force-stop step shows it.
- **`AndroidTtsEngineTest` exists because a fake cannot.** A fake
  `TtsEngine` can only show `SpeechQueue` *calls* `stop()`. Both the
  `ACTION_AUDIO_BECOMING_NOISY` path and the truncation timeout depend on
  that call actually achieving something.
- **`GatePolicyFrameworkConstantsTest` guards a silent failure mode.**
  Constant drift would not fail to compile; it would misclassify.
- **`InstalledAppsTest` tests the manifest, not the function.** Its three
  lines are not the risk; the `<queries>` block is.
- **`assembleRelease` cannot run in CI** (no signing config).
  `lintRelease` can, because lint analyzes without packaging or signing.
- **Every on-device result to date is an emulator result.** `SECURITY.md`
  §4 holds the caveats.

## Known-fragile tests

- `speech/SpeechQueueTest` and `listener/` tests exercise real background
  coroutines with real time. Re-run standalone several times before
  concluding a failure is a fluke — a real race hid behind exactly that
  assumption, and a second one only ever reproduced on CI. See
  [traps-and-skills.md](traps-and-skills.md).
- `RuleEngineHolderTest` needs **real** time, not
  `kotlinx-coroutines-test`'s virtual-time `runTest`: mixing the two made
  `RuleEngine.evaluate()`'s timeout fire spuriously. Its own top-of-file
  comment has the detail.
- AGP's `android.testOptions.unitTests.isReturnDefaultValues = true` is
  set in `app/build.gradle.kts` so Android SDK stub calls return a
  default instead of throwing `"... not mocked"`. Without it,
  `SafeLog.error()` inside a `catch` block crashes on a *different*
  exception than the one it was reporting, hiding the real failure. Not
  Robolectric — no new dependency; Robolectric stays out of §2's list
  deliberately, since the dev shell ships a real emulator and a simulated
  framework is the wrong place to learn framework facts.

## The debug injector

`app/src/debug/java/net/breadthcharge/exigentheron/debug/FakeNotifications.kt`,
landed Phase 1, `debug/` source set only per `AGENTS.md` §5. Exercises
the domain pipeline (`Deduplicator` → `RuleEngine` → `SecretDetector`)
without a device or a real listener. Shares `domain/ContentHash.kt` so
the fake and the real extractor cannot drift apart.

**Nothing wires it into the app** — no debug menu, no UI. It is reached
from a unit test or a scratch `main()`:

```kotlin
val engine = RuleEngine(rules = listOf(/* ... */))
runBlocking {
    FakeNotifications.scenarios().forEach { payload ->
        println(engine.evaluate(payload)) // evaluate() is suspend
    }
}
```

`AGENTS.md` §5 explicitly forbids the tempting alternative: a
`BroadcastReceiver` that accepts text to speak. That would give every app
on the device a voice.

If something ever does call the injector for real — a debug menu item, a
harness against the live listener — record it here rather than leaving
this as the only known invocation.

## Emulator lifecycle traps

Both were real bugs in `scripts/test.sh`, both fixed 2026-09-08, both
worth knowing before writing anything else that drives an emulator. Full
write-ups in [traps-and-skills.md](traps-and-skills.md).

- **`$!` after `emulator -avd ... &` is the launcher, not the emulator.**
  `emulator` spawns `qemu-system-x86_64-headless` and can exit itself, so
  `kill -0 "$PID"` reports a live emulator as dead and vice versa. The
  script now matches the real process: `pgrep -f "qemu-system.*-avd
  $AVD"`, and never tracks `$!`.
- **`adb wait-for-device` has no timeout.** With a stale AVD lock it sat
  eleven minutes with no output, indistinguishable from a slow boot. The
  boot wait is now bounded (`BOOT_TIMEOUT`, default 600s), the emulator's
  stdout is kept in `build/emulator.log` instead of `/dev/null`, and a
  failure prints that log's tail.

## Phase acceptance criteria, as originally written

`BUILD_PLAN.md` held these until it was removed **2026-09-08**. Recorded
here because [testing.md](testing.md)'s manual section is now the copy of
record and it is worth being able to check that copy against the original
intent.

- **Phase 2**: a notification is spoken once; three duplicates within 10s
  still speak once; music ducks and recovers.
  *Status: first two automated 2026-09-08; ducking still needs ears.*
- **Phase 3**: a rule added via the UI survives force-stop; an invalid
  pattern errors at save time rather than crashing later.
  *Status: true by code review (`RuleValidator`, `RuleCodec`,
  `RuleEngineHolder` are all directly tested); never run on a device.*
- **Phase 4**: headset-only blocks speech with no headset connected;
  engine-picker switching takes effect; ten notifications in five seconds
  collapse to one summary.
  *Status: first verified on the emulator 2026-09-07; second unrun; third
  covered by `SpeechQueueTest`.*
- **Phase 5**: tested on a locked device, in a call, and with a work
  profile present if available.
  *Status: locked and in-call verified on the emulator 2026-09-07; work
  profile only against a non-DPC secondary profile. Repro steps in
  `SECURITY.md` §4.*
