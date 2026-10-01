package yemoja.ui.tui

import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import yemoja.logic.Universe
import yemoja.logic.divecomputer.FoundDevices

/*
 * The terminal itself: the one place that reads a keyboard and puts characters on a screen.
 * Screen decides what those characters are and knows nothing about any of this.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/**
 * Show the logbook in [folder] until the user leaves, and answer with what to exit with.
 *
 * **What this supplies is the platform.** A terminal to draw on, and a way to look for a dive
 * computer: finding one needs a native library, which the layers below declare a port for and do
 * not implement. `LOGIC-2`.
 */
fun tui(folder: String): Int {
    val universe = try {
        Universe.open(folder, FoundDevices())
    } catch (refused: Exception) {
        // A folder that is not a logbook, or a file in it that will not read. Either way the
        // message names what was wrong, and a terminal that never started needs no tidying up.
        System.err.println("$folder could not be read: ${refused.message}")
        return 1
    }
    return show(universe)
}

/**
 * Draw [screen] and answer keys until it stops running.
 *
 * The terminal is put back the way it was found whatever happens, including the alternate screen
 * and the cursor, which raw mode alone does not restore.
 */
private fun show(universe: Universe): Int {
    val terminal = Terminal()
    val raw = try {
        terminal.enterRawMode()
    } catch (without: IllegalStateException) {
        // Piped, redirected, or run by a build tool. Nothing is broken and there is nothing to
        // tidy up, since the terminal has not been touched yet.
        System.err.println("the terminal interface needs a terminal: ${without.message}")
        return 1
    }
    // Made once the terminal is raw, because the one question a download puts is read here.
    val screen = Screen(universe) { question -> prompted(terminal, raw, question) }
    print(ENTER)
    try {
        raw.use {
            while (screen.running) {
                paint(terminal, screen)
                // OrNull rather than readKey, which throws instead. Input can end without the
                // user leaving — a closed terminal, a pipe running out — and that is a session
                // over rather than a fault to report.
                val event = it.readKeyOrNull() ?: break
                keyOf(event.key, event.ctrl, event.shift)?.let { key -> screen.press(key) }
            }
        }
    } finally {
        print(LEAVE)
        System.out.flush()
    }
    return 0
}

/**
 * Put [screen] on the terminal, whatever size it is now.
 *
 * The whole rectangle every time. A terminal this small has nothing worth the machinery of
 * working out which lines changed, and redrawing everything cannot leave anything stale.
 *
 * Lines end `\r\n` because raw mode does not translate a newline: on its own it drops a line and
 * leaves the cursor where it was.
 */
private fun paint(terminal: Terminal, screen: Screen) {
    val size = terminal.size
    val width = size.width.coerceAtLeast(Screen.LEAST_WIDTH)
    val height = size.height.coerceAtLeast(Screen.LEAST_HEIGHT)
    print(HOME + screen.paint(width, height).joinToString("\r\n") { shown(it) })
    System.out.flush()
}

/**
 * One row, with what sets its pieces apart turned into what a terminal understands.
 *
 * Every span is closed as it ends rather than left open to the next, which costs a few characters
 * and means a row can be cut anywhere without the rest of the screen taking its style.
 */
private fun shown(line: Line): String = line.spans.joinToString("") { span ->
    if (span.styles.isEmpty()) span.text
    else span.styles.joinToString("") { CODES.getValue(it) } + span.text + PLAIN
}

/** What each style is, in the escapes a terminal has understood since the VT100. */
private val CODES = mapOf(
    Style.BOLD to "\u001B[1m",
    Style.ITALIC to "\u001B[3m",
    Style.UNDERLINED to "\u001B[4m",
    Style.SELECTED to "\u001B[7m",
)

private const val PLAIN = "\u001B[0m"

/** The alternate screen, so a user's scrollback is where they left it, and no cursor. */
private const val ENTER = "\u001B[?1049h\u001B[?25l"

private const val LEAVE = "\u001B[?25h\u001B[?1049l"

/** Clear, then the top left corner. */
private const val HOME = "\u001B[2J\u001B[H"
