package yemoja.ui.tui

import com.github.ajalt.mordant.input.RawModeScope
import com.github.ajalt.mordant.terminal.Terminal

/*
 * A line typed while a download holds the screen.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md, `LOGIC-24`.
 */

/**
 * Put [question] on the terminal's bottom line and answer with what was typed, or absent where
 * the user pressed escape or the input ended.
 *
 * Reads the keyboard itself, because the screen's own loop is not running: the download that
 * asked is holding it. Enter answers, escape gives up, backspace takes a character back, and
 * anything else that is a single character is typed.
 */
internal fun prompted(terminal: Terminal, raw: RawModeScope, question: String): String? {
    val typed = StringBuilder()
    while (true) {
        val row = terminal.size.height.coerceAtLeast(Screen.LEAST_HEIGHT)
        print("[$row;1H[2K$question $typed")
        System.out.flush()
        val event = raw.readKeyOrNull() ?: return null
        when {
            event.ctrl -> Unit
            event.key == "Enter" -> return typed.toString()
            event.key == "Escape" -> return null
            event.key == "Backspace" -> if (typed.isNotEmpty()) typed.setLength(typed.length - 1)
            event.key.length == 1 -> typed.append(event.key)
        }
    }
}
