# exigent-heron
personal android tts utility, huge wip

See [AGENTS.md](AGENTS.md) for the actual build spec,
[wiki/status.md](wiki/status.md) for the phase-by-phase build history, and
[INSTALL.md](INSTALL.md) to build a signed APK and put it on a phone.

## LLM co-programming

Most of this codebase — implementation, tests, and the `wiki/` — is
written by LLM coding agents under human direction and review, not
hand-written line by line. `AGENTS.md` is the standing spec given to
whichever agent is working; `wiki/history.md` records decisions made
along the way, and `wiki/traps-and-skills.md` records mistakes actually
made and caught.

## Dev environment

```
direnv allow      # or: nix develop
just              # lists every check
just test         # JVM unit tests, no device needed
just test-device  # androidTest + the listener acceptance script
just test-all     # everything, cheapest first
```

`just` is the interface; [scripts/test.sh](scripts/test.sh) is what it
runs, and works on its own if you'd rather call it directly. The device
recipes boot a headless emulator themselves when none is attached, and
stop only one they started.

No `gradlew` is committed — the flake's dev shell puts a pinned `gradle`
(and JDK, Kotlin, and the Android SDK) on `PATH` instead. See
[flake.nix](flake.nix) for what's pinned and why.

VS Code: accept the recommended extensions prompt (or `Extensions: Show
Recommended Extensions`) — `mkhl.direnv` is the one that actually matters,
it's what gets `JAVA_HOME`/`ANDROID_HOME` from the dev shell into the
Kotlin language server.

## License

[Apache-2.0](LICENSE) — see also
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the third-party
dependencies the app ships with.
