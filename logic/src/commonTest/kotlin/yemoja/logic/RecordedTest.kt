package yemoja.logic

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * The fields a dive takes from the recording it was made on.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private fun set(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun value(item: Item, field: String): Any? =
    (item.read(field) as? Result.Usable)?.value

/** A dive with one profile, begun at the given moment on its clock and corrected by [offset]. */
private fun dived(
    start: String = """"start_date": "2026-06-21", "start_time": "10:00:00"""",
    offset: Int = 0,
    series: String = """"depth": [[0, 0], [60, 12.4], [1800, 18.9], [3600, 0]]""",
    dive: String = "",
): Item = set(
    "dive/d#0.json" to
        """{"profiles": {"p1": {$start, "recorded_time_offset": $offset, $series}}$dive}""",
)["d#0"]!!

class ProfileTimesTest {

    private fun profile(dive: Item): Item {
        val held = assertIs<Result.Usable<*>>(dive.read("profiles")).value
        @Suppress("UNCHECKED_CAST")
        val entries = held as Map<String, Element<Any>>
        return (entries["p1"] as Element.Usable).value as Item
    }

    @Test
    fun `a recording ends at its last sample`() {
        val recording = profile(dived())
        assertEquals(Date(2026, 6, 21), value(recording, "end_date"))
        assertEquals(Time(11, 0, 0), value(recording, "end_time"))
        assertEquals(3600.0, value(recording, "duration"))
    }

    @Test
    fun `the last sample of any series counts, not the depth alone`() {
        val recording = profile(
            dived(series = """"depth": [[0, 0], [600, 0]], "temperature": [[0, 21], [900, 20]]"""),
        )
        assertEquals(900.0, value(recording, "duration"))
    }

    @Test
    fun `a recording with no samples has no end`() {
        val recording = profile(dived(series = """"depth": []"""))
        assertEquals(Result.Absent, recording.read("end_date"))
        assertEquals(Result.Absent, recording.read("duration"))
    }
}

class DiveTimesTest {

    @Test
    fun `a dive takes its times from the recording, less what its clock was out by`() {
        // A clock two hours ahead of local time: the offset comes off rather than going on.
        val dive = dived(offset = 7200)
        assertEquals(Date(2026, 6, 21), value(dive, "start_date"))
        assertEquals(Time(8, 0, 0), value(dive, "start_time"))
        assertEquals(Time(9, 0, 0), value(dive, "end_time"))
        assertEquals(3600.0, value(dive, "duration"))
    }

    @Test
    fun `the correction moves the date where it has to`() {
        // Two minutes past midnight, with two hours coming off, is late the previous evening.
        val dive = dived(
            start = """"start_date": "2026-06-21", "start_time": "00:02:00"""",
            offset = 7200,
        )
        assertEquals(Date(2026, 6, 20), value(dive, "start_date"))
        assertEquals(Time(22, 2, 0), value(dive, "start_time"))
    }

    @Test
    fun `a dive that ran past midnight ends on the following day`() {
        val dive = dived(
            start = """"start_date": "2025-09-06", "start_time": "23:20:00"""",
            series = """"depth": [[0, 0], [2700, 0]]""",
        )
        assertEquals(Date(2025, 9, 6), value(dive, "start_date"))
        assertEquals(Date(2025, 9, 7), value(dive, "end_date"))
        assertEquals(Time(0, 5, 0), value(dive, "end_time"))
    }

    @Test
    fun `a dive with no recording has no times`() {
        val dive = set("dive/d#0.json" to """{"dive_number": 1}""")["d#0"]!!
        assertEquals(Result.Absent, dive.read("start_date"))
        assertEquals(Result.Absent, dive.read("duration"))
    }

    @Test
    fun `a hand-logged dive ends on the day it started`() {
        val dive = logged("2024-06-15", "10:05:00", "10:41:00")
        assertEquals(Date(2024, 6, 15), value(dive, "end_date"))
        assertEquals(2160.0, value(dive, "duration"))
    }

    @Test
    fun `a hand-logged dive ending before it started ran past midnight`() {
        val dive = logged("2025-09-06", "23:20:00", "00:05:00")
        assertEquals(Date(2025, 9, 7), value(dive, "end_date"))
        assertEquals(2700.0, value(dive, "duration"))
    }

    @Test
    fun `a hand-logged dive with no start time is not placed either side of midnight`() {
        // Nothing for the end time to be earlier than, so whether the day turned is unknown.
        val dive = set(
            "dive/d#0.json" to """{"start_date": "2024-06-15", "end_time": "10:41:00"}""",
        )["d#0"]!!
        assertEquals(Result.Absent, dive.read("end_date"))
        assertEquals(Result.Absent, dive.read("duration"))
    }

    @Test
    fun `a hand-logged dive with no end time has no end`() {
        val dive = set(
            "dive/d#0.json" to """{"start_date": "2024-06-15", "start_time": "10:05:00"}""",
        )["d#0"]!!
        assertEquals(Result.Absent, dive.read("end_date"))
        assertEquals(Result.Absent, dive.read("duration"))
    }

    @Test
    fun `a recording still wins over the dive's own times`() {
        val dive = dived(dive = ""","end_time": "23:59:00"""")
        assertEquals(Date(2026, 6, 21), value(dive, "end_date"))
        assertEquals(3600.0, value(dive, "duration"))
    }

    @Test
    fun `a dive that cannot choose a recording says so rather than falling back`() {
        val dive = set(
            "dive/d#0.json" to """{
                "start_date": "2024-06-15", "start_time": "10:05:00", "end_time": "10:41:00",
                "profiles": {"p1": {"depth": [[0, 0]]}, "p2": {"depth": [[0, 0]]}}
            }""",
        )["d#0"]!!
        assertIs<Result.Unusable>(dive.read("end_date"))
        assertIs<Result.Unusable>(dive.read("duration"))
    }

    /** A dive as a diver writes one: dates and times, and no recording at all. */
    private fun logged(date: String, start: String, end: String): Item = set(
        "dive/d#0.json" to
            """{"start_date": "$date", "start_time": "$start", "end_time": "$end"}""",
    )["d#0"]!!

    @Test
    fun `a written time wins over the recording`() {
        val dive = dived(dive = ""","start_time": "07:30:00"""")
        val read = assertIs<Result.Usable<*>>(dive.read("start_time"))
        assertEquals(Time(7, 30, 0), read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}

class PrimaryProfileTest {

    private val two = """"p1": {"start_date": "2026-06-21", "start_time": "10:00:00",
        "depth": [[0, 0], [1800, 30.0]]},
        "p2": {"start_date": "2026-06-21", "start_time": "10:01:00",
        "depth": [[0, 0], [1500, 28.0]]}"""

    @Test
    fun `one profile needs no naming, there being nothing to choose between`() {
        assertEquals(18.9, value(dived(), "max_depth"))
    }

    @Test
    fun `several and none named is reported rather than guessed at`() {
        val dive = set("dive/d#0.json" to """{"profiles": {$two}}""")["d#0"]!!
        val read = assertIs<Result.Unusable>(dive.read("max_depth"))
        assertTrue("which is primary" in read.reason, read.reason)
        // Every field taken from a recording says the same thing, not just this one.
        assertIs<Result.Unusable>(dive.read("start_date"))
        assertIs<Result.Unusable>(dive.read("duration"))
    }

    @Test
    fun `the named one is the one used`() {
        val dive = set(
            "dive/d#0.json" to """{"profiles": {$two}, "primary_profile": "*p2"}""",
        )["d#0"]!!
        assertEquals(28.0, value(dive, "max_depth"))
    }

    @Test
    fun `naming a profile that is not there is a fault`() {
        val dive = set(
            "dive/d#0.json" to """{"profiles": {$two}, "primary_profile": "*p9"}""",
        )["d#0"]!!
        val read = assertIs<Result.Unusable>(dive.read("max_depth"))
        assertTrue("p9" in read.reason, read.reason)
    }
}

class AverageDepthTest {

    @Test
    fun `it is weighted by time, not by sample`() {
        // Ten minutes at 30, then one minute at 10 taken in four crowded samples. Counting
        // samples would call this about 17 metres; the dive was nearer 29.
        val dive = dived(
            series = """"depth": [[0, 30.0], [600, 30.0], [615, 10.0], [630, 10.0],
                [645, 10.0], [660, 10.0]]""",
        )
        val read = assertIs<Result.Usable<*>>(dive.read("average_depth"))
        val depth = read.value as Double
        assertTrue(depth > 28.0 && depth < 30.0, "time-weighted, and was $depth")
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a square profile averages its own depth`() {
        val dive = dived(series = """"depth": [[0, 20.0], [1800, 20.0]]""")
        assertEquals(20.0, (dive.read("average_depth") as Result.Usable).value)
    }

    @Test
    fun `a straight descent averages half its depth`() {
        // Read as piecewise linear, so a line from nothing to forty spends its time at twenty.
        val dive = dived(series = """"depth": [[0, 0.0], [600, 40.0]]""")
        assertEquals(20.0, (dive.read("average_depth") as Result.Usable).value)
    }

    @Test
    fun `one sample is no interval, so there is nothing to average`() {
        assertEquals(Result.Absent, dived(series = """"depth": [[0, 12.0]]""")
            .read("average_depth"))
    }

    @Test
    fun `a written average wins over the recording`() {
        val dive = dived(dive = ""","average_depth": 14.0""")
        val read = assertIs<Result.Usable<*>>(dive.read("average_depth"))
        assertEquals(14.0, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}

class DecoTest {

    private fun deco(series: String): Result<Any> =
        dived(series = """"depth": [[0, 0], [600, 0]], $series""").read("deco")

    @Test
    fun `a stop above zero anywhere means yes`() {
        assertEquals(true, (deco(""""decostop": [[0, 0], [300, 3.0], [600, 0]]""") as
            Result.Usable).value)
    }

    @Test
    fun `a recorded stop that never rose above zero means no`() {
        assertEquals(false, (deco(""""decostop": [[0, 0], [600, 0]]""") as Result.Usable).value)
    }

    @Test
    fun `no stops written, and a no-deco time that never ran out, means no`() {
        assertEquals(false, (deco(""""no_deco_time": [[0, 3600], [600, 1200]]""") as
            Result.Usable).value)
    }

    @Test
    fun `a no-deco time that reached zero means yes`() {
        assertEquals(true, (deco(""""no_deco_time": [[0, 3600], [600, 0]]""") as
            Result.Usable).value)
    }

    @Test
    fun `a zero before the first positive value is the computer not having calculated yet`() {
        // A Perdix reads zero at the surface at ten seconds and ninety-nine minutes at twenty,
        // and a dive cannot begin in deco.
        assertEquals(false, (deco(""""no_deco_time": [[10, 0], [20, 5940], [600, 5940]]""") as
            Result.Usable).value)
        // Reaching zero later is still deco, whatever the first sample said.
        assertEquals(true, (deco(""""no_deco_time": [[10, 0], [20, 5940], [600, 0]]""") as
            Result.Usable).value)
    }

    @Test
    fun `neither recorded is left for the user, not answered`() {
        // The computer decided this at the time, with settings nothing here can reproduce.
        assertEquals(Result.Absent, deco(""""temperature": [[0, 21]]"""))
    }
}

class SurfaceIntervalTest {

    private val two = set(
        "dive/a#0.json" to """{"profiles": {"p1": {"start_date": "2026-06-21",
            "start_time": "09:00:00", "depth": [[0, 0], [3600, 0]]}}}""",
        "dive/b#0.json" to """{"previous_dive": "@a#0", "profiles": {"p1": {
            "start_date": "2026-06-21", "start_time": "12:00:00",
            "depth": [[0, 0], [1800, 0]]}}}""",
    )

    @Test
    fun `the interval runs from one dive's end to the next dive's start`() {
        // Out at 10:00, back in at 12:00.
        assertEquals(7200.0, value(two["b#0"]!!, "surface_interval"))
    }

    @Test
    fun `two dives in different zones are put on one clock by their own offsets`() {
        // Out at 10:00 in a zone two hours ahead, in at 11:00 local in one an hour ahead: that is
        // 08:00 and 10:00 on one clock, two hours apart rather than one.
        val apart = set(
            "dive/a#0.json" to """{"time_zone_offset": 7200, "profiles": {"p1": {
                "start_date": "2026-06-21", "start_time": "09:00:00",
                "depth": [[0, 0], [3600, 0]]}}}""",
            "dive/b#0.json" to """{"previous_dive": "@a#0", "time_zone_offset": 3600,
                "profiles": {"p1": {"start_date": "2026-06-21", "start_time": "11:00:00",
                "depth": [[0, 0], [1800, 0]]}}}""",
        )
        assertEquals(7200.0, value(apart["b#0"]!!, "surface_interval"))
    }

    @Test
    fun `a dive saying nothing about its zone is on GMT, and its times stay local`() {
        val dive = set(
            "dive/a#0.json" to """{"time_zone_offset": 7200, "profiles": {"p1": {
                "start_date": "2026-06-21", "start_time": "09:00:00",
                "depth": [[0, 0], [3600, 0]]}}}""",
            "dive/b#0.json" to """{"previous_dive": "@a#0", "profiles": {"p1": {
                "start_date": "2026-06-21", "start_time": "12:00:00",
                "depth": [[0, 0], [1800, 0]]}}}""",
        )
        assertEquals(Time(9, 0, 0), value(dive["a#0"]!!, "start_time"), "the zone moves no time")
        assertEquals(14400.0, value(dive["b#0"]!!, "surface_interval"), "08:00 to 12:00")
    }

    @Test
    fun `a dive starting clean has none, which is most of them`() {
        assertEquals(Result.Absent, two["a#0"]!!.read("surface_interval"))
    }

    @Test
    fun `a previous dive that is not in the logbook is a fault`() {
        val dive = set("dive/b#0.json" to """{"previous_dive": "@gone"}""")["b#0"]!!
        val read = assertIs<Result.Unusable>(dive.read("surface_interval"))
        assertTrue("gone" in read.reason, read.reason)
    }

    @Test
    fun `a previous dive that ended later is a fault, not a negative interval`() {
        val wrong = set(
            "dive/a#0.json" to """{"profiles": {"p1": {"start_date": "2026-06-21",
                "start_time": "15:00:00", "depth": [[0, 0], [3600, 0]]}}}""",
            "dive/b#0.json" to """{"previous_dive": "@a#0", "profiles": {"p1": {
                "start_date": "2026-06-21", "start_time": "09:00:00",
                "depth": [[0, 0], [1800, 0]]}}}""",
        )
        assertIs<Result.Unusable>(wrong["b#0"]!!.read("surface_interval"))
    }
}

class TripDivesTest {

    private val trip = set(
        "dive_trip.json" to """{
            "week": {"name": "Week"},
            "leg_one": {"name": "Leg one", "parent": "@week"},
            "leg_two": {"name": "Leg two", "parent": "@leg_one"},
            "elsewhere": {"name": "Elsewhere"}
        }""",
        "dive/a#0.json" to """{"details": {"dive_trip": "@leg_one"},
            "profiles": {"p1": {"start_date": "2026-06-21", "depth": [[0, 0], [60, 5]]}}}""",
        "dive/b#0.json" to """{"details": {"dive_trip": "@leg_two"},
            "profiles": {"p1": {"start_date": "2026-06-25", "depth": [[0, 0], [60, 5]]}}}""",
        "dive/c#0.json" to """{"details": {"dive_trip": "@elsewhere"},
            "profiles": {"p1": {"start_date": "2026-07-01", "depth": [[0, 0], [60, 5]]}}}""",
    )

    private fun named(item: Item, field: String): List<String> {
        val read = assertIs<Result.Usable<*>>(item.read(field))
        return (read.value as List<*>).map { (it as Element.Usable<*>).value }
            .map { (it as Reference.Identified).id }
    }

    @Test
    fun `a trip gathers the dives of everything beneath it, however deep`() {
        assertEquals(listOf("a#0", "b#0"), named(trip["week"]!!, "dives"))
        assertEquals(listOf("a#0", "b#0"), named(trip["leg_one"]!!, "dives"))
        assertEquals(listOf("b#0"), named(trip["leg_two"]!!, "dives"))
    }

    @Test
    fun `a trip's dates are the span of those dives`() {
        assertEquals(Date(2026, 6, 21), value(trip["week"]!!, "start_date"))
        assertEquals(Date(2026, 6, 25), value(trip["week"]!!, "end_date"))
    }

    @Test
    fun `a trip with no diving on it yet has no dates`() {
        val empty = set("dive_trip.json" to """{"planned": {"name": "Planned"}}""")
        assertEquals(Result.Absent, empty["planned"]!!.read("start_date"))
    }

    @Test
    fun `a trip inside itself is reported rather than walked forever`() {
        val looped = set(
            "dive_trip.json" to """{
                "a": {"name": "A", "parent": "@b"},
                "b": {"name": "B", "parent": "@a"}
            }""",
        )
        val read = assertIs<Result.Unusable>(looped["a"]!!.read("dives"))
        assertTrue("inside itself" in read.reason, read.reason)
    }
}
