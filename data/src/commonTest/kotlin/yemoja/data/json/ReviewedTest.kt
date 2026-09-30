package yemoja.data.json

import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.NumberDescription
import yemoja.data.Dimension
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.TextDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What a review of the data layer found, each held to the behaviour it should have had.
 * See ../../../../../../json/doc.md.
 */

private val DEPTH = ItemDescription(
    "spot",
    listOf(TextDescription("name"), NumberDescription("max_depth", Dimension.LENGTH)),
)

class ReviewedTest {

    @Test
    fun `a quoted NaN or Infinity is not a number, and goes back to the file as written`() {
        val store = MemoryFileStore(mapOf("spot.json" to """{"a": {"name": "A", "max_depth": "NaN"}}"""))
        val set = LogbookReader.read(store, listOf(DEPTH))
        assertIs<Result.Unusable>(set["a"]!!.read("max_depth"), "unusable, not a depth of NaN")
        set["a"]!!.write("name", "B")
        LogbookWriter.write(store, DEPTH, "a", set["a"]!!)
        assertTrue("\"max_depth\": \"NaN\"" in store.readText("spot.json"), store.readText("spot.json"))
    }

    @Test
    fun `a small number is written without a nought its mantissa never had`() {
        assertEquals("""{
  "a": 0.0001
}""", Json.write(Json.parse("""{"a": 0.0001}""")))
        assertEquals(0.0001, (Json.parse(Json.write(Stored.Leaf(0.0001))) as Stored.Leaf).value)
    }

    @Test
    fun `half a surrogate pair is escaped, and reads back as itself`() {
        val alone = "a\uD83Db"
        val written = Json.write(Stored.Leaf(alone))
        assertTrue("\\ud83d" in written, written)
        assertEquals(alone, (Json.parse(written) as Stored.Leaf).value)
        val pair = "😀"
        assertEquals("\"$pair\"", Json.write(Stored.Leaf(pair)), "a whole pair is written as it is")
    }

    @Test
    fun `a logbook's file ends with a newline, as a hand-written one does`() {
        val store = MemoryFileStore(mapOf("spot.json" to "{\"a\": {\"name\": \"A\"}}\n"))
        val set = LogbookReader.read(store, listOf(DEPTH))
        set["a"]!!.write("name", "B")
        LogbookWriter.write(store, DEPTH, "a", set["a"]!!)
        assertTrue(store.readText("spot.json").endsWith("}\n"))
    }

    @Test
    fun `an id a file cannot hold is the file's fault, said as one`() {
        val store = MemoryFileStore(mapOf("spot/a b.json" to """{"name": "A"}"""))
        val refused = assertFailsWith<LogbookFormatException> { LogbookReader.read(store, listOf(DEPTH)) }
        assertTrue("a b" in refused.message.orEmpty(), refused.message)
    }

    @Test
    fun `choosing another user moves the set's revision`() {
        val set = ItemSet(listOf(DEPTH))
        val before = set.revision
        set.user = Reference.Identified("anna")
        assertTrue(set.revision > before, "what is derived from the user reads again")
    }
}
