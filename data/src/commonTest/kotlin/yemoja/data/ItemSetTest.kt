package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Two invented types, so the set has more than one to tell apart. `TEST-4`. */
private val KIOSK = ItemDescription("kiosk", listOf(TextDescription("name")))

private val ROUND = ItemDescription("round", listOf(TextDescription("name")))

class ItemSetTest {

    private val set = ItemSet(listOf(KIOSK, ROUND))

    private fun kiosk() = ReferenceableItem(KIOSK, emptyMap(), set)

    private fun round() = ReferenceableItem(ROUND, emptyMap(), set)

    @Test
    fun `an id resolves to the item it names`() {
        val item = kiosk()
        set.add("market_square", item)
        assertSame(item, set["market_square"])
    }

    @Test
    fun `an id nothing has resolves to nothing`() {
        assertNull(set["market_square"])
    }

    @Test
    fun `one id names one item, whatever their types`() {
        set.add("market_square", kiosk())
        assertFailsWith<IllegalArgumentException> { set.add("market_square", kiosk()) }
        // The namespace is not per type: a reference carries the id and nothing else.
        assertFailsWith<IllegalArgumentException> { set.add("market_square", round()) }
    }

    @Test
    fun `an id is written the way a reference can name it`() {
        assertFailsWith<IllegalArgumentException> { set.add("", kiosk()) }
        assertFailsWith<IllegalArgumentException> { set.add("market square", kiosk()) }
        assertFailsWith<IllegalArgumentException> { set.add("market*square", kiosk()) }
    }

    @Test
    fun `a type lists everything of that type and nothing else`() {
        val first = kiosk()
        val second = kiosk()
        set.add("market_square", first)
        set.add("station", second)
        set.add("tuesday", round())

        assertEquals(listOf(first, second), set.allOf(KIOSK))
        assertEquals(1, set.allOf(ROUND).size)
        assertEquals(3, set.size)
    }

    @Test
    fun `a type with nothing of it lists nothing`() {
        assertEquals(emptyList(), set.allOf(KIOSK))
    }

    @Test
    fun `the set answers in both directions`() {
        val item = kiosk()
        set.add("market_square", item)
        assertEquals("market_square", set.idOf(item))
    }

    @Test
    fun `an item of another set is not called anything here`() {
        set.add("market_square", kiosk())
        assertNull(set.idOf(kiosk()))
    }

    @Test
    fun `two items alike are told apart by which one they are`() {
        // Items have no equality, so the set answers about the object it was given.
        val first = kiosk()
        val second = kiosk()
        set.add("market_square", first)
        set.add("station", second)
        assertEquals("station", set.idOf(second))
    }
}
