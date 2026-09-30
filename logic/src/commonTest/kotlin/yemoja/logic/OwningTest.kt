package yemoja.logic

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Saying whose a logbook is, from the application rather than by hand. See
 * ../../../../../../data/json/doc.md — `JSON-22`, and ../../../../../../ui/gui/doc.md — `GUI-51`.
 */
class OwningTest {

    private val manifest = """{"libraries": {"region": ["region/world"]}}"""

    private fun universe(): Pair<Universe, MemoryFileStore> {
        val store = MemoryFileStore(
            mapOf(
                LogbookReader.MANIFEST to manifest,
                "person.json" to """{"anna": {"first_name": "Anna"}, "bo": {"first_name": "Bo"}}""",
                "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
            ),
        )
        val read = LogbookReader.read(store, Types.ALL, LogbookReader.manifest(store))
        return Universe(read, null, store, null, null) to store
    }

    @Test
    fun `owning names the person in the manifest and keeps what else it declared`() {
        val (universe, store) = universe()
        assertNull(universe.user, "nobody yet")
        val before = universe.revision
        assertIs<Outcome.Done>(universe.own("anna"))
        assertEquals("anna", universe.logbook.idOf(universe.user!!))
        assertTrue(universe.revision > before, "what reads the user reads again")
        val written = LogbookReader.manifest(store)
        assertEquals("anna", written.user?.id, "read back from the file")
        assertEquals(mapOf("region" to listOf("region/world")), written.libraries, "untouched")
    }

    @Test
    fun `owning again moves it, a logbook naming one person or nobody`() {
        val (universe, store) = universe()
        universe.own("anna")
        assertIs<Outcome.Done>(universe.own("bo"))
        assertEquals("bo", LogbookReader.manifest(store).user?.id)
        assertEquals("bo", universe.logbook.idOf(universe.user!!))
    }

    @Test
    fun `only a person can own a logbook, and only one that is there`() {
        val (universe, store) = universe()
        val site = assertIs<Outcome.Refused>(universe.own("blue"))
        assertTrue("rather than a person" in site.reason, site.reason)
        val nobody = assertIs<Outcome.Refused>(universe.own("nobody"))
        assertEquals("nobody is not in this logbook", nobody.reason)
        assertNull(LogbookReader.manifest(store).user, "and nothing was written")
        assertNull(universe.user)
    }

    @Test
    fun `what is worked out from the user follows the choice, without reopening`() {
        val store = MemoryFileStore(
            mapOf(
                "person.json" to """{"anna": {"first_name": "Anna"}, "bo": {"first_name": "Bo"}}""",
                "dive/2026-06-21#0.json" to """{"buddies": ["@bo"],
                    "profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
                "dive/2026-06-22#0.json" to """{"profiles": {"p": {"depth": [[0, 0], [60, 9.0]]}}}""",
            ),
        )
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
        val anna = universe.logbook["anna"]!!
        fun dives(): Int = ((anna.read("dives") as yemoja.data.Result.Usable<*>).value as List<*>).size
        assertEquals(0, dives(), "not a buddy on either, and nobody's logbook yet")
        assertIs<Outcome.Done>(universe.own("anna"))
        assertEquals("anna", universe.logbook.user?.id, "the set knows too")
        assertEquals(2, dives(), "the user's dives are every dive, at once")
    }

    @Test
    fun `whatever else the manifest holds survives the owner being chosen`() {
        val store = MemoryFileStore(
            mapOf(
                LogbookReader.MANIFEST to """{"libraries": {"region": ["region/world"]}, "notes": "kept"}""",
                "person.json" to """{"anna": {"first_name": "Anna"}}""",
            ),
        )
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
        assertIs<Outcome.Done>(universe.own("anna"))
        val text = store.readText(LogbookReader.MANIFEST)
        assertTrue("\"notes\": \"kept\"" in text, text)
        assertTrue("\"user\": \"@anna\"" in text, text)
    }

    @Test
    fun `a manifest that will not read is refused rather than written over, and nothing throws`() {
        // Spoilt after opening, by another hand: an agent allowed at the files, or an editor.
        val (universe, store) = universe()
        store.writeText(LogbookReader.MANIFEST, """{"user": 1}""")
        val refused = assertIs<Outcome.Refused>(universe.own("anna"))
        assertTrue("manifest could not be read" in refused.reason, refused.reason)
        assertEquals("""{"user": 1}""", store.readText(LogbookReader.MANIFEST), "left as it was")
        assertNull(universe.user)
    }

    @Test
    fun `reading the files again moves the set's user with the manifest`() {
        val (universe, store) = universe()
        store.writeText(LogbookReader.MANIFEST, """{"user": "@bo", "libraries": {}}""")
        assertIs<Outcome.Done>(universe.reload())
        assertEquals("bo", universe.logbook.user?.id)
        assertEquals("bo", universe.logbook.idOf(universe.user!!))
    }

    @Test
    fun `a logbook with no manifest gets one that names the owner and no libraries`() {
        val store = MemoryFileStore(mapOf("person.json" to """{"anna": {"first_name": "Anna"}}"""))
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
        assertIs<Outcome.Done>(universe.own("anna"))
        val written = LogbookReader.manifest(store)
        assertEquals("anna", written.user?.id)
        assertEquals(emptyMap(), written.libraries)
    }
}
