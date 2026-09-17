package yemoja.ui.api

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The tools reached the way an agent reaches them, over MCP. See ../../../../../../api/doc.md —
 * `API-4`.
 */
class ToolServerTest {

    private val universe: Universe = MemoryFileStore(
        mapOf(
            "dive/2026-06-01#0.json" to """{"max_depth": 18}""",
            "dive/2026-06-02#0.json" to """{"max_depth": 24}""",
        ),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    /** A client connected to a server over a pair of pipes, and [check] run against it. */
    private fun connected(check: suspend (Client) -> Unit) = runBlocking {
        withTimeout(TIMEOUT) {
            val toServer = PipedOutputStream()
            val fromClient = PipedInputStream(toServer, PIPE)
            val toClient = PipedOutputStream()
            val fromServer = PipedInputStream(toClient, PIPE)
            serve(
                toolServer(Tools(universe), Dispatchers.Default),
                fromClient.asSource().buffered(),
                toClient.asSink().buffered(),
            )
            val client = Client(Implementation("test", "1"))
            val transport =
                StdioClientTransport(fromServer.asSource().buffered(), toServer.asSink().buffered())
            client.connect(transport)
            try {
                check(client)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `the tools are listed, reading ones and staging ones`() = connected { client ->
        assertEquals(
            listOf(
                "describe", "list", "get", "series", "aggregate",
                "stage_set", "stage_add", "stage_delete", "staged",
            ),
            client.listTools().tools.map { it.name },
        )
    }

    @Test
    fun `a staging call is refused while the user has not allowed changes`() = connected { client ->
        val refused = client.callTool("stage_set", mapOf("id" to "2026-06-01#0", "path" to "rating"))
        assertEquals(true, refused.isError)
        val said = (refused.content.single() as TextContent).text
        assertTrue("Allowed to change data" in said, said)
    }

    @Test
    fun `the instructions travel with the server`() = connected { client ->
        assertEquals(INSTRUCTIONS, client.serverInstructions)
    }

    @Test
    fun `a call reaches the tools and answers as they do`() = connected { client ->
        val ids = listOf("2026-06-01#0", "2026-06-02#0")
        val answered = client.callTool(
            "aggregate",
            mapOf("ids" to ids, "path" to "max_depth", "measure" to "mean"),
        )
        assertEquals(false, answered.isError)
        val text = (answered.content.single() as TextContent).text
        assertEquals(Tools(universe).aggregate(ids, "max_depth", "mean").text, text)
    }

    @Test
    fun `a refusal is an error the agent can read`() = connected { client ->
        val refused = client.callTool("get", mapOf("id" to "nobody"))
        assertEquals(true, refused.isError)
        assertTrue("names nothing" in (refused.content.single() as TextContent).text)
    }

    @Test
    fun `the manual's data chapters can be read`() = connected { client ->
        val asked = ReadResourceRequestParams("yemoja://manual/data-fields.md")
        val read = client.readResource(ReadResourceRequest(asked))
        val text = (read.contents.single() as TextResourceContents).text
        assertTrue(text.startsWith("#"), "a chapter of the manual, not ${text.take(40)}")
    }
}

/** Long enough for a slow machine, and short enough that a hung pipe fails rather than waits. */
private const val TIMEOUT = 30_000L

/** How much a pipe holds before a writer waits for the reader. A reply to `describe` is large. */
private const val PIPE = 1 shl 20
