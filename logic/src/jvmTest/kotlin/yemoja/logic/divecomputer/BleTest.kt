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

class PreferredTest {

    private val family = listOf("Petrel 2", "Perdix", "Perdix AI", "Teric", "Perdix 2")

    private val none = List(5) { 0 }

    /** Oceanic Pro Plus X, Aqualung i470TC, Aqualung i330R, as the library numbers them. */
    private val oceanic = listOf("Pro Plus X", "i470TC", "i330R")

    private val codes = listOf(0x4552, 0x4743, 0x4744)

    @Test
    fun `the member whose name the advertisement contains is the one`() {
        assertEquals(3, preferred("Teric", family, none))
    }

    @Test
    fun `the longest where several are contained`() {
        assertEquals(4, preferred("Perdix 2", family, none), "not Perdix, which is in it too")
        assertEquals(2, preferred("Perdix AI", family, none))
    }

    @Test
    fun `case does not matter and nothing contained is the first`() {
        assertEquals(1, preferred("PERDIX", family, none))
        assertEquals(0, preferred("Nerd 2", family, none))
    }

    @Test
    fun `two letters and then digits are a model number, and say which model`() {
        assertEquals(2, preferred("GD312445", oceanic, codes), "GD is 0x4744")
        assertEquals(0, preferred("er009", oceanic, codes), "ER is 0x4552, and case is nothing")
    }

    @Test
    fun `a code no model spells, or letters where digits should be, is the first`() {
        assertEquals(0, preferred("ZZ99", oceanic, codes), "no model is ZZ")
        assertEquals(0, preferred("GD31A2", oceanic, codes), "a letter among the digits")
        assertEquals(0, preferred("GD", oceanic, codes), "a prefix and no number at all")
    }
}

/** A wire that answers what it was told to and remembers what was sent. */
private class Laid(
    override val name: String = "Perdix",
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
    fun `a read answers one packet, however much room was offered`() {
        val wire = Laid(answers = ArrayDeque(listOf(bytes(1, 2, 3), bytes(4, 5))))
        val custom = Custom(wire)
        val into = Memory(8)
        val actual = Memory(8)
        assertEquals(0, custom.read(into, 8, actual))
        assertContentEquals(bytes(1, 2, 3), into.getByteArray(0, 3))
        assertEquals(3L, actual.getLong(0), "the first packet, not the room")
        assertEquals(0, custom.read(into, 8, actual))
        assertContentEquals(bytes(4, 5), into.getByteArray(0, 2))
        assertEquals(2L, actual.getLong(0))
    }

    @Test
    fun `a packet larger than the room is handed over in pieces`() {
        val wire = Laid(answers = ArrayDeque(listOf(bytes(1, 2, 3, 4, 5))))
        val custom = Custom(wire)
        val into = Memory(8)
        val actual = Memory(8)
        assertEquals(0, custom.read(into, 4, actual))
        assertContentEquals(bytes(1, 2, 3, 4), into.getByteArray(0, 4))
        assertEquals(0, custom.read(into, 4, actual))
        assertContentEquals(bytes(5), into.getByteArray(0, 1))
        assertEquals(1L, actual.getLong(0))
    }

    @Test
    fun `a read with nothing coming is a timeout, and hands over nothing`() {
        val custom = Custom(Laid())
        val actual = Memory(8)
        assertEquals(Libdivecomputer.TIMEOUT, custom.read(Memory(8), 3, actual))
        assertEquals(0L, actual.getLong(0))
    }

    @Test
    fun `a write goes out as one packet, however long`() {
        // Two writes are two packets to the device: a twenty-one byte request split into
        // twenty and one is what the i330R would not answer.
        val wire = Laid()
        val custom = Custom(wire)
        val from = Memory(32).also { it.write(0, ByteArray(21) { at -> at.toByte() }, 0, 21) }
        val actual = Memory(8)
        assertEquals(0, custom.write(from, 21, actual))
        assertEquals(listOf(21), wire.sent.map { it.size })
        assertEquals(21L, actual.getLong(0))
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
        val unknown = (1 shl 30) or ('b'.code shl 8) or 9
        assertEquals(Libdivecomputer.UNSUPPORTED, custom.ioctl(unknown, Memory(8), 8))
    }

    @Test
    fun `the request numbers are the header's`() {
        assertEquals(0x40006200, Custom.GET_NAME)
        assertEquals(0x40006201, Custom.GET_PINCODE)
        assertEquals(0x40006202, Custom.GET_ACCESSCODE)
        assertEquals(0x80006202.toInt(), Custom.SET_ACCESSCODE)
        assertEquals(0x40006203, Custom.CHARACTERISTIC_READ)
    }
}

/** A session that remembers what it was asked and answers what it was told to. */
private class Answering(
    private val typed: String? = null,
    private val kept: ByteArray? = null,
) : Session {
    val askedFor = ArrayList<String>()
    var given: Pair<String, ByteArray>? = null

    override fun pin(name: String): String? {
        askedFor += name
        return typed
    }

    override fun accessCode(name: String): ByteArray? = kept

    override fun keep(name: String, accessCode: ByteArray) {
        given = name to accessCode
    }
}

class GuardedTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `the code the user types goes back ended by zero, asked for by the device's name`() {
        val session = Answering(typed = "123456")
        val custom = Custom(Laid(name = "FQ001124"), session)
        val room = Memory(16)
        assertEquals(0, custom.ioctl(Custom.GET_PINCODE, room, 16))
        assertEquals("123456", room.getString(0))
        assertEquals(listOf("FQ001124"), session.askedFor)
    }

    @Test
    fun `nobody typing is giving up, not a fault`() {
        val custom = Custom(Laid(), Answering(typed = null))
        assertEquals(Libdivecomputer.CANCELLED, custom.ioctl(Custom.GET_PINCODE, Memory(16), 16))
    }

    @Test
    fun `an access code kept is handed over, and none kept is unsupported`() {
        val kept = Custom(Laid(), Answering(kept = bytes(9, 8, 7, 6)))
        val room = Memory(8)
        assertEquals(0, kept.ioctl(Custom.GET_ACCESSCODE, room, 8))
        assertContentEquals(bytes(9, 8, 7, 6), room.getByteArray(0, 4))
        val none = Custom(Laid(), Answering())
        assertEquals(Libdivecomputer.UNSUPPORTED, none.ioctl(Custom.GET_ACCESSCODE, Memory(8), 8))
    }

    @Test
    fun `an access code handed over is kept under the device's name`() {
        val session = Answering()
        val custom = Custom(Laid(name = "FQ001124"), session)
        val given = Memory(4).also { it.write(0, bytes(1, 2, 3, 4), 0, 4) }
        assertEquals(0, custom.ioctl(Custom.SET_ACCESSCODE, given, 4))
        assertEquals("FQ001124", session.given?.first)
        assertContentEquals(bytes(1, 2, 3, 4), session.given?.second)
    }
}
