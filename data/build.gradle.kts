plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    // The only target this machine can build. Native needs a C++ toolchain and Developer Mode,
    // neither of which is installed; Android needs the SDK; iPhone needs a Mac.
    jvm()

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
