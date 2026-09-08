# Third-party notices

This project's build strips the `META-INF/{AL2.0,LGPL2.1}` license files
from the APK (`app/build.gradle.kts`, the standard Android template
exclusion). This file is the readable record of the third-party code the
app ships with, kept at the repo root rather than generated, since the
dependency list is small and closed (`AGENTS.md` §2). Pinned versions live
in [gradle/libs.versions.toml](gradle/libs.versions.toml); they are
deliberately not repeated here so this file doesn't rot on every bump.

## Shipped in the APK

All of the following are licensed under the Apache License, Version 2.0
(see [LICENSE](LICENSE) for the text — the same license as this project),
and are used unmodified:

| Artifact | Source |
|---|---|
| `androidx.core:core-ktx` | https://android.googlesource.com/platform/frameworks/support |
| `androidx.lifecycle:lifecycle-runtime-ktx` | https://android.googlesource.com/platform/frameworks/support |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | https://android.googlesource.com/platform/frameworks/support |
| `androidx.activity:activity-compose` | https://android.googlesource.com/platform/frameworks/support |
| `androidx.compose:*` (BOM: material3, ui, ui-tooling-preview, and transitive runtime) | https://android.googlesource.com/platform/frameworks/support |
| `androidx.datastore:datastore-preferences` | https://android.googlesource.com/platform/frameworks/support |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | https://github.com/Kotlin/kotlinx.coroutines |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | https://github.com/Kotlin/kotlinx.serialization |

Copyright for the AndroidX libraries is held by The Android Open Source
Project; for the kotlinx libraries, by JetBrains s.r.o. and
contributors.

The app bundles no TTS engine, font, icon set, or other asset — it speaks
through whatever TTS engine the user has installed on the device.

## Test-only (never shipped)

| Artifact | License | Source |
|---|---|---|
| `junit:junit` | EPL-1.0 | https://github.com/junit-team/junit4 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| `com.google.truth:truth` | Apache-2.0 | https://github.com/google/truth |

JUnit's EPL-1.0 obligations apply only when you distribute it; unit-test
dependencies are not included in the APK, so nothing here carries them
forward.

## Build-time only (not distributed)

The Android Gradle Plugin and Kotlin Gradle plugins are toolchain used to
produce the build; no part of them ships in the APK.
