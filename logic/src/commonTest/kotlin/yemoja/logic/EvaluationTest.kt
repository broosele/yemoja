package yemoja.logic

import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        "dive/d#0.json" to """{
            "gas_sources": {$sources},
            "profiles": {"a": {"planned": true, "water_type": "fresh", "atmospheric_pressure": 1.0,
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
    fun `a dive owing no stop at the high factor has no ceiling, however low the low one is`() {
        // Half an hour at eighteen metres at 30/75, the fixture's afternoon plan. The ceiling at
        // 0.3 passes the surface long before any stop is owed at 0.75, and taking that as the
        // first stop drew a ceiling beside an hour of time left: the two saying opposite things.
        val evaluated = done(
            logbook(
                "dive/d#0.json" to """{
                    "gas_sources": {"g1": {"gas_type": "AIR"}},
                    "profiles": {"a": {"planned": true, "water_type": "fresh", "atmospheric_pressure": 1.0,
                        "gradient_factor_low": 0.3, "gradient_factor_high": 0.75,
                        "depth": [[0, 0], [90, 18], [1800, 18], [1920, 0]]}}}""",
            )["d#0"]!!.let { profile(it, "a") },
        )

        assertTrue(values(evaluated.ceiling).all { it == 0.0 }, "${values(evaluated.ceiling)}")
        assertTrue(
            values(evaluated.noDecompressionTime).all { it > 0 },
            "time left throughout: ${values(evaluated.noDecompressionTime)}",
        )
        assertTrue(evaluated.findings.none { "ceiling" in it.said }, "${evaluated.findings}")
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
        assertTrue("the ceiling" in finding.said, finding.said)
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

        assertTrue("set to follow itself" in reason, reason)
    }

    @Test
    fun `a run before with no surface interval to cross leaves nothing to say`() {
        val set = logbook(
            "dive/first#0.json" to """{
                "gas_sources": {"g1": {"gas_type": "AIR"}},
                "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
                    "gradient_factor_high": 1.0, $DEEP}}}""",
            "dive/second#0.json" to """{
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
            "how conservative it was" in refused(
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
        assertTrue("salt or fresh" in refused(bare(""""gradient_factor_low": 1.0,
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
        val rich = evaluated.findings.single { "at most" in it.said }

        assertEquals(Severity.WARNING, rich.severity)
        assertTrue("2.4" in rich.said || "2.5" in rich.said, rich.said)
    }

    @Test
    fun `a dive says how long before flying, and how long before it is out of you`() {
        val deep = done(planned(DEEP))
        val shallow = done(planned(SHALLOW))

        assertTrue(deep.noFlight!! > 0, "forty metres for half an hour is not a wait of nothing")
        assertTrue(deep.noFlight!! < deep.desaturation!!, "flying comes first, and settling later")
        assertTrue(
            shallow.noFlight!! < deep.noFlight!!,
            "twelve metres asks less of a wait than forty: ${shallow.noFlight} against" +
                " ${deep.noFlight}",
        )
    }

    @Test
    fun `the oxygen clocks run while the dive does, and faster on a richer mix`() {
        val air = done(planned(SHALLOW))
        val nitrox = done(
            planned(
                """"gas_switches": [[0, "*g1"]], $SHALLOW""",
                sources = """"g1": {"gas_type": "EAN50"}""",
            ),
        )

        assertEquals(values(air.cns).size, air.ceiling.size, "one reading a sample")
        assertTrue(nitrox.oxygen.percentCns > air.oxygen.percentCns, "${nitrox.oxygen}")
        assertTrue(nitrox.oxygen.otu > air.oxygen.otu, "${nitrox.oxygen}")
        assertTrue(values(air.cns).zipWithNext().all { (before, after) -> after >= before })
    }

    @Test
    fun `a dive shallow enough on air spends nothing of the clock`() {
        val evaluated = done(planned(""""depth": [[0, 3], [1800, 3]]"""))

        assertEquals(0.0, evaluated.oxygen.percentCns)
        assertEquals(0.0, evaluated.oxygen.otu)
    }

    @Test
    fun `a surface interval gives the clock back, and the tissues keep their gas`() {
        val second = done(repetitive(""""previous_profile": "@first#0*a", """))
        val alone = done(planned(DEEP))

        assertTrue(
            second.oxygen.percentCns > values(alone.cns).last(),
            "an hour of surface gives back some of the clock, not all of it: ${second.oxygen}",
        )
        assertTrue(
            second.oxygen.percentCns < 2 * values(alone.cns).last(),
            "and two dives' worth would be more than this: ${second.oxygen}",
        )
    }

    @Test
    fun `an ascent written into a plan is one the model then approves of`() {
        val bottom = """"depth": [[0, 0], [90, 40], [1800, 40]]"""
        val ascent = assertIs<Ascended.Done>(completeAscent(planned(bottom), 9.0, 3.0))
        val written = ascent.depth.joinToString(", ") { (second, metres) -> "[$second, $metres]" }
        val whole = done(planned(""""depth": [[0, 0], [90, 40], [1800, 40], $written]"""))

        assertTrue(ascent.depth.isNotEmpty(), "there is a way up from forty metres")
        assertEquals(0.0, ascent.depth.last().second, "and it ends at the surface")
        assertTrue(whole.findings.none { "ceiling" in it.said }, "${whole.findings}")
    }

    @Test
    fun `an ascent owing stops holds them on the threes, the shallowest where it was asked`() {
        val ascent = assertIs<Ascended.Done>(
            completeAscent(planned(""""depth": [[0, 0], [90, 40], [1800, 40]]"""), 9.0, 6.0),
        )
        val held = ascent.depth.map { it.second }.filter { it > 0 }.distinct()

        assertTrue(held.isNotEmpty(), "forty metres for half an hour owes a stop")
        assertTrue(held.all { it % 3.0 == 0.0 }, "$held")
        assertEquals(6.0, held.min(), "the shallowest stop is the one asked for")
    }

    @Test
    fun `a dive owing nothing comes straight up`() {
        val ascent = assertIs<Ascended.Done>(
            completeAscent(planned(""""depth": [[0, 0], [60, 12], [1800, 12]]"""), 9.0, 3.0),
        )

        assertEquals(listOf(0.0), ascent.depth.map { it.second }, "straight to the surface")
    }

    @Test
    fun `the ascent moves to the richest gas the depth allows`() {
        val ascent = assertIs<Ascended.Done>(
            completeAscent(
                planned(
                    """"gas_switches": [[0, "*g1"]], "depth": [[0, 0], [90, 40], [1800, 40]]""",
                    sources = """"g1": {"gas_type": "EAN28"}, "g2": {"gas_type": "EAN50"}""",
                ),
                9.0,
                3.0,
            ),
        )
        val switch = ascent.switches.single()
        val at = ascent.depth.first { it.first >= switch.first }.second

        assertEquals("g2", switch.second)
        assertTrue(at <= 21.0, "EAN50 is breathable from 21 metres, and it switched at $at")
    }

    @Test
    fun `a run already at the surface has no way up to write`() {
        val ascent = assertIs<Ascended.Done>(completeAscent(planned(SHALLOW), 9.0, 3.0))

        assertTrue(ascent.depth.isEmpty() && ascent.switches.isEmpty(), "${ascent.depth}")
    }

    @Test
    fun `what cannot be evaluated cannot be ascended from either`() {
        val reason = assertIs<Ascended.Refused>(
            completeAscent(bare(""""depth": [[0, 0], [90, 40]]"""), 9.0, 3.0),
        ).reason

        assertTrue("how conservative" in reason, reason)
    }

    @Test
    fun `a run typed in and the same run read off a profile are answered alike`() {
        val typed = Run(
            depth = listOf(0 to 0.0, 90 to 40.0, 1800 to 40.0, 1900 to 0.0),
            sources = mapOf("g1" to Source(Gas.AIR, sac = 20.0, volume = 12.0, fill = 200.0)),
            gradientFactorLow = 1.0,
            gradientFactorHigh = 1.0,
            density = 1000.0,
            surface = 1.0,
        )
        val fromRun = assertIs<Evaluated.Done>(evaluate(typed))
        val fromProfile = done(
            planned(
                DEEP,
                sources = """"g1": {"gas_type": "AIR", "sac": 20, "volume": 12,
                    "start_pressure": 200}""",
            ),
        )

        assertEquals(values(fromProfile.ceiling), values(fromRun.ceiling))
        assertEquals(fromProfile.gasUsed, fromRun.gasUsed)
        assertEquals(fromProfile.findings.map { it.said }, fromRun.findings.map { it.said })
    }

    @Test
    fun `a run typed in can be given its ascent, and the two doors agree on it`() {
        val typed = Run(
            depth = listOf(0 to 0.0, 90 to 40.0, 1800 to 40.0),
            sources = mapOf("g1" to Source(Gas(28, 0)), "g2" to Source(Gas(50, 0))),
            gradientFactorLow = 1.0,
            gradientFactorHigh = 1.0,
            switches = listOf(0 to "g1"),
            density = 1000.0,
            surface = 1.0,
        )
        val fromRun = assertIs<Ascended.Done>(completeAscent(typed, 9.0, 3.0))
        val fromProfile = assertIs<Ascended.Done>(
            completeAscent(
                planned(
                    """"gas_switches": [[0, "*g1"]], "depth": [[0, 0], [90, 40], [1800, 40]]""",
                    sources = """"g1": {"gas_type": "EAN28"}, "g2": {"gas_type": "EAN50"}""",
                ),
                9.0,
                3.0,
            ),
        )

        assertEquals(fromProfile.depth, fromRun.depth)
        assertEquals(fromProfile.switches, fromRun.switches)
    }

    @Test
    fun `a run starts fresh unless told what it carries`() {
        val loaded = Tissues.saturated(1.0).breathing(Gas.AIR, 4.0, 4.0, 1800.0)
        val plain = Run(listOf(0 to 0.0, 60 to 12.0, 1800 to 12.0, 1860 to 0.0),
            mapOf("g1" to Source(Gas.AIR)), 1.0, 1.0, density = 1000.0, surface = 1.0)
        val carrying = Run(plain.depth, plain.sources, 1.0, 1.0, density = 1000.0, surface = 1.0,
            carried = loaded)

        val fresh = assertIs<Evaluated.Done>(evaluate(plain))
        val after = assertIs<Evaluated.Done>(evaluate(carrying))
        assertTrue(
            after.surfacing.nitrogenIn(16) > fresh.surfacing.nitrogenIn(16),
            "what was carried in is still there at the end",
        )
    }

    @Test
    fun `a run says what it will not hold`() {
        assertEquals(
            "the high gradient factor should be 0 to 1, but was 1.2",
            assertFailsWith<IllegalArgumentException> {
                Run(emptyList(), emptyMap(), 0.3, 1.2)
            }.message,
        )
        assertEquals(
            "depths should run forwards, but 60 follows 90",
            assertFailsWith<IllegalArgumentException> {
                Run(listOf(0 to 0.0, 90 to 30.0, 60 to 30.0), emptyMap(), 0.3, 0.7)
            }.message,
        )
        assertTrue("no depths" in assertIs<Evaluated.Refused>(
            evaluate(Run(emptyList(), mapOf("g1" to Source(Gas.AIR)), 0.3, 0.7)),
        ).reason)
    }

    @Test
    fun `a finding about a cylinder says which, apart from the sentence`() {
        val evaluated = done(
            planned(
                """"gas_switches": [[0, "*g1"]], "depth": [[0, 0], [90, 40], [600, 40]]""",
                sources = """"g1": {"gas_type": "EAN50", "sac": 25, "volume": 3,
                    "start_pressure": 100}""",
            ),
        )
        val dry = evaluated.findings.single { "empty" in it.said }
        val rich = evaluated.findings.single { "at most" in it.said }

        assertEquals("g1", dry.source, "which cylinder, for whatever names cylinders")
        assertEquals("g1", rich.source)
        // The key is a run's word for a cylinder, not a reader's, so it stays out of the sentence.
        assertTrue("g1" !in dry.said, dry.said)
        assertTrue("g1" !in rich.said, rich.said)
    }

    @Test
    fun `a mix says how deep it may be breathed, and the run agrees with it`() {
        // Published figures at 1.6 bar: air to about 66 metres, EAN50 to about 22.
        val air = assertNotNull(maximumOperatingDepth(Gas.AIR))
        val fifty = assertNotNull(maximumOperatingDepth(Gas(50, 0)))

        assertTrue(air in 64.0..68.0, "$air m on air")
        assertTrue(fifty in 21.0..23.0, "$fifty m on EAN50")
        assertNull(maximumOperatingDepth(Gas(0, 79)), "a mix with no oxygen is breathable nowhere")
    }

    @Test
    fun `a plan built to the depth a mix allows raises no finding about it`() {
        val deepest = assertNotNull(maximumOperatingDepth(Gas(50, 0), density = 1000.0, surface = 1.0))
        val evaluated = assertIs<Evaluated.Done>(
            evaluate(
                Run(
                    depth = listOf(0 to 0.0, 60 to deepest, 600 to deepest, 700 to 0.0),
                    sources = mapOf("g1" to Source(Gas(50, 0))),
                    gradientFactorLow = 1.0,
                    gradientFactorHigh = 1.0,
                    density = 1000.0,
                    surface = 1.0,
                ),
            ),
        )

        assertTrue(evaluated.findings.none { "pO₂" in it.said }, "${evaluated.findings}")
    }

    @Test
    fun `an ascent surfaces as soon as the model allows, and no sooner`() {
        // The rule the ascent is held to: the factor that decides whether a diver may surface is
        // the one at the surface, the high one. Reading it where the diver stands instead made
        // every shallow stop longer than it should be, by a factor nobody chose.
        val bottom = Run(
            depth = listOf(0 to 0.0, 133 to 40.0, 1633 to 40.0),
            sources = mapOf("g1" to Source(Gas.AIR)),
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))

        fun findingsOf(depth: List<Pair<Int, Double>>): List<Finding> =
            assertIs<Evaluated.Done>(
                evaluate(
                    Run(depth, bottom.sources, 0.3, 0.7, switches = bottom.switches),
                ),
            ).findings

        val whole = bottom.depth + ascent.depth
        assertTrue(
            findingsOf(whole).none { "ceiling" in it.said },
            "what the ascent wrote is what the model allows: ${findingsOf(whole)}",
        )

        // A minute less at the last stop, surfacing a minute early, and the model objects.
        val surfaced = whole.last().first
        val hurried = whole.filter { it.first < surfaced - 80 } +
            listOf(surfaced - 80 to 3.0, surfaced - 60 to 0.0)
        assertTrue(
            findingsOf(hurried).any { "ceiling" in it.said },
            "a minute early should break the ceiling: ${findingsOf(hurried)}",
        )
    }

    @Test
    fun `an ascent never rises faster than the rate it was given`() {
        // Nineteen metres at nine a minute is 126.7 seconds. Rounded down, the rise is a shade
        // faster than asked, and a form timing it by its own arithmetic calls it too short.
        val bottom = Run(
            depth = listOf(0 to 0.0, 134 to 40.0, 1500 to 40.0),
            sources = mapOf("g1" to Source(Gas.AIR)),
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
        val whole = listOf(bottom.depth.last()) + ascent.depth

        for ((from, to) in whole.zipWithNext()) {
            val risen = from.second - to.second
            if (risen <= 0) continue
            val rate = risen / (to.first - from.first) * 60
            assertTrue(rate <= 9.0, "$risen m in ${to.first - from.first} s is $rate m a minute")
        }
    }

    @Test
    fun `a cylinder held to less oxygen is warned about at less`() {
        // EAN32 at thirty-five metres of sea water is 1.44 bar: within 1.6, over 1.4.
        fun oxygenFindings(most: Double): List<Finding> = assertIs<Evaluated.Done>(
            evaluate(
                Run(
                    depth = listOf(0 to 0.0, 120 to 35.0, 900 to 35.0),
                    sources = mapOf("g1" to Source(Gas.parse("EAN32"), mostOxygen = most)),
                    gradientFactorLow = 1.0,
                    gradientFactorHigh = 1.0,
                ),
            ),
        ).findings.filter { "at most" in it.said }

        assertTrue(oxygenFindings(MOST_OXYGEN).isEmpty(), "${oxygenFindings(MOST_OXYGEN)}")
        val held = oxygenFindings(1.4).single()
        assertTrue("1.40" in held.said, held.said)
        assertEquals("g1", held.source)
    }

    @Test
    fun `an ascent takes a deco gas no deeper than its own limit allows`() {
        fun switchedAt(most: Double): Pair<Double, Double> {
            val run = decoRun(Source(Gas.parse("EAN50"), mostOxygen = most))
            val ascent = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0))
            val second = ascent.switches.single { it.second == "g2" }.first
            val metres = ascent.depth.single { it.first == second }.second
            return metres to 0.5 * ambientAt(metres, run.density, run.surface)
        }

        val (usual, _) = switchedAt(MOST_OXYGEN)
        val (held, oxygen) = switchedAt(1.4)
        assertTrue(held < usual, "held to 1.4 it is taken shallower: $held against $usual")
        assertTrue(oxygen <= 1.4, "and breathed within it: $oxygen bar at $held m")
    }

    @Test
    fun `an ascent passes a deco gas's depth until it owes a stop, unless told to stop there`() {
        // Twenty-five minutes at forty metres on air owes its first stop shallower than twenty-one
        // metres, where EAN50 comes within 1.6 bar of oxygen.
        val run = decoRun(Source(Gas.parse("EAN50")))
        fun switchedAt(ascent: Ascended.Done): Double {
            val second = ascent.switches.single { it.second == "g2" }.first
            return ascent.depth.single { it.first == second }.second
        }
        val passing = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0))
        val stopping = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0, switchStops = true))

        assertTrue(switchedAt(passing) < 21.0, "${passing.depth}")
        assertEquals(21.0, switchedAt(stopping), "${stopping.depth}")
        assertEquals(60, heldIn(run, stopping, 21.0), "held a minute for the switch")
        // Here the richer gas saves about the minute held for it, and stops come in whole minutes,
        // so the two surface together. The switch costs nothing, which is all the option promises.
        assertTrue(
            stopping.depth.last().first <= passing.depth.last().first,
            "the minute held for the switch is paid back by the richer gas",
        )
    }

    @Test
    fun `a dive no deeper than a deco gas's limit makes no stop for it`() {
        val run = Run(
            depth = listOf(0 to 0.0, 60 to 18.0, 1200 to 18.0),
            sources = mapOf("g1" to Source(Gas.AIR), "g2" to Source(Gas.parse("EAN50"))),
            gradientFactorLow = 1.0,
            gradientFactorHigh = 1.0,
            switches = listOf(0 to "g1"),
        )

        assertEquals(
            assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0)).depth,
            assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0, switchStops = true)).depth,
        )
    }

    @Test
    fun `an ascent finished from part-way up is the rest of the one begun at the bottom`() {
        // The low factor is anchored at the dive's first stop. Anchored afresh where the typed
        // lines stop, the rest of the way up would hold longer than the ascent it continues.
        val bottom = decoRun(Source(Gas.parse("EAN50")))
        val whole = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
        for (cut in 1..<whole.depth.size - 1) {
            val typed = bottom.withDepth(
                bottom.depth + whole.depth.take(cut),
                bottom.switches + whole.switches.filter { it.first <= whole.depth[cut - 1].first },
            )
            val rest = assertIs<Ascended.Done>(completeAscent(typed, 9.0, 3.0))
            assertEquals(whole.depth.drop(cut), rest.depth, "typed to ${whole.depth[cut - 1]}")
        }
    }

    @Test
    fun `the time to surface on a planned ascent is the time the ascent has left`() {
        val bottom = decoRun(Source(Gas.parse("EAN50"))).withRate(9.0)
        val whole = bottom.withDepth(
            bottom.depth + assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0)).depth,
            bottom.switches + assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0)).switches,
        )
        val tts = done(whole).timeToSurface
        val end = whole.depth.last().first

        for (at in 0..<tts.size) {
            val second = tts.secondAt(at)
            if (second < bottom.depth.last().first) continue
            assertEquals((end - second).toDouble(), (tts.valueAt(at) as Element.Usable).value as Double, 1e-9, "at $second")
        }
    }

    @Test
    fun `the time to surface is nought at the surface and longer the more is owed`() {
        val tts = done(decoRun(Source(Gas.parse("EAN50")))).timeToSurface
        val values = (0..<tts.size).map { tts.secondAt(it) to ((tts.valueAt(it) as Element.Usable).value as Double) }

        assertEquals(0.0, values.first().second, "on the surface before the dive")
        assertTrue(values.last().second > values[1].second, "the end of the bottom owes more than its start: $values")
    }

    @Test
    fun `a recording's time to surface rises at nine metres a minute to a three-metre last stop`() {
        val recording = decoRun(Source(Gas.parse("EAN50")))
        val tts = done(recording).timeToSurface
        val ascent = assertIs<Ascended.Done>(completeAscent(recording, TTS_METRES_A_MINUTE, TTS_LAST_STOP))

        assertEquals(
            (ascent.depth.last().first - recording.depth.last().first).toDouble(),
            (tts.valueAt(tts.size - 1) as Element.Usable).value as Double,
            1e-9,
        )
    }

    @Test
    fun `the gradient factor now is below nought on the bottom and within the high factor on surfacing`() {
        val bottom = decoRun(Source(Gas.parse("EAN50")))
        val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
        val whole = bottom.withDepth(bottom.depth + ascent.depth, bottom.switches + ascent.switches)
        val factors = done(whole).gradientFactorNow
        fun at(second: Int): Double {
            val index = (0..<factors.size).first { factors.secondAt(it) == second }
            return (factors.valueAt(index) as Element.Usable).value as Double
        }

        assertTrue(at(bottom.depth.last().first) < 0, "still taking gas on at forty metres")
        val surfacing = at(whole.depth.last().first)
        assertTrue(surfacing > 0 && surfacing <= 70.0 + 1e-6, "between nought and GF high on surfacing: $surfacing")
    }

    @Test
    fun `an ascent never stops for a bailout`() {
        val bailout = decoRun(Source(Gas.parse("EAN50"), ascentMayChoose = false))

        assertEquals(
            assertIs<Ascended.Done>(completeAscent(bailout, 9.0, 3.0)).depth,
            assertIs<Ascended.Done>(completeAscent(bailout, 9.0, 3.0, switchStops = true)).depth,
        )
    }

    @Test
    fun `an ascent never chooses a bailout`() {
        val chosen = assertIs<Ascended.Done>(
            completeAscent(decoRun(Source(Gas.parse("EAN50"))), 9.0, 3.0),
        )
        val bailout = assertIs<Ascended.Done>(
            completeAscent(decoRun(Source(Gas.parse("EAN50"), ascentMayChoose = false)), 9.0, 3.0),
        )

        assertTrue(chosen.switches.any { it.second == "g2" }, "a deco gas is taken")
        assertTrue(bailout.switches.isEmpty(), "a bailout is not: ${bailout.switches}")
    }

    @Test
    fun `an ascent begun on a bailout is not switched to a leaner mix`() {
        // The user has gone to the bailout at twenty-one metres. Air is the only gas the ascent may
        // choose, and moving to it would take a diver off the richer mix for nothing.
        val run = Run(
            depth = listOf(0 to 0.0, 134 to 40.0, 1500 to 40.0, 1627 to 21.0),
            sources = mapOf(
                "g1" to Source(Gas.AIR),
                "g2" to Source(Gas.parse("EAN50"), ascentMayChoose = false),
            ),
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1", 1627 to "g2"),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0))

        assertTrue(ascent.switches.isEmpty(), "${ascent.switches}")
    }

    @Test
    fun `an ascent holds the safety stop where the model would have come straight up`() {
        val shallow = safetyRun(metres = 18.0, minutes = 20, low = 1.0, high = 1.0)
        val without = assertIs<Ascended.Done>(completeAscent(shallow, 9.0, 3.0))
        val with = assertIs<Ascended.Done>(completeAscent(shallow.withSafetyStop(6.0, 180), 9.0, 3.0))

        assertEquals(listOf(0.0), without.depth.map { it.second }, "no stop owed without it")
        assertEquals(180, heldIn(shallow, with, 6.0), "${with.depth}")
        assertEquals(0.0, with.depth.last().second, "and then to the surface")
    }

    @Test
    fun `a deco stop already longer than the safety stop is left alone`() {
        val deep = safetyRun(metres = 40.0, minutes = 25, low = 0.3, high = 0.7)
        val without = assertIs<Ascended.Done>(completeAscent(deep, 9.0, 3.0))
        val with = assertIs<Ascended.Done>(completeAscent(deep.withSafetyStop(6.0, 180), 9.0, 3.0))

        assertTrue(heldIn(deep, without, 6.0) > 180, "the deco stop at six is the longer")
        assertEquals(without.depth, with.depth)
    }

    @Test
    fun `a deco stop shorter than the safety stop is lengthened to it`() {
        val deep = safetyRun(metres = 40.0, minutes = 25, low = 0.3, high = 0.7)
        val without = assertIs<Ascended.Done>(completeAscent(deep, 9.0, 3.0))
        val longest = heldIn(deep, without, 6.0) + 150
        val with = assertIs<Ascended.Done>(
            completeAscent(deep.withSafetyStop(6.0, longest), 9.0, 3.0),
        )

        assertEquals(longest, heldIn(deep, with, 6.0), "${with.depth}")
    }

    @Test
    fun `a run typed to the surface past its safety stop is warned about`() {
        val skipped = safetyRun(
            metres = 18.0,
            minutes = 20,
            low = 1.0,
            high = 1.0,
            end = listOf(1320 to 0.0),
        ).withSafetyStop(6.0, 180)
        val short = safetyRun(
            metres = 18.0,
            minutes = 20,
            low = 1.0,
            high = 1.0,
            end = listOf(1280 to 6.0, 1340 to 6.0, 1380 to 0.0),
        ).withSafetyStop(6.0, 180)

        val none = done(skipped).findings.single { "Safety stop" in it.said }
        assertTrue("should last 3:00, not 0:00" in none.said, none.said)
        val partly = done(short).findings.single { "Safety stop" in it.said }
        assertTrue("should last 3:00, not 1:00" in partly.said, partly.said)
        assertEquals(1340, partly.second, "said where the stop is left")
    }

    @Test
    fun `a safety stop is owed only by a run that went deeper and has surfaced`() {
        val shallow = safetyRun(metres = 5.0, minutes = 30, low = 1.0, high = 1.0, end = listOf(1830 to 0.0))
        val underWater = safetyRun(metres = 18.0, minutes = 20, low = 1.0, high = 1.0)

        assertTrue(done(shallow.withSafetyStop(6.0, 180)).findings.none { "safety" in it.said })
        assertTrue(done(underWater.withSafetyStop(6.0, 180)).findings.none { "safety" in it.said })
    }

    @Test
    fun `an ascent written with a safety stop is one the model then approves of`() {
        val run = safetyRun(metres = 40.0, minutes = 25, low = 0.3, high = 0.7).withSafetyStop(6.0, 600)
        val ascent = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0))
        val whole = run.withDepth(run.depth + ascent.depth, run.switches + ascent.switches)

        assertTrue(done(whole).findings.none { "safety" in it.said }, "${done(whole).findings}")
    }

    @Test
    fun `a rise faster than the plan's rate is said once, where it begins`() {
        val hurried = Run(
            depth = listOf(0 to 0.0, 60 to 18.0, 1200 to 18.0, 1230 to 9.0, 1260 to 0.0),
            sources = mapOf("g1" to Source(Gas.AIR)),
            gradientFactorLow = 1.0,
            gradientFactorHigh = 1.0,
            ascentRate = 9.0,
        )
        val fast = done(hurried).findings.single { "Ascent" in it.said }

        assertEquals(1200, fast.second)
        assertTrue("18 m/min" in fast.said && "at most 9 m/min" in fast.said, fast.said)
        assertTrue(
            done(hurried.withRate(null)).findings.none { "Ascent" in it.said },
            "a run that names no rate is not judged by one",
        )
    }

    @Test
    fun `an ascent written at the plan's rate is not called too fast`() {
        val run = safetyRun(metres = 40.0, minutes = 25, low = 0.3, high = 0.7).withRate(9.0)
        val ascent = assertIs<Ascended.Done>(completeAscent(run, 9.0, 3.0))
        val whole = run.withDepth(run.depth + ascent.depth, run.switches + ascent.switches)

        assertTrue(done(whole).findings.none { "Ascent" in it.said }, "${done(whole).findings}")
    }

    @Test
    fun `a hypoxic mix breathed at the surface is said once, and not at depth`() {
        // Trimix 10/70 is 0.10 bar at the surface and reaches 0.18 bar at about eight metres.
        fun leanFindings(switches: List<Pair<Int, String>>): List<Finding> = assertIs<Evaluated.Done>(
            evaluate(
                Run(
                    depth = listOf(0 to 0.0, 60 to 3.0, 120 to 20.0, 600 to 20.0),
                    sources = mapOf("g1" to Source(Gas.AIR), "g2" to Source(Gas.parse("TMX10/70"))),
                    gradientFactorLow = 1.0,
                    gradientFactorHigh = 1.0,
                    switches = switches,
                ),
            ),
        ).findings.filter { "at least" in it.said }

        val atTheSurface = leanFindings(listOf(0 to "g2")).single()
        assertEquals(0, atTheSurface.second)
        assertEquals("g2", atTheSurface.source)
        assertTrue("0.18" in atTheSurface.said, atTheSurface.said)
        assertTrue(leanFindings(listOf(0 to "g1", 120 to "g2")).isEmpty(), "switched to at 20 m, it is fine")
    }

    @Test
    fun `a cylinder held to a lower minimum is warned of only below it`() {
        // Trimix 10/70 at the surface is 0.10 bar: under the 0.18 default, over a minimum of 0.08.
        fun leanAtTheSurface(least: Double): List<Finding> = assertIs<Evaluated.Done>(
            evaluate(
                Run(
                    depth = listOf(0 to 0.0, 60 to 3.0, 120 to 20.0, 600 to 20.0),
                    sources = mapOf("g1" to Source(Gas.parse("TMX10/70"), leastOxygen = least)),
                    gradientFactorLow = 1.0,
                    gradientFactorHigh = 1.0,
                ),
            ),
        ).findings.filter { "should be at least" in it.said }

        assertEquals(1, leanAtTheSurface(LEAST_OXYGEN).size)
        assertTrue(leanAtTheSurface(0.08).isEmpty(), "${leanAtTheSurface(0.08)}")
        assertTrue("0.08" !in leanAtTheSurface(LEAST_OXYGEN).single().said)
    }

    @Test
    fun `a finding about the dive rather than a cylinder names none`() {
        val ceiling = done(planned(DEEP)).findings.single { "ceiling" in it.said }

        assertEquals(null, ceiling.source)
    }

    @Test
    fun `a first switch part way down says nothing about what was breathed before it`() {
        // A computer that reports only its changes: trimix to the bottom, a switch to EAN50 at
        // twenty-four minutes, and nothing about the trimix. Taking the first switch's gas for
        // the time before it breathed EAN50 at thirty-five metres, and said so as a finding.
        val set = logbook(
            "dive/d#0.json" to """{
                "gas_sources": {"g1": {"gas_type": "TMX18/35"}, "g2": {"gas_type": "EAN50"}},
                "profiles": {"p1": {"water_type": "fresh", "atmospheric_pressure": 1.0, "gradient_factor_low": 0.3,
                    "gradient_factor_high": 0.7, "gas_switches": [[1440, "*g2"]],
                    "depth": [[0, 0], [120, 35], [1400, 35], [1440, 21], [2400, 0]]}}}""",
        )
        val refused = assertIs<Evaluated.Refused>(evaluate(profile(set["d#0"]!!, "p1")))

        assertEquals(Refusal.FAULTY, refused.why, "the starting gas is worth writing in")
        assertTrue("before" in refused.reason && "2:00" in refused.reason, refused.reason)
    }

    @Test
    fun `a starting gas written a sample after going under is still the starting gas`() {
        // A computer sampling every five seconds is under a metre at five and writes the gas it
        // started on at ten. The rule was one sample wide and refused every such dive; it allows
        // a minute, which no bottom gas is breathed long enough in to matter.
        val set = logbook(
            "dive/d#0.json" to """{
                "gas_sources": {"g1": {"gas_type": "TMX18/35"}, "g2": {"gas_type": "EAN50"}},
                "profiles": {"p1": {"water_type": "fresh", "atmospheric_pressure": 1.0, "gradient_factor_low": 0.3,
                    "gradient_factor_high": 0.7, "gas_switches": [[10, "*g1"], [1440, "*g2"]],
                    "depth": [[0, 0], [5, 2], [120, 35], [1400, 35], [1440, 21], [2400, 0]]}}}""",
        )

        assertIs<Evaluated.Done>(evaluate(profile(set["d#0"]!!, "p1")))
    }

    @Test
    fun `a first switch at the surface is the gas the dive went in on`() {
        val set = logbook(
            "dive/d#0.json" to """{
                "gas_sources": {"g1": {"gas_type": "TMX18/35"}, "g2": {"gas_type": "EAN50"}},
                "profiles": {"p1": {"water_type": "fresh", "atmospheric_pressure": 1.0, "gradient_factor_low": 0.3,
                    "gradient_factor_high": 0.7, "gas_switches": [[5, "*g1"], [1440, "*g2"]],
                    "depth": [[0, 0], [120, 35], [1400, 35], [1440, 21], [2400, 0]]}}}""",
        )
        val evaluated = done(profile(set["d#0"]!!, "p1"))

        assertTrue(evaluated.findings.none { "pO₂" in it.said }, "${evaluated.findings}")
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
        "dive/d#0.json" to """{
            "gas_sources": {"g1": {"gas_type": "AIR"}}, "profiles": {"a": {$run}}}""",
    )
    return profile(set["d#0"]!!, "a")
}

/** The second of two deep dives an hour apart, whose plan holds [carrying]. */
private fun repetitive(carrying: String): Item =
    profile(chained(carrying)["second#0"]!!, "a")

private fun chained(carrying: String): ItemSet = logbook(
    "dive/first#0.json" to """{
        "gas_sources": {"g1": {"gas_type": "AIR"}},
        "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
            "gradient_factor_high": 1.0, $DEEP}}}""",
    "dive/second#0.json" to """{
        "surface_interval": 3600,
        "gas_sources": {"g1": {"gas_type": "AIR"}},
        "profiles": {"a": {"water_type": "fresh", "gradient_factor_low": 1.0,
            "gradient_factor_high": 1.0, $carrying $DEEP}}}""",
)

/** Twenty-five minutes at forty metres on air, carrying [deco] as its second source. */
private fun decoRun(deco: Source): Run = Run(
    depth = listOf(0 to 0.0, 134 to 40.0, 1500 to 40.0),
    sources = mapOf("g1" to Source(Gas.AIR), "g2" to deco),
    gradientFactorLow = 0.3,
    gradientFactorHigh = 0.7,
    switches = listOf(0 to "g1"),
)

/** An air dive to [metres], leaving the bottom at [minutes], then whatever [end] adds. */
private fun safetyRun(
    metres: Double,
    minutes: Int,
    low: Double,
    high: Double,
    end: List<Pair<Int, Double>> = emptyList(),
): Run = Run(
    depth = listOf(0 to 0.0, ceil(metres / 18.0 * 60).toInt() to metres, minutes * 60 to metres) +
        end,
    sources = mapOf("g1" to Source(Gas.AIR)),
    gradientFactorLow = low,
    gradientFactorHigh = high,
    switches = listOf(0 to "g1"),
)

private fun done(run: Run): Evaluated.Done = assertIs<Evaluated.Done>(evaluate(run))

private fun Run.copied(
    depth: List<Pair<Int, Double>> = this.depth,
    switches: List<Pair<Int, String>> = this.switches,
    safetyStop: SafetyStop? = this.safetyStop,
    ascentRate: Double? = this.ascentRate,
): Run = Run(
    depth = depth,
    sources = sources,
    gradientFactorLow = gradientFactorLow,
    gradientFactorHigh = gradientFactorHigh,
    switches = switches,
    density = density,
    surface = surface,
    safetyStop = safetyStop,
    ascentRate = ascentRate,
)

private fun Run.withSafetyStop(metres: Double, seconds: Int): Run =
    copied(safetyStop = SafetyStop(metres, seconds))

private fun Run.withRate(rate: Double?): Run = copied(ascentRate = rate)

private fun Run.withDepth(depth: List<Pair<Int, Double>>, switches: List<Pair<Int, String>>): Run =
    copied(depth = depth, switches = switches)

/** The seconds [ascent] holds at [metres], counting from the run's own last point. */
private fun heldIn(run: Run, ascent: Ascended.Done, metres: Double): Int =
    (listOf(run.depth.last()) + ascent.depth).zipWithNext()
        .filter { (from, to) -> from.second == metres && to.second == metres }
        .sumOf { (from, to) -> to.first - from.first }
