package yemoja.ui.api

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolKind
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
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `an agent works beside the logbook, is briefed there, and leaves nothing in it`() {
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
            for (name in BRIEFING_NAMES) {
                val written = java.io.File(beside, name)
                assertTrue(written.exists(), "$name should be written where the agent starts")
                assertEquals(socket.briefing(), written.readText(), "and say what the tools say")
            }
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

/*
 * How the relay is started, from the source tree and from an installed copy. `API-4`.
 */
class RelayArgumentsTest {

    @Test
    fun `a java is told the classes and the entry, then the command`() {
        assertEquals(
            listOf("-cp", "a.jar;b.jar", "yemoja.ui.MainKt", "api", "4711"),
            relayArguments("C:\\jdk\\bin\\java.exe", "a.jar;b.jar", "yemoja.ui.MainKt", 4711),
        )
        assertEquals(
            listOf("-cp", "a.jar", "yemoja.ui.MainKt", "api", "4711"),
            relayArguments("/usr/lib/jvm/bin/java", "a.jar", "yemoja.ui.MainKt", 4711),
        )
    }

    @Test
    fun `the installed launcher is given the command alone, since it knows the rest`() {
        assertEquals(
            listOf("api", "4711"),
            relayArguments("C:\\Program Files\\Yemoja\\Yemoja.exe", "x", "yemoja.ui.MainKt", 4711),
        )
        assertEquals(
            listOf("api", "4711"),
            relayArguments("/opt/yemoja/bin/Yemoja", "x", "yemoja.ui.MainKt", 4711),
        )
    }
}

/*
 * Which permission requests are the window's own tools, and so allowed. `API-5`, `GUI-38`.
 */
class OursTest {

    @Test
    fun `a tool the window served is allowed, however an agent joins the names`() {
        for (title in listOf(
            "mcp__yemoja__describe", "yemoja/list", "yemoja.aggregate", "yemoja:stage_set",
            "MCP__Yemoja__staged",
        )) {
            assertTrue(isOurs("yemoja", title), title)
        }
    }

    @Test
    fun `a title that only mentions the server is refused`() {
        for (title in listOf(
            "cat D:/yemoja/dive/2026-06-01#0.json",
            "Write D:/programming/yemoja/dive/2026-06-01#0.json",
            "rm -rf yemoja",
            "yemoja",
            "mcp__yemoja__",
            "mcp__yemoja__describe && rm -rf .",
            "mcp__other__describe",
            "describe",
        )) {
            assertFalse(isOurs("yemoja", title), title)
        }
    }
}

/*
 * Which of the agent's own tools are allowed while the user lets it at the files: those that name
 * where they act, and act inside the logbook or the agent's own folder. `API-5`.
 */
class ReachesOnlyFilesTest {

    private val logbook = Files.createTempDirectory("yemoja-").toFile()

    private val folders = listOf(logbook, java.io.File("${logbook.path}.agent"))

    private fun at(vararg paths: String): List<ToolCallLocation> =
        paths.map { ToolCallLocation(path = it) }

    @Test
    fun `reading, editing, deleting, moving and searching inside the logbook are allowed`() {
        val inside = java.io.File(logbook, "dive/2026-06-01#0.json").path
        for (kind in listOf(
            ToolKind.READ, ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE, ToolKind.SEARCH,
        )) {
            assertTrue(reachesOnlyFiles(kind, at(inside), folders), kind.toString())
        }
        val own = java.io.File("${logbook.path}.agent", "notes.md").path
        assertTrue(reachesOnlyFiles(ToolKind.EDIT, at(own), folders), "its own folder too")
    }

    @Test
    fun `a command is never run, and a tool naming nowhere is held to nothing`() {
        val inside = java.io.File(logbook, "person.json").path
        assertFalse(reachesOnlyFiles(ToolKind.EXECUTE, at(inside), folders))
        assertFalse(reachesOnlyFiles(ToolKind.FETCH, at(inside), folders))
        assertFalse(reachesOnlyFiles(ToolKind.OTHER, at(inside), folders))
        assertFalse(reachesOnlyFiles(null, at(inside), folders))
        assertFalse(reachesOnlyFiles(ToolKind.READ, emptyList(), folders))
        assertFalse(reachesOnlyFiles(ToolKind.READ, null, folders))
    }

    @Test
    fun `the internet is a box of its own, and a fetch waits on it`() {
        val fetch = { online: Boolean ->
            allows(false, ToolKind.FETCH, at("https://example.test"), folders, false, online)
        }
        assertFalse(fetch(false), "off, a fetch is refused with everything else of its own")
        assertTrue(fetch(true), "on, it is answered, whatever it names")
        assertFalse(
            allows(false, ToolKind.EXECUTE, at("https://example.test"), folders, true, true),
            "a command is run for no box",
        )
        val inside = java.io.File(logbook, "person.json").path
        assertFalse(allows(false, ToolKind.READ, at(inside), folders, false, true),
            "the internet says nothing about the files")
        assertTrue(allows(true, ToolKind.OTHER, null, folders, false, false),
            "a tool of ours needs no box")
        // The title is the agent's to write: a command or an edit claiming to be ours is not.
        assertFalse(allows(true, ToolKind.EXECUTE, null, folders, true, true), "a command titled as ours")
        assertFalse(allows(true, ToolKind.EDIT, null, folders, false, false), "an edit titled as ours")
    }

    @Test
    fun `one location outside refuses the whole call, however it is spelled`() {
        val inside = java.io.File(logbook, "person.json").path
        val outside = java.io.File(logbook.parentFile, "elsewhere.json").path
        val climbing = java.io.File(logbook, "../elsewhere.json").path
        assertFalse(reachesOnlyFiles(ToolKind.READ, at(inside, outside), folders))
        assertFalse(reachesOnlyFiles(ToolKind.EDIT, at(climbing), folders))
        assertTrue(within(folders, java.io.File(logbook, "dive/../person.json").path), "resolved")
        val longer = "${logbook.path}-other/person.json"
        assertFalse(within(folders, longer), "a longer name is outside")
    }
}

/*
 * A permission request names a title; what the call is and where it acts were announced earlier.
 */
class CallsTest {

    private val here = ToolCallLocation(path = "D:/dives/mine/dive/2026-06-01#0.json")

    @Test
    fun `an announcement is remembered by id, and a later one fills in what the first left out`() {
        val calls = Calls()
        calls.saw(SessionUpdate.ToolCall(ToolCallId("t1"), "Read File", kind = ToolKind.READ))
        assertEquals(ToolKind.READ, calls.kindOf("t1"))
        assertEquals(null, calls.locationsOf("t1"), "nowhere said yet")
        calls.saw(SessionUpdate.ToolCallUpdate(ToolCallId("t1"), locations = listOf(here)))
        assertEquals(ToolKind.READ, calls.kindOf("t1"), "kept")
        assertEquals(listOf(here), calls.locationsOf("t1"))
        calls.saw(SessionUpdate.ToolCallUpdate(ToolCallId("t1"), locations = emptyList()))
        assertEquals(listOf(here), calls.locationsOf("t1"), "an empty list says nothing new")
    }

    @Test
    fun `a call nobody announced is unknown`() {
        val calls = Calls()
        assertEquals(null, calls.kindOf("t9"))
        assertEquals(null, calls.locationsOf("t9"))
    }
}

/*
 * An agent that starts and then fails: the process goes, and what it said is passed on.
 */
class FailedStartTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `an agent that says nothing and stops is not left running, and is quoted`() {
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        // A program that complains and exits, which is what an agent that will not log in does.
        val java = ProcessHandle.current().info().command().orElse("java")
        val classes = System.getProperty("java.class.path")
        val hosted = Hosted(
            Started(java, listOf("-cp", classes, "yemoja.ui.api.SulkingAgent")),
            socket,
            Files.createTempDirectory("yemoja-").toString(),
            scope,
        )
        val watching = watchdog("a failed start") {
            hosted.close()
            socket.close()
        }
        try {
            val refused = assertFailsWith<Exception> {
                runBlocking { withTimeout(WAITING) { hosted.open() } }
            }
            assertTrue(
                "Run claude /login" in refused.message.orEmpty(),
                "should quote what the agent said, but said: ${refused.message}",
            )
            assertTrue(hosted.complained.isNotEmpty(), "and keeps what it said")
        } finally {
            hosted.close()
            socket.close()
            watching.interrupt()
        }
    }
}

/*
 * Stopping an agent that was started by a launcher, which is what `npx …` is.
 */
class LauncherTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `stopping a launcher stops the agent it started`() {
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        val said = Files.createTempFile("yemoja-", ".pid").toFile()
        val running = ProcessHandle.current().info().command().orElse("java")
        val classes = System.getProperty("java.class.path")
        val hosted = Hosted(
            Started(running, listOf("-cp", classes, "yemoja.ui.api.LaunchingAgent", said.path)),
            socket,
            Files.createTempDirectory("yemoja-").toString(),
            scope,
        )
        val watching = watchdog("stopping a launcher") {
            hosted.close()
            socket.close()
        }
        try {
            runBlocking { withTimeout(WAITING) { hosted.open() } }
            val started = said.readText().trim().toLong()
            val agent = ProcessHandle.of(started).orElseThrow()
            assertTrue(agent.isAlive, "the agent behind the launcher is running")
            hosted.close()
            // Destroying is not instant: the test waits on the process rather than on a clock.
            agent.onExit().orTimeout(WAITING, TimeUnit.MILLISECONDS).join()
            assertFalse(agent.isAlive, "and goes when the launcher does")
        } finally {
            hosted.close()
            socket.close()
            watching.interrupt()
        }
    }
}

/*
 * Finding what a command names on a platform where the ending decides what starts it.
 */
class ResolvedTest {

    private val folder = Files.createTempDirectory("yemoja-").toFile()

    private val endings = listOf(".COM", ".EXE", ".BAT", ".CMD")

    @Test
    fun `a command with no ending finds the shim a shell would find`() {
        val shim = java.io.File(folder, "npx.CMD")
        shim.writeText("@echo off")
        assertEquals(shim.path, resolvedIn("npx", listOf(folder.path), endings))
    }

    @Test
    fun `a command nothing is found for is taken as it was typed`() {
        // The failure a user then sees is the machine's own, which says the command was not found.
        assertEquals("nothing_here", resolvedIn("nothing_here", listOf(folder.path), endings))
    }

    @Test
    fun `a command that names a folder or an ending is left alone`() {
        java.io.File(folder, "npx.CMD").writeText("@echo off")
        assertEquals("npx.cmd", resolvedIn("npx.cmd", listOf(folder.path), endings))
        assertEquals("C:/tools/npx", resolvedIn("C:/tools/npx", listOf(folder.path), endings))
    }
}

/*
 * A program that is not an agent: it starts, and then says nothing. `GUI-38`.
 */
class SilentStartTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `a program that never answers is given up on, stopped, and explained`() {
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        val running = ProcessHandle.current().info().command().orElse("java")
        val classes = System.getProperty("java.class.path")
        val hosted = Hosted(
            Started(running, listOf("-cp", classes, "yemoja.ui.api.SilentAgent")),
            socket,
            Files.createTempDirectory("yemoja-").toString(),
            scope,
            patience = PATIENCE,
        )
        val watching = watchdog("a silent start") {
            hosted.close()
            socket.close()
        }
        try {
            val began = System.currentTimeMillis()
            val refused = assertFailsWith<Exception> {
                runBlocking { withTimeout(WAITING) { hosted.open() } }
            }
            val waited = System.currentTimeMillis() - began
            assertTrue(waited < WAITING, "given up on in $waited ms rather than waiting for ever")
            val why = refused.message.orEmpty()
            assertTrue("did not answer as an agent" in why, why)
            assertTrue("claude-code-acp" in why, "and says what to start instead: $why")
        } finally {
            hosted.close()
            socket.close()
            watching.interrupt()
        }
    }
}

/** How long a silent start is given: enough to start a JVM that then says nothing. */
private const val PATIENCE = 8_000L

/*
 * A command that names nothing this computer has. `GUI-38`.
 */
class NotInstalledTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun letGo() {
        scope.cancel()
    }

    @Test
    fun `a command that is not installed is said to be, in words a user can act on`() {
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        val hosted = Hosted(
            Started("no_such_agent_anywhere"),
            socket,
            Files.createTempDirectory("yemoja-").toString(),
            scope,
        )
        try {
            val refused = assertFailsWith<IllegalStateException> {
                runBlocking { withTimeout(WAITING) { hosted.open() } }
            }
            val why = refused.message.orEmpty()
            assertTrue(why.startsWith("no_such_agent_anywhere is not installed"), why)
            assertTrue("npx comes with Node.js" in why, "and where npx comes from: $why")
        } finally {
            hosted.close()
            socket.close()
        }
    }
}
