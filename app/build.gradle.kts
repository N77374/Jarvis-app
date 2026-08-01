import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Secrets: prefer CI environment variables (set as GitHub Actions secrets),
// fall back to local.properties for local builds. Never hardcode/commit these.
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}
fun secret(key: String): String =
    System.getenv(key) ?: localProps.getProperty(key) ?: ""

android {
    namespace = "com.naruto.jarvis"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.naruto.jarvis"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${secret("PICOVOICE_ACCESS_KEY")}\"")
        buildConfigField("String", "PROXY_BASE_URL", "\"${secret("PROXY_BASE_URL")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ai.picovoice:porcupine-android:3.0.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
