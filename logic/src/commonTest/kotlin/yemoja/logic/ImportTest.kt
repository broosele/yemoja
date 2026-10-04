package yemoja.logic

import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.ItemWriter
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.Time
import yemoja.data.Units
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Items arriving from somewhere else, staged, reviewed and taken in.
 *
 * See ../../../../../doc.md — the layer's own document is logic/reconciliation.md.
 */

/** A Universe over these files. */
private fun logbook(vararg files: Pair<String, String>): Pair<MemoryFileStore, Universe> {
    val store = MemoryFileStore(mapOf(*files))
    return store to Universe(LogbookReader.read(store, Types.ALL), null, store)
}

/** An item set read from these files, which is what a source hands over. */
private fun arriving(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** An import of [from] into [into], staged in a folder of its own. */
private fun staged(
    from: Array<out Pair<String, String>>,
    into: Universe,
): Pair<MemoryFileStore, Import> {
    val staging = MemoryFileStore(emptyMap())
    return staging to Import.begin(arriving(*from), staging, into)
}

private fun nameOf(universe: Universe, id: String): String? =
    (universe.logbook[id]?.single<String>("name") as? Result.Usable)?.value

class StagedImportTest {

    @Test
    fun `the incoming items are written out as a logbook of their own`() {
        // Which is what makes reviewing one cost nothing new. `RECON-1`.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        // A file an item, since each leaves the folder as it is decided.
        assertTrue(staging.isFile("region/a.json"), staging.namesIn("").toString())
        assertEquals("A", nameOf(import.staged, "a"))
    }

    @Test
    fun `staging says how many items of how many are written`() {
        val (_, into) = logbook("region.json" to "{}")
        val heard = ArrayList<Pair<Int, Int>>()
        Import.begin(
            arriving("region.json" to """{"a": {"name": "A"}, "b": {"name": "B"}}""", "dive/d#0.json" to "{}"),
            MemoryFileStore(emptyMap()),
            into,
        ) { done, of -> heard += done to of }
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), heard)
    }

    @Test
    fun `a reference between two incoming items resolves among them`() {
        // Not in the logbook they are going into, which does not hold either of them yet.
        val (_, into) = logbook("person.json" to "{}")
        val (_, import) = staged(
            arrayOf(
                "person.json" to """{"tom": {"first_name": "Tom"}}""",
                "dive/d#0.json" to """{"buddies": ["@tom"]}""",
            ),
            into,
        )
        val dive = import.staged.logbook["d#0"]!!
        val read = dive.list<yemoja.data.Reference>("buddies")
        assertIs<Result.Usable<*>>(read)
        val first = ((read as Result.Usable).value.first() as Element.Usable).value
        assertIs<yemoja.data.Reference.Identified>(first)
    }

    @Test
    fun `an incoming item can be edited before it is taken in`() {
        // It is a logbook, so it is edited by what edits a logbook, and saved as it is edited.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        val region = import.staged.logbook["a"]!!
        import.staged.change(Operation.EDIT, Change.Write(region, "name", "Corrected"))
        assertTrue("Corrected" in staging.readText("region/a.json"), staging.readText("region/a.json"))
        import.apply()
        assertEquals("Corrected", nameOf(into, "a"))
    }
}

class MetImportTest {

    @Test
    fun `an item nothing answers to is new`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}}"""), into)
        assertEquals(Meeting.NOTHING, import.meeting("a"))
    }

    @Test
    fun `one of the same type under the same id is the same item`() {
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertEquals(Meeting.THE_SAME, import.meeting("a"))
    }

    @Test
    fun `an id taken by another type is something else`() {
        // Two logbooks can each mint north_sea, one for a region and one for a person.
        val (_, into) = logbook("region.json" to """{"north_sea": {}}""")
        val (_, import) = staged(arrayOf("person.json" to """{"north_sea": {}}"""), into)
        assertEquals(Meeting.SOMETHING_ELSE, import.meeting("north_sea"))
    }
}

class AppliedImportTest {

    @Test
    fun `a new item goes in under the id it came with`() {
        // Mint a new one and the dive that names the person no longer finds them.
        val (store, into) = logbook("person.json" to "{}", "dive/d#0.json" to "{}")
        val (_, import) = staged(
            arrayOf(
                "person.json" to """{"tom": {"first_name": "Tom"}}""",
                "dive/d#1.json" to """{"buddies": ["@tom"]}""",
            ),
            into,
        )
        assertIs<Outcome.Done>(import.apply())
        assertTrue(into.logbook["tom"] != null)
        assertTrue("@tom" in store.readText("dive/d#1.json"), store.readText("dive/d#1.json"))
    }

    @Test
    fun `a field the incoming item holds is written onto the one already held`() {
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Theirs", nameOf(into, "a"))
    }

    @Test
    fun `a field it does not hold is left alone`() {
        // Silence is not an instruction to erase: a dive computer knows nothing about buddies.
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine", "category": "sea"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Theirs", nameOf(into, "a"), "the one it does hold was written")
        val category = into.logbook["a"]!!.single<String>("category")
        assertEquals("sea", (category as Result.Usable).value, "and the one it does not survived")
    }

    @Test
    fun `an id taken by another type is left out`() {
        val (_, into) = logbook("region.json" to """{"north_sea": {"name": "Mine"}}""")
        val (_, import) = staged(
            arrayOf("person.json" to """{"north_sea": {"first_name": "Tom"}}"""),
            into,
        )
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Mine", nameOf(into, "north_sea"), "and what was there is untouched")
    }

    @Test
    fun `applying twice does nothing the second time`() {
        // What was added answers to its id afterwards, so it is met as the same item.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertIs<Outcome.Done>(import.apply())
        assertEquals(1, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `it lands whole or not at all`() {
        // One change, so the journal has one thing to record and one to take back. `REQ-2`.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(
            arrayOf("region.json" to """{"a": {"name": "A"}, "b": {"north": 91}}"""),
            into,
        )
        assertIs<Outcome.Refused>(import.apply())
        assertEquals(0, into.logbook.allOf(Types.REGION).size, "including the good one")
    }
}

class DecidedImportTest {

    @Test
    fun `an item taken in leaves the staged logbook`() {
        // Deciding is editing, so what is left in the folder is what has not been decided.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        assertIs<Outcome.Done>(import.insert("a"))
        assertTrue(into.logbook["a"] != null)
        assertEquals(listOf("b"), import.incoming.map { import.staged.logbook.idOf(it) })
        assertTrue(!staging.isFile("region/a.json") && staging.isFile("region/b.json"), staging.namesIn("region").toString())
    }

    @Test
    fun `an item turned down leaves it too, and goes nowhere`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        assertEquals(listOf("b"), import.incoming.map { import.staged.logbook.idOf(it) })
        assertEquals(0, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `a refused item stays where it is`() {
        // So the reason can be shown against an item still on the screen.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"north": 91}}"""), into)
        assertIs<Outcome.Refused>(import.insert("a"))
        assertEquals(listOf("a"), import.incoming.map { import.staged.logbook.idOf(it) })
    }

    @Test
    fun `a review put down is taken up where it stopped`() {
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        val again = Import.open(staging, Types.ALL, into)
        assertEquals(listOf("b"), again.incoming.map { again.staged.logbook.idOf(it) })
    }

    @Test
    fun `applying takes everything left across`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        assertIs<Outcome.Done>(import.apply())
        assertEquals(listOf("b"), into.logbook.allOf(Types.REGION).map { into.logbook.idOf(it) })
        assertEquals(emptyList(), import.incoming, "and nothing is left to review")
    }
}

class ListedImportTest {

    @Test
    fun `the incoming items are listed, each type in its own order`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(
            arrayOf("region.json" to """{"b": {"name": "Bee"}, "a": {"name": "Ay"}}"""),
            into,
        )
        val said = import.incoming.map { import.staged.logbook.idOf(it) }
        assertEquals(listOf("a", "b"), said, "alphabetical, as a region's tab lists them")
    }
}

/** A source whose ids were minted here, which no id may be matched by. */
class UnmatchedImportTest {

    private fun minted(held: String, coming: String): Pair<Universe, Import> {
        val store = MemoryFileStore(mapOf("region.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val source = LogbookReader.read(MemoryFileStore(mapOf("region.json" to coming)), Types.ALL)
        into.importFrom(source, MemoryFileStore(emptyMap()), Matching.NONE)
        return into to into.importing!!
    }

    @Test
    fun `nothing is met, however its id reads`() {
        // A minted id says what this model would have called such an item, not which it is.
        val (_, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertEquals(Meeting.NOTHING, import.meeting("a"))
    }

    @Test
    fun `an item already held is left exactly as it was`() {
        // The bug this exists for: an unrelated item under a colliding id was written over.
        val (into, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Mine", nameOf(into, "a"))
    }

    @Test
    fun `the arriving one goes in beside it, under a name that is free`() {
        val (into, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertIs<Outcome.Done>(import.apply())
        val held = into.logbook.allOf(Types.REGION).map { into.logbook.idOf(it) }
        assertEquals(listOf("a", "a#1"), held)
        assertEquals("Theirs", nameOf(into, "a#1"))
    }

    @Test
    fun `two arriving under one taken name do not land on each other`() {
        val coming = """{"a": {"name": "One"}, "b": {"name": "Two"}}"""
        val (into, import) = minted("""{"a": {}}""", coming)
        assertIs<Outcome.Done>(import.apply())
        assertEquals(3, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `importing the same thing twice puts it in twice, which is visible`() {
        // Not what anybody wants, and better than quietly writing over what was there. The rule
        // that recognises an item by what it says is what reconciliation.md asks for.
        val store = MemoryFileStore(mapOf("region.json" to "{}"))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val coming = """{"a": {"name": "Theirs"}}"""
        repeat(2) {
            val from = MemoryFileStore(mapOf("region.json" to coming))
            into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
                Matching.NONE)
            into.importing!!.apply()
        }
        assertEquals(2, into.logbook.allOf(Types.REGION).size)
    }
}

/** Which item already held an arriving one appears to be, where nothing was matched by id. */
class ProposalTest {

    private fun dived(at: String, long: String = "3600") =
        """{"start_date": "2024-06-15", "start_time": "$at", "duration": $long}"""

    private fun over(held: String, coming: String): Import {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("dive/x#0.json" to coming))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
            Matching.NONE)
        return into.importing!!
    }

    @Test
    fun `one that overlaps in time is what it appears to be`() {
        // Nobody is on two dives at once, so two recordings that overlap are one dive's.
        assertEquals("d#0", over(dived("10:00:00"), dived("10:30:00")).proposal("x#0"))
    }

    @Test
    fun `one that does not overlap is nothing`() {
        assertNull(over(dived("10:00:00"), dived("14:00:00")).proposal("x#0"))
    }

    @Test
    fun `two beginning at the same instant meet, however long they say they were`() {
        val without = """{"start_date": "2024-06-15", "start_time": "10:00:00"}"""
        assertEquals("d#0", over(without, without).proposal("x#0"))
    }

    @Test
    fun `two dives are compared on one clock, whatever zone each was logged in`() {
        // 10:00 two hours ahead of GMT and 09:00 an hour ahead are the same instant.
        val held = """{"start_date": "2024-06-15", "start_time": "10:00:00",
            "time_zone_offset": 7200, "duration": 600}"""
        val coming = """{"start_date": "2024-06-15", "start_time": "09:00:00",
            "time_zone_offset": 3600, "duration": 600}"""
        assertEquals("d#0", over(held, coming).proposal("x#0"))
        assertNull(over(held, dived("09:00:00", "600")).proposal("x#0"), "an hour apart on GMT")
    }

    @Test
    fun `one that says no time appears to be nothing`() {
        assertNull(over(dived("10:00:00"), """{"max_depth": 12}""").proposal("x#0"))
    }

    /** An import of one region into a logbook holding another, matching nothing. */
    private fun regions(held: String, coming: String): Import {
        val store = MemoryFileStore(mapOf("region.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("region.json" to coming))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
            Matching.NONE)
        return into.importing!!
    }

    @Test
    fun `a type that keeps no times is proposed by the name its type would give it`() {
        // Nine of the ten propose an id from the item's own name, so the proposal is the name
        // match: two logbooks each holding a Blue Hole both propose blue_hole.
        val import = regions("""{"x": {"name": "North Sea"}}""", """{"y": {"name": "North Sea"}}""")
        assertEquals("x", import.proposal("y"))
    }

    @Test
    fun `one of another name is proposed as nothing`() {
        val import = regions("""{"x": {"name": "North Sea"}}""", """{"y": {"name": "Red Sea"}}""")
        assertNull(import.proposal("y"))
    }

    @Test
    fun `two nobody named are not proposed as one`() {
        // They would both fall back to unknown_region, which says nothing about either.
        assertNull(regions("""{"x": {}}""", """{"y": {}}""").proposal("y"))
    }

    @Test
    fun `the index a proposal carries is left off`() {
        // It says where an item sat rather than what it is, which is what makes an id unusable
        // for matching in the first place.
        val held = """{"north_sea": {"name": "North Sea"}, "b": {"name": "North Sea"}}"""
        val import = regions(held, """{"y": {"name": "North Sea"}}""")
        assertEquals("north_sea", import.proposal("y"))
    }

    @Test
    fun `taking one in onto another writes its fields there and adds nothing`() {
        val import = over(dived("10:00:00"), """{"start_date": "2024-06-15",
            "start_time": "10:30:00", "max_depth": 28.4}""")
        assertIs<Outcome.Done>(import.insert("x#0", "d#0"))
        val into = import.staged
        assertEquals(0, into.logbook.allOf(Types.DIVE).size, "and it left the review")
    }

    @Test
    fun `a carried id is not asked about`() {
        val store = MemoryFileStore(mapOf("region.json" to "{}"))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {}}"""))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()))
        assertTrue(!into.importing!!.asked("a"))
    }
}

/** How a staged review's items match is written beside them, so taking it up cannot forget. */
class RememberedMatchingTest {

    private fun holding(held: String): Universe {
        val store = MemoryFileStore(mapOf("region.json" to held))
        return Universe(LogbookReader.read(store, Types.ALL), null, store)
    }

    @Test
    fun `a review taken up again matches as it did`() {
        // Forgetting would match a minted id as though it were carried, which is the overwriting
        // Matching.NONE exists to stop.
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(emptyMap())
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        Import.begin(LogbookReader.read(from, Types.ALL), staging, into, Matching.NONE)
        assertTrue(staging.isFile(Import.ABOUT), "the matching is written beside the items")
        assertEquals(Meeting.NOTHING, Import.open(staging, Types.ALL, into).meeting("a"))
    }

    @Test
    fun `one begun as another logbook still matches by id when taken up`() {
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(emptyMap())
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        Import.begin(LogbookReader.read(from, Types.ALL), staging, into)
        assertEquals(Meeting.THE_SAME, Import.open(staging, Types.ALL, into).meeting("a"))
    }

    @Test
    fun `a folder with no note is read as matching nothing`() {
        // The safe reading of an unknown source is the one that duplicates rather than overwrites.
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        assertEquals(Meeting.NOTHING, Import.open(staging, Types.ALL, into).meeting("a"))
    }
}

/** A second computer's recording of a dive already logged is a second profile on it. `RECON-7`. */
class SecondComputerTest {

    private val held = """{"start_date": "2024-06-15", "start_time": "10:00:00",
        "gas_sources": {"g1": {"gas_type": "EAN32"}, "g2": {"gas_type": "EAN50"}},
        "profiles": {"p1": {"serial": "1111", "fingerprint": ["aa"], "start_date": "2024-06-15",
            "start_time": "10:00:00", "depth": [[0, 0], [600, 30], [1800, 0]],
            "gas_switches": [[0, "*g1"], [1500, "*g2"]]}}}"""

    /** [coming] taken in onto the held dive, and the dive as it then reads. */
    private fun landed(coming: String, holding: String = held): Item {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to holding))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("dive/x#0.json" to coming))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()), Matching.NONE)
        assertIs<Outcome.Done>(into.importing!!.insert("x#0", "d#0"))
        return into.logbook["d#0"]!!
    }

    private fun recording(serial: String, fingerprint: String, gases: String, switches: String, pressures: String = "{}") =
        """{"start_date": "2024-06-15", "start_time": "10:02:00",
        "gas_sources": $gases,
        "profiles": {"p1": {"serial": "$serial", "fingerprint": ["$fingerprint"],
            "start_date": "2024-06-15", "start_time": "10:02:00",
            "depth": [[0, 0], [600, 31.5], [1700, 0]], "gas_switches": $switches,
            "pressures": $pressures}}}"""

    private fun profilesOf(dive: Item): Map<String, OwnedItem> =
        (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
            .mapValues { (it.value as Element.Usable).value }

    private fun sourcesOf(dive: Item): Map<String, String?> =
        (dive.keyed<OwnedItem>("gas_sources") as Result.Usable).value
            .mapValues { ((it.value as Element.Usable).value.single<Gas>("gas_type") as? Result.Usable)?.value?.toString() }

    private fun switchesOf(profile: Item): String =
        ItemWriter.write(profile, Units.DEFAULT).members["gas_switches"].toString()

    @Test
    fun `another computer's recording goes in beside the first, which stays primary`() {
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "EAN32"}}""", """[[0, "*g1"]]"""))
        assertEquals(setOf("p1", "p2"), profilesOf(dive).keys)
        assertEquals("2222", (profilesOf(dive).getValue("p2").single<String>("serial") as Result.Usable).value)
        assertEquals("*p1", (dive.single<KeyReference>("primary_profile") as Result.Usable).value.toString())
    }

    @Test
    fun `it writes none of the dive's own fields`() {
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "EAN32", "start_pressure": 210,
            "end_pressure": 70}}""", """[[0, "*g1"]]"""))
        assertEquals("10:00:00", (dive.single<Time>("start_time") as Result.Usable).value.toString())
        val first = (dive.keyed<OwnedItem>("gas_sources") as Result.Usable).value.getValue("g1")
        assertIs<Result.Absent>((first as Element.Usable).value.single<Double>("start_pressure"),
            "the second computer's pressures do not fill the cylinder's empty fields")
    }

    @Test
    fun `its gases name the dive's cylinders by mix`() {
        // The arriving recording calls its EAN50 g1, where the dive calls it g2.
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "EAN50"}, "g2": {"gas_type": "EAN32"}}""",
            """[[0, "*g2"], [1500, "*g1"]]""", """{"g2": [[0, 200], [1700, 80]]}"""))
        val second = profilesOf(dive).getValue("p2")
        assertTrue("*g1" in switchesOf(second) && switchesOf(second).indexOf("*g1") < switchesOf(second).indexOf("*g2"),
            "it went in on the dive's EAN32, g1, and switched to its EAN50, g2: ${switchesOf(second)}")
        assertEquals(setOf("g1", "g2"), sourcesOf(dive).keys, "no cylinder was added")
        val pressures = ItemWriter.write(second, Units.DEFAULT).members["pressures"]
        assertTrue((pressures as Stored.Members).members.keys == setOf("g1"), "pressures follow: $pressures")
    }

    @Test
    fun `a mix the dive does not hold becomes a cylinder of its own`() {
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "EAN32"}, "g2": {"gas_type": "EAN80"}}""",
            """[[0, "*g1"], [1500, "*g2"]]"""))
        assertEquals("EAN80", sourcesOf(dive)["g3"])
        assertTrue("*g3" in switchesOf(profilesOf(dive).getValue("p2")))
    }

    @Test
    fun `of two cylinders of one mix, the one whose pressures agree is chosen`() {
        val twins = """{"start_date": "2024-06-15", "start_time": "10:00:00",
            "gas_sources": {"g1": {"gas_type": "AIR", "start_pressure": 200, "end_pressure": 100},
                "g2": {"gas_type": "AIR", "start_pressure": 210, "end_pressure": 60}},
            "profiles": {"p1": {"serial": "1111", "depth": [[0, 0], [600, 20], [1800, 0]]}}}"""
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "AIR"}}""", """[[0, "*g1"]]""",
            """{"g1": [[0, 209], [1700, 62]]}"""), twins)
        assertTrue("*g2" in switchesOf(profilesOf(dive).getValue("p2")))
    }

    @Test
    fun `of two that nothing tells apart, a cylinder of its own is added`() {
        val twins = """{"start_date": "2024-06-15", "start_time": "10:00:00",
            "gas_sources": {"g1": {"gas_type": "AIR"}, "g2": {"gas_type": "AIR"}},
            "profiles": {"p1": {"serial": "1111", "depth": [[0, 0], [600, 20], [1800, 0]]}}}"""
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "AIR"}}""", """[[0, "*g1"]]"""), twins)
        assertEquals(setOf("g1", "g2", "g3"), sourcesOf(dive).keys)
    }

    @Test
    fun `the same computer downloaded again is laid over its own profile`() {
        val dive = landed(recording("1111", "aa", """{"g1": {"gas_type": "EAN32"}}""", """[[0, "*g1"]]"""))
        assertEquals(setOf("p1"), profilesOf(dive).keys)
    }

    @Test
    fun `without serials, a fingerprint in common is the same computer`() {
        val unserialled = held.replace(""""serial": "1111", """, "")
        val dive = landed(recording("", "aa", """{"g1": {"gas_type": "EAN32"}}""", """[[0, "*g1"]]""")
            .replace(""""serial": "", """, ""), unserialled)
        assertEquals(setOf("p1"), profilesOf(dive).keys)
    }

    @Test
    fun `a recording with no serial, fingerprint or key in common is a second computer`() {
        val unserialled = held.replace(""""serial": "1111", """, "")
        val dive = landed(recording("", "cc", """{"g1": {"gas_type": "EAN32"}}""", """[[0, "*g1"]]""")
            .replace(""""serial": "", """, "").replace(""""profiles": {"p1"""", """"profiles": {"i330r""""), unserialled)
        assertEquals(setOf("p1", "i330r"), profilesOf(dive).keys, "and keeps the key it came under")
    }

    @Test
    fun `a dive with no recording yet takes the arrival whole`() {
        val logged = """{"start_date": "2024-06-15", "start_time": "10:00:00"}"""
        val dive = landed(recording("2222", "bb", """{"g1": {"gas_type": "EAN32"}}""", """[[0, "*g1"]]"""), logged)
        assertEquals("10:02:00", (dive.single<Time>("start_time") as Result.Usable).value.toString())
    }
}
