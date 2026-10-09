plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.rahmatsobrian.miraicode"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.rahmatsobrian.miraicode"
        minSdk = 28          // Android 9
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "1.0"
    }
    // Buang resource bahasa lain dari library (Compose/Material punya puluhan locale)
    androidResources { localeFilters += listOf("en", "id") }
    // Jangan sisipkan blok metadata dependency di APK
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/META-INF/proguard/**",
                "/META-INF/com/android/build/gradle/**",
                "/kotlin/**",
                "/DebugProbesKt.bin",
                "/kotlin-tooling-metadata.json",
            )
        }
    }
    buildFeatures { compose = true; aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        create("release") {
            storeFile = file("release-key.jks")
            // Isi password di bawah (atau set env KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "rahmatsobrian"
            keyAlias = System.getenv("KEY_ALIAS") ?: "rahmatsobrian"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "rahmatsobrian"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true       // R8: shrink + optimize + obfuscate
            isShrinkResources = true     // buang resource yang tidak terpakai
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug { signingConfig = signingConfigs.getByName("release") }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
