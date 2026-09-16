plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

/** Where the application starts. Named once, since two places need it. */
val entry = "yemoja.ui.MainKt"

kotlin {
    jvmToolchain(21)

    // The only target this machine can build, for the reasons data/build.gradle.kts gives.
    // No run task: Gradle gives a child process no terminal, so it cannot host this one.
    jvm()

    sourceSets {
        commonMain.dependencies {
            // The layer below, and the only one. A front end talks to logic and nothing else.
            implementation(project(":logic"))
            // The application front end. Apache-2.0. One toolkit for all five targets, which
            // is what `ui/gui/doc.md` chose it for; the screens are written here and the
            // window that hosts them is per platform.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            // The platform's glyphs, the whole set rather than the fifty in the core one, which
            // has nothing for diving, a map or a book. Apache-2.0, the same publisher.
            implementation(compose.materialIconsExtended)
        }
        jvmMain.dependencies {
            // The desktop window and the event loop that owns it, which only a JVM has.
            implementation(compose.desktop.currentOs)
            // Raw keys and the terminal's size, which the JDK offers no way to ask for. Apache-2.0.
            implementation("com.github.ajalt.mordant:mordant:3.1.0")
            // What actually talks to the console. The other one needs JDK 22, and this is 21.
            // JNA is dual-licensed and taken here under Apache-2.0, not the LGPL.
            implementation("com.github.ajalt.mordant:mordant-jvm-jna:3.1.0")
            // Serving an agent the logbook's tools over MCP. MIT for what was written before, and
            // Apache-2.0 for what is added since. Its HTTP half is not used, so no Ktor engine is
            // declared beside it. `API-4`.
            implementation("io.modelcontextprotocol:kotlin-sdk-server:0.15.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            // The other end of the protocol, to call the tool server the way an agent does.
            implementation("io.modelcontextprotocol:kotlin-sdk-client:0.15.0")
        }
    }
}

// The manual, bundled so the Manuals tab can read it. Every chapter, and not the file about
// writing them, which is internal.
tasks.named<Copy>("jvmProcessResources") {
    from(rootProject.file("manual")) {
        include("*.md")
        exclude("doc.md")
        into("manual")
    }
}

// Where libdivecomputer is on this machine, for what is run and tested from here. Absent is a
// fine answer: nothing is told where it is, and nothing reads a dive computer. `LOGIC-27`.
val libdivecomputer: String? =
    System.getenv("LIBDIVECOMPUTER") ?: findProperty("libdivecomputer")?.toString()

// A window needs no terminal, so unlike the other front end this one can be run from here.
tasks.register<JavaExec>("gui") {
    description = "Opens a logbook in a window: ./gradlew :ui:gui --args=<logbook folder>."
    group = "application"
    mainClass = entry
    classpath = files(
        tasks.named("jvmJar"),
        kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
    )
    args = listOf("gui")
    // Whatever follows --args, so the folder is named where every other command names one.
    (providers.gradleProperty("args").orNull ?: "").split(" ").filter { it.isNotBlank() }
        .let { args = listOf("gui") + it }
    // Run from here there is no installation to look beside, so it is told. `LOGIC-27`.
    libdivecomputer?.let { systemProperty("jna.library.path", it) }
}

// Start scripts, so this is run as `yemoja <command>` rather than through Gradle. They are the
// only way to use an interactive front end, since Gradle gives a child process no terminal. The
// application plugin would write them and is incompatible with the multiplatform one, so the two
// tasks it would have added are here instead.
val jvmMain = kotlin.jvm().compilations.getByName("main")

val runtime = files(tasks.named("jvmJar"), jvmMain.runtimeDependencyFiles)

val startScripts = tasks.register<CreateStartScripts>("startScripts") {
    applicationName = "yemoja"
    mainClass = entry
    outputDir = layout.buildDirectory.dir("scripts").get().asFile
    classpath = runtime
}

tasks.register<Sync>("installDist") {
    description = "Writes bin/yemoja, the jars it needs and the dive computer library."
    group = "distribution"
    into(layout.buildDirectory.dir("install/yemoja"))
    from(startScripts) { into("bin") }
    from(runtime) { into("lib") }
    // The library that reads dive computers travels beside the jars, where the application
    // looks for it, and stays a file a user can see and replace -- which is what the licence
    // wants of it. A build on a machine without it writes none, and such an installation reads
    // no dive computer. `LOGIC-27`.
    libdivecomputer?.let { where ->
        from(where) {
            include("*.dll", "*.so", "*.so.*", "*.dylib")
            into("native")
        }
    }
    // Said out loud, because this task writes the whole folder afresh: building without the
    // library takes it back out of an installation that had it, and the window then says it
    // cannot read a dive computer. True, and baffling if nobody mentioned it.
    doFirst {
        if (libdivecomputer == null) {
            logger.quiet(
                "No libdivecomputer given: this installation will not read a dive computer. " +
                    "Set LIBDIVECOMPUTER, or pass -Plibdivecomputer=<folder>.",
            )
        }
    }
}
