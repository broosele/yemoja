package yemoja.ui.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import yemoja.data.Date
import yemoja.logic.Universe
import yemoja.logic.divecomputer.FoundDevices
import java.awt.Desktop
import java.net.URI
import java.time.LocalDate

/*
 * The window the screens are shown in, which is the one part per platform.
 *
 * See ../../../../../../gui/desktop/windows/doc.md.
 */

/**
 * Show the logbook in [folder] until the window is closed, and answer with what to exit with.
 *
 * **A folder is optional.** Named with none, the window opens on nothing and greets whoever
 * opened it, which is where a reader with no logbook starts. A folder that is named and will
 * not read is an error all the same: it is what was asked for. `GUI-30`.
 *
 * **What this supplies is the platform**: a window, a way to look for a dive computer, the manual
 * and the map read from the jar, and a browser for a link that leads out. Everything shown is
 * decided by the screens, which know nothing about any of this.
 */
fun gui(folder: String? = null): Int {
    val universe = if (folder == null) null else try {
        Universe.open(folder, FoundDevices())
    } catch (refused: RuntimeException) {
        // A folder that is not a logbook, or a file in it that will not read. There is no window
        // yet to say so in, so it is said where the command was typed.
        System.err.println("$folder could not be read: ${refused.message}")
        return 1
    }
    val platform = Platform(
        manual = CHAPTERS.map { file -> chapterOf(file, bundled("manual/$file")) },
        atlas = { Atlas.read { scale, layer -> bundled("libraries/map/$scale/$layer.txt") } },
        open = ::browse,
        today = ::today,
    )
    application {
        // The icon is drawn from its vector at whatever size the platform asks for.
        val density = LocalDensity.current
        val icon = remember { useResource("yemoja.svg") { loadSvgPainter(it, density) } }
        Window(
            onCloseRequest = ::exitApplication,
            title = if (folder == null) "Yemoja" else "Yemoja — $folder",
            icon = icon,
            state = rememberWindowState(size = DpSize(1650.dp, 1140.dp)),
        ) {
            // Light or dark as the system is set, in the application's own colours. `GUI-3`.
            val scheme = if (isSystemInDarkTheme()) MARINE_DARK else MARINE_LIGHT
            MaterialTheme(colorScheme = scheme) {
                Application(universe, platform)
            }
        }
    }
    return 0
}

/** What day it is here, which the greeting remarks on. */
private fun today(): Date = LocalDate.now().let { Date(it.year, it.monthValue, it.dayOfMonth) }

/** A file's text, as the build bundled it. One missing from the jar is a broken build. */
private fun bundled(path: String): String {
    val stream = Bundled::class.java.getResourceAsStream("/$path")
        ?: error("$path is not bundled, and the build should have done that")
    return stream.bufferedReader().use { it.readText() }
}

/** Something to look up resources from. A function has no class of its own to ask. */
private object Bundled

/** A link out of the manual, handed to whatever the desktop opens such things with. */
private fun browse(url: String) {
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
}
