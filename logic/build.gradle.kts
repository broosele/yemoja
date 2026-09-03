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

// The supplied libraries travel inside the application, because there is no reliable way to ask
// where it was installed and on a phone no such place exists. `LIB-6` in data/libraries.md.
// The licence goes with them: they are published data, and its terms travel with the data.
tasks.named<ProcessResources>("jvmProcessResources") {
    from(rootProject.file("libraries")) {
        // The name FileStore.LIBRARIES resolves under. A build script cannot see it.
        into("libraries")
        include("**/*.json", "LICENSE")
    }
}
