package yemoja.ui.gui

import yemoja.data.Item
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Change
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Choosing which of a dive's recordings it is worked from, on the dive's own page. `GUI-16`.
 */
class PrimaryTest {

    private fun dive(profiles: String, primary: String? = "*perdix"): Pair<Universe, Item> {
        val chosen = primary?.let { """"primary_profile": "$it", """ } ?: ""
        val store = MemoryFileStore(mapOf("dive/d#0.json" to """{$chosen"profiles": {$profiles}}"""))
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store)
        return universe to universe.logbook["d#0"]!!
    }

    private val two = """"perdix": {"depth": [[0, 0], [60, 10]]}, "i330r": {"depth": [[0, 0], [60, 10]]}"""

    @Test
    fun `another recording may be made the primary, and the primary may not`() {
        val (_, dive) = dive(two)
        val entries = shownEntriesOf(dive, PROFILES)
        val primary = pointedEntryOf(dive, PROFILES)
        assertEquals(listOf(false, true), entries.indices.map { primaryOffered(entries, it, primary) })
    }

    @Test
    fun `a dive with one recording offers no choice`() {
        val (_, dive) = dive(""""perdix": {"depth": [[0, 0], [60, 10]]}""", primary = null)
        val entries = shownEntriesOf(dive, PROFILES)
        assertEquals(false, primaryOffered(entries, 0, pointedEntryOf(dive, PROFILES)))
    }

    @Test
    fun `a plan is not offered where the dive has a recording, and is among plans alone`() {
        val (_, recorded) = dive(""""perdix": {"depth": [[0, 0], [60, 10]]}, "a": {"planned": true, "depth": [[0, 0], [60, 10]]}""")
        val entries = shownEntriesOf(recorded, PROFILES)
        assertEquals(false, primaryOffered(entries, entries.indexOfFirst { it.first == "a" }, 0))
        val (_, planned) = dive(""""a": {"planned": true, "depth": [[0, 0]]}, "b": {"planned": true, "depth": [[0, 0]]}""", "*a")
        val plans = shownEntriesOf(planned, PROFILES)
        assertTrue(primaryOffered(plans, plans.indexOfFirst { it.first == "b" }, 0))
    }

    @Test
    fun `the recording made primary is the one shown first`() {
        val (universe, dive) = dive(two)
        assertIs<Outcome.Done>(universe.change(Operation.EDIT, Change.Write(dive, "primary_profile", Stored.Leaf("*i330r"))))
        assertEquals("i330r", shownEntriesOf(dive, PROFILES).first().first)
        assertEquals(0, pointedEntryOf(dive, PROFILES))
    }
}
