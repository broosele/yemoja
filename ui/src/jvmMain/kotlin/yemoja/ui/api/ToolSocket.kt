package yemoja.ui.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.coroutines.CoroutineContext

/*
 * How an agent's own process reaches the tools inside the running window.
 *
 * See ../../../../../../api/doc.md — `API-4`.
 */

/**
 * ToolSocket is the window's end of the relay: the tools, on a port only this machine can reach.
 *
 * An agent starts `yemoja api`, which connects here and passes messages both ways. That command
 * is an MCP server over its own input and output, which every agent must accept, and this is what
 * it relays to, so the tools run against the Universe the window already has open. `API-4`.
 *
 * **A caller must hold the token.** The port is on the loopback address, which every other program
 * on the machine can also reach, so a connection says the token first and is dropped where it does
 * not match. One is made for each of these.
 *
 * Every call is carried onto [onto], the thread the Universe lives on.
 *
 * Not immutable: it listens from [open] until [close].
 */
class ToolSocket(private val tools: Tools, private val onto: CoroutineContext) {

    private var listening: ServerSocket? = null

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * What a connection says first to show it was started by this window.
     *
     * Made afresh every time this is opened, so an agent left over from a conversation that ended
     * cannot come back to one that has begun since.
     */
    var token: String = tokenOf()
        private set

    /** The port to reach it on, or absent before it is open. */
    val port: Int? get() = listening?.localPort

    /** What an agent is told before it is asked anything, for writing where agents read it. */
    fun briefing(): String = tools.briefing()

    /**
     * Listens on a port the machine chooses, and answers which.
     *
     * Each connection is served a session of its own, so a second conversation is a second
     * connection rather than a second logbook.
     *
     * **It opens again after being closed**, with another port and another token: stopping an
     * agent and starting one is two conversations, and the second is not owed the first's. Opening
     * one that is already open changes nothing and answers the port it is on.
     */
    fun open(): Int {
        listening?.let { return it.localPort }
        token = tokenOf()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val socket = ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK))
        listening = socket
        scope.launch {
            while (!socket.isClosed) {
                val connection = try {
                    socket.accept()
                } catch (closed: java.io.IOException) {
                    return@launch
                }
                scope.launch { hold(connection) }
            }
        }
        return socket.localPort
    }

    /** Stops listening, and lets go of whatever is connected. [open] starts it again. */
    fun close() {
        listening?.close()
        listening = null
        scope.cancel()
    }

    /** Serves one connection, once it has said the token. */
    private suspend fun hold(connection: Socket) {
        connection.use {
            val input = it.getInputStream()
            if (lineOf(input) != token) return
            val ended = CompletableDeferred<Unit>()
            val session = serve(
                toolServer(tools, onto),
                input.asSource().buffered(),
                it.getOutputStream().asSink().buffered(),
            )
            session.onClose { ended.complete(Unit) }
            // The session reads on its own, so this waits for it: leaving here would close the
            // socket under a conversation that has not finished.
            ended.await()
        }
    }

    private companion object {

        /** How many connections may wait to be accepted. A conversation or two, never a crowd. */
        const val BACKLOG = 4
    }
}

/**
 * Passes messages between this process's [input] and [output] and the window listening on [port].
 *
 * What `yemoja api` does, and the whole of it: the agent talks MCP to this process, and every byte
 * goes to the window and back. Nothing here reads a logbook or knows what a tool is.
 *
 * Answers when either end closes, with what the command should exit with.
 */
fun relay(port: Int, token: String, input: InputStream, output: OutputStream): Int {
    val connection = try {
        Socket(InetAddress.getByName(LOOPBACK), port)
    } catch (refused: java.io.IOException) {
        System.err.println("no window is listening on $port: ${refused.message}")
        return 1
    }
    connection.use {
        val upward = it.getOutputStream()
        upward.write((token + "\n").toByteArray())
        upward.flush()
        val downward = Thread { copy(it.getInputStream(), output) }
        downward.isDaemon = true
        downward.start()
        copy(input, upward)
        // Whichever direction stops first ends the other: the window has nothing more to say to
        // an agent that has gone, and closing is what wakes a read that would otherwise wait for
        // ever. Waiting on the other thread without this is a hang rather than a slow finish.
        it.close()
        downward.join(LETTING_GO)
    }
    return 0
}

/** Everything [from] gives to [to], a block at a time, until either end is done with it. */
private fun copy(from: InputStream, to: OutputStream) {
    val block = ByteArray(BLOCK)
    try {
        while (true) {
            val read = from.read(block)
            if (read < 0) return
            to.write(block, 0, read)
            to.flush()
        }
    } catch (ended: java.io.IOException) {
        return
    }
}

/** How much is carried at once. A page of dives is larger than this and goes in several. */
private const val BLOCK = 8 * 1024

/** How long the other direction is given to notice the socket closed, in milliseconds. */
private const val LETTING_GO = 2_000L

/** The first line [from] gives, without its ending, or absent where it gives none. */
private fun lineOf(from: InputStream): String? {
    val said = StringBuilder()
    while (said.length < TOKEN_LENGTH * 2) {
        val read = from.read()
        if (read < 0) return null
        if (read == '\n'.code) return said.toString().trim()
        said.append(read.toChar())
    }
    return null
}

/** A token nobody can guess, made afresh for each socket. */
private fun tokenOf(): String {
    val bytes = ByteArray(TOKEN_BYTES)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { byte ->
        ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
    }
}

/** How many bytes a token is made of, before it is written as hexadecimal. */
private const val TOKEN_BYTES = 24

/** How long a token is written, which is what bounds what is read looking for one. */
private const val TOKEN_LENGTH = TOKEN_BYTES * 2

/** Only this machine reaches the socket, and only through the loopback; the relay too. */
private const val LOOPBACK = "127.0.0.1"
