plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

// :player-ui — player surface + controls/gestures (plan §2.1). Depends on
// :player-core and :player-api, plus Media3 UI and Compose.
android {
    namespace = "com.jv.player.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // The control surface renders through Media3's SurfaceView-backed AspectRatioFrameLayout
    // and drives the @UnstableApi Player video-surface / resize APIs (plan §5.3). Those are
    // annotated @UnstableApi in Media3 1.5.1 with no stable alternative, so the per-call
    // UnsafeOptInUsageError check is noise here, exactly as in :player-core.
    lint {
        disable += "UnsafeOptInUsageError"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":player-core"))
    api(project(":player-api"))

    // media3-ui provides the SurfaceView-backed AspectRatioFrameLayout render target (§5.3);
    // media3-common carries the Player / VideoSize / PlaybackException types the control
    // façade binds to. Pinned lockstep via the version catalog (plan §2.4).
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    // media3-ui-compose intentionally omitted — first published at 1.6.0, not available at the
    // pinned 1.5.1. The Compose control surface is hand-built over media3-ui + Compose foundation
    // instead. Re-add when the Media3 pin moves to >= 1.6.0. See catalog note.

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Lifecycle-aware background/foreground handling (W4): LocalLifecycleOwner + event observer
    // let PlayerSurface pause when the host goes to the background (plan §6 W4).
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = providers.gradleProperty("jvplayer.group").get()
                artifactId = "player-ui"
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
