package yemoja.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What `yemoja` answers when it is asked what it can do. */
class MainTest {

    @Test
    fun `every command names what follows it, and wants that many`() {
        // The count and the usage line come from one list, so they cannot disagree — which they
        // did when the count was read back out of the written form and a two-word name was two.
        for ((name, command) in COMMANDS) {
            assertEquals(
                command.arguments.size,
                Regex("<[^>]+>").findAll(command.written).count(),
                name,
            )
        }
    }

    @Test
    fun `the terminal interface takes a logbook and nothing else`() {
        val tui = COMMANDS.getValue("tui")
        assertEquals(listOf("logbook folder"), tui.arguments)
        assertEquals("<logbook folder>", tui.written)
    }

    @Test
    fun `the usage says every command there is`() {
        val usage = usage()
        for (name in COMMANDS.keys) assertTrue("yemoja $name" in usage, "$name should be listed")
        assertEquals(COMMANDS.size + 1, usage.lines().size, "one line each, under a heading")
    }
}
