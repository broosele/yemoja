package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * What a dive's recordings become on a graph. See ../../../../../../gui/doc.md — `GUI-4`.
 */
class GraphsTest {

    private fun dive(json: String) =
        LogbookReader.read(MemoryFileStore(mapOf("dive/2026-06-21#0.json" to json)), Types.ALL)
            .let { it["2026-06-21#0"]!! }

    private val two = dive(
        """{"primary_profile": "*b", "profiles": {
            "a": {"depth": [[0, 0], [60, 10.0], [120, 0]]},
            "b": {"depth": [[0, 0], [30, 12.0], [90, 6.0], [120, 0]],
                  "temperature": [[0, 20.0], [120, 18.0]],
                  "decostop": [[60, 3.0], [90, 0]],
                  "no_deco_time": [[0, 3600], [60, 600]],
                  "cns": [[0, 1], [120, 4]],
                  "pressures": {"g1": [[0, 200.0], [120, 120.0]], "g2": [[0, 100.0]]}}
        }}""",
    )

    @Test
    fun `depth comes first, the primary recording to be read and the other laid over it`() {
        val depth = graphsOf(two).first()
        assertEquals("Depth", depth.title)
        assertTrue(depth.down)
        assertEquals(listOf("Depth", "Deco stop", "a"), depth.lines.map { it.label })
        assertEquals(listOf(true, false, false), depth.lines.map { it.main })
        assertEquals(12.0, depth.lines[0].points.maxOf { it.value }, "the primary is b")
        assertTrue(depth.lines[1].stepped, "a deco stop steps")
    }

    @Test
    fun `seconds become minutes, and no-deco time is in minutes too`() {
        val graphs = graphsOf(two)
        assertEquals(listOf(0.0, 0.5, 1.5, 2.0), graphs[0].lines[0].points.map { it.minute })
        val noDeco = graphs.first { it.title == "No-deco time" }
        assertEquals(listOf(60.0, 10.0), noDeco.lines.single().points.map { it.value })
        assertEquals("min", noDeco.unit)
    }

    @Test
    fun `every other series the primary holds is a small graph of its own`() {
        assertEquals(
            listOf("Depth", "Temperature", "Pressure", "No-deco time", "CNS"),
            graphsOf(two).map { it.title },
        )
        val pressure = graphsOf(two).first { it.title == "Pressure" }
        assertEquals(listOf("g1", "g2"), pressure.lines.map { it.label })
    }

    @Test
    fun `a dive with no recording, or none with a depth, has no graph`() {
        assertEquals(emptyList(), graphsOf(dive("""{"dive_number": 1}""")))
        val noDepth = dive("""{"profiles": {"a": {"temperature": [[0, 20.0]]}}}""")
        assertEquals(emptyList(), graphsOf(noDepth))
    }

    @Test
    fun `naming no primary, the first recording is read`() {
        val one = dive(
            """{"profiles": {"x": {"depth": [[0, 0], [60, 5.0]]}, "y": {"depth": [[0, 0]]}}}""",
        )
        assertEquals(5.0, graphsOf(one).first().lines[0].points.maxOf { it.value })
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
