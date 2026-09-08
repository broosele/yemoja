package yemoja.logic

import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * A whole logbook imported into an empty one, which is the largest thing there is to run this on.
 *
 * On the JVM because it reads the fixture from disk, and from the module's own directory, which
 * is where a Gradle test task runs. See ../../../../../doc.md — logic/reconciliation.md is the
 * document, and `TEST-1` puts the fixtures at the root.
 */
class ImportedFixtureTest {

    private fun cousteau() = LogbookReader.read(DiskFileStore("../fixtures/cousteau"), Types.ALL)

    private fun empty(): Universe {
        val store = MemoryFileStore(emptyMap())
        return Universe(LogbookReader.read(store, Types.ALL), null, store)
    }

    private fun many(universe: Universe): Int = Types.ALL.sumOf { universe.logbook.allOf(it).size }

    @Test
    fun `the whole fixture goes in, and every item keeps the id it came with`() {
        val source = cousteau()
        val into = empty()
        val import = Import.begin(source, MemoryFileStore(emptyMap()), into)
        val arrived = Types.ALL.flatMap { source.allOf(it) }.mapNotNull { source.idOf(it) }
        assertIs<Outcome.Done>(import.apply())
        assertEquals(arrived.size, many(into))
        for (id in arrived) assertTrue(into.logbook[id] != null, "$id did not arrive under its id")
    }

    @Test
    fun `a reference that resolved before resolves after`() {
        // Which is the whole reason the id comes across rather than being minted again.
        val into = empty()
        Import.begin(cousteau(), MemoryFileStore(emptyMap()), into).apply()
        val dive = into.logbook["2024-06-15#0"]!!
        val read = dive.list<Reference>("buddies")
        assertIs<Result.Usable<*>>(read)
        val named = ((read as Result.Usable).value.first() as yemoja.data.Element.Usable).value
        assertIs<Reference.Identified>(named)
        assertTrue(into.logbook[named.id] != null, "${named.id} is named and not held")
    }

    @Test
    fun `importing the same logbook again changes nothing`() {
        val into = empty()
        val import = Import.begin(cousteau(), MemoryFileStore(emptyMap()), into)
        assertIs<Outcome.Done>(import.apply())
        val landed = many(into)
        val again = import.apply()
        assertIs<Outcome.Done>(again)
        assertEquals(emptyList(), again.added, "nothing was added the second time")
        assertEquals(landed, many(into))
    }
}
