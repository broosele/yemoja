plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)

    // The targets this machine can build. Native needs a C++ toolchain and Developer Mode,
    // neither of which is installed, and iPhone needs a Mac.
    jvm()
    android {
        namespace = "yemoja.data"
        compileSdk = property("androidSdk").toString().toInt()
        minSdk = property("androidOldest").toString().toInt()
    }

    sourceSets {
        commonMain.dependencies {
            // Files, which common Kotlin has none of. Behind FileStore, so nothing else sees it.
            implementation("com.squareup.okio:okio:3.18.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
