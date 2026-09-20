package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Ascended
import yemoja.logic.Change
import yemoja.logic.Evaluated
import yemoja.logic.Refusal
import yemoja.logic.Types
import yemoja.logic.completeAscent
import yemoja.logic.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What the model's answer looks like on a screen. See ../../../../../../gui/doc.md — `GUI-40`.
 *
 * Fresh water and an atmosphere of a round bar, as the logic layer's own tests use, so a depth of
 * ten metres is two bar.
 */

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** A dive holding one plan, whose profile holds [run] beside the settings every test shares. */
private fun planned(run: String, sources: String = AIR): Item = logbook(
    "dive/d#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
        "gas_sources": {$sources},
        "profiles": {"a": {"planned": true, "water_type": "fresh",
            "gradient_factor_low": 1.0, "gradient_factor_high": 1.0, $run}}}""",
)["d#0"]!!

private const val AIR = """"g1": {"gas_type": "AIR", "sac": 20, "volume": 12,
    "start_pressure": 200}"""

/** Half an hour at forty metres, which owes stops. */
private const val DEEP = """"depth": [[0, 0], [90, 40], [1800, 40], [1900, 0]]"""

/** Half an hour at twelve, which owes none. */
private const val SHALLOW = """"depth": [[0, 0], [60, 12], [1800, 12], [1860, 0]]"""

private fun profile(dive: Item, key: String = "a"): Item {
    val held = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
    return (held.getValue(key) as Element.Usable).value
}

private fun done(dive: Item): Evaluated.Done = assertIs<Evaluated.Done>(evaluate(profile(dive)))

private fun saidBy(shown: List<Shown>, label: String): String? =
    shown.firstOrNull { it.label == label }?.text

class PlanningTest {

    @Test
    fun `a run owing stops draws its ceiling, and one owing none draws nothing`() {
        val ceiling = ceilingLineOf(done(planned(DEEP)))

        assertTrue(ceiling != null, "forty metres for half an hour owes a stop")
        assertEquals("Ceiling", ceiling.label)
        assertTrue(ceiling.points.any { it.value > 0 })
        assertTrue(!ceiling.main, "it is read against the depth, not instead of it")
        assertNull(ceilingLineOf(done(planned(SHALLOW))), "a flat line at nought says nothing")
    }

    @Test
    fun `what the model works out says so, beside what a computer wrote`() {
        val dive = planned(DEEP)
        val titles = workedOverlaysOf(dive, profile(dive), done(dive)).map { it.title }

        assertTrue(titles.all { "worked out" in it }, "$titles")
        assertTrue(titles.any { it.startsWith("CNS") } && titles.any { it.startsWith("OTU") })
        assertTrue(titles.any { it.startsWith("NDL") }, "$titles")
    }

    @Test
    fun `the figures say what a plan costs`() {
        val dive = planned(DEEP)
        val figures = workedFiguresOf(dive, profile(dive), done(dive))

        assertTrue(figures.all { it.worked }, "nobody wrote any of them")
        assertTrue(saidBy(figures, "Stops")!!.startsWith("from "), saidBy(figures, "Stops")!!)
        assertTrue(saidBy(figures, "CNS")!!.endsWith("%"))
        assertTrue(saidBy(figures, "No-fly time") != null)
        assertTrue(saidBy(figures, "Desaturation time") != null)
    }

    @Test
    fun `a cylinder says what it gives up and what it ends at`() {
        val dive = planned(DEEP)
        val said = saidBy(workedFiguresOf(dive, profile(dive), done(dive)), "G1 used")

        assertTrue(said != null, "a cylinder with a rate and a fill has a figure")
        assertTrue(said.startsWith("2"), "about two thousand litres, not $said")
        assertTrue("ending at" in said, said)
    }

    @Test
    fun `a cylinder the plan empties says so rather than reading below nothing`() {
        val dive = planned(
            DEEP,
            sources = """"g1": {"gas_type": "AIR", "sac": 25, "volume": 3,
                "start_pressure": 200}""",
        )
        val said = saidBy(workedFiguresOf(dive, profile(dive), done(dive)), "G1 used")

        assertTrue(said != null && said.endsWith("ending at empty"), "$said")
    }

    @Test
    fun `a run owing nothing says so where a stop would be named`() {
        val dive = planned(SHALLOW)

        assertEquals(
            "none",
            saidBy(workedFiguresOf(dive, profile(dive), done(dive)), "Stops"),
        )
    }

    @Test
    fun `what the model objects to reads as a fault, at the minute it happened`() {
        val dive = planned(DEEP)
        val findings = findingsSaidOf(done(dive))

        assertTrue(findings.isNotEmpty(), "surfacing from forty metres owes stops it did not take")
        assertTrue(findings.all { it.wrong }, "a warning reads as a value that would not read")
        assertTrue(findings.all { it.label.startsWith("At ") }, "${findings.map { it.label }}")
        // This plan runs its cylinder dry as well, and that is said first because it happens
        // first: the findings are in the order a diver would meet them.
        assertTrue(findings.any { "ceiling" in it.text }, "${findings.map { it.text }}")
        assertTrue("runs out of gas" in findings.first().text, findings.first().text)
    }

    @Test
    fun `a finding about a cylinder is named as the rest of the dive names it`() {
        val dive = planned(
            DEEP,
            sources = """"g1": {"gas_type": "AIR", "sac": 25, "volume": 3,
                "start_pressure": 200}""",
        )
        val evaluated = done(dive)
        val named = findingsSaidOf(dive, profile(dive), evaluated).single { "runs out" in it.text }
        val bare = findingsSaidOf(evaluated).single { "runs out" in it.text }

        assertTrue(named.text.startsWith("G1 "), named.text)
        // With nobody to supply a name, the key stands in rather than the line naming nothing.
        assertTrue(bare.text.startsWith("g1 "), bare.text)
    }

    @Test
    fun `a plan the model will not answer for is told as anything unreadable is`() {
        val dive = logbook(
            "dive/d#0.json" to """{"profiles": {"a": {"planned": true, $DEEP}}}""",
        )["d#0"]!!
        val refused = assertIs<Evaluated.Refused>(evaluate(profile(dive)))
        val shown = refusedSaidOf(refused, planned = true)!!

        assertEquals(Refusal.UNASKED, refused.why, "nobody asked it anything")
        assertTrue(shown.wrong)
        assertEquals("Decompression", shown.label)
        assertTrue("conservative" in shown.text, shown.text)
    }

    @Test
    fun `a recording nobody asked the model about says nothing at all`() {
        val dive = logbook(
            "dive/d#0.json" to """{"profiles": {"p1": {"water_type": "fresh", $DEEP}}}""",
        )["d#0"]!!
        val refused = assertIs<Evaluated.Refused>(evaluate(profile(dive, "p1")))

        assertEquals(Refusal.UNASKED, refused.why)
        assertNull(
            refusedSaidOf(refused, planned = false),
            "a red line under every dive would say only that the model was not asked",
        )
    }

    @Test
    fun `a recording with a fault in it is told of wherever it is found`() {
        val dive = logbook(
            "dive/d#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
                "gas_sources": {"g1": {"gas_type": "AIR"}, "g2": {"gas_type": "EAN50"}},
                "profiles": {"p1": {"water_type": "fresh", "gradient_factor_low": 1.0,
                    "gradient_factor_high": 1.0, $DEEP}}}""",
        )["d#0"]!!
        val refused = assertIs<Evaluated.Refused>(evaluate(profile(dive, "p1")))

        assertEquals(Refusal.FAULTY, refused.why, "two cylinders and nothing saying which")
        assertTrue(refusedSaidOf(refused, planned = false) != null)
    }

    @Test
    fun `the way up is written on to the end of what was typed`() {
        val dive = planned(""""depth": [[0, 0], [90, 40], [1800, 40]]""")
        val plan = profile(dive)
        val ascent = assertIs<Ascended.Done>(completeAscent(plan, 9.0, 3.0))
        val changes = ascentWrittenTo(plan, ascent)

        val depth = changes.filterIsInstance<Change.Write>().first { it.field == "depth" }
        val written = assertIs<Stored.Elements>(depth.given).elements
        assertEquals(3 + ascent.depth.size, written.size, "what was typed, and then the way up")
        assertTrue(
            secondOf(written.first()) == 0 && secondOf(written[2]) == 1800,
            "the typed points keep their times",
        )
        assertTrue(secondOf(written.last()) > 1800, "and the ascent follows them")
    }

    @Test
    fun `a plan with a deco gas writes the switch to it as well`() {
        val dive = planned(
            """"gas_switches": [[0, "*g1"]], "depth": [[0, 0], [90, 40], [1800, 40]]""",
            sources = """"g1": {"gas_type": "EAN28"}, "g2": {"gas_type": "EAN50"}""",
        )
        val plan = profile(dive)
        val ascent = assertIs<Ascended.Done>(completeAscent(plan, 9.0, 3.0))
        val changes = ascentWrittenTo(plan, ascent)
        val switches = changes.filterIsInstance<Change.Write>()
            .first { it.field == "gas_switches" }
        val written = assertIs<Stored.Elements>(switches.given).elements

        assertEquals(2, written.size, "the one that was typed, and the one worked out")
        assertEquals("*g2", valueOf(written.last()))
    }

    @Test
    fun `a run already at the surface has nothing to write`() {
        val plan = profile(planned(SHALLOW))
        val ascent = assertIs<Ascended.Done>(completeAscent(plan, 9.0, 3.0))

        assertTrue(ascentWrittenTo(plan, ascent).isEmpty())
    }

    @Test
    fun `a plan says it is one and a recording does not`() {
        assertTrue(isPlanned(profile(planned(DEEP))))
        val recorded = logbook(
            "dive/d#0.json" to """{"profiles": {"p1": {"water_type": "fresh", $DEEP}}}""",
        )["d#0"]!!
        assertTrue(!isPlanned(profile(recorded, "p1")))
    }
}

private fun secondOf(sample: Stored): Int =
    ((assertIs<Stored.Elements>(sample).elements[0] as Stored.Leaf).value as Number).toInt()

private fun valueOf(sample: Stored): Any? =
    (assertIs<Stored.Elements>(sample).elements[1] as Stored.Leaf).value
