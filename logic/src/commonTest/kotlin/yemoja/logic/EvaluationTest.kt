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
import kotlin.test.assertTrue

/*
 * What the model makes of a profile. See ../../../../../doc.md — the layer's own document is
 * logic/doc.md, `LOGIC-37`.
 *
 * Fresh water and an atmosphere of a round bar throughout, so a depth of ten metres is two bar
 * and the arithmetic can be followed.
 */

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** A dive holding one plan, whose profile is [run] beside the settings every test shares. */
private fun planned(run: String, sources: String = AIR): Item {
    val set = logbook(
        "dive/d#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
            "gas_sources": {$sources},
            "profiles": {"a": {"planned": true, "water_type": "fresh",
                "gradient_factor_low": 1.0, "gradient_factor_high": 1.0, $run}}}""",
    )
    return profile(set["d#0"]!!, "a")
}

private fun profile(dive: Item, key: String): Item {
    val held = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
    return (held.getValue(key) as Element.Usable).value
}

private fun values(series: Series): List<Double> =
    (0..<series.size).map { (series.valueAt(it) as Element.Usable).value as Double }

private fun done(profile: Item): Evaluated.Done = assertIs<Evaluated.Done>(evaluate(profile))

private fun refused(profile: Item): String =
    assertIs<Evaluated.Refused>(evaluate(profile)).reason

/** One cylinder of air, which most of these dives are on. */
private const val AIR = """"g1": {"gas_type": "AIR"}"""

/** Half an hour at twelve metres, which no model asks anybody to stop after. */
private const val SHALLOW = """"depth": [[0, 0], [60, 12], [1800, 12], [1860, 0]]"""

/** Half an hour at forty, which every model does. */
private const val DEEP = """"depth": [[0, 0], [90, 40], [1800, 40], [1900, 0]]"""

class EvaluationTest {

    @Test
    fun `a shallow half hour needs no stop, and says how long it could have stayed`() {
        val evaluated = done(planned(SHALLOW))

        assertEquals(listOf(0.0, 0.0, 0.0, 0.0), values(evaluated.ceiling))
        assertTrue(evaluated.findings.isEmpty(), "${evaluated.findings}")
        // The two samples under water. At the surface there is no limit to report, the ceiling
        // never passing the surface however long anybody stands there.
        assertEquals(2, evaluated.noDecompressionTime.size)
        assertTrue(
            values(evaluated.noDecompressionTime).zipWithNext().all { (before, after) ->
                after < before
            },
            "the time left should shorten as the dive goes on",
        )
    }

    @Test
    fun `a deep half hour owes a stop, and the time left runs out before the dive does`() {
        val evaluated = done(planned(DEEP))

        assertTrue(values(evaluated.ceiling).any { it > 0 }, "a stop should be required")
        assertTrue(
            evaluated.noDecompressionTime.size < evaluated.ceiling.size,
            "once a stop is owed there is no time left to report",
        )
    }

    @Test
    fun `surfacing through the ceiling is said once, however long it lasts`() {
        val evaluated = done(planned(""""depth": [[0, 0], [90, 40], [1800, 40], [1900, 0],
            [2400, 0]]"""))

        assertEquals(1, evaluated.findings.size, "${evaluated.findings}")
        val finding = evaluated.findings.first()
        assertEquals(1900, finding.second)
        assertEquals(Severity.WARNING, finding.severity)
        assertTrue("above the ceiling" in finding.said, finding.said)
    }

    @Test
    fun `a gas with less nitrogen in it loads less`() {
        val air = done(planned(DEEP)).surfacing
        val nitrox = done(
            planned(
                """"gas_switches": [[0, "*g1"]], $DEEP""",
                sources = """"g1": {"gas_type": "EAN32"}""",
            ),
        ).surfacing

        for (number in 1..Tissues.COMPARTMENTS) {
            assertTrue(
                nitrox.nitrogenIn(number) < air.nitrogenIn(number),
                "compartment $number: ${nitrox.nitrogenIn(number)} against air's" +
                    " ${air.nitrogenIn(number)}",
            )
        }
    }

    @Test
    fun `a second dive carries what the first left, and is held deeper for it`() {
        val alone = done(planned(DEEP)).ceiling
        val after = done(repetitive(""""previous_profile": "@first#0*a", """)).ceiling

        assertTrue(
            values(after).last() > values(alone).last(),
            "the repeat should be held deeper: ${values(after)} against ${values(alone)}",
        )
    }

    @Test
    fun `a chain that comes back on itself is refused rather than followed`() {
        val set = chained(""""previous_profile": "@second#0*a", """)
        val reason = refused(profile(set["second#0"]!!, "a"))

        assertTrue("carries gas from itself" in reason, reason)
    }

    @Test
    fun `a run before with no surface interval to cross leaves nothing to say`() {
        val set = logbook(
            "dive/first#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
                "gas_sources": {"g1": {"gas_type": "AIR"}},
                "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
                    "gradient_factor_high": 1.0, $DEEP}}}""",
            "dive/second#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
                "gas_sources": {"g1": {"gas_type": "AIR"}},
                "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
                    "gradient_factor_high": 1.0, "previous_profile": "@first#0*a", $DEEP}}}""",
        )
        val reason = refused(profile(set["second#0"]!!, "a"))

        assertTrue("surface interval" in reason, reason)
    }

    @Test
    fun `what the model cannot be asked, it says it cannot be asked`() {
        assertTrue(
            "how conservative" in refused(
                profile(
                    logbook(
                        "dive/d#0.json" to """{"gas_sources": {"g1": {"gas_type": "AIR"}},
                            "profiles": {"a": {"water_type": "fresh", $DEEP}}}""",
                    )["d#0"]!!,
                    "a",
                ),
            ),
        )
        assertTrue("vpm" in refused(planned(""""deco_model": "vpm", $DEEP""")))
        assertTrue("what water" in refused(bare(""""gradient_factor_low": 1.0,
            "gradient_factor_high": 1.0, $DEEP""")))
        assertTrue("no depths" in refused(planned(""""remarks": "nothing recorded"""")))
    }

    @Test
    fun `what a run costs is the rate, the pressure it is breathed at, and the minutes`() {
        val evaluated = done(
            planned(
                """"depth": [[0, 20], [600, 20]]""",
                sources = """"g1": {"gas_type": "AIR", "sac": 20, "volume": 12,
                    "start_pressure": 200}""",
            ),
        )
        // Ten minutes at twenty metres of fresh water, which is 2.9613 bar under a bar of air.
        val litres = 20 * ambientAt(20.0, 1000.0, 1.0) * 10

        assertEquals(litres, evaluated.gasUsed.getValue("g1"), 1e-9)
        assertEquals(listOf(200.0, 200.0 - litres / 12), values(evaluated.pressures.getValue("g1")))
        assertTrue(evaluated.findings.isEmpty(), "${evaluated.findings}")
    }

    @Test
    fun `a cylinder the run empties says where it ran out`() {
        val evaluated = done(
            planned(
                """"depth": [[0, 30], [600, 30], [1200, 30]]""",
                sources = """"g1": {"gas_type": "AIR", "sac": 25, "volume": 3,
                    "start_pressure": 200}""",
            ),
        )
        val empty = evaluated.findings.single { "empty" in it.said }

        assertEquals(600, empty.second)
        assertEquals(Severity.WARNING, empty.severity)
        assertTrue(values(evaluated.pressures.getValue("g1")).last() < 0, "and it goes on falling")
    }

    @Test
    fun `a source nobody gave a rate costs nothing that can be counted`() {
        val evaluated = done(planned(SHALLOW))

        assertTrue(evaluated.gasUsed.isEmpty(), "${evaluated.gasUsed}")
        assertTrue(evaluated.pressures.isEmpty(), "${evaluated.pressures}")
    }

    @Test
    fun `a mix too rich for the depth it is breathed at is said once`() {
        val evaluated = done(
            planned(
                """"gas_switches": [[0, "*g1"]], "depth": [[0, 0], [90, 40], [600, 40],
                    [700, 0]]""",
                sources = """"g1": {"gas_type": "EAN50"}""",
            ),
        )
        val rich = evaluated.findings.single { "oxygen" in it.said }

        assertEquals(Severity.WARNING, rich.severity)
        assertTrue("2.4" in rich.said || "2.5" in rich.said, rich.said)
    }

    @Test
    fun `two cylinders and nothing saying which was breathed is nothing to work from`() {
        val reason = refused(
            planned(
                DEEP,
                sources = """"g1": {"gas_type": "AIR"}, "g2": {"gas_type": "EAN50"}""",
            ),
        )

        assertTrue("what was breathed" in reason, reason)
    }
}

/** A dive whose profile holds [run] and nothing the tests otherwise supply. */
private fun bare(run: String): Item {
    val set = logbook(
        "dive/d#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
            "gas_sources": {"g1": {"gas_type": "AIR"}}, "profiles": {"a": {$run}}}""",
    )
    return profile(set["d#0"]!!, "a")
}

/** The second of two deep dives an hour apart, whose plan holds [carrying]. */
private fun repetitive(carrying: String): Item =
    profile(chained(carrying)["second#0"]!!, "a")

private fun chained(carrying: String): ItemSet = logbook(
    "dive/first#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
        "gas_sources": {"g1": {"gas_type": "AIR"}},
        "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
            "gradient_factor_high": 1.0, $DEEP}}}""",
    "dive/second#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
        "surface_interval": 3600,
        "gas_sources": {"g1": {"gas_type": "AIR"}},
        "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
            "gradient_factor_high": 1.0, $carrying $DEEP}}}""",
)
