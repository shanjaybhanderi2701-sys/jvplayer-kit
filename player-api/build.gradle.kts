plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// :player-api — pure contract module (plan §2.1/§2.2).
// INTENTIONALLY has NO Media3 and NO Compose dependency: it is the stable,
// minimal surface CalcVault/JGallery compile against to author a PlaybackSource.
// The architecture unit test (§2.3) fails the build if a Media3/Compose dep is added here.
android {
    namespace = "com.jv.player.api"
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
    // Android SDK (android.net.Uri) only — no Media3, no Compose. See §2.2.
    testImplementation(libs.junit)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = providers.gradleProperty("jvplayer.group").get()
                artifactId = "player-api"
                version = providers.gradleProperty("jvplayer.version").get()
            }
        }
        repositories {
            // Stub internal Maven repo — real credentials wired later (plan §2.4).
            maven {
                name = "internal"
                url = uri(rootProject.layout.buildDirectory.dir("internal-maven-repo"))
            }
        }
    }
}
