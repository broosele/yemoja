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
    fun `three keys leave, because raw mode swallows the usual one`() {
        assertEquals(Key.QUIT, keyOf("q"))
        assertEquals(Key.QUIT, keyOf("Q"))
        assertEquals(Key.QUIT, keyOf("Escape"))
        assertEquals(Key.QUIT, keyOf("c", ctrl = true))
    }

    @Test
    fun `anything else is ignored rather than guessed at`() {
        assertNull(keyOf("a"))
        assertNull(keyOf("Enter"))
        assertNull(keyOf("F1"))
        assertNull(keyOf(""))
    }

    @Test
    fun `a held control key is not the letter it is held with`() {
        // Ctrl-Q is not quit and ctrl-A is not anything.
        assertNull(keyOf("q", ctrl = true))
        assertNull(keyOf("ArrowUp", ctrl = true))
    }
}
