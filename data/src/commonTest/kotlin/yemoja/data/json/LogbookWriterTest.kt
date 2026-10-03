package yemoja.data.json

import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.NumberDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.TextDescription
import yemoja.data.WholeNumberDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Putting an item back into a logbook's files.
 *
 * See ../../../../../../doc.md — the source's own document is data/json/doc.md.
 */

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

/** A store over these files, and the logbook read out of it. */
private fun opened(vararg files: Pair<String, String>): Pair<MemoryFileStore, ItemSet> {
    val store = MemoryFileStore(mapOf(*files))
    return store to LogbookReader.read(store, TYPES)
}

private fun text(set: ItemSet, id: String, field: String): String? =
    (set[id]?.single<String>(field) as? Result.Usable)?.value

private fun number(set: ItemSet, id: String, field: String): Double? =
    (set[id]?.single<Double>(field) as? Result.Usable)?.value

class WrittenItemTest {

    @Test
    fun `an item written back reads the same`() {
        val (store, set) = opened("postbox.json" to
            """{"market_square": {"name": "Market Square", "height": 1.2, "round": "@north"}}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        val again = LogbookReader.read(store, TYPES)
        assertEquals("Market Square", text(again, "market_square", "name"))
        assertEquals(1.2, number(again, "market_square", "height"))
        assertEquals("@north", (again["market_square"]!!.read("round") as Result.Usable)
            .value.toString())
    }

    @Test
    fun `the other items in a shared file are left alone`() {
        val (store, set) = opened("postbox.json" to """{
            "market_square": {"name": "Market Square"},
            "station": {"name": "Station"}
        }""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        val again = LogbookReader.read(store, TYPES)
        assertEquals("Station", text(again, "station", "name"))
    }

    @Test
    fun `a file of its own is written where the type is a folder`() {
        val (store, set) = opened("postbox/market_square.json" to """{"name": "Market Square"}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        assertTrue(store.isFile("postbox/market_square.json"))
        assertTrue(!store.isFile("postbox.json"), "the grouped file is not made as well")
    }

    @Test
    fun `a type stored neither way yet is written grouped`() {
        // A logbook with three of something should not be a folder of three files, and JSON-21
        // lets a user split it by hand afterwards.
        val (_, made) = opened("round.json" to """{"north": {"name": "North"}}""")
        val (store, _) = opened("postbox.json" to """{"market_square": {"name": "Square"}}""")
        LogbookWriter.write(store, ROUND, "north", made["north"]!!)
        assertTrue(store.isFile("round.json"), "grouped")
        assertTrue(!store.isFolder("round"), "and not a folder of one")
        assertEquals("North", text(LogbookReader.read(store, TYPES), "north", "name"))
    }

    @Test
    fun `a value that would not read is written back as it came`() {
        // `DATA-50` keeps the raw so an interface can show it; losing it on a save would be the
        // application deciding that what it cannot use is not worth keeping.
        val (store, set) = opened("postbox.json" to
            """{"market_square": {"height": "waist high"}}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        val read = LogbookReader.read(store, TYPES)["market_square"]!!.read("height")
        assertTrue(read is Result.Unusable, read.toString())
        assertTrue("waist high" in store.readText("postbox.json"), store.readText("postbox.json"))
    }

    @Test
    fun `a name this version does not recognise survives`() {
        val (store, set) = opened("postbox.json" to
            """{"market_square": {"name": "Square", "invented_later": {"deep": [1, 2]}}}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        assertTrue("invented_later" in store.readText("postbox.json"))
        assertEquals("Square", text(LogbookReader.read(store, TYPES), "market_square", "name"))
    }

    @Test
    fun `nothing is written for a field the item does not hold`() {
        val (store, set) = opened("postbox.json" to """{"market_square": {"name": "Square"}}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        assertTrue("height" !in store.readText("postbox.json"), store.readText("postbox.json"))
    }
}

/** What a file says its numbers are in, which nothing above the reader keeps. `DATA-76`. */
class WrittenUnitsTest {

    @Test
    fun `a logbook written in feet stays in feet`() {
        val (store, set) = opened("postbox.json" to
            """{"units": {"length": "ft"}, "market_square": {"height": 4}}""")
        assertEquals(1.2192, number(set, "market_square", "height"))
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        val written = store.readText("postbox.json")
        assertTrue(""""height": 4""" in written, written)
        assertTrue(""""ft"""" in written, "the declaration is kept with the file it belongs to")
    }

    @Test
    fun `the declaration keeps its place at the head of the file`() {
        val (store, set) = opened("postbox.json" to
            """{"units": {"length": "ft"}, "market_square": {"height": 4}}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        assertTrue(store.readText("postbox.json").startsWith("{\n  \"units\""))
    }

    @Test
    fun `a file of its own carries its own declaration`() {
        val (store, set) = opened("postbox/market_square.json" to
            """{"units": {"length": "ft"}, "height": 4}""")
        LogbookWriter.write(store, POSTBOX, "market_square", set["market_square"]!!)
        val written = store.readText("postbox/market_square.json")
        assertTrue(""""height": 4""" in written, written)
        assertEquals(1.2192, number(LogbookReader.read(store, TYPES), "market_square", "height"))
    }
}

/** A library belongs to the installation, and never lands in somebody's logbook. */
class WrittenLibraryTest {

    private fun withLibrary(): Pair<MemoryFileStore, ItemSet> {
        val store = MemoryFileStore(mapOf(
            "yemoja.json" to """{"libraries": {"round": ["round/every"]}}""",
            "round.json" to """{"north": {"name": "North"}}""",
            "libraries/round/every.json" to """{"south": {"name": "South"}}""",
        ))
        return store to LogbookReader.read(store, TYPES)
    }

    @Test
    fun `a supplied item is in the set and is not the logbook's to write`() {
        val (store, set) = withLibrary()
        assertEquals("South", text(set, "south", "name"), "it is read")
        LogbookWriter.write(store, ROUND, "north", set["north"]!!)
        assertTrue("south" !in store.readText("round.json"), store.readText("round.json"))
    }

    @Test
    fun `the library file itself is untouched`() {
        val (store, set) = withLibrary()
        LogbookWriter.write(store, ROUND, "north", set["north"]!!)
        assertEquals("""{"south": {"name": "South"}}""",
            store.readText("libraries/round/every.json"))
    }
}

/** Taking an item out. */
class DeletedItemTest {

    @Test
    fun `a member of a shared file goes and the rest stays`() {
        val (store, _) = opened("postbox.json" to """{
            "market_square": {"name": "Market Square"},
            "station": {"name": "Station"}
        }""")
        LogbookWriter.delete(store, POSTBOX, "market_square")
        val again = LogbookReader.read(store, TYPES)
        assertNull(again["market_square"])
        assertEquals("Station", text(again, "station", "name"))
    }

    @Test
    fun `a file of its own is removed`() {
        val (store, _) = opened("postbox/market_square.json" to """{"name": "Square"}""")
        LogbookWriter.delete(store, POSTBOX, "market_square")
        assertTrue(!store.isFile("postbox/market_square.json"))
    }

    @Test
    fun `deleting what is not there is not a fault`() {
        val (store, _) = opened("postbox.json" to """{"station": {"name": "Station"}}""")
        LogbookWriter.delete(store, POSTBOX, "market_square")
        LogbookWriter.delete(store, ROUND, "nowhere")
        assertEquals("Station", text(LogbookReader.read(store, TYPES), "station", "name"))
    }

    @Test
    fun `an emptied file is left, being a logbook with none of that type`() {
        val (store, _) = opened("postbox.json" to """{"market_square": {"name": "Square"}}""")
        LogbookWriter.delete(store, POSTBOX, "market_square")
        assertTrue(store.isFile("postbox.json"))
        assertEquals(0, LogbookReader.read(store, TYPES).size)
    }
}

/*
 * Many items put into a logbook in one pass.
 */
class WrittenTogetherTest {

    /** A store that counts how often each file is written. */
    private class Counting(files: Map<String, String>) : FileStore {
        val writes = HashMap<String, Int>()
        private val inner = MemoryFileStore(files)
        override fun isFile(path: String): Boolean = inner.isFile(path)
        override fun isFolder(path: String): Boolean = inner.isFolder(path)
        override fun namesIn(path: String): List<String> = inner.namesIn(path)
        override fun readText(path: String): String = inner.readText(path)
        override fun writeText(path: String, text: String) {
            writes[path] = (writes[path] ?: 0) + 1
            inner.writeText(path, text)
        }
        override fun delete(path: String) = inner.delete(path)
    }

    private val set = opened(
        "postbox.json" to """{"a": {"name": "A"}, "b": {"name": "B"}, "c": {"name": "C"}}""",
    ).second

    private val items: List<Pair<String, Item>> = listOf("a", "b", "c").map { it to set[it]!! }

    @Test
    fun `a type kept in one file is written once, however many items go into it`() {
        val store = Counting(mapOf("postbox.json" to """{"z": {"name": "Z"}}"""))
        var told = 0
        LogbookWriter.writeAll(store, POSTBOX, items) { told++ }
        assertEquals(mapOf("postbox.json" to 1), store.writes)
        assertEquals(3, told, "told once an item")
        val again = LogbookReader.read(store, TYPES)
        assertEquals(listOf("A", "B", "C", "Z"), listOf("a", "b", "c", "z").map { text(again, it, "name") })
    }

    @Test
    fun `apart, each item is a file of its own, and reads back the same`() {
        val store = Counting(emptyMap())
        LogbookWriter.writeAll(store, POSTBOX, items, apart = true)
        assertEquals(setOf("postbox/a.json", "postbox/b.json", "postbox/c.json"), store.writes.keys)
        val again = LogbookReader.read(store, TYPES)
        assertEquals("B", text(again, "b", "name"))
    }

    @Test
    fun `apart does not split a type that already has a file`() {
        val store = Counting(mapOf("postbox.json" to """{"z": {"name": "Z"}}"""))
        LogbookWriter.writeAll(store, POSTBOX, items, apart = true)
        assertEquals(mapOf("postbox.json" to 1), store.writes)
    }
}
