package yemoja.logic

import yemoja.data.Result
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Making a logbook, against the libraries the application really ships.
 *
 * See ../../../../../../doc.md — `LOGIC-26` in logic/doc.md.
 */
class NewLogbookTest {

    private fun somewhere(): Path = Files.createTempDirectory("yemoja-").resolve("new")

    @Test
    fun `it makes the folder, writes the manifest, and opens what it made`() {
        val at = somewhere()
        assertTrue(!at.exists(), "nothing is there to begin with")
        val universe = Universe.create(at.toString())
        assertTrue(at.resolve("yemoja.json").exists(), "a logbook is a folder with a manifest")
        assertEquals(0, universe.logbook.allOf(Types.DIVE).size, "and no dives yet")
        assertNull(universe.user, "nor anybody to call its own")
    }

    @Test
    fun `what it declares is every library the application ships`() {
        val universe = Universe.create(somewhere().toString())
        val set = universe.logbook
        assertEquals("World", name(set, "world"), "the widest region there is")
        assertNotNull(set["north_sea"], "a sea from one of the continent files")
        assertNotNull(set["red_sea"], "and one from another")
        assertTrue(set.size > 100, "the regions are many, and were ${set.size}")
        assertNotNull(set.allOf(Types.CERTIFICATION).firstOrNull(), "the agencies' as well")
    }

    @Test
    fun `the manifest names the libraries by folder, in order, and nothing else`() {
        val at = somewhere()
        Universe.create(at.toString())
        val written = at.resolve("yemoja.json").readText()
        assertTrue("\"region/world\"" in written, written)
        assertTrue("\"certification/padi\"" in written, written)
        assertTrue("\"region/africa\"" in written.substringBefore("\"region/world\""), "sorted")
        assertTrue("generic_gear" !in written, "a loose library cannot say what type it holds")
        assertTrue("\"user\"" !in written, "a new logbook belongs to nobody yet")
        assertTrue(written.endsWith("\n"), "a file ends with a line ending")
    }

    @Test
    fun `a folder that already holds a logbook is refused rather than written over`() {
        val at = somewhere()
        Files.createDirectories(at)
        at.resolve("yemoja.json").writeText("""{"libraries": {}}""")
        val refused = assertFailsWith<IllegalArgumentException> { Universe.create(at.toString()) }
        assertTrue("already holds a logbook" in refused.message.orEmpty(), refused.message ?: "")
        assertEquals("""{"libraries": {}}""", at.resolve("yemoja.json").readText(), "left alone")
    }

    @Test
    fun `whatever else is in the folder is left where it is`() {
        val at = somewhere()
        Files.createDirectories(at)
        at.resolve("notes.txt").writeText("mine")
        Universe.create(at.toString())
        assertEquals("mine", at.resolve("notes.txt").readText())
    }

    private fun name(set: yemoja.data.ItemSet, id: String): String? =
        (set[id]?.single<String>("name") as? Result.Usable)?.value
}
