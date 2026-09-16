package yemoja.ui.api

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetAddress
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The relay an agent reaches an open window through. See ../../../../../../api/doc.md — `API-4`.
 */
class ToolSocketTest {

    private val universe: Universe = MemoryFileStore(
        mapOf("dive/2026-06-01#0.json" to """{"max_depth": 18}"""),
    ).let { Universe(LogbookReader.read(it, Types.ALL), null, it, null, null) }

    private fun socketOf(): ToolSocket = ToolSocket(Tools(universe), Dispatchers.Default)

    @Test
    fun `an agent reaches the tools through the relay, as it would through its own process`() {
        val socket = socketOf()
        val watching = watchdog("the relay test") { socket.close() }
        val port = socket.open()
        // What the agent writes to and reads from. `yemoja api` has these as its own input and
        // output, and relays both to the window.
        val toRelay = PipedOutputStream()
        val relaysInput = PipedInputStream(toRelay, PIPE)
        val relaysOutput = PipedOutputStream()
        val fromRelay = PipedInputStream(relaysOutput, PIPE)
        val relaying = Thread { relay(port, socket.token, relaysInput, relaysOutput) }
        relaying.isDaemon = true
        relaying.start()
        try {
            runBlocking {
                withTimeout(TIMEOUT) {
                    val client = Client(Implementation("test", "1"))
                    client.connect(
                        StdioClientTransport(
                            fromRelay.asSource().buffered(),
                            toRelay.asSink().buffered(),
                        ),
                    )
                    val answered = client.callTool("get", mapOf("id" to "2026-06-01#0"))
                    val text = (answered.content.single() as TextContent).text
                    assertTrue(""""max_depth": 18""" in text, text)
                    assertEquals(INSTRUCTIONS, client.serverInstructions, "and the instructions")
                    client.close()
                }
            }
        } finally {
            socket.close()
            watching.interrupt()
        }
    }

    @Test
    fun `a connection that does not know the token is dropped`() {
        val socket = socketOf()
        val watching = watchdog("the token test") { socket.close() }
        val port = socket.open()
        try {
            Socket(InetAddress.getByName("127.0.0.1"), port).use {
                it.getOutputStream().write("not the token\n".toByteArray())
                it.getOutputStream().flush()
                it.soTimeout = TIMEOUT.toInt()
                assertEquals(-1, it.getInputStream().read(), "should be closed, and said nothing")
            }
        } finally {
            socket.close()
            watching.interrupt()
        }
    }

    @Test
    fun `the port is the machine's own, and is given up when the window lets go`() {
        val socket = socketOf()
        val watching = watchdog("the closing test") { socket.close() }
        val port = socket.open()
        assertEquals(port, socket.port)
        socket.close()
        assertEquals(null, socket.port)
        // Nothing is listening once it is closed, so connecting is refused rather than accepted.
        val nothing = ByteArrayInputStream(ByteArray(0))
        assertEquals(1, relay(port, socket.token, nothing, ByteArrayOutputStream()))
        watching.interrupt()
    }
}

/** Long enough for a slow machine, and short enough that a hung pipe fails rather than waits. */
private const val TIMEOUT = 30_000L

/** How much a pipe holds before a writer waits for the reader. */
private const val PIPE = 1 shl 20
