plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Signing and versioning come from the environment (CI), mirroring ntfy-android's kc-release:
// KC_KEYSTORE_FILE / KC_KEYSTORE_PASSWORD / KC_KEY_ALIAS / KC_KEY_PASSWORD, KC_VERSION_NAME / KC_VERSION_CODE.
// Same key as ntfy Kudcrafts. Without KC_KEYSTORE_FILE the release build stays unsigned.
val keystoreFile: String? = System.getenv("KC_KEYSTORE_FILE")

android {
    namespace = "com.kudcrafts.widgets"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kudcrafts.widgets"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("KC_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("KC_VERSION_NAME") ?: "0.0.0-dev"
    }

    signingConfigs {
        create("kudcrafts") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storeType = "pkcs12"
                storePassword = System.getenv("KC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KC_KEY_ALIAS")
                keyPassword = System.getenv("KC_KEY_PASSWORD") ?: System.getenv("KC_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("kudcrafts")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
