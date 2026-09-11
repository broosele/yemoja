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
            listOf("Temperature", "Twelve pressure", "g2 pressure", "No-deco time", "CNS"),
            overlays.map { it.title },
        )
        assertEquals(listOf("°C", "bar", "bar", "min", "%"), overlays.map { it.unit })
    }

    @Test
    fun `no-deco time is in minutes`() {
        val noDeco = overlaysOf(dive, profile("a")).first { it.title == "No-deco time" }
        assertEquals(listOf(60.0, 10.0), noDeco.line.points.map { it.value })
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
    fun `ticks fall on values a reader would choose, about as many as asked for`() {
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0, 40.0), ticksOf(0.0, 42.0, 5))
        // Never more marks than asked for: the step rounds up, so 31 over 6 is tens, not fives.
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), ticksOf(0.0, 31.0, 6))
        assertEquals(listOf(18.0, 18.5, 19.0, 19.5, 20.0), ticksOf(18.0, 20.0, 4))
        assertEquals(listOf(7.0), ticksOf(7.0, 7.0, 4), "nothing to span is one mark")
    }
}
