package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.Dimension
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
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

/** Move down until the field called [name] is chosen, at whatever depth it sits. */
private fun toField(screen: Screen, name: String) {
    repeat(screen.rows.size) { if (screen.field?.name != name) screen.press(Key.DOWN) }
    assertEquals(name, screen.field?.name)
}

/** Change tab until the type called [name] is open, however many there are. */
private fun toTab(screen: Screen, name: String) {
    repeat(Types.ALL.size) { if (screen.type.name != name) screen.press(Key.NEXT_TAB) }
    assertEquals(name, screen.type.name)
}

/** The rows between the rule under the heading and the rule above the bar. */
private fun body(screen: Screen, width: Int = 60, height: Int = 12): List<Line> =
    screen.paint(width, height).drop(2).dropLast(2)

/** The chosen row of the list column, without its marker or its padding. */
private fun chosen(screen: Screen): String? = body(screen).map { it.text }
    .firstOrNull { it.startsWith("> ") }
    ?.substringBefore("  ")
    ?.removePrefix("> ")

/** What Screen shows a line break as: a backslash and an n, as a file writes one. */
private const val ESCAPE = "\\n"

/** One line with its runs of spaces squeezed, so a test states content and not padding. */
private fun squeezed(line: String): String = line.trim().replace(Regex(" +"), " ")

/** The rows an open field gives its value, which are the ones under the last heading. */
private fun held(painted: List<String>): List<String> = painted
    .dropWhile { !it.startsWith("What it holds") }
    .drop(1)
    .takeWhile { it.isNotBlank() }

class TabTest {

    @Test
    fun `there is a tab for every type, in the order the types are given`() {
        // Not trimmed: an unopened tab is spaced on both sides, the last one included.
        val bar = screen().paint(200, 6)[0].text
        assertTrue(bar.startsWith("[dive]"), bar)
        for (type in Types.ALL.drop(1)) assertTrue(" ${type.name} " in bar, bar)
    }

    @Test
    fun `tab moves to the next type and shift-tab back again`() {
        val screen = screen()
        screen.press(Key.NEXT_TAB)
        assertEquals(Types.PERSON, screen.type)
        screen.press(Key.PREVIOUS_TAB)
        assertEquals(Types.DIVE, screen.type)
    }

    @Test
    fun `the tabs are a ring, so either end is one press from the other`() {
        val screen = screen()
        screen.press(Key.PREVIOUS_TAB)
        assertEquals(Types.ALL.last(), screen.type)
        screen.press(Key.NEXT_TAB)
        assertEquals(Types.DIVE, screen.type)
    }

    @Test
    fun `leaving a tab and coming back returns to where the user was`() {
        val screen = screen()
        toTab(screen, "region")
        screen.press(Key.RIGHT)
        assertEquals("wadden_sea", chosen(screen))
        screen.press(Key.NEXT_TAB)
        screen.press(Key.PREVIOUS_TAB)
        assertEquals("wadden_sea", chosen(screen))
    }

    @Test
    fun `a type with no items still has a tab, and says so`() {
        val screen = screen(logbook("gear.json" to """{"faber_12": {}}"""))
        assertTrue(body(screen, 60, 8).first().text.startsWith("  (no dive)"))
        assertEquals(null, screen.item)
    }
}

class ListTest {

    @Test
    fun `the list holds the ids of the open type and nothing else`() {
        val screen = screen()
        toTab(screen, "region")
        val ids = body(screen, 60, 8)
            .map { it.text.take(14).trim() }
            .filter { it.isNotEmpty() }
        assertEquals(listOf("> north_sea", "wadden_sea"), ids)
    }

    @Test
    fun `right moves the choice and left moves it back`() {
        val screen = screen()
        toTab(screen, "region")
        assertEquals("north_sea", chosen(screen))
        screen.press(Key.RIGHT)
        assertEquals("wadden_sea", chosen(screen))
        screen.press(Key.LEFT)
        assertEquals("north_sea", chosen(screen))
    }

    @Test
    fun `the choice stops at the ends rather than wrapping`() {
        // A list that wraps loses the user's place on a long one.
        val screen = screen()
        toTab(screen, "region")
        repeat(5) { screen.press(Key.RIGHT) }
        assertEquals("wadden_sea", chosen(screen))
        repeat(5) { screen.press(Key.LEFT) }
        assertEquals("north_sea", chosen(screen))
    }

    @Test
    fun `a long list scrolls to keep the choice in view`() {
        val many = (1..40).joinToString(", ") { """"region_$it": {"name": "Region $it"}""" }
        val screen = screen(logbook("region.json" to "{$many}"))
        toTab(screen, "region")
        repeat(20) { screen.press(Key.RIGHT) }
        val list = body(screen).map { it.text.take(14).trim() }
        assertTrue("> region_21" in list, "the chosen row should be on screen, and $list is not")
        assertEquals(8, list.size)
    }

    @Test
    fun `the window moves as little as the choice allows`() {
        val many = (1..40).joinToString(", ") { """"region_$it": {}""" }
        val screen = screen(logbook("region.json" to "{$many}"))
        toTab(screen, "region")
        repeat(12) { screen.press(Key.RIGHT) }
        // Twelve down on ten rows shows rows four to thirteen, not the chosen one at the top.
        val list = body(screen).map { it.text.take(14).trim() }
        assertEquals("region_6", list.first())
        assertEquals("> region_13", list.last())
    }
}

class DetailTest {

    // The list column is a third of the width, and the detail begins one space after it.
    private fun detail(screen: Screen): List<String> = body(screen, 72, 16)
        .map { squeezed(it.text.substring(72 / 3 + 1)) }
        .filter { it.isNotEmpty() }

    @Test
    fun `every shown field of the chosen item is there, in the type's order`() {
        val screen = screen()
        toTab(screen, "region")
        assertEquals(
            listOf(
                "Name North Sea",
                "Category sea",
                "Parents",
                "Children (empty)",
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
        toTab(screen, "person")
        assertTrue(detail(screen).any { it.startsWith("Birthday") })
    }

    @Test
    fun `a worked-out field shows what was worked out`() {
        val screen = screen()
        toTab(screen, "person")
        assertTrue(
            detail(screen).any { it.startsWith("Name") && it.endsWith("Anna de Vries") },
            "the assembled name should show",
        )
    }

    @Test
    fun `a value that cannot be read says why, rather than showing a blank`() {
        val screen = screen(logbook("region.json" to """{"north_sea": {"north": 91.0}}"""))
        toTab(screen, "region")
        assertTrue(
            detail(screen).any { it.contains("! north should be within") },
            "an unusable value should say why, and ${detail(screen)} does not",
        )
    }

    @Test
    fun `a number is shown as a file writes it`() {
        val screen = screen()
        toTab(screen, "gear")
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
            for (height in listOf(5, 10, 25)) {
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
        toTab(screen, "region")
        // Opened as well as beside the list. Checking only the list is what let a break
        // through: a value short enough to fit was painted without being flattened.
        for (opened in listOf(false, true)) {
            if (opened) {
                toField(screen, "remarks")
                screen.press(Key.OPEN)
            }
            for (line in screen.paint(80, 12).map { it.text }) {
                assertTrue(line.none { it < ' ' }, "one row per line, and |$line| is not")
                assertEquals(80, line.length)
            }
        }
    }

    @Test
    fun `a break in text that is not a remark never reaches a row at all`() {
        // `remarks` is the only field allowed one, and the layer below refuses the rest, so
        // taking a break as a break is safe: nothing else can be carrying one.
        val set = logbook(
            "dive_site.json" to """{"blue_quarry": {"name": "Blue Quarry",
                "alternative_names": ["The Quarry\nDe Groeve", "Blauwe Groeve"]}}""",
        )
        val screen = screen(set)
        toTab(screen, "dive_site")
        toField(screen, "alternative_names")
        screen.press(Key.OPEN)
        val said = screen.paint(80, 16).map { it.text }
        for (line in said) {
            assertTrue(line.none { it < ' ' }, "one row per line, and |$line| is not")
            assertEquals(80, line.length)
        }
        assertTrue(
            said.any { "alternative_names should be a single line of text" in it },
            "refused, and the entry beside it is untouched: " + said.joinToString("|"),
        )
        assertTrue(said.any { "Blauwe Groeve" in it }, said.joinToString("|"))
    }

    @Test
    fun `a line break is shown as the escape a file writes it with`() {
        val set = logbook(
            "region.json" to """{"north_sea": {"remarks": "First.\nSecond."}}""",
        )
        val screen = screen(set)
        toTab(screen, "region")
        // Tall enough for every field of a region, remarks being the last of them.
        val remarks = screen.paint(80, 16).map { it.text }.first { "Remarks" in it }
        assertTrue("Remarks   First." + ESCAPE + "Second." in remarks, remarks)
    }

    @Test
    fun `opened, each line of a value is a row of its own, set in alike`() {
        val set = logbook(
            "region.json" to """{"north_sea": {"remarks": "Ebb runs hard.\nPark north."}}""",
        )
        val screen = screen(set)
        toTab(screen, "region")
        toField(screen, "remarks")
        screen.press(Key.OPEN)
        val said = screen.paint(60, 20).map { it.text.trimEnd() }
        assertTrue("  Ebb runs hard." in said, said.joinToString("|"))
        assertTrue("  Park north." in said, "the second line keeps the column: $said")
    }

    @Test
    fun `a value too wide for the screen keeps its column where it carries on`() {
        val long = "Ebb runs hard and the vis goes with it, so the second half is on the wall."
        val set = logbook("region.json" to """{"north_sea": {"remarks": "$long"}}""")
        val screen = screen(set)
        toTab(screen, "region")
        toField(screen, "remarks")
        screen.press(Key.OPEN)
        val said = screen.paint(50, 20).map { it.text }
        val at = said.indexOfFirst { it.startsWith("  Ebb runs") }
        assertTrue(at >= 0, said.toString())
        val next = said[at + 1]
        assertTrue(next.startsWith("  "), "carried on in the same column: |$next|")
        assertTrue(next.isNotBlank(), "and there is more of it: $said")
    }

    @Test
    fun `prose breaks at a space rather than through a word`() {
        // `remarks` is the one field long enough to wrap and it is prose, so a mid-word break
        // reads as damage rather than as a line ending.
        val long = "Ebb runs hard and the visibility goes with it, so the second half of the " +
            "dive is usually done along the wall rather than out over the sand."
        val set = logbook("region.json" to """{"north_sea": {"remarks": "$long"}}""")
        val screen = screen(set)
        toTab(screen, "region")
        toField(screen, "remarks")
        screen.press(Key.OPEN)
        val wrapped = held(screen.paint(50, 20).map { it.text })
        assertTrue(wrapped.size >= 3, "it should take several rows: $wrapped")
        for (row in wrapped) assertTrue(row.startsWith("  "), "one column: |$row|")
        // Put back together it is the whole remark, each break standing in for its space.
        assertEquals(long, wrapped.joinToString(" ") { it.trim() })
    }

    @Test
    fun `a value with no space in it is broken where the row ends`() {
        // An id, a position or a gas mix has nowhere better to break, and wants none.
        val run = "a".repeat(72)
        val set = logbook("region.json" to """{"north_sea": {"remarks": "$run"}}""")
        val screen = screen(set)
        toTab(screen, "region")
        toField(screen, "remarks")
        screen.press(Key.OPEN)
        val rows = held(screen.paint(50, 20).map { it.text })
        assertEquals(2, rows.size, rows.toString())
        assertEquals(50, rows.first().length, "the first row is full: |${rows.first()}|")
        assertEquals(run, rows.joinToString("") { it.trim() })
    }

    @Test
    fun `a rule sits under the tabs`() {
        val painted = screen().paint(30, 6)
        assertEquals("-".repeat(30), painted[1].text)
        assertEquals("-".repeat(30), painted[painted.size - 2].text)
    }

    @Test
    fun `a screen too small to hold anything is a fault rather than a drawing`() {
        assertFailsWith<IllegalArgumentException> { screen().paint(19, 10) }
        assertFailsWith<IllegalArgumentException> { screen().paint(40, 4) }
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

    @Test
    fun `quit leaves from however deep the reader has gone`() {
        val screen = screen()
        toTab(screen, "person")
        toField(screen, "courses")
        repeat(3) { screen.press(Key.OPEN) }
        assertTrue(screen.opened, "and it is worth going in before leaving")
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
    return body(screen, width, 12)
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
        screen.press(Key.DOWN)
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
        screen.press(Key.DOWN)
        assertEquals(setOf(Style.UNDERLINED, Style.SELECTED), styling(screen)["@tuesday"])
    }

    @Test
    fun `the chosen row is set apart to the edge of the screen`() {
        val screen = invented(*logbook)
        val row = body(screen, 70, 12).first { Style.SELECTED in it.spans.last().styles }
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
    fun `down moves to the next field and up back again`() {
        val screen = invented(*logbook)
        screen.press(Key.DOWN)
        assertEquals("round", screen.field?.name)
        screen.press(Key.UP)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `the fields are a ring, since a field list is short enough to see whole`() {
        val screen = invented(*logbook)
        screen.press(Key.UP)
        assertEquals("shape", screen.field?.name)
        screen.press(Key.DOWN)
        assertEquals("name", screen.field?.name)
    }

    @Test
    fun `each tab remembers its own field`() {
        val screen = invented(*logbook)
        screen.press(Key.DOWN)
        screen.press(Key.NEXT_TAB)
        assertEquals("name", screen.field?.name)
        screen.press(Key.PREVIOUS_TAB)
        assertEquals("round", screen.field?.name)
    }

    @Test
    fun `a type with no shown fields has no chosen field`() {
        val empty = ItemDescription("empty", emptyList())
        val screen = Screen(LogbookReader.read(MemoryFileStore(emptyMap()), listOf(empty)),
            listOf(empty))
        assertNull(screen.field)
        screen.press(Key.DOWN)
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
        screen.press(Key.DOWN)
        screen.press(Key.FOLLOW)
        assertEquals(ROUND, screen.type)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `following starts at the first field of what it arrived at`() {
        val screen = invented(*logbook)
        screen.press(Key.DOWN)
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
        screen.press(Key.DOWN)
        screen.press(Key.FOLLOW)
        assertEquals(POSTBOX, screen.type)
    }

    @Test
    fun `an empty reference goes nowhere`() {
        val screen = invented("postbox.json" to """{"market_square": {"name": "Market Square"}}""")
        screen.press(Key.DOWN)
        screen.press(Key.FOLLOW)
        assertEquals(POSTBOX, screen.type)
    }
}

class CutValueTest {

    private val long = "x".repeat(80)

    // The row is the list, then the label, then the value, then the padding.
    private fun value(screen: Screen): String = body(screen, 90, 8).first().spans[2].text

    @Test
    fun `a value short enough is shown as it is`() {
        val screen = invented("postbox.json" to """{"a": {"name": "Market Square"}}""")
        assertEquals("Market Square", value(screen))
    }

    @Test
    fun `a long value is cut, and marked so that the cut is visible`() {
        val screen = invented("""postbox.json""" to """{"a": {"name": "$long"}}""")
        val shown = value(screen)
        assertEquals(VALUE_WIDTH, shown.length, "the mark counts towards it")
        assertEquals("x".repeat(VALUE_WIDTH - 3) + "...", shown)
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
        val edge = "x".repeat(VALUE_WIDTH)
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
    fun `escape stays where there is nothing to come out of`() {
        // A step back is the mildest thing a reader does, so it does not also end the session.
        val screen = invented(*logbook)
        assertTrue(screen.press(Key.CLOSE))
        assertTrue(screen.running)
        assertFalse(screen.opened)
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
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("field round" in said, said.toString())
        assertTrue("kind reference" in said, said.toString())
        assertTrue("holds single value" in said, said.toString())
        assertTrue("names a round" in said, said.toString())
        // Nothing is said about a plain name where one is not allowed, which is the norm.
        assertTrue(said.none { "plain name" in it }, said.toString())
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
        toTab(screen, "gear")
        toField(screen, "capacity")
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("measures volume" in said, said.toString())
        assertTrue(said.any { it.startsWith("written in l,") }, said.toString())
    }

    @Test
    fun `it says how a value came to be what it is`() {
        val screen = invented(*logbook)
        repeat(2) { screen.press(Key.DOWN) }
        assertEquals("colour", screen.field?.name)
        screen.press(Key.OPEN)
        assertTrue("What it holds (worked out)" in opened(screen), opened(screen).toString())
    }

    @Test
    fun `a field holding nothing says so beside the heading, and adds nothing under it`() {
        val screen = invented("postbox.json" to """{"a": {}}""")
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("What it holds (nothing)" in said, said.toString())
        // The heading is the last thing said, since there is nothing to say under it.
        assertEquals("", said[said.indexOf("What it holds (nothing)") + 1])
    }

    @Test
    fun `a value that could not be read says so beside the heading`() {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(mapOf("region.json" to """{"r": {"north": 91.0}}""")),
                Types.ALL,
            ),
            Types.ALL,
        )
        toTab(screen, "region")
        toField(screen, "north")
        screen.press(Key.OPEN)
        val said = opened(screen)
        assertTrue("What it holds (written, and could not be read)" in said, said.toString())
        assertTrue(said.any { it.startsWith("north should be within") }, said.toString())
        assertTrue(said.any { it.startsWith("as written:") }, said.toString())
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
        assertEquals("> a", body(screen, 60, 8).first().text.take(20).trim())
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

/** A field holding several values: on a row, and opened. */
class ListTest2 {

    private val ROUNDS = ItemDescription("round", listOf(TextDescription("name")))

    private val WALK = ItemDescription(
        "walk",
        listOf(
            TextDescription("name"),
            ReferenceDescription("rounds", targetType = "round", cardinality = Cardinality.LIST),
        ),
    )

    private val TYPES = listOf(WALK, ROUNDS)

    private fun walk(rounds: String): Screen = Screen(
        LogbookReader.read(
            MemoryFileStore(mapOf("walk.json" to """{"a": {"rounds": $rounds}}""")),
            TYPES,
        ),
        TYPES,
    )

    /** The value column of the row the chosen field is on. */
    private fun row(screen: Screen): String = body(screen, 90, 8)[1].spans[2].text.trimEnd()

    private fun opened(screen: Screen): List<String> {
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        return screen.paint(90, 24).map { it.text.trim() }
    }

    @Test
    fun `a few values sit on the row, separated by commas`() {
        assertEquals("@one, @two, @three", row(walk("""["@one", "@two", "@three"]""")))
    }

    @Test
    fun `too many to fit says how many were left out`() {
        val many = (1..9).joinToString(", ") { """"@region_$it"""" }
        val shown = row(walk("[$many]"))
        assertTrue(shown.length <= VALUE_WIDTH, shown)
        assertTrue(shown.endsWith("others)"), shown)
        assertTrue(shown.startsWith("@region_1 "), shown)
    }

    @Test
    fun `one left out is one other, not one others`() {
        // Two short ones and a long one: the first two fit beside the count and the third
        // cannot, so exactly one is left out.
        val three = """["@ab", "@cd", "@${"e".repeat(30)}"]"""
        assertEquals("@ab, @cd ... (1 other)", row(walk(three)))
    }

    @Test
    fun `a written list with nothing in it is not an absent field`() {
        assertEquals("(empty)", row(walk("[]")))
    }

    @Test
    fun `opened, each value is a bullet of its own`() {
        val said = opened(walk("""["@one", "@two", "@three"]"""))
        assertTrue("- @one" in said, said.toString())
        assertTrue("- @two" in said, said.toString())
        assertTrue("- @three" in said, said.toString())
    }

    @Test
    fun `opened, nothing is left out however many there are`() {
        val many = (1..9).joinToString(", ") { """"@region_$it"""" }
        val said = opened(walk("[$many]"))
        for (at in 1..9) assertTrue("- @region_$at" in said, "region_$at should be there")
    }

    @Test
    fun `opened, an empty list says so rather than showing nothing`() {
        assertTrue("(empty)" in opened(walk("[]")))
    }

    @Test
    fun `opened, it says the field holds several`() {
        assertTrue("holds several, in the order written" in opened(walk("[]")).map {
            it.replace(Regex(" +"), " ")
        })
    }

    @Test
    fun `an entry that could not be read shows why, in its place`() {
        val said = opened(walk("""["@one", 7, "@three"]"""))
        assertTrue("- @one" in said, said.toString())
        assertTrue("- @three" in said, said.toString())
        assertTrue(said.any { it.startsWith("- !") }, said.toString())
    }
}

/** Following a field that names several. The only reference any described type has is one. */
class FollowListTest {

    private val ROUNDS = ItemDescription("round", listOf(TextDescription("name")))

    private val WALK = ItemDescription(
        "walk",
        listOf(
            TextDescription("name"),
            ReferenceDescription(
                "rounds",
                targetType = "round",
                cardinality = Cardinality.LIST,
                oneOffAllowed = true,
            ),
        ),
    )

    private val TYPES = listOf(WALK, ROUNDS)

    private fun walk(rounds: String, vararg rest: Pair<String, String>): Screen {
        val files = mapOf("walk.json" to """{"a": {"rounds": $rounds}}""") + mapOf(*rest)
        val screen = Screen(LogbookReader.read(MemoryFileStore(files), TYPES), TYPES)
        screen.press(Key.DOWN)
        return screen
    }

    private val rounds =
        "round.json" to """{"monday": {"name": "Monday"}, "tuesday": {"name": "Tuesday"}}"""

    @Test
    fun `a list of one is followed, which is every list a region has`() {
        val screen = walk("""["@tuesday"]""", rounds)
        screen.press(Key.FOLLOW)
        assertEquals(ROUNDS, screen.type)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `a list of several is followed to the first, which is the one on the row`() {
        val screen = walk("""["@monday", "@tuesday"]""", rounds)
        screen.press(Key.FOLLOW)
        assertEquals("Monday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `one that names nothing is passed over for one that names something`() {
        val screen = walk("""["@nowhere", "@tuesday"]""", rounds)
        screen.press(Key.FOLLOW)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `a plain name is not followed, there being nothing to open`() {
        val screen = walk("""["Aunt Maud", "@tuesday"]""", rounds)
        screen.press(Key.FOLLOW)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `a list naming nothing that is there goes nowhere`() {
        val screen = walk("""["@nowhere", "@elsewhere"]""", rounds)
        screen.press(Key.FOLLOW)
        assertEquals(WALK, screen.type)
    }

    @Test
    fun `an empty list goes nowhere`() {
        val screen = walk("[]", rounds)
        screen.press(Key.FOLLOW)
        assertEquals(WALK, screen.type)
    }
}

/** Moving between the values of an open field, and following the one chosen. */
class EntryCursorTest {

    private val ROUNDS = ItemDescription("round", listOf(TextDescription("name")))

    private val WALK = ItemDescription(
        "walk",
        listOf(
            TextDescription("name"),
            ReferenceDescription("rounds", targetType = "round", cardinality = Cardinality.LIST),
        ),
    )

    private val TYPES = listOf(WALK, ROUNDS)

    private val rounds = "round.json" to
        """{"monday": {"name": "Monday"}, "tuesday": {"name": "Tuesday"},
            "friday": {"name": "Friday"}}"""

    /** A walk with these rounds, opened on the field that holds them. */
    private fun opened(rounds_: String): Screen {
        val files = mapOf("walk.json" to """{"a": {"rounds": $rounds_}}""") + mapOf(rounds)
        val screen = Screen(LogbookReader.read(MemoryFileStore(files), TYPES), TYPES)
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        return screen
    }

    /** The bullet the cursor is on, without its mark. */
    private fun chosenEntry(screen: Screen, height: Int = 24): String? = screen.paint(60, height)
        .firstOrNull { row -> row.spans.any { Style.SELECTED in it.styles } }
        ?.text?.trim()?.removePrefix("- ")

    private val three = """["@monday", "@tuesday", "@friday"]"""

    @Test
    fun `the first value is chosen when a field is opened`() {
        assertEquals("@monday", chosenEntry(opened(three)))
    }

    @Test
    fun `down moves to the next value and up back`() {
        val screen = opened(three)
        screen.press(Key.DOWN)
        assertEquals("@tuesday", chosenEntry(screen))
        screen.press(Key.DOWN)
        assertEquals("@friday", chosenEntry(screen))
        screen.press(Key.UP)
        assertEquals("@tuesday", chosenEntry(screen))
    }

    @Test
    fun `the cursor stops at the ends`() {
        val screen = opened(three)
        repeat(5) { screen.press(Key.DOWN) }
        assertEquals("@friday", chosenEntry(screen))
        repeat(5) { screen.press(Key.UP) }
        assertEquals("@monday", chosenEntry(screen))
    }

    @Test
    fun `space opens the value the cursor is on, not the first`() {
        val screen = opened(three)
        screen.press(Key.DOWN)
        screen.press(Key.FOLLOW)
        assertEquals(ROUNDS, screen.type)
        assertEquals("Tuesday", (screen.item!!.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `following closes the field, the user having arrived somewhere else`() {
        val screen = opened(three)
        screen.press(Key.FOLLOW)
        assertFalse(screen.opened)
    }

    @Test
    fun `space on a value naming nothing stays put`() {
        val screen = opened("""["@nowhere", "@tuesday"]""")
        screen.press(Key.FOLLOW)
        assertEquals(WALK, screen.type)
        assertTrue(screen.opened, "and the field stays open")
    }

    @Test
    fun `a long list scrolls to keep the cursor in view`() {
        val many = "[" + (1..40).joinToString(", ") { """"@r$it"""" } + "]"
        val screen = opened(many)
        repeat(30) { screen.press(Key.DOWN) }
        assertEquals("@r31", chosenEntry(screen, 12), "the cursor should be on screen")
    }

    @Test
    fun `a field holding one value has no cursor, and up and down scroll its rows`() {
        val long = "x".repeat(600)
        val files = mapOf("walk.json" to """{"a": {"name": "$long"}}""")
        val screen = Screen(LogbookReader.read(MemoryFileStore(files), TYPES), TYPES)
        screen.press(Key.OPEN)
        assertEquals(null, chosenEntry(screen, 12), "one value is not chosen between")
        val top = screen.paint(40, 12).map { it.text }
        repeat(4) { screen.press(Key.DOWN) }
        assertNotEquals(top, screen.paint(40, 12).map { it.text }, "it should have scrolled")
    }

    @Test
    fun `opening a field again starts at its first value`() {
        val screen = opened(three)
        screen.press(Key.DOWN)
        screen.press(Key.CLOSE)
        screen.press(Key.OPEN)
        assertEquals("@monday", chosenEntry(screen))
    }
}

/** What is underlined where a field is open: a value that names an item, and nothing else. */
class OpenedUnderliningTest {

    private val ROUNDS = ItemDescription("round", listOf(TextDescription("name")))

    private val WALK = ItemDescription(
        "walk",
        listOf(
            ReferenceDescription(
                "rounds",
                targetType = "round",
                cardinality = Cardinality.LIST,
                oneOffAllowed = true,
            ),
            ReferenceDescription("first", targetType = "round"),
            TextDescription("name"),
        ),
    )

    private val TYPES = listOf(WALK, ROUNDS)

    private fun opened(fields: String, steps: Int = 0): Map<String, Set<Style>> {
        val files = mapOf(
            "walk.json" to """{"a": $fields}""",
            "round.json" to """{"tuesday": {"name": "Tuesday"}}""",
        )
        val screen = Screen(LogbookReader.read(MemoryFileStore(files), TYPES), TYPES)
        repeat(steps) { screen.press(Key.DOWN) }
        screen.press(Key.OPEN)
        return screen.paint(70, 20)
            .flatMap { it.spans }
            .filter { it.text.isNotBlank() && !it.text.startsWith("  ") }
            .associate { it.text.trim() to it.styles - Style.SELECTED }
    }

    @Test
    fun `a value naming an item is underlined`() {
        val said = opened("""{"rounds": ["@tuesday"]}""")
        assertEquals(setOf(Style.UNDERLINED), said["@tuesday"])
    }

    @Test
    fun `a single reference is underlined too`() {
        val said = opened("""{"first": "@tuesday"}""", steps = 1)
        assertEquals(setOf(Style.UNDERLINED), said["@tuesday"])
    }

    @Test
    fun `a plain name is not, there being nothing to open`() {
        val said = opened("""{"rounds": ["Aunt Maud", "@tuesday"]}""")
        assertEquals(emptySet(), said["Aunt Maud"])
        assertEquals(setOf(Style.UNDERLINED), said["@tuesday"])
    }

    @Test
    fun `a value that would not read is not, being no value at all`() {
        val said = opened("""{"rounds": [7, "@tuesday"]}""")
        assertEquals(setOf(Style.UNDERLINED), said["@tuesday"])
        val refused = said.keys.first { it.startsWith("!") }
        assertEquals(emptySet(), said[refused])
    }

    @Test
    fun `a value that names nothing is text, and text is not underlined`() {
        val said = opened("""{"name": "Market Square"}""", steps = 2)
        assertEquals(emptySet(), said["Market Square"])
    }
}

/** The bar at the bottom saying what the keys do here. */
class ActionsTest {

    private fun bar(screen: Screen, width: Int = 90): String =
        screen.paint(width, 12).last().text.trim()

    @Test
    fun `the bar sits under a rule at the bottom`() {
        val painted = screen().paint(60, 12)
        assertEquals("-".repeat(60), painted[painted.size - 2].text)
        assertTrue(painted.last().text.startsWith("[q] quit"), painted.last().text)
    }

    @Test
    fun `it says what each key does where the list is`() {
        // Wide enough for all of them, which eighty columns is not.
        val said = bar(screen(), 120)
        assertEquals(
            "[q] quit | [(shift)-tab] type | [<,>] item | [^,v] field | " +
                "[enter] open | [space] follow",
            said,
        )
    }

    @Test
    fun `it says something else where a field is open`() {
        val screen = screen()
        toTab(screen, "person")
        screen.press(Key.OPEN)
        val said = bar(screen, 120)
        assertTrue(said.startsWith("[q] quit | [esc] back"), said)
        assertTrue("[space] follow" in said, said)
        assertTrue("[<,>]" !in said, "the list does not move from inside a field: $said")
    }

    @Test
    fun `an open field says whether up and down move or scroll`() {
        val screen = screen()
        toTab(screen, "person")
        screen.press(Key.OPEN)
        assertTrue("[^,v] scroll" in bar(screen), bar(screen))
        // Parents holds a list, so there are values to move between rather than rows to scroll,
        // whether or not this region has any written.
        screen.press(Key.CLOSE)
        toTab(screen, "region")
        toField(screen, "parents")
        screen.press(Key.OPEN)
        assertTrue("[^,v] value" in bar(screen), bar(screen))
    }

    @Test
    fun `a narrow screen says fewer things rather than half a thing`() {
        val said = bar(screen(), 40)
        assertTrue(said.length <= 40, said)
        assertTrue(said.startsWith("[q] quit | [(shift)-tab] type"), said)
        assertFalse(said.endsWith("|"), "no dangling separator: $said")
        assertFalse(said.contains("[ente"), "no half a key: $said")
        // It stops rather than skipping to something shorter, so the front is always the same.
        assertFalse("[^,v]" in said, "nothing from past the cut: $said")
    }

    @Test
    fun `the narrowest screen there is still says one thing`() {
        assertEquals("[q] quit", bar(screen(), Screen.LEAST_WIDTH))
    }
}

/** What an open field says about a reference that allows a plain name. */
class PlainNameTest {

    private fun opened(oneOff: Boolean): List<String> {
        val walk = ItemDescription(
            "walk",
            listOf(ReferenceDescription("who", targetType = "person", oneOffAllowed = oneOff)),
        )
        val types = listOf(walk)
        val screen = Screen(
            LogbookReader.read(MemoryFileStore(mapOf("walk.json" to """{"a": {}}""")), types),
            types,
        )
        screen.press(Key.OPEN)
        return screen.paint(90, 20).map { squeezed(it.text) }
    }

    @Test
    fun `a field that allows one says so where it says what it names`() {
        assertTrue(
            "names a person, or a plain name where there is no item" in opened(true),
            opened(true).toString(),
        )
    }

    @Test
    fun `a field that does not simply says what it names`() {
        assertTrue("names a person" in opened(false), opened(false).toString())
        assertTrue(opened(false).none { "plain name" in it }, opened(false).toString())
    }
}

/** A field holding values against time. No described type has one until dives do. */
class SeriesTest2 {

    private val DIVE = ItemDescription(
        "dive",
        listOf(
            TextDescription("name"),
            NumberDescription("depth", Dimension.LENGTH, cardinality = Cardinality.SERIES),
        ),
    )

    private val TYPES = listOf(DIVE)

    private fun dive(depth: String): Screen = Screen(
        LogbookReader.read(
            MemoryFileStore(mapOf("dive.json" to """{"a": {"depth": $depth}}""")),
            TYPES,
        ),
        TYPES,
    )

    private fun row(screen: Screen): String = body(screen, 90, 8)[1].spans[2].text.trimEnd()

    private fun opened(screen: Screen): List<String> {
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        return screen.paint(90, 24).map { squeezed(it.text) }
    }

    private val three = "[[0, 0.0], [30, 8.4], [90, 12.1]]"

    @Test
    fun `the row says how many samples there are`() {
        assertEquals("3 samples", row(dive(three)))
    }

    @Test
    fun `one sample is one sample`() {
        assertEquals("1 sample", row(dive("[[0, 0.0]]")))
    }

    @Test
    fun `a thousand samples still take one row`() {
        val many = (0..<1000).joinToString(", ") { "[${it * 10}, ${it % 40}.0]" }
        val said = row(dive("[$many]"))
        assertEquals("1000 samples", said)
        assertTrue(said.length <= VALUE_WIDTH, said)
    }

    @Test
    fun `opened, each sample is a row of its own`() {
        val said = opened(dive(three))
        assertTrue("0 0" in said, said.toString())
        assertTrue("30 8.4" in said, said.toString())
        assertTrue("90 12.1" in said, said.toString())
    }

    @Test
    fun `the times are lined up on the right, so the values make a column`() {
        // A sample is two spans: the time, and what was read. They line up when every time
        // takes the same room, whatever number of digits it has.
        val screen = dive("[[0, 0.0], [30, 8.4], [1830, 12.1]]")
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        val times = screen.paint(90, 24)
            .map { it.spans.first() }
            .filter { it.text.trim().toIntOrNull() != null }
        assertEquals(listOf("0", "30", "1830"), times.map { it.text.trim() })
        assertTrue(times.all { it.text.length == times.first().text.length }, times.toString())
    }

    @Test
    fun `a sample that could not be read says why, in its place`() {
        val said = opened(dive("""[[0, 0.0], [30, "deep"], [90, 12.1]]"""))
        assertTrue(said.any { it.startsWith("30 !") }, said.toString())
        assertTrue("90 12.1" in said, said.toString())
    }

    @Test
    fun `a series with nothing in it says so`() {
        assertEquals("0 samples", row(dive("[]")))
        assertTrue("(empty)" in opened(dive("[]")))
    }

    @Test
    fun `up and down scroll a series rather than moving between samples`() {
        // There is nothing to follow in a series, so a cursor over one would buy nothing.
        val many = (0..<200).joinToString(", ") { "[${it * 10}, ${it % 40}.0]" }
        val screen = dive("[$many]")
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertTrue("[^,v] scroll" in screen.paint(90, 12).last().text, "it scrolls")
        val top = screen.paint(60, 12).map { it.text }
        repeat(5) { screen.press(Key.DOWN) }
        assertNotEquals(top, screen.paint(60, 12).map { it.text })
    }
}

/** An item inside an item, indented under the name of the field holding it. */
class NestedTest {

    private fun person(fields: String): Screen {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(mapOf("person.json" to """{"anna": $fields}""")),
                Types.ALL,
            ),
            Types.ALL,
        )
        toTab(screen, "person")
        return screen
    }

    /** The column of fields, without the list beside it. */
    private fun rows(screen: Screen, width: Int = 90, height: Int = 26): List<String> =
        body(screen, width, height)
            .map { it.text.substring(width / 3 + 1).trimEnd() }
            .filter { it.isNotBlank() }

    private val medical = """{"medical": {"blood_group": "O+", "height": 1.78}}"""

    @Test
    fun `a field holding one item names it and sets its fields in`() {
        val said = rows(person(medical))
        assertTrue("Medical" in said, said.toString())
        assertTrue(said.any { it.startsWith("  Blood group") }, said.toString())
        assertTrue(said.any { it.startsWith("  Height") }, said.toString())
    }

    @Test
    fun `the value of a field inside an item is beside its own name`() {
        assertTrue(rows(person(medical)).any { squeezed(it) == "Blood group O+" })
    }

    @Test
    fun `a field holding several says its keys and no more`() {
        // Unbounded: a dive with three profiles of twenty fields would be sixty rows of
        // somebody else's business. They are shown whole where a reader asks for that field.
        val courses = """{"courses": {"k1": {"date": "2019-06-02"}, "k2": {}}}"""
        val said = rows(person(courses))
        assertTrue(said.any { squeezed(it) == "Courses k1, k2" }, said.toString())
        assertTrue(said.none { it.trim().startsWith("Date") }, said.toString())
    }

    @Test
    fun `a field holding an item nobody wrote is named and has nothing under it`() {
        val said = rows(person("{}"))
        val at = said.indexOf("Medical")
        assertTrue(at >= 0, said.toString())
        assertEquals("Insurance", said[at + 1], "nothing between them: $said")
    }

    @Test
    fun `values line up in one column however deep their names sit`() {
        val labels = body(person(medical), 90, 26)
            .filter { it.spans.size > 2 && it.spans[2].text.isNotBlank() }
            .map { it.spans[1].text.length }
        assertTrue(labels.isNotEmpty(), "there should be values")
        assertTrue(labels.all { it == labels.first() }, labels.toString())
    }

    @Test
    fun `only the chosen field's own row is set apart, not what is under it`() {
        val screen = person(medical)
        toField(screen, "medical")
        val marked = body(screen, 90, 26).filter { row ->
            row.spans.any { Style.SELECTED in it.styles }
        }
        assertEquals(1, marked.size, "one row, not the whole item")
        assertEquals("Medical", marked.single().text.substring(31).trim())
    }

    @Test
    fun `the column scrolls to keep the chosen field in view`() {
        val screen = person(medical)
        // Up from the top wraps to the last row, which a medical's own fields put off a short
        // screen. Named this way rather than by field, a medical having a remarks of its own.
        screen.press(Key.UP)
        assertEquals("remarks", screen.field?.name)
        assertTrue("Remarks" in rows(screen, 90, 12), "it should be on screen")
    }

    @Test
    fun `opened, a field holding an item shows what is in it and not its own name twice`() {
        val screen = person(medical)
        toField(screen, "medical")
        screen.press(Key.OPEN)
        val said = screen.paint(90, 24).map { squeezed(it.text) }
        assertEquals("person / anna / medical", said.first())
        assertTrue("Blood group O+" in said, said.toString())
        assertTrue(said.none { it == "Medical" }, "the heading names it already: $said")
    }

    @Test
    fun `opened, a field holding nothing says so`() {
        val screen = person("{}")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertTrue("(empty)" in screen.paint(90, 24).map { squeezed(it.text) })
    }
}

/** Going into an item, and coming back out of it. */
class PathTest {

    private val fields = """{"medical": {"blood_group": "O+", "height": 1.78},
        "courses": {"k1": {"date": "2019-06-02"}, "k2": {"date": "2021-03-04"}}}"""

    private fun person(): Screen {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(mapOf("person.json" to """{"anna": $fields}""")),
                Types.ALL,
            ),
            Types.ALL,
        )
        toTab(screen, "person")
        return screen
    }

    private fun said(screen: Screen): List<String> =
        screen.paint(90, 24).map { squeezed(it.text) }

    private fun chosenRow(screen: Screen): String? = screen.paint(90, 24)
        .firstOrNull { row -> row.spans.any { Style.SELECTED in it.styles } }
        ?.let { squeezed(it.text) }

    /**
     * Move down inside an open item until the chosen row is [label]'s.
     *
     * By name rather than by counting presses. A type gaining a field used to move every
     * cursor below it and break tests that had nothing to do with the field.
     */
    private fun toRow(screen: Screen, label: String) {
        repeat(20) { if (chosenRow(screen)?.startsWith(label) != true) screen.press(Key.DOWN) }
        assertEquals(true, chosenRow(screen)?.startsWith(label), chosenRow(screen))
    }

    @Test
    fun `opening a field that holds an item shows that item's own fields`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertEquals("person / anna / medical", said(screen).first())
        assertTrue("Blood group O+" in said(screen), said(screen).toString())
        assertEquals("Last medical check", chosenRow(screen), "the first field is chosen")
    }

    @Test
    fun `up and down move between the fields of an item`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.DOWN)
        assertEquals("Blood group O+", chosenRow(screen))
        screen.press(Key.DOWN)
        assertEquals("Height 1.78", chosenRow(screen))
        screen.press(Key.UP)
        assertEquals("Blood group O+", chosenRow(screen))
    }

    @Test
    fun `enter goes into the field the cursor is on`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        assertEquals("person / anna / medical / blood_group", said(screen).first())
        assertTrue("field blood_group" in said(screen), said(screen).toString())
    }

    @Test
    fun `opening a field that holds several lands on the first of them`() {
        val screen = person()
        toField(screen, "courses")
        screen.press(Key.OPEN)
        assertEquals("person / anna / courses / k1", said(screen).first())
        assertEquals("[k1] k2", said(screen)[2], "a tab apiece, the open one in brackets")
        assertTrue("Date 2019-06-02" in said(screen), said(screen).toString())
    }

    @Test
    fun `tab moves between the keys, as it does between the types`() {
        val screen = person()
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(Key.NEXT_TAB)
        assertEquals("person / anna / courses / k2", said(screen).first())
        assertEquals("k1 [k2]", said(screen)[2])
        assertTrue("Date 2021-03-04" in said(screen), said(screen).toString())
        screen.press(Key.PREVIOUS_TAB)
        assertEquals("person / anna / courses / k1", said(screen).first())
    }

    @Test
    fun `the keys are a ring, and the tabs are left alone while one is open`() {
        val screen = person()
        val type = screen.type
        toField(screen, "courses")
        screen.press(Key.OPEN)
        screen.press(Key.PREVIOUS_TAB)
        assertEquals("person / anna / courses / k2", said(screen).first())
        assertEquals(type, screen.type, "the types do not move under an open field")
    }

    @Test
    fun `up and down move between the fields of the open key`() {
        val screen = person()
        toField(screen, "courses")
        screen.press(Key.OPEN)
        assertEquals("Certification", chosenRow(screen))
        screen.press(Key.DOWN)
        assertEquals("Number", chosenRow(screen))
        screen.press(Key.UP)
        assertEquals("Certification", chosenRow(screen))
    }

    /** A course naming both a certification and an instructor, each of which is there. */
    private fun course(): Screen {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(
                    mapOf(
                        "person.json" to """{
                            "anna": {"courses": {"k1": {
                                "certification": "@padi", "instructor": "@tom"}}},
                            "tom": {"first_name": "Tom"}
                        }""",
                        "certification.json" to """{"padi": {"name": "Open Water"}}""",
                    ),
                ),
                Types.ALL,
            ),
            Types.ALL,
        )
        toTab(screen, "person")
        toField(screen, "courses")
        screen.press(Key.OPEN)
        return screen
    }

    @Test
    fun `space follows the reference the cursor is on inside an item`() {
        // The path ends at the item; what a reader points at is one step further.
        val screen = course()
        screen.press(Key.FOLLOW)
        assertEquals(Types.CERTIFICATION, screen.type, "the first field names a certification")
        assertFalse(screen.opened, "arriving somewhere closes what was open")
    }

    @Test
    fun `it follows the one the cursor moved to, not the first`() {
        val screen = course()
        toRow(screen, "Instructor")
        screen.press(Key.FOLLOW)
        assertEquals(Types.PERSON, screen.type)
        assertEquals("Tom", (screen.item?.single<String>("name") as? Result.Usable)?.value)
    }

    @Test
    fun `enter goes into a field of the open key`() {
        val screen = person()
        toField(screen, "courses")
        screen.press(Key.OPEN)
        toRow(screen, "Date")
        screen.press(Key.OPEN)
        assertEquals("person / anna / courses / k1 / date", said(screen).first())
    }

    @Test
    fun `escape comes out one step, and lands on what was left`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.DOWN)
        screen.press(Key.OPEN)
        screen.press(Key.CLOSE)
        assertEquals("person / anna / medical", said(screen).first())
        assertEquals("Blood group O+", chosenRow(screen), "back on what was opened")
    }

    @Test
    fun `a key is not a stop of its own on the way out`() {
        val screen = person()
        toField(screen, "courses")
        screen.press(Key.OPEN)
        toRow(screen, "Date")
        screen.press(Key.OPEN)
        screen.press(Key.CLOSE)
        assertEquals("person / anna / courses / k1", said(screen).first())
        assertEquals("Date 2019-06-02", chosenRow(screen), "back on what was opened")
        screen.press(Key.CLOSE)
        assertFalse(screen.opened, "and out again is the list, the keys being no stop")
        assertTrue(screen.running)
    }

    @Test
    fun `enter at a value goes nowhere, there being nothing further in`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        val where = said(screen).first()
        screen.press(Key.OPEN)
        assertEquals(where, said(screen).first())
    }

    @Test
    fun `the bar says what up and down move over here`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertTrue("[^,v] field" in screen.paint(120, 24).last().text)
        screen.press(Key.OPEN)
        assertTrue("[^,v] scroll" in screen.paint(120, 24).last().text)
        val keyed = person()
        toField(keyed, "courses")
        keyed.press(Key.OPEN)
        val bar = keyed.paint(120, 24).last().text
        assertTrue("[(shift)-tab] key" in bar, bar)
        assertTrue("[^,v] field" in bar, bar)
    }

    @Test
    fun `a field holding an item nobody wrote says so rather than opening`() {
        val screen = Screen(
            LogbookReader.read(
                MemoryFileStore(mapOf("person.json" to """{"anna": {}}""")),
                Types.ALL,
            ),
            Types.ALL,
        )
        toTab(screen, "person")
        toField(screen, "medical")
        screen.press(Key.OPEN)
        assertTrue("(empty)" in said(screen), said(screen).toString())
    }

    @Test
    fun `tab stays put inside a field that has no keys to move between`() {
        val screen = person()
        toField(screen, "medical")
        screen.press(Key.OPEN)
        screen.press(Key.OPEN)
        val was = said(screen)
        screen.press(Key.NEXT_TAB)
        screen.press(Key.PREVIOUS_TAB)
        assertEquals(was, said(screen))
    }
}
