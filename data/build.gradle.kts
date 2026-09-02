plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    // The only target this machine can build. Native needs a C++ toolchain and Developer Mode,
    // neither of which is installed; Android needs the SDK; iPhone needs a Mac.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
