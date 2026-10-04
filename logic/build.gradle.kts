plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(21)

    // The targets this machine can build, for the reasons data/build.gradle.kts gives.
    jvm()
    android {
        namespace = "yemoja.logic"
        compileSdk = property("androidSdk").toString().toInt()
        minSdk = property("androidOldest").toString().toInt()
    }
    // The website's planner. `wasmJsMain` holds the one thing a browser needs that nothing else
    // does: a JSON-in, JSON-out entry point a page can call without a Kotlin type in sight.
    // `LOGIC-43`, website/doc.md in the yemoja_website repository.
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        // What a JVM and Android share, both being Java underneath: the date, and in time the
        // reach to a dive computer. Common Kotlin has neither.
        val javaMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(javaMain)
        androidMain.get().dependsOn(javaMain)
        commonMain.dependencies {
            // The layer below, and the only one. Nothing here reaches a file or a screen.
            api(project(":data"))
            // Reading UDDF, which is XML and which common Kotlin has no reader for. Apache 2.0.
            // Here rather than in the data layer, whose promise of no dependencies stands: UDDF
            // is a foreign format and reading one is this layer's job. `FEAT-7`.
            implementation("io.github.pdvrieze.xmlutil:core:0.90.3")
        }
        // Calling libdivecomputer, which is C. Apache 2.0 under its dual licence. Shared by the
        // JVM and Android, whose code reaching it is one, and published for each as its own
        // artifact: a jar for the JVM, and for Android an archive carrying its native part.
        // `LOGIC-2` puts each target's own answer below the port.
        named("javaMain").dependencies {
            compileOnly("net.java.dev.jna:jna:5.19.1")
            // Reaching a dive computer over Bluetooth LE, which libdivecomputer leaves to the
            // application. Apache 2.0. One library for every target it will be built for.
            implementation("com.juul.kable:kable-core:0.44.3")
        }
        jvmMain.dependencies {
            implementation("net.java.dev.jna:jna:5.19.1")
        }
        androidMain.dependencies {
            implementation("net.java.dev.jna:jna:5.19.1@aar")
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
val bundledLibraries: CopySpec = copySpec {
    from(rootProject.file("libraries")) {
        // The name FileStore.LIBRARIES resolves under. A build script cannot see it.
        into("libraries")
        include("**/*.json", "map/**/*.txt", "LICENSE")
    }
}

// The tide calculators' own files: the stations and places they ask, and the waters they cover.
// Not a library, since nothing in a logbook names them; they are this layer's, as its code is.
// `LOGIC-44`.
val bundledTides: CopySpec = copySpec {
    from(project.file("tides")) {
        into("tides")
        include("*.txt")
    }
}

tasks.named<ProcessResources>("jvmProcessResources") {
    with(bundledLibraries)
    with(bundledTides)
}

// Android packs what a library's resource folders hold into the app, so the same files are put
// in one of those for it. A folder inside an app cannot be listed, only a file read, so an index
// of every file goes with them, which is what a new logbook learns the shipped libraries from.
val androidLibraries = tasks.register<Sync>("androidLibraries") {
    with(bundledLibraries)
    into(layout.buildDirectory.dir("androidLibraries"))
    doLast {
        val files = destinationDir.walk().filter { it.isFile }
            .map { it.relativeTo(destinationDir).invariantSeparatorsPath }
            .sorted()
            .toList()
        destinationDir.resolve("libraries/index.txt").writeText(files.joinToString("\n") + "\n")
    }
}
kotlin.sourceSets.named("androidMain") { resources.srcDir(androidLibraries) }

// The tides for Android, apart from the libraries so the index above lists libraries alone.
val androidTides = tasks.register<Sync>("androidTides") {
    with(bundledTides)
    into(layout.buildDirectory.dir("androidTides"))
}
kotlin.sourceSets.named("androidMain") { resources.srcDir(androidTides) }
