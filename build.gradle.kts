plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9 sudah punya Kotlin bawaan: plugin org.jetbrains.kotlin.android TIDAK dipakai lagi
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
