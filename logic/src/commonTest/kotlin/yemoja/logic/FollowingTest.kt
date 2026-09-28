package yemoja.logic

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.KeyReference
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * A planned dive following an earlier run. See ../../../../../doc.md — `LOGIC-37`.
 */

/** A dive holding one plan under [key], thirty metres for twenty-five minutes, begun at [start]. */
private fun planned(key: String, start: String, extra: String = ""): String = """{$extra
    "gas_sources": {"g1": {"gas_type": "AIR"}},
    "profiles": {"$key": {"planned": true, "start_date": "2026-10-03", "start_time": "$start",
        "water_type": "salt", "gradient_factor_low": 0.3, "gradient_factor_high": 0.7,
        "depth": [[0, 0], [100, 30], [1500, 30], [1800, 0]]}}}"""

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun run(set: ItemSet, dive: String, key: String): Item {
    val runs = (set[dive]!!.keyed<OwnedItem>("profiles") as Result.Usable).value
    return (runs.getValue(key) as Element.Usable).value
}

private val AT_NOON = Moment(Date(2026, 10, 3), Time(12, 0, 0))

class FollowingTest {

    @Test
    fun `a plan typed after an earlier run starts as the same plan saved after it`() {
        // The morning's plan ends at 09:30, and the afternoon's saved plan begins at 11:00 and
        // names it: a surface interval of an hour and a half, as the logbook works it out.
        val set = logbook(
            "dive/m#0.json" to planned("a", "09:00:00"),
            "dive/n#0.json" to planned("b", "11:00:00", """"previous_dive": "@m#0","""),
        )
        val saved = assertIs<Evaluated.Done>(evaluate(run(set, "n#0", "b")))

        val left = assertIs<Residual.Done>(residualAfter(run(set, "m#0", "a"), 90 * 60.0))
        val typed = assertIs<Evaluated.Done>(
            evaluate(
                Run(
                    depth = listOf(0 to 0.0, 100 to 30.0, 1500 to 30.0, 1800 to 0.0),
                    sources = mapOf("g1" to Source(Gas.AIR)),
                    gradientFactorLow = 0.3,
                    gradientFactorHigh = 0.7,
                    density = densityOfWater("salt")!!,
                    carried = left.tissues,
                    oxygenCarried = left.oxygen,
                ),
            ),
        )

        for (number in 1..Tissues.COMPARTMENTS) {
            assertEquals(saved.surfacing.nitrogenIn(number), typed.surfacing.nitrogenIn(number), 1e-9, "compartment $number")
        }
        assertEquals(saved.oxygen.percentCns, typed.oxygen.percentCns, 1e-9)
    }

    @Test
    fun `a run followed leaves more behind the sooner the next begins`() {
        val set = logbook("dive/m#0.json" to planned("a", "09:00:00"))
        val soon = assertIs<Residual.Done>(residualAfter(run(set, "m#0", "a"), 30 * 60.0))
        val late = assertIs<Residual.Done>(residualAfter(run(set, "m#0", "a"), 6 * 60 * 60.0))

        assertTrue(soon.tissues.nitrogenIn(1) > late.tissues.nitrogenIn(1))
        assertTrue(soon.oxygen.percentCns > late.oxygen.percentCns)
    }

    @Test
    fun `a run the model cannot answer for is refused when followed`() {
        val set = logbook(
            "dive/m#0.json" to """{"profiles": {"a": {"planned": true, "start_date": "2026-10-03",
                "start_time": "09:00:00", "depth": [[0, 0], [100, 30], [1800, 0]]}}}""",
        )

        assertIs<Residual.Refused>(residualAfter(run(set, "m#0", "a"), 3600.0))
    }

    @Test
    fun `earlier runs are those on dives that ended before the start, latest first`() {
        val set = logbook(
            "dive/m#0.json" to planned("a", "09:00:00"),
            "dive/n#0.json" to planned("b", "10:30:00"),
            "dive/late#0.json" to planned("c", "11:45:00"),
            "dive/undated#0.json" to """{"profiles": {"d": {"planned": true, "depth": [[0, 0], [60, 10], [600, 0]]}}}""",
        )
        val found = earlierRuns(set, AT_NOON, 24 * 60 * 60)

        assertEquals(listOf("n#0", "m#0"), found.map { it.dive }, "one still in the water at noon, one never dated")
        assertEquals(Moment(Date(2026, 10, 3), Time(11, 0, 0)), found.first().ended)
        assertTrue(found.all { it.planned && it.primary })
    }

    @Test
    fun `a dive ended longer ago than asked is not an earlier run`() {
        val set = logbook("dive/m#0.json" to planned("a", "09:00:00"))

        assertTrue(earlierRuns(set, AT_NOON, 60 * 60).isEmpty(), "it ended two and a half hours before")
        assertEquals(1, earlierRuns(set, AT_NOON, 3 * 60 * 60).size)
    }

    @Test
    fun `a dive's runs are all offered, its primary first`() {
        val set = logbook(
            "dive/m#0.json" to """{"primary_profile": "*rec",
                "gas_sources": {"g1": {"gas_type": "AIR"}},
                "profiles": {
                    "plan": {"planned": true, "start_date": "2026-10-03", "start_time": "09:00:00",
                        "depth": [[0, 0], [100, 30], [1800, 0]]},
                    "rec": {"start_date": "2026-10-03", "start_time": "09:00:00",
                        "depth": [[0, 0], [100, 30], [1800, 0]]}}}""",
        )
        val found = earlierRuns(set, AT_NOON, 24 * 60 * 60)

        assertEquals(listOf("rec", "plan"), found.map { it.key })
        assertTrue(found.first().primary && !found.first().planned)
    }

    @Test
    fun `a reference to a run finds it, and a reference to nothing finds nothing`() {
        val set = logbook("dive/m#0.json" to planned("a", "09:00:00"))

        assertNotNull(runOf(set, KeyReference("a", "m#0")))
        assertEquals(null, runOf(set, KeyReference("z", "m#0")))
        assertEquals(null, runOf(set, KeyReference("a", "gone#0")))
    }
}
