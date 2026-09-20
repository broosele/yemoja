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
    fun `an owned item is described with its fields`() {
        val described = read(Tools(diving()).describe("person"))
        val fields = (described.at("types", "0", "fields") as Stored.Elements).elements
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

class BriefingTest {

    private val briefing = Tools(diving()).briefing()

    @Test
    fun `the briefing opens with the instructions and lists every type with its fields`() {
        assertTrue(briefing.startsWith("# Working with this Yemoja logbook"))
        assertTrue(INSTRUCTIONS.trim() in briefing, "the rules come first")
        val types = Types.ALL.map { "\n### ${it.name}\n" }
        assertTrue(types.all { it in briefing }, "each type has a heading")
        assertTrue("- `max_depth`: number in m; worked out unless written" in briefing)
        assertTrue("- `buddies`: reference, list to a person" in briefing, "how many, and of what")
    }

    @Test
    fun `a block's fields are indented under it, and a worked out field says so`() {
        assertTrue("- `medical`: owned item\n  - `last_medical_check`: date\n" in briefing)
        assertTrue("  - `body_mass`: number in kg\n" in briefing)
        assertTrue("- `sac`: number, series in l/min; worked out, never written" in briefing)
    }
}

/*
 * Where the files are, told only while the user allows the agent at them. `API-5`.
 */
class FilesTest {

    @Test
    fun `the files are refused until the box is ticked, naming the box`() {
        val reason = reason(Tools(diving()).files())
        assertTrue("tick *Allow files*" in reason, reason)
        assertTrue("say why the tools were not enough" in reason, reason)
    }

    @Test
    fun `allowed, the folder is told with the format chapter and the rules`() {
        val store = MemoryFileStore(mapOf("dive/2026-06-01#0.json" to "{}"))
        val onDisk = Universe(LogbookReader.read(store, Types.ALL), null, store, "D:/dives/mine")
        val told = read(Tools(onDisk, direct = { true }).files())
        assertEquals("D:/dives/mine", told.leaf("folder"))
        assertEquals("yemoja://manual/data-format.md", told.leaf("format"))
        val rules = (told.at("rules") as Stored.Elements).elements.map { (it as Stored.Leaf).value }
        assertTrue(rules.any { "only for what they cannot" in it.toString() })
        assertTrue(rules.any { "reads the logbook again" in it.toString() })
    }

    @Test
    fun `a logbook held in memory has no files to tell of`() {
        val tools = Tools(diving(), direct = { true })
        assertEquals("this logbook is not on disk", reason(tools.files()))
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
    fun `a person is sent whole, private details and all`() {
        // Nothing is held back from an agent, and the manual says so rather than the code doing
        // it. `API-5`.
        val anna = read(Tools(diving()).get("anna"))
        assertEquals("Anna", anna.leaf("item", "name"))
        assertEquals("anna@example.invalid", anna.leaf("item", "email"))
        assertEquals(60L, anna.leaf("item", "medical", "body_mass"))
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
    fun `naming fields cuts each item down to them, and leaves out a field an item has not got`() {
        val tools = Tools(diving())
        val wanted = listOf("buddies", "rating", "gas_sources.g1.sac")
        val dive = read(tools.list("dive", fields = wanted)).at("items", "2026-06-01#0")
        dive as Stored.Members
        assertEquals(setOf("buddies", "gas_sources"), dive.members.keys, "no profiles, no rating")
        assertEquals(15.25, dive.leaf("gas_sources", "g1", "sac"))
        assertNull(dive.at("gas_sources", "g1", "gas_type"), "only the field asked for")
    }

    @Test
    fun `a whole block can be named, and no fields at all means whole items`() {
        val tools = Tools(diving())
        val block = read(tools.list("dive", fields = listOf("gas_sources")))
        assertEquals("EAN32", block.leaf("items", "2026-06-01#0", "gas_sources", "g1", "gas_type"))
        val whole = read(tools.list("dive")).at("items", "2026-06-01#0") as Stored.Members
        assertTrue("profiles" in whole.members.keys)
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
    fun `a figure may be taken over a person's private details like any other field`() {
        val allowed = read(Tools(diving()).aggregate(listOf("anna"), "medical.body_mass", "mean"))
        assertEquals(60L, allowed.leaf("value"))
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
        assertTrue("*Allow changes*" in said, "and names the box as it is labelled: $said")
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
