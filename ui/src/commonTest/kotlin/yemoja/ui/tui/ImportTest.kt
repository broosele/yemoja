package yemoja.ui.tui

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Items arriving from somewhere else, shown in the tab they will end up in.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/** A screen over [held], with [coming] arriving into it. */
private fun importing(held: String, coming: String): Pair<MemoryFileStore, Screen> {
    val store = MemoryFileStore(mapOf("region.json" to held))
    val universe = Universe(LogbookReader.read(store, Types.ALL), null, store)
    val source = LogbookReader.read(MemoryFileStore(mapOf("region.json" to coming)), Types.ALL)
    universe.importFrom(source, MemoryFileStore(emptyMap()))
    val screen = Screen(universe)
    toTab(screen, "region")
    return store to screen
}

/** The list column, without the two rows of chrome above it and the two below. */
private fun column(screen: Screen): List<String> =
    screen.paint(90, 12).drop(2).dropLast(2)
        .map { it.spans.first().text.trim() }.filter { it.isNotEmpty() }

class ArrivingListTest {

    @Test
    fun `what is arriving is listed above what is held, and the cursor starts on it`() {
        // In the tab its type would put it in, so it is read against what is already there.
        val (_, screen) = importing("""{"held": {}}""", """{"coming": {}}""")
        assertEquals(listOf("> coming", "held"), column(screen))
    }

    @Test
    fun `an arriving item is set in italic`() {
        // Which is what italic means on a value too: not held here as written.
        val (_, screen) = importing("""{"held": {}}""", """{"coming": {}}""")
        val rows = screen.paint(90, 12)
        val coming = rows.first { "coming" in it.spans.first().text }
        val held = rows.first { "held" in it.spans.first().text }
        assertTrue(Style.ITALIC in coming.spans.first().styles, coming.text)
        assertTrue(Style.ITALIC !in held.spans.first().styles, held.text)
    }

    @Test
    fun `an arriving item is edited where it arrived, not where it is going`() {
        val (store, screen) = importing("""{"held": {}}""", """{"coming": {"name": "As sent"}}""")
        toField(screen, "name")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        for (character in "!") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertTrue("As sent!" !in store.readText("region.json"), store.readText("region.json"))
        assertEquals("As sent!", (screen.item!!.single<String>("name") as Result.Usable).value)
    }
}

class TakenInTest {

    @Test
    fun `insert takes the arriving item in and it leaves the list`() {
        val (store, screen) = importing("{}", """{"coming": {"name": "A"}}""")
        screen.press(Key.INSERT)
        assertTrue("coming" in store.readText("region.json"), store.readText("region.json"))
        assertEquals(listOf("> coming"), column(screen), "still listed, now as one that is held")
        val rows = screen.paint(90, 12)
        assertTrue(rows.none { Style.ITALIC in it.spans.first().styles }, "nothing is arriving now")
    }

    @Test
    fun `an item that cannot go in says why and stays`() {
        val (_, screen) = importing("{}", """{"coming": {"north": 91}}""")
        screen.press(Key.INSERT)
        val bar = screen.paint(120, 12).last().text.trim()
        assertTrue(bar.startsWith("! "), bar)
        assertTrue("-90.0" in bar, bar)
        assertEquals(1, screen.items.size, "and it is still there to be fixed")
    }

    @Test
    fun `the message goes when the next key is pressed`() {
        val (_, screen) = importing("{}", """{"coming": {"north": 91}}""")
        screen.press(Key.INSERT)
        screen.press(Key.DOWN)
        assertTrue(!screen.paint(120, 12).last().text.trim().startsWith("!"))
    }

    @Test
    fun `delete asks, then leaves it out of the import`() {
        val (store, screen) = importing("{}", """{"coming": {}}""")
        screen.press(Key.DELETE)
        assertEquals(1, screen.items.size, "asked, not done")
        screen.press(Key.DELETE)
        assertEquals(0, screen.items.size)
        assertTrue("coming" !in store.readText("region.json"), "and it went nowhere")
    }

    @Test
    fun `the bar offers both, and only where something is arriving`() {
        val (_, screen) = importing("""{"held": {}}""", """{"coming": {}}""")
        val bar = { screen.paint(160, 12).last().text.trim() }
        assertTrue("[ins] take it in" in bar(), bar())
        assertTrue("[del] leave it out" in bar(), bar())
        screen.press(Key.RIGHT)
        assertTrue("[ins]" !in bar(), "not on an item already held: " + bar())
        assertTrue("[del] delete item" in bar(), "which is deleted rather than left out: " + bar())
    }
}

class ImportScreenTest {

    private fun screen(): Screen {
        val store = MemoryFileStore(mapOf("region.json" to "{}"))
        return Screen(Universe(LogbookReader.read(store, Types.ALL), null, store))
    }

    private fun body(screen: Screen): List<String> =
        screen.paint(90, 12).map { it.text.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `plus opens it, and it offers the two an import can come from`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        assertTrue("import" in body(screen).first(), body(screen).toString())
        assertTrue("another logbook" in body(screen), body(screen).toString())
        assertTrue("a dive computer" in body(screen), body(screen).toString())
    }

    @Test
    fun `a dive computer says it is not built`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { "FEAT-3" in it }, body(screen).toString())
    }

    @Test
    fun `another logbook asks for a folder`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.OPEN)
        for (character in "nowhere") screen.press(Key.Typed(character))
        assertTrue(body(screen).any { it.startsWith("folder: nowhere") }, body(screen).toString())
    }

    @Test
    fun `a folder that is not there says so and stays open`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.OPEN)
        for (character in "nowhere") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { it.startsWith("!") }, body(screen).toString())
        assertTrue(body(screen).any { "another logbook" in it }, "still on the import screen")
    }

    @Test
    fun `escape comes back out of it`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.CLOSE)
        assertTrue("region" in body(screen).first(), body(screen).first())
        assertNull(screen.typing)
    }
}
