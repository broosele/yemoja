@file:OptIn(ExperimentalUuidApi::class)

package yemoja.logic.divecomputer

import com.sun.jna.Memory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * The Bluetooth stream, driven over a wire laid by hand, and the choice of characteristics.
 *
 * What a device would say is not here: this is whether the bytes the library asks for are
 * handed over as it expects them, and whether the pair it talks through is picked as
 * `LOGIC-22` says.
 *
 * See ../../../../../../doc.md.
 */

private val BATTERY = Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")
private val INFORMATION = Uuid.parse("0000180a-0000-1000-8000-00805f9b34fb")
private val OWN = Uuid.parse("fe25c237-0ece-443c-b0aa-e02033e7029d")
private val OTHER = Uuid.parse("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
private val A = Uuid.parse("27b7570b-359e-45a3-91bb-cf7e70049bd2")
private val B = Uuid.parse("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
private val C = Uuid.parse("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
private val LEVEL = Uuid.parse("00002a19-0000-1000-8000-00805f9b34fb")

class ChosenTest {

    @Test
    fun `one characteristic doing both is the pair`() {
        val chosen = chosenAmong(
            listOf(
                Offered(BATTERY, LEVEL, writable = false, notifying = true),
                Offered(OWN, A, writable = true, notifying = true),
            ),
        )
        assertEquals(Chosen(OWN, A, A), chosen)
    }

    @Test
    fun `otherwise two within one service, one each way`() {
        val chosen = chosenAmong(
            listOf(
                Offered(OTHER, B, writable = true, notifying = false),
                Offered(OTHER, C, writable = false, notifying = true),
            ),
        )
        assertEquals(Chosen(OTHER, B, C), chosen)
    }

    @Test
    fun `a service the SIG defined is never it, whatever it offers`() {
        val chosen = chosenAmong(
            listOf(
                Offered(BATTERY, LEVEL, writable = true, notifying = true),
                Offered(INFORMATION, A, writable = true, notifying = true),
            ),
        )
        assertNull(chosen, "battery and device information are not where dives are")
    }

    @Test
    fun `a writer in one service and a listener in another is nothing`() {
        val chosen = chosenAmong(
            listOf(
                Offered(OWN, A, writable = true, notifying = false),
                Offered(OTHER, C, writable = false, notifying = true),
            ),
        )
        assertNull(chosen)
    }

    @Test
    fun `a vendor's sixteen-bit service is its own`() {
        val assigned = Uuid.parse("0000fe59-0000-1000-8000-00805f9b34fb")
        assertEquals(false, Offered(assigned, A, writable = true, notifying = true).assigned)
        assertEquals(true, Offered(BATTERY, LEVEL, writable = false, notifying = true).assigned)
    }
}

/** A wire that answers what it was told to and remembers what was sent. */
private class Laid(
    override val name: String = "Perdix",
    override val chunk: Int = 4,
    private val answers: ArrayDeque<ByteArray> = ArrayDeque(),
    private val others: Map<Uuid, ByteArray> = emptyMap(),
) : Wire {
    val sent = ArrayList<ByteArray>()
    var closed = false

    override fun send(bytes: ByteArray) {
        sent += bytes
    }

    override fun received(milliseconds: Long): ByteArray? = answers.removeFirstOrNull()

    override fun readOther(characteristic: Uuid): ByteArray? = others[characteristic]

    override fun close() {
        closed = true
    }
}

class CustomTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `a read is filled from what arrived and the rest waits for the next`() {
        val wire = Laid(answers = ArrayDeque(listOf(bytes(1, 2, 3), bytes(4, 5))))
        val custom = Custom(wire)
        val into = Memory(8)
        val actual = Memory(8)
        assertEquals(0, custom.read(into, 4, actual))
        assertContentEquals(bytes(1, 2, 3, 4), into.getByteArray(0, 4))
        assertEquals(4L, actual.getLong(0))
        assertEquals(0, custom.read(into, 1, actual))
        assertContentEquals(bytes(5), into.getByteArray(0, 1))
        assertEquals(1L, actual.getLong(0))
    }

    @Test
    fun `a read that cannot be filled says timeout and gives what it has`() {
        val wire = Laid(answers = ArrayDeque(listOf(bytes(9))))
        val custom = Custom(wire)
        val into = Memory(8)
        val actual = Memory(8)
        assertEquals(Libdivecomputer.TIMEOUT, custom.read(into, 3, actual))
        assertEquals(1L, actual.getLong(0), "the one byte that came is handed over")
        assertContentEquals(bytes(9), into.getByteArray(0, 1))
    }

    @Test
    fun `a write goes out in chunks the wire can carry`() {
        val wire = Laid(chunk = 4)
        val custom = Custom(wire)
        val from = Memory(16).also { it.write(0, bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 0, 10) }
        val actual = Memory(8)
        assertEquals(0, custom.write(from, 10, actual))
        assertEquals(listOf(4, 4, 2), wire.sent.map { it.size })
        assertContentEquals(bytes(9, 10), wire.sent.last())
        assertEquals(10L, actual.getLong(0))
    }

    @Test
    fun `the name ioctl answers the advertised name, ended and cut to fit`() {
        val custom = Custom(Laid(name = "Perdix 2"))
        val room = Memory(5)
        assertEquals(0, custom.ioctl(Custom.GET_NAME, room, 5))
        assertEquals("Perd", room.getString(0), "four letters and the zero that ends it")
    }

    @Test
    fun `a characteristic read takes the uuid in and lays the value after it`() {
        val wire = Laid(others = mapOf(A to bytes(7, 8, 9)))
        val custom = Custom(wire)
        val data = Memory(32).also { it.write(0, A.toByteArray(), 0, 16) }
        assertEquals(0, custom.ioctl(Custom.CHARACTERISTIC_READ, data, 16 + 3))
        assertContentEquals(bytes(7, 8, 9), data.getByteArray(16, 3))
    }

    @Test
    fun `a characteristic the device has not is an io failure, not a crash`() {
        val custom = Custom(Laid())
        val data = Memory(32).also { it.write(0, B.toByteArray(), 0, 16) }
        assertEquals(Libdivecomputer.IO, custom.ioctl(Custom.CHARACTERISTIC_READ, data, 20))
    }

    @Test
    fun `a request this stream has no answer to is unsupported`() {
        val custom = Custom(Laid())
        val pincode = (1 shl 30) or ('b'.code shl 8) or 1
        assertEquals(Libdivecomputer.UNSUPPORTED, custom.ioctl(pincode, Memory(8), 8))
    }

    @Test
    fun `the request numbers are the header's`() {
        assertEquals(0x40006200, Custom.GET_NAME)
        assertEquals(0x40006203, Custom.CHARACTERISTIC_READ)
    }
}
