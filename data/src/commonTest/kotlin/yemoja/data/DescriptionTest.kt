package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Invented fields on an invented type. This layer knows nothing about diving. `TEST-4`. */
private fun usable(description: FieldDescription, given: Any?): Any {
    val read = description.read(given, false, Units.DEFAULT)
    assertIs<Result.Usable<Any>>(read, "$given should have read as a value")
    return read.value
}

private fun unusable(description: FieldDescription, given: Any?): Result.Unusable {
    val read = description.read(given, false, Units.DEFAULT)
    assertIs<Result.Unusable>(read, "$given should not have read as a value")
    assertEquals(Stored.Leaf(given), read.raw, "what was there should be kept as it was")
    return read
}

class FieldDescriptionTest {

    @Test
    fun `a label is made from the name where none is given`() {
        assertEquals("Height", TextDescription("height").label)
        assertEquals("Collections per day", WholeNumberDescription("collections_per_day").label)
    }

    @Test
    fun `a label is given where the name would read badly`() {
        // An acronym needs one: cns would otherwise show as Cns.
        assertEquals("CNS", NumberDescription("cns", Dimension.DIMENSIONLESS, label = "CNS").label)
    }

    @Test
    fun `a stored value carries where it came from`() {
        val height = NumberDescription("height", Dimension.LENGTH)
        val stored = height.read("1.5", false, Units.DEFAULT) as Result.Usable
        val overridden = height.read("1.5", true, Units.DEFAULT) as Result.Usable
        assertEquals(Result.Origin.STORED, stored.origin)
        assertEquals(Result.Origin.OVERRIDDEN, overridden.origin)
    }

    @Test
    fun `a kind that is a group answers unusable when given one value`() {
        val inner = ItemDescription("inner", listOf(TextDescription("name")))
        val owned = OwnedItemDescription("detail", inner)
        val read = unusable(owned, "anything")
        assertEquals("detail should be a set of fields, not one value", read.reason)
    }

    @Test
    fun `a description says what it is, for a test that fails`() {
        val height = NumberDescription("height", Dimension.LENGTH)
        assertEquals("NumberDescription(height, Primary, SINGLE)", height.toString())
    }

    @Test
    fun `what a field refuses from a file is what it refuses from a form`() {
        // parse runs validate, so the two can never drift apart.
        val rating = WholeNumberDescription("rating", range = 1..10)
        assertIs<Validity.Invalid>(rating.validate(11))
        assertEquals("rating should be within 1..10", unusable(rating, "11").reason)
    }
}

class ValueDescriptionTest {

    @Test
    fun `a number is a number, within its range where it has one`() {
        val latitude = NumberDescription("latitude", Dimension.ANGLE, range = -90.0..90.0)
        assertEquals(51.5, usable(latitude, "51.5"))
        assertEquals(-90.0, usable(latitude, "-90.0"))
        assertEquals(0.0, usable(latitude, "  0  "))
        unusable(latitude, "90.1")
        unusable(latitude, "north")
        unusable(latitude, "")
        assertEquals(Double::class, latitude.valueType)
    }

    @Test
    fun `a whole number takes no decimal point`() {
        val rating = WholeNumberDescription("rating", range = 1..10)
        assertEquals(7, usable(rating, "7"))
        unusable(rating, "7.0")
        unusable(rating, "0")
        unusable(rating, "11")
        assertEquals(Int::class, rating.valueType)
    }

    @Test
    fun `text is one line, and does not begin with the markers that name other things`() {
        val name = TextDescription("name")
        assertEquals("Marlborough Street", usable(name, "Marlborough Street"))
        unusable(name, "two\nlines")
        unusable(name, "a\ttab")
        unusable(name, "@john")
        unusable(name, "*p1")
        assertEquals(String::class, name.valueType)
    }

    @Test
    fun `a closed vocabulary refuses what is outside it and says what is inside`() {
        val surface = TextDescription("surface", fixedSet = setOf("brick", "stone"))
        assertEquals("brick", usable(surface, "brick"))
        assertEquals("surface should be one of brick, stone", unusable(surface, "wattle").reason)
    }

    @Test
    fun `an offered vocabulary constrains nothing`() {
        val colour = TextDescription("colour", suggestedSet = setOf("red", "green"))
        assertEquals("puce", usable(colour, "puce"))
        assertEquals(setOf("red", "green"), colour.suggestedSet)
        assertNull(colour.fixedSet)
    }

    @Test
    fun `prose allows the line breaks and markers that text refuses`() {
        val remarks = MultilineTextDescription("remarks")
        assertEquals("two\nlines", usable(remarks, "two\nlines"))
        assertEquals("@john was there", usable(remarks, "@john was there"))
        unusable(remarks, "a\ttab")
        assertEquals(String::class, remarks.valueType)
    }

    @Test
    fun `a date and a time read as they are written and no other way`() {
        val day = DateDescription("day")
        assertEquals(Date(2026, 2, 23), usable(day, "2026-02-23"))
        unusable(day, "23/02/2026")
        assertEquals(Date::class, day.valueType)

        val at = TimeDescription("at")
        assertEquals(Time(9, 15, 0), usable(at, "09:15:00"))
        unusable(at, "9:15am")
        assertEquals(Time::class, at.valueType)
    }

    @Test
    fun `a boolean is written two ways and read in several`() {
        val covered = BooleanDescription("covered")
        for (yes in listOf("true", "TRUE", "True", " true ", "t", "T", "yes", "Yes", "y")) {
            assertEquals(true, usable(covered, yes), yes)
        }
        for (no in listOf("false", "FALSE", "False", " false ", "f", "F", "no", "No", "n")) {
            assertEquals(false, usable(covered, no), no)
        }
        assertEquals(Boolean::class, covered.valueType)
    }

    @Test
    fun `a boolean is not a number, and not a guess`() {
        val covered = BooleanDescription("covered")
        // 1 would invite 2, and a count is a different kind.
        unusable(covered, "1")
        unusable(covered, "0")
        unusable(covered, "ja")
        unusable(covered, "maybe")
        unusable(covered, "")
    }

    @Test
    fun `a mix reads through the same parser divers write to`() {
        val mix = GasDescription("mix")
        assertEquals(Gas(32, 0), usable(mix, "EAN32"))
        assertEquals(Gas(21, 0), usable(mix, "air"))
        unusable(mix, "EAN200")
        unusable(mix, "32")
        assertEquals(Gas::class, mix.valueType)
    }

    @Test
    fun `a key reference checks its syntax and not whether the key exists`() {
        val from = KeyReferenceDescription("from", collection = "sources")
        assertEquals(KeyReference("g2"), usable(from, "*g2"))
        // Nothing here has the collection to ask, and a missing key is not a syntax fault.
        assertEquals(KeyReference("nothing_like_it"), usable(from, "*nothing_like_it"))
        unusable(from, "g2")
        assertEquals("sources", from.collection)
        assertEquals(KeyReference::class, from.valueType)
    }

    @Test
    fun `a reference checks its syntax and not whether it resolves`() {
        val district = ReferenceDescription("district", targetType = "district")
        assertEquals(Reference.Identified("north"), usable(district, "@north"))
        // @john names a person whether or not that person is in the logbook. DATA-66.
        assertEquals(Reference.Identified("never_entered"), usable(district, "@never_entered"))
        unusable(district, "north")
        assertEquals("district", district.targetType)
        assertEquals(Reference::class, district.valueType)
    }

    @Test
    fun `a one-off is allowed only where the field says so`() {
        val strict = ReferenceDescription("district", targetType = "district")
        val loose = ReferenceDescription("district", targetType = "district", oneOffAllowed = true)
        unusable(strict, "north")
        assertEquals(Reference.OneOff("north"), usable(loose, "north"))
        assertIs<Validity.Invalid>(strict.validate(Reference.OneOff("north")))
        assertIs<Validity.Valid>(loose.validate(Reference.OneOff("north")))
    }

    @Test
    fun `every kind writes back what it read`() {
        val cases = listOf(
            NumberDescription("height", Dimension.LENGTH) to "1.5",
            WholeNumberDescription("rating") to "7",
            TextDescription("name") to "Marlborough Street",
            DateDescription("day") to "2026-02-23",
            TimeDescription("at") to "09:15:00",
            BooleanDescription("covered") to "true",
            GasDescription("mix") to "EAN32",
            ReferenceDescription("round", targetType = "round") to "@tuesday",
            ReferenceDescription("who", targetType = "person", oneOffAllowed = true) to "Anna",
            KeyReferenceDescription("from", collection = "profiles") to "*p1",
        )
        for ((description, text) in cases) {
            val value = usable(description, text)
            assertEquals(text, description.format(value, Units.DEFAULT), description.name)
        }
    }

    @Test
    fun `text refuses a line break however it is written`() {
        val name = TextDescription("name")
        unusable(name, "two\nlines")
        unusable(name, "two\rlines")
        unusable(name, "two\u2028lines")
        unusable(name, "two\u2029paragraphs")
    }

    @Test
    fun `text refuses what a reader would not see`() {
        val name = TextDescription("name")
        unusable(name, "a\u0008backspace")
        unusable(name, "an\u001Bescape")
        unusable(name, "a\u0000null")
        unusable(name, "a\u0085stray byte")
        unusable(name, "a\u200Bzero width space")
        unusable(name, "a\uFEFFbyte order mark")
    }

    @Test
    fun `text refuses what would be shown in another order`() {
        // A name could otherwise read as something the file does not say.
        val name = TextDescription("name")
        unusable(name, "Zeeland\u2067brug")
        assertEquals(
            "name should not contain characters that cannot be seen",
            unusable(name, "Zeeland\u202Ebrug").reason,
        )
    }

    @Test
    fun `text keeps the joiners that real writing needs`() {
        // A zero-width joiner builds one emoji out of several, and Persian and Indic writing
        // needs the non-joiner. Neither hides anything.
        val name = TextDescription("name")
        assertEquals("family\u200Dhere", usable(name, "family\u200Dhere"))
        assertEquals("Zeeland\u200Cbrug", usable(name, "Zeeland\u200Cbrug"))
    }

    @Test
    fun `prose keeps its line breaks and refuses the rest`() {
        val remarks = MultilineTextDescription("remarks")
        assertEquals("two\nlines", usable(remarks, "two\nlines"))
        assertEquals("family\u200Dhere", usable(remarks, "family\u200Dhere"))
        unusable(remarks, "a\ttab")
        unusable(remarks, "a\rcarriage return")
        unusable(remarks, "a\u2028line separator")
        unusable(remarks, "a\u0008backspace")
        unusable(remarks, "a\u202Ereordering")
    }

    @Test
    fun `a value outside a fixed set is kept exactly as it was written`() {
        val surface = TextDescription("surface", fixedSet = setOf("brick"))
        assertEquals(Stored.Leaf("  Wattle  "), unusable(surface, "  Wattle  ").raw)
    }
}

class MadeValueTest {

    @Test
    fun `a kind takes a value a source had already made`() {
        // A database column, or a date picker: neither has to render it to text first.
        assertEquals(Date(2026, 2, 23), usable(DateDescription("day"), Date(2026, 2, 23)))
        assertEquals(Time(9, 15, 0), usable(TimeDescription("at"), Time(9, 15, 0)))
        assertEquals(Gas(32, 0), usable(GasDescription("mix"), Gas(32, 0)))
        assertEquals(true, usable(BooleanDescription("covered"), true))
        assertEquals("Zeelandbrug", usable(TextDescription("name"), "Zeelandbrug"))
    }

    @Test
    fun `a made value is still judged`() {
        // Taking it as it comes is not taking it on trust: validate runs either way.
        val rating = WholeNumberDescription("rating", range = 1..10)
        assertEquals(7, usable(rating, 7))
        unusable(rating, 11)
    }

    @Test
    fun `a made value carries the origin it is given`() {
        val day = DateDescription("day")
        val read = day.read(Date(2026, 2, 23), true, Units.DEFAULT) as Result.Usable
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }

    @Test
    fun `a number takes a whole one, because that is how files write them`() {
        // 108000 in a desaturation_time has no decimal point, and every fixture is like it.
        val height = NumberDescription("height", Dimension.LENGTH)
        assertEquals(1.5, usable(height, 1.5))
        assertEquals(108000.0, usable(height, 108000L))
        assertEquals(7.0, usable(height, 7))
    }

    @Test
    fun `a whole number refuses a fraction however it arrives`() {
        val rating = WholeNumberDescription("rating", range = 1..10)
        assertEquals(7, usable(rating, 7))
        assertEquals(7, usable(rating, 7L))
        unusable(rating, 7.0)
        assertEquals("rating should be a whole number", unusable(rating, 7.0).reason)
    }

    @Test
    fun `a whole number too large to hold is refused rather than truncated`() {
        val count = WholeNumberDescription("count")
        val read = unusable(count, Int.MAX_VALUE.toLong() + 1)
        assertEquals("count should be a whole number this machine can hold", read.reason)
        assertEquals(Int.MAX_VALUE, usable(count, Int.MAX_VALUE.toLong()))
    }

    @Test
    fun `a kind refuses a made value of the wrong kind`() {
        unusable(TextDescription("name"), 7L)
        unusable(DateDescription("day"), Time(9, 15, 0))
        unusable(GasDescription("mix"), Date(2026, 2, 23))
        unusable(BooleanDescription("covered"), 1L)
        unusable(NumberDescription("height", Dimension.LENGTH), true)
    }

    @Test
    fun `what could not be used is kept whatever it was`() {
        assertEquals(Stored.Leaf(7.0), unusable(WholeNumberDescription("rating"), 7.0).raw)
        assertEquals(Stored.Leaf(null), unusable(TextDescription("name"), null).raw)
    }
}

class ItemDescriptionTest {

    private val name = TextDescription("name")
    private val height = NumberDescription("height", Dimension.LENGTH)
    private val postbox = ItemDescription("postbox", listOf(name, height))

    @Test
    fun `a type finds its fields by name`() {
        assertSame(name, postbox["name"])
        assertSame(height, postbox["height"])
        assertNull(postbox["colour"])
    }

    @Test
    fun `a type keeps its fields in the order an interface offers them`() {
        assertEquals(listOf(name, height), postbox.fields)
        assertEquals(setOf("name", "height"), postbox.byName.keys)
    }

    @Test
    fun `a type keeps its own copy of the list it was given`() {
        val fields = mutableListOf<FieldDescription>(name)
        val type = ItemDescription("kiosk", fields)
        fields.add(height)
        assertEquals(listOf(name), type.fields)
    }
}

class ResultTest {

    @Test
    fun `the three states of a read are told apart`() {
        assertEquals(Result.Absent, Result.Absent)
        assertEquals(Result.Usable(1, Result.Origin.STORED), Result.Usable(1, Result.Origin.STORED))
        val stored = Result.Usable(1, Result.Origin.STORED)
        assertTrue(stored != Result.Usable(1, Result.Origin.DERIVED))
        assertTrue(Result.Usable(1, Result.Origin.STORED) != Result.Usable(2, Result.Origin.STORED))
    }

    @Test
    fun `an unusable read keeps what it found`() {
        val read = Result.Unusable(Stored.Leaf("EAN200"), "mix should be a gas mix")
        assertEquals(Stored.Leaf("EAN200"), read.raw)
        assertEquals(Result.Unusable(Stored.Leaf("EAN200"), "mix should be a gas mix"), read)
    }

    @Test
    fun `a member of a collection has two states and no origin`() {
        assertEquals(Element.Usable(1), Element.Usable(1))
        assertTrue(Element.Usable(1) != Element.Usable(2))
        val refused = Element.Unusable(Stored.Leaf("x"), "why")
        assertEquals(refused, Element.Unusable(Stored.Leaf("x"), "why"))
    }

    @Test
    fun `a verdict carries its reason`() {
        assertEquals(Validity.Valid, Validity.Valid)
        val refused = Validity.Invalid("rating should be within 1..10")
        assertEquals(refused, Validity.Invalid("rating should be within 1..10"))
        assertTrue(Validity.Invalid("one") != Validity.Invalid("another"))
    }
}

class HousekeepingTest {

    @Test
    fun `a type may mark fields as housekeeping or as sources, and only its own fields`() {
        val type = ItemDescription(
            "thing",
            listOf(TextDescription("name"), TextDescription("key"), TextDescription("raw")),
            housekeeping = setOf("key"),
            sources = setOf("raw"),
        )
        assertEquals(setOf("key"), type.housekeeping)
        assertEquals(setOf("raw"), type.sources)
        assertFailsWith<IllegalArgumentException> {
            ItemDescription("thing", listOf(TextDescription("name")), housekeeping = setOf("nope"))
        }
    }
}
