import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Secrets: prefer CI environment variables, fall back to local.properties for local builds.
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
    implementation("com.alphacephei:vosk-android:0.3.47@aar") { isTransitive = true }
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
