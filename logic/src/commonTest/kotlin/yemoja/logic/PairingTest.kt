package yemoja.logic

import yemoja.data.Date
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.divecomputer.DiveComputer
import yemoja.logic.divecomputer.Recording
import yemoja.logic.divecomputer.Session
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/*
 * Reading a computer that guards itself: the code asked once, the access code kept. `LOGIC-24`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** A computer that asks the session what the i330R's driver asks, in the order it asks. */
private class Guarded(private val serial: String, private val handsBack: ByteArray) : DiveComputer {

    override val name: String = "Aqualung i330R over Bluetooth"

    var offered: ByteArray? = null
    var askedForCode = false
    var resumedFrom: String? = null

    override fun recordings(session: Session): Sequence<Recording> {
        offered = session.accessCode(ADVERTISED)
        if (offered == null) {
            askedForCode = true
            session.pin(ADVERTISED) ?: return emptySequence()
            session.keep(ADVERTISED, handsBack)
        }
        resumedFrom = session.resume(serial)
        val dive = Recording(
            computer = name,
            serial = serial,
            fingerprints = listOf("0a0b"),
            began = Date(2026, 8, 1),
            at = Time(9, 0, 0),
        )
        return sequenceOf(dive)
    }

    companion object {
        /** How such a computer announces itself: a model code and its serial. */
        const val ADVERTISED = "FQ001124"
    }
}

private fun universe(gear: String): Universe {
    val store = MemoryFileStore(mapOf("gear.json" to gear))
    val staging = MemoryFileStore(emptyMap())
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null, staging)
}

private fun accessCodeOf(universe: Universe, id: String): String? =
    (universe.logbook[id]?.single<String>("access_code") as? Result.Usable)?.value

class PairingTest {

    private val handed = byteArrayOf(1, 2, 254.toByte())

    @Test
    fun `the first time the code is asked, and what comes back is kept on the gear item`() {
        val universe = universe("""{"i330r": {"name": "i330R", "serial": "001124"}}""")
        val device = Guarded("1124", handed)
        var asked: String? = null
        assertIs<Outcome.Done>(universe.downloadFrom(device) { asked = it; "123456" })
        assertTrue(device.askedForCode)
        assertNotNull(asked, "the question reached the front end")
        assertEquals("0102fe", accessCodeOf(universe, "i330r"), "as hexadecimal, beside the serial")
    }

    @Test
    fun `the next time the kept code goes over by the advertised name, and nothing is asked`() {
        val universe = universe(
            """{"i330r": {"name": "i330R", "serial": "001124", "access_code": "0102fe"}}""",
        )
        val device = Guarded("1124", byteArrayOf())
        assertIs<Outcome.Done>(universe.downloadFrom(device) { fail("nothing should be asked") })
        assertContentEquals(handed, device.offered)
        assertFalse(device.askedForCode)
    }

    @Test
    fun `nobody able to type gives the download up, and keeps nothing`() {
        val universe = universe("""{"i330r": {"name": "i330R", "serial": "001124"}}""")
        val device = Guarded("1124", handed)
        assertIs<Outcome.Refused>(universe.downloadFrom(device))
        assertNull(accessCodeOf(universe, "i330r"))
    }

    @Test
    fun `a serial no gear item carries has nowhere to keep the code`() {
        val universe = universe("""{"i330r": {"name": "i330R"}}""")
        val device = Guarded("1124", handed)
        assertIs<Outcome.Done>(universe.downloadFrom(device) { "123456" })
        assertNull(accessCodeOf(universe, "i330r"), "it is asked again next time")
    }

    @Test
    fun `where to stop is answered once the device has said its serial`() {
        val universe = universe(
            """{"i330r": {"name": "i330R", "serial": "001124", "access_code": "0102fe"}}""",
        )
        val device = Guarded("1124", byteArrayOf())
        universe.downloadFrom(device)
        assertNull(device.resumedFrom, "nothing of this computer's is held yet")
    }
}
