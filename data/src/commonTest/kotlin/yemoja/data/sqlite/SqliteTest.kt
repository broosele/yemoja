package yemoja.data.sqlite

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A SQLite database read table by table. See ../../../../../../doc.md — `DATA-130`.
 *
 * The database is written by SQLite itself, through tool/sqlitefixture.py, which says what it holds.
 */

@OptIn(ExperimentalEncodingApi::class)
private val DATABASE: SqliteFile by lazy { SqliteFile(Base64.decode(FIXTURE)) }

class SqliteTest {

    private fun rows(table: String): List<Map<String, Any?>> = DATABASE.rows(table)!!

    @Test
    fun `every table is listed, and SQLite's own and the indexes are not`() {
        assertEquals(listOf("kinds", "lines", "altered", "odd table", "keyed"), DATABASE.tables)
    }

    @Test
    fun `each kind of value reads as what SQLite stored`() {
        val first = rows("kinds").first()
        assertEquals(1L, first["id"], "the id column is the row's id, which the row does not store")
        assertEquals(7L, first["small"])
        assertEquals(1L shl 40, first["big"])
        assertEquals(1.5, first["real"])
        assertEquals("Plongée à 40 m", first["text"], "UTF-8, accents included")
        assertContentEquals(byteArrayOf(0, -1), first["blob"] as ByteArray)
        assertNull(first["absent"])
        assertTrue("absent" in first, "a null is a value of the column, not a missing column")
    }

    @Test
    fun `negative numbers, nought and one, and every width SQLite stores keep their sign and size`() {
        val (_, second, third, fourth) = rows("kinds")
        assertEquals(-3L, second["small"])
        assertEquals(-(1L shl 62), second["big"])
        assertEquals(-0.25, second["real"])
        assertEquals("", second["text"])
        assertContentEquals(byteArrayOf(), second["blob"] as ByteArray)
        assertEquals(0L, third["small"], "nought, which SQLite writes as a type with no bytes")
        assertEquals(1L, third["big"], "and one likewise")
        assertEquals(1e100, third["real"])
        assertEquals(300L, fourth["small"])
        assertEquals(-70000L, fourth["big"])
    }

    @Test
    fun `a table spread over many pages reads whole, in the order of its ids`() {
        val lines = rows("lines")
        assertEquals(301, lines.size)
        assertEquals((1L..301L).toList(), lines.map { it["n"] })
        assertEquals("line 150", lines[149]["label"])
    }

    @Test
    fun `a row too long for its page is gathered from its overflow pages`() {
        val long = rows("lines").last()["label"] as String
        assertEquals("long " + "abcdefghij".repeat(300), long)
    }

    @Test
    fun `a column added after a row was written reads as null in that row`() {
        val (before, after) = rows("altered")
        assertEquals("before", before["a"])
        assertNull(before["b"], "the default the column was added with is not read")
        assertEquals(9L, after["b"])
    }

    @Test
    fun `names in quotes are read without them, and a constraint is not a column`() {
        assertEquals(listOf("first col", "second", "third"), DATABASE.columns("odd table"))
        val row = rows("odd table").single()
        assertEquals("one, two", row["first col"], "a comma inside a value is not a column's end")
        assertEquals(2L, row["second"])
        assertEquals(3.25, row["third"])
    }

    @Test
    fun `a table without row ids is named but refused when read`() {
        val refused = assertFailsWith<SqliteFormatException> { DATABASE.rows("keyed") }
        assertTrue("WITHOUT ROWID" in refused.message!!, refused.message)
    }

    @Test
    fun `a table that is not there has neither columns nor rows`() {
        assertNull(DATABASE.columns("dives"))
        assertNull(DATABASE.rows("dives"))
    }

    @Test
    fun `a file is known by its first bytes, and one that is not SQLite is refused`() {
        @OptIn(ExperimentalEncodingApi::class)
        assertTrue(isSqlite(Base64.decode(FIXTURE)))
        val text = "<uddf version=\"3.2.3\"/>".encodeToByteArray()
        assertTrue(!isSqlite(text))
        assertEquals(
            "this is not a SQLite database",
            assertFailsWith<SqliteFormatException> { SqliteFile(text) }.message,
        )
    }

    @Test
    fun `a file cut short is refused with where it broke, not an index out of bounds`() {
        @OptIn(ExperimentalEncodingApi::class)
        val whole = Base64.decode(FIXTURE)
        val cut = whole.copyOf(whole.size / 2)
        val refused = assertFailsWith<SqliteFormatException> { SqliteFile(cut).rows("lines") }
        assertTrue("page" in refused.message!!, refused.message)
    }
}
