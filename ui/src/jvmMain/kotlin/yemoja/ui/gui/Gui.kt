package yemoja.ui.gui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import yemoja.ui.api.Talking
import yemoja.ui.api.ToolSocket
import yemoja.ui.api.Tools
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.net.URI
import java.time.LocalDate
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

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
    application {
        // Which logbook is open can change while the window is: a new one is made into it.
        var at by remember { mutableStateOf(folder) }
        var held by remember { mutableStateOf(universe) }
        // What an agent runs in, which outlives any one conversation: an agent is started and
        // stopped many times while a window is open. `GUI-38`.
        val scope = rememberCoroutineScope()
        val platform = remember {
            Platform(
                manual = CHAPTERS.map { file -> chapterOf(file, bundled("manual/$file")) },
                atlas = {
                    Atlas.read { scale, layer -> bundled("libraries/map/$scale/$layer.txt") }
                },
                open = ::browse,
                today = ::today,
                ask = ::asked,
                pick = ::picked,
                save = ::saved,
                conversing = { personal, writing ->
                    val open = held
                    val where = at
                    if (open == null || where == null) {
                        error("an agent is offered only where a logbook is open")
                    }
                    // A socket of its own, which the conversation closes with itself: the token
                    // an agent was given stops working when the talking stops. `API-4`. Onto the
                    // toolkit's thread, which is the one the Universe lives on.
                    // Named, because which flag is which is what the boxes on the panel promise:
                    // one withholds a person's private details and the other lets an agent stage
                    // changes. `API-5`.
                    val relay = ToolSocket(
                        Tools(open, writing = writing, personal = personal),
                        Dispatchers.Main,
                    )
                    Talking(relay, where, scope)
                },
                deeds = mapOf(
                    Deed.NEW to {
                        chosen("New logbook", "Create")?.let { where ->
                            made(where)?.let {
                                at = where
                                held = it
                            }
                        }
                    },
                    Deed.OPEN to {
                        chosen("Open logbook", "Open")?.let { where ->
                            opened(where)?.let {
                                at = where
                                held = it
                            }
                        }
                    },
                ),
            )
        }
        // The icon is drawn from its vector at whatever size the platform asks for.
        val density = LocalDensity.current
        val icon = remember { useResource("yemoja.svg") { loadSvgPainter(it, density) } }
        Window(
            onCloseRequest = ::exitApplication,
            title = if (at == null) "Yemoja" else "Yemoja — $at",
            icon = icon,
            state = rememberWindowState(size = DpSize(1650.dp, 1140.dp)),
        ) {
            // Light or dark as the system is set, in the application's own colours. `GUI-3`.
            val scheme = if (isSystemInDarkTheme()) MARINE_DARK else MARINE_LIGHT
            MaterialTheme(colorScheme = scheme) {
                Application(held, platform)
            }
        }
    }
    return 0
}

/**
 * A folder the user picks, or absent where they change their mind.
 *
 * Folders only, and one that is not there yet may be typed: making a logbook makes its folder,
 * so a reader should not have to make it first in another application.
 */
/**
 * A folder or a file to import, or nothing where the reader named none.
 *
 * One dialog for both, since what is there says how it is read: a folder is another Yemoja
 * logbook and a file is a UDDF document. `GUI-33`.
 */
private fun picked(asking: String): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = asking
        fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
        approveButtonText = "Import"
    }
    if (chooser.showDialog(null, "Import") != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.absolutePath
}

/**
 * A file to write an export to, or nothing where the reader named none or would not write over one.
 *
 * `.uddf` is added to a name typed without an extension. A file already there is asked about
 * rather than written over silently, the dialog itself not asking. `GUI-37`.
 */
private fun saved(asking: String): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = asking
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter("UDDF documents", "uddf")
        approveButtonText = "Export"
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val to = chooser.selectedFile?.absolutePath?.let(::uddfNamed) ?: return null
    if (!File(to).exists()) return to
    val answer = JOptionPane.showConfirmDialog(
        null,
        "$to is already there. Write over it?",
        "Yemoja",
        JOptionPane.YES_NO_OPTION,
    )
    return if (answer == JOptionPane.YES_OPTION) to else null
}

private fun chosen(asking: String, approving: String): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = asking
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        approveButtonText = approving
    }
    if (chooser.showDialog(null, approving) != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.absolutePath
}

/** A new logbook at [where], or absent where it could not be made and the reader was told. */
private fun made(where: String): Universe? = try {
    Universe.create(where, FoundDevices())
} catch (refused: RuntimeException) {
    warn("$where could not be made into a logbook: ${refused.message}")
    null
}

/**
 * The logbook at [where], or absent where it would not read and the reader was told.
 *
 * A folder holding no manifest is a logbook that declares nothing rather than a mistake, which
 * is what `yemoja gui` does with the same folder. It opens, and its greeting says it holds no
 * dives, which is a truer answer than refusing a folder the reader picked.
 */
private fun opened(where: String): Universe? = try {
    Universe.open(where, FoundDevices())
} catch (refused: RuntimeException) {
    warn("$where could not be read: ${refused.message}")
    null
}

/**
 * A question put to the reader, waited on, and answered.
 *
 * On the toolkit's own thread whatever thread it was asked from, a download running on one of
 * its own; and waited for there, since what asked cannot go on without the answer.
 */
private fun asked(question: String): String? {
    var answer: String? = null
    val put = Runnable { answer = JOptionPane.showInputDialog(null, question, "Yemoja", QUESTION) }
    if (SwingUtilities.isEventDispatchThread()) put.run() else SwingUtilities.invokeAndWait(put)
    return answer?.ifBlank { null }
}

/** What a question looks like, which is a question rather than a warning. */
private const val QUESTION = JOptionPane.QUESTION_MESSAGE

/**
 * What went wrong, in a box over the window.
 *
 * A box rather than the screen behind it: it answers something the reader just asked for, and
 * the window under it has not changed.
 */
private fun warn(message: String) {
    JOptionPane.showMessageDialog(null, message, "Yemoja", JOptionPane.ERROR_MESSAGE)
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
