package yemoja.data.json

import yemoja.data.Stored

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The document is wrapped in an object or an array throughout, so a raw string never begins or
 * ends with a quote. Inside a raw string a backslash stays a backslash, which is what a JSON
 * escape is, so the input reads as it would in a file.
 */
private fun one(document: String): Stored =
    (Json.parse(document) as Stored.Members).members.getValue("a")

private fun members(document: String): Map<String, Stored> =
    (Json.parse(document) as Stored.Members).members

private fun elements(document: String): List<Stored> =
    (Json.parse(document) as Stored.Elements).elements

private fun refused(document: String): String =
    assertFailsWith<JsonFormatException> { Json.parse(document) }.message ?: ""

class JsonTextTest {

    @Test
    fun `a string reads as its text`() {
        assertEquals(Stored.Leaf("Zeelandbrug"), one("""{"a": "Zeelandbrug"}"""))
        assertEquals(Stored.Leaf(""), one("""{"a": ""}"""))
    }

    @Test
    fun `every escape is resolved`() {
        assertEquals(Stored.Leaf("\""), one("""{"a": "\""}"""))
        assertEquals(Stored.Leaf("\\"), one("""{"a": "\\"}"""))
        assertEquals(Stored.Leaf("/"), one("""{"a": "\/"}"""))
        assertEquals(Stored.Leaf("\u0008"), one("""{"a": "\b"}"""))
        assertEquals(Stored.Leaf("\u000C"), one("""{"a": "\f"}"""))
        assertEquals(Stored.Leaf("\n"), one("""{"a": "\n"}"""))
        assertEquals(Stored.Leaf("\r"), one("""{"a": "\r"}"""))
        assertEquals(Stored.Leaf("\t"), one("""{"a": "\t"}"""))
        assertEquals(Stored.Leaf("é"), one("""{"a": "é"}"""))
    }

    @Test
    fun `a character outside the basic plane survives as its two halves`() {
        // A Kotlin string is UTF-16, so the pair needs no joining.
        val read = one("""{"a": "🐟"}""")
        assertEquals(Stored.Leaf("🐟"), read)
        assertEquals(2, ((read as Stored.Leaf).value as String).length)
    }

    @Test
    fun `a hexadecimal escape reads as its character`() {
        assertEquals(Stored.Leaf("A"), one("""{"a": "\u0041"}"""))
        assertEquals(Stored.Leaf("\u00E9"), one("""{"a": "\u00e9"}"""))
        assertEquals(Stored.Leaf("\u00E9"), one("""{"a": "\u00E9"}"""))
        assertEquals(Stored.Leaf("\u2028"), one("""{"a": "\u2028"}"""))
    }

    @Test
    fun `an escaped surrogate pair joins itself`() {
        val read = one("""{"a": "\uD83D\uDC1F"}""")
        assertEquals(Stored.Leaf("\uD83D\uDC1F"), read)
    }

    @Test
    fun `only the digits the grammar allows are digits`() {
        // Kotlin's digitToInt knows more digits than JSON does.
        assertTrue(refused("""{"a": "\u٠٠٤١"}""").contains("hexadecimal"))
    }

    @Test
    fun `a string is closed, and holds no control character`() {
        assertTrue(refused("""{"a": "unfinished}""").startsWith("a string should be closed"))
        assertTrue(refused("""{"a": "\q"}""").contains("should not follow a backslash"))
        assertTrue(refused("""{"a": "\u00g0"}""").contains("hexadecimal"))
        assertTrue(refused("{\"a\": \"two\nlines\"}").contains("control character"))
    }
}

class JsonNumberTest {

    @Test
    fun `a number with no point and no exponent is whole`() {
        assertEquals(Stored.Leaf(7L), one("""{"a": 7}"""))
        assertEquals(Stored.Leaf(-7L), one("""{"a": -7}"""))
        assertEquals(Stored.Leaf(0L), one("""{"a": 0}"""))
        assertEquals(Stored.Leaf(108000L), one("""{"a": 108000}"""))
    }

    @Test
    fun `a number with a point or an exponent is a fraction`() {
        assertEquals(Stored.Leaf(11.8), one("""{"a": 11.8}"""))
        assertEquals(Stored.Leaf(-0.5), one("""{"a": -0.5}"""))
        assertEquals(Stored.Leaf(1000.0), one("""{"a": 1e3}"""))
        assertEquals(Stored.Leaf(0.0015), one("""{"a": 1.5E-3}"""))
    }

    @Test
    fun `seven and seven point zero are told apart`() {
        // The whole point of the split: a whole number field refuses 7.0 and a number takes 7.
        assertIs<Stored.Leaf>(one("""{"a": 7}"""))
        assertIs<Stored.Leaf>(one("""{"a": 7.0}"""))
    }

    @Test
    fun `a number is written the way the grammar says`() {
        assertTrue(refused("""{"a": 01}""").contains("should not begin with a zero"))
        assertTrue(refused("""{"a": -}""").contains("should have a digit"))
        assertTrue(refused("""{"a": 1.}""").contains("should have a digit"))
        assertTrue(refused("""{"a": 1e}""").contains("should have a digit"))
        assertTrue(refused("""{"a": +1}""").contains("a value should begin with"))
    }

    @Test
    fun `a number too large to hold is refused rather than rounded`() {
        val huge = "9".repeat(30)
        assertTrue(refused("""{"a": $huge}""").contains("whole number this machine can hold"))
        assertTrue(refused("""{"a": 1e400}""").contains("number this machine can hold"))
    }
}

class JsonTruthTest {

    @Test
    fun `true and false read as themselves`() {
        assertEquals(Stored.Leaf(true), one("""{"a": true}"""))
        assertEquals(Stored.Leaf(false), one("""{"a": false}"""))
    }

    @Test
    fun `null reads as null, for something else to judge`() {
        // A reader does not know what a field is, so it carries a null rather than refusing it.
        assertEquals(Stored.Leaf(null), one("""{"a": null}"""))
        assertEquals(listOf(Stored.Leaf(null), Stored.Leaf(1L)), elements("""[null, 1]"""))
    }

    @Test
    fun `a half-written word is not a value`() {
        assertTrue(refused("""{"a": tru}""").contains("a value should begin with"))
        assertTrue(refused("""{"a": TRUE}""").contains("a value should begin with"))
        assertTrue(refused("""{"a": nul}""").contains("a value should begin with"))
        assertTrue(refused("""{"a": NULL}""").contains("a value should begin with"))
    }
}

class JsonMembersTest {

    @Test
    fun `an object reads its members in the order the file wrote them`() {
        val read = members("""{"name": "Anna", "rating": 7, "deco": false}""")
        assertEquals(listOf("name", "rating", "deco"), read.keys.toList())
        assertEquals(Stored.Leaf("Anna"), read["name"])
        assertEquals(Stored.Leaf(7L), read["rating"])
        assertEquals(Stored.Leaf(false), read["deco"])
    }

    @Test
    fun `an empty object is an object`() {
        assertEquals(Stored.Members(emptyMap()), Json.parse("{}"))
        assertEquals(Stored.Members(emptyMap()), Json.parse("  {  }  "))
    }

    @Test
    fun `one name may not be given twice`() {
        assertTrue(refused("""{"a": 1, "a": 2}""").contains("names two"))
    }

    @Test
    fun `an object is written the way the grammar says`() {
        assertTrue(refused("""{a: 1}""").contains("a name should be in quotes"))
        assertTrue(refused("""{"a" 1}""").contains("followed by a colon"))
        assertTrue(refused("""{"a": 1 "b": 2}""").contains("comma or a closing brace"))
        assertTrue(refused("""{"a": 1""").contains("the text ended"))
    }
}

class JsonElementsTest {

    @Test
    fun `an array reads its elements in order`() {
        val read = elements("""["@anna_devries", "john"]""")
        assertEquals(listOf(Stored.Leaf("@anna_devries"), Stored.Leaf("john")), read)
    }

    @Test
    fun `an empty array is an array`() {
        assertEquals(Stored.Elements(emptyList()), Json.parse("[]"))
    }

    @Test
    fun `an array holds whatever a value may be`() {
        val read = elements("""[[0, 0], [30, 8.4]]""")
        assertEquals(2, read.size)
        assertEquals(listOf(Stored.Leaf(30L), Stored.Leaf(8.4)),
            (read[1] as Stored.Elements).elements)
    }

    @Test
    fun `an array is written the way the grammar says`() {
        assertTrue(refused("""[1 2]""").contains("comma or a closing bracket"))
        assertTrue(refused("""[1,""").contains("the text ended"))
    }
}

class JsonForgivenessTest {

    @Test
    fun `a trailing comma is allowed, so deleting a line does not break the one above`() {
        assertEquals(listOf("a", "b"), members("""{"a": 1, "b": 2,}""").keys.toList())
        assertEquals(2, elements("""[1, 2,]""").size)
        assertEquals(1, elements("""[1,]""").size)
    }

    @Test
    fun `a byte order mark at the start is skipped`() {
        // An editor on Windows leaves one, and nobody can see it to take it out again.
        assertEquals(Stored.Members(emptyMap()), Json.parse("\uFEFF{}"))
        assertEquals(2, members("\uFEFF{\"a\": 1, \"b\": 2}").size)
        // Only at the very start. Anywhere else it is a character like any other.
        assertTrue(refused("{}\uFEFF").contains("nothing should follow"))
    }

    @Test
    fun `a comma with nothing before it is still wrong`() {
        assertTrue(refused("""{,}""").contains("a name should be in quotes"))
        assertTrue(refused("""[,]""").contains("a value should begin with"))
        assertTrue(refused("""[1,,2]""").contains("a value should begin with"))
    }

    @Test
    fun `space between anything is ignored`() {
        val spaced = "{\n  \"a\" :\t[ 1 ,\r\n 2 ]\n}"
        assertEquals(2, (members(spaced).getValue("a") as Stored.Elements).elements.size)
    }
}

class JsonFailureTest {

    @Test
    fun `a failure says where it was`() {
        val message = refused("{\n  \"a\": 1,\n  \"b\": x\n}")
        assertTrue(message.contains("line 3"), message)
        assertTrue(message.contains("column 8"), message)
    }

    @Test
    fun `nothing may follow the value`() {
        assertTrue(refused("""{} {}""").contains("nothing should follow"))
        assertTrue(refused("""[] junk""").contains("nothing should follow"))
    }

    @Test
    fun `an empty text is not a value`() {
        assertTrue(refused("").contains("the text ended"))
        assertTrue(refused("   ").contains("the text ended"))
    }

    @Test
    fun `nesting is allowed up to the limit`() {
        // The limit is a guard against a corrupt file, not against a deep one.
        val deep = "[".repeat(60) + "1" + "]".repeat(60)
        Json.parse(deep)
    }

    @Test
    fun `nesting stops before the stack does`() {
        // A corrupt file of nothing but brackets should be refused, not crash.
        val deep = "[".repeat(500)
        assertTrue(refused(deep).contains("nested more than"))
    }
}

class JsonDocumentTest {

    @Test
    fun `a document shaped like a real one reads whole`() {
        // data/json tests may use the real format, unlike the rest of the layer. TEST-4.
        val dive = """
            {
              "date": "2026-02-23",
              "rating": 7,
              "max_depth": 11.8,
              "deco": false,
              "buddies": ["@anna_devries", "john"],
              "profiles": {
                "p1": {
                  "depth": [[0, 0], [30, 8.4]],
                  "alarms": [[600, "deco"]]
                }
              }
            }
        """
        val read = members(dive)
        assertEquals(
            listOf("date", "rating", "max_depth", "deco", "buddies", "profiles"),
            read.keys.toList(),
        )
        assertEquals(Stored.Leaf(11.8), read["max_depth"])
        assertEquals(Stored.Leaf(7L), read["rating"])

        val profile = ((read.getValue("profiles") as Stored.Members)
            .members.getValue("p1") as Stored.Members).members
        val firstSample = (profile.getValue("depth") as Stored.Elements).elements[0]
        assertEquals(
            listOf(Stored.Leaf(0L), Stored.Leaf(0L)),
            (firstSample as Stored.Elements).elements,
        )
    }
}
