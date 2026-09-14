package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Matching
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Reading a dive computer, as far as it goes without one.
 * See ../../../../../../gui/doc.md — `GUI-31`.
 */
class DownloadingTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    private fun arriving(vararg files: Pair<String, String>) =
        LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

    @Test
    fun `each stage says what is happening, and an idle one says nothing`() {
        assertNull(sayingOf(Stage.IDLE, null))
        assertEquals("Looking for a dive computer…", sayingOf(Stage.LOOKING, null))
        assertTrue(sayingOf(Stage.CHOOSING, null)!!.startsWith("More than one"))
        assertEquals(
            "Reading Perdix 2. A full computer takes minutes.",
            sayingOf(Stage.READING, "Perdix 2"),
        )
        assertNull(sayingOf(Stage.DONE, null), "what came of it is said another way")
    }

    @Test
    fun `what came of a download is how many arrived, or why none did`() {
        assertEquals("3 dives came across.", outcomeOf(Outcome.Done(), 3))
        assertEquals("1 dive came across.", outcomeOf(Outcome.Done(), 1))
        assertEquals("nothing new", outcomeOf(Outcome.Refused("nothing new"), 0))
    }

    @Test
    fun `how many arrived is how many dives the staging holds`() {
        assertEquals(0, arrivedIn(null))
        val into = logbook()
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"max_depth": 30.0}""",
                "dive/2026-06-22#0.json" to """{"max_depth": 18.0}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        assertEquals(2, arrivedIn(into.importing))
    }

    @Test
    fun `taking them all in empties the staging into the logbook`() {
        val into = logbook()
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"max_depth": 30.0}""",
                "dive/2026-06-22#0.json" to """{"max_depth": 18.0}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val taken = takenIn(into.importing!!)
        assertEquals(2, taken.many)
        assertNull(taken.refusal)
        assertEquals(2, into.logbook.allOf(Types.DIVE).size, "both are in the logbook now")
        assertEquals(0, arrivedIn(into.importing), "and none is left staged")
    }

    @Test
    fun `an arriving dive keeps what it said`() {
        val into = logbook("dive/2026-01-01#0.json" to """{"max_depth": 12.0}""")
        into.importFrom(
            arriving("dive/2026-06-21#0.json" to """{"max_depth": 30.0, "rating": 8}"""),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        takenIn(into.importing!!)
        assertEquals(2, into.logbook.allOf(Types.DIVE).size, "the one held and the one that came")
        val arrived = into.logbook.allOf(Types.DIVE).first { it !== into.logbook["2026-01-01#0"] }
        assertEquals(30.0, (arrived.single<Double>("max_depth") as yemoja.data.Result.Usable).value)
    }

    @Test
    fun `nothing staged is nothing taken`() {
        val into = logbook()
        into.importFrom(arriving(), MemoryFileStore(emptyMap()), Matching.NONE)
        val taken = takenIn(into.importing!!)
        assertEquals(0, taken.many)
        assertNull(taken.refusal)
    }
}
