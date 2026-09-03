package yemoja.ui.tui

import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import yemoja.logic.Logbook
import yemoja.logic.Types
import kotlin.system.exitProcess

/*
 * The terminal itself: the one place that reads a keyboard and puts characters on a screen.
 * Screen decides what those characters are and knows nothing about any of this.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/** What the interface answers to, shown when it is started with the wrong arguments. */
private const val USAGE = "yemoja-tui <logbook folder>"

/**
 * Open the logbook named on the command line and show it until the user leaves.
 *
 * One argument, because a logbook is a folder and this reads exactly one. **Nothing is written**:
 * there is no writer anywhere in the project yet, so this cannot change a logbook whatever a user
 * presses.
 */
fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val set = try {
        Logbook.open(args[0])
    } catch (refused: RuntimeException) {
        // A folder that is not a logbook, or a file in it that will not read. Either way the
        // message names what was wrong, and a terminal that never started needs no tidying up.
        System.err.println("${args[0]} could not be read: ${refused.message}")
        exitProcess(1)
    }
    show(Screen(set, Types.ALL))
}

/**
 * Draw [screen] and answer keys until it stops running.
 *
 * The terminal is put back the way it was found whatever happens, including the alternate screen
 * and the cursor, which raw mode alone does not restore.
 */
private fun show(screen: Screen) {
    val terminal = Terminal()
    val raw = try {
        terminal.enterRawMode()
    } catch (without: IllegalStateException) {
        // Piped, redirected, or run by a build tool. Nothing is broken and there is nothing to
        // tidy up, since the terminal has not been touched yet.
        System.err.println("$USAGE needs a terminal: ${without.message}")
        exitProcess(1)
    }
    print(ENTER)
    try {
        raw.use {
            while (screen.running) {
                paint(terminal, screen)
                val event = it.readKey() ?: break
                keyOf(event.key, event.ctrl)?.let { key -> screen.press(key) }
            }
        }
    } finally {
        print(LEAVE)
        System.out.flush()
    }
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
    print(HOME + screen.paint(width, height).joinToString("\r\n"))
    System.out.flush()
}

/** The alternate screen, so a user's scrollback is where they left it, and no cursor. */
private const val ENTER = "\u001B[?1049h\u001B[?25l"

private const val LEAVE = "\u001B[?25h\u001B[?1049l"

/** Clear, then the top left corner. */
private const val HOME = "\u001B[2J\u001B[H"
