package yemoja.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
            assertTrue(command.least <= command.arguments.size, "$name wants more than it names")
        }
    }

    @Test
    fun `the terminal interface takes a logbook and nothing else`() {
        val tui = COMMANDS.getValue("tui")
        assertEquals(listOf("logbook folder"), tui.arguments)
        assertEquals(1, tui.least, "a terminal with no logbook has nothing to show")
        assertEquals("<logbook folder>", tui.written)
    }

    @Test
    fun `the window may be given a logbook and will open without one`() {
        val gui = COMMANDS.getValue("gui")
        assertEquals(listOf("logbook folder"), gui.arguments)
        assertEquals(0, gui.least)
        assertEquals("[<logbook folder>]", gui.written, "written as what it may be given")
    }

    @Test
    fun `an installation keeps the dive computer library beside its jars`() {
        assertEquals(
            "/opt/yemoja/native",
            besideOf("/opt/yemoja/lib/ui-jvm.jar"),
            "lib and native are siblings, which is what installDist writes",
        )
        assertEquals(
            "D:/yemoja/native",
            besideOf("D:\\yemoja\\lib\\ui-jvm.jar"),
            "written with the separator every platform reads",
        )
    }

    @Test
    fun `anywhere else says nothing, and a development run is anywhere else`() {
        assertNull(besideOf(null))
        assertNull(besideOf("/opt/yemoja/lib/classes"), "a folder of classes is not a jar")
        assertNull(besideOf("/opt/yemoja/other/ui-jvm.jar"), "not under lib")
        assertNull(besideOf("lib/ui-jvm.jar"), "nothing above lib to put it beside")
    }

    @Test
    fun `the usage says every command there is`() {
        val usage = usage()
        for (name in COMMANDS.keys) assertTrue("yemoja $name" in usage, "$name should be listed")
        assertEquals(COMMANDS.size + 1, usage.lines().size, "one line each, under a heading")
    }
}
