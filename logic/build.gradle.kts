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
            // Reading UDDF, which is XML and which common Kotlin has no reader for. Apache 2.0.
            // Here rather than in the data layer, whose promise of no dependencies stands: UDDF
            // is a foreign format and reading one is this layer's job. `FEAT-7`.
            implementation("io.github.pdvrieze.xmlutil:core:0.90.3")
        }
        jvmMain.dependencies {
            // Calling libdivecomputer, which is C. Apache 2.0 under its dual licence. Here rather
            // than in common because only a JVM reaches a library this way: `LOGIC-2` puts each
            // target's own answer below the port.
            implementation("net.java.dev.jna:jna:5.19.1")
            // Reaching a dive computer over Bluetooth LE, which libdivecomputer leaves to the
            // application. Apache 2.0. One library for every target it will be built for, but
            // declared per target all the same, for the reason above.
            implementation("com.juul.kable:kable-core:0.44.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Where libdivecomputer is, for the tests that call it. Absent is a fine answer: those tests
// then check that a machine without it reads no dive computers, which is what a user gets.
// Where it lives when the application ships is not settled -- `LOGIC-2`.
tasks.withType<Test>().configureEach {
    (System.getenv("LIBDIVECOMPUTER") ?: findProperty("libdivecomputer")?.toString())
        ?.let { systemProperty("jna.library.path", it) }
}

// The supplied libraries travel inside the application, because there is no reliable way to ask
// where it was installed and on a phone no such place exists. `LIB-6` in data/libraries.md.
// The licence goes with them: they are published data, and its terms travel with the data.
tasks.named<ProcessResources>("jvmProcessResources") {
    from(rootProject.file("libraries")) {
        // The name FileStore.LIBRARIES resolves under. A build script cannot see it.
        into("libraries")
        include("**/*.json", "map/**/*.txt", "LICENSE")
    }
}
