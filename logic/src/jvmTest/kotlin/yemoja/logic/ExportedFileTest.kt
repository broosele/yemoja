package yemoja.logic

import yemoja.data.Result
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Naming a file and writing the logbook to it, which is the path a user takes out.
 *
 * On the JVM because it touches the disk. See ../../../../../doc.md — the mapping is
 * logic/uddf.md.
 */
class ExportedFileTest {

    private val here = File.createTempFile("yemoja", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    @AfterTest
    fun clean() {
        here.deleteRecursively()
    }

    private val cousteau = Universe.open("../fixtures/cousteau")

    /** How many of the fixture's dives were made: a plan is not written to a file. `LOGIC-36`. */
    private fun made(): Int = cousteau.logbook.allOf(Types.DIVE).count { dive ->
        (dive.single<Boolean>("planned") as? Result.Usable)?.value != true
    }

    @Test
    fun `the logbook goes to the file named, and every dive is counted`() {
        val to = File(here, "out.uddf")
        val exported = cousteau.exportTo(to.path)
        assertTrue(to.isFile, "nothing was written")
        assertEquals(made(), exported.dives, "the dives that were made")
        assertEquals(exported.text, to.readText())
    }

    @Test
    fun `a file already there is written over, the reader having named it`() {
        val to = File(here, "out.uddf")
        to.writeText("an older export")
        cousteau.exportTo(to.path)
        assertTrue(to.readText().startsWith("<?xml"), "the old file is still there")
    }

    @Test
    fun `what went out comes back in through the import`() {
        val to = File(here, "out.uddf")
        cousteau.exportTo(to.path)
        val empty = Universe.create(File(here, "empty").path)
        assertEquals(Outcome.Done(), empty.importFrom(to.path))
        val arrived = empty.importing!!.incoming.count { it.description == Types.DIVE }
        assertEquals(made(), arrived, "the dives that were made")
    }
}
