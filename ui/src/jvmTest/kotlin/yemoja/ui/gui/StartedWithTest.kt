package yemoja.ui.gui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.ui.api.Talking
import yemoja.ui.api.ToolSocket
import yemoja.ui.api.Tools
import yemoja.ui.api.watchdog
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Starting an agent from the panel.
 *
 * On the JVM because a real start is a second process: the fake agent in ../api/FakeAgent.kt,
 * which speaks the protocol with no model and no account. See ../../../../../../gui/doc.md —
 * `GUI-38`.
 */
class StartedWithTest {

    /** A conversation that starts or refuses as told, and remembers what it was asked to start. */
    private class Scripted(private val refusing: Exception? = null) : Conversation {
        var started: String? = null
        override suspend fun start(command: String) {
            refusing?.let { throw it }
            started = command
        }
        override suspend fun ask(said: String, heard: (String) -> Unit) = Unit
        override suspend fun interrupt() = Unit
        override fun close() = Unit
        override val refused: List<String> = emptyList()
    }

    @Test
    fun `a command that starts has nothing to say`() {
        val scripted = Scripted()
        val said = runBlocking { startedWith(scripted, "codex-acp") }
        assertNull(said, "nothing to say when it started")
        assertEquals("codex-acp", scripted.started)
    }

    @Test
    fun `a command that will not start is said aloud, in the machine's words`() {
        val refusing = Scripted(IOException("CreateProcess error=2"))
        val said = runBlocking { startedWith(refusing, "codx-acp") }
        assertTrue(said!!.startsWith("codx-acp could not be started: CreateProcess error=2"), said)
    }

    @Test
    fun `the fake agent, started the way the panel starts one, starts`() {
        val talking = Talking(
            ToolSocket(Tools(universe), Dispatchers.Default),
            Files.createTempDirectory("yemoja-").toString(),
            scope,
        )
        val watching = watchdog("starting an agent from the panel") { talking.close() }
        val java = ProcessHandle.current().info().command().orElse("java")
        val command = "$java -cp ${System.getProperty("java.class.path")} yemoja.ui.api.FakeAgent"
        try {
            val said = runBlocking { withTimeout(WAITING) { startedWith(talking, command) } }
            assertNull(said, "the fake agent started")
        } finally {
            talking.close()
            watching.interrupt()
        }
    }

    private val store = MemoryFileStore(mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""))

    private val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }
}

/** Long enough to start a second JVM, and short enough to fail before the watchdog steps in. */
private const val WAITING = 45_000L
