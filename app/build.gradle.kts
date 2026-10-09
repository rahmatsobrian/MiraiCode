plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.rahmatsobrian.miraicode"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.rahmatsobrian.miraicode"
        minSdk = 28          // Android 9
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true; aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            // Isi password di bawah (atau set env KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "GANTI_PASSWORD_KEYSTORE"
            keyAlias = System.getenv("KEY_ALIAS") ?: "rahmatsobrian"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "rahmatsobrian"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
