package yemoja.ui.api

import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * The planner as an agent reaches it. See ../../../../../../ui/api/doc.md — `API-9`.
 */

private const val FORTY = """
    {"name": "forty", "lines": [{"depth": 40}, {"depth": 40, "duration": "22:46"}],
     "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
     "gf_low": 100, "gf_high": 100}
"""

private fun plan(json: String = FORTY): Stored = Json.parse(json)

/** A logbook with somewhere to stage a change, which is what an agent's writing needs. */
private fun universeOf(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(
        LogbookReader.read(store, Types.ALL),
        null,
        store,
        null,
        null,
        null,
        MemoryFileStore(emptyMap()),
    )
}

class PlanToolTest {

    @Test
    fun `calculating is always allowed, there being nothing in it to allow`() {
        val tools = Tools(universeOf(), writing = { false })
        val reply = tools.plan(plan())
        assertTrue(!reply.refused, reply.text)
        assertTrue("\"stops\"" in reply.text)
        assertTrue("\"added\": true" in reply.text, "the way up the model added")
    }

    @Test
    fun `a plan that will not read is refused in the form's own words`() {
        val tools = Tools(universeOf())
        val reply = tools.plan(plan("""{"lines": [{"depth": 40, "duration": "soon"}]}"""))
        assertTrue(reply.refused)
        assertTrue("2:13" in reply.text, reply.text)
    }

    @Test
    fun `creating one is refused while the user has not allowed changes`() {
        val tools = Tools(universeOf(), writing = { false })
        val reply = tools.createPlan(plan(), dive = null, name = "Plan A")
        assertTrue(reply.refused)
        assertTrue("Allow logbook edits" in reply.text, "and it says what to tick")
    }

    @Test
    fun `a plan staged on no dive is a dive staged to be added`() {
        val universe = universeOf()
        val tools = Tools(universe, writing = { true })
        val reply = tools.createPlan(plan(), dive = null, name = "Plan A")
        assertTrue(!reply.refused, reply.text)
        assertTrue("\"doing\": \"add\"" in reply.text, reply.text)
        assertEquals(emptyList(), universe.logbook.allOf(Types.DIVE), "and nothing has landed")
    }

    @Test
    fun `a plan staged on a dive that exists waits, and lands beside what the dive holds`() {
        val universe = universeOf(
            "dive/2026-06-21#0.json" to
                """{"primary_profile": "*p", "profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
        )
        val tools = Tools(universe, writing = { true })
        val reply = tools.createPlan(plan(), dive = "2026-06-21#0", name = "Plan A")
        assertTrue(!reply.refused, reply.text)
        val dive = assertNotNull(universe.logbook["2026-06-21#0"])
        fun profiles(): Set<Any?> = ((dive.read("profiles") as Result.Usable<*>).value as Map<*, *>).keys
        assertEquals(setOf("p"), profiles(), "nothing lands until the user applies it")
        val staged = assertNotNull(universe.staging).staged.single()
        assertTrue(staged.fields.all { it.at.startsWith("profiles.Plan_A.") }, "one path per field")
        val applied = universe.staging!!.apply()
        assertTrue(applied.refused.isEmpty(), applied.refused.toString())
        assertEquals(setOf("p", "Plan_A"), profiles(), "the recording stays where it was")
        assertEquals("*p", (dive.read("primary_profile") as Result.Usable<*>).value.toString(), "and stays primary")
    }

    @Test
    fun `a plan on a dive that has none becomes the one the dive is worked from`() {
        val universe = universeOf("dive/2026-06-21#0.json" to """{"rating": 4}""")
        val tools = Tools(universe, writing = { true })
        assertTrue(!tools.createPlan(plan(), dive = "2026-06-21#0", name = "Plan A").refused)
        universe.staging!!.apply()
        val dive = assertNotNull(universe.logbook["2026-06-21#0"])
        assertEquals("*Plan_A", (dive.read("primary_profile") as Result.Usable<*>).value.toString())
    }

    @Test
    fun `a name the dive already has is refused rather than written over`() {
        val universe = universeOf(
            "dive/2026-06-21#0.json" to """{"profiles": {"Plan_A": {"depth": [[0, 0], [60, 9.0]]}}}""",
        )
        val tools = Tools(universe, writing = { true })
        val reply = tools.createPlan(plan(), dive = "2026-06-21#0", name = "Plan A")
        assertTrue(reply.refused)
        assertTrue("already has a plan called Plan_A" in reply.text, reply.text)
        assertTrue(universe.staging?.staged.isNullOrEmpty(), "and nothing is staged")
    }

    @Test
    fun `a plan staging refuses leaves nothing staged, and the logbook as it was`() {
        // A collection the file spoils is one staging cannot reach into, so the plan's first
        // field is refused.
        val written = """{"rating": 4, "profiles": 5}"""
        val store = MemoryFileStore(mapOf("dive/2026-06-21#0.json" to written))
        val universe = Universe(
            LogbookReader.read(store, Types.ALL), null, store, null, null, null,
            MemoryFileStore(emptyMap()),
        )
        val reply = Tools(universe, writing = { true }).createPlan(plan(), "2026-06-21#0", "Plan A")
        assertTrue(reply.refused, reply.text)
        assertTrue(universe.staging?.staged.isNullOrEmpty(), "nothing waits to be applied")
        assertEquals(written, store.readText("dive/2026-06-21#0.json"), "and the file is untouched")
        val dive = assertNotNull(universe.logbook["2026-06-21#0"])
        assertEquals("4", (dive.read("rating") as Result.Usable<*>).value.toString(), "nor the dive in memory")
    }

    @Test
    fun `a plan that will not calculate is refused rather than staged`() {
        val universe = universeOf()
        val tools = Tools(universe, writing = { true })
        val reply = tools.createPlan(
            plan("""{"lines": [{"depth": 40, "duration": "soon"}]}"""),
            dive = null,
            name = "Plan A",
        )
        assertTrue(reply.refused)
        assertEquals(null, universe.staging?.staged?.firstOrNull(), "and nothing is staged")
    }

    @Test
    fun `a dive that is not there, or is not a dive, says so`() {
        val universe = universeOf("person.json" to """{"anna": {"first_name": "Anna"}}""")
        val tools = Tools(universe, writing = { true })
        assertTrue(tools.createPlan(plan(), "nowhere", "Plan A").refused)
        val wrong = tools.createPlan(plan(), "anna", "Plan A")
        assertTrue(wrong.refused)
        assertTrue("rather than a dive" in wrong.text, wrong.text)
    }
}
