package yemoja.logic

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What cannot be saved, and what is read rather than stopping the logbook. See
 * ../../../../../../data/doc.md — `DATA-77`, `DATA-87`.
 */
class UnwritableTest {

    private fun logbook(vararg files: Pair<String, String>): Pair<Universe, MemoryFileStore> {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null) to store
    }

    @Test
    fun `a depth typed into a file whose length unit is unknown is refused, not saved as metres`() {
        val (universe, store) = logbook(
            "dive/2026-06-21#0.json" to """{"units": {"length": "fathom"}, "max_depth": 5, "rating": 6}""",
        )
        val dive = universe.logbook["2026-06-21#0"]!!
        assertIs<Result.Unusable>(dive.read("max_depth"), "the fathoms cannot be read")
        val refused = assertIs<Outcome.Refused>(
            universe.change(Operation.EDIT, Change.Write(dive, "max_depth", 9.0)),
        )
        assertTrue("fathom" in refused.reason && "units block" in refused.reason, refused.reason)
        assertTrue("\"max_depth\": 5" in store.readText("dive/2026-06-21#0.json"), "left as written")
        assertIs<Outcome.Done>(
            universe.change(Operation.EDIT, Change.Write(dive, "rating", 7L)),
            "a field in no dimension still saves",
        )
    }

    @Test
    fun `a whole number too large to hold leaves the logbook open and spoils only its field`() {
        val (universe, _) = logbook(
            "dive/2026-06-21#0.json" to """{"dive_number": 123456789012345678901, "rating": 6}""",
        )
        val dive = universe.logbook["2026-06-21#0"]!!
        assertIs<Result.Unusable>(dive.read("dive_number"))
        assertEquals(6L, (dive.read("rating") as Result.Usable).value.let { (it as Number).toLong() })
    }
}
