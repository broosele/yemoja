plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    // Compose, for the application front end. The compiler plugin is versioned with Kotlin
    // itself, so it cannot drift from the compiler it runs inside.
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
    // Building for Android, which only Google's plugin does. Apache 2.0. One for the layers, each a
    // library with an Android target beside its JVM one, and one for the app that holds them.
    // `AND-7` in ui/gui/phone/android/doc.md.
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
    id("com.android.application") version "9.4.1" apply false
}
