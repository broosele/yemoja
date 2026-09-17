package yemoja.ui.gui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Settings
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Starting an agent from the panel, and what is remembered of the command.
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
        override fun close() = Unit
        override val refused: List<String> = emptyList()
    }

    @Test
    fun `a command that starts is handed on to be kept`() {
        val kept = ArrayList<String>()
        val said = runBlocking { startedWith(Scripted(), "codex-acp", kept::add) }
        assertNull(said, "nothing to say when it started")
        assertEquals(listOf("codex-acp"), kept)
    }

    @Test
    fun `a command that will not start is said aloud and not kept`() {
        val kept = ArrayList<String>()
        val refusing = Scripted(IOException("CreateProcess error=2"))
        val said = runBlocking { startedWith(refusing, "codx-acp", kept::add) }
        assertTrue(said!!.startsWith("codx-acp could not be started: CreateProcess error=2"), said)
        assertEquals(emptyList(), kept, "a mistyped command is not what the panel opens on next time")
    }
}

class RememberedCommandTest {

    private val store = MemoryFileStore(mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""))

    private val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `an agent that really started leaves its command on this device and nowhere else`() {
        val talking = Talking(
            ToolSocket(Tools(universe), Dispatchers.Default),
            Files.createTempDirectory("yemoja-").toString(),
            scope,
        )
        val watching = watchdog("starting an agent from the panel") { talking.close() }
        // A command naming this machine's own java, which is the kind the rule exists for.
        val java = ProcessHandle.current().info().command().orElse("java")
        val command = "$java -cp ${System.getProperty("java.class.path")} yemoja.ui.api.FakeAgent"
        try {
            val said = runBlocking {
                withTimeout(WAITING) {
                    startedWith(talking, command) { universe.settings.choose(Settings.AGENT_COMMAND, it) }
                }
            }
            assertNull(said, "the fake agent started")
            assertEquals(command, universe.settings.text(Settings.AGENT_COMMAND))
            assertTrue(store.isFile("settings.local.json"), "kept on this device")
            assertTrue("FakeAgent" in store.readText("settings.local.json"))
            assertFalse(store.isFile("settings.json"), "and never where it would travel")
        } finally {
            talking.close()
            watching.interrupt()
        }
    }
}

/** Long enough to start a second JVM, and short enough to fail before the watchdog steps in. */
private const val WAITING = 45_000L
