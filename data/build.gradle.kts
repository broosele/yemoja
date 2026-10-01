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
    // The website's planner, compiled from the logic layer rather than built for it, `LOGIC-43`.
    // Okio publishes for this target at the version pinned above, which is all that is asked here.
    // A library target only: nothing in this layer is the thing that runs in a browser.
    wasmJs {
        browser()
    }

    sourceSets {
        // What a JVM and Android share: the real disk, through DiskFileStore. A browser has none,
        // `LOGIC-43`, so wasmJs stays on FileStore and MemoryFileStore alone, both fully common.
        val javaMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(javaMain)
        androidMain.get().dependsOn(javaMain)
        commonMain.dependencies {
            // Files, which common Kotlin has none of. Behind FileStore, so nothing else sees it.
            implementation("com.squareup.okio:okio:3.18.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
