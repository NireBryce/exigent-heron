// Helper for kls-classpath (see that file for why this exists): defines
// a throwaway task that dumps :app's resolved debug compile classpath,
// without touching android.bootClasspath — the legacy API whose removal
// from AGP's new-style extension breaks fwcd.kotlin's own built-in
// Gradle classpath resolver. See wiki/traps-and-skills.md.
//
// Not part of the real build — only ever invoked via
// `gradle --init-script kls-classpath-init.gradle.kts`, never applied
// to app/build.gradle.kts itself.
allprojects {
    afterEvaluate {
        if (extensions.findByName("android") != null) {
            tasks.register("printKlsClasspath") {
                doLast {
                    val cp = configurations.getByName("debugCompileClasspath")
                    // Plain `cp.files` returns raw .aar archives for library
                    // dependencies, which the Kotlin compiler can't read
                    // classes out of directly. Request the classes-jar view
                    // AGP registers as an artifact transform target instead
                    // — this is the same normalized (aar-or-jar) -> jar view
                    // AGP itself uses to build its own compile classpath.
                    val artifactType = Attribute.of("artifactType", String::class.java)
                    val classesJars = cp.incoming.artifactView {
                        attributes { attribute(artifactType, "android-classes-jar") }
                    }.files
                    println(classesJars.joinToString(":") { it.absolutePath })
                }
            }
        }
    }
}
