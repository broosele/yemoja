package yemoja.ui.api

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import yemoja.ui.gui.Conversation

/*
 * What the panel talks to, which is an agent this machine is running.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */

/**
 * Talking is a conversation with a hosted agent, as the screens ask for one.
 *
 * The window makes one per logbook and hands it to the panel, which knows nothing of processes or
 * protocols. Everything it does is [Hosted]'s; what is here is turning a command a user typed into
 * a program to start, and an agent's stream of updates into the words a panel shows.
 *
 * Not immutable: an agent runs from [start] until [close].
 */
internal class Talking(
    /**
     * The tools this conversation's agent reaches, which it owns.
     *
     * **One socket to a conversation**, so the token an agent was given stops working when the
     * conversation it belonged to ends and a process left running cannot come back to a logbook
     * nobody is talking about. `API-4`. [close] closes it and [start] opens it again, on another
     * port with another token, which is what makes stopping an agent and starting one work.
     */
    private val socket: ToolSocket,
    /** The logbook being talked about. The agent works beside it rather than in it. `API-4`. */
    private val folder: String,
    private val scope: CoroutineScope,
    /** Whether the user allows the agent at the logbook's files, asked whenever it asks. */
    private val direct: () -> Boolean = { false },
    /** Whether the user allows the agent the internet, asked whenever it asks. `API-5`. */
    private val online: () -> Boolean = { false },
    /**
     * Reads the logbook again after a turn in which the agent was allowed at its files, answering
     * what went wrong where it did.
     *
     * The window's, since the Universe is its to hold. Called on the thread [ask] resumes on, which
     * is the panel's. `API-5`.
     */
    private val reread: () -> String? = { null },
) : Conversation {

    private var hosted: Hosted? = null

    /**
     * Starts what [command] names, splitting it the way a shell would on spaces.
     *
     * `npx @zed-industries/claude-code-acp` is a command and three arguments, and a user types it
     * as one line because that is how every agent's own instructions give it.
     */
    override suspend fun start(command: String) {
        // Not close(), which would take the socket with it: this is the same conversation getting
        // another agent, and the tools it reaches are the ones it already had.
        hosted?.close()
        hosted = null
        val words = command.trim().split(SPACES).filter { it.isNotEmpty() }
        require(words.isNotEmpty()) { "an agent should be named, and nothing was" }
        val started = Started(words.first(), words.drop(1))
        val agent = Hosted(started, socket, folder, scope, direct = direct, online = online)
        agent.open()
        hosted = agent
    }

    /**
     * Puts [said] to the agent, handing each piece of its answer to [heard].
     *
     * Only what the agent says in words reaches the panel. Everything else it reports — the tool
     * it is calling, how far a plan has got — is how it is working rather than what it answered,
     * and `GUI-38` gives the panel no place to show it.
     */
    override suspend fun ask(said: String, heard: (String) -> Unit) {
        val agent = hosted ?: error("an agent was asked something before it was started")
        // Direct access ticked at any moment of the turn may have been used, unticked since or not.
        var touched = direct()
        agent.ask(said).collect { event ->
            if (direct()) touched = true
            if (event !is Event.SessionUpdateEvent) return@collect
            val update = event.update
            if (update !is SessionUpdate.AgentMessageChunk) return@collect
            (update.content as? ContentBlock.Text)?.let { heard(it.text) }
        }
        // The agent may have edited the files, and nothing in the window saw it. What will not
        // read again is said in the conversation, since that is where the user is looking.
        if (touched || direct()) reread()?.let { heard("\n\n$it") }
    }

    override suspend fun interrupt() {
        hosted?.interrupt()
    }

    /** Stops the agent and closes the socket with it, the token dying with the conversation. */
    override fun close() {
        hosted?.close()
        hosted = null
        socket.close()
    }

    override val refused: List<String> get() = hosted?.refused.orEmpty()
}

/** What separates a command from its arguments, however many spaces a user typed. */
private val SPACES = Regex("\\s+")
