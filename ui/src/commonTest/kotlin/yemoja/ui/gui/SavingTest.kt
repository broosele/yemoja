package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Breathed
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Role
import yemoja.logic.Segment
import yemoja.logic.Shaped
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.Worked
import yemoja.logic.planName
import yemoja.logic.shapedOf
import yemoja.logic.workedOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * A plan from the Calculations tab saved into the logbook, and taken back out. See
 * ../../../../../../gui/doc.md — `GUI-44`.
 */

private val FORTY = arrayOf(Segment("40"), Segment("40", duration = "22:46"))

private val CARRIED = listOf(
    Breathed("air", Role.BOTTOM, size = "24", fill = "232", sac = "20"),
    Breathed("EAN50", Role.DECO, size = "7", fill = "200", sac = "20"),
)

private fun shaping(vararg segments: Segment, gases: List<Breathed> = CARRIED): Shaping {
    val shaping = Shaping()
    shaping.prefill(null)
    shaping.gradientLow = "30"
    shaping.gradientHigh = "70"
    shaping.segments.clear()
    shaping.segments.addAll(segments)
    shaping.gases.clear()
    shaping.gases.addAll(gases)
    return shaping
}

private fun doneOf(shaping: Shaping): Pair<Shaped.Ready, Worked.Done> {
    val ready = assertIs<Shaped.Ready>(shapedOf(shaping.described()))
    return ready to assertIs<Worked.Done>(workedOf(ready))
}

private fun fieldsOf(shaping: Shaping): Map<String, Stored> {
    val (ready, done) = doneOf(shaping)
    return planFieldsOf(shaping.described(), ready.conditions, done.whole)
}

/** [shaping]'s fields as a plan saved before its lines were kept would hold them. */
private fun withoutLinesOf(shaping: Shaping): Map<String, Stored> = fieldsOf(shaping) - "runtime"

/** The plan [fields] describe, saved as a new dive and opened again into a fresh planner. */
private fun reopened(fields: Map<String, Stored>): Shaping {
    val logbook = emptyLogbook()
    val made = assertIs<Outcome.Done>(logbook.change(Operation.EDIT, *newDiveOf("Plan_A", fields).toTypedArray()))
    val dive = assertNotNull(logbook.logbook[made.added.single()])
    val back = Shaping()
    back.prefill(null)
    back.loadFrom(profilesOf(dive).getValue("Plan_A"), dive, logbook)
    return back
}

private fun emptyLogbook(files: Map<String, String> = emptyMap()): Universe {
    val store = MemoryFileStore(files)
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

/** [points] without those inside a stretch held at one depth, which change nothing about the dive. */
private fun cornersOf(points: List<Pair<Int, Double>>): List<Pair<Int, Double>> =
    points.filterIndexed { at, (_, metres) ->
        at == 0 || at == points.lastIndex || !(points[at - 1].second == metres && points[at + 1].second == metres)
    }

@Suppress("UNCHECKED_CAST")
private fun profilesOf(dive: Item): Map<String, OwnedItem> =
    ((dive.read("profiles") as Result.Usable).value as Map<String, Element<Any>>)
        .mapValues { (it.value as Element.Usable).value as OwnedItem }

class PlanNameTest {

    @Test
    fun `a plan is named for the first letter its dive has not used`() {
        assertEquals("Plan A", planName { false })
        assertEquals("Plan B", planName { it == "Plan A" })
        assertEquals("Plan A", planName { it == "Plan B" }, "a letter given up comes back")
        assertEquals("Plan Z#1", planName { !it.contains('#') }, "past the alphabet, as any key goes on")
    }

    @Test
    fun `a plan's key is its name with the spaces a key cannot hold written as underscores`() {
        assertEquals("Plan_A", yemoja.logic.planKeyOf("Plan A"))
        assertEquals("Night_dive", yemoja.logic.planKeyOf("  Night   dive "))
        assertEquals("Plan A", prettyOf("Plan_A"), "and it is shown back by its name")
    }
}

class PlanFieldsTest {

    @Test
    fun `a plan is saved as a planned profile of the whole run, its way up included`() {
        val fields = fieldsOf(shaping(*FORTY))
        assertEquals(Stored.Leaf(true), fields["planned"])
        assertEquals(Stored.Leaf("salt"), fields["water_type"])
        assertEquals(Stored.Leaf(0.3), fields["gradient_factor_low"])
        assertEquals(Stored.Leaf(0.7), fields["gradient_factor_high"])
        val depth = assertIs<Stored.Elements>(fields["depth"]).elements
        val last = assertIs<Stored.Elements>(depth.last()).elements
        assertEquals(Stored.Leaf(0.0), last[1], "it ends at the surface")
    }

    @Test
    fun `cylinders are keyed by what they are for, and the switches name those keys`() {
        val gases = CARRIED + Breathed("EAN80", Role.DECO)
        val fields = fieldsOf(shaping(*FORTY, gases = gases))
        val sources = assertIs<Stored.Members>(fields["gas_sources"]).members
        assertEquals(listOf("bottom", "deco", "deco#1"), sources.keys.toList())
        val bottom = assertIs<Stored.Members>(sources["bottom"]).members
        assertEquals(Stored.Leaf("AIR"), bottom["gas_type"])
        assertEquals(Stored.Leaf(24.0), bottom["volume"])
        assertEquals(Stored.Leaf(232.0), bottom["start_pressure"])
        val switched = assertIs<Stored.Elements>(fields["gas_switches"]).elements.map {
            (assertIs<Stored.Elements>(it).elements[1] as Stored.Leaf).value
        }
        assertEquals("*bottom", switched.first())
        assertTrue(switched.all { it in setOf("*bottom", "*deco", "*deco#1") }, "$switched")
    }

    @Test
    fun `a role is written as the usage a cylinder is for, and read back`() {
        for (role in Role.entries) assertEquals(role, roleOf(usageOf(role)))
        assertEquals(Role.BOTTOM, roleOf("stage"), "a usage the planner has no role for")
        assertEquals(Role.BOTTOM, roleOf(null))
    }
}

class SavingRoundTripTest {

    @Test
    fun `a plan saved as a new dive comes back as the lines that were typed, and the same run`() {
        val shaping = shaping(Segment("40", rate = "20"), Segment("40", duration = "20"), Segment("21", gas = 1))
        val (_, done) = doneOf(shaping)
        val back = reopened(fieldsOf(shaping))
        assertEquals(
            listOf(Segment("40", rate = "20"), Segment("40", duration = "20:00"), Segment("21", gas = 1)),
            back.segments.toList(),
            "a rate stays a rate, a line given neither stays so, and the way up is not among them",
        )
        val (_, again) = doneOf(back)
        assertEquals(done.whole.depth, again.whole.depth)
        assertEquals(done.tail, again.tail, "the way up is worked out again")
    }

    @Test
    fun `a plan comes back under the settings it was made with, not the ones a new plan starts from`() {
        val shaping = shaping(*FORTY)
        shaping.bottomOxygen = "1.3"
        shaping.decoOxygen = "1.5"
        shaping.leastOxygen = "0.16"
        shaping.descentRate = "15"
        shaping.ascentRate = "10"
        shaping.lastStop = "6"
        shaping.switchStops = true
        shaping.safetyDepth = "5"
        shaping.safetyMinutes = "5"
        shaping.panicFactor = "3"
        shaping.problemMinutes = "1.5"
        shaping.lostGas = 1
        shaping.sharedScenario = false
        val back = reopened(fieldsOf(shaping))
        assertEquals("1.3", back.bottomOxygen)
        assertEquals("1.5", back.decoOxygen)
        assertEquals("0.16", back.leastOxygen)
        assertEquals("15", back.descentRate)
        assertEquals("10", back.ascentRate)
        assertEquals("6", back.lastStop)
        assertEquals(true, back.switchStops)
        assertEquals("5", back.safetyDepth)
        assertEquals("5", back.safetyMinutes, "held in seconds, shown in minutes")
        assertEquals("3", back.panicFactor)
        assertEquals("1.5", back.problemMinutes)
        assertEquals(true, back.lostGasScenario)
        assertEquals(1, back.lostGas, "the cylinder by its place, saved as its key")
        assertEquals(false, back.sharedScenario)
    }

    @Test
    fun `the settings are written as a logbook writes them, under the names the settings use`() {
        val shaping = shaping(*FORTY)
        shaping.safetyMinutes = "3"
        shaping.lostGasScenario = false
        val fields = fieldsOf(shaping)
        assertEquals(Stored.Leaf(180L), fields["safety_stop_duration"], "seconds")
        assertEquals(Stored.Leaf(1.4), fields["po2_max_bottom"])
        assertEquals(Stored.Leaf(false), fields["lost_gas_reserve"])
        assertEquals(Stored.Leaf(false), fields["gas_switch_stops"], "written off as well as on")
        val lines = assertIs<Stored.Members>(fields["runtime"]).members
        assertEquals(listOf("1", "2"), lines.keys.toList(), "keyed by their place")
        val second = assertIs<Stored.Members>(lines["2"]).members
        assertEquals(Stored.Leaf(1366L), second["duration"])
    }

    @Test
    fun `a blank line is not kept, and a gas chosen on a line names its cylinder's key`() {
        val fields = fieldsOf(shaping(Segment("40", duration = "20"), Segment("21", gas = 1), Segment()))
        val lines = assertIs<Stored.Members>(fields["runtime"]).members
        assertEquals(2, lines.size)
        assertEquals(Stored.Leaf("*deco"), assertIs<Stored.Members>(lines["2"]).members["gas_source"])
    }

    @Test
    fun `lines that no longer lead to the points are set aside for the points`() {
        val shaping = shaping(*FORTY)
        val fields = fieldsOf(shaping).toMutableMap()
        // As though the bottom had been typed over by hand in the file: 38 m where 40 was saved.
        val depth = assertIs<Stored.Elements>(fields["depth"]).elements.map { point ->
            val (second, metres) = assertIs<Stored.Elements>(point).elements
            val changed = if ((metres as Stored.Leaf).value == 40.0) Stored.Leaf(38.0) else metres
            Stored.Elements(listOf(second, changed))
        }
        fields["depth"] = Stored.Elements(depth)
        val back = reopened(fields)
        assertEquals("38", back.segments.first().depth, "the points, as lines")
        assertEquals(emptyList(), doneOf(back).second.tail, "and they reach the surface already")
    }

    @Test
    fun `a plan saved before its lines were kept comes back as the same run, from its points`() {
        val shaping = shaping(*FORTY)
        val (_, done) = doneOf(shaping)
        val logbook = emptyLogbook()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", withoutLinesOf(shaping)).toTypedArray()
            )
        )
        val dive = assertNotNull(logbook.logbook[made.added.single()])
        val plan = assertNotNull(profilesOf(dive)["Plan_A"])
        assertTrue(isPlanned(plan))

        val back = Shaping()
        back.prefill(null)
        back.loadFrom(plan, dive)
        assertEquals("30", back.gradientLow)
        assertEquals(listOf("AIR", "EAN50"), back.gases.map { it.gas.uppercase() })
        assertEquals(listOf(Role.BOTTOM, Role.DECO), back.gases.map { it.role })
        val (_, again) = doneOf(back)
        // The same dive to the second, though a stop the model wrote a point a minute is one line
        // now, so the points inside a flat stretch are not compared.
        assertEquals(cornersOf(done.whole.depth), cornersOf(again.whole.depth))
        assertEquals(done.whole.switches.map { it.first }, again.whole.switches.map { it.first })
        assertEquals(emptyList(), again.tail, "a saved plan reaches the surface, so nothing is added")
    }

    @Test
    fun `a stop the model held a minute at a time comes back as one line`() {
        val shaping = shaping(*FORTY)
        val logbook = emptyLogbook()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", withoutLinesOf(shaping)).toTypedArray()
            )
        )
        val dive = logbook.logbook[made.added.single()]!!
        val back = Shaping()
        back.prefill(null)
        back.loadFrom(profilesOf(dive).getValue("Plan_A"), dive)
        // Two stays running on at one depth, on one gas, would be a stop said twice.
        val depths = back.segments.map { it.depth }
        for (at in 2..<depths.size) {
            val again =
                depths[at] == depths[at - 1] && depths[at - 1] == depths[at - 2] && back.segments[at].gas == null
            assertTrue(!again, "${back.segments}")
        }
    }

    @Test
    fun `a plan added to a recorded dive keeps the recording and its place`() {
        val logbook = emptyLogbook()
        val recording = mapOf(
            "primary_profile" to Stored.Leaf("*p1"),
            "profiles" to Stored.Members(
                mapOf("p1" to Stored.Members(mapOf("start_date" to Stored.Leaf("2026-05-01")))),
            ),
        )
        val made =
            assertIs<Outcome.Done>(logbook.change(Operation.EDIT, yemoja.logic.Change.Add(Types.DIVE, recording)))
        val id = made.added.single()
        val name = planNameOn(logbook.logbook[id])
        assertEquals("Plan A", name)
        assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *onDiveOf(logbook.logbook[id]!!, yemoja.logic.planKeyOf(name), fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        )
        val dive = logbook.logbook[id]!!
        assertEquals(setOf("p1", "Plan_A"), profilesOf(dive).keys)
        val primary = (dive.read("primary_profile") as Result.Usable).value as KeyReference
        assertEquals("p1", primary.key, "the recording is still what the dive is read from")
        assertEquals("Plan B", planNameOn(dive), "and the next plan there is the next letter")
    }

    @Test
    fun `a plan attached to a dive takes the name typed, or the next free one where it is taken`() {
        val logbook = emptyLogbook()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        )
        val dive = logbook.logbook[made.added.single()]!!
        assertEquals("Night_dive", attachedKeyOf(dive, "Night dive"))
        assertEquals("Plan_B", attachedKeyOf(dive, "Plan A"), "attaching adds a plan and never saves over one")
        assertEquals("Plan_B", attachedKeyOf(dive, "  "))
    }

    @Test
    fun `a plan saved over keeps what else was written on it`() {
        val logbook = emptyLogbook()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        )
        val id = made.added.single()
        val noted = onDiveOf(logbook.logbook[id]!!, "Plan_A", mapOf("start_date" to Stored.Leaf("2026-06-01")))
        assertIs<Outcome.Done>(logbook.change(Operation.EDIT, *noted.toTypedArray()))

        val shallower = shaping(Segment("30"), Segment("30", duration = "20"))
        assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *onDiveOf(logbook.logbook[id]!!, "Plan_A", fieldsOf(shallower)).toTypedArray()
            )
        )
        val plan = profilesOf(logbook.logbook[id]!!).getValue("Plan_A")
        assertIs<Result.Usable<*>>(plan.read("start_date"), "written by hand, and kept")
        val back = Shaping()
        back.prefill(null)
        back.loadFrom(plan, logbook.logbook[id])
        assertEquals("30", back.segments.first().depth, "and the run is the new one")
        assertEquals(1, profilesOf(logbook.logbook[id]!!).size)
    }
}

class OpeningTest {

    private fun chosen(): Universe = emptyLogbook(
        mapOf("settings.json" to """{"default_gradient_factor_low": 0.3, "default_gradient_factor_high": 0.75}"""),
    )

    @Test
    fun `a plan starts from the factors the user chose, and empty where nobody chose any`() {
        val shaping = Shaping()
        shaping.prefill(chosen().settings)
        assertEquals("30", shaping.gradientLow)
        assertEquals("75", shaping.gradientHigh)
        shaping.prefill(null)
        assertEquals("", shaping.gradientLow, "no conservatism is chosen for anybody")
    }

    @Test
    fun `adding a plan to a dive starts a blank one under the next free name`() {
        val logbook = chosen()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        )
        val id = made.added.single()
        val working = Shaping()
        working.segments[0] = Segment("12")
        val saving = Saving()
        saving.open(Bound.Adding(id), logbook, working)
        assertEquals("Plan B", saving.name)
        assertEquals(listOf(""), working.segments.map { it.depth }, "what was on the slate is not added")
        assertEquals("30", working.gradientLow)
    }

    @Test
    fun `every saved plan is listed by its dive and its name, and only plans`() {
        val logbook = chosen()
        val recorded = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                yemoja.logic.Change.Add(
                    Types.DIVE,
                    mapOf("profiles" to Stored.Members(mapOf("p1" to Stored.Members(mapOf("start_date" to Stored.Leaf("2026-05-01")))))),
                ),
            ),
        ).added.single()
        val planned = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        ).added.single()
        val plans = plansIn(logbook)
        assertEquals(listOf("$planned: Plan A"), plans.map { it.second }, "the recording on $recorded is not a plan")
        assertEquals(Bound.Editing(planned, "Plan_A"), plans.single().first)
    }

    @Test
    fun `editing a plan loads it, under its own name`() {
        val logbook = chosen()
        val made = assertIs<Outcome.Done>(
            logbook.change(
                Operation.EDIT,
                *newDiveOf("Plan_A", fieldsOf(shaping(*FORTY))).toTypedArray()
            )
        )
        val working = Shaping()
        val saving = Saving()
        working.gases.addAll(listOf(Breathed(), Breathed("EAN80", Role.DECO)))
        working.lostGas = 2
        saving.open(Bound.Editing(made.added.single(), "Plan_A"), logbook, working)
        assertEquals("Plan A", saving.name)
        assertEquals(null, working.lostGas, "a lost cylinder chosen for another plan does not carry over")
        assertEquals("40", working.segments.first().depth)
        assertEquals(2, working.gases.size)
    }
}
