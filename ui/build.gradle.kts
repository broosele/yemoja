import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

/** Where the application starts. Named once, since two places need it. */
val entry = "yemoja.ui.MainKt"

kotlin {
    jvmToolchain(21)

    // The targets this machine can build, for the reasons data/build.gradle.kts gives.
    // No run task: Gradle gives a child process no terminal, so it cannot host this one.
    jvm()
    android {
        namespace = "yemoja.ui"
        compileSdk = property("androidSdk").toString().toInt()
        minSdk = property("androidOldest").toString().toInt()
    }

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
            // Hosting the agent the user installed, which is how their own subscription answers.
            // Apache-2.0 in what it publishes; the repository's own LICENSE.txt says MIT, and
            // either is fine here. `GUI-38`.
            implementation("com.agentclientprotocol:acp:0.30.1")
        }
        androidMain.dependencies {
            // The phone's back button, handed to the screens so it steps back as their arrow
            // does. The same library the app takes its window from. `PHONE-2`.
            implementation("androidx.activity:activity-compose:1.13.0")
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

// What an agent with a model behind it needs, and only when somebody asks for it: the command
// that starts the agent and the logbook to ask about. `RealAgentTest` does nothing without them,
// because a real conversation sends a logbook to a provider and costs whoever runs it money.
tasks.withType<Test>().configureEach {
    findProperty("agent")?.let { environment("YEMOJA_AGENT", it.toString()) }
    findProperty("logbook")?.let { environment("YEMOJA_LOGBOOK", it.toString()) }
}

// The version, from gradle.properties, where it is said once. `GUI-5`.
val release = property("release").toString()

// The manual, bundled so the Manuals tab can read it. Every chapter, and not the file about
// writing them, which is internal.
val bundledManual: CopySpec = copySpec {
    from(rootProject.file("manual")) {
        include("*.md")
        exclude("doc.md")
        into("manual")
        filesMatching("app-info.md") { filter { line -> line.replace("{version}", release) } }
    }
}

tasks.named<Copy>("jvmProcessResources") {
    inputs.property("release", release)
    with(bundledManual)
}

// The same manual for Android, through one of the resource folders it packs into the app.
val androidManual = tasks.register<Sync>("androidManual") {
    inputs.property("release", release)
    with(bundledManual)
    into(layout.buildDirectory.dir("androidManual"))
}
kotlin.sourceSets.named("androidMain") { resources.srcDir(androidManual) }

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
    // The window takes one argument, a folder. Gradle's --args replaces the arguments set here and
    // splits on spaces, which a folder's name may hold, as `My Drive` does; so `gui` is put back in
    // front and what Gradle split is joined into the one path again.
    val property = providers.gradleProperty("args").orNull.orEmpty()
    doFirst {
        val given = (args.orEmpty().dropWhile { it == "gui" } + property.split(" ")).filter { it.isNotBlank() }
        args = listOf("gui") + listOfNotNull(given.joinToString(" ").ifEmpty { null })
    }
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

// The installer, which is how the first version reaches a user: Windows only, `WIN-1`, unsigned,
// `WIN-2`. Compose's packaging makes it with the platform's jpackage, which needs the WiX toolset;
// the plugin fetches WiX itself the first time, so nothing is installed on the machine that
// builds. `./gradlew :ui:packageMsi` writes it under build/compose/binaries.
//
// The dive computer library travels as one of the application's resources rather than in a
// folder beside the jars, the installer laying the jars out where `installDist` does not.
// `findLibrary` looks for it there too. `LOGIC-27`.
val nativeResources = layout.buildDirectory.dir("installerResources")

val installerResources = tasks.register<Sync>("installerResources") {
    description = "Puts the dive computer library where the installer takes resources from."
    into(nativeResources)
    libdivecomputer?.let { where ->
        from(where) {
            include("*.dll")
            into("windows")
        }
    }
    doFirst {
        if (libdivecomputer == null) {
            logger.quiet(
                "No libdivecomputer given: the installer will not read a dive computer. " +
                    "Set LIBDIVECOMPUTER, or pass -Plibdivecomputer=<folder>.",
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = entry
        // Given no arguments the launcher opens the window. Given some, it is the command they
        // name, which is how an agent starts `yemoja api` from an installed copy. `API-4`.
        args += listOf("gui")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Yemoja"
            packageVersion = release
            description = "A dive logbook kept as readable files"
            appResourcesRootDir.set(nativeResources)
            // The modules jdeps finds the shipped jars using, and three loaded by name that it
            // cannot see. A new library means running jdeps again, since a module left out fails
            // only when the code needing it runs. `DESK-10`.
            modules(
                "java.instrument", "java.management", "jdk.unsupported",
                "jdk.crypto.ec", "jdk.charsets", "jdk.accessibility",
            )
            windows {
                // Made from the window's own yemoja.svg by tool/icons.py, which is run again when
                // the drawing changes. The installer, the exe and its shortcuts all carry it.
                iconFile.set(project.file("icons/yemoja.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                // Fixed for good: it is how Windows knows a later installer upgrades this one.
                upgradeUuid = "4edab8bc-ba5c-4ead-94b9-f4821b831dab"
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(installerResources) }
