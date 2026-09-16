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
import com.agentclientprotocol.model.ReadTextFileResponse
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.WriteTextFileResponse
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Not immutable: it holds a process from [open] until [close].
 */
class Hosted(
    private val started: Started,
    private val socket: ToolSocket,
    /** Where the agent is to work, which is the logbook's folder. */
    private val folder: String,
    private val scope: CoroutineScope,
) {

    private var running: Process? = null

    private var talking: ClientSession? = null

    private val refusals = ArrayList<String>()

    /** Every request of the agent's own that was refused, most recent last. `GUI-38`. */
    val refused: List<String> get() = refusals

    /**
     * Starts the agent and opens a conversation with it.
     *
     * Throws where the agent cannot be started, which is a fault of the machine rather than of the
     * logbook: an agent that is not installed, or a command the settings name wrongly.
     */
    suspend fun open() {
        val port = socket.port ?: socket.open()
        val process = ProcessBuilder(listOf(started.command) + started.arguments)
            .directory(java.io.File(folder))
            .start()
        running = process
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
            SessionCreationParameters(cwd = folder, mcpServers = listOf(relaying(port))),
        ) { _, _ -> Refusing(refusals) }
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

    /** Stops the agent, and stops listening for it. */
    fun close() {
        running?.destroy()
        running = null
        talking = null
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
    }
}

/**
 * Refusing is a client that gives an agent nothing of the machine it is running on.
 *
 * Every request is turned down and noted in [refused], so the window can say what an agent tried
 * to do. The tools are the only way to the logbook. `API-5`, `GUI-38`.
 */
private class Refusing(private val refused: MutableList<String>) : ClientSessionOperations {

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        refused += toolCall.title ?: toolCall.toolCallId.value
        return RequestPermissionResponse(RequestPermissionOutcome.Cancelled)
    }

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

/** The lines [reader] gives, read off the thread that asked for them. */
private fun linesOf(reader: BufferedReader): Flow<String> = flow {
    reader.use {
        while (true) emit(it.readLine() ?: return@use)
    }
}.flowOn(Dispatchers.IO)

/** The java this application is running on, which is what runs the relay beside it. */
private fun javaOf(): String =
    ProcessHandle.current().info().command().orElse("java")
