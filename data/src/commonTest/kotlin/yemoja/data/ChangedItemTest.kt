package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Changing an item: the other way through the door reading comes in by.
 *
 * See ../../../../../doc.md — the layer's own document is data/doc.md.
 */

/** Invented types, one holding another. `TEST-4`. */
private val PLAQUE = ItemDescription("plaque", listOf(TextDescription("wording")))

private val POSTBOX = ItemDescription(
    "postbox",
    listOf(
        TextDescription("name"),
        NumberDescription("height", Dimension.LENGTH),
        TextDescription("colour", fixedSet = setOf("red", "green")),
        TextDescription("label", role = Role.Derived { item ->
            (item.read("name") as? Result.Usable)?.let {
                Result.Usable("the ${it.value} box", Result.Origin.DERIVED)
            } ?: Result.Absent
        }),
        WholeNumberDescription(
            "emptied",
            role = Role.Overrideable { Result.Usable(1, Result.Origin.DERIVED) },
        ),
        OwnedItemDescription("plaque", PLAQUE),
    ),
)

private fun postbox(vararg fields: Pair<String, Any>): ReferenceableItem {
    val set = ItemSet(listOf(POSTBOX))
    val held = fields.associate { (name, value) ->
        name to Result.Usable(value, Result.Origin.STORED) as Result<Any>
    }
    val item = ReferenceableItem(POSTBOX, held, set)
    set.add("market_square", item)
    return item
}

private fun text(item: Item, name: String): String? =
    (item.single<String>(name) as? Result.Usable)?.value

class ChangedItemTest {

    @Test
    fun `a value written is a value read`() {
        val item = postbox("name" to "Market Square")
        assertEquals(Validity.Valid, item.write("name", "Station"))
        assertEquals("Station", text(item, "name"))
    }

    @Test
    fun `a value is judged by its field, not taken as it comes`() {
        // `DATA-66`: a form goes through the same door a file does, and is trusted no further.
        val item = postbox()
        val refused = assertIs<Validity.Invalid>(item.write("colour", "brackish"))
        assertTrue("red" in refused.reason, refused.reason)
        assertEquals(Result.Absent, item.read("colour"), "and nothing was written")
    }

    @Test
    fun `text is parsed the way a file's text is`() {
        val item = postbox()
        assertEquals(Validity.Valid, item.write("height", "1.2"))
        assertEquals(1.2, (item.single<Double>("height") as Result.Usable).value)
    }

    @Test
    fun `a value arrives in the units it was expressed in`() {
        val item = postbox()
        item.write("height", 4.0, Units.of(mapOf("length" to "ft")))
        assertEquals(1.2192, (item.single<Double>("height") as Result.Usable).value)
    }

    @Test
    fun `null clears the field`() {
        val item = postbox("name" to "Market Square")
        assertEquals(Validity.Valid, item.write("name", null))
        assertEquals(Result.Absent, item.read("name"))
        assertTrue("name" !in item.fields, "and it is not a key any more")
    }

    @Test
    fun `clearing a correction restores what is worked out`() {
        // Deleting a correction is writing nothing over it, not an operation of its own.
        val item = postbox()
        item.write("emptied", 3)
        assertEquals(3, (item.single<Int>("emptied") as Result.Usable).value)
        item.write("emptied", null)
        val read = assertIs<Result.Usable<*>>(item.read("emptied"))
        assertEquals(1, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a correction is marked as one`() {
        val item = postbox()
        item.write("emptied", 3)
        assertEquals(Result.Origin.OVERRIDDEN, (item.read("emptied") as Result.Usable).origin)
    }

    @Test
    fun `a worked-out field is refused as a fault`() {
        // Nothing offers to edit one, so asking is the mistake naming a field that is not there is.
        val item = postbox("name" to "Market Square")
        assertFailsWith<IllegalArgumentException> { item.write("label", "anything") }
        assertEquals("the Market Square box", text(item, "label"))
    }

    @Test
    fun `a field the type does not have is refused as a fault`() {
        assertFailsWith<IllegalArgumentException> { postbox().write("depth", 3.0) }
    }

    @Test
    fun `an owned item is made by writing an empty set of fields`() {
        // Which is what `"plaque": {}` says in a file. It cannot arrive ready-made: an owned item
        // is built by its owner, and this is the owner. `DATA-85`.
        val item = postbox()
        assertEquals(Result.Absent, item.read("plaque"))
        assertEquals(Validity.Valid, item.write("plaque", Stored.Members(emptyMap())))
        val plaque = assertIs<OwnedItem>((item.read("plaque") as Result.Usable).value)
        assertEquals(item, plaque.parent, "and it belongs to the one that made it")
    }

    @Test
    fun `an owned item is edited by asking it, being an item`() {
        val item = postbox()
        item.write("plaque", Stored.Members(emptyMap()))
        val plaque = (item.read("plaque") as Result.Usable).value as OwnedItem
        assertEquals(Validity.Valid, plaque.write("wording", "Collections at noon"))
        assertEquals("Collections at noon", text(plaque, "wording"))
    }

    @Test
    fun `a reference held to an item shows what was written to it`() {
        // `Referent` says items change, and this is what that buys: nothing has to be told.
        val item = postbox("name" to "Market Square")
        val held: Item = item
        item.write("name", "Station")
        assertEquals("Station", text(held, "name"))
    }
}

class ChangedSetTest {

    private fun set(): ItemSet = ItemSet(listOf(POSTBOX))

    @Test
    fun `an item removed is gone, and says it was there`() {
        val set = set()
        set.add("market_square", ReferenceableItem(POSTBOX, emptyMap(), set))
        assertTrue(set.remove("market_square"))
        assertEquals(null, set["market_square"])
        assertEquals(0, set.size)
    }

    @Test
    fun `removing what is not there says so and changes nothing`() {
        val set = set()
        val was = set.revision
        assertTrue(!set.remove("nowhere"))
        assertEquals(was, set.revision)
    }

    @Test
    fun `the revision moves when the set does`() {
        // Not notification: something holding a list asks whether what it has is still current,
        // and counting the items does not tell it. `DATA-6`.
        val set = set()
        val first = set.revision
        set.add("market_square", ReferenceableItem(POSTBOX, emptyMap(), set))
        val second = set.revision
        assertTrue(second != first, "adding")
        set.remove("market_square")
        assertTrue(set.revision != second, "and removing")
    }
}
