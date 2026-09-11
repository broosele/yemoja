package yemoja.ui.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import yemoja.logic.Universe
import yemoja.logic.divecomputer.FoundDevices

/*
 * The window the screens are shown in, which is the one part per platform.
 *
 * See ../../../../../../gui/desktop/windows/doc.md.
 */

/**
 * Show the logbook in [folder] until the window is closed, and answer with what to exit with.
 *
 * **What this supplies is the platform**: a window, and a way to look for a dive computer.
 * Everything shown is decided by the screens, which know nothing about any of this.
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
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Yemoja — $folder",
            state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
        ) {
            // The platform's own light or dark, whichever the system is set to, until `GUI-3`
            // decides how much identity to define. Adopting the default first is the branch
            // that question offers.
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) { Application(universe) }
        }
    }
    return 0
}
