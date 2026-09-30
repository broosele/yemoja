package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Evaluated
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A plan's start, and the earlier run it follows. See ../../../../../../gui/doc.md — `GUI-43`.
 */

/** The morning's saved plan: thirty metres for twenty-five minutes, from 09:00 to 09:30. */
private val MORNING = """{"gas_sources": {"g1": {"gas_type": "AIR"}},
    "profiles": {"plan_a": {"planned": true, "start_date": "2026-10-03", "start_time": "09:00:00",
        "water_type": "salt", "gradient_factor_low": 0.3, "gradient_factor_high": 0.7,
        "depth": [[0, 0], [100, 30], [1500, 30], [1800, 0]]}}}"""

private fun logbook(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

/** The afternoon: thirty metres for twenty minutes on air, typed. */
private fun afternoon(date: String = "2026-10-03", time: String = "11:00"): Shaping {
    val shaping = Shaping()
    shaping.prefill(null)
    shaping.gradientLow = "30"
    shaping.gradientHigh = "70"
    shaping.segments.clear()
    shaping.segments.addAll(listOf(Segment("30"), Segment("30", duration = "18:20")))
    shaping.gases.clear()
    shaping.gases.add(Breathed("air", Role.BOTTOM, size = "24", fill = "232", sac = "20"))
    shaping.startDate = date
    shaping.startTime = time
    return shaping
}

class StartTest {

    @Test
    fun `a start is a date and a time, and neither is none`() {
        assertEquals(Start.Unset, startOf(afternoon(date = "", time = "").described()))
        assertIs<Start.At>(startOf(afternoon().described()))
        assertIs<Start.At>(startOf(afternoon(time = "11:00:00").described()), "seconds may be given")
        assertEquals("Start time is missing", assertIs<Start.Wrong>(startOf(afternoon(time = "").described())).reason)
        assertTrue(assertIs<Start.Wrong>(startOf(afternoon(date = "tomorrow").described())).reason.startsWith("Start date should be"))
    }

    @Test
    fun `a start typed wrong is the plan's fault, and none is not`() {
        assertIs<Shaped.Ready>(shapedOf(afternoon(date = "", time = "").described()))
        assertIs<Shaped.Wrong>(shapedOf(afternoon(time = "late").described()))
    }
}

class FollowingTest {

    @Test
    fun `a plan follows nothing until a run is chosen, and nothing is chosen for it`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon()

        assertNull(shaping.following)
        assertEquals(Followed.Fresh, followedOf(shaping.described(), universe))
        assertNull(assertIs<Shaped.Ready>(shapedOf(shaping.described(), universe)).run.carried)
    }

    @Test
    fun `a dive that ended in the day before is noted, and a chosen one is not`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon()
        val note = assertNotNull(followedSaid(shaping.described(), universe, followedOf(shaping.described(), universe)))

        assertTrue("ended 1 hour and 30 minutes before this start" in note, note)
        assertTrue("choose it under After" in note, note)
        assertTrue("carry" !in note, "said as what it is: $note")

        val later = afternoon(date = "2026-10-05")
        assertNull(followedSaid(later.described(), universe, followedOf(later.described(), universe)), "two days later is not worth a note")
    }

    @Test
    fun `the runs offered are those that ended in the two days before the start`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)

        assertEquals(listOf("plan_a"), offeredOf(afternoon().described(), universe).map { it.key })
        assertTrue(offeredOf(afternoon(time = "09:10").described(), universe).isEmpty(), "still in the water")
        assertTrue(offeredOf(afternoon(date = "", time = "").described(), universe).isEmpty(), "no start, nothing to measure from")
    }

    @Test
    fun `following a run starts the plan from what it left, and says so plainly`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon()
        shaping.following = Following("2026-10-03#0", "plan_a")
        val followed = assertIs<Followed.After>(followedOf(shaping.described(), universe))
        val fresh = assertIs<Shaped.Ready>(shapedOf(afternoon().described(), universe))
        val after = assertIs<Shaped.Ready>(shapedOf(shaping.described(), universe))

        assertEquals(90 * 60L, followed.intervalSeconds)
        assertNotNull(after.run.carried)
        val freshDone = assertIs<Worked.Done>(workedOf(fresh)).evaluated
        val afterDone = assertIs<Worked.Done>(workedOf(after)).evaluated
        assertTrue(afterDone.oxygen.percentCns > freshDone.oxygen.percentCns, "the morning's oxygen is still counted")
        val said = assertNotNull(followedSaid(shaping.described(), universe, followed))
        assertTrue(said.startsWith("Surface interval 1 hour and 30 minutes after "), said)
        assertTrue("CNS" in said && "at the start" in said, said)
    }

    @Test
    fun `a start before the run followed ends is the plan's fault`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon(time = "09:10")
        shaping.following = Following("2026-10-03#0", "plan_a")
        val wrong = assertIs<Shaped.Wrong>(shapedOf(shaping.described(), universe))

        assertTrue(wrong.reason.startsWith("Start should be after 2026-10-03 09:30"), wrong.reason)
    }

    @Test
    fun `following a run needs a start and the logbook`() {
        val shaping = afternoon(date = "", time = "")
        shaping.following = Following("2026-10-03#0", "plan_a")

        assertIs<Followed.Wrong>(followedOf(shaping.described(), logbook("dive/2026-10-03#0.json" to MORNING)))
        assertIs<Followed.Wrong>(
            followedOf(afternoon().described().copy(following = shaping.following), null),
        )
    }
}

class SavedFollowingTest {

    @Test
    fun `a plan saved as a new dive says when it began and what it follows, and reads back the same`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon()
        shaping.following = Following("2026-10-03#0", "plan_a")
        val ready = assertIs<Shaped.Ready>(shapedOf(shaping.described(), universe))
        val done = assertIs<Worked.Done>(workedOf(ready))
        val changes = newDiveOf("plan_b", planFieldsOf(shaping.described(), ready.conditions, done.whole), diveFieldsOf(shaping.described(), universe))
        val added = assertIs<Outcome.Done>(universe.change(yemoja.logic.Operation.EDIT, *changes.toTypedArray()))
        val dive = universe.logbook[added.added.single()]!!
        val runs = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val saved = (runs.getValue("plan_b") as Element.Usable).value

        assertEquals("2026-10-03#1", added.added.single(), "a dated plan is filed under its day")
        assertEquals(90 * 60.0, (dive.single<Double>("surface_interval") as Result.Usable).value)
        // What the saved plan starts from is what the planner showed.
        val savedDone = assertIs<Evaluated.Done>(evaluate(saved))
        assertEquals(done.evaluated.oxygen.percentCns, savedDone.oxygen.percentCns, 1e-9)

        val reopened = Shaping()
        reopened.loadFrom(saved, dive)
        assertEquals("2026-10-03", reopened.startDate)
        assertEquals("11:00", reopened.startTime)
        assertEquals(Following("2026-10-03#0", "plan_a"), reopened.following)
    }

    @Test
    fun `a dive already following another refuses a plan that follows something else`() {
        val universe = logbook(
            "dive/2026-10-03#0.json" to MORNING,
            "dive/2026-10-02#0.json" to MORNING.replace("2026-10-03", "2026-10-02"),
            "dive/2026-10-03#1.json" to """{"previous_dive": "@2026-10-02#0"}""",
        )
        val shaping = afternoon()
        shaping.following = Following("2026-10-03#0", "plan_a")
        val clash = assertNotNull(followingClashOf(shaping.described(), universe.logbook["2026-10-03#1"]!!))

        assertTrue(clash.startsWith("After should be a run of 2026-10-02#0"), clash)
        assertNull(followingClashOf(afternoon().described(), universe.logbook["2026-10-03#1"]!!), "a plan following nothing does not clash")
    }

    @Test
    fun `a plan with no start and nothing followed saves as it always has`() {
        val shaping = afternoon(date = "", time = "")
        val ready = assertIs<Shaped.Ready>(shapedOf(shaping.described()))
        val fields = planFieldsOf(shaping.described(), ready.conditions, assertIs<Worked.Done>(workedOf(ready)).whole)

        assertTrue("start_date" !in fields && "previous_profile" !in fields)
        assertTrue(diveFieldsOf(shaping.described(), null).isEmpty())
    }

    @Test
    fun `a previous profile is written as the dive and the run it names`() {
        val universe = logbook("dive/2026-10-03#0.json" to MORNING)
        val shaping = afternoon()
        shaping.following = Following("2026-10-03#0", "plan_a")
        val ready = assertIs<Shaped.Ready>(shapedOf(shaping.described(), universe))
        val fields = planFieldsOf(shaping.described(), ready.conditions, assertIs<Worked.Done>(workedOf(ready)).whole)

        assertEquals("@2026-10-03#0*plan_a", (fields.getValue("previous_profile") as yemoja.data.Stored.Leaf).value)
        assertEquals("@2026-10-03#0", (diveFieldsOf(shaping.described(), universe).getValue("previous_dive") as yemoja.data.Stored.Leaf).value)
    }
}
