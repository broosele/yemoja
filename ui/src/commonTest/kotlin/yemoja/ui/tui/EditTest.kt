package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.TextDescription
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
 * Typing into an open field, which is the whole of editing in this front end.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/** A screen over these files, and the store beneath it, so a save can be read back. */
private fun editable(vararg files: Pair<String, String>): Pair<MemoryFileStore, Screen> {
    val store = MemoryFileStore(mapOf(*files))
    val set: ItemSet = LogbookReader.read(store, Types.ALL)
    return store to Screen(Universe(set, null, store))
}

/**
 * Open what the cursor is on and start typing into it.
 *
 * Two presses: enter goes one step further in, and going further into a value is editing it.
 */
private fun edit(screen: Screen) {
    screen.press(Key.OPEN)
    screen.press(Key.OPEN)
}

/** Type [text], a character at a time, as a user would. */
private fun type(screen: Screen, text: String) {
    for (character in text) screen.press(Key.Typed(character))
}

private fun text(store: MemoryFileStore, path: String): String = store.readText(path)

/** One value typed over, saved, abandoned or cleared. */
class TypedFieldTest {

    private fun atName(): Pair<MemoryFileStore, Screen> {
        val (store, screen) = editable("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "name")
        return store to screen
    }

    @Test
    fun `enter starts typing, seeded with what is there`() {
        // The written form, so correcting a value is editing it rather than starting again.
        val (_, screen) = atName()
        edit(screen)
        assertEquals("North Sea", screen.typing)
    }

    @Test
    fun `what is typed is shown with a cursor after it`() {
        val (_, screen) = atName()
        edit(screen)
        type(screen, "!")
        assertTrue(body(screen).any { "North Sea!_" in it.text }, body(screen).toString())
    }

    @Test
    fun `backspace rubs out, and enter saves`() {
        val (store, screen) = atName()
        edit(screen)
        repeat(3) { screen.press(Key.BACKSPACE) }
        type(screen, "Ice")
        assertEquals("North Ice", screen.typing)
        screen.press(Key.OPEN)
        assertNull(screen.typing, "and typing has stopped")
        assertTrue("North Ice" in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `escape abandons the edit and changes nothing`() {
        val (store, screen) = atName()
        edit(screen)
        type(screen, "!!!")
        screen.press(Key.CLOSE)
        assertNull(screen.typing)
        assertTrue("North Sea" in text(store, "region.json"))
        assertTrue("!!!" !in text(store, "region.json"))
    }

    @Test
    fun `q is a letter while typing, and ctrl-C still leaves`() {
        // `TUI-6` said this would happen: once enter opens an editor, q is a character.
        val (_, screen) = atName()
        edit(screen)
        type(screen, "q")
        assertEquals("North Seaq", screen.typing)
        assertTrue(screen.running)
        screen.press(Key.LEAVE)
        assertTrue(!screen.running)
    }

    @Test
    fun `a value that will not do says why, and saving it does nothing`() {
        val (store, screen) = editable("region.json" to """{"north_sea": {}}""")
        toTab(screen, "region")
        toField(screen, "north")
        edit(screen)
        type(screen, "91")
        val said = body(screen)
        assertTrue(said.any { "! " in it.text && "-90.0" in it.text }, said.toString())
        screen.press(Key.OPEN)
        assertEquals("91", screen.typing, "still being typed, since it was not taken")
        assertTrue("91" !in text(store, "region.json"))
    }

    @Test
    fun `saving nothing clears the field`() {
        val (store, screen) = atName()
        edit(screen)
        repeat(20) { screen.press(Key.BACKSPACE) }
        assertEquals("", screen.typing)
        screen.press(Key.OPEN)
        assertTrue("name" !in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `a worked-out field is not offered`() {
        // What may not be written is a property of the field, not of this interface. `TUI-3`.
        val (_, screen) = editable("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "children")
        edit(screen)
        assertNull(screen.typing)
    }

    @Test
    fun `clearing a correction puts back what is worked out`() {
        val (_, screen) = editable(
            "dive/d#0.json" to """{"start_date": "2026-06-21", "start_time": "10:00:00",
                "end_time": "11:00:00", "duration": 60}""",
        )
        toTab(screen, "dive")
        toField(screen, "duration")
        edit(screen)
        repeat(6) { screen.press(Key.BACKSPACE) }
        screen.press(Key.OPEN)
        val read = screen.item!!.single<Double>("duration") as Result.Usable
        assertEquals(3600.0, read.value, "worked out from the times again")
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `the bar says what the keys do while typing, and does not offer to quit`() {
        val (_, screen) = atName()
        edit(screen)
        val said = screen.paint(90, 12).last().text.trim()
        assertTrue("[enter] save" in said, said)
        assertTrue("[esc] cancel" in said, said)
        assertTrue("[q]" !in said, said)
    }
}

/** One entry of a list, which is edited by writing the whole list back. */
class TypedEntryTest {

    private fun atBuddies(): Pair<MemoryFileStore, Screen> {
        val (store, screen) = editable(
            "person.json" to """{"anna": {}, "tom": {}}""",
            "dive/d#0.json" to """{"buddies": ["@anna", "@tom"]}""",
        )
        toTab(screen, "dive")
        toField(screen, "buddies")
        screen.press(Key.OPEN)
        return store to screen
    }

    @Test
    fun `the entry the cursor is on is the one edited`() {
        val (store, screen) = atBuddies()
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertEquals("@tom", screen.typing, "the entry the cursor is on")
        repeat(4) { screen.press(Key.BACKSPACE) }
        type(screen, "@anna")
        screen.press(Key.OPEN)
        val written = text(store, "dive/d#0.json")
        assertTrue(written.count { it == '@' } == 2, written)
        assertTrue("@tom" !in written, written)
    }

    @Test
    fun `the entries beside it are left as they were`() {
        val (store, screen) = atBuddies()
        screen.press(Key.OPEN)
        repeat(5) { screen.press(Key.BACKSPACE) }
        type(screen, "@nobody")
        screen.press(Key.OPEN)
        val written = text(store, "dive/d#0.json")
        assertTrue("@nobody" in written, written)
        assertTrue("@tom" in written, written)
    }

    @Test
    fun `emptying an entry takes it out of the list`() {
        val (store, screen) = atBuddies()
        screen.press(Key.OPEN)
        repeat(10) { screen.press(Key.BACKSPACE) }
        screen.press(Key.OPEN)
        val written = text(store, "dive/d#0.json")
        assertTrue("@anna" !in written, written)
        assertTrue("@tom" in written, written)
    }
}

/** Free text, the one kind that holds a line break. */
class TypedRemarkTest {

    @Test
    fun `a line break is typed as the two characters a row shows it as`() {
        // Enter saves, so a break cannot be typed. A row shows one as \n; typing that makes one.
        val (store, screen) = editable("region.json" to """{"north_sea": {}}""")
        toTab(screen, "region")
        toField(screen, "remarks")
        edit(screen)
        type(screen, "cold" + '\\' + "nand grey")
        screen.press(Key.OPEN)
        val read = screen.item!!.single<String>("remarks") as Result.Usable
        assertEquals("cold\nand grey", read.value)
    }
}

/** A field whose values are known, which is picked from rather than typed into. */
class ChosenValueTest {

    private fun atWaterType(held: String): Pair<MemoryFileStore, Screen> {
        val (store, screen) = editable("dive_site.json" to """{"blue_hole": {$held}}""")
        toTab(screen, "dive_site")
        toField(screen, "water_type")
        edit(screen)
        return store to screen
    }

    private fun rows(screen: Screen): List<String> =
        body(screen).map { it.text.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `enter offers the values rather than an empty line to type on`() {
        val (_, screen) = atWaterType("")
        assertNull(screen.typing, "nothing is being typed")
        assertTrue("salt" in rows(screen), rows(screen).toString())
        assertTrue("fresh" in rows(screen), rows(screen).toString())
    }

    @Test
    fun `the last row is the field holding nothing`() {
        val (_, screen) = atWaterType("")
        assertEquals("(nothing)", rows(screen).last())
    }

    @Test
    fun `the cursor starts on what the field holds`() {
        val (_, screen) = atWaterType(""""water_type": "fresh"""")
        assertEquals("fresh", rows(screen)[screen.choice!!])
    }

    @Test
    fun `and on the row that clears it where the field holds nothing`() {
        // Which says *none of these* by sitting there, and is where a value the set does not
        // contain leaves it too.
        val (_, screen) = atWaterType("")
        assertEquals("(nothing)", rows(screen)[screen.choice!!])
    }

    @Test
    fun `up and down move over the choices, round the ends`() {
        val (_, screen) = atWaterType("")
        val many = rows(screen).size
        screen.press(Key.DOWN)
        assertEquals(0, screen.choice, "past the last is the first again")
        screen.press(Key.UP)
        assertEquals(many - 1, screen.choice)
    }

    @Test
    fun `enter takes the one the cursor is on`() {
        val (store, screen) = atWaterType("")
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertNull(screen.choice, "and the chooser has closed")
        assertTrue("salt" in text(store, "dive_site.json"), text(store, "dive_site.json"))
    }

    @Test
    fun `the last row clears the field`() {
        val (store, screen) = atWaterType(""""water_type": "fresh"""")
        while (rows(screen)[screen.choice!!] != "(nothing)") screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertEquals(Result.Absent, screen.item!!.read("water_type"))
        assertTrue("water_type" !in text(store, "dive_site.json"))
    }

    @Test
    fun `escape leaves the value as it was`() {
        val (store, screen) = atWaterType(""""water_type": "fresh"""")
        screen.press(Key.DOWN)
        screen.press(Key.CLOSE)
        assertNull(screen.choice)
        assertTrue("fresh" in text(store, "dive_site.json"))
    }

    @Test
    fun `the bar says the three keys a chooser has`() {
        val (_, screen) = atWaterType("")
        val said = screen.paint(90, 12).last().text.trim()
        assertEquals("[enter] take it | [esc] cancel | [^,v] choice", said)
    }

    @Test
    fun `a boolean is two values and a box`() {
        val (store, screen) = editable("gear.json" to """{"faber_12": {}}""")
        toTab(screen, "gear")
        toField(screen, "generic")
        edit(screen)
        assertEquals(listOf("[ ] true", "[ ] false", "[x] (nothing)"), rows(screen))
        screen.press(Key.DOWN)
        assertEquals(listOf("[x] true", "[ ] false", "[ ] (nothing)"), rows(screen))
        screen.press(Key.OPEN)
        assertTrue("true" in text(store, "gear.json"), text(store, "gear.json"))
    }

    @Test
    fun `free text is still typed into`() {
        val (_, screen) = editable("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "name")
        edit(screen)
        assertEquals("North Sea", screen.typing)
        assertNull(screen.choice)
    }

    @Test
    fun `a fixed set offers no way out of it`() {
        // `DATA-24` makes a value outside one unusable, so there is nothing to leave to.
        val (_, screen) = atWaterType("")
        assertTrue(rows(screen).none { it.startsWith("other") }, rows(screen).toString())
    }
}

/** Invented, because no described type holds a list of a fixed set. `TEST-4`. */
private val COLOURS = setOf("red", "green", "blue")

private val FLAG = ItemDescription(
    "flag",
    listOf(TextDescription("stripes", fixedSet = COLOURS, cardinality = Cardinality.LIST)),
)

class ChosenEntryTest {

    private fun atStripes(held: String): Pair<MemoryFileStore, Screen> {
        val store = MemoryFileStore(mapOf("flag.json" to """{"a": {"stripes": $held}}"""))
        val screen = Screen(Universe(LogbookReader.read(store, listOf(FLAG)), null, store))
        screen.press(Key.OPEN)
        return store to screen
    }

    private fun rows(screen: Screen): List<String> =
        body(screen).map { it.text.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `one entry of a list is chosen from, the others left alone`() {
        val (store, screen) = atStripes("""["red", "blue"]""")
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertEquals("blue", rows(screen)[screen.choice!!], "the entry the cursor is on")
        screen.press(Key.UP)
        screen.press(Key.OPEN)
        val written = text(store, "flag.json")
        assertTrue("green" in written, written)
        assertTrue("red" in written, "and the one beside it stayed: $written")
    }

    @Test
    fun `clearing an entry takes it out of the list`() {
        val (store, screen) = atStripes("""["red", "blue"]""")
        screen.press(Key.OPEN)
        while (rows(screen)[screen.choice!!] != "(nothing)") screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        val written = text(store, "flag.json")
        assertTrue("red" !in written, written)
        assertTrue("blue" in written, written)
    }
}

/** What the rest of the keyboard does while an editor is open, which is nothing. */
class EditorHoldsTest {

    @Test
    fun `moving keys are still while a value is typed`() {
        // A key that changed which field was chosen would leave the editor saving into somewhere
        // the reader is no longer looking at.
        val (store, screen) = editable("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "name")
        edit(screen)
        repeat(9) { screen.press(Key.BACKSPACE) }
        type(screen, "Irish Sea")
        screen.press(Key.DOWN)
        screen.press(Key.RIGHT)
        screen.press(Key.NEXT_TAB)
        assertEquals("Irish Sea", screen.typing, "still the same edit")
        assertEquals("region", screen.type.name, "and still the same tab")
        screen.press(Key.OPEN)
        assertTrue("Irish Sea" in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `and while a value is chosen, apart from the two that move over it`() {
        val (_, screen) = editable("dive_site.json" to """{"blue_hole": {}}""")
        toTab(screen, "dive_site")
        toField(screen, "water_type")
        edit(screen)
        screen.press(Key.RIGHT)
        screen.press(Key.NEXT_TAB)
        assertEquals("dive_site", screen.type.name)
        assertTrue(screen.choice != null, "and the chooser is still open")
    }
}

/** A set that is offered rather than enforced, which is picked from or written past. */
class ChosenOrTypedTest {

    private fun atCategory(held: String): Pair<MemoryFileStore, Screen> {
        val (store, screen) = editable("region.json" to """{"north_sea": {$held}}""")
        toTab(screen, "region")
        toField(screen, "category")
        edit(screen)
        return store to screen
    }

    private fun rows(screen: Screen): List<String> =
        body(screen).map { it.text.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `the suggestions are offered, with a way past them`() {
        val (_, screen) = atCategory("")
        val said = rows(screen)
        assertTrue("sea" in said, said.toString())
        assertEquals(listOf("other:", "(nothing)"), said.takeLast(2))
    }

    @Test
    fun `one of them is taken like any other choice`() {
        val (store, screen) = atCategory("")
        while (rows(screen)[screen.choice!!] != "sea") screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertNull(screen.choice)
        assertTrue("sea" in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `enter on the other row starts typing rather than saving`() {
        val (store, screen) = atCategory("")
        while (!rows(screen)[screen.choice!!].startsWith("other")) screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertEquals("", screen.typing)
        type(screen, "archipelago")
        val said = rows(screen)
        assertTrue(said.any { it == "other: archipelago" + CURSOR }, said.toString())
        screen.press(Key.OPEN)
        assertNull(screen.typing)
        assertNull(screen.choice, "and the chooser has closed with it")
        assertTrue("archipelago" in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `escape from there comes back to the choices rather than out`() {
        // A step in is undone by a step out, and picking *other* was a step in.
        val (_, screen) = atCategory("")
        while (!rows(screen)[screen.choice!!].startsWith("other")) screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        type(screen, "x")
        screen.press(Key.CLOSE)
        assertNull(screen.typing)
        assertTrue(screen.choice != null, "still choosing")
        assertEquals("other:", rows(screen)[screen.choice!!], "and on the row it came from")
    }

    @Test
    fun `a value the suggestions do not offer sits on the other row`() {
        // Which is what it is. Nothing else on the screen would say where the value went.
        val (_, screen) = atCategory(""""category": "archipelago"""")
        assertEquals("other: archipelago", rows(screen)[screen.choice!!])
    }

    @Test
    fun `and the editor there starts from that value`() {
        val (store, screen) = atCategory(""""category": "archipelago"""")
        screen.press(Key.OPEN)
        assertEquals("archipelago", screen.typing)
        repeat(5) { screen.press(Key.BACKSPACE) }
        screen.press(Key.OPEN)
        assertTrue("archi" in text(store, "region.json"), text(store, "region.json"))
    }

    @Test
    fun `the bar offers a step back rather than a way out`() {
        val (_, screen) = atCategory("")
        while (!rows(screen)[screen.choice!!].startsWith("other")) screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        val said = screen.paint(90, 12).last().text.trim()
        assertEquals("[enter] save | [esc] back | [backspace] rub out", said)
    }
}
