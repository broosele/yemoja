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
 * **It is given the logbook's tools and nothing else.** The session names `yemoja api` as an MCP
 * server of its own, which is how the agent reaches [socket], and every request the agent makes to
 * read a file, write one or run a command is refused. An agent meant for writing code would
 * otherwise edit the logbook's files directly and walk around every rule the tools hold it to.
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
) {

    private var running: Process? = null

    private val complaints = ArrayDeque<String>()

    private var talking: ClientSession? = null

    private val refusals = ArrayList<String>()

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
        ) { _, _ -> Refusing(NAME, refusals) }
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
        return session.prompt(listOf(ContentBlock.Text(said)))
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
        args = listOf(
            "-cp",
            System.getProperty("java.class.path"),
            ENTRY,
            "api",
            port.toString(),
        ),
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

/**
 * Refusing is a client that gives an agent nothing of the machine it is running on, and the
 * logbook's tools freely.
 *
 * **An agent asks permission for the tools we gave it as well as for its own**, so refusing
 * everything refuses the logbook: a real one asked to call `describe` and was turned down, and
 * the fake one in the tests never asks at all, which is how that got as far as it did. What is
 * ours is allowed, because the user opening the panel is the consent for it and nothing it holds
 * can write. Everything else — a file read, a file written, a command run — is turned down and
 * noted in [refused], so the window can say what the agent tried to do. `API-5`, `GUI-38`.
 *
 * [server] is what the tool server calls itself, which is what its tools are named after.
 */
private class Refusing(
    private val server: String,
    private val refused: MutableList<String>,
) : ClientSessionOperations {

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        val called = toolCall.title ?: toolCall.toolCallId.value
        if (!ours(called)) {
            refused += called
            return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
        }
        // Standing where it is offered: the tools are read-only, and a conversation that asks
        // about eight hundred dives would otherwise ask the user four hundred times.
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
        refused += "read $path"
        throw NotImplementedError("Yemoja reads no files for an agent: the tools are the way in")
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        refused += "write $path"
        throw NotImplementedError("Yemoja writes no files for an agent: the tools are the way in")
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

/** The java this application is running on, which is what runs the relay beside it. */
private fun javaOf(): String =
    ProcessHandle.current().info().command().orElse("java")
