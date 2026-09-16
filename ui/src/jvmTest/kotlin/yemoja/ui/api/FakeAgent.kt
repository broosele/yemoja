package yemoja.ui.api

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.model.McpServer
import com.agentclientprotocol.model.PromptResponse
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation as McpImplementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import java.io.BufferedReader

/*
 * An agent that answers without a model, so that hosting one can be tested with no account, no
 * network and nothing installed.
 *
 * It is a program rather than an object in the test, because what is being tested is that Yemoja
 * starts a process, speaks ACP to it, and gives it a way to reach the logbook. Run as:
 *
 *     java -cp <the test classpath> yemoja.ui.api.FakeAgent <what to ask the tools for>
 *
 * See ../../../../../../api/doc.md — `API-4`, and ../../../../../../gui/doc.md — `GUI-38`.
 */
object FakeAgent {

    /**
     * Speaks ACP over this process's input and output until the window lets go.
     *
     * Whatever it is prompted with, it reads the item of that id through the MCP server the window
     * gave it, and says what came back. That is the whole of the behaviour: a real agent puts a
     * model between the two, and the wiring either side of the model is what this exercises.
     */
    @JvmStatic
    fun main(arguments: Array<String>) {
        // Only the protocol may write here, so nothing else is allowed to. A logger that announces
        // itself on this stream is read as a message, which is what it cost to learn.
        val talking = System.out
        System.setOut(System.err)
        runBlocking {
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val transport = StdioTransport(
                parentScope = scope,
                ioDispatcher = Dispatchers.IO,
                input = linesOf(System.`in`.bufferedReader()),
                output = { line ->
                    talking.write((line + "\n").toByteArray())
                    talking.flush()
                },
            )
            val protocol = Protocol(scope, transport)
            Agent(protocol, Answering())
            protocol.start()
            awaitCancellation()
        }
    }
}

/** An agent that holds one session and knows one trick. */
private class Answering : AgentSupport {

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo =
        AgentInfo(implementation = Implementation("fake", "1"))

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession =
        Reading(sessionParameters)
}

/** A session that reads what it is asked for through the tools it was handed. */
private class Reading(private val parameters: SessionCreationParameters) : AgentSession {

    override val sessionId: SessionId = SessionId("fake")

    override suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement?): Flow<Event> {
        val asked = content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
        val said = try {
            read(asked.trim())
        } catch (refused: Exception) {
            "the tools could not be reached: $refused"
        }
        return flow {
            emit(Event.SessionUpdateEvent(SessionUpdate.AgentMessageChunk(ContentBlock.Text(said))))
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
        }
    }

    /** What `get` answers for [id], through the MCP server the window named. */
    private suspend fun read(id: String): String {
        val named = parameters.mcpServers.filterIsInstance<McpServer.Stdio>().first()
        val process = ProcessBuilder(listOf(named.command) + named.args)
            .also { builder -> named.env.forEach { builder.environment()[it.name] = it.value } }
            .start()
        try {
            val client = Client(McpImplementation("fake", "1"))
            client.connect(
                StdioClientTransport(
                    process.inputStream.asSource().buffered(),
                    process.outputStream.asSink().buffered(),
                ),
            )
            val answered = client.callTool("get", mapOf("id" to id))
            return (answered.content.single() as TextContent).text
        } finally {
            process.destroy()
        }
    }
}

/** The lines [reader] gives, read off the thread that asked for them. */
private fun linesOf(reader: BufferedReader): Flow<String> = flow {
    reader.use {
        while (true) emit(it.readLine() ?: return@use)
    }
}.flowOn(Dispatchers.IO)
