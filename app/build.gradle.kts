import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from keystore.properties (git-ignored) or HERMESTTY_* environment variables.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.planetwally.hermestty"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.planetwally.hermestty"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        val store = signingValue("storeFile", "HERMESTTY_KEYSTORE")
        if (store != null) create("release") {
            storeFile = rootProject.file(store)
            storePassword = signingValue("storePassword", "HERMESTTY_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "HERMESTTY_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "HERMESTTY_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key, fall back to the debug key so local builds still install.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug").also {
                logger.warn("hermes-tty: no release keystore configured — release APK is signed with the debug key")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.graphicsMode", "NATIVE")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core:1.7.0")
}
