pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "yemoja"

// One module per layer, so the layering rule is a build error rather than a convention.
// ui joins when it has something in it.
include(":data")
include(":logic")
