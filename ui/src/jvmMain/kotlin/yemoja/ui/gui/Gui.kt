package yemoja.ui.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import yemoja.logic.Universe
import yemoja.logic.divecomputer.FoundDevices
import java.awt.Desktop
import java.net.URI

/*
 * The window the screens are shown in, which is the one part per platform.
 *
 * See ../../../../../../gui/desktop/windows/doc.md.
 */

/**
 * Show the logbook in [folder] until the window is closed, and answer with what to exit with.
 *
 * **What this supplies is the platform**: a window, a way to look for a dive computer, the manual
 * read from the jar, and a browser for a link that leads out of it. Everything shown is decided
 * by the screens, which know nothing about any of this.
 */
fun gui(folder: String): Int {
    val universe = try {
        Universe.open(folder, FoundDevices())
    } catch (refused: RuntimeException) {
        // A folder that is not a logbook, or a file in it that will not read. There is no window
        // yet to say so in, so it is said where the command was typed.
        System.err.println("$folder could not be read: ${refused.message}")
        return 1
    }
    val manual = CHAPTERS.map { file -> chapterOf(file, bundled(file)) }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Yemoja — $folder",
            state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
        ) {
            // Light or dark as the system is set, in the application's own colours. `GUI-3`.
            val scheme = if (isSystemInDarkTheme()) MARINE_DARK else MARINE_LIGHT
            MaterialTheme(colorScheme = scheme) {
                Application(universe, manual, onOpen = ::browse)
            }
        }
    }
    return 0
}

/** A chapter's text, as the build bundled it. A chapter missing from the jar is a broken build. */
private fun bundled(file: String): String {
    val stream = Bundled::class.java.getResourceAsStream("/manual/$file")
        ?: error("manual/$file is not bundled, and the build should have done that")
    return stream.bufferedReader().use { it.readText() }
}

/** Something to look up resources from. A function has no class of its own to ask. */
private object Bundled

/** A link out of the manual, handed to whatever the desktop opens such things with. */
private fun browse(url: String) {
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
}
