package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** An invented type again: this layer knows nothing about diving. `TEST-4`. */
private val ROUND = ItemDescription("round", listOf(TextDescription("name")))

private val POSTBOX = ItemDescription(
    "postbox",
    listOf(
        TextDescription("name"),
        NumberDescription("height", Dimension.LENGTH),
        WholeNumberDescription("collections_per_day", range = 0..9),
        ReferenceDescription("emptied_by", targetType = "round", cardinality = Cardinality.LIST),
        OwnedItemDescription("plate", ROUND),
        OwnedItemDescription("rounds", ROUND, cardinality = Cardinality.KEYED),
        NumberDescription("queue", Dimension.DIMENSIONLESS, cardinality = Cardinality.SERIES),
        NumberDescription("waits", Dimension.TIME, cardinality = Cardinality.KEYED_SERIES),
        TextDescription(
            "shown_as",
            role = Role.Overrideable { Result.Usable("a postbox", Result.Origin.DERIVED) },
        ),
    ),
)

private fun read(vararg members: Pair<String, Stored>): ReferenceableItem =
    ItemReader.read(
        POSTBOX,
        Stored.Members(linkedMapOf(*members)),
        ItemSet(listOf(POSTBOX, ROUND)),
        Units.DEFAULT,
    )

private fun leaves(vararg values: Any?): Stored.Elements =
    Stored.Elements(values.map { Stored.Leaf(it) })

private fun sample(second: Any, value: Any?): Stored =
    Stored.Elements(listOf(Stored.Leaf(second), Stored.Leaf(value)))

private fun field(item: Item, name: String): Any =
    (item.fields.getValue(name) as Result.Usable).value

/** The cast is unchecked whatever we do, so it is done once, here, rather than at each use. */
@Suppress("UNCHECKED_CAST")
private fun <T> entries(item: Item, name: String): Map<String, T> =
    field(item, name) as Map<String, T>

private fun refused(item: Item, name: String): Result.Unusable {
    val read = item.fields.getValue(name)
    assertIs<Result.Unusable>(read, "$name should not have read")
    return read
}

class SingleFieldTest {

    @Test
    fun `a leaf is handed to the description, which says what it is`() {
        val item = read("name" to Stored.Leaf("Marlborough Street"), "height" to Stored.Leaf(1.5))
        assertEquals("Marlborough Street", field(item, "name"))
        assertEquals(1.5, field(item, "height"))
    }

    @Test
    fun `a group where one value belongs is an unusable field`() {
        val held = Stored.Members(mapOf("a" to Stored.Leaf(1L)))
        assertEquals("name should be one value", refused(read("name" to held), "name").reason)
        assertEquals(held, refused(read("name" to held), "name").raw)
    }

    @Test
    fun `an overrideable field says its value was overridden`() {
        val item = read("shown_as" to Stored.Leaf("the old one"))
        val read = item.fields.getValue("shown_as") as Result.Usable
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }

    @Test
    fun `a field the file does not mention is simply not there`() {
        val item = read("name" to Stored.Leaf("Marlborough Street"))
        assertEquals(setOf("name"), item.fields.keys)
        assertEquals(Result.Absent, item.read("height"))
    }
}

class ListFieldTest {

    @Test
    fun `a list reads its members in order`() {
        val item = read("emptied_by" to leaves("@tuesday", "@friday"))
        assertEquals(
            listOf(
                Element.Usable(Reference.Identified("tuesday")),
                Element.Usable(Reference.Identified("friday")),
            ),
            field(item, "emptied_by"),
        )
    }

    @Test
    fun `one bad member leaves the rest alone`() {
        val item = read("emptied_by" to leaves("@tuesday", "friday", "@sunday"))
        val members = field(item, "emptied_by") as List<*>
        assertIs<Element.Usable<*>>(members[0])
        assertIs<Element.Unusable>(members[1])
        assertEquals(Stored.Leaf("friday"), (members[1] as Element.Unusable).raw)
        assertIs<Element.Usable<*>>(members[2])
    }

    @Test
    fun `a single value reads as a list of one`() {
        val item = read("emptied_by" to Stored.Leaf("@tuesday"))
        val one = listOf(Element.Usable(Reference.Identified("tuesday")))
        assertEquals(one, field(item, "emptied_by"))
    }

    @Test
    fun `a group where a list belongs is an unusable field`() {
        val held = Stored.Members(mapOf("a" to Stored.Leaf("@tuesday")))
        val reason = refused(read("emptied_by" to held), "emptied_by").reason
        assertEquals("emptied_by should be a list", reason)
    }
}

class OwnedFieldTest {

    @Test
    fun `an owned item is built against the item that holds it`() {
        val item = read("plate" to Stored.Members(mapOf("name" to Stored.Leaf("VR2"))))
        val plate = field(item, "plate") as OwnedItem
        assertSame(item, plate.parent)
        assertEquals("VR2", (plate.fields.getValue("name") as Result.Usable).value)
    }

    @Test
    fun `owned items under keys keep their keys`() {
        val item = read(
            "rounds" to Stored.Members(
                linkedMapOf(
                    "r1" to Stored.Members(mapOf("name" to Stored.Leaf("morning"))),
                    "r2" to Stored.Members(mapOf("name" to Stored.Leaf("evening"))),
                )
            )
        )
        val rounds = entries<Element<Any>>(item, "rounds")
        assertEquals(listOf("r1", "r2"), rounds.keys.toList())
        val first = (rounds.getValue("r1") as Element.Usable).value as OwnedItem
        assertSame(item, first.parent)
    }

    @Test
    fun `an entry that is not a set of fields is an unusable entry`() {
        val item = read("rounds" to Stored.Members(mapOf("r1" to Stored.Leaf("morning"))))
        val rounds = entries<Element<Any>>(item, "rounds")
        assertIs<Element.Unusable>(rounds.getValue("r1"))
    }

    @Test
    fun `a leaf where an owned item belongs is an unusable field`() {
        assertEquals(
            "plate should be a set of fields",
            refused(read("plate" to Stored.Leaf("VR2")), "plate").reason,
        )
    }
}

class SeriesFieldTest {

    @Test
    fun `a series is pairs of a time and a value`() {
        val item = read("queue" to Stored.Elements(listOf(sample(0L, 0.0), sample(30L, 8.4))))
        val series = field(item, "queue") as Series
        assertEquals(2, series.size)
        assertEquals(30, series.secondAt(1))
        assertEquals(Element.Usable(8.4), series.valueAt(1))
    }

    @Test
    fun `a value that will not read is an unusable sample in its place`() {
        val item = read("queue" to Stored.Elements(listOf(sample(0L, 0.0), sample(30L, "deep"))))
        val series = field(item, "queue") as Series
        assertEquals(Element.Usable(0.0), series.valueAt(0))
        assertIs<Element.Unusable>(series.valueAt(1))
    }

    @Test
    fun `a sample that is not a time and a value spoils the field`() {
        // A value with no time has nowhere to sit, and dropping it would redraw the curve.
        val held = Stored.Elements(listOf(sample(0L, 0.0), leaves(30L)))
        val reason = refused(read("queue" to held), "queue").reason
        assertEquals("queue should hold a time and a value at each sample", reason)
    }

    @Test
    fun `a time that is not whole seconds spoils the field`() {
        val held = Stored.Elements(listOf(sample(0.5, 0.0)))
        val reason = refused(read("queue" to held), "queue").reason
        assertEquals("queue should be timed in whole seconds", reason)
    }

    @Test
    fun `a series that does not run forwards is an unusable field, not a thrown fault`() {
        val held = Stored.Elements(listOf(sample(30L, 0.0), sample(0L, 8.4)))
        assertTrue(refused(read("queue" to held), "queue").reason.contains("should run forwards"))
    }

    @Test
    fun `one key's series failing leaves the others alone`() {
        val item = read(
            "waits" to Stored.Members(
                linkedMapOf(
                    "a" to Stored.Elements(listOf(sample(0L, 1.0))),
                    "b" to Stored.Leaf("not a series"),
                )
            )
        )
        val waits = entries<Element<Series>>(item, "waits")
        assertIs<Element.Usable<*>>(waits.getValue("a"))
        assertIs<Element.Unusable>(waits.getValue("b"))
    }
}

class UnrecognisedFieldTest {

    @Test
    fun `a name the description does not carry is kept apart, untouched`() {
        val item = read(
            "name" to Stored.Leaf("Marlborough Street"),
            "posting_times" to Stored.Elements(listOf(Stored.Leaf("09:00"))),
        )
        assertEquals(setOf("name"), item.fields.keys)
        assertEquals(setOf("posting_times"), item.unrecognisedFields.keys)
        assertEquals(
            Stored.Elements(listOf(Stored.Leaf("09:00"))),
            item.unrecognisedFields.getValue("posting_times"),
        )
    }
}
