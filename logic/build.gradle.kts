plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    // The only target this machine can build, for the reasons data/build.gradle.kts gives.
    jvm()

    sourceSets {
        commonMain.dependencies {
            // The layer below, and the only one. Nothing here reaches a file or a screen.
            api(project(":data"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
