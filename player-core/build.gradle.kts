plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// :player-core — engine module (plan §2.1). Media3/ExoPlayer wiring lives here.
// Depends on :player-api ONLY (never :player-ui — enforced by the architecture test §2.3).
android {
    namespace = "com.jv.player.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // This module IS the Media3/ExoPlayer wiring layer (plan §2.1/§3.3). ExoPlayer,
    // DataSource, DataSpec, SeekParameters, DefaultMediaSourceFactory are all annotated
    // @UnstableApi in Media3 1.5.1 with no stable alternative — opting into them is the
    // module's whole reason to exist, so the per-call UnsafeOptInUsageError check is pure
    // noise here. (Media3's @UnstableApi is an androidx.annotation.experimental @RequiresOptIn
    // marker, which Kotlin's @OptIn does not honour — only this Lint check enforces it.)
    lint {
        disable += "UnsafeOptInUsageError"
    }

    // Robolectric drives the DataSource byte-range/seek/EOF unit tests on the JVM
    // (they need a real android.net.Uri / Media3 DataSpec, not the stubbed one).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":player-api"))

    // Media3 — pinned lockstep via the version catalog (plan §2.4). No BOM exists.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource)
    implementation(libs.media3.common)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = providers.gradleProperty("jvplayer.group").get()
                artifactId = "player-core"
                version = providers.gradleProperty("jvplayer.version").get()
            }
        }
        repositories {
            maven {
                name = "internal"
                url = uri(rootProject.layout.buildDirectory.dir("internal-maven-repo"))
            }
        }
    }
}
