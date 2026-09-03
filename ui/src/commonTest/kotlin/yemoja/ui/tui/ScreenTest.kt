package yemoja.ui.tui

import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
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
private fun chosen(screen: Screen): String? = screen.paint(60, 12).map { it.text }
    .firstOrNull { it.startsWith("> ") }
    ?.substringBefore("  ")
    ?.removePrefix("> ")

/** One line with its runs of spaces squeezed, so a test states content and not padding. */
/** What Screen shows a line break as: a backslash and an n, as a file writes one. */
private const val ESCAPE = "\\n"

private fun squeezed(line: String): String = line.trim().replace(Regex(" +"), " ")

class TabTest {

    @Test
    fun `there is a tab for every type, in the order the types are given`() {
        assertEquals("[person]   region    gear", screen().paint(60, 6)[0].text.trim())
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
        assertTrue(screen.paint(60, 6)[2].text.startsWith("  (no person)"))
        assertEquals(null, screen.item)
    }
}

class ListTest {

    @Test
    fun `the list holds the ids of the open type and nothing else`() {
        val screen = screen()
        screen.press(Key.RIGHT)
        val ids = screen.paint(60, 8).drop(2)
            .map { it.text.take(14).trim() }
            .filter { it.isNotEmpty() }
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
        val list = screen.paint(60, 12).drop(2).map { it.text.take(14).trim() }
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
        val list = screen.paint(60, 12).drop(2).map { it.text.take(14).trim() }
        assertEquals("region_4", list.first())
        assertEquals("> region_13", list.last())
    }
}

class DetailTest {

    // The list column is a third of the width, and the detail begins one space after it.
    private fun detail(screen: Screen): List<String> = screen.paint(72, 14)
        .drop(2)
        .map { squeezed(it.text.substring(72 / 3 + 1)) }
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
                for (line in painted) assertEquals(width, line.width, "$width by $height")
            }
        }
    }

    @Test
    fun `a painted line holds no control character, whatever an item holds`() {
        // Counting the characters is not enough: a line break measures as one and takes a row.
        val set = logbook(
            "region.json" to """{"north_sea": {"name": "North Sea",
                "remarks": "First line.\nSecond line.\r\nThird."}}""",
        )
        val screen = screen(set)
        screen.press(Key.RIGHT)
        for (line in screen.paint(80, 12).map { it.text }) {
            assertTrue(line.none { it < ' ' }, "one row per line, and |$line| is not")
            assertEquals(80, line.length)
        }
    }

    @Test
    fun `a line break is shown as the escape a file writes it with`() {
        val set = logbook(
            "region.json" to """{"north_sea": {"remarks": "First.\nSecond."}}""",
        )
        val screen = screen(set)
        screen.press(Key.RIGHT)
        val remarks = screen.paint(80, 12).map { it.text }.first { "Remarks" in it }
        assertTrue("Remarks   First." + ESCAPE + "Second." in remarks, remarks)
    }

    @Test
    fun `a rule sits under the tabs`() {
        assertEquals("-".repeat(30), screen().paint(30, 5)[1].text)
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

/** A value nothing wrote, for the fields that are worked out. */
private fun worked(value: String): Result<Any> =
    Result.Usable(value, Result.Origin.DERIVED)

/** Invented types, because no described one has a single reference yet. `TEST-4`. */
private val ROUND = ItemDescription("round", listOf(TextDescription("name")))

private val POSTBOX = ItemDescription(
    "postbox",
    listOf(
        TextDescription("name"),
        ReferenceDescription("round", targetType = "round"),
        TextDescription("colour", role = Role.Derived { worked("red") }),
        TextDescription("shape", role = Role.Overrideable { worked("round") }),
    ),
)

private val INVENTED = listOf(POSTBOX, ROUND)

private fun invented(vararg files: Pair<String, String>): Screen =
    Screen(LogbookReader.read(MemoryFileStore(mapOf(*files)), INVENTED), INVENTED)

/** The styles of one row of the detail column, by the text they are on. */
private fun styling(screen: Screen, width: Int = 70): Map<String, Set<Style>> {
    val detail = width / 3 + 1
    return screen.paint(width, 10).drop(2)
        .flatMap { line ->
            var at = 0
            line.spans.mapNotNull { span ->
                val from = at
                at += span.text.length
                if (from >= detail && span.text.isNotBlank()) span.text.trim() to span.styles
                else null
            }
        }
        .toMap()
}

class StyleTest {

    private val one = """{"market_square": {"name": "Market Square", "round": "@tuesday"}}"""

    private val logbook = arrayOf(
        "postbox.json" to one,
        "round.json" to """{"tuesday": {"name": "Tuesday"}}""",
    )

    @Test
    fun `a reference is underlined, so what can be followed shows before it is tried`() {
        val styles = styling(invented(*logbook))
        assertEquals(setOf(Style.UNDERLINED), styles["@tuesday"])
    }

    @Test
    fun `a worked-out value is italic`() {
        assertEquals(setOf(Style.ITALIC), styling(invented(*logbook))["red"])
    }

    @Test
    fun `a value written over a worked-out one is bold`() {
        val written = """{"market_square": {"shape": "square"}}"""
        assertEquals(setOf(Style.BOLD), styling(invented("postbox.json" to written))["square"])
    }

    @Test
    fun `a value simply written is set apart by nothing`() {
        // Off the chosen row, so that what is left is what the value is rather than where we are.
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        assertEquals(emptySet(), styling(screen)["Market Square"])
    }

    @Test
    fun `the chosen field is set apart, and it is the first one to start with`() {
        val screen = invented(*logbook)
        assertEquals("name", screen.field?.name)
        assertTrue(Style.SELECTED in styling(screen).getValue("Market Square"))
        assertFalse(Style.SELECTED in styling(screen).getValue("@tuesday"))
    }

    @Test
    fun `being chosen is carried alongside what a value is, not instead of it`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        assertEquals(setOf(Style.UNDERLINED, Style.SELECTED), styling(screen)["@tuesday"])
    }

    @Test
    fun `the chosen row is set apart to the edge of the screen`() {
        val screen = invented(*logbook)
        val row = screen.paint(70, 10).drop(2).first { Style.SELECTED in it.spans.last().styles }
        assertTrue(row.spans.last().text.isBlank(), "the padding should carry the row's style")
        assertEquals(70, row.width)
    }
}

class FieldTest {

    private val logbook = arrayOf(
        "postbox.json" to """{"market_square": {"name": "Market Square", "round": "@tuesday"}}""",
        "round.json" to """{"tuesday": {"name": "Tuesday"}}""",
    )

    @Test
    fun `tab moves to the next field and shift-tab back`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        assertEquals("round", screen.field?.name)
        screen.press(Key.PREVIOUS_FIELD)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `the fields are a ring, since a field list is short enough to see whole`() {
        val screen = invented(*logbook)
        screen.press(Key.PREVIOUS_FIELD)
        assertEquals("shape", screen.field?.name)
        screen.press(Key.NEXT_FIELD)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `each tab remembers its own field`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.RIGHT)
        assertEquals("name", screen.field?.name)
        screen.press(Key.LEFT)
        assertEquals("round", screen.field?.name)
    }

    @Test
    fun `a type with no shown fields has no chosen field`() {
        val empty = ItemDescription("empty", emptyList())
        val screen = Screen(LogbookReader.read(MemoryFileStore(emptyMap()), listOf(empty)),
            listOf(empty))
        assertNull(screen.field)
        screen.press(Key.NEXT_FIELD)
        assertNull(screen.field)
    }
}

class FollowTest {

    private val logbook = arrayOf(
        "postbox.json" to """{"market_square": {"name": "Market Square", "round": "@tuesday"}}""",
        "round.json" to """{"monday": {}, "tuesday": {"name": "Tuesday"}}""",
    )

    @Test
    fun `space on a reference opens what it names`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.FOLLOW)
        assertEquals(ROUND, screen.type)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `following starts at the first field of what it arrived at`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.FOLLOW)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `space on anything that is not a reference does nothing`() {
        val screen = invented(*logbook)
        screen.press(Key.FOLLOW)
        assertEquals(POSTBOX, screen.type)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `a reference to nothing goes nowhere`() {
        val screen = invented(
            "postbox.json" to """{"market_square": {"round": "@nowhere"}}""",
        )
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.FOLLOW)
        assertEquals(POSTBOX, screen.type)
    }

    @Test
    fun `an empty reference goes nowhere`() {
        val screen = invented("postbox.json" to """{"market_square": {"name": "Market Square"}}""")
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.FOLLOW)
        assertEquals(POSTBOX, screen.type)
    }
}

class CutValueTest {

    private val long = "x".repeat(80)

    // The row is the list, then the label, then the value, then the padding.
    private fun value(screen: Screen): String = screen.paint(90, 6)[2].spans[2].text

    @Test
    fun `a value short enough is shown as it is`() {
        val screen = invented("postbox.json" to """{"a": {"name": "Market Square"}}""")
        assertEquals("Market Square", value(screen))
    }

    @Test
    fun `a long value is cut, and marked so that the cut is visible`() {
        val screen = invented("""postbox.json""" to """{"a": {"name": "$long"}}""")
        val shown = value(screen)
        assertEquals(Screen.VALUE_WIDTH, shown.length, "the mark counts towards it")
        assertEquals("x".repeat(Screen.VALUE_WIDTH - 3) + "...", shown)
    }

    @Test
    fun `the mark is written in characters every console has`() {
        // One character meaning three dots shows as a question mark where the code page
        // is not UTF-8, which reads as a value nobody could make sense of rather than as
        // a value that was cut.
        val screen = invented("postbox.json" to """{"a": {"name": "$long"}}""")
        assertTrue(value(screen).all { it.code < 127 }, value(screen))
    }

    @Test
    fun `a value exactly as long as the column is not marked`() {
        val edge = "x".repeat(Screen.VALUE_WIDTH)
        val screen = invented("postbox.json" to """{"a": {"name": "$edge"}}""")
        assertEquals(edge, value(screen))
    }

    @Test
    fun `what the list cut is whole when the field is opened`() {
        val screen = invented("postbox.json" to """{"a": {"name": "$long"}}""")
        screen.press(Key.OPEN)
        assertTrue(long in screen.paint(120, 24).joinToString("") { it.text }, "the whole value")
    }
}

class OpenFieldTest {

    private val logbook = arrayOf(
        "postbox.json" to """{"market_square": {"name": "Market Square", "round": "@tuesday"}}""",
        "round.json" to """{"tuesday": {"name": "Tuesday"}}""",
    )

    private fun opened(screen: Screen, width: Int = 90): List<String> =
        screen.paint(width, 24).map { squeezed(it.text) }

    @Test
    fun `enter opens the chosen field and escape closes it`() {
        val screen = invented(*logbook)
        assertFalse(screen.opened)
        screen.press(Key.OPEN)
        assertTrue(screen.opened)
        screen.press(Key.CLOSE)
        assertFalse(screen.opened)
        assertTrue(screen.running, "closing what is open should not leave")
    }

    @Test
    fun `escape leaves where nothing is open`() {
        val screen = invented(*logbook)
        assertFalse(screen.press(Key.CLOSE))
        assertFalse(screen.running)
    }

    @Test
    fun `it says where the field came from`() {
        val screen = invented(*logbook)
        screen.press(Key.OPEN)
        assertEquals("postbox / market_square / name", opened(screen).first())
    }

    @Test
    fun `it says what the field is`() {
        val screen = invented(*logbook)
        screen.press(Key.NEXT_FIELD)
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("field round" in said, said.toString())
        assertTrue("kind reference" in said, said.toString())
        assertTrue("holds one value" in said, said.toString())
        assertTrue("names a round" in said, said.toString())
    }

    @Test
    fun `it says a number's dimension and the unit it is held in`() {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(mapOf("gear.json" to """{"g": {"capacity": 12.0}}""")),
                Types.ALL,
            ),
            Types.ALL,
        )
        screen.press(Key.RIGHT)
        screen.press(Key.RIGHT)
        repeat(7) { screen.press(Key.NEXT_FIELD) }
        assertEquals("capacity", screen.field?.name)
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("measures volume" in said, said.toString())
        assertTrue(said.any { it.startsWith("written in l,") }, said.toString())
    }

    @Test
    fun `it says how a value came to be what it is`() {
        val screen = invented(*logbook)
        repeat(2) { screen.press(Key.NEXT_FIELD) }
        assertEquals("colour", screen.field?.name)
        screen.press(Key.OPEN)
        assertTrue("worked out" in opened(screen), opened(screen).toString())
    }

    @Test
    fun `a field holding nothing says so`() {
        val screen = invented("postbox.json" to """{"a": {}}""")
        screen.press(Key.OPEN)
        assertTrue("nothing" in opened(screen), opened(screen).toString())
    }

    @Test
    fun `up and down scroll the open field rather than the list`() {
        val screen = invented(
            "postbox.json" to """{"a": {"name": "${"x".repeat(600)}"}, "b": {}}""",
        )
        screen.press(Key.OPEN)
        val top = opened(screen, 40)
        repeat(4) { screen.press(Key.DOWN) }
        assertNotEquals(top, opened(screen, 40), "the open field should have moved")
        // And the list did not move under it.
        screen.press(Key.CLOSE)
        assertEquals("> a", screen.paint(60, 8)[2].text.take(20).trim())
    }

    @Test
    fun `closing forgets how far the field was scrolled`() {
        val screen = invented(
            "postbox.json" to """{"a": {"name": "${"x".repeat(600)}"}}""",
        )
        screen.press(Key.OPEN)
        repeat(4) { screen.press(Key.DOWN) }
        screen.press(Key.CLOSE)
        screen.press(Key.OPEN)
        assertEquals("This field", opened(screen, 40)[3], opened(screen, 40).toString())
    }

    @Test
    fun `the open field is still exactly the rectangle asked for`() {
        val screen = invented(*logbook)
        screen.press(Key.OPEN)
        for (line in screen.paint(64, 14)) assertEquals(64, line.width)
    }
}
