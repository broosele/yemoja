plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(21)

    // The only target this machine can build, for the reasons data/build.gradle.kts gives.
    jvm {
        mainRun { mainClass.set("yemoja.ui.tui.MainKt") }
    }

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
