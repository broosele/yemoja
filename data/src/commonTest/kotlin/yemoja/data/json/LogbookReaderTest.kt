package yemoja.data.json

import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.TextDescription
import yemoja.data.WholeNumberDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Invented types again: nothing in this layer knows what a dive is. `TEST-4`. */
private val POSTBOX = ItemDescription(
    "postbox",
    listOf(
        TextDescription("name"),
        WholeNumberDescription("collections_per_day", range = 0..9),
        ReferenceDescription("round", targetType = "round"),
    ),
)

private val ROUND = ItemDescription("round", listOf(TextDescription("name")))

private val TYPES = listOf(POSTBOX, ROUND)

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), TYPES)

private fun withLibraries(
    files: Map<String, String>,
    libraries: Map<String, List<String>>,
): ItemSet = LogbookReader.read(MemoryFileStore(files, libraries), TYPES)

private fun name(set: ItemSet, id: String): Any? =
    (set[id]?.fields?.get("name") as? Result.Usable)?.value

class OnePerFileTest {

    @Test
    fun `a folder holds one item per file, and the file name is the id`() {
        val logbook = logbook(
            "postbox/market_square.json" to """{"name": "Market Square"}""",
            "postbox/station.json" to """{"name": "Station"}""",
        )
        assertEquals(2, logbook.size)
        assertEquals("Market Square", name(logbook, "market_square"))
        assertEquals("Station", name(logbook, "station"))
    }

    @Test
    fun `items arrive in a settled order whatever the folder listed`() {
        // Two machines reading the same logbook should hold it the same way.
        val logbook = logbook(
            "postbox/station.json" to "{}",
            "postbox/market_square.json" to "{}",
            "postbox/almshouse.json" to "{}",
        )
        assertEquals(
            listOf("almshouse", "market_square", "station"),
            logbook.allOf(POSTBOX).map { logbook.idOf(it) },
        )
    }

    @Test
    fun `a file that is not JSON is passed over rather than complained about`() {
        // A logbook is a folder someone else may also keep things in.
        val logbook = logbook(
            "postbox/market_square.json" to "{}",
            "postbox/notes.txt" to "not json at all",
        )
        assertEquals(1, logbook.size)
    }
}

class GroupedTest {

    @Test
    fun `one file holds every item of a type, each under its id`() {
        val logbook = logbook(
            "postbox.json" to """{"market_square": {"name": "Market Square"}, "station": {}}""",
        )
        assertEquals(2, logbook.size)
        assertEquals("Market Square", name(logbook, "market_square"))
    }

    @Test
    fun `the order the file was written in is kept`() {
        val logbook = logbook("postbox.json" to """{"station": {}, "almshouse": {}}""")
        assertEquals(
            listOf("station", "almshouse"),
            logbook.allOf(POSTBOX).map { logbook.idOf(it) },
        )
    }

    @Test
    fun `an entry that is not a set of fields stops the read`() {
        val refused = assertFailsWith<LogbookFormatException> {
            logbook("postbox.json" to """{"market_square": "just a name"}""")
        }
        assertEquals("market_square in postbox.json should be a set of fields", refused.message)
    }
}

class LogbookShapeTest {

    @Test
    fun `each type is stored one way or the other, and either is read`() {
        val logbook = logbook(
            "postbox/market_square.json" to "{}",
            "round.json" to """{"tuesday": {"name": "Tuesday"}}""",
        )
        assertEquals(2, logbook.size)
        assertEquals("Tuesday", name(logbook, "tuesday"))
    }

    @Test
    fun `a type stored both ways at once is refused rather than chosen between`() {
        val refused = assertFailsWith<LogbookFormatException> {
            logbook("postbox/market_square.json" to "{}", "postbox.json" to "{}")
        }
        assertTrue(refused.message!!.contains("only one of them can be the postbox"))
    }

    @Test
    fun `a type with neither a folder nor a file contributes nothing`() {
        val logbook = logbook("postbox.json" to """{"market_square": {}}""")
        assertEquals(1, logbook.size)
        assertEquals(emptyList(), logbook.allOf(ROUND))
    }

    @Test
    fun `an empty logbook reads as an empty set`() {
        assertEquals(0, logbook().size)
    }

    @Test
    fun `a file that is not JSON stops the read, and says which file`() {
        val refused = assertFailsWith<LogbookFormatException> {
            logbook("postbox/market_square.json" to "{oh dear")
        }
        assertTrue(refused.message!!.startsWith("postbox/market_square.json should be JSON"))
    }

    @Test
    fun `a file holding something other than a set of fields stops the read`() {
        val refused = assertFailsWith<LogbookFormatException> {
            logbook("postbox.json" to """["market_square"]""")
        }
        assertEquals("postbox.json should hold a set of fields", refused.message)
    }
}

class LibraryTest {

    @Test
    fun `a declared library is read alongside the logbook`() {
        val set = withLibraries(
            mapOf(
                "postbox.json" to """{"market_square": {"name": "Market Square"}}""",
                "libraries/postbox/supplied.json" to """{"almshouse": {"name": "Almshouse"}}""",
            ),
            mapOf("postbox" to listOf("postbox/supplied")),
        )
        assertEquals(2, set.size)
        assertEquals("Almshouse", name(set, "almshouse"))
    }

    @Test
    fun `the logbook shadows a library holding the same id`() {
        val set = withLibraries(
            mapOf(
                "postbox.json" to """{"almshouse": {"name": "Mine"}}""",
                "libraries/postbox/supplied.json" to """{"almshouse": {"name": "Theirs"}}""",
            ),
            mapOf("postbox" to listOf("postbox/supplied")),
        )
        assertEquals(1, set.size)
        assertEquals("Mine", name(set, "almshouse"))
    }

    @Test
    fun `the library declared first shadows one declared after it`() {
        val set = withLibraries(
            mapOf(
                "libraries/postbox/first.json" to """{"almshouse": {"name": "First"}}""",
                "libraries/postbox/second.json" to """{"almshouse": {"name": "Second"}}""",
            ),
            mapOf("postbox" to listOf("postbox/first", "postbox/second")),
        )
        assertEquals(1, set.size)
        assertEquals("First", name(set, "almshouse"))
    }

    @Test
    fun `a logbook file in a folder shadows a library too`() {
        val set = withLibraries(
            mapOf(
                "postbox/almshouse.json" to """{"name": "Mine"}""",
                "libraries/postbox/supplied.json" to """{"almshouse": {"name": "Theirs"}}""",
            ),
            mapOf("postbox" to listOf("postbox/supplied")),
        )
        assertEquals(1, set.size)
        assertEquals("Mine", name(set, "almshouse"))
    }

    @Test
    fun `a library declared for one type does not reach another`() {
        val set = withLibraries(
            mapOf("libraries/round/supplied.json" to """{"tuesday": {"name": "Tuesday"}}"""),
            mapOf("round" to listOf("round/supplied")),
        )
        assertEquals(1, set.size)
        assertEquals(listOf("tuesday"), set.allOf(ROUND).map { set.idOf(it) })
        assertEquals(emptyList(), set.allOf(POSTBOX))
    }
}

class LogbookItemTest {

    @Test
    fun `an item is read against its description`() {
        val stored = """{"market_square": {"collections_per_day": 2, "colour": "red"}}"""
        val logbook = logbook("postbox.json" to stored)
        val item = logbook["market_square"]!!
        assertEquals(2, (item.fields.getValue("collections_per_day") as Result.Usable).value)
        // And a name the description does not carry is kept apart. DATA-65.
        assertEquals(setOf("colour"), item.unrecognisedFields.keys)
    }

    @Test
    fun `an item can reach the set it was read into`() {
        val logbook = logbook(
            "postbox.json" to """{"market_square": {"round": "@tuesday"}}""",
            "round.json" to """{"tuesday": {}}""",
        )
        val postbox = logbook["market_square"]!!
        assertEquals(logbook, postbox.set)
        assertEquals("tuesday", logbook.idOf(logbook["tuesday"]!!))
    }

    @Test
    fun `one id may not name two items, whatever their types`() {
        assertFailsWith<IllegalArgumentException> {
            logbook(
                "postbox.json" to """{"tuesday": {}}""",
                "round.json" to """{"tuesday": {}}""",
            )
        }
    }

    @Test
    fun `an id nothing wrote is not there`() {
        val logbook = logbook("postbox.json" to """{"market_square": {}}""")
        assertNull(logbook["station"])
    }

    @Test
    fun `a field that cannot be read is kept and the item is still there`() {
        val logbook = logbook(
            "postbox.json" to """{"market_square": {"collections_per_day": 99}}""",
        )
        val item = logbook["market_square"]!!
        val read = item.fields.getValue("collections_per_day")
        assertTrue(read is Result.Unusable)
        assertEquals(1, logbook.size)
    }
}
