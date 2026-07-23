plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-JVM module hosting the architecture unit test (plan §2.3). Deliberately
// dependency-light (JUnit only) so it runs as a fast unit test in every CI build,
// independent of the Android toolchain. It scans the repo's module build files and
// SDK sources to enforce dependency direction and the no-encryption rule.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnit()
    // The test walks module build files + sources; hand it the repo root explicitly.
    systemProperty("jvplayer.rootDir", rootProject.projectDir.absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
