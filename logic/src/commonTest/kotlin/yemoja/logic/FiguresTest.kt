package yemoja.logic

import yemoja.data.ItemSet
import yemoja.data.NumberDescription
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/*
 * A figure taken over items somebody else chose. See ../../../../../doc.md — `LOGIC-34`.
 */

/**
 * Three dives: a short one and a long one with a SAC written on each source, and one holding
 * nothing but a site.
 */
private val set: ItemSet = LogbookReader.read(
    MemoryFileStore(
        mapOf(
            "dive/short#0.json" to """{"duration": 1200, "max_depth": 10,
                "environment": {"bottom_temperature": 18},
                "gas_sources": {"g1": {"usage": "bottom", "volume": 12, "sac": 20}}}""",
            "dive/long#0.json" to """{"duration": 3600, "max_depth": 30,
                "environment": {"bottom_temperature": 24},
                "gas_sources": {"g1": {"usage": "bottom", "volume": 24, "sac": 14},
                    "g2": {"usage": "stage", "gas_type": "EAN50", "volume": 7, "sac": 11}}}""",
            "dive/empty#0.json" to """{"dive_site": "@blue"}""",
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
        ),
    ),
    Types.ALL,
)

private fun taken(
    path: String,
    measure: Measure,
    ids: List<String> = listOf("short#0", "long#0"),
    weight: String? = null,
): Figure = assertIs<Figured.Taken>(figureOf(set, ids, path, measure, weight)).figure

private fun refusal(path: String, measure: Measure = Measure.MEAN, weight: String? = null): String =
    assertIs<Figured.Refused>(figureOf(set, listOf("short#0"), path, measure, weight)).reason

class FiguresTest {

    @Test
    fun `every measure over a field on the item`() {
        assertEquals(2.0, taken("max_depth", Measure.COUNT).value)
        assertEquals(40.0, taken("max_depth", Measure.SUM).value)
        assertEquals(10.0, taken("max_depth", Measure.MINIMUM).value)
        assertEquals(30.0, taken("max_depth", Measure.MAXIMUM).value)
        assertEquals(20.0, taken("max_depth", Measure.RANGE).value)
        assertEquals(20.0, taken("max_depth", Measure.MEAN).value)
        assertEquals(20.0, taken("max_depth", Measure.MEDIAN).value)
        val deviation = taken("max_depth", Measure.STANDARD_DEVIATION)
        assertEquals(10.0, deviation.value, "of the population")
    }

    @Test
    fun `a median of an odd count is the middle one`() {
        val figure = taken("gas_sources.*.sac", Measure.MEDIAN)
        assertEquals(14.0, figure.value)
        assertEquals(3, figure.used)
    }

    @Test
    fun `a path goes into a singular owned item and into every entry of a keyed one`() {
        assertEquals(21.0, taken("environment.bottom_temperature", Measure.MEAN).value)
        assertEquals(15.0, taken("gas_sources.*.sac", Measure.MEAN).value)
        assertEquals(17.0, taken("gas_sources.g1.sac", Measure.MEAN).value, "or into one entry")
    }

    @Test
    fun `a weighted mean counts a long dive for more than a short one`() {
        // 20 over 1200 seconds and 14 over 3600: (20 * 1200 + 14 * 3600) / 4800.
        val figure = taken("gas_sources.g1.sac", Measure.WEIGHTED_MEAN, weight = "duration")
        assertEquals(15.5, figure.value)
        assertEquals(Measure.WEIGHTED_MEAN, figure.measure, "and says which mean it is")
    }

    @Test
    fun `a weight is found beside its own entry, or on the item above it`() {
        // (20 * 12 + 14 * 24 + 11 * 7) / 43, each source weighted by its own volume.
        val byVolume =
            taken("gas_sources.*.sac", Measure.WEIGHTED_MEAN, weight = "gas_sources.*.volume")
        assertEquals((20.0 * 12 + 14 * 24 + 11 * 7) / 43, byVolume.value!!, 1e-9)
        val byDuration = taken("gas_sources.*.sac", Measure.WEIGHTED_MEAN, weight = "duration")
        assertEquals((20.0 * 1200 + 14 * 3600 + 11 * 3600) / 8400, byDuration.value!!, 1e-9)
    }

    @Test
    fun `what was not used is named with the reason, and not counted as nothing`() {
        val figure = taken("max_depth", Measure.MEAN, listOf("short#0", "empty#0", "nobody#0"))
        assertEquals(10.0, figure.value)
        assertEquals(1, figure.used)
        assertEquals(
            listOf(
                Skipped("@empty#0 max_depth", "holds nothing"),
                Skipped("@nobody#0", "names nothing"),
            ),
            figure.skipped,
        )
    }

    @Test
    fun `an item of another type is skipped rather than read`() {
        val figure = taken("max_depth", Measure.COUNT, listOf("short#0", "blue"))
        assertEquals(1.0, figure.value)
        assertEquals(
            listOf(Skipped("@blue", "should be a dive, like the first, but was a dive_site")),
            figure.skipped,
        )
    }

    @Test
    fun `a count reaches what is not a number, and nothing at all is a count of none`() {
        assertEquals(3.0, taken("gas_sources.*.usage", Measure.COUNT).value)
        assertEquals(0.0, taken("max_depth", Measure.COUNT, emptyList()).value)
        assertNull(taken("max_depth", Measure.MEAN, listOf("empty#0")).value, "and no mean")
    }

    @Test
    fun `a path that names no number is refused before anything is read`() {
        assertEquals("dive should have a field called depth, but has none", refusal("depth"))
        assertEquals("usage should be a single number, but is not", refusal("gas_sources.*.usage"))
        assertEquals(
            "gas_sources.g1 should go on into a field of gas_sources, but stops there",
            refusal("gas_sources.g1"),
        )
        assertEquals(
            "environment should go on into a field of environment, but stops there",
            refusal("environment"),
        )
        assertEquals(
            "max_depth.value should end at max_depth, which holds no fields, but goes on",
            refusal("max_depth.value"),
        )
        assertEquals(
            "a weighted mean should name the field it is weighted by",
            refusal("max_depth", Measure.WEIGHTED_MEAN),
        )
    }

    @Test
    fun `the field a path ends at, found from the type alone`() {
        val sac = assertIs<NumberDescription>(fieldAt(Types.DIVE, "gas_sources.*.sac"))
        assertEquals("sac", sac.name)
        assertNull(fieldAt(Types.DIVE, "gas_sources"))
        assertNull(fieldAt(Types.DIVE, "nothing.here"))
    }
}

/*
 * A figure over an owned item nobody wrote, where what it holds is worked out. `DATA-124`.
 */
class WorkedFiguresTest {

    private val held: ItemSet = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "dive/written#0.json" to """{"environment": {"bottom_temperature": 18}}""",
                "dive/sampled#0.json" to
                    """{"profiles": {"p1": {"temperature": [[0, 24], [600, 10], [1800, 16]]}}}""",
                "dive/bare#0.json" to """{"dive_number": 3}""",
            ),
        ),
        Types.ALL,
    )

    @Test
    fun `a dive with a recording and no conditions written is counted, and a bare one skipped`() {
        val ids = listOf("written#0", "sampled#0", "bare#0")
        val taken = assertIs<Figured.Taken>(
            figureOf(held, ids, "environment.bottom_temperature", Measure.MEAN, null),
        ).figure
        assertEquals(14.0, taken.value, "eighteen written and ten sampled")
        assertEquals(2, taken.used)
        assertEquals(1, taken.skipped.size, "the bare dive holds nothing to count")
    }
}
