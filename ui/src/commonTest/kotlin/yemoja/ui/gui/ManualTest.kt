package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * How a chapter is read: the subset of markdown the manual's conventions allow, and no more.
 * See ../../../../../../gui/doc.md — `GUI-15`.
 */
class ManualTest {

    @Test
    fun `a chapter is titled by its first heading and sectioned by its second-level ones`() {
        val text = "# Your logbook\n\nProse.\n\n## Folders\n\n### Deep\n\n## Files\n"
        val chapter = chapterOf("a.md", text)
        assertEquals("Your logbook", chapter.title)
        assertEquals(listOf("Folders", "Files"), chapter.sections.map { it.title })
        assertEquals(listOf("folders", "files"), chapter.sections.map { it.anchor })
        // Where the section starts, so the screen can scroll to it.
        assertIs<Block.Heading>(chapter.blocks[chapter.sections[1].block])
    }

    @Test
    fun `a paragraph is its lines joined, and a blank line ends it`() {
        val blocks = chapterOf("a.md", "One\ntwo.\n\nThree.\n").blocks
        assertEquals(2, blocks.size)
        assertEquals("One two.", plainOf((blocks[0] as Block.Paragraph).text))
        assertEquals("Three.", plainOf((blocks[1] as Block.Paragraph).text))
    }

    @Test
    fun `a list item continues on an indented line`() {
        val text = "- **First.** Says\n  more.\n- Second.\n\n1. Close it.\n2. Keep it\n   valid.\n"
        val blocks = chapterOf("a.md", text).blocks
        val bullets = assertIs<Block.Bullets>(blocks[0])
        assertEquals(listOf("First. Says more.", "Second."), bullets.items.map { plainOf(it) })
        val numbered = assertIs<Block.Numbered>(blocks[1])
        assertEquals(listOf("Close it.", "Keep it valid."), numbered.items.map { plainOf(it) })
    }

    @Test
    fun `fenced code is kept as written, marks and all`() {
        val text = "```\n{\n  \"a\": \"*b*\"\n}\n```\n"
        val code = assertIs<Block.Code>(chapterOf("a.md", text).blocks[0])
        assertEquals("{\n  \"a\": \"*b*\"\n}", code.text)
    }

    @Test
    fun `a table is its header and its rows, and the line of dashes is neither`() {
        val text = "| Unit | Means |\n|---|---|\n| `m` | metres |\n| `ft` | feet |\n"
        val table = assertIs<Block.Table>(chapterOf("a.md", text).blocks[0])
        assertEquals(listOf("Unit", "Means"), table.header.map { plainOf(it) })
        assertEquals(listOf("m", "ft"), table.rows.map { plainOf(it[0]) })
        assertIs<Span.Code>(table.rows[0][0].single())
    }

    @Test
    fun `a rule is a rule`() {
        assertEquals(Block.Rule, chapterOf("a.md", "---\n").blocks.single())
    }

    @Test
    fun `inside a line, the four marks are read and everything else is left alone`() {
        val spans = spansOf("See **this**, *that*, `code` and [there](x.md#s), 2 * 3.")
        assertEquals(
            listOf("See ", "this", ", ", "that", ", ", "code", " and ", "there", ", 2 * 3."),
            spans.map { it.text },
        )
        assertIs<Span.Strong>(spans[1])
        assertIs<Span.Emphasis>(spans[3])
        assertIs<Span.Code>(spans[5])
        assertEquals("x.md#s", assertIs<Span.Link>(spans[7]).target)
        // An asterisk with no closing one is an asterisk.
        assertIs<Span.Plain>(spans[8])
    }

    @Test
    fun `a heading's anchor is spelt the way a link to it is`() {
        assertEquals("how-an-item-is-identified", slugOf("How an item is identified"))
        assertEquals(
            "editing-by-hand--what-to-watch-for",
            slugOf("Editing by hand — what to watch for"),
        )
    }

    @Test
    fun `what is not in the subset reads as prose rather than failing`() {
        val blocks = chapterOf("a.md", "> quoted\n\n<b>html</b>\n").blocks
        val kinds = blocks.map { it::class.simpleName }
        assertTrue(blocks.all { it is Block.Paragraph }, kinds.toString())
    }
}
