package yemoja.logic.divecomputer

import com.sun.jna.Memory
import com.sun.jna.Pointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Reading a sample off the union libdivecomputer hands over, which is done by offset.
 *
 * The offsets and the constants are this file's subject: they were taken from the published
 * header by hand, and a device would not obviously show them to be wrong.
 *
 * See ../../../../../../doc.md.
 */
class WalkedTest {

    private val walked = Walked()

    private fun at(milliseconds: Int) =
        walked.invoke(0, Memory(8).also { it.setInt(0, milliseconds) }, null)

    private fun depth(metres: Double) =
        walked.invoke(1, Memory(8).also { it.setDouble(0, metres) }, null)

    private fun pressure(tank: Int, bar: Double) = walked.invoke(
        2,
        Memory(16).also { it.setInt(0, tank); it.setDouble(8, bar) },
        null,
    )

    /** `{ type, time, flags, value }`, each a four-byte word. */
    private fun event(type: Int, flags: Int) = walked.invoke(
        4,
        Memory(16).also {
            it.setInt(0, type)
            it.setInt(4, 0)
            it.setInt(8, flags)
            it.setInt(12, 0)
        },
        null,
    )

    /** `{ type, time, depth, tts }`, the depth aligned to eight. */
    private fun deco(type: Int, seconds: Int, metres: Double) = walked.invoke(
        12,
        Memory(24).also { it.setInt(0, type); it.setInt(4, seconds); it.setDouble(8, metres) },
        null,
    )

    @Test
    fun `a time opens a sample and what follows belongs to it`() {
        at(0)
        depth(0.0)
        at(60000)
        depth(12.5)
        val held = walked.done()
        assertEquals(listOf(0, 60), held.map { it.at }, "milliseconds there, seconds here")
        assertEquals(12.5, held[1].depth)
    }

    @Test
    fun `a pressure carries which tank it was read from`() {
        at(0)
        pressure(1, 185.0)
        val held = walked.done().single()
        assertEquals(mapOf(1 to 185.0), held.pressures)
    }

    @Test
    fun `a required stop is kept and a safety stop is not`() {
        // Folding the two together would make `deco` derive true for every recreational dive
        // that held three minutes at five metres. `LOGIC-13`.
        at(0)
        deco(2, 180, 6.0)
        at(60000)
        deco(1, 180, 5.0)
        val held = walked.done()
        assertEquals(6.0, held[0].decostop, "type 2 is a required stop")
        assertNull(held[1].decostop, "type 1 is a safety stop")
    }

    @Test
    fun `no-decompression time is the one that carries a time rather than a depth`() {
        at(0)
        deco(0, 1200, 0.0)
        assertEquals(1200.0, walked.done().single().noDecoTime)
    }

    @Test
    fun `an alarm is kept where it begins and dropped where it ends`() {
        // Two identical words in the series could not say which was which. `LOGIC-16`.
        at(0)
        event(3, 1)
        at(60000)
        event(3, 2)
        val held = walked.done()
        assertEquals(listOf("ascent"), held[0].alarms)
        assertEquals(emptyList(), held[1].alarms)
    }

    @Test
    fun `only the five that map become alarms`() {
        at(0)
        event(1, 1)
        event(8, 1)
        event(2, 1)
        val held = walked.done().single()
        assertEquals(listOf("rbt"), held.alarms, "a decostop event is not our deco")
    }

    @Test
    fun `nothing before the first time is a sample of its own`() {
        depth(3.0)
        assertTrue(walked.done().isEmpty())
    }
}

/** Turning what a device hands out into text and back, which is what a fingerprint travels as. */
class HexTest {

    @Test
    fun `bytes become hexadecimal, two digits apiece`() {
        val held = Memory(4)
        held.setByte(0, 0x00); held.setByte(1, 0x0f)
        held.setByte(2, 0xa1.toByte()); held.setByte(3, 0xff.toByte())
        assertEquals("000fa1ff", hexOf(held, 4))
    }

    @Test
    fun `and hexadecimal back into the same bytes`() {
        val out = bytesOf("000fa1ff")!!
        assertEquals(listOf(0, 15, 161, 255), out.map { it.toInt() and 0xff })
    }

    @Test
    fun `nothing in, nothing out`() {
        assertNull(hexOf(null, 4))
        assertNull(hexOf(Memory(4), 0))
        assertNull(bytesOf(null))
        assertNull(bytesOf(""))
    }

    @Test
    fun `something that is not hexadecimal is refused rather than half read`() {
        // It reaches a device, and half a fingerprint would say stop somewhere else entirely.
        assertNull(bytesOf("00f"))
        assertNull(bytesOf("zz"))
    }
}
