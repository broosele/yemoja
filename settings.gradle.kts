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
