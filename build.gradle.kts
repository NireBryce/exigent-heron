// Root build file. Per-module config lives in app/build.gradle.kts —
// single module, see AGENTS.md §3.
//
// No org.jetbrains.kotlin.android plugin: AGP 9's Kotlin support is
// built in, and applying that plugin on top is now a hard error.
// See https://kotl.in/gradle/agp-built-in-kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
