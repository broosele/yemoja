package yemoja.ui.api

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EnvVariable
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.McpServer
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.ReadTextFileResponse
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.ToolCallLocation
import com.agentclientprotocol.model.ToolKind
import com.agentclientprotocol.model.WriteTextFileResponse
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader

/*
 * The window's half of hosting an agent: starting the one the user picked, and giving it the
 * logbook's tools and nothing else.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */

/**
 * Started is what to run to get an agent, as the user's settings name it.
 *
 * Immutable.
 */
data class Started(val command: String, val arguments: List<String> = emptyList())

/**
 * Hosted is one agent the window is running, and the conversation with it.
 *
 * The agent logs itself in, so whether it answers from a subscription, an API key or a model on
 * this machine is between the user and whoever it belongs to. Nothing here holds a key. `GUI-38`.
 *
 * **It is given the logbook's tools, and of the machine only what the boxes allow.** The session
 * names `yemoja api` as an MCP server of its own, which is how the agent reaches [socket]. A request
 * of the agent's own is refused unless a box allows it — files within the logbook for *Allow raw
 * file access*, a fetch for *Allow internet access* — and a command is refused always. An agent
 * meant for writing code would otherwise edit the logbook's files directly and walk around every
 * rule the tools hold it to. `API-5`.
 *
 * **It works beside the logbook rather than in it.** An agent treats the folder it is started in
 * as its own and writes there — the first real one left a file recording which tools it had been
 * allowed — and a user's dives are not a scratch directory. So it is given the logbook's path
 * with `.agent` after it, which puts it outside the logbook exactly as staging an import does.
 * `RECON-1`.
 *
 * Not immutable: it holds a process from [open] until [close].
 */
class Hosted(
    private val started: Started,
    private val socket: ToolSocket,
    /** The logbook being talked about. The agent is not started here and cannot read it. */
    private val folder: String,
    private val scope: CoroutineScope,
    /**
     * How long a started program is given to answer the protocol, in milliseconds.
     *
     * A program that is not an agent starts and says nothing: `claude` on its own is Claude Code's
     * terminal, which reads its input and waits, and so would this, for ever, saying *Starting*.
     * Long enough for a real agent to load — some are large programs on a cold start — and no
     * longer, since a user is watching.
     */
    private val patience: Long = HANDSHAKE,
    /**
     * Whether the user allows the agent at the logbook's files, asked whenever it asks.
     *
     * Off, its own requests to read or write a file and its own tools that would are refused. On,
     * those that stay inside the logbook or its own folder are allowed, and nothing else is: a
     * command is never run for it. `API-5`.
     */
    private val direct: () -> Boolean = { false },
    /**
     * Whether the user allows the agent the internet, asked whenever it asks.
     *
     * Off, a tool of its own that fetches is refused with the rest. On, it is allowed and nothing
     * here reads what comes back: a fetch names a URL and the answer is somebody else's. `API-5`.
     */
    private val online: () -> Boolean = { false },
) {

    private var running: Process? = null

    private val complaints = ArrayDeque<String>()

    private var talking: ClientSession? = null

    private val refusals = ArrayList<String>()

    private val calls = Calls()

    /** Every request of the agent's own that was refused, most recent last. `GUI-38`. */
    val refused: List<String> get() = refusals

    /**
     * The last few lines the agent wrote to its error stream, oldest first.
     *
     * An agent that will not start, or that refuses a session, says why here and nowhere else: the
     * protocol carries `Internal error` and the reason goes to the stream a terminal would have
     * shown. Kept to [COMPLAINTS] lines, since some agents log every request there, and read by
     * whoever has to tell the user what went wrong.
     */
    val complained: List<String> get() = complaints.toList()

    /**
     * Starts the agent and opens a conversation with it.
     *
     * Throws where the agent cannot be started, which is a fault of the machine rather than of the
     * logbook: an agent that is not installed, or a command the settings name wrongly.
     *
     * **What fails after the process is up takes the process with it, and says what the agent
     * said.** An agent that is installed but not logged in starts, refuses the session, and
     * answers `Internal error` over the protocol while writing the reason to its error stream —
     * which is where the reason to show a user is. Leaving it running would leave one stray
     * process behind every failed start.
     */
    suspend fun open() {
        val port = socket.port ?: socket.open()
        val working = workingBeside(folder)
        brief(working)
        val process = try {
            ProcessBuilder(listOf(resolved(started.command)) + started.arguments)
                .directory(working)
                .start()
        } catch (missing: java.io.IOException) {
            throw IllegalStateException(notFoundOf(missing), missing)
        }
        running = process
        listen(process)
        try {
            withTimeout(patience) { beganOrDied(process, port, working.path) }
        } catch (waited: TimeoutCancellationException) {
            close()
            throw failedBy(IllegalStateException(silentOf()))
        } catch (refused: Exception) {
            close()
            throw failedBy(refused)
        }
    }

    /**
     * Writes what the agent is to know into the folder it is started in, before it is started.
     *
     * Agents read a file of their own name from where they run — Claude Code `CLAUDE.md`, Codex
     * and others `AGENTS.md` — and take it as standing instructions. The same text is served as
     * the tool server's instructions and answered by `guide`, but an instructions field is one
     * some agents never show their model, and a tool has to be called: a file in the working
     * folder reaches the model before it is asked anything. Written afresh at every start, so it
     * cannot be older than the running version. `API-4`.
     */
    private fun brief(working: java.io.File) {
        val briefing = socket.briefing()
        for (name in BRIEFING_NAMES) java.io.File(working, name).writeText(briefing)
    }

    /**
     * [began], unless the process stops first.
     *
     * A program that exits — not logged in, wrong arguments, not an agent at all — would
     * otherwise be waited for until the patience ran out, since a request nobody will answer looks
     * exactly like one not answered yet. Its going is noticed instead, and said with its code.
     */
    private suspend fun beganOrDied(process: Process, port: Int, working: String) {
        coroutineScope {
            val begun = async { began(process, port, working) }
            val died = async(Dispatchers.IO) { runInterruptible { process.waitFor() } }
            select<Unit> {
                begun.onAwait { died.cancel() }
                died.onAwait { code ->
                    begun.cancel()
                    throw IllegalStateException(
                        "${started.command} stopped with code $code before it answered as an " +
                            "agent",
                    )
                }
            }
        }
    }

    /**
     * What to say of a command the machine could not find or run.
     *
     * The machine says `CreateProcess error=2, The system cannot find the file specified`, which
     * is true and tells a user nothing they can do. What they can do is install the program or
     * name where it is, and `npx` in particular comes with Node.js, which is not installed by
     * default on anything.
     */
    private fun notFoundOf(missing: java.io.IOException): String =
        "${started.command} is not installed on this computer, or is not where programs are " +
            "looked for. Install it, or type the full path to it. (npx comes with Node.js.) " +
            "The machine said: ${missing.message}"

    /**
     * What to say of a program that started and never spoke the protocol.
     *
     * The likeliest cause is named, because the likeliest cause is a real one: the command an
     * agent's own documentation gives for using it in a terminal is not the one that speaks to
     * other programs, and a user has no way to know that from a panel that says *Starting*.
     */
    private fun silentOf(): String =
        "${started.command} started but did not answer as an agent within ${patience / 1000} " +
            "seconds. An agent has to speak the Agent Client Protocol, and a program made for a " +
            "terminal does not: for Claude Code, start its adapter, " +
            "npx @zed-industries/claude-code-acp, rather than claude itself"

    /** Everything after the process is running: the protocol, and a session on it. */
    private suspend fun began(process: Process, port: Int, working: String) {
        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = linesOf(process.inputStream.bufferedReader()),
            output = { line ->
                process.outputStream.write((line + "\n").toByteArray())
                process.outputStream.flush()
            },
        )
        // The protocol is what is started, not the transport: starting it is what begins handling
        // what arrives, and it starts the transport itself. Starting the transport alone leaves an
        // agent talking to something that never answers.
        val protocol = Protocol(scope, transport)
        val client = Client(protocol)
        protocol.start()
        client.initialize(ClientInfo(implementation = Implementation(NAME, VERSION)))
        talking = client.newSession(
            SessionCreationParameters(
                cwd = working,
                mcpServers = listOf(relaying(port)),
            ),
        ) { _, _ ->
            val reach = listOf(java.io.File(folder), workingBeside(folder))
            Refusing(NAME, refusals, reach, direct, online, calls)
        }
    }

    /**
     * [refused] with what the agent said for itself, where it said anything.
     *
     * The protocol carries `Internal error` and no more, so the words a user can act on are the
     * ones the agent wrote to its error stream. The last few are enough: the rest is logging.
     */
    private fun failedBy(refused: Exception): Exception {
        val said = complaints.filter { it.isNotBlank() }.takeLast(SAID)
        if (said.isEmpty()) return refused
        val words = said.joinToString(" ")
        return IllegalStateException("${refused.message}. ${started.command} said: $words")
    }

    /**
     * Puts [said] to the agent, and answers with what comes back as it comes.
     *
     * The agent's tool calls happen while this runs, so a question about the whole logbook takes as
     * long as the reading does.
     */
    suspend fun ask(said: String): Flow<Event> {
        val session = talking ?: error("nothing was asked of an agent that is not open")
        // Noted on the way past, so that a permission request naming only a title can be held to
        // what its call was announced to be and where. `API-5`.
        return session.prompt(listOf(ContentBlock.Text(said))).onEach { event ->
            if (event is Event.SessionUpdateEvent) calls.saw(event.update)
        }
    }

    /**
     * Asks the agent to stop the answer under way, and nothing else.
     *
     * The protocol's own cancel: the agent ends the turn where it has got to, its process and
     * its session stay, and the flow [ask] answered with completes. Nothing where no agent is
     * open, since there is nothing to interrupt.
     */
    suspend fun interrupt() {
        talking?.cancel()
    }

    /**
     * Stops the agent, and stops listening for it.
     *
     * **Everything it started goes with it.** A command like `npx …` or a `.cmd` on Windows is a
     * launcher: the process this started is not the agent, and destroying it alone leaves the
     * agent running, holding whatever account session it opened and the relay it started beside
     * it. The tools are closed either way, so a survivor could do nothing to the logbook, but a
     * process nobody can see is not something to leave behind.
     */
    fun close() {
        running?.let { process ->
            process.descendants().forEach { it.destroy() }
            process.destroy()
        }
        running = null
        talking = null
    }

    /**
     * Keeps what the agent writes to its error stream, and keeps reading it.
     *
     * Reading matters whether or not anybody looks: a process whose error stream fills up stops,
     * and an agent that logs every request would fill one in a minute.
     */
    private fun listen(process: Process) {
        val reading = Thread {
            process.errorStream.bufferedReader().forEachLine {
                if (complaints.size >= COMPLAINTS) complaints.removeFirst()
                complaints.addLast(it)
            }
        }
        reading.isDaemon = true
        reading.start()
    }

    /** How the agent is told to reach this window: by starting the relay `API-4` describes. */
    private fun relaying(port: Int): McpServer.Stdio = McpServer.Stdio(
        name = NAME,
        command = javaOf(),
        args = relayArguments(javaOf(), System.getProperty("java.class.path"), ENTRY, port),
        // The token goes in the environment rather than the arguments, which other programs on
        // the machine can read.
        env = listOf(EnvVariable(TOKEN, socket.token)),
    )

    private companion object {

        /** What the window calls itself to an agent. */
        const val NAME = "yemoja"

        /** What version of the client it is. */
        const val VERSION = "1"

        /** Where the application starts, which is what the relay is run through. */
        const val ENTRY = "yemoja.ui.MainKt"

        /** How many lines of an agent's complaining are kept. Enough to say what went wrong. */
        const val COMPLAINTS = 50

        /** How many of those are shown to a user when a start fails. */
        const val SAID = 5

        /** How long a start is given before it is called silent, in milliseconds. */
        const val HANDSHAKE = 30_000L
    }
}

/** The names agents read standing instructions from, in the folder they are started in. */
internal val BRIEFING_NAMES: List<String> = listOf("CLAUDE.md", "AGENTS.md")

/**
 * Refusing is a client that gives an agent nothing of the machine it is running on, and the
 * logbook's tools freely.
 *
 * **An agent asks permission for the tools we gave it as well as for its own**, so refusing
 * everything refuses the logbook: a real one asked to call `describe` and was turned down, and
 * the fake one in the tests never asks at all, which is how that got as far as it did. What is
 * ours is allowed, because the user opening the panel is the consent for it, and its writing only
 * stages. Everything else is judged by [allows] against the boxes, and what is turned down is noted
 * in [refused], so the window can say what the agent tried to do. `API-5`, `GUI-38`.
 *
 * [server] is what the tool server calls itself, which is what its tools are named after.
 */
private class Refusing(
    private val server: String,
    private val refused: MutableList<String>,
    /** The logbook and the agent's own folder, which is as far as direct access reaches. */
    private val folders: List<java.io.File>,
    private val direct: () -> Boolean,
    private val online: () -> Boolean,
    /** What each tool call was announced as, which its permission request does not repeat. */
    private val calls: Calls,
) : ClientSessionOperations {

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        val called = toolCall.title ?: toolCall.toolCallId.value
        // The request itself carries a title and little else: what kind of tool this is and where
        // it will act were said when the call was announced, so they are looked up by its id.
        val kind = toolCall.kind ?: calls.kindOf(toolCall.toolCallId.value)
        val locations = toolCall.locations ?: calls.locationsOf(toolCall.toolCallId.value)
        if (!allows(ours(called), kind, locations, folders, direct(), online())) {
            refused += called
            return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        }
        // Standing where it is offered: what is allowed here is allowed for the conversation,
        // and one that asks about eight hundred dives would otherwise ask four hundred times.
        val allowed = permissions.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ALWAYS }
            ?: permissions.firstOrNull { it.kind == PermissionOptionKind.ALLOW_ONCE }
            ?: return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        return RequestPermissionResponse(RequestPermissionOutcome.Selected(allowed.optionId))
    }

    private fun ours(called: String): Boolean = isOurs(server, called)

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse {
        if (!direct() || !within(folders, path)) {
            refused += "read $path"
            throw NotImplementedError(NOT_FOR_AGENTS)
        }
        val lines = java.io.File(path).readLines()
        val from = line?.toInt()?.minus(1)?.coerceIn(0, lines.size) ?: 0
        val upTo = limit?.toInt()?.let { (from + it).coerceAtMost(lines.size) } ?: lines.size
        return ReadTextFileResponse(lines.subList(from, upTo).joinToString("\n"))
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        if (!direct() || !within(folders, path)) {
            refused += "write $path"
            throw NotImplementedError(NOT_FOR_AGENTS)
        }
        java.io.File(path).writeText(content)
        return WriteTextFileResponse()
    }

    override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) = Unit
}

/**
 * The folder an agent works in, beside the logbook at [folder], made where it is not there.
 *
 * The logbook's own path with `.agent` after it, which is how an import's staging folder is named
 * and for the same reason: what is in it is not part of the logbook and must not be read as
 * though it were.
 */
private fun workingBeside(folder: String): java.io.File =
    java.io.File(folder.trimEnd('/', '\\') + BESIDE).also { it.mkdirs() }

/** What an agent's own folder is called, beside the logbook. */
private const val BESIDE = ".agent"

/**
 * Whether a permission request titled [called] is for one of the tools the window named [server]
 * gave the agent.
 *
 * **The whole title has to be a tool's name**, joined to the server's the way agents join them:
 * `mcp__yemoja__describe`, `yemoja/describe`, `yemoja.describe`. A title that only *mentions* the
 * server is not one of ours — a command that reads `D:/yemoja/dive/2026-06-01#0.json` mentions it
 * too, and allowing that would let an agent edit the logbook's files and walk around every rule
 * the tools keep. A name given some other way is refused, which is the safe way to be wrong.
 */
internal fun isOurs(server: String, called: String): Boolean {
    val title = called.trim()
    return TOOL_NAMES.any { tool ->
        JOINS.any { join -> title.equals("$server$join$tool", ignoreCase = true) } ||
            title.equals("mcp__${server}__$tool", ignoreCase = true)
    }
}

/** How agents join a server's name to one of its tools' names. */
private val JOINS = listOf("__", "/", ".", ":")

/**
 * Calls is what each of the agent's tool calls was announced as: its kind, and where it acts.
 *
 * An agent announces a call with its kind and locations, and then asks permission for it with its
 * id and a title. Claude Code's adapter does exactly that, so a rule about kinds and locations has
 * to remember the announcement. A later announcement fills in what an earlier one left out, since
 * the first often comes before the agent has said where. `API-5`.
 *
 * Not immutable: it grows as the agent works. Read and written from different threads, so its map
 * is a concurrent one.
 */
internal class Calls {

    private val known = java.util.concurrent.ConcurrentHashMap<String, Announced>()

    fun saw(update: SessionUpdate) {
        when (update) {
            is SessionUpdate.ToolCall ->
                note(update.toolCallId.value, update.kind, update.locations)
            is SessionUpdate.ToolCallUpdate ->
                note(update.toolCallId.value, update.kind, update.locations)
            else -> Unit
        }
    }

    private fun note(id: String, kind: ToolKind?, locations: List<ToolCallLocation>?) {
        known.compute(id) { _, was ->
            Announced(
                kind ?: was?.kind,
                locations?.takeIf { it.isNotEmpty() } ?: was?.locations,
            )
        }
    }

    fun kindOf(id: String): ToolKind? = known[id]?.kind

    fun locationsOf(id: String): List<ToolCallLocation>? = known[id]?.locations

    private class Announced(val kind: ToolKind?, val locations: List<ToolCallLocation>?)
}

/**
 * Whether a request the agent makes is answered rather than cancelled.
 *
 * A tool of Yemoja's own is always answered: the tools are what an agent is given. Of its own,
 * one that stays at the files is answered where the user allows the files, and one that fetches
 * where they allow the internet. A fetch is the one kind whose reach is nobody's to check — what
 * it asks for is a URL and what comes back is somebody else's — so the box is the whole of the
 * answer. Everything else is refused, a command above all. `API-5`.
 */
internal fun allows(
    ours: Boolean,
    kind: ToolKind?,
    locations: List<ToolCallLocation>?,
    folders: List<java.io.File>,
    direct: Boolean,
    online: Boolean,
): Boolean = when {
    // The title is the agent's to write, so it is trusted only for the kind our tools arrive
    // as: a command or a file edit titled `yemoja/describe` is judged as what it is.
    ours && (kind == null || kind == ToolKind.OTHER) -> true
    direct && reachesOnlyFiles(kind, locations, folders) -> true
    online && kind == ToolKind.FETCH -> true
    else -> false
}

/**
 * Whether a tool of the agent's own, of [kind] and touching [locations], stays at the files in
 * [folders] and does nothing else.
 *
 * Reading, editing, deleting, moving and searching are what direct access means, and each names
 * where it will act. Running a command names nothing and can do anything, so it is refused however
 * it is titled; so is a tool that names no location, since there is nothing to hold it to.
 * `API-5`.
 */
internal fun reachesOnlyFiles(
    kind: ToolKind?,
    locations: List<ToolCallLocation>?,
    folders: List<java.io.File>,
): Boolean {
    if (kind !in AT_FILES) return false
    if (locations.isNullOrEmpty()) return false
    return locations.all { within(folders, it.path) }
}

/** What an agent is told when it asks the window for a file it may not have. */
private const val NOT_FOR_AGENTS =
    "Yemoja reads and writes no files for an agent unless the user allows it: the tools are the " +
        "way in"

/** The kinds of tool that act on files they name. */
private val AT_FILES: Set<ToolKind> =
    setOf(ToolKind.READ, ToolKind.EDIT, ToolKind.DELETE, ToolKind.MOVE, ToolKind.SEARCH)

/** Whether [path] lies in one of [folders], with `..` and links resolved first. */
internal fun within(folders: List<java.io.File>, path: String): Boolean {
    val asked = try {
        java.io.File(path).canonicalFile.toPath()
    } catch (unreadable: java.io.IOException) {
        return false
    }
    return folders.any { asked.startsWith(it.canonicalFile.toPath()) }
}

/**
 * [command] as something this machine can start, which on Windows is not always what was typed.
 *
 * A shell there finds `npx` by trying each ending in `PATHEXT` against each folder in `PATH`, and
 * what it finds is `npx.cmd`. Nothing does that for a process started directly, so an agent's own
 * instructions, and this application's manual, would be wrong for the first platform it runs on.
 * A command naming a folder of its own, or already carrying an ending, is left as it was typed,
 * and so is anything nothing is found for: the failure to show a user is then the one the machine
 * gives rather than one invented here.
 */
private fun resolved(command: String): String {
    if (!onWindows()) return command
    return resolvedIn(
        command,
        (System.getenv("PATH") ?: "").split(';'),
        (System.getenv("PATHEXT") ?: WINDOWS_ENDINGS).split(';'),
    )
}

/**
 * [command] found in [folders] with one of [endings] after it, or [command] where it is not.
 *
 * Apart from what reads the machine, so that it can be tried against folders a test made. A
 * command naming a folder of its own, or already carrying an ending, is taken as it was typed.
 */
internal fun resolvedIn(command: String, folders: List<String>, endings: List<String>): String {
    if ('/' in command || '\\' in command || '.' in command) return command
    for (folder in folders.filter { it.isNotBlank() }) {
        for (ending in endings.filter { it.isNotBlank() }) {
            val found = java.io.File(folder, command + ending)
            if (found.isFile) return found.path
        }
    }
    return command
}

/** Whether this is the platform where a command's ending decides what starts it. */
private fun onWindows(): Boolean =
    System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/** What Windows tries when it is not told, which is what a shell falls back to. */
private const val WINDOWS_ENDINGS = ".COM;.EXE;.BAT;.CMD"

/** The lines [reader] gives, read off the thread that asked for them. */
private fun linesOf(reader: BufferedReader): Flow<String> = flow {
    reader.use {
        while (true) emit(it.readLine() ?: return@use)
    }
}.flowOn(Dispatchers.IO)

/**
 * The program this application is running in, which is what runs the relay beside it.
 *
 * A `java` from the source tree, or the installer's own launcher in an installed copy.
 */
private fun javaOf(): String =
    ProcessHandle.current().info().command().orElse("java")

/**
 * What [program] is given to start the relay to [port].
 *
 * A Java executable is told where the classes are and which to run. The installer's launcher
 * must not be: it knows both already, and hands everything it is given to the application, which
 * read `-cp` as a command it did not know and exited before an agent could reach a single tool.
 * An installed runtime holds no `java.exe` to use instead. `API-4`.
 */
internal fun relayArguments(
    program: String,
    classPath: String,
    entry: String,
    port: Int,
): List<String> {
    val name = program.substringAfterLast('/').substringAfterLast('\\')
        .substringBeforeLast('.').lowercase()
    val relay = listOf("api", port.toString())
    return if (name in JAVAS) listOf("-cp", classPath, entry) + relay else relay
}

/** What a Java executable is called, without an ending. */
private val JAVAS = setOf("java", "javaw")
