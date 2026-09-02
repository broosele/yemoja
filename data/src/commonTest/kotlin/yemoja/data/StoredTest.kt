package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StoredLeafTest {

    @Test
    fun `a leaf holds whatever a source had`() {
        assertEquals("2026-02-23", Stored.Leaf("2026-02-23").value)
        assertEquals(7L, Stored.Leaf(7L).value)
        assertEquals(11.8, Stored.Leaf(11.8).value)
        assertEquals(false, Stored.Leaf(false).value)
    }

    @Test
    fun `a leaf holds a value a source had already made`() {
        // The reason the leaf is open: a database column hands over a date, JSON hands over text.
        assertEquals(Date(2026, 2, 23), Stored.Leaf(Date(2026, 2, 23)).value)
        assertEquals(Gas(32, 0), Stored.Leaf(Gas(32, 0)).value)
    }

    @Test
    fun `a leaf holding nothing is a leaf, not an absence`() {
        // A member that is there and empty differs from a member that is not there at all.
        val members = Stored.Members(mapOf("a" to Stored.Leaf(null)))
        assertEquals(Stored.Leaf(null), members.members["a"])
        assertEquals(null, members.members["b"])
        assertTrue("a" in members.members)
    }

    @Test
    fun `a whole number and a fraction tell themselves apart`() {
        // Nothing carries this distinction: a Long is not a Double. A whole number field
        // refuses 7.0 and a number field accepts 7, so the two must not compare equal.
        assertNotEquals(Stored.Leaf(7L), Stored.Leaf(7.0))
        assertEquals(Stored.Leaf(7L), Stored.Leaf(7L))
        assertEquals(Stored.Leaf(7.0), Stored.Leaf(7.0))
    }

    @Test
    fun `leaves compare by what they hold`() {
        assertEquals(Stored.Leaf("air"), Stored.Leaf("air"))
        assertEquals(Stored.Leaf("air").hashCode(), Stored.Leaf("air").hashCode())
        assertNotEquals(Stored.Leaf("air"), Stored.Leaf("EAN32"))
        assertNotEquals<Any?>(Stored.Leaf("air"), null)
    }
}

class StoredMembersTest {

    @Test
    fun `members keep the order the source held them in`() {
        val members = Stored.Members(
            linkedMapOf(
                "name" to Stored.Leaf("Anna"),
                "rating" to Stored.Leaf(7L),
                "deco" to Stored.Leaf(false),
            )
        )
        assertEquals(listOf("name", "rating", "deco"), members.members.keys.toList())
    }

    @Test
    fun `members compare by what they hold`() {
        val one = Stored.Members(mapOf("a" to Stored.Leaf(1L)))
        assertEquals(one, Stored.Members(mapOf("a" to Stored.Leaf(1L))))
        assertEquals(one.hashCode(), Stored.Members(mapOf("a" to Stored.Leaf(1L))).hashCode())
        assertNotEquals(one, Stored.Members(mapOf("a" to Stored.Leaf(2L))))
        assertNotEquals(one, Stored.Members(mapOf("b" to Stored.Leaf(1L))))
        assertNotEquals<Stored>(one, Stored.Leaf(1L))
        assertNotEquals<Any?>(one, null)
    }

    @Test
    fun `members keep their own copy of the map`() {
        val given = mutableMapOf<String, Stored>("a" to Stored.Leaf(1L))
        val members = Stored.Members(given)
        given["b"] = Stored.Leaf(2L)
        assertEquals(setOf("a"), members.members.keys)
    }

    @Test
    fun `members nest`() {
        val profile = Stored.Members(mapOf("depth" to Stored.Leaf(8.4)))
        val dive = Stored.Members(mapOf("profiles" to Stored.Members(mapOf("p1" to profile))))
        val profiles = dive.members.getValue("profiles") as Stored.Members
        assertEquals(profile, profiles.members["p1"])
    }
}

class StoredElementsTest {

    @Test
    fun `elements keep their order`() {
        val buddies = Stored.Elements(listOf(Stored.Leaf("@anna_devries"), Stored.Leaf("john")))
        assertEquals(Stored.Leaf("@anna_devries"), buddies.elements[0])
        assertEquals(Stored.Leaf("john"), buddies.elements[1])
    }

    @Test
    fun `elements compare by what they hold, in order`() {
        val one = Stored.Elements(listOf(Stored.Leaf(1L), Stored.Leaf(2L)))
        assertEquals(one, Stored.Elements(listOf(Stored.Leaf(1L), Stored.Leaf(2L))))
        val same = Stored.Elements(listOf(Stored.Leaf(1L), Stored.Leaf(2L)))
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one, Stored.Elements(listOf(Stored.Leaf(2L), Stored.Leaf(1L))))
        assertNotEquals<Stored>(one, Stored.Members(emptyMap()))
    }

    @Test
    fun `elements keep their own copy of the list`() {
        val given = mutableListOf<Stored>(Stored.Leaf(1L))
        val elements = Stored.Elements(given)
        given.add(Stored.Leaf(2L))
        assertEquals(1, elements.elements.size)
    }

    @Test
    fun `elements nest, which is how a series arrives`() {
        val samples = Stored.Elements(
            listOf(
                Stored.Elements(listOf(Stored.Leaf(0L), Stored.Leaf(0L))),
                Stored.Elements(listOf(Stored.Leaf(30L), Stored.Leaf(8.4))),
            )
        )
        val second = samples.elements[1] as Stored.Elements
        assertEquals(Stored.Leaf(30L), second.elements[0])
        assertEquals(Stored.Leaf(8.4), second.elements[1])
    }

    @Test
    fun `an empty group and an empty run are not each other`() {
        assertNotEquals<Stored>(Stored.Members(emptyMap()), Stored.Elements(emptyList()))
    }
}
