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
