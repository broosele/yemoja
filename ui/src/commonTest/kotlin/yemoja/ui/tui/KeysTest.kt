package yemoja.ui.tui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeysTest {

    @Test
    fun `the cursor keys move`() {
        assertEquals(Key.LEFT, keyOf("ArrowLeft"))
        assertEquals(Key.RIGHT, keyOf("ArrowRight"))
        assertEquals(Key.UP, keyOf("ArrowUp"))
        assertEquals(Key.DOWN, keyOf("ArrowDown"))
    }

    @Test
    fun `a printable character comes back as itself, whatever it comes to mean`() {
        // `q` leaves where nothing is being typed and is a letter inside an editor, and only the
        // screen knows which. So this reports rather than decides. `TUI-6`.
        assertEquals(Key.Typed('a'), keyOf("a"))
        assertEquals(Key.Typed('4'), keyOf("4"))
        assertEquals(Key.Typed('@'), keyOf("@"))
        assertEquals(Key.Typed('q'), keyOf("q"))
    }

    @Test
    fun `the two that leave are a character and a control`() {
        // Raw mode swallows the usual one, so `q` does it where nothing is being typed and
        // ctrl-C does it everywhere. Only the second has a meaning of its own here.
        assertEquals(Key.QUIT, keyOf("q"))
        assertEquals(Key.Typed('Q'), keyOf("Q"), "and the screen takes either case")
        assertEquals(Key.LEAVE, keyOf("c", ctrl = true))
    }

    @Test
    fun `escape closes, which is leaving where nothing is open`() {
        assertEquals(Key.CLOSE, keyOf("Escape"))
    }

    @Test
    fun `tab moves between tabs, and enter opens a field`() {
        assertEquals(Key.NEXT_TAB, keyOf("Tab"))
        assertEquals(Key.PREVIOUS_TAB, keyOf("Tab", shift = true))
        assertEquals(Key.FOLLOW, keyOf(" "))
        assertEquals(Key.OPEN, keyOf("Enter"))
    }

    @Test
    fun `the two an editor needs are keys of their own`() {
        assertEquals(Key.DELETE, keyOf("Delete"))
        assertEquals(Key.BACKSPACE, keyOf("Backspace"))
    }

    @Test
    fun `anything else is ignored rather than guessed at`() {
        assertNull(keyOf("F1"))
        assertNull(keyOf("PageDown"))
        assertNull(keyOf(""))
    }

    @Test
    fun `a held control key is not the letter it is held with`() {
        // Ctrl-Q is not quit and ctrl-A is not anything.
        assertNull(keyOf("q", ctrl = true))
        assertNull(keyOf("ArrowUp", ctrl = true))
    }
}
