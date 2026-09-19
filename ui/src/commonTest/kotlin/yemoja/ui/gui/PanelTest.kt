package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * The agent panel, apart from the drawing of it. See ../../../../../../gui/doc.md — `GUI-38`.
 */
class AgentOfTest {

    @Test
    fun `a command that is one word is what the agent is called`() {
        assertEquals("codex", agentOf("codex"))
        assertEquals("codex", agentOf("  codex  "), "however it was typed")
    }

    @Test
    fun `an agent run through another program is named, not the program`() {
        assertEquals("claude-code-acp", agentOf("npx @zed-industries/claude-code-acp"))
    }

    @Test
    fun `a command named by its path is named without the path`() {
        assertEquals("gemini", agentOf("/usr/local/bin/gemini"))
        assertEquals("gemini.exe", agentOf("C:\\Agents\\gemini.exe"), "and a Windows path too")
    }

    @Test
    fun `an option at the end is not the agent's name`() {
        assertEquals("gemini", agentOf("/usr/local/bin/gemini --experimental-acp"))
    }

    @Test
    fun `nothing typed names nothing`() {
        assertNull(agentOf(""))
        assertNull(agentOf("   "))
        assertNull(agentOf("--acp"), "options only, and no agent among them")
    }
}

class HeardTest {

    @Test
    fun `an answer arriving in pieces reads as one thing said`() {
        var said = heard(emptyList(), "I counted ")
        said = heard(said, "three dives.")
        assertEquals(listOf(Exchange(Turn.AGENT, "I counted three dives.")), said)
    }

    @Test
    fun `an answer follows what was asked rather than joining it`() {
        val asked = listOf(Exchange(Turn.USER, "How many dives?"))
        assertEquals(
            listOf(Exchange(Turn.USER, "How many dives?"), Exchange(Turn.AGENT, "Three.")),
            heard(asked, "Three."),
        )
    }

    @Test
    fun `something said since the last piece ends that answer`() {
        val so = listOf(
            Exchange(Turn.AGENT, "Reading them."),
            Exchange(Turn.WINDOW, refusalOf("read a file")),
        )
        assertEquals(3, heard(so, "Three.").size, "the next piece begins another answer")
        assertEquals(Exchange(Turn.AGENT, "Three."), heard(so, "Three.").last())
    }
}

class RefusalsAfterTest {

    private val refused = listOf("read D:/logbook/dive.json", "run git log")

    @Test
    fun `what has not been said yet is said, as the window's own`() {
        val said = refusalsAfter(refused, 0)
        assertEquals(listOf(Turn.WINDOW, Turn.WINDOW), said.map { it.who })
        assertTrue(said.first().said.startsWith("The agent asked to read D:/logbook"))
    }

    @Test
    fun `what has been said already is not said twice`() {
        assertEquals(1, refusalsAfter(refused, 1).size)
        assertTrue("run git log" in refusalsAfter(refused, 1).single().said)
        assertEquals(emptyList(), refusalsAfter(refused, 2))
    }

    @Test
    fun `a conversation that starts afresh has nothing to say`() {
        assertEquals(emptyList(), refusalsAfter(emptyList(), 0))
    }
}

class SaidByTest {

    @Test
    fun `the user and the agent are named, the window is not`() {
        assertEquals("You", saidBy(Turn.USER, "codex"))
        assertEquals("codex", saidBy(Turn.AGENT, "codex"))
        assertNull(saidBy(Turn.WINDOW, "codex"), "what became of a conversation is not part of it")
    }

    @Test
    fun `an agent whose name is not known is still named`() {
        assertEquals("The agent", saidBy(Turn.AGENT, null))
    }
}

class StoppedOfTest {

    @Test
    fun `an agent that stops part way through says what the machine said`() {
        val said = stoppedOf("the connection was closed")
        assertTrue(said.startsWith("The agent stopped answering: the connection was closed."), said)
        assertTrue("start it afresh" in said, "and what to do about it: $said")
        assertTrue("said nothing about why" in stoppedOf(null))
    }
}

/*
 * A start that finishes after the reader has pressed Stop. `GUI-38`.
 */
class StoppedWhileStartingTest {

    @Test
    fun `a start that finishes says the agent is ready`() {
        val talk = Talk()
        talk.stance = Stance.STARTING
        started(talk, talk.turn, failed = null)
        assertEquals(Stance.READY, talk.stance)
    }

    @Test
    fun `a start stopped while it ran says nothing at all`() {
        val talk = Talk()
        talk.stance = Stance.STARTING
        val turn = talk.turn
        // Stop: the conversation is closed and the panel is back where it started.
        talk.stopped()
        talk.stance = Stance.NONE
        started(talk, turn, failed = null)
        assertEquals(Stance.NONE, talk.stance, "a stopped conversation is not made ready again")
        assertEquals(emptyList(), talk.exchanges)
    }

    @Test
    fun `a start that failed after being stopped says nothing either`() {
        val talk = Talk()
        val turn = talk.turn
        talk.stopped()
        started(talk, turn, failed = "npx could not be started")
        assertEquals(emptyList(), talk.exchanges, "the reader has moved on; this is not their news")
    }

    @Test
    fun `a start that failed says so, and leaves the panel offering to start`() {
        val talk = Talk()
        talk.agent = "npx"
        talk.stance = Stance.STARTING
        started(talk, talk.turn, failed = "npx could not be started")
        assertEquals(Stance.NONE, talk.stance)
        assertEquals(null, talk.agent)
        assertEquals(listOf(Exchange(Turn.WINDOW, "npx could not be started")), talk.exchanges)
    }
}

/*
 * The button on the tab row, and why it is greyed. `GUI-38`.
 */
class UnaskedTest {

    @Test
    fun `an agent can be asked once a logbook is open and a command is set`() {
        assertNull(unaskedOf(logbookOpen = true, hosts = true, command = "codex-acp"))
    }

    @Test
    fun `without a command the reader is sent to the settings`() {
        val said = unaskedOf(logbookOpen = true, hosts = true, command = null)
        assertTrue(said!!.contains("Settings"), said)
        assertEquals(said, unaskedOf(logbookOpen = true, hosts = true, command = "  "), "blank is none")
    }

    @Test
    fun `without a logbook there is nothing to ask about`() {
        assertTrue(unaskedOf(logbookOpen = false, hosts = true, command = "codex-acp")!!.startsWith("Open a logbook"))
    }

    @Test
    fun `a platform that hosts no agent says so first`() {
        assertTrue("desktop" in unaskedOf(logbookOpen = false, hosts = false, command = null)!!)
    }
}
