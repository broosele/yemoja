package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * How much gas was breathed, brought back to the surface. See ../../../../../doc.md — the
 * layer's own document is logic/doc.md, `LOGIC-33`.
 */

private fun set(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** Ten metres of fresh water under an atmosphere of one bar, which is what a stretch here is at. */
private const val AMBIENT = 1.0 + 1000 * 9.80665 * 10 / 100_000

/**
 * A dive at a steady ten metres in fresh water, whose sources are [sources] and whose recording
 * holds [recording] beside its depth.
 */
private fun dived(sources: String, recording: String): Item = set(
    "dive/d#0.json" to """{
        "gas_sources": {$sources},
        "profiles": {"p1": {"water_type": "fresh", "atmospheric_pressure": 1.0,
            "depth": [[0, 10], [1800, 10]], $recording}}}""",
)["d#0"]!!

private fun profile(dive: Item): Item =
    ((dive.keyed<OwnedItem>("profiles") as Result.Usable).value.values.first() as Element.Usable)
        .value

private fun source(dive: Item, key: String): Item =
    ((dive.keyed<OwnedItem>("gas_sources") as Result.Usable).value.getValue(key) as Element.Usable)
        .value

private fun sac(item: Item): Double? = (item.single<Double>("sac") as? Result.Usable)?.value

private fun points(dive: Item): List<Pair<Int, Double>> {
    val series = assertIs<Series>((profile(dive).read("sac") as Result.Usable).value)
    return (0..<series.size).map {
        series.secondAt(it) to (series.valueAt(it) as Element.Usable).value as Double
    }
}

private fun near(expected: Double, actual: Double?, what: String = "") {
    assertEquals(expected, actual ?: Double.NaN, 1e-9, what)
}

class ConsumptionTest {

    @Test
    fun `a stretch is the gas used at the surface, a minute at a time`() {
        // Twenty bar from a twelve-litre cylinder over ten minutes, at ten metres.
        val dive = dived(
            """"g1": {"volume": 12}""",
            """"pressures": {"g1": [[0, 200], [600, 180]]}""",
        )
        near(20.0 * 12 / 10 / AMBIENT, points(dive).single().second)
        assertEquals(0, points(dive).single().first, "a point where the stretch begins")
        near(20.0 * 12 / 10 / AMBIENT, sac(source(dive, "g1")))
        val read = source(dive, "g1").read("sac") as Result.Usable
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a source's figure weighs a long stretch more than a short one`() {
        // Ten minutes using 240 litres, then two minutes using 96: the whole is 336 over twelve,
        // not the mean of 24 and 48.
        val dive = dived(
            """"g1": {"volume": 12}""",
            """"pressures": {"g1": [[0, 200], [600, 180], [720, 172]]}""",
        )
        near(336.0 / 12 / AMBIENT, sac(source(dive, "g1")))
        assertEquals(2, points(dive).size)
    }

    @Test
    fun `each source counts only while breathed, and a stretch holding a switch not at all`() {
        val dive = dived(
            """"back": {"volume": 24}, "stage": {"volume": 7}""",
            """"gas_switches": [[0, "*back"], [900, "*stage"]],
            "pressures": {"back": [[0, 200], [600, 190], [1200, 150]],
                          "stage": [[600, 200], [900, 200], [1500, 190]]}""",
        )
        near(10.0 * 24 / 10 / AMBIENT, sac(source(dive, "back")), "the back gas up to 600 alone")
        near(10.0 * 7 / 10 / AMBIENT, sac(source(dive, "stage")), "the stage from 900 alone")
        assertEquals(listOf(0, 900), points(dive).map { it.first }, "nothing across the switch")
    }

    @Test
    fun `a dive with no switches breathes its only source`() {
        val dive = dived(
            """"g1": {"volume": 10}, "g2": {"volume": 10}""",
            """"pressures": {"g1": [[0, 200], [600, 190]]}""",
        )
        assertEquals(Result.Absent, source(dive, "g1").read("sac"), "two sources, and nothing says")
    }

    @Test
    fun `without a volume or without pressures there is nothing, until somebody writes one`() {
        val novolume = dived(""""g1": {}""", """"pressures": {"g1": [[0, 200], [600, 180]]}""")
        assertEquals(Result.Absent, source(novolume, "g1").read("sac"))
        assertEquals(Result.Absent, profile(novolume).read("sac"))
        val written = dived(""""g1": {"volume": 12, "sac": 14.5}""", """"temperature": [[0, 20]]""")
        val read = source(written, "g1").read("sac") as Result.Usable
        assertEquals(14.5, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}
