# Build Plan: Phases

**Audience:** Claude Code. Companion to [AGENTS.md](AGENTS.md) — that
document specifies *what* to build; this one specifies *what order* to
build it in and what "done" means at each step. Same ground rule as
AGENTS.md §0: read this whole document before starting a phase, not
partway through it.

Each phase must build and install. Do not proceed to phase N+1 until
phase N builds and its acceptance criteria pass. Commit at each phase
boundary with a message describing what now works (AGENTS.md §0).

### Phase 0 — Skeleton
Project scaffold, version catalog, manifest hardening from §5, `.gitignore`, `SafeLog`, empty `AppContainer`.

**Accept:** `gradle assembleDebug` succeeds. App installs, shows a blank screen. `INTERNET` absent from merged manifest (verify with `gradle :app:processDebugMainManifest` and read the output).

### Phase 1 — Domain core, no Android
Write `NotificationPayload`, `Rule`, `Decision`, `RuleEngine`, `SecretDetector`, `Deduplicator` and their unit tests. **Tests first.** No listener, no TTS, no UI.

Also build a debug-only fake notification injector: a function in `debug/` source set producing synthetic `NotificationPayload`s so you can exercise the pipeline without a device. Debug source set only — it must not exist in release.

**Accept:** `gradle testDebugUnitTest` passes with meaningful coverage of the dedup and secret-detection cases in §4.3 and §4.5. The `toString()` test passes.

### Phase 2 — Listener + speech, hardcoded rules
`NotificationTtsListener`, `NotificationExtractor`, `SpeechQueue`, `AndroidTtsEngine`, `AudioFocusManager`. Rules hardcoded to one package. No UI beyond an enable-access button.

**Accept:** on a real device, a notification from the hardcoded app is spoken exactly once. Send the same notification three times in 10 seconds — it speaks once. Start music, trigger a notification — music ducks and recovers.

### Phase 3 — Persistence + rules UI
`SettingsRepository`, `RuleRepository`, DataStore wiring, rule list and editor screens, installed-app picker.

**Accept:** rules survive app restart and force-stop. Invalid regex shows an error at save time rather than crashing later.

### Phase 4 — Gates and polish
`OutputRouteGate`, lock-state gate, DND respect, engine picker, queue-collapse-on-burst, announce-only mode, templates.

**Accept:** with headset-only on and no headset connected, nothing is spoken. Engine picker lists engines and switching takes effect. Ten notifications in five seconds produce a single summary utterance.

### Phase 5 — Hardening pass
Grep the codebase for logging violations. Verify release build has no debug injector. Confirm merged release manifest. Test on a locked device, in a call, and with a work profile present if available.

**Accept:** written checklist in `SECURITY.md` with every item ticked and evidence noted.
