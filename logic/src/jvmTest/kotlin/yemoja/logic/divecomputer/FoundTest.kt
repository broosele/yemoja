package yemoja.logic.divecomputer

import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * Looking for a dive computer, which answers whether or not one is there.
 *
 * See ../../../../../../doc.md.
 */
class FoundTest {

    @Test
    fun `looking answers, with or without a device and with or without the library`() {
        // Three hundred and fifty-seven models, each asked over two transports. Nothing here
        // needs a dive computer: what is being checked is that asking is safe and terminates.
        val found = FoundDevices().found()
        assertTrue(found.size >= 0)
        assertTrue(found.all { it.name.isNotEmpty() }, "anything found says what it is")
    }

    @Test
    fun `nothing is found where the library is not there`() {
        if (Libdivecomputer.LOADED != null) return
        assertTrue(FoundDevices().found().isEmpty())
    }
}
