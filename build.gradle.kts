plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    // Compose, for the application front end. The compiler plugin is versioned with Kotlin
    // itself, so it cannot drift from the compiler it runs inside.
    kotlin("plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.compose") version "1.9.3" apply false
}
