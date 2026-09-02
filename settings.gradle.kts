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
// logic and ui join when they have something in them.
include(":data")
