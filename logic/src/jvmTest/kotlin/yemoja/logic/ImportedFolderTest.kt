package yemoja.logic

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Naming a folder and reading it, which is the one path a user takes and the one the rest of the
 * tests reach past by handing over an item set already read.
 *
 * On the JVM because it touches the disk. See ../../../../../doc.md.
 */
class ImportedFolderTest {

    private val here = File.createTempFile("yemoja", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private val staging = File(here.path + ".import")

    @AfterTest
    fun clean() {
        here.deleteRecursively()
        staging.deleteRecursively()
    }

    private fun opened(): Universe = Universe.open(here.path)

    @Test
    fun `a folder that is not there is refused rather than thrown`() {
        val refused = opened().importFrom(File(here, "nowhere").path)
        assertIs<Outcome.Refused>(refused)
        assertTrue("folder" in refused.reason, refused.reason)
    }

    @Test
    fun `naming a folder stages what is in it, beside the logbook`() {
        val universe = opened()
        assertIs<Outcome.Done>(universe.importFrom("../fixtures/cousteau"))
        val import = universe.importing
        assertTrue(import != null, "there is an import to review")
        assertEquals(534, import.incoming.size)
        assertTrue(staging.isDirectory, "${staging.path} was not written")
        assertTrue(!File(here, "region.json").exists(), "and nothing has gone into the logbook")
    }

    @Test
    fun `an item taken in is written into the logbook and out of the staging`() {
        val universe = opened()
        universe.importFrom("../fixtures/cousteau")
        val import = universe.importing!!
        assertIs<Outcome.Done>(import.insert("north_sea"))
        assertTrue(universe.logbook["north_sea"] != null)
        assertTrue(File(here, "region.json").readText().contains("north_sea"))
        assertTrue(import.staged.logbook["north_sea"] == null, "and it left the review")
    }
}
