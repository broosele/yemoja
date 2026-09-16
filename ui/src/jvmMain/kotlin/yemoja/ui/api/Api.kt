package yemoja.ui.api

/*
 * The command an agent starts to reach an open window's tools.
 *
 * See ../../../../../../api/doc.md — `API-4`.
 */

/**
 * Relays this process's input and output to the window listening on [port], and answers with what
 * to exit with.
 *
 * What `yemoja api` runs. An agent is told to start it as an MCP server of its own, which is the
 * one kind every agent accepts, and it carries the conversation to the window rather than opening
 * a logbook itself. Nothing about a logbook is here.
 *
 * **The token comes from the environment**, not from the command line, because a command line is
 * something other programs on the machine can read. The window puts it there when it starts the
 * agent.
 */
fun api(port: String): Int {
    val listening = port.toIntOrNull()
    if (listening == null || listening !in 1..PORTS) {
        System.err.println("port should be a port number, but was $port")
        return 2
    }
    val token = System.getenv(TOKEN)
    if (token.isNullOrEmpty()) {
        System.err.println("$TOKEN should hold the token the window gave, and holds nothing")
        return 2
    }
    // **Only the protocol may write to standard output.** A library printing one line there — a
    // logger saying it has started, which is what caught this — is read by the agent as a message
    // and ends the conversation. So the stream is taken away from everything else first, and what
    // anything prints from here goes where it can do no harm.
    val protocol = System.out
    System.setOut(System.err)
    return relay(listening, token, System.`in`, protocol)
}

/** What the window puts the token in when it starts an agent. */
const val TOKEN: String = "YEMOJA_API_TOKEN"

/** The highest port there is. */
private const val PORTS = 65535
