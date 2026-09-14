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
        assertTrue("a logbook folder or a UDDF file" in body(screen), body(screen).toString())
        assertTrue("a dive computer" in body(screen), body(screen).toString())
    }

    @Test
    fun `a dive computer with nothing attached says so`() {
        // This universe was given nothing to look with, which is the same answer as nothing
        // being plugged in: the front end supplies the platform. `LOGIC-2`.
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { "no dive computer" in it }, body(screen).toString())
    }

    @Test
    fun `it asks for one path, whether that names a folder or a file`() {
        // What is there says how it is read, which is one question fewer to put to somebody
        // who already knows what they are pointing at.
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.OPEN)
        for (character in "nowhere") screen.press(Key.Typed(character))
        assertTrue(body(screen).any { it.startsWith("path: nowhere") }, body(screen).toString())
    }

    @Test
    fun `a path that is neither says so and stays open`() {
        val screen = screen()
        screen.press(Key.Typed('+'))
        screen.press(Key.OPEN)
        for (character in "nowhere") screen.press(Key.Typed(character))
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { it.startsWith("!") }, body(screen).toString())
        assertTrue(body(screen).any { "UDDF" in it }, "still on the import screen")
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

/** What insert asks where nothing could be matched by id. */
class AskedOnInsertTest {

    private fun unmatched(held: String, coming: String): Pair<MemoryFileStore, Screen> {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to held))
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("dive/x#0.json" to coming))
        val source = LogbookReader.read(from, Types.ALL)
        universe.importFrom(source, MemoryFileStore(emptyMap()), yemoja.logic.Matching.NONE)
        val screen = Screen(universe)
        toTab(screen, "dive")
        return store to screen
    }

    private fun body(screen: Screen): List<String> =
        screen.paint(90, 12).map { it.text.trim() }.filter { it.isNotEmpty() }

    private fun dived(at: String) =
        """{"start_date": "2024-06-15", "start_time": "$at", "duration": 3600}"""

    @Test
    fun `it asks, offering as new and the dives already held`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("14:00:00"))
        screen.press(Key.INSERT)
        assertTrue("as new" in body(screen), body(screen).toString())
        assertTrue("the same as d#0" in body(screen), body(screen).toString())
    }

    @Test
    fun `one that overlaps in time is what it starts on`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("10:30:00"))
        screen.press(Key.INSERT)
        val at = screen.paint(90, 12).indexOfFirst { row ->
            row.spans.any { Style.SELECTED in it.styles }
        }
        val said = screen.paint(90, 12)[at].text
        assertTrue("the same as d#0" in said, said)
    }

    @Test
    fun `one that overlaps nothing starts on as new`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("14:00:00"))
        screen.press(Key.INSERT)
        val at = screen.paint(90, 12).first { row -> row.spans.any { Style.SELECTED in it.styles } }
        assertTrue("as new" in at.text, at.text)
    }

    @Test
    fun `enter takes the answer the cursor is on`() {
        val (store, screen) = unmatched(dived("10:00:00"), dived("10:30:00"))
        screen.press(Key.INSERT)
        screen.press(Key.OPEN)
        assertEquals(1, screen.items.size, "one dive, not two")
        assertTrue("10:30:00" in store.readText("dive/d#0.json"), store.readText("dive/d#0.json"))
    }

    @Test
    fun `as new puts it in beside the one already there`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("10:30:00"))
        screen.press(Key.INSERT)
        screen.press(Key.UP)
        screen.press(Key.OPEN)
        assertEquals(2, screen.items.size)
    }

    @Test
    fun `escape leaves it in the review`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("10:30:00"))
        screen.press(Key.INSERT)
        screen.press(Key.CLOSE)
        assertEquals(2, screen.items.size, "the arriving one and the held one, as before")
    }

    @Test
    fun `nothing to be confused with is not asked about`() {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to "{}"))
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {}}"""))
        universe.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
            yemoja.logic.Matching.NONE)
        val screen = Screen(universe)
        toTab(screen, "region")
        screen.press(Key.INSERT)
        assertEquals(1, screen.items.size, "it went straight in")
    }

    @Test
    fun `the bar says the three keys the question has`() {
        val (_, screen) = unmatched(dived("10:00:00"), dived("10:30:00"))
        screen.press(Key.INSERT)
        val said = screen.paint(90, 12).last().text.trim()
        assertEquals("[enter] take it in | [esc] cancel | [^,v] answer", said)
    }
}

/** A dive computer that is not one, so that the screen can be driven without hardware. */
private class Pretend(
    override val name: String,
    private val held: List<yemoja.logic.divecomputer.Recording> = emptyList(),
) : yemoja.logic.divecomputer.DiveComputer {
    var askedAfter: String? = null
    var asked = false

    override fun recordings(
        session: yemoja.logic.divecomputer.Session,
    ): Sequence<yemoja.logic.divecomputer.Recording> {
        asked = true
        // A pretend device says no serial, so what is asked for is what the name finds.
        askedAfter = session.resume(null)
        return held.asSequence()
    }
}

/** Reading a dive computer from the import screen. */
class ReadDeviceTest {

    private fun dived(day: Int, fingerprint: String? = null) =
        yemoja.logic.divecomputer.Recording(
            computer = "Reef Computer",
            fingerprints = listOfNotNull(fingerprint),
            began = yemoja.data.Date(2026, 6, day),
            at = yemoja.data.Time(10, 5, 0),
        )

    private fun over(vararg attached: Pretend): Pair<MemoryFileStore, Screen> {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to "{}"))
        val universe = Universe(
            LogbookReader.read(store, Types.ALL), null, store, null,
            object : yemoja.logic.divecomputer.Devices {
                override fun found() = attached.toList()
            },
            MemoryFileStore(emptyMap()),
        )
        return store to Screen(universe)
    }

    private fun body(screen: Screen): List<String> =
        screen.paint(90, 12).map { it.text.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `choosing a dive computer lists what is attached`() {
        val (_, screen) = over(Pretend("Reef Computer"), Pretend("Old Gauge"))
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertTrue("Reef Computer" in body(screen), body(screen).toString())
        assertTrue("Old Gauge" in body(screen), body(screen).toString())
        assertTrue(body(screen).first().endsWith("a dive computer"), body(screen).first())
    }

    @Test
    fun `escape comes back to the two an import can come from`() {
        val (_, screen) = over(Pretend("Reef Computer"))
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.CLOSE)
        assertTrue("a logbook folder or a UDDF file" in body(screen), body(screen).toString())
    }

    @Test
    fun `enter reads the one the cursor is on and stages what it holds`() {
        val reef = Pretend("Reef Computer", listOf(dived(21)))
        val (_, screen) = over(Pretend("Old Gauge"), reef)
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertTrue(reef.asked, "the one the cursor was on")
        toTab(screen, "dive")
        assertEquals(2, screen.items.size, "the arriving dive above the one already held")
    }

    @Test
    fun `it resumes from the newest recording that computer made`() {
        // Handing the token back means only later dives are transferred at all. `DATA-90`.
        val store = MemoryFileStore(
            mapOf(
                "dive/d#0.json" to """{"start_date": "2026-06-20", "start_time": "10:00:00",
                    "profiles": {"reef_computer": {"dive_computer": "Reef Computer",
                    "fingerprint": "aa"}}}""",
            ),
        )
        val reef = Pretend("Reef Computer", listOf(dived(21)))
        val universe = Universe(
            LogbookReader.read(store, Types.ALL), null, store, null,
            object : yemoja.logic.divecomputer.Devices {
                override fun found() = listOf<yemoja.logic.divecomputer.DiveComputer>(reef)
            },
            MemoryFileStore(emptyMap()),
        )
        val screen = Screen(universe)
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        assertEquals("aa", reef.askedAfter)
    }

    @Test
    fun `one holding nothing new says so and stays open`() {
        val (_, screen) = over(Pretend("Reef Computer"))
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { it.startsWith("!") }, body(screen).toString())
        assertTrue("Reef Computer" in body(screen), "still listed, to be tried again")
    }

    @Test
    fun `a logbook with nowhere to stage says so rather than writing somewhere`() {
        // One opened from a folder stages beside it; one opened from nowhere has nowhere.
        val store = MemoryFileStore(mapOf("dive/d#0.json" to "{}"))
        val reef = Pretend("Reef Computer", listOf(dived(21)))
        val universe = Universe(
            LogbookReader.read(store, Types.ALL), null, store, null,
            object : yemoja.logic.divecomputer.Devices {
                override fun found() = listOf<yemoja.logic.divecomputer.DiveComputer>(reef)
            },
        )
        val screen = Screen(universe)
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        assertTrue(body(screen).any { "nowhere to stage" in it }, body(screen).toString())
    }

    @Test
    fun `the bar says the keys the list has`() {
        val (_, screen) = over(Pretend("Reef Computer"))
        screen.press(Key.Typed('+'))
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        val said = screen.paint(90, 12).last().text.trim()
        assertEquals("[enter] read it | [esc] back | [^,v] computer", said)
    }
}
