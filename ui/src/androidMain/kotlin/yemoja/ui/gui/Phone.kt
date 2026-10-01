package yemoja.ui.gui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import yemoja.data.json.Lock
import yemoja.data.json.LogbookReader
import yemoja.logic.Universe
import yemoja.logic.today
import java.io.File

/*
 * The window the screens are shown in on Android, which is the one part per platform.
 *
 * See ../../../../../../gui/phone/android/doc.md.
 */

/**
 * The application on an Android screen, over the logbook in [folder].
 *
 * The folder is made into a logbook the first time. What this supplies is the platform: the
 * manual and the map read from the app, whatever the phone opens a link with, whether the screen
 * is a phone's, and its back button. Absent so far: a folder the user picks, `AND-5`; a dive
 * computer, `AND-6`; and import and export, which wait on the folder picker.
 */
@Composable
fun Yemoja(folder: String) {
    val context = LocalContext.current
    // A phone where the screen's shorter side is under 600, which is where Android itself draws
    // the line; a tablet is laid out as a desktop is. `PHONE-3`.
    val compact = LocalConfiguration.current.smallestScreenWidthDp < TABLET
    val universe = remember(folder) {
        // Android ends an app without warning, so a lock is left behind every time it does. In
        // the app's own storage nothing else can reach the logbook, so a lock found here is one
        // this app left and is let go. A folder the user picks needs another answer. `AND-5`.
        File(Lock.folderOf(folder)).deleteRecursively()
        if (File(folder, LogbookReader.MANIFEST).isFile) Universe.open(folder) else Universe.create(folder)
    }
    DisposableEffect(universe) { onDispose { universe.close() } }
    val platform = remember(compact) {
        Platform(
            manual = CHAPTERS.map { file -> chapterOf(file, bundled("manual/$file")) },
            atlas = { Atlas.read { scale, layer -> bundled("libraries/map/$scale/$layer.txt") } },
            open = { url ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            today = ::today,
            compact = compact,
            back = { enabled, onBack -> BackHandler(enabled, onBack) },
        )
    }
    // Light or dark as the system is set, in the application's own colours. `GUI-3`.
    val scheme = if (isSystemInDarkTheme()) MARINE_DARK else MARINE_LIGHT
    // Android draws an app under its status and navigation bars, so the window keeps clear of
    // them itself.
    MaterialTheme(colorScheme = scheme) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { Application(universe, platform) }
    }
}

/** The shorter side of a tablet's screen at its least, in density-independent pixels. */
private const val TABLET = 600

/** A text the build put inside the app, read whole. */
private fun bundled(path: String): String {
    val stream = Bundled::class.java.getResourceAsStream("/$path")
        ?: error("$path is not bundled, and the build should have done that")
    return stream.bufferedReader().use { it.readText() }
}

/** Something to look up resources from. A function has no class of its own to ask. */
private object Bundled
