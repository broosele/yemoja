package yemoja.ui.tui

import yemoja.data.ItemSet
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private val THREE = logbook(
    "person.json" to """{"anna": {"first_name": "Anna", "last_name": "de Vries"}}""",
    "region.json" to """{
        "north_sea": {"name": "North Sea", "category": "sea", "north": 61.0},
        "wadden_sea": {"name": "Wadden Sea", "category": "sea"}
    }""",
    "gear.json" to """{"faber_12": {"name": "Faber 12", "capacity": 12.0}}""",
)

private fun screen(set: ItemSet = THREE): Screen = Screen(set, Types.ALL)

/** The chosen row of the list column, without its marker or its padding. */
private fun chosen(screen: Screen): String? = screen.paint(60, 12)
    .firstOrNull { it.startsWith("> ") }
    ?.substringBefore("  ")
    ?.removePrefix("> ")

/** One line with its runs of spaces squeezed, so a test states content and not padding. */
private fun squeezed(line: String): String = line.trim().replace(Regex(" +"), " ")

class TabTest {

    @Test
    fun `there is a tab for every type, in the order the types are given`() {
        assertEquals("[person]   region    gear", screen().paint(60, 6)[0].trim())
    }

    @Test
    fun `right moves to the next tab and left back again`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        assertEquals(Types.REGION, screen.type)
        screen.press(Key.LEFT)
        assertEquals(Types.PERSON, screen.type)
    }

    @Test
    fun `the tabs are a ring, so either end is one press from the other`() {
        val screen = screen()
        screen.press(Key.LEFT)
        assertEquals(Types.GEAR, screen.type)
        screen.press(Key.RIGHT)
        assertEquals(Types.PERSON, screen.type)
    }

    @Test
    fun `leaving a tab and coming back returns to where the user was`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        screen.press(Key.DOWN)
        assertEquals("wadden_sea", chosen(screen))
        screen.press(Key.RIGHT)
        screen.press(Key.LEFT)
        assertEquals("wadden_sea", chosen(screen))
    }

    @Test
    fun `a type with no items still has a tab, and says so`() {
        val screen = screen(logbook("gear.json" to """{"faber_12": {}}"""))
        assertTrue(screen.paint(60, 6)[2].startsWith("  (no person)"))
        assertEquals(null, screen.item)
    }
}

class ListTest {

    @Test
    fun `the list holds the ids of the open type and nothing else`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        val ids = screen.paint(60, 8).drop(2).map { it.take(14).trim() }.filter { it.isNotEmpty() }
        assertEquals(listOf("> north_sea", "wadden_sea"), ids)
    }

    @Test
    fun `down moves the choice and up moves it back`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        assertEquals("north_sea", chosen(screen))
        screen.press(Key.DOWN)
        assertEquals("wadden_sea", chosen(screen))
        screen.press(Key.UP)
        assertEquals("north_sea", chosen(screen))
    }

    @Test
    fun `the choice stops at the ends rather than wrapping`() {
        // A list that wraps loses the user's place on a long one.
        val screen = screen()
        screen.press(Key.RIGHT)
        repeat(5) { screen.press(Key.DOWN) }
        assertEquals("wadden_sea", chosen(screen))
        repeat(5) { screen.press(Key.UP) }
        assertEquals("north_sea", chosen(screen))
    }

    @Test
    fun `a long list scrolls to keep the choice in view`() {
        val many = (1..40).joinToString(", ") { """"region_$it": {"name": "Region $it"}""" }
        val screen = screen(logbook("region.json" to "{$many}"))
        screen.press(Key.RIGHT)
        repeat(20) { screen.press(Key.DOWN) }
        val list = screen.paint(60, 12).drop(2).map { it.take(14).trim() }
        assertTrue("> region_21" in list, "the chosen row should be on screen, and $list is not")
        assertEquals(10, list.size)
    }

    @Test
    fun `the window moves as little as the choice allows`() {
        val many = (1..40).joinToString(", ") { """"region_$it": {}""" }
        val screen = screen(logbook("region.json" to "{$many}"))
        screen.press(Key.RIGHT)
        repeat(12) { screen.press(Key.DOWN) }
        // Twelve down on ten rows shows rows four to thirteen, not the chosen one at the top.
        val list = screen.paint(60, 12).drop(2).map { it.take(14).trim() }
        assertEquals("region_4", list.first())
        assertEquals("> region_13", list.last())
    }
}

class DetailTest {

    // The list column is a third of the width, and the detail begins one space after it.
    private fun detail(screen: Screen): List<String> = screen.paint(72, 14)
        .drop(2)
        .map { squeezed(it.substring(72 / 3 + 1)) }
        .filter { it.isNotEmpty() }

    @Test
    fun `every single-valued field of the chosen item is shown, in the type's order`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        assertEquals(
            listOf(
                "Name North Sea",
                "Category sea",
                "West",
                "East",
                "South",
                "North 61",
                "Remarks",
            ),
            detail(screen),
        )
    }

    @Test
    fun `a field with nothing in it is still listed`() {
        // What a type can hold is half of what this interface is for.
        val screen = screen()
        assertTrue(detail(screen).any { it.startsWith("Birthday") })
    }

    @Test
    fun `a worked-out field shows what was worked out`() {
        assertTrue(
            detail(screen()).any { it.startsWith("Name") && it.endsWith("Anna de Vries") },
            "the assembled name should show",
        )
    }

    @Test
    fun `a value that cannot be read says why, rather than showing a blank`() {
        val screen = screen(logbook("region.json" to """{"north_sea": {"north": 91.0}}"""))
        screen.press(Key.RIGHT)
        assertTrue(
            detail(screen).any { it.contains("! north should be within") },
            "an unusable value should say why, and ${detail(screen)} does not",
        )
    }

    @Test
    fun `a number is shown as a file writes it`() {
        val screen = screen()
        screen.press(Key.LEFT)
        assertTrue("Capacity 12" in detail(screen), detail(screen).toString())
    }

    @Test
    fun `an empty tab shows no fields at all`() {
        val screen = screen(logbook("gear.json" to """{"faber_12": {}}"""))
        assertEquals(emptyList(), detail(screen))
    }
}

class PaintingTest {

    @Test
    fun `the screen is exactly the rectangle it was asked for`() {
        for (width in listOf(20, 40, 61, 100)) {
            for (height in listOf(3, 10, 25)) {
                val painted = screen().paint(width, height)
                assertEquals(height, painted.size, "$width by $height")
                for (line in painted) assertEquals(width, line.length, "$width by $height")
            }
        }
    }

    @Test
    fun `a rule sits under the tabs`() {
        assertEquals("-".repeat(30), screen().paint(30, 5)[1])
    }

    @Test
    fun `a screen too small to hold anything is a fault rather than a drawing`() {
        assertFailsWith<IllegalArgumentException> { screen().paint(19, 10) }
        assertFailsWith<IllegalArgumentException> { screen().paint(40, 2) }
    }

    @Test
    fun `a screen with no types at all is a fault`() {
        assertFailsWith<IllegalArgumentException> { Screen(THREE, emptyList()) }
    }
}

class LeavingTest {

    @Test
    fun `quit stops the interface and every other key does not`() {
        val screen = screen()
        assertTrue(screen.press(Key.DOWN))
        assertTrue(screen.running)
        assertFalse(screen.press(Key.QUIT))
        assertFalse(screen.running)
    }
}
