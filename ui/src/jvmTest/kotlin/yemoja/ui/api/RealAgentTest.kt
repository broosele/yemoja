package yemoja.ui.api

import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * One conversation with an agent that has a model behind it.
 *
 * **It does not run unless it is asked to.** Everything else about hosting is proved by an agent
 * that answers without a model, which needs no account and no network — see FakeAgent.kt. This
 * one exists for the question that cannot be answered that way: whether a real model, given these
 * tools and these instructions, finds them usable. It sends a logbook to whoever runs that model
 * and it spends whatever that costs, so it is opt-in and stays that way.
 *
 * Run it with both of these set, from the repository root:
 *
 *     YEMOJA_AGENT="node C:/path/to/claude-code-acp/dist/index.js"
 *     YEMOJA_LOGBOOK="D:/programming/yemoja/fixtures/cousteau"
 *     ./gradlew :ui:jvmTest --tests 'yemoja.ui.api.RealAgentTest' -i
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
class RealAgentTest {

    @Test
    fun `an agent with a model behind it answers about a logbook`() {
        val command = System.getenv(AGENT)
        val folder = System.getenv(LOGBOOK)
        if (command.isNullOrBlank() || folder.isNullOrBlank()) {
            println("$AGENT and $LOGBOOK are not both set, so no real agent was asked anything.")
            return
        }
        val universe = Universe.open(folder)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val socket = ToolSocket(Tools(universe), Dispatchers.Default)
        val words = command.trim().split(Regex("\\s+"))
        val hosted = Hosted(Started(words.first(), words.drop(1)), socket, folder, scope)
        val watching = watchdog("a real agent", seconds = PATIENCE) {
            hosted.close()
            socket.close()
        }
        val said = StringBuilder()
        try {
            runBlocking {
                withTimeout(PATIENCE * 1000) {
                    hosted.open()
                    hosted.ask(ASKED).collect { event ->
                        if (event !is Event.SessionUpdateEvent) return@collect
                        when (val update = event.update) {
                            // What it says, which is the answer.
                            is SessionUpdate.AgentMessageChunk ->
                                (update.content as? ContentBlock.Text)?.let { said.append(it.text) }
                            // Which tool it reached for, which is what this is really watching.
                            is SessionUpdate.ToolCallUpdate ->
                                println("TOOL: ${update.title ?: update.toolCallId.value}")
                            else -> Unit
                        }
                    }
                }
            }
        } finally {
            println("ASKED: $ASKED")
            println("ANSWERED: $said")
            println("REFUSED: ${hosted.refused}")
            for (line in hosted.complained) println("AGENT: $line")
            hosted.close()
            socket.close()
            scope.cancel()
            watching.interrupt()
        }
        assertTrue(said.isNotEmpty(), "a real agent should have said something, and said nothing")
    }
}

/** What to ask. One question, because each one costs the user of this test money. */
private const val ASKED =
    "How many dives does this logbook hold, and which one is the deepest? " +
        "Say what you based it on, and name the deepest dive."

/** The environment variable naming the command that starts an agent. */
private const val AGENT = "YEMOJA_AGENT"

/** The environment variable naming the logbook to ask about. */
private const val LOGBOOK = "YEMOJA_LOGBOOK"

/** How long a model is given. It reads a page at a time, and a logbook is many pages. */
private const val PATIENCE = 600L
