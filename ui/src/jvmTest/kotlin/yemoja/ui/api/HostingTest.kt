package yemoja.ui.api

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * Hosting an agent: starting one, handing it the logbook's tools, and refusing it everything else.
 *
 * The agent here answers without a model — see FakeAgent.kt — so this runs with no account and
 * nothing installed. What it proves is the wiring: a process is started, ACP is spoken to it, and
 * the tools it reaches are this window's.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
class HostingTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val socket = ToolSocket(Tools(universe), Dispatchers.Default)

    private val folder: String = Files.createTempDirectory("yemoja-").toString()

    /** The fake agent, started the way a real one is: a command the window runs. */
    private fun hosting(): Hosted = Hosted(
        Started(
            ProcessHandle.current().info().command().orElse("java"),
            listOf("-cp", System.getProperty("java.class.path"), "yemoja.ui.api.FakeAgent"),
        ),
        socket,
        folder,
        scope,
    )

    @AfterTest
    fun letGo() {
        socket.close()
        scope.cancel()
    }

    @Test
    fun `an agent is started, and reads the logbook through the tools it is handed`() {
        val hosted = hosting()
        val watching = watchdog("hosting an agent") {
            hosted.close()
            socket.close()
        }
        try {
            val said = runBlocking {
                withTimeout(WAITING) {
                    hosted.open()
                    val events = hosted.ask("2026-06-01#0").toList()
                    events.filterIsInstance<Event.SessionUpdateEvent>()
                        .map { it.update }
                        .filterIsInstance<SessionUpdate.AgentMessageChunk>()
                        .joinToString("") { (it.content as ContentBlock.Text).text }
                }
            }
            // The dive as this window holds it, fetched by the agent's own process through
            // `yemoja api` and answered by the tools against the Universe open here.
            assertTrue(""""max_depth": 18""" in said, said)
            assertTrue(""""revision": 0""" in said, "and the revision travels with it: $said")
        } finally {
            hosted.close()
            watching.interrupt()
        }
    }

    @Test
    fun `an agent works beside the logbook, and leaves nothing in it`() {
        val hosted = hosting()
        val watching = watchdog("starting an agent") {
            hosted.close()
            socket.close()
        }
        val logbook = java.io.File(folder)
        val held = logbook.list().orEmpty().toSet()
        try {
            runBlocking { withTimeout(WAITING) { hosted.open() } }
            val beside = java.io.File("$folder.agent")
            assertTrue(java.io.File(beside, LEFT_BEHIND).exists(), "should work in $beside")
            assertEquals(held, logbook.list().orEmpty().toSet(), "and leave the logbook alone")
        } finally {
            hosted.close()
            watching.interrupt()
        }
    }

    @Test
    fun `nothing of the machine is given to it, and nothing was asked for`() {
        val hosted = hosting()
        val watching = watchdog("refusing an agent") {
            hosted.close()
            socket.close()
        }
        try {
            runBlocking { withTimeout(WAITING) { hosted.open() } }
            // This agent asks for no file and runs no command, so the list is empty. What the test
            // holds to is that the window is the one keeping it: an agent that did ask would be
            // refused and named here rather than quietly served. `GUI-38`.
            assertEquals(emptyList(), hosted.refused)
        } finally {
            hosted.close()
            watching.interrupt()
        }
    }
}

/** Long enough to start a second JVM, and short enough to fail before the watchdog steps in. */
private const val WAITING = 30_000L

/*
 * The same agent reached the way the panel reaches one: through the port the screens hold.
 */
class TalkingTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun talkingOf(): Talking = Talking(
        ToolSocket(Tools(universe), Dispatchers.Default),
        Files.createTempDirectory("yemoja-").toString(),
        scope,
    )

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `a command typed as one line is started, and what it says comes back in pieces`() {
        val talking = talkingOf()
        val watching = watchdog("talking to an agent") { talking.close() }
        val heard = StringBuilder()
        try {
            runBlocking {
                withTimeout(WAITING) {
                    // Typed the way an agent's own instructions give it: a command and arguments,
                    // on one line, with the spaces a user happens to type.
                    val java = ProcessHandle.current().info().command().orElse("java")
                    val classes = System.getProperty("java.class.path")
                    talking.start("$java  -cp $classes  yemoja.ui.api.FakeAgent")
                    talking.ask("2026-06-01#0") { heard.append(it) }
                }
            }
            assertTrue(""""max_depth": 18""" in heard.toString(), heard.toString())
            assertEquals(emptyList(), talking.refused)
        } finally {
            talking.close()
            watching.interrupt()
        }
    }

    @Test
    fun `closing a conversation closes its tools, so a stale agent cannot come back`() {
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        val port = socket.open()
        val talking = Talking(socket, Files.createTempDirectory("yemoja-").toString(), scope)
        talking.close()
        assertEquals(null, socket.port, "the socket goes with the conversation")
        val nothing = ByteArrayInputStream(ByteArray(0))
        val answered = relay(port, socket.token, nothing, ByteArrayOutputStream())
        assertEquals(1, answered, "and nobody answers where the window was listening")
    }

    @Test
    fun `nothing is asked of an agent that was never started`() {
        val talking = talkingOf()
        val refused = assertFailsWith<IllegalStateException> {
            runBlocking { talking.ask("anything") {} }
        }
        assertEquals("an agent was asked something before it was started", refused.message)
    }
}
