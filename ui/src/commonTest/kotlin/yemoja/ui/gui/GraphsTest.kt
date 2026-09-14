package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * What a recording becomes on a graph. See ../../../../../../gui/doc.md — `GUI-4`.
 */
class GraphsTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "gear.json" to """{"twelve": {"name": "Twelve"}}""",
                "dive/2026-06-21#0.json" to """{
                    "gas_sources": {"g1": {"cylinder": "@twelve"}, "g2": {}},
                    "profiles": {
                        "a": {"depth": [[0, 0], [30, 12.0], [90, 6.0], [120, 0]],
                              "gas_switches": [[0, "*g1"], [60, "*g2"]],
                              "alarms": [[45, "ascent"]],
                              "temperature": [[0, 20.0], [120, 18.0]],
                              "decostop": [[60, 3.0], [90, 0]],
                              "no_deco_time": [[0, 3600], [60, 600]],
                              "cns": [[0, 1], [120, 4]],
                              "pressures": {"g1": [[0, 200.0], [120, 120.0]], "g2": [[0, 100.0]]}},
                        "b": {"temperature": [[0, 20.0]]}
                    }
                }""",
            ),
        ),
        Types.ALL,
    )

    private val dive = set["2026-06-21#0"]!!

    @Suppress("UNCHECKED_CAST")
    private fun profile(key: String): OwnedItem {
        val read = (dive.read("profiles") as Result.Usable<*>).value as Map<String, Element<Any>>
        return (read.getValue(key) as Element.Usable).value as OwnedItem
    }

    @Test
    fun `the depth side is the depth, to be read, and the deco stops stepped across it`() {
        val lines = depthLinesOf(profile("a"))
        assertEquals(listOf("Depth", "Deco stop"), lines.map { it.label })
        assertEquals(listOf(true, false), lines.map { it.main })
        assertTrue(lines[1].stepped)
        assertEquals(listOf(0.0, 0.5, 1.5, 2.0), lines[0].points.map { it.minute })
    }

    @Test
    fun `a recording with no depth has no depth side`() {
        assertEquals(emptyList(), depthLinesOf(profile("b")))
    }

    @Test
    fun `an entry of a type with no depth at all has no depth side either, and no fault`() {
        // Every keyed entry is asked, a gas source among them, and a type without the field
        // refuses to be read for it.
        val read = (dive.read("gas_sources") as Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val sources = read as Map<String, Element<Any>>
        val source = (sources.getValue("g1") as Element.Usable).value as OwnedItem
        assertEquals(emptyList(), depthLinesOf(source))
        assertEquals(emptyList(), overlaysOf(dive, source))
    }

    @Test
    fun `the right axis offers what the recording holds, a cylinder named as its gas source is`() {
        val overlays = overlaysOf(dive, profile("a"))
        assertEquals(
            listOf("Temperature", "Twelve pressure", "G2 pressure", "NDL", "CNS"),
            overlays.map { it.title },
        )
        assertEquals(listOf("°C", "bar", "bar", "min", "%"), overlays.map { it.unit })
    }

    @Test
    fun `no-deco time is in minutes`() {
        val noDeco = overlaysOf(dive, profile("a")).first { it.title == "NDL" }
        assertEquals(listOf(60.0, 10.0), noDeco.lines.single().points.map { it.value })
    }

    @Test
    fun `no-deco time is capped where a computer says no limit, and its surface zeros dropped`() {
        fun series(vararg samples: Pair<Int, Int>) = Series(
            samples.map { it.first }.toIntArray(),
            samples.map { Element.Usable(it.second as Any) },
        )
        // A Perdix: zero before it has calculated, then 99 minutes as its own cap.
        val perdix = noDecoOf(series(10 to 0, 20 to 5940, 3810 to 5940))
        assertEquals(listOf(99.0, 99.0), perdix.map { it.value })
        // An i330R: 598 minutes as its marker for no limit, then a real ninety.
        val i330r = noDecoOf(series(2 to 35880, 112 to 5400))
        assertEquals(listOf(99.0, 90.0), i330r.map { it.value })
    }

    @Test
    fun `the events are the gas switches, named as their sources are, and the alarms, in order`() {
        val marks = eventsOf(dive, profile("a"))
        assertEquals(listOf("Twelve", "ascent", "G2"), marks.map { it.label })
        assertEquals(listOf(0.0, 0.75, 1.0), marks.map { it.minute })
        assertEquals(
            listOf(Marking.SWITCH, Marking.ALARM, Marking.SWITCH),
            marks.map { it.marking },
        )
    }

    @Test
    fun `the depth at a moment lies between the samples either side of it`() {
        val line = depthLinesOf(profile("a")).first()
        assertEquals(12.0, depthAt(line, 0.5))
        assertEquals(9.0, depthAt(line, 1.0), "halfway from twelve at half a minute to six at 1.5")
        assertEquals(0.0, depthAt(line, 5.0), "beyond the end is the end")
        assertEquals(null, depthAt(Line("none", emptyList()), 1.0))
    }

    @Test
    fun `ticks fall on values a reader would choose, about as many as asked for`() {
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0), ticksOf(0.0, 42.0, 5))
        // Never more marks than asked for: the step rounds up, so 31 over 6 is tens, not fives.
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), ticksOf(0.0, 31.0, 6))
        assertEquals(listOf(18.0, 18.5, 19.0, 19.5, 20.0), ticksOf(18.0, 20.0, 4))
        assertEquals(listOf(7.0), ticksOf(7.0, 7.0, 4), "nothing to span is one mark")
    }
}

class AxisRangeTest {

    @Test
    fun `an axis leaves room at each end, so no line lies on the plot's edge`() {
        val range = rangeOf(listOf(6.0, 7.0))
        assertEquals(5.95, range.start, 1e-9)
        assertEquals(7.05, range.endInclusive, 1e-9)
        assertEquals(true, 6.0 > range.start && 7.0 < range.endInclusive)
    }

    @Test
    fun `a reading that never changed is given a range around it`() {
        val range = rangeOf(listOf(6.0, 6.0, 6.0))
        assertEquals(5.0, range.start, 1e-9)
        assertEquals(7.0, range.endInclusive, 1e-9)
    }

    @Test
    fun `no reading at all is nought to one`() {
        assertEquals(0.0, rangeOf(emptyList()).start, 1e-9)
        assertEquals(1.0, rangeOf(emptyList()).endInclusive, 1e-9)
    }
}

class NoDecoStretchTest {

    private fun points(vararg pairs: Pair<Double, Double>): List<Point> =
        pairs.map { (minute, value) -> Point(minute, value) }

    @Test
    fun `a stop between two readings breaks the line, the reading not existing there`() {
        val reading = points(0.0 to 60.0, 10.0 to 0.0, 50.0 to 99.0, 60.0 to 99.0)
        val stops = points(11.0 to 6.0, 40.0 to 6.0)
        val stretches = stretchesOf(reading, stops)
        assertEquals(2, stretches.size)
        assertEquals(listOf(60.0, 0.0), stretches[0].map { it.value })
        assertEquals(listOf(99.0, 99.0), stretches[1].map { it.value })
    }

    @Test
    fun `a dive with no stop is one stretch, and one with no reading is none`() {
        val reading = points(0.0 to 60.0, 10.0 to 20.0)
        assertEquals(listOf(reading), stretchesOf(reading, emptyList()))
        assertEquals(emptyList(), stretchesOf(emptyList(), points(5.0 to 6.0)))
    }

    @Test
    fun `a stop of nought is no stop, and one outside the readings breaks nothing`() {
        val reading = points(0.0 to 60.0, 10.0 to 20.0)
        assertEquals(1, stretchesOf(reading, points(5.0 to 0.0)).size)
        assertEquals(1, stretchesOf(reading, points(20.0 to 6.0)).size)
    }

    @Test
    fun `a recording in deco offers its no-deco time in the stretches it was written`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/2026-06-21#0.json" to """{"profiles": {"a": {
                        "depth": [[0, 0], [600, 40.0], [3600, 0]],
                        "decostop": [[700, 6.0], [3000, 6.0]],
                        "no_deco_time": [[0, 3600], [600, 0], [3100, 5940], [3600, 5940]]
                    }}}""",
                ),
            ),
            Types.ALL,
        )
        val dive = set["2026-06-21#0"]!!
        val read = (dive.read("profiles") as Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val profiles = read as Map<String, Element<Any>>
        val one = (profiles.getValue("a") as Element.Usable).value as OwnedItem
        val noDeco = overlaysOf(dive, one).first { it.title == "NDL" }
        assertEquals(2, noDeco.lines.size, "the stop stands between the two stretches")
        assertEquals(listOf(60.0, 0.0), noDeco.lines[0].points.map { it.value })
        assertEquals(listOf(99.0, 99.0), noDeco.lines[1].points.map { it.value })
    }
}
