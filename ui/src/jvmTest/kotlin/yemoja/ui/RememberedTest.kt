package yemoja.ui

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * What the window remembers between runs. See ../../../../../gui/desktop/doc.md — `DESK-12`.
 */
class RememberedTest {

    private val base: File = Files.createTempDirectory("remembered").toFile()

    @Test
    fun `the last logbook opened is the one given back`() {
        val logbook = Files.createTempDirectory("logbook").toFile()
        assertNull(lastLogbook(base), "nothing opened yet")
        rememberLogbook(logbook.path, base)
        assertEquals(logbook.absolutePath, lastLogbook(base))
    }

    @Test
    fun `a folder that has gone is not given back`() {
        val logbook = Files.createTempDirectory("logbook").toFile()
        rememberLogbook(logbook.path, base)
        logbook.delete()
        assertNull(lastLogbook(base))
    }

    @Test
    fun `remembering makes the place it is kept in`() {
        val logbook = Files.createTempDirectory("logbook").toFile()
        val nested = File(base, "not/yet/there")
        rememberLogbook(logbook.path, nested)
        assertEquals(logbook.absolutePath, lastLogbook(nested))
    }
}
