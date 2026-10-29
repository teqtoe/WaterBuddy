plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.waterbuddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.waterbuddy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "4.0"
        ndk {
            // phone-களுக்கு மட்டும் (APK size குறைய)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // ஒரே key — புது APK பழசு மேலயே install ஆகும்
    signingConfigs {
        create("shared") {
            storeFile = file("waterbuddy.keystore")
            storePassword = "waterbuddy123"
            keyAlias = "waterbuddy"
            keyPassword = "waterbuddy123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    // 3D: Google Filament
    implementation("com.google.android.filament:filament-android:1.66.0")
    implementation("com.google.android.filament:gltfio-android:1.66.0")
    // Photo-ல முகம் கண்டுபிடிக்க (phone-லயே)
    implementation("com.google.mlkit:face-detection:16.1.7")
}
