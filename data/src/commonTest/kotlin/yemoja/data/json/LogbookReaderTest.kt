package yemoja.data.json

import yemoja.data.Dimension
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.ItemSet
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Stored
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
        NumberDescription("height", Dimension.LENGTH),
        WholeNumberDescription("collections_per_day", range = 0..9),
        ReferenceDescription("round", targetType = "round"),
    ),
)

private val ROUND = ItemDescription("round", listOf(TextDescription("name")))

private val TYPES = listOf(POSTBOX, ROUND)

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), TYPES)

/** A logbook whose manifest declares [libraries] for `postbox`, unless [type] says otherwise. */
private fun withLibraries(
    files: Map<String, String>,
    libraries: List<String>,
    type: String = "postbox",
): ItemSet {
    val declared = libraries.joinToString(", ") { "\"$it\"" }
    val manifest = """{"libraries": {"$type": [$declared]}}"""
    return LogbookReader.read(MemoryFileStore(files + (LogbookReader.MANIFEST to manifest)), TYPES)
}

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

class ManifestTest {

    private val supplied = "libraries/postbox/supplied.json" to """{"almshouse": {}}"""

    private fun withManifest(manifest: String): ItemSet =
        LogbookReader.read(
            MemoryFileStore(mapOf(supplied, LogbookReader.MANIFEST to manifest)),
            TYPES,
        )

    @Test
    fun `the manifest says which libraries a logbook uses`() {
        val set = withManifest("""{"libraries": {"postbox": ["postbox/supplied"]}}""")
        assertEquals(1, set.size)
    }

    @Test
    fun `a library the manifest does not name is left alone, though it is there`() {
        assertEquals(0, withManifest("""{"libraries": {}}""").size)
    }

    @Test
    fun `the rest of the manifest is passed over`() {
        // The owner is declared and nothing reads it yet.
        val set = withManifest(
            """{"user": "@anna_devries", "libraries": {"postbox": ["postbox/supplied"]}}""",
        )
        assertEquals(1, set.size)
    }

    @Test
    fun `a manifest declaring no libraries at all reads them as none`() {
        assertEquals(0, withManifest("""{"user": "@anna_devries"}""").size)
    }

    @Test
    fun `a logbook without a manifest reads as one declaring nothing`() {
        // Whether the file is required has never been settled.
        assertEquals(0, logbook(supplied).size)
    }

    @Test
    fun `a manifest that is not JSON stops the read, and says which file`() {
        val refused = assertFailsWith<LogbookFormatException> { withManifest("{oh dear") }
        assertTrue(refused.message!!.startsWith("yemoja.json should be JSON"))
    }

    @Test
    fun `libraries that are not grouped by type are refused`() {
        val refused = assertFailsWith<LogbookFormatException> {
            withManifest("""{"libraries": ["postbox/supplied"]}""")
        }
        assertEquals(
            "libraries in yemoja.json should hold a list of names for each type",
            refused.message,
        )
    }

    @Test
    fun `a type naming one library rather than a list of them is refused`() {
        val refused = assertFailsWith<LogbookFormatException> {
            withManifest("""{"libraries": {"postbox": "postbox/supplied"}}""")
        }
        assertEquals(
            "libraries.postbox in yemoja.json should be a list of library names",
            refused.message,
        )
    }

    @Test
    fun `a library name that is not text is refused`() {
        val refused = assertFailsWith<LogbookFormatException> {
            withManifest("""{"libraries": {"postbox": [7]}}""")
        }
        assertEquals(
            "libraries.postbox in yemoja.json should be a list of library names",
            refused.message,
        )
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
            listOf("postbox/supplied"),
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
            listOf("postbox/supplied"),
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
            listOf("postbox/first", "postbox/second"),
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
            listOf("postbox/supplied"),
        )
        assertEquals(1, set.size)
        assertEquals("Mine", name(set, "almshouse"))
    }

    @Test
    fun `a library declared for one type does not reach another`() {
        val set = withLibraries(
            mapOf("libraries/round/supplied.json" to """{"tuesday": {"name": "Tuesday"}}"""),
            listOf("round/supplied"),
            "round",
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

/** `DATA-46`: `units` is a key, not an item and not a field. `DATA-87` for a bad one. */
class UnitsInAFileTest {

    private fun height(set: ItemSet, id: String): Double? =
        ((set[id]?.fields?.get("height")) as? Result.Usable)?.value as Double?

    @Test
    fun `a units block in a file of many items is not one of them`() {
        val set = logbook(
            "postbox.json" to
                """{"units": {"length": "ft"}, "market_square": {"height": 10.0}}""",
        )
        assertEquals(listOf("market_square"), set.allOf(POSTBOX).map { set.idOf(it) })
        assertEquals(3.048, height(set, "market_square"))
    }

    @Test
    fun `a units block in a file of one item is not one of its fields`() {
        val set = logbook(
            "postbox/market_square.json" to """{"units": {"length": "ft"}, "height": 10.0}""",
        )
        assertEquals(1, set.size)
        assertEquals(3.048, height(set, "market_square"))
        // Not kept as something the description did not name, either. DATA-65.
        assertEquals(emptySet(), set["market_square"]!!.unrecognisedFields.keys)
    }

    @Test
    fun `a declaration reaches its own file and no other`() {
        val set = logbook(
            "postbox/in_feet.json" to """{"units": {"length": "ft"}, "height": 10.0}""",
            "postbox/in_metres.json" to """{"height": 10.0}""",
        )
        assertEquals(3.048, height(set, "in_feet"))
        assertEquals(10.0, height(set, "in_metres"))
    }

    @Test
    fun `a library declares its own units, and its items are read in them`() {
        // Every supplied library file carries one, which is why this had to work.
        val set = withLibraries(
            mapOf(
                "libraries/postbox/supplied.json" to
                    """{"units": {"length": "ft"}, "almshouse": {"height": 10.0}}""",
            ),
            listOf("postbox/supplied"),
        )
        assertEquals(listOf("almshouse"), set.allOf(POSTBOX).map { set.idOf(it) })
        assertEquals(3.048, height(set, "almshouse"))
    }

    @Test
    fun `a unit nobody knows costs that dimension and leaves the file open`() {
        val set = logbook(
            "postbox.json" to """{
                "units": {"length": "fathom"},
                "market_square": {
                    "name": "Market Square", "height": 10.0, "collections_per_day": 2
                }
            }""",
        )
        val item = set["market_square"]!!
        val refused = item.fields.getValue("height")
        assertTrue(refused is Result.Unusable)
        assertEquals("height cannot be read: fathom is not a unit of length", refused.reason)
        assertEquals(Stored.Leaf(10.0), refused.raw)
        // The rest of the item reads normally.
        assertEquals("Market Square", name(set, "market_square"))
        assertEquals(2, (item.fields.getValue("collections_per_day") as Result.Usable).value)
    }

    @Test
    fun `a units block that is not a set of names stops the read`() {
        val refused = assertFailsWith<LogbookFormatException> {
            logbook("postbox.json" to """{"units": "ft"}""")
        }
        assertEquals(
            "units in postbox.json should name a unit for each dimension",
            refused.message,
        )
    }
}
