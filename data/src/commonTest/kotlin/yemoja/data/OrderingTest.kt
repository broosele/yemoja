package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Invented types, one listed by name and one by date. `TEST-4`. */
private val STALL = ItemDescription(
    "stall",
    listOf(TextDescription("name")),
    orderedBy = listOf(Ordering("name")),
)

private val ROUND = ItemDescription(
    "round",
    listOf(DateDescription("held"), TimeDescription("at"), TextDescription("name")),
    orderedBy = listOf(
        Ordering("held", Direction.DESCENDING),
        Ordering("at", Direction.DESCENDING),
    ),
)

/** Says nothing about its order, which is the case a front end still has to cope with. */
private val WALK = ItemDescription("walk", listOf(TextDescription("name")))

class OrderingTest {

    private val set = ItemSet(listOf(STALL, ROUND, WALK))

    private fun add(id: String, description: ItemDescription, vararg fields: Pair<String, Any>) {
        val held = fields.associate { (name, value) ->
            name to Result.Usable(value, Result.Origin.STORED) as Result<Any>
        }
        set.add(id, ReferenceableItem(description, held, set))
    }

    private fun ids(description: ItemDescription): List<String?> =
        set.inOrder(description).map { set.idOf(it) }

    @Test
    fun `a type ordered by name comes out alphabetically`() {
        add("c", STALL, "name" to "Cheese")
        add("a", STALL, "name" to "Apples")
        add("b", STALL, "name" to "Bread")
        assertEquals(listOf("a", "b", "c"), ids(STALL))
    }

    @Test
    fun `a descending step puts the newest first`() {
        add("old", ROUND, "held" to Date(2024, 3, 1))
        add("new", ROUND, "held" to Date(2026, 1, 9))
        add("middle", ROUND, "held" to Date(2025, 7, 4))
        assertEquals(listOf("new", "middle", "old"), ids(ROUND))
    }

    @Test
    fun `a later step breaks a tie on the first`() {
        val day = Date(2025, 7, 4)
        add("morning", ROUND, "held" to day, "at" to Time(9, 0, 0))
        add("evening", ROUND, "held" to day, "at" to Time(19, 30, 0))
        assertEquals(listOf("evening", "morning"), ids(ROUND))
    }

    @Test
    fun `the id breaks a tie on every step`() {
        val day = Date(2025, 7, 4)
        add("zulu", ROUND, "held" to day)
        add("alpha", ROUND, "held" to day)
        assertEquals(listOf("alpha", "zulu"), ids(ROUND))
    }

    @Test
    fun `an item with nothing in the field goes last, ascending`() {
        add("named", STALL, "name" to "Bread")
        add("unnamed", STALL)
        assertEquals(listOf("named", "unnamed"), ids(STALL))
    }

    @Test
    fun `an item with nothing in the field goes last descending too`() {
        // The sign that reverses the rest must not lift the unknown to the top: a round with no
        // date is not the most recent one.
        add("dated", ROUND, "held" to Date(2024, 3, 1))
        add("undated", ROUND)
        assertEquals(listOf("dated", "undated"), ids(ROUND))
    }

    @Test
    fun `text sorts without regard to case`() {
        // `Zoo` before `apples` is what comparing code points gives, and it is not a list.
        add("z", STALL, "name" to "Zoo")
        add("a", STALL, "name" to "apples")
        assertEquals(listOf("a", "z"), ids(STALL))
    }

    @Test
    fun `a type asking for no order is left as it was read`() {
        add("c", WALK, "name" to "Cheese")
        add("a", WALK, "name" to "Apples")
        assertEquals(listOf("c", "a"), ids(WALK))
    }

    @Test
    fun `a worked-out field can be sorted on, and is read once per item`() {
        var reads = 0
        val counted = ItemDescription(
            "counted",
            listOf(TextDescription("name", role = Role.Derived { item ->
                reads++
                item.fields["seed"] ?: Result.Absent
            })),
            orderedBy = listOf(Ordering("name")),
        )
        val set = ItemSet(listOf(counted))
        for (id in listOf("d", "a", "c", "b")) {
            val seed = mapOf("seed" to Result.Usable(id, Result.Origin.STORED) as Result<Any>)
            set.add(id, ReferenceableItem(counted, seed, set))
        }
        assertEquals(listOf("a", "b", "c", "d"), set.inOrder(counted).map { set.idOf(it) })
        assertEquals(4, reads, "one read per item, not one per comparison")
    }

    @Test
    fun `an order naming a field the type does not have leaves those items last`() {
        val wrong = ItemDescription(
            "wrong",
            listOf(TextDescription("name")),
            orderedBy = listOf(Ordering("absent_field")),
        )
        val set = ItemSet(listOf(wrong))
        for (id in listOf("b", "a")) {
            set.add(id, ReferenceableItem(wrong, emptyMap(), set))
        }
        assertEquals(listOf("a", "b"), set.inOrder(wrong).map { set.idOf(it) })
    }
}
