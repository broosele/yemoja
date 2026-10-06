plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

// The Android app: one activity that hosts the window the ui layer draws, and nothing of its own.
// Everything shown, and everything the platform supplies to it, is in ui/src/androidMain.
android {
    namespace = "yemoja.android"
    compileSdk = property("androidSdk").toString().toInt()

    defaultConfig {
        applicationId = "app.yemoja"
        minSdk = property("androidOldest").toString().toInt()
        targetSdk = property("androidSdk").toString().toInt()
        // Raised with every release: Android installs an update only over a lower number.
        versionCode = 22
        versionName = property("release").toString()
        // The processors libdivecomputer is built for: a phone's, and the emulator's. Every phone
        // running Android 12 or later that is worth supporting is one of these. `AND-2`.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // A release is signed with the author's own key, kept outside the repository in a folder
    // holding yemoja.jks and its password beside it, named by the property yemoja.signing in
    // ~/.gradle/gradle.properties. An update installs only over an app signed with the same key,
    // so the one key signs every release. `AND-1`. Without the property a release build is
    // unsigned, and a debug build signs with Android's own debug key as always.
    val signing = findProperty("yemoja.signing")?.toString()?.let { file(it) }
    if (signing != null) {
        signingConfigs.create("release") {
            storeFile = signing.resolve("yemoja.jks")
            storePassword = signing.resolve("password.txt").readText().trim()
            keyAlias = "yemoja"
            keyPassword = storePassword
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // libdivecomputer, built for Android by tool/libdivecomputer-android.py into a folder of one
    // subfolder per processor, and carried as a file of its own as on the desktop. `LOGIC-27`.
    // Where none is given the app reads no dive computer, which it says. `LOGIC-28`.
    findProperty("libdivecomputer.android")?.toString()?.let { built ->
        sourceSets.getByName("main").jniLibs.directories += built
    }

    packaging {
        // Two libraries both carry their licence notices under the same names, and an app holds one
        // copy of each name. The notices are the libraries' own and are not this app's to choose.
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation(project(":ui"))
    // What gives an activity a Compose window to draw in. Apache 2.0, from the same publisher as
    // the toolkit, and what every Compose app on Android starts from.
    implementation("androidx.activity:activity-compose:1.13.0")
}
