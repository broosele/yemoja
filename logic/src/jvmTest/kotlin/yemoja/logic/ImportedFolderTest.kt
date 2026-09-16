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
    fun `a path that is neither a folder nor a file is refused rather than thrown`() {
        val refused = opened().importFrom(File(here, "nowhere").path)
        assertIs<Outcome.Refused>(refused)
        assertTrue("neither" in refused.reason, refused.reason)
    }

    /** A UDDF document written where the test can point at it. */
    private fun uddf(text: String): String {
        val at = File(here.parentFile, "yemoja-test.uddf")
        at.writeText(text)
        at.deleteOnExit()
        return at.path
    }

    @Test
    fun `a file at the path is read as UDDF`() {
        // One prompt, one path: what is there says how it is read.
        val universe = opened()
        val done = universe.importFrom(
            uddf(
                """
                <uddf xmlns="http://www.streit.cc/uddf/3.2/"><profiledata><repetitiongroup>
                  <dive><informationbeforedive>
                    <datetime>2024-06-15T10:05:00</datetime>
                  </informationbeforedive></dive>
                </repetitiongroup></profiledata></uddf>
                """,
            ),
        )
        assertIs<Outcome.Done>(done)
        assertEquals(1, universe.importing!!.incoming.size)
        assertTrue(universe.importing!!.staged.logbook["2024-06-15#0"] != null)
    }

    @Test
    fun `a file that is not XML is refused rather than thrown`() {
        val refused = opened().importFrom(uddf("not a document at all"))
        assertIs<Outcome.Refused>(refused)
        assertTrue("UDDF" in refused.reason, refused.reason)
    }

    @Test
    fun `a document holding no dives says so`() {
        // Only dives are read from one yet, so an empty review would say nothing about why.
        val refused = opened().importFrom(uddf("""<uddf><generator/></uddf>"""))
        assertIs<Outcome.Refused>(refused)
        assertTrue("no dives" in refused.reason, refused.reason)
    }

    @Test
    fun `naming a folder stages what is in it, beside the logbook`() {
        val universe = opened()
        assertIs<Outcome.Done>(universe.importFrom("../fixtures/cousteau"))
        val import = universe.importing
        assertTrue(import != null, "there is an import to review")
        assertEquals(535, import.incoming.size)
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
