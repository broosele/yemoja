package yemoja.ui.tui

/*
 * What a keystroke is, and what the terminal reports for one.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/**
 * Key is a keystroke this interface answers to. Everything else is ignored.
 *
 * **A printable character is not given a meaning here.** `q` leaves where nothing is being typed
 * and is a letter inside an editor, and only the screen knows which it is — so [Typed] carries the
 * character and the screen says what it means. `TUI-6` saw this coming: *once enter opens an
 * editor, `q` is a character somebody is typing and escape means cancel this edit; ctrl-C is the
 * only one that survives intact.*
 *
 * Everything else is a movement or an instruction that means one thing wherever the reader is.
 */
sealed class Key {

    /**
     * A character the user typed, whatever it comes to mean.
     *
     * [FOLLOW] and [QUIT] are two of these, named for what they do where nothing is being typed.
     */
    data class Typed(val character: Char) : Key()

    object LEFT : Key()

    object RIGHT : Key()

    object UP : Key()

    object DOWN : Key()

    object NEXT_TAB : Key()

    object PREVIOUS_TAB : Key()

    /** Enter: one step further in, and into a value that is editing it. */
    object OPEN : Key()

    /** Escape: one step back out, and out of an editor that is abandoning the edit. */
    object CLOSE : Key()

    /** Remove whatever the reader is on: an item, an entry of a list, an entry under a key. */
    object DELETE : Key()

    /** Rub out the character before the cursor, which only an editor has anything to do with. */
    object BACKSPACE : Key()

    /**
     * Ctrl-C: leave, from wherever the reader is and whatever they are typing.
     *
     * The one key an editor does not take for itself, which is why it is not a [Typed].
     */
    object LEAVE : Key()

    companion object {

        /** Space, which follows a reference where nothing is being typed. */
        val FOLLOW: Key = Typed(' ')

        /** `q`, which leaves where nothing is being typed. */
        val QUIT: Key = Typed('q')
    }
}

/**
 * The key [name] stands for, or absent where this interface has nothing to do with it.
 *
 * [name] is a keystroke as the web names them, which is what the terminal library reports:
 * `ArrowUp` for a cursor key and the character itself for anything printable. Keeping the mapping
 * here rather than at the terminal leaves one place to look when a terminal reports something
 * unexpected, and lets it be tested without one.
 *
 * **It reports rather than decides.** A character comes back as itself, so what `q` or a space
 * means is settled where it is known whether an editor is open. Ctrl-C is the exception and is
 * given its meaning here, because it has no other.
 */
fun keyOf(name: String, ctrl: Boolean = false, shift: Boolean = false): Key? = when {
    ctrl -> if (name.equals("c", ignoreCase = true)) Key.LEAVE else null
    name == "ArrowLeft" -> Key.LEFT
    name == "ArrowRight" -> Key.RIGHT
    name == "ArrowUp" -> Key.UP
    name == "ArrowDown" -> Key.DOWN
    name == "Tab" -> if (shift) Key.PREVIOUS_TAB else Key.NEXT_TAB
    name == "Enter" -> Key.OPEN
    name == "Escape" -> Key.CLOSE
    name == "Delete" -> Key.DELETE
    name == "Backspace" -> Key.BACKSPACE
    name.length == 1 -> Key.Typed(name[0])
    else -> null
}
