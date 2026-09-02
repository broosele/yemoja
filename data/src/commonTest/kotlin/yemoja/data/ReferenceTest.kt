package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ReferenceTest {

    @Test
    fun `a reference is written with an at sign and a one-off without`() {
        assertEquals("@john", Reference.Identified("john").asWritten)
        assertEquals("john", Reference.OneOff("john").asWritten)
    }

    @Test
    fun `a reference reads back from the way it is written`() {
        assertEquals(Reference.Identified("john"), Reference.parse("@john", oneOffAllowed = false))
        val padded = Reference.parse("  @john  ", oneOffAllowed = true)
        assertEquals(Reference.Identified("john"), padded)
        assertEquals(Reference.OneOff("john"), Reference.parse("john", oneOffAllowed = true))
    }

    @Test
    fun `a plain name is not a reference where the field does not allow one`() {
        assertFailsWith<ValueFormatException> { Reference.parse("john", oneOffAllowed = false) }
    }

    @Test
    fun `an id has to be something a reference can carry`() {
        assertFailsWith<ValueFormatException> { Reference.parse("@", oneOffAllowed = false) }
        assertFailsWith<ValueFormatException> { Reference.parse("@jo hn", oneOffAllowed = false) }
        assertFailsWith<ValueFormatException> { Reference.parse("@jo*hn", oneOffAllowed = false) }
        assertFailsWith<IllegalArgumentException> { Reference.Identified("") }
        assertFailsWith<IllegalArgumentException> { Reference.Identified("jo hn") }
        assertFailsWith<IllegalArgumentException> { Reference.Identified("jo*hn") }
    }

    @Test
    fun `an index is part of an id, not a separator to be refused`() {
        assertEquals(Reference.Identified("2026-02-23#1"), Reference.parse("@2026-02-23#1", false))
    }

    @Test
    fun `a one-off may not read as an id`() {
        assertFailsWith<IllegalArgumentException> { Reference.OneOff("@john") }
        assertFailsWith<IllegalArgumentException> { Reference.OneOff("") }
    }

    @Test
    fun `a one-off may hold what an id may not`() {
        // Only the id worked out from a name is constrained; the name itself is free.
        assertEquals("Jo*hn Smith", Reference.OneOff("Jo*hn Smith").asWritten)
    }

    @Test
    fun `two references written the same are the same reference`() {
        assertEquals(Reference.Identified("john"), Reference.Identified("john"))
        assertEquals(Reference.OneOff("john"), Reference.OneOff("john"))
        // What differs is what they claim, which is a question for what they resolve to.
        assertNotEquals<Reference>(Reference.Identified("john"), Reference.OneOff("john"))
    }

    @Test
    fun `a written reference survives being read back`() {
        for (reference in listOf(Reference.Identified("anna_devries"), Reference.OneOff("Anna"))) {
            assertEquals(reference, Reference.parse(reference.asWritten, oneOffAllowed = true))
        }
    }
}

class KeyReferenceTest {

    @Test
    fun `a key reference is written with a star`() {
        assertEquals("*p1", KeyReference("p1").asWritten)
        assertEquals("*p1", KeyReference("p1").toString())
    }

    @Test
    fun `a key reference reads back from the way it is written`() {
        assertEquals(KeyReference("p1"), KeyReference.parse("*p1"))
        assertEquals(KeyReference("p1"), KeyReference.parse("  *p1  "))
    }

    @Test
    fun `without the star it is not one`() {
        assertFailsWith<ValueFormatException> { KeyReference.parse("p1") }
        assertFailsWith<ValueFormatException> { KeyReference.parse("@p1") }
        assertFailsWith<ValueFormatException> { KeyReference.parse("") }
    }

    @Test
    fun `a key has to be something a key reference can carry`() {
        assertFailsWith<ValueFormatException> { KeyReference.parse("*") }
        assertFailsWith<ValueFormatException> { KeyReference.parse("*p 1") }
        assertFailsWith<IllegalArgumentException> { KeyReference("") }
        assertFailsWith<IllegalArgumentException> { KeyReference("p*1") }
        assertFailsWith<IllegalArgumentException> { KeyReference("p 1") }
    }
}

class ReferentTest {

    private val description = ItemDescription("gauge", listOf(TextDescription("name")))

    @Test
    fun `a dangling reference is still an identity`() {
        val dangling = Referent.Dangling("john")
        assertEquals("john", dangling.id)
    }

    @Test
    fun `a resolved referent holds the item itself`() {
        val set = ItemSet(listOf(description))
        val item = ReferenceableItem(description, emptyMap(), set)
        set.add("john", item)
        assertEquals(item, Referent.Resolved(item).item)
    }

    @Test
    fun `two one-off referents naming the same thing are not the same thing`() {
        // A one-off asserts no identity, so two of them may be two people who share a name.
        assertNotEquals(Referent.OneOff("John"), Referent.OneOff("John"))
    }
}
