package yemoja.ui.tui

import yemoja.data.ItemSet
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Following a mention written in a remark. `FEAT-23`, on the convention `JSON-23` settles.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

private const val REMARK = "Met @tom_janssen on the wall, on his @quarry_club_diver.\\n" +
    "Mail him on tom@example.invalid."

private fun logbook(): ItemSet = LogbookReader.read(
    MemoryFileStore(
        mapOf(
            "person.json" to """{"tom_janssen": {"first_name": "Tom", "last_name": "Janssen"}}""",
            "certification.json" to """{"quarry_club_diver": {"name": "Quarry Club Diver"}}""",
            "dive/d#0.json" to """{"remarks": "$REMARK"}""",
        ),
    ),
    Types.ALL,
)

/** A screen with the remark open. */
private fun opened(): Screen {
    val screen = screenOver(logbook())
    repeat(screen.rows.size) { if (screen.field?.name != "remarks") screen.press(Key.DOWN) }
    assertEquals("remarks", screen.field?.name)
    screen.press(Key.OPEN)
    return screen
}

private fun said(screen: Screen): List<Line> = screen.paint(80, 24)

/** Every stretch of a painted screen that carries a style. */
private fun styled(screen: Screen, style: Style): List<String> = said(screen)
    .flatMap { it.spans }
    .filter { style in it.styles }
    .map { it.text }

class MentionFollowingTest {

    @Test
    fun `a mention naming an item is underlined where it sits in the sentence`() {
        val marked = styled(opened(), Style.UNDERLINED)
        assertEquals(listOf("@tom_janssen", "@quarry_club_diver"), marked)
    }

    @Test
    fun `an address is left as the text it is, naming nothing`() {
        val whole = said(opened()).joinToString(" ") { it.text }
        assertTrue("tom@example.invalid" in whole, whole)
        assertTrue(styled(opened(), Style.UNDERLINED).none { "example" in it })
    }

    @Test
    fun `up and down move between the mentions, and the bar says so`() {
        val screen = opened()
        assertEquals(listOf("@tom_janssen"), styled(screen, Style.SELECTED))
        screen.press(Key.DOWN)
        assertEquals(listOf("@quarry_club_diver"), styled(screen, Style.SELECTED))
        screen.press(Key.UP)
        assertEquals(listOf("@tom_janssen"), styled(screen, Style.SELECTED))
        assertTrue("[^,v] mention" in said(screen).last().text, said(screen).last().text)
    }

    @Test
    fun `space follows the one the cursor is on`() {
        val screen = opened()
        screen.press(Key.DOWN)
        screen.press(Key.FOLLOW)
        assertEquals("certification", screen.type.name)
        assertEquals(false, screen.opened, "arriving somewhere closes the field left behind")
    }

    @Test
    fun `following the first needs no moving`() {
        val screen = opened()
        screen.press(Key.FOLLOW)
        assertEquals("person", screen.type.name)
    }

    @Test
    fun `a mention keeps its underline when the row it sits on wraps`() {
        // The wrap used to paint every row with the first span's styles, which on a value row
        // is the indent: empty. A reference long enough to need a second row lost its mark.
        val long = "Ran into @tom_janssen halfway along the wall, which is a good deal " +
            "further than anybody expected to get on the ebb."
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "person.json" to """{"tom_janssen": {"first_name": "Tom"}}""",
                    "dive/d#0.json" to """{"remarks": "$long"}""",
                ),
            ),
            Types.ALL,
        )
        val screen = screenOver(set)
        repeat(screen.rows.size) { if (screen.field?.name != "remarks") screen.press(Key.DOWN) }
        screen.press(Key.OPEN)
        val painted = screen.paint(60, 24)
        assertTrue(painted.any { it.spans.size > 2 }, "the remark should wrap over rows")
        assertEquals(listOf("@tom_janssen"), styled(screen, Style.UNDERLINED))
    }

    @Test
    fun `a remark with nothing to follow scrolls as it always did`() {
        val set = LogbookReader.read(
            MemoryFileStore(mapOf("dive/d#0.json" to """{"remarks": "Flat calm all day."}""")),
            Types.ALL,
        )
        val screen = screenOver(set)
        repeat(screen.rows.size) { if (screen.field?.name != "remarks") screen.press(Key.DOWN) }
        screen.press(Key.OPEN)
        assertTrue("[^,v] scroll" in screen.paint(80, 24).last().text)
        assertEquals(emptyList(), styled(screen, Style.UNDERLINED))
    }

    @Test
    fun `a mention nothing answers to is not followable and not marked`() {
        val set = LogbookReader.read(
            MemoryFileStore(mapOf("dive/d#0.json" to """{"remarks": "Ask @nobody about it."}""")),
            Types.ALL,
        )
        val screen = screenOver(set)
        repeat(screen.rows.size) { if (screen.field?.name != "remarks") screen.press(Key.DOWN) }
        screen.press(Key.OPEN)
        assertEquals(emptyList(), styled(screen, Style.UNDERLINED))
        screen.press(Key.FOLLOW)
        assertEquals("dive", screen.type.name, "nothing to go to, so nothing moved")
    }
}
