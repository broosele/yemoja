package yemoja.ui.tui

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Making a new thing, and removing the one the cursor is on.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

private fun over(vararg files: Pair<String, String>): Pair<MemoryFileStore, Screen> {
    val store = MemoryFileStore(mapOf(*files))
    return store to Screen(Universe(LogbookReader.read(store, Types.ALL), null, store))
}

private val NEW = Key.Typed('n')

/** An item of the open type, made and deleted from the list. */
class MadeItemTest {

    @Test
    fun `n makes one of the open type, and the cursor lands on it`() {
        val (store, screen) = over("person.json" to """{"anna": {"first_name": "Anna"}}""")
        toTab(screen, "person")
        screen.press(NEW)
        assertEquals(2, screen.items.size)
        val made = screen.item!!
        assertEquals(Result.Absent, made.read("first_name"), "the empty one, not the one there")
        assertTrue("unknown_person" in store.readText("person.json"), store.readText("person.json"))
    }

    @Test
    fun `a made item can be typed into straight away`() {
        val (store, screen) = over("person.json" to "{}")
        toTab(screen, "person")
        screen.press(NEW)
        toField(screen, "first_name")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        for (character in "Tom") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertTrue("Tom" in store.readText("person.json"), store.readText("person.json"))
    }

    @Test
    fun `delete asks before it removes an item`() {
        // Nothing can be undone until FEAT-4, and a dive holds a recording nobody types again.
        val (store, screen) = over("person.json" to """{"anna": {}, "tom": {}}""")
        toTab(screen, "person")
        screen.press(Key.DELETE)
        assertEquals(2, screen.items.size, "asked, not done")
        screen.press(Key.DELETE)
        assertEquals(1, screen.items.size)
        assertTrue("anna" !in store.readText("person.json"), store.readText("person.json"))
    }

    @Test
    fun `anything else answers no`() {
        val (_, screen) = over("person.json" to """{"anna": {}, "tom": {}}""")
        toTab(screen, "person")
        screen.press(Key.DELETE)
        screen.press(Key.DOWN)
        screen.press(Key.DELETE)
        assertEquals(2, screen.items.size, "the second delete asked again rather than doing it")
    }
}

/** One value of an open list. */
class MadeEntryTest {

    private fun atBuddies(held: String): Pair<MemoryFileStore, Screen> {
        val (store, screen) = over(
            "person.json" to """{"anna": {}}""",
            "dive/d#0.json" to """{"buddies": $held}""",
        )
        toTab(screen, "dive")
        toField(screen, "buddies")
        screen.press(Key.OPEN)
        return store to screen
    }

    private fun buddies(screen: Screen): List<String> {
        val read = screen.item!!.list<Reference>("buddies")
        if (read !is Result.Usable) return emptyList()
        return read.value.mapNotNull {
            when (val one = (it as? Element.Usable)?.value) {
                is Reference.Identified -> "@" + one.id
                is Reference.OneOff -> one.name
                else -> null
            }
        }
    }

    @Test
    fun `n puts an entry at the place the cursor is on`() {
        // Three values have four places and n uses the one under the cursor, so a buddy can go
        // between two others rather than only after them all.
        val (store, screen) = atBuddies("""["@anna", "@tom"]""")
        screen.press(Key.DOWN)
        screen.press(NEW)
        screen.press(Key.OPEN)
        for (character in "@sam") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertEquals(listOf("@anna", "@sam", "@tom"), buddies(screen))
        assertTrue("@sam" in store.readText("dive/d#0.json"))
    }

    @Test
    fun `the last place is where one goes on the end`() {
        val (_, screen) = atBuddies("""["@anna"]""")
        screen.press(Key.DOWN)
        screen.press(NEW)
        screen.press(Key.OPEN)
        for (character in "@tom") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertEquals(listOf("@anna", "@tom"), buddies(screen))
    }

    @Test
    fun `typing at a place with nothing in it adds a value there`() {
        // Which is what an empty list is: one place, nothing in it. n is not needed first.
        val (store, screen) = atBuddies("[]")
        screen.press(Key.OPEN)
        for (character in "@anna") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertEquals(listOf("@anna"), buddies(screen))
        assertTrue("@anna" in store.readText("dive/d#0.json"), store.readText("dive/d#0.json"))
    }

    @Test
    fun `delete does nothing at the place after the last value`() {
        val (_, screen) = atBuddies("""["@anna"]""")
        screen.press(Key.DOWN)
        screen.press(Key.DELETE)
        assertEquals(listOf("@anna"), buddies(screen))
    }

    @Test
    fun `delete takes the entry out without asking`() {
        // One value, and putting it back is typing it, so it does not earn a question.
        val (_, screen) = atBuddies("""["@anna", "@tom"]""")
        screen.press(Key.DELETE)
        assertEquals(listOf("@tom"), buddies(screen))
    }
}

/** The one owned item a field holds, made where it holds none and cleared again. */
class MadeOwnedItemTest {

    @Test
    fun `n creates the owned item a field holds none of`() {
        val (store, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(NEW)
        assertIs<Result.Usable<*>>(screen.item!!.read("medical"), "there to be filled in")
        // An empty block says exactly what no block says, so it waits for a field. `DATA-116`.
        assertTrue("medical" !in store.readText("person.json"), "and nothing stored yet")
    }

    @Test
    fun `delete clears it from inside it`() {
        // Opening a field that holds one goes into it, so that is where a reader stands.
        val (store, screen) = over("person.json" to """{"anna": {"medical": {"height": 1.8}}}""")
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.DELETE)
        assertEquals(Result.Absent, screen.item!!.read("medical"))
        assertTrue("height" !in store.readText("person.json"), store.readText("person.json"))
    }
}

/** An entry of a keyed collection, under a key nobody typed. */
class MadeKeyedEntryTest {

    @Test
    fun `n adds an entry under a key its own type proposed`() {
        // A hand-made entry is empty, so it takes its type's fallback, exactly as a hand-made
        // item becomes unknown_person. `JSON-18`.
        val (store, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(NEW)
        val held = screen.item!!.keyed<OwnedItem>("courses")
        assertIs<Result.Usable<*>>(held)
        assertEquals(listOf("course"), (held as Result.Usable).value.keys.toList())
        assertTrue("course" in store.readText("person.json"))
    }

    @Test
    fun `a second takes the next free key`() {
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(NEW)
        screen.press(NEW)
        val held = screen.item!!.keyed<OwnedItem>("courses") as Result.Usable
        assertEquals(listOf("course", "course#1"), held.value.keys.toList())
    }

    @Test
    fun `delete takes the entry under the chosen key out`() {
        val (_, screen) = over(
            "person.json" to """{"anna": {"courses": {"k1": {}, "k2": {}}}}""",
        )
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(Key.DELETE)
        val held = screen.item!!.keyed<OwnedItem>("courses") as Result.Usable
        assertEquals(listOf("k2"), held.value.keys.toList())
    }

    @Test
    fun `a made entry can be filled in straight away`() {
        val (store, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(NEW)
        // Inside an entry the cursor moves over that entry's own fields, which the rows beside
        // the list do not list, so this counts them off the description as the screen does.
        val within = (Types.PERSON["courses"] as OwnedItemDescription).description
        repeat(within.fields.indexOfFirst { it.name == "date" }) { screen.press(Key.DOWN) }
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        for (character in "2022-06-01") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertTrue("2022-06-01" in store.readText("person.json"), store.readText("person.json"))
    }
}

/** What the bar says about the two keys that change what the logbook holds. */
class MakingOfferedTest {

    // Wide enough for the whole bar: what a narrow one drops is the tail, and these are it.
    private fun bar(screen: Screen): String = screen.paint(160, 12).last().text.trim()

    @Test
    fun `it offers to delete an item once there is one`() {
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        assertTrue("[del] delete item" in bar(screen), bar(screen))
    }

    @Test
    fun `the question names what would go`() {
        // Delete moves nothing, but the list shows the next item under the cursor after it, which
        // is a poor thing to have to guess from.
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        screen.press(Key.DELETE)
        assertEquals("delete anna? | [del] delete | [any key] keep", bar(screen))
    }

    @Test
    fun `the question goes away when it is answered`() {
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        screen.press(Key.DELETE)
        screen.press(Key.DOWN)
        assertTrue("delete anna?" !in bar(screen), bar(screen))
    }

    @Test
    fun `an entry can be made and taken out of a collection`() {
        val (_, screen) = over("person.json" to """{"anna": {"courses": {"k1": {}}}}""")
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        val said = bar(screen)
        assertTrue("[n] new entry" in said, said)
        assertTrue("[del] delete entry" in said, said)
    }

    @Test
    fun `an owned item is offered by its own name`() {
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertTrue("[n] new medical" in bar(screen), bar(screen))
        screen.press(NEW)
        assertTrue("[del] delete medical" in bar(screen), bar(screen))
    }

    @Test
    fun `it offers nothing at a plain value`() {
        val (_, screen) = over("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "name")
        screen.press(Key.OPEN)
        val said = bar(screen)
        assertTrue("[n]" !in said, said)
        assertTrue("[del]" !in said, said)
    }
}

/** What the bar leaves out, which is as decided as what it says. */
class UnsaidTest {

    private fun bar(screen: Screen): String = screen.paint(160, 12).last().text.trim()

    @Test
    fun `nothing inside a field offers to quit`() {
        // `q` does not work there, and a bar that offered it would be lying about a key.
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertTrue("[q] quit" !in bar(screen), bar(screen))
        assertTrue("[esc] back" in bar(screen), bar(screen))
    }

    @Test
    fun `an editor says nothing about ctrl-C`() {
        // It works, from an editor as from anywhere. It is not worth a place on a bar that has
        // three keys to explain and no room to spare.
        val (_, screen) = over("region.json" to """{"north_sea": {"name": "North Sea"}}""")
        toTab(screen, "region")
        toField(screen, "name")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        assertEquals("[enter] save | [esc] cancel | [backspace] rub out", bar(screen))
        assertFalse(screen.press(Key.LEAVE), "and it still leaves")
    }

    @Test
    fun `a field that holds an item is opened into rather than typed into`() {
        // Its own fields are what an editor reaches, one at a time. Enter twice used to put a
        // text editor on the field itself, which nothing below would have taken.
        val (_, screen) = over("person.json" to """{"anna": {}}""")
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        assertNull(screen.typing)
    }
}

/** The places of an open list, which are one more than its values. */
class ListPlacesTest {

    private fun rows(screen: Screen): List<String> =
        body(screen, height = 24).map { it.text.trim() }

    private fun bar(screen: Screen): String = screen.paint(160, 12).last().text.trim()

    private fun atBuddies(held: String): Screen {
        val (_, screen) = over("dive/d#0.json" to """{"buddies": $held}""")
        toTab(screen, "dive")
        toField(screen, "buddies")
        screen.press(Key.OPEN)
        return screen
    }

    /** Which row the cursor is on. */
    private fun at(screen: Screen): Int = body(screen, height = 24)
        .indexOfFirst { row -> row.spans.any { Style.SELECTED in it.styles } }

    @Test
    fun `two values are three places`() {
        val screen = atBuddies("""["@anna", "@tom"]""")
        val first = at(screen)
        repeat(3) { screen.press(Key.DOWN) }
        assertEquals(first, at(screen), "three presses come back round")
        screen.press(Key.DOWN)
        assertTrue(at(screen) != first, "and a fourth does not")
        assertEquals(2, rows(screen).count { it.startsWith("- ") }, rows(screen).toString())
    }

    @Test
    fun `the place after the last carries no bullet`() {
        // A bullet with nothing after it reads as a value somebody left blank.
        val screen = atBuddies("""["@anna"]""")
        screen.press(Key.DOWN)
        val at = body(screen, height = 24)
            .indexOfFirst { row -> row.spans.any { Style.SELECTED in it.styles } }
        assertEquals("", body(screen, height = 24)[at].text.trim())
    }

    @Test
    fun `the bar offers to type one there rather than to make one`() {
        // `n` and enter would be the same key twice at a place holding nothing.
        val screen = atBuddies("""["@anna"]""")
        assertTrue("[n] new entry" in bar(screen), bar(screen))
        screen.press(Key.DOWN)
        assertTrue("[enter] new entry" in bar(screen), bar(screen))
        assertTrue("[n] new entry" !in bar(screen), bar(screen))
        assertTrue("[del]" !in bar(screen), "and nothing to take out: " + bar(screen))
    }
}
