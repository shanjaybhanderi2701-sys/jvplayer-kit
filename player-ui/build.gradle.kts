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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":player-core"))
    api(project(":player-api"))

    implementation(libs.media3.ui)
    // media3-ui-compose intentionally omitted in Wave 0 — first published at 1.6.0,
    // not available at the pinned 1.5.1. Added in the Compose UI wave. See catalog note.

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

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
