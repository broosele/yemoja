package yemoja.logic

import yemoja.data.Date
import yemoja.data.Stored
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.divecomputer.DiveComputer
import yemoja.logic.divecomputer.Recording
import yemoja.logic.divecomputer.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * A read of a dive computer that runs while the logbook is edited, and is staged afterwards.
 * `GUI-52`.
 */

/** A computer holding two dives, which reports its progress and asks whether to stop. */
private class Counting(private val tokens: List<String> = listOf("aa", "bb")) : DiveComputer {

    override val name: String = "Reef"

    var resumedFrom: String? = null
    var offered: ByteArray? = null

    /** What to do between the first dive and the second, as a device would between blocks. */
    var between: () -> Unit = {}

    override fun recordings(session: Session): Sequence<Recording> {
        offered = session.accessCode("Reef 77")
        resumedFrom = session.resume("77")
        val held = ArrayList<Recording>()
        for ((index, token) in tokens.withIndex()) {
            if (session.cancelled) break
            session.progress(index * 100L, tokens.size * 100L)
            held += Recording(
                computer = name,
                serial = "77",
                fingerprints = listOf(token),
                began = Date(2026, 8, index + 1),
                at = Time(9, 0, 0),
            )
            between()
        }
        session.progress(tokens.size * 100L, tokens.size * 100L)
        return held.asSequence()
    }
}

/** A logbook holding the computer as gear, and one dive it made, with token `aa`. */
private fun universe(): Universe {
    val store = MemoryFileStore(
        mapOf(
            "gear.json" to """{"reef": {"name": "Reef", "serial": "77", "access_code": "0102"}}""",
            "dive/2026-07-01#0.json" to """{"profiles": {"reef": {"dive_computer": "@reef",
                "fingerprint": "aa", "start_date": "2026-07-01", "start_time": "10:00:00"}}}""",
        ),
    )
    val staging = MemoryFileStore(emptyMap())
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null, staging)
}

class DeviceReadTest {

    @Test
    fun `what the read asks is answered from the logbook as it was when the read was made`() {
        val universe = universe()
        val device = Counting()
        val read = universe.readerOf(device)
        // An edit while the read waits to run: the gear loses its serial and its code.
        val gear = assertNotNull(universe.logbook["reef"])
        universe.change(Operation.EDIT, Change.Write(gear, "access_code", Stored.Leaf(null)))
        read.run()
        assertEquals("aa", device.resumedFrom, "where the last download stopped")
        val offered = device.offered?.map { it.toInt() }
        assertEquals(listOf(1, 2), offered, "the code kept before the edit")
    }

    @Test
    fun `progress is passed on as the device counts it`() {
        val heard = ArrayList<Pair<Long, Long>>()
        universe().readerOf(Counting()).run { done, total -> heard += done to total }
        assertEquals(listOf(0L to 200L, 100L to 200L, 200L to 200L), heard)
    }

    @Test
    fun `a read given up hands back nothing, not what had arrived so far`() {
        val universe = universe()
        val device = Counting(listOf("bb", "cc"))
        val read = universe.readerOf(device)
        device.between = { read.cancelled = true }
        read.run()
        val outcome = assertIs<Outcome.Refused>(universe.arrive(read))
        assertTrue("Nothing new" in outcome.reason, outcome.reason)
    }

    @Test
    fun `a recording whose token the logbook holds is not offered again`() {
        val universe = universe()
        val read = universe.readerOf(Counting())
        read.run()
        assertIs<Outcome.Done>(universe.arrive(read))
        val staged = assertNotNull(universe.importing).let { arrivedOf(it) }
        assertEquals(1, staged, "aa is in the logbook already, and bb is not")
    }

    @Test
    fun `staged again, a read meets the logbook as it is now`() {
        val universe = universe()
        val read = universe.readerOf(Counting(listOf("bb")))
        read.run()
        assertIs<Outcome.Done>(universe.arrive(read))
        universe.stopImporting()
        // The dive taken in by hand meanwhile, carrying the token the device gave.
        universe.change(
            Operation.EDIT,
            Change.Add(
                Types.DIVE,
                mapOf(
                    "start_date" to Stored.Leaf("2026-08-01"),
                    "profiles" to Stored.Members(
                        mapOf("reef" to Stored.Members(mapOf("fingerprint" to Stored.Leaf("bb")))),
                    ),
                ),
            ),
        )
        assertIs<Outcome.Refused>(universe.arrive(read), "nothing left to offer")
    }
}

/** How many dives [import] holds for review. */
private fun arrivedOf(import: Import): Int = import.staged.logbook.allOf(Types.DIVE).size
