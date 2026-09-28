package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Talking to an agent about the logbook. See ../../../../../../gui/doc.md — `GUI-38`.
 */
class ConversingTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
                "dive/2026-06-01#0.json" to """{"dive_site": "@blue", "max_depth": 18}""",
            ),
        ),
        Types.ALL,
    )

    @Test
    fun `what is said while an agent is starting and while it is answering`() {
        assertEquals("Starting codex…", sayingOf(Stance.STARTING, "codex"))
        assertEquals("Starting the agent…", sayingOf(Stance.STARTING, null))
        assertTrue(sayingOf(Stance.ANSWERING, "codex")!!.startsWith("Working on your question."))
        assertNull(sayingOf(Stance.NONE, "codex"), "the panel offers to start, and says nothing")
        assertNull(sayingOf(Stance.READY, "codex"), "and nothing while it waits to be asked")
    }

    @Test
    fun `an agent that will not start says so in the machine's own words`() {
        val said = failedOf("claude-code-acp", "no such file or directory")
        assertTrue(said.startsWith("claude-code-acp could not be started: no such file"), said)
        assertTrue("you install yourself" in said, "and whose job it is to install one: $said")
        assertTrue("said nothing about why" in failedOf("codex", null))
    }

    @Test
    fun `a request of the agent's own that was refused is said aloud`() {
        val said = refusalOf("read D:/logbook/dive/2026-06-01#0.json")
        assertTrue(said.startsWith("The agent asked to read D:/logbook"), said)
        assertTrue("no other way" in said)
    }

    @Test
    fun `a mention that resolves is shown as the item's name and leads to it`() {
        val parts = partsOf("The deepest was @2026-06-01#0, at Blue Hole.", set)
        val words = listOf("The deepest was ", "2026-06-01#0", ", at Blue Hole.")
        assertEquals(words, parts.map { it.text })
        assertEquals(listOf(null, "2026-06-01#0", null), parts.map { it.leadsTo })
    }

    @Test
    fun `a mention of a named item reads as its name`() {
        val parts = partsOf("Try @blue again.", set)
        assertEquals(listOf("Try ", "Blue Hole", " again."), parts.map { it.text })
        assertEquals("blue", parts[1].leadsTo, "and leads to the site")
    }

    @Test
    fun `what resolves to nothing stays as it was written`() {
        val parts = partsOf("Mail me on tom@example.invalid about @nobody.", set)
        assertEquals(1, parts.size, "one run of text, since neither names anything")
        assertEquals("Mail me on tom@example.invalid about @nobody.", parts.single().text)
        assertNull(parts.single().leadsTo)
    }

    @Test
    fun `an answer with nothing in it is no parts at all`() {
        assertEquals(emptyList(), partsOf("", set).map { it.text })
    }
}
