package yemoja.data.json

import yemoja.data.Stored
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Writing a Stored tree back out as JSON.
 *
 * See ../../../../../../doc.md — the source's own document is data/json/doc.md.
 */

/** Parse, write, and parse again: what a writer must never change is the meaning. */
private fun roundTrip(text: String): Stored = Json.parse(Json.write(Json.parse(text)))

class WrittenJsonTest {

    @Test
    fun `a group is a member to a line, two spaces in`() {
        assertEquals(
            """
            {
              "name": "North Sea",
              "category": "sea"
            }
            """.trimIndent(),
            Json.write(Json.parse("""{"name": "North Sea", "category": "sea"}""")),
        )
    }

    @Test
    fun `an empty group and an empty list are written on one line`() {
        assertEquals("""{"held": {}, "none": []}""".let {
            "{\n  \"held\": {},\n  \"none\": []\n}"
        }, Json.write(Json.parse("""{"held": {}, "none": []}""")))
    }

    @Test
    fun `a short list of plain values stays on its line`() {
        assertEquals(
            "{\n  \"tags\": [\"reef\", \"photo\"]\n}",
            Json.write(Json.parse("""{"tags": ["reef", "photo"]}""")),
        )
    }

    @Test
    fun `a long list of plain values takes a line each`() {
        val many = (1..12).joinToString(", ") { """"@a_rather_long_identifier_$it"""" }
        val written = Json.write(Json.parse("""{"items": [$many]}"""))
        // The brace, the name and its bracket, twelve elements, the bracket back, the brace back.
        assertEquals(16, written.lines().size, written)
        assertEquals(true, "\n    \"@a_rather_long_identifier_1\"," in written, written)
    }

    @Test
    fun `a list holding lists always takes a line each, however short`() {
        // A profile's samples. Each pair is plain and stays together; the run of them does not.
        assertEquals(
            "{\n  \"depth\": [\n    [0, 0],\n    [90, 8.6]\n  ]\n}",
            Json.write(Json.parse("""{"depth": [[0, 0], [90, 8.6]]}""")),
        )
    }

    @Test
    fun `a whole number and a fractional one keep the difference`() {
        // The reader tells them apart, so writing them alike would change a file nobody edited.
        val written = Json.write(Json.parse("""{"a": 18, "b": 18.0}"""))
        assertEquals("{\n  \"a\": 18,\n  \"b\": 18.0\n}", written)
    }

    @Test
    fun `no number is written with an exponent`() {
        // `DATA-88` refuses one, and Kotlin prints one for anything far enough from zero.
        assertEquals("2.0E7", 2.0E7.toString(), "the platform prints one, so this is worth doing")
        assertEquals("{\n  \"a\": 20000000.0,\n  \"b\": 0.0000456\n}",
            Json.write(Json.parse("""{"a": 2.0E7, "b": 4.56E-5}""")))
    }

    @Test
    fun `neither forgiveness the reader offers is written back`() {
        val text = "\uFEFF{\"a\": [1, 2,],}"
        assertEquals("{\n  \"a\": [1, 2]\n}", Json.write(Json.parse(text)))
    }

    @Test
    fun `what a string cannot carry raw is escaped`() {
        val written = Json.write(Json.parse(""" {"a": "one\ntwo\"three\\four"} """))
        assertEquals("{\n  \"a\": \"one\\ntwo\\\"three\\\\four\"\n}", written)
        assertEquals(Json.parse(""" {"a": "one\ntwo\"three\\four"} """), Json.parse(written))
    }

    @Test
    fun `a control character nothing spells is written as its code`() {
        assertEquals("{\n  \"a\": \"\\u0001\"\n}", Json.write(Json.parse("""{"a": "\u0001"}""")))
    }
}

/** What has to hold for every file: writing it changes nothing about what it says. */
class RoundTripTest {

    private val dive = """
        {
          "dive_number": 38,
          "dive_site": "@lantern_reef",
          "buddies": ["@jacques_cousteau"],
          "rating": 8,
          "details": {"tags": ["reef", "photo"], "dive_trip": "@provence_week"},
          "environment": {"visibility": 18, "remarks": "Flat calm.\nSand at twelve."},
          "profiles": {
            "p1": {
              "gmt_offset": 7200,
              "depth": [[0, 0], [90, 8.6], [2340, 0]],
              "pressures": {"g1": [[0, 205], [2340, 106]]}
            }
          }
        }
    """.trimIndent()

    @Test
    fun `a dive survives being written and read again`() {
        assertEquals(Json.parse(dive), roundTrip(dive))
    }

    @Test
    fun `writing twice changes nothing the second time`() {
        // What "no diff on save" rests on: the form is settled after one pass.
        val once = Json.write(Json.parse(dive))
        assertEquals(once, Json.write(Json.parse(once)))
    }

    @Test
    fun `a units block is carried like any other member`() {
        val text = """{"units": {"length": "ft"}, "a_dive": {"max_depth": 92}}"""
        assertEquals(Json.parse(text), roundTrip(text))
    }

    @Test
    fun `an unrecognised name survives, since nothing here knows what a field is`() {
        val text = """{"a": {"invented_by_a_later_version": {"deep": [1, 2]}}}"""
        assertEquals(Json.parse(text), roundTrip(text))
    }
}
