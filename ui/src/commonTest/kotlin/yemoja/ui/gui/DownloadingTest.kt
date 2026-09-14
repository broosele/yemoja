package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Matching
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Reading a dive computer, as far as it goes without one.
 * See ../../../../../../gui/doc.md — `GUI-31`.
 */
class DownloadingTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    private fun arriving(vararg files: Pair<String, String>) =
        LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

    @Test
    fun `each stage says what is happening, and an idle one says nothing`() {
        assertNull(sayingOf(Stage.IDLE, null))
        assertEquals("Looking for a dive computer…", sayingOf(Stage.LOOKING, null))
        assertTrue(sayingOf(Stage.CHOOSING, null)!!.startsWith("More than one"))
        assertEquals(
            "Reading Perdix 2. A full computer takes minutes.",
            sayingOf(Stage.READING, "Perdix 2"),
        )
        assertNull(sayingOf(Stage.DONE, null), "what came of it is said another way")
    }

    @Test
    fun `finding nothing is said two ways, the remedies being nothing alike`() {
        val nothing = emptyOf(true)
        val nowhere = emptyOf(false)
        assertTrue("within reach" in nothing, nothing)
        assertTrue("Bluetooth" in nothing, "what to do about it")
        assertTrue("libdivecomputer" in nowhere, nowhere)
        assertTrue("within reach" !in nowhere, "not a computer's fault")
    }

    @Test
    fun `what came of a download is how many arrived, or why none did`() {
        assertEquals("3 dives came across.", outcomeOf(Outcome.Done(), 3))
        assertEquals("1 dive came across.", outcomeOf(Outcome.Done(), 1))
        assertEquals("nothing new", outcomeOf(Outcome.Refused("nothing new"), 0))
    }

    @Test
    fun `how many arrived is how many dives the staging holds`() {
        assertEquals(0, arrivedIn(null))
        val into = logbook()
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"max_depth": 30.0}""",
                "dive/2026-06-22#0.json" to """{"max_depth": 18.0}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        assertEquals(2, arrivedIn(into.importing))
    }

    @Test
    fun `taking them all in empties the staging into the logbook`() {
        val into = logbook()
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"max_depth": 30.0}""",
                "dive/2026-06-22#0.json" to """{"max_depth": 18.0}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val taken = takenIn(into.importing!!, into.logbook)
        assertEquals(2, taken.many)
        assertNull(taken.refusal)
        assertEquals(2, into.logbook.allOf(Types.DIVE).size, "both are in the logbook now")
        assertEquals(0, arrivedIn(into.importing), "and none is left staged")
    }

    private val ONE = """{"start_date": "2026-06-21", "start_time": "10:00:00",
        "duration": 3600, "max_depth": 30.0,
        "profiles": {"perdix_2": {"fingerprint": ["a1"], "depth": [[0, 0], [60, 30.0]]}}}"""

    // The same dive, seen by the other computer: it overlaps, and nobody is on two at once.
    private val OTHER = """{"start_date": "2026-06-21", "start_time": "10:11:00",
        "duration": 3400, "max_depth": 29.4,
        "profiles": {"i330r": {"fingerprint": ["b2"], "depth": [[0, 0], [60, 29.4]]}}}"""

    private val LATER = """{"start_date": "2026-06-21", "start_time": "14:00:00",
        "duration": 2400, "max_depth": 18.0,
        "profiles": {"perdix_2": {"fingerprint": ["c3", "c4"], "depth": [[0, 0], [60, 18.0]]}}}"""

    @Test
    fun `a dive that overlaps one already held is the same dive, and says so`() {
        val into = logbook("dive/2026-06-21#0.json" to ONE)
        into.importFrom(arriving("dive/2026-06-21#0.json" to OTHER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val waiting = arrivingIn(into.importing!!, into.logbook, 1).single()
        assertEquals("2026-06-21#0", waiting.onto, "the dive it overlaps")
        assertTrue(waiting.ontoSaid!!.startsWith("10:00"), waiting.ontoSaid)
        assertTrue("Perdix 2" in waiting.ontoSaid, "said by what recorded it")
    }

    @Test
    fun `a dive that overlaps nothing is one of its own, under the next number`() {
        val into = logbook("dive/2026-06-21#0.json" to ONE)
        into.importFrom(arriving("dive/2026-06-21#1.json" to LATER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val waiting = arrivingIn(into.importing!!, into.logbook, 339).single()
        assertNull(waiting.onto)
        assertEquals(339, waiting.number)
        assertEquals(2, waiting.glued, "two recordings were put together to make it")
    }

    @Test
    fun `dives are numbered up through the day, the newest taking the highest`() {
        val into = logbook("dive/2026-01-01#0.json" to """{"dive_number": 338}""")
        into.importFrom(
            arriving(
                "dive/2026-09-13#0.json" to """{"start_date": "2026-09-13",
                    "start_time": "14:00:00", "duration": 2400}""",
                "dive/2026-09-13#1.json" to """{"start_date": "2026-09-13",
                    "start_time": "10:00:00", "duration": 2400}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val import = into.importing!!
        val waiting = arrivingIn(import, into.logbook, nextNumberIn(into.logbook))
        assertEquals(listOf("2026-09-13#1", "2026-09-13#0"), waiting.map { it.id }, "oldest first")
        assertEquals(listOf(339, 340), waiting.map { it.number })
        assertTrue(waiting.first().said.startsWith("10:00"), waiting.first().said)
    }

    @Test
    fun `the number to give the next dive is one past the highest held`() {
        assertEquals(1, nextNumberIn(logbook().logbook), "an empty logbook starts at one")
        val into = logbook(
            "dive/2026-06-21#0.json" to """{"dive_number": 338}""",
            "dive/2026-06-20#0.json" to """{"dive_number": 12}""",
        )
        assertEquals(339, nextNumberIn(into.logbook))
    }

    @Test
    fun `taken in as its own, a dive carries the number it was offered`() {
        val into = logbook("dive/2026-06-21#0.json" to """{"dive_number": 338}""")
        into.importFrom(arriving("dive/2026-06-21#1.json" to LATER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val import = into.importing!!
        val waiting = arrivingIn(import, into.logbook, nextNumberIn(into.logbook)).single()
        assertEquals(339, waiting.number)
        takeIn(import, waiting, null)
        val numbers = into.logbook.allOf(Types.DIVE).mapNotNull {
            ((it.read("dive_number") as? yemoja.data.Result.Usable)?.value as? Number)?.toInt()
        }
        assertEquals(listOf(338, 339), numbers.sorted())
    }

    private fun primaryOf(dive: yemoja.data.Item): String? =
        ((dive.read("primary_profile") as? yemoja.data.Result.Usable)?.value
            as? yemoja.data.KeyReference)?.key

    @Test
    fun `taken in as its own, the recording it came with is the one it is worked from`() {
        val into = logbook()
        into.importFrom(arriving("dive/2026-06-21#1.json" to LATER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val import = into.importing!!
        takeIn(import, arrivingIn(import, into.logbook, 1).single(), null)
        assertEquals("perdix_2", primaryOf(into.logbook.allOf(Types.DIVE).single()))
    }

    @Test
    fun `put together, the recording already there stays the one it is worked from`() {
        val into = logbook()
        into.importFrom(arriving("dive/2026-06-21#0.json" to ONE),
            MemoryFileStore(emptyMap()), Matching.NONE)
        takeIn(into.importing!!, arrivingIn(into.importing!!, into.logbook, 1).single(), null)
        into.importFrom(arriving("dive/2026-06-21#0.json" to OTHER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val second = into.importing!!
        val waiting = arrivingIn(second, into.logbook, 2).single()
        takeIn(second, waiting, waiting.onto)
        val held = into.logbook.allOf(Types.DIVE).single()
        assertEquals("perdix_2", primaryOf(held), "the second computer does not displace it")
    }

    @Test
    fun `put together, it lands on the dive it was and takes no number of its own`() {
        val into = logbook("dive/2026-06-21#0.json" to ONE)
        into.importFrom(arriving("dive/2026-06-21#0.json" to OTHER),
            MemoryFileStore(emptyMap()), Matching.NONE)
        val import = into.importing!!
        val waiting = arrivingIn(import, into.logbook, 339).single()
        takeIn(import, waiting, waiting.onto)
        assertEquals(1, into.logbook.allOf(Types.DIVE).size, "one dive, not two")
        val held = into.logbook["2026-06-21#0"]!!
        val profiles = (held.keyed<yemoja.data.OwnedItem>("profiles")
            as yemoja.data.Result.Usable).value
        assertEquals(setOf("perdix_2", "i330r"), profiles.keys, "both recordings on it")
    }

    @Test
    fun `an arriving dive keeps what it said`() {
        val into = logbook("dive/2026-01-01#0.json" to """{"max_depth": 12.0}""")
        into.importFrom(
            arriving("dive/2026-06-21#0.json" to """{"max_depth": 30.0, "rating": 8}"""),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        takenIn(into.importing!!, into.logbook)
        assertEquals(2, into.logbook.allOf(Types.DIVE).size, "the one held and the one that came")
        val arrived = into.logbook.allOf(Types.DIVE).first { it !== into.logbook["2026-01-01#0"] }
        assertEquals(30.0, (arrived.single<Double>("max_depth") as yemoja.data.Result.Usable).value)
    }

    @Test
    fun `nothing staged is nothing taken`() {
        val into = logbook()
        into.importFrom(arriving(), MemoryFileStore(emptyMap()), Matching.NONE)
        val taken = takenIn(into.importing!!, into.logbook)
        assertEquals(0, taken.many)
        assertNull(taken.refusal)
    }

    private val SITES = """{"gombe": {"name": "La Gombe", "latitude": 50.4213,
        "longitude": 4.5077}, "floreffe": {"name": "Carrière de Floreffe",
        "latitude": 50.4342, "longitude": 4.7602}}"""

    // A dive at La Gombe, as a download brings one: a site of its own carrying the fix.
    private val FIXED = """{"start_date": "2026-06-21", "start_time": "10:00:00",
        "duration": 3600, "dive_site": "@unknown_dive_site"}"""

    private val FOUND = """{"unknown_dive_site": {"latitude": 50.4215, "longitude": 4.5079}}"""

    private fun waiting(): Pair<Universe, Arriving> {
        val into = logbook("dive_site.json" to SITES)
        into.importFrom(
            arriving("dive/2026-06-21#0.json" to FIXED, "dive_site.json" to FOUND),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val import = into.importing!!
        return into to arrivingIn(import, into.logbook, 1).single()
    }

    @Test
    fun `a dive that came with a fix asks where it was, offering what is near`() {
        val (_, dive) = waiting()
        assertEquals("unknown_dive_site", dive.site)
        assertEquals("50.4215, 4.5079", dive.fix)
        assertEquals("La Gombe", dive.nearby.first().name, "the nearest site already held")
        assertTrue(dive.nearby.first().metres < 40, "and it is metres away, not kilometres")
        assertEquals(2, dive.nearby.size, "both sites offered, nearest first")
    }

    @Test
    fun `a distance reads in metres up to a kilometre and in kilometres past it`() {
        assertEquals("24 m", apartOf(24.6))
        assertEquals("999 m", apartOf(999.4))
        assertEquals("18.3 km", apartOf(18_340.0))
    }

    @Test
    fun `answered with a site already held, the dive names it and the fix is dropped`() {
        val (into, dive) = waiting()
        val import = into.importing!!
        answered(import, dive, "gombe")
        assertNull(import.staged.logbook["unknown_dive_site"], "a fix nobody claimed is no item")
        val after = arrivingIn(import, into.logbook, 1).single()
        assertNull(after.fix, "nothing left to ask")
        takeIn(import, after, null)
        val landed = into.logbook.allOf(Types.DIVE).single()
        val named = landed.single<yemoja.data.Reference>("dive_site")
        val site = (named as yemoja.data.Result.Usable).value
        assertEquals("gombe", (site as yemoja.data.Reference.Identified).id)
        assertEquals(2, into.logbook.allOf(Types.DIVE_SITE).size, "no site was added")
    }

    @Test
    fun `answered with nowhere, the dive names nothing and the fix is dropped`() {
        val (into, dive) = waiting()
        val import = into.importing!!
        answered(import, dive, null)
        assertNull(import.staged.logbook["unknown_dive_site"])
        takeIn(import, arrivingIn(import, into.logbook, 1).single(), null)
        val landed = into.logbook.allOf(Types.DIVE).single()
        assertEquals(yemoja.data.Result.Absent, landed.read("dive_site"))
        assertEquals(2, into.logbook.allOf(Types.DIVE_SITE).size)
    }

    @Test
    fun `a fix nobody answered is not taken in as a site`() {
        val (into, dive) = waiting()
        val import = into.importing!!
        takeIn(import, dive, null)
        assertEquals(2, into.logbook.allOf(Types.DIVE_SITE).size, "no unknown site was added")
        assertEquals(
            yemoja.data.Result.Absent,
            into.logbook.allOf(Types.DIVE).single().read("dive_site"),
            "and the dive names nowhere rather than a decimal position",
        )
    }

    @Test
    fun `named, the fix becomes a site of its own and lands with the dive`() {
        val (into, dive) = waiting()
        val import = into.importing!!
        namedAs(import, dive, "Nieuwdorp")
        takeIn(import, arrivingIn(import, into.logbook, 1).single(), null)
        assertEquals(3, into.logbook.allOf(Types.DIVE_SITE).size, "the new one as well")
        val made = into.logbook["unknown_dive_site"]!!
        assertEquals("Nieuwdorp", (made.single<String>("name") as yemoja.data.Result.Usable).value)
        assertEquals(50.4215, (made.single<Double>("latitude") as yemoja.data.Result.Usable).value)
    }
}
