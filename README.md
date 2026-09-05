# exigent-heron
personal android tts utility, huge wip

See [AGENTS.md](AGENTS.md) for the actual build spec.

## Dev environment

```
direnv allow      # or: nix develop
gradle assembleDebug
gradle testDebugUnitTest
```

No `gradlew` is committed — the flake's dev shell puts a pinned `gradle`
(and JDK, Kotlin, and the Android SDK) on `PATH` instead. See
[flake.nix](flake.nix) for what's pinned and why.

VS Code: accept the recommended extensions prompt (or `Extensions: Show
Recommended Extensions`) — `mkhl.direnv` is the one that actually matters,
it's what gets `JAVA_HOME`/`ANDROID_HOME` from the dev shell into the
Kotlin language server.
