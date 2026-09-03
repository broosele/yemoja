plugins {
    kotlin("multiplatform")
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
        }
        jvmMain.dependencies {
            // Raw keys and the terminal's size, which the JDK offers no way to ask for. Apache-2.0.
            implementation("com.github.ajalt.mordant:mordant:3.1.0")
            // What actually talks to the console. The other one needs JDK 22, and this is 21.
            // JNA is dual-licensed and taken here under Apache-2.0, not the LGPL.
            implementation("com.github.ajalt.mordant:mordant-jvm-jna:3.1.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
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
    description = "Writes bin/yemoja and the jars it needs into build/install/yemoja."
    group = "distribution"
    into(layout.buildDirectory.dir("install/yemoja"))
    from(startScripts) { into("bin") }
    from(runtime) { into("lib") }
}
