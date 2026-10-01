pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // Compose is published partly as androidx, which Google host and nobody mirrors.
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "yemoja"

// One module per layer, so the layering rule is a build error rather than a convention.
include(":data")
include(":logic")
include(":ui")
// The Android app, which is an activity hosting the window the ui layer already draws. A module of
// its own because Android's plugin for an app and its plugin for a multiplatform layer do not mix.
include(":android")
