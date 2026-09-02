package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * An invented type, because this layer knows nothing about diving. `TEST-4`.
 *
 * A postbox on a street, in a district, emptied on a round.
 */
private val DISTRICT = ItemDescription("district", listOf(TextDescription("name")))

private val POSTBOX = ItemDescription(
    "postbox",
    listOf(
        TextDescription("name"),
        NumberDescription("height", Dimension.LENGTH),
        WholeNumberDescription("collections_per_day", range = 0..9),
        ReferenceDescription("district", targetType = "district"),
        ReferenceDescription("emptied_by", targetType = "district", cardinality = Cardinality.LIST),
        NumberDescription("queue", Dimension.DIMENSIONLESS, cardinality = Cardinality.SERIES),
        // Worked out and never stored: how tall it is in the units nobody uses.
        NumberDescription(
            "height_in_hands",
            Dimension.LENGTH,
            role = Role.Derived { item ->
                when (val height = item.single<Double>("height")) {
                    is Result.Usable -> Result.Usable(height.value / 0.1016, Result.Origin.DERIVED)
                    else -> Result.Absent
                }
            },
        ),
        TextDescription(
            "shown_as",
            role = Role.Overrideable { Result.Usable("a postbox", Result.Origin.DERIVED) },
        ),
    ),
)

private fun postbox(set: ItemSet, fields: Map<String, Result<Any>>) =
    ReferenceableItem(POSTBOX, fields, set)

private fun stored(value: Any) = Result.Usable(value, Result.Origin.STORED)

class ItemTest {

    private val set = ItemSet(listOf(POSTBOX, DISTRICT))

    @Test
    fun `a stored field reads back as it was stored`() {
        val item = postbox(set, mapOf("name" to stored("Marlborough Street")))
        assertEquals(Result.Usable("Marlborough Street", Result.Origin.STORED), item.read("name"))
    }

    @Test
    fun `a field with nothing stored is absent`() {
        val item = postbox(set, emptyMap())
        assertEquals(Result.Absent, item.read("name"))
    }

    @Test
    fun `a name the description does not carry is a fault, not an absent`() {
        val item = postbox(set, emptyMap())
        assertFailsWith<IllegalArgumentException> { item.read("colour") }
    }

    @Test
    fun `a derived field is worked out and was never stored`() {
        val item = postbox(set, mapOf("height" to stored(1.524)))
        val read = item.single<Double>("height_in_hands")
        assertEquals(Result.Origin.DERIVED, (read as Result.Usable).origin)
        assertEquals(15.0, read.value, 0.0001)
        assertNull(item.fields["height_in_hands"])
    }

    @Test
    fun `an overrideable field prefers what was stored`() {
        val plain = postbox(set, emptyMap())
        assertEquals(Result.Usable("a postbox", Result.Origin.DERIVED), plain.read("shown_as"))

        val overridden = Result.Usable("the old one", Result.Origin.OVERRIDDEN)
        val corrected = postbox(set, mapOf("shown_as" to overridden))
        assertEquals(overridden, corrected.read("shown_as"))
    }

    @Test
    fun `a typed read hands the value back already typed`() {
        val item = postbox(set, mapOf("collections_per_day" to stored(2)))
        val read = item.single<Int>("collections_per_day")
        assertEquals(Result.Usable(2, Result.Origin.STORED), read)
    }

    @Test
    fun `asking for the wrong kind is a fault even where nothing is stored`() {
        val item = postbox(set, emptyMap())
        // The point of checking the description rather than the value: absent is the common case.
        assertFailsWith<IllegalArgumentException> { item.single<Int>("height") }
        assertFailsWith<IllegalArgumentException> { item.single<String>("height") }
    }

    @Test
    fun `asking for the wrong shape is a fault`() {
        val item = postbox(set, emptyMap())
        assertFailsWith<IllegalArgumentException> { item.single<Reference>("emptied_by") }
        assertFailsWith<IllegalArgumentException> { item.list<Double>("height") }
        assertFailsWith<IllegalArgumentException> { item.series<Reference>("district") }
    }

    @Test
    fun `a list keeps each member's own fate`() {
        val members = listOf(
            Element.Usable(Reference.Identified("north")),
            Element.Unusable(Stored.Leaf("@"), "an id should not be empty"),
        )
        val item = postbox(set, mapOf("emptied_by" to stored(members)))
        val read = item.list<Reference>("emptied_by")
        assertEquals(members, (read as Result.Usable).value)
    }

    @Test
    fun `a field the description never heard of keeps its own mapping`() {
        val kept = mapOf("posting_times" to Stored.Leaf("09:00"))
        val item = ReferenceableItem(POSTBOX, emptyMap(), set, kept)
        assertEquals(Stored.Leaf("09:00"), item.unrecognisedFields["posting_times"])
        // Not among the fields, and the checked read refuses it: the description has no such name.
        assertEquals(emptyMap(), item.fields)
        assertFailsWith<IllegalArgumentException> { item.read("posting_times") }
    }

    @Test
    fun `an item assembled in memory has no unrecognised fields`() {
        assertEquals(emptyMap(), postbox(set, emptyMap()).unrecognisedFields)
        assertEquals(emptyMap(), OwnedItem(DISTRICT, emptyMap(), postbox(set, emptyMap()))
            .unrecognisedFields)
    }

    @Test
    fun `an item keeps its own copy of what it was given`() {
        val unrecognised = mutableMapOf<String, Stored>("posting_times" to Stored.Leaf("09:00"))
        val item = ReferenceableItem(POSTBOX, emptyMap(), set, unrecognised)
        unrecognised["colour"] = Stored.Leaf("red")
        assertEquals(setOf("posting_times"), item.unrecognisedFields.keys)
    }

    @Test
    fun `an owned item is built against the parent it belongs to`() {
        // Neither can exist first, so the parent hands itself to whatever builds its fields.
        val owner = ReferenceableItem(
            POSTBOX,
            { parent -> mapOf("district" to stored(OwnedItem(DISTRICT, emptyMap(), parent))) },
            set,
        )
        val owned = (owner.fields.getValue("district") as Result.Usable).value as OwnedItem
        assertSame(owner, owned.parent)
        assertSame(set, owned.set)
    }

    @Test
    fun `a parent is finished when its constructor returns`() {
        val owner = ReferenceableItem(
            POSTBOX,
            { parent -> mapOf("district" to stored(OwnedItem(DISTRICT, emptyMap(), parent))) },
            set,
        )
        // Nothing was read outward while building, so everything is there afterwards.
        assertSame(set, owner.set)
        assertEquals(POSTBOX, owner.description)
        assertEquals(setOf("district"), owner.fields.keys)
    }

    @Test
    fun `owned items nest`() {
        val owner = ReferenceableItem(
            POSTBOX,
            { parent ->
                val outer = OwnedItem(
                    DISTRICT,
                    { middle -> mapOf("name" to stored(OwnedItem(DISTRICT, emptyMap(), middle))) },
                    parent,
                )
                mapOf("district" to stored(outer))
            },
            set,
        )
        val outer = (owner.fields.getValue("district") as Result.Usable).value as OwnedItem
        val inner = (outer.fields.getValue("name") as Result.Usable).value as OwnedItem
        assertSame(outer, inner.parent)
        assertSame(owner, outer.parent)
        assertSame(set, inner.set)
    }

    @Test
    fun `an owned item reaches the set through its owner`() {
        val owner = postbox(set, emptyMap())
        val owned = OwnedItem(DISTRICT, emptyMap(), owner)
        assertSame(set, owned.set)
        assertSame(owner, owned.parent)
    }
}
