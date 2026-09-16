package yemoja.ui.api

import yemoja.data.Stored
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Change
import yemoja.logic.Operation
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What an agent can ask of a logbook. See ../../../../../../api/doc.md — `API-4` and `API-5`.
 */

private fun logbook(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

/** A dive with a buddy, a recording and a written SAC, and a person with private details. */
private fun diving(): Universe = logbook(
    "person.json" to """{"anna": {"first_name": "Anna", "email": "anna@example.invalid",
        "medical": {"body_mass": 60}}}""",
    "dive/2026-06-01#0.json" to """{"buddies": ["@anna"],
        "gas_sources": {"g1": {"gas_type": "EAN32", "volume": 12, "sac": 15.25}},
        "profiles": {"p1": {"start_date": "2026-06-01", "start_time": "10:00:00",
            "depth": [[0, 0], [60, 12.5], [120, 0]],
            "pressures": {"g1": [[0, 200], [120, 180]]}}}}""",
)

/** The same logbook, with somewhere to stage a change beside it. */
private fun staging(): Universe {
    val store = MemoryFileStore(
        mapOf(
            "dive/2026-06-01#0.json" to """{"rating": 6,
                "gas_sources": {"g1": {"gas_type": "EAN32", "usage": "bottom"}}}""",
        ),
    )
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

/** The reply read back, so a test asks what it says rather than how it is spelled. */
private fun read(reply: Reply): Stored.Members = Json.parse(reply.text) as Stored.Members

private fun Stored.at(vararg path: String): Stored? {
    var here: Stored? = this
    for (step in path) {
        here = when (here) {
            is Stored.Members -> here.members[step]
            is Stored.Elements -> step.toIntOrNull()?.let { here.elements.getOrNull(it) }
            else -> null
        }
    }
    return here
}

private fun Stored.leaf(vararg path: String): Any? = (at(*path) as? Stored.Leaf)?.value

private fun reason(reply: Reply): String {
    assertTrue(reply.refused, "should be refused, but answered ${reply.text}")
    return read(reply).leaf("refused") as String
}

class DescribeTest {

    @Test
    fun `every type is described, with each field's kind, unit and role`() {
        val types = read(Tools(diving()).describe()).at("types") as Stored.Elements
        assertEquals(Types.ALL.map { it.name }, types.elements.map { it.leaf("type") })
        val dive = types.elements.first()
        val fields = (dive.at("fields") as Stored.Elements).elements
        val depth = fields.first { it.leaf("name") == "max_depth" }
        assertEquals("number", depth.leaf("kind"))
        assertEquals("m", depth.leaf("unit"))
        assertEquals("one", depth.leaf("holds"))
        assertEquals("worked out, and correctable", depth.leaf("role"))
    }

    @Test
    fun `an owned item is described with its fields, and a private field is marked`() {
        val described = read(Tools(diving()).describe("person"))
        val fields = (described.at("types", "0", "fields") as Stored.Elements).elements
        assertEquals(true, fields.first { it.leaf("name") == "email" }.leaf("personal"))
        assertNull(fields.first { it.leaf("name") == "name" }.leaf("personal"))
        val medical = fields.first { it.leaf("name") == "medical" }
        assertEquals("owned item", medical.leaf("kind"))
        val inside = (medical.at("fields") as Stored.Elements).elements.map { it.leaf("name") }
        assertTrue("body_mass" in inside, "and what it holds: $inside")
    }

    @Test
    fun `a type that does not exist is refused, naming the ones that do`() {
        assertEquals(
            "type should be one of ${Types.ALL.joinToString(", ") { it.name }}, but was diver",
            reason(Tools(diving()).describe("diver")),
        )
    }
}

class GetTest {

    @Test
    fun `an item is sent whole, worked-out values included and series counted`() {
        val dive = read(Tools(diving()).get("2026-06-01#0"))
        assertEquals("dive", dive.leaf("type"))
        assertEquals(1L, dive.leaf("item", "buddy_count"), "worked out from the buddies")
        assertEquals(12.5, dive.leaf("item", "max_depth"), "worked out from the recording")
        assertEquals("@anna", dive.leaf("item", "buddies", "0"))
        assertEquals("EAN32", dive.leaf("item", "gas_sources", "g1", "gas_type"))
        assertEquals(3L, dive.leaf("item", "profiles", "p1", "depth", "samples"))
        assertEquals(2L, dive.leaf("item", "profiles", "p1", "pressures", "g1", "samples"))
    }

    @Test
    fun `a person's private details are withheld, and named as withheld`() {
        val anna = read(Tools(diving()).get("anna"))
        assertEquals("Anna", anna.leaf("item", "name"))
        assertNull(anna.at("item", "email"))
        assertNull(anna.at("item", "medical"))
        val withheld = (anna.at("withheld") as Stored.Elements).elements
        assertEquals(listOf("email", "medical"), withheld.map { (it as Stored.Leaf).value })
    }

    @Test
    fun `allowed, they are sent`() {
        var allowed = false
        val tools = Tools(diving(), personal = { allowed })
        assertNull(read(tools.get("anna")).at("item", "email"))
        allowed = true
        val anna = read(tools.get("anna"))
        assertEquals("anna@example.invalid", anna.leaf("item", "email"), "asked on every call")
        assertEquals(60L, anna.leaf("item", "medical", "body_mass"))
        assertNull(anna.at("withheld"))
    }

    @Test
    fun `an id naming nothing is refused`() {
        assertEquals(
            "nobody should name an item, but names nothing",
            reason(Tools(diving()).get("nobody")),
        )
    }
}

class ListTest {

    private val sites = logbook(
        "dive_site.json" to (1..51).joinToString(", ", "{", "}") {
            val number = it.toString().padStart(2, '0')
            """"site_$number": {"name": "Site $number"}"""
        },
    )

    @Test
    fun `a listing comes a page at a time, and the last page has no next`() {
        val tools = Tools(sites)
        val first = read(tools.list("dive_site"))
        assertEquals(51L, first.leaf("total"))
        val listed = (first.at("items") as Stored.Members).members.keys
        assertEquals(Tools.PAGE, listed.size)
        assertEquals("site_01", listed.first(), "in order")
        val second = read(tools.list("dive_site", first.leaf("next") as String))
        val rest = (second.at("items") as Stored.Members).members.keys
        assertEquals(listOf("site_51"), rest.toList())
        assertNull(second.at("next"))
    }

    @Test
    fun `a cursor from before a change is refused, so one listing never mixes two states`() {
        val tools = Tools(sites)
        val next = read(tools.list("dive_site")).leaf("next") as String
        sites.change(Operation.EDIT, Change.Write(sites.logbook["site_02"]!!, "name", "Renamed"))
        assertEquals(
            "cursor should be from revision 1, but was from 0: the logbook changed since this " +
                "listing began, so list again from the start",
            reason(tools.list("dive_site", next)),
        )
    }

    @Test
    fun `a cursor nobody handed out is refused`() {
        assertEquals(
            "cursor should be one a listing handed out, but was page two",
            reason(Tools(sites).list("dive_site", "page two")),
        )
    }

    @Test
    fun `every reply carries the revision`() {
        val tools = Tools(sites)
        assertEquals(0L, read(tools.list("dive_site")).leaf("revision"))
        sites.change(Operation.EDIT, Change.Write(sites.logbook["site_02"]!!, "name", "Renamed"))
        assertEquals(1L, read(tools.describe("wreck")).leaf("revision"))
        assertEquals(1L, read(tools.get("nobody")).leaf("revision"), "a refusal too")
    }
}

class SeriesTest {

    @Test
    fun `a series is sent as pairs of a second and a value, with its unit`() {
        val depth = read(Tools(diving()).series("2026-06-01#0", "profiles.p1.depth"))
        assertEquals("m", depth.leaf("unit"))
        assertEquals(listOf(listOf(0L, 0L), listOf(60L, 12.5), listOf(120L, 0L)), pairsOf(depth))
    }

    @Test
    fun `one key of a keyed series is named after the field`() {
        val pressures = read(Tools(diving()).series("2026-06-01#0", "profiles.p1.pressures.g1"))
        assertEquals("bar", pressures.leaf("unit"))
        assertEquals(listOf(listOf(0L, 200L), listOf(120L, 180L)), pairsOf(pressures))
    }

    @Test
    fun `a path that reaches no series is refused`() {
        val tools = Tools(diving())
        assertEquals(
            "profile should have a field called deepness, but has none",
            reason(tools.series("2026-06-01#0", "profiles.p1.deepness")),
        )
        assertEquals(
            "profiles should have an entry p9, but has none",
            reason(tools.series("2026-06-01#0", "profiles.p9.depth")),
        )
        assertEquals(
            "gas_sources.g1.volume should end at a series, but volume is not one",
            reason(tools.series("2026-06-01#0", "gas_sources.g1.volume")),
        )
    }

    private fun pairsOf(reply: Stored.Members): List<List<Any?>> =
        (reply.at("samples") as Stored.Elements).elements.map { pair ->
            (pair as Stored.Elements).elements.map { (it as Stored.Leaf).value }
        }
}

class AggregateTest {

    @Test
    fun `a figure says which measure it is, in what unit, and what it was based on`() {
        val ids = listOf("2026-06-01#0", "nobody")
        val figure = read(Tools(diving()).aggregate(ids, "gas_sources.*.sac", "mean"))
        assertEquals("mean", figure.leaf("measure"))
        assertEquals(15.25, figure.leaf("value"))
        assertEquals("l/min", figure.leaf("unit"))
        assertEquals(1L, figure.leaf("used"))
        assertEquals("@nobody", figure.leaf("skipped", "0", "at"))
        assertEquals("names nothing", figure.leaf("skipped", "0", "reason"))
    }

    @Test
    fun `a weighted mean says what it was weighted by, and a count is whole`() {
        val tools = Tools(diving())
        val dive = listOf("2026-06-01#0")
        val weighted = read(tools.aggregate(dive, "gas_sources.*.sac", "weighted mean", "duration"))
        assertEquals("duration", weighted.leaf("weighted_by"))
        val count = read(tools.aggregate(dive, "gas_sources.*.gas_type", "count"))
        assertEquals(1L, count.leaf("value"))
        assertNull(count.at("unit"))
    }

    @Test
    fun `a measure that does not exist is refused, naming the ones that do`() {
        assertEquals(
            "measure should be one of count, sum, minimum, maximum, range, mean, weighted mean, " +
                "median, standard deviation, but was average",
            reason(Tools(diving()).aggregate(listOf("2026-06-01#0"), "max_depth", "average")),
        )
    }

    @Test
    fun `a private detail is withheld from a figure too, unless allowed`() {
        assertEquals(
            "medical is withheld, being a person's private details, unless the user allows them",
            reason(Tools(diving()).aggregate(listOf("anna"), "medical.body_mass", "mean")),
        )
        val tools = Tools(diving(), personal = { true })
        val allowed = read(tools.aggregate(listOf("anna"), "medical.body_mass", "mean"))
        assertEquals(60L, allowed.leaf("value"))
        assertFalse(allowed.leaf("unit") == null)
    }
}

/*
 * Staging a change, which is the only way an agent changes anything. `RECON-8`, `API-5`.
 */
class StagingToolsTest {

    private fun writing(): Pair<Universe, Tools> {
        val universe = staging()
        return universe to Tools(universe, writing = { true })
    }

    @Test
    fun `a write tool is refused while the user has not allowed changes`() {
        val tools = Tools(staging())
        val said = reason(tools.stageSet("2026-06-01#0", "rating", "8"))
        assertTrue(said.startsWith("ask the user to tick"), "what to do comes first: $said")
        assertTrue("allowed to change data" in said, "and says which box: $said")
        assertTrue(reason(tools.stageDelete("2026-06-01#0")).isNotEmpty())
        assertTrue(reason(tools.stageAdd("dive_site", emptyMap())).isNotEmpty())
        assertTrue(reason(tools.staged()).isNotEmpty())
    }

    @Test
    fun `what is staged says the field as it is and as it would be, and changes nothing`() {
        val (universe, tools) = writing()
        val staged = read(tools.stageSet("2026-06-01#0", "gas_sources.g1.usage", "stage"))
        assertEquals("2026-06-01#0", staged.leaf("staged", "0", "id"))
        assertEquals("edit", staged.leaf("staged", "0", "doing"))
        assertEquals("gas_sources.g1.usage", staged.leaf("staged", "0", "fields", "0", "at"))
        assertEquals("bottom", staged.leaf("staged", "0", "fields", "0", "from"))
        assertEquals("stage", staged.leaf("staged", "0", "fields", "0", "to"))
        val held = universe.logbook["2026-06-01#0"]!!
        assertEquals(0, universe.revision, "and the logbook is untouched until somebody applies it")
        assertNotNull(held)
    }

    @Test
    fun `a value the field refuses is refused, in the field's own words`() {
        val (_, tools) = writing()
        assertEquals(
            "rating should be within 1..10",
            reason(tools.stageSet("2026-06-01#0", "rating", "11")),
        )
    }

    @Test
    fun `an item to delete and an item to add are staged as what they are`() {
        val (_, tools) = writing()
        tools.stageDelete("2026-06-01#0")
        val staged = read(tools.stageAdd("dive_site", mapOf("name" to "Elphinstone")))
        val doings = (staged.at("staged") as Stored.Elements).elements.map { it.leaf("doing") }
        assertEquals(listOf("add", "delete"), doings.sortedBy { it.toString() })
    }
}
