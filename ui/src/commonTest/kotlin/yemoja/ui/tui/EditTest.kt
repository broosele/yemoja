package yemoja.ui.tui

import yemoja.data.ItemSet
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
        val (store, screen) = editable("dive_site.json" to """{"blue_hole": {}}""")
        toTab(screen, "dive_site")
        toField(screen, "water_type")
        edit(screen)
        type(screen, "brackish")
        val said = body(screen)
        assertTrue(said.any { "! " in it.text && "salt" in it.text }, said.toString())
        screen.press(Key.OPEN)
        assertEquals("brackish", screen.typing, "still being typed, since it was not taken")
        assertTrue("brackish" !in text(store, "dive_site.json"))
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
