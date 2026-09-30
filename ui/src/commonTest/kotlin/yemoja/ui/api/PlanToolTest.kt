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
    fun `a plan on a dive that already exists is refused, and says what to do instead`() {
        // Staging reaches a field, and an entry of a keyed collection is not one. `API-9`.
        val universe = universeOf(
            "dive/2026-06-21#0.json" to
                """{"profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
        )
        val tools = Tools(universe, writing = { true })
        val reply = tools.createPlan(plan(), dive = "2026-06-21#0", name = "Plan A")
        assertTrue(reply.refused)
        assertTrue("only be staged as a new dive" in reply.text, reply.text)
        assertTrue("Calculations tab" in reply.text, "and what the user can do instead")
        val dive = assertNotNull(universe.logbook["2026-06-21#0"])
        val profiles = (dive.read("profiles") as Result.Usable<*>).value as Map<*, *>
        assertEquals(setOf("p"), profiles.keys, "and the dive is untouched")
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
