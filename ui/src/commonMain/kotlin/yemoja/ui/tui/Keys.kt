package yemoja.ui.tui

/**
 * The key [name] stands for, or absent where this interface has nothing to do with it.
 *
 * [name] is a keystroke as the web names them, which is what the terminal library reports:
 * `ArrowUp` for a cursor key and the character itself for anything printable. Keeping the mapping
 * here rather than at the terminal leaves one place to look when a terminal reports something
 * unexpected, and lets it be tested without one.
 *
 * Leaving is three keys because a terminal in raw mode swallows the usual one: `q`, escape, and
 * ctrl-C all mean the same thing.
 */
fun keyOf(name: String, ctrl: Boolean = false): Key? = when {
    ctrl -> if (name.equals("c", ignoreCase = true)) Key.QUIT else null
    name == "ArrowLeft" -> Key.LEFT
    name == "ArrowRight" -> Key.RIGHT
    name == "ArrowUp" -> Key.UP
    name == "ArrowDown" -> Key.DOWN
    name == "Escape" -> Key.QUIT
    name.equals("q", ignoreCase = true) -> Key.QUIT
    else -> null
}
