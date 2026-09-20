package yemoja.ui.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import yemoja.data.ItemSet

/*
 * The agent panel: the user's own agent, asked about the logbook, beside whatever tab is showing.
 *
 * The words it says and the conversation behind it are Conversing.kt's; this draws them.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */

/**
 * Talk is what the panel holds while it is open: an agent, what has been said, and what is typed.
 *
 * It lives as long as the panel does and no longer. **Nothing of a conversation is kept**, so
 * closing the panel is the end of it. The command that starts an agent is not the panel's to hold:
 * it is a setting, and the panel reads it when it starts one. `GUI-38`, `GUI-42`.
 *
 * Not immutable.
 */
internal class Talk {

    /** What the agent running is called, or absent where none is running. */
    var agent: String? by mutableStateOf(null)

    var stance: Stance by mutableStateOf(Stance.NONE)

    /**
     * Which conversation this is, counted up whenever one is stopped.
     *
     * Starting and asking both answer on a coroutine, and what they answer is stale where the
     * reader has pressed Stop meanwhile: a start that finishes after a stop would say the agent is
     * ready when the conversation it belonged to is closed. Each turn keeps the number it began
     * with and says nothing where it has moved.
     */
    var turn: Int = 0
        private set

    /** Counts this conversation done, so nothing it started still speaks for the panel. */
    fun stopped() {
        turn += 1
    }

    /** The conversation so far, oldest first. */
    var exchanges: List<Exchange> by mutableStateOf(emptyList())

    /** What is being typed to ask next. */
    var question: String by mutableStateOf("")

    /**
     * Whether an agent may stage changes at all, which is the other box and starts off too.
     *
     * Staging is not changing: what an agent stages waits for somebody to look at it. The box is
     * what stands between an agent and the logbook all the same, and the write tools refuse with
     * a reply naming it while it is off. `API-5`, `RECON-8`.
     */
    var writing: Boolean by mutableStateOf(false)

    /**
     * Whether an agent may read and edit the logbook's files itself, the second box, off at the
     * start of every conversation.
     *
     * The last resort the tools are not: what it edits is not staged and not reviewed, and the
     * window reads the logbook again when its turn ends. `API-5`.
     */
    var direct: Boolean by mutableStateOf(false)

    /** How many of the agent's refused requests have been shown. `GUI-38`. */
    var shown: Int by mutableStateOf(0)
}

/**
 * The agent panel, beside whichever tab is showing.
 *
 * [conversing] is asked for a conversation when the panel opens and is given the box the user
 * ticks, which the tools read on every call rather than once. What an agent names is followed
 * through [onFollow], the same way a reference in a field is, so a dive it mentions opens in its
 * own tab while the conversation carries on. `GUI-38`.
 */
@Composable
internal fun Panel(
    set: ItemSet,
    conversing: (writing: () -> Boolean, direct: () -> Boolean) -> Conversation,
    /** The command that starts the agent, as the settings hold it. `GUI-42`. */
    command: String,
    /** How many items an agent has staged, waiting to be reviewed. */
    staged: Int,
    /** What a review came to, which the conversation records as the window's own turn. */
    told: Told?,
    /** Opens the review, which is on the home screen where an import's is. */
    onReview: () -> Unit,
    /**
     * Said when a turn ends, an agent having staged whatever it staged while it answered.
     *
     * What a review is showing goes out of date while somebody reads it, and nothing below this
     * layer announces a change. `DATA-6`, `RECON-8`.
     */
    onTurn: () -> Unit,
    onFollow: (String) -> Unit,
    onClose: () -> Unit,
) {
    val talk = remember { Talk() }
    val conversation = remember { conversing({ talk.writing }, { talk.direct }) }
    // Whatever was said before this panel opened has been read already: a panel opened again is
    // not a conversation carried on, and would otherwise begin with old news.
    val before = remember { told }
    LaunchedEffect(told) {
        if (told != null && told !== before) talk.exchanges += Exchange(Turn.WINDOW, told.said)
    }
    DisposableEffect(conversation) { onDispose { conversation.close() } }
    val scope = rememberCoroutineScope()
    // Opening the panel is asking for the agent: the button that opens it is greyed until a
    // command is set, so there is nothing a Start of its own would wait for. Start is offered
    // again only after Stop, or after a start that failed. `GUI-38`.
    LaunchedEffect(conversation) {
        if (command.isNotBlank()) start(talk, conversation, scope, command)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(talk.exchanges) { scroll.scrollTo(scroll.maxValue) }
    Column(modifier = Modifier.width(PANEL).fillMaxHeight().padding(GAP)) {
        Heading(talk, conversation, scope, command, onClose)
        HorizontalDivider()
        // A view of its own, so what is copied out of a conversation is the conversation.
        // `GUI-36`.
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Selectable {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
                    for (exchange in talk.exchanges) {
                        Spoken(exchange, talk.agent, set, onFollow)
                    }
                }
            }
        }
        sayingOf(talk.stance, talk.agent)?.let { Aside(it) }
        if (staged > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stagedLineOf(staged),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReview) { Text("Review") }
            }
        }
        Asking(talk, conversation, scope, onTurn)
    }
}

/**
 * The panel's title, naming the agent, and beside it the deed that stops it and the one that
 * closes the panel.
 *
 * *Stop* becomes *Start* after a stop or a failed start, which is the way to try again once the
 * command has been mended. The title names the agent the command names whether or not one is
 * running, so the row does not change shape as the agent comes and goes.
 */
@Composable
private fun Heading(
    talk: Talk,
    conversation: Conversation,
    scope: CoroutineScope,
    command: String,
    onClose: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Ask ${agentOf(command) ?: "the agent"}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (talk.stance == Stance.NONE) {
            TextButton(
                onClick = { start(talk, conversation, scope, command) },
                enabled = command.isNotBlank(),
            ) { Text("Start") }
        } else {
            TextButton(onClick = { stop(talk, conversation) }) { Text("Stop") }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** What to ask next, and what the agent is allowed to be told while it answers. */
@Composable
private fun Asking(
    talk: Talk,
    conversation: Conversation,
    scope: CoroutineScope,
    onTurn: () -> Unit,
) {
    val canAsk = talk.stance == Stance.READY && talk.question.isNotBlank()
    Compact(
        value = talk.question,
        onChange = { talk.question = it },
        hint = "Ask about your logbook",
        lines = 3,
        // Enter asks, as it does in any chat; Shift and Enter is the way to a new line. Enter is
        // taken even where nothing can be asked yet, so a stray press does not start a new line
        // that is sent with the question.
        modifier = Modifier.onPreviewKeyEvent { pressed ->
            val enter = pressed.type == KeyEventType.KeyDown && pressed.key == Key.Enter
            if (!enter || pressed.isShiftPressed) return@onPreviewKeyEvent false
            if (canAsk) ask(talk, conversation, scope, onTurn)
            true
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HALF),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Boxed("Allow changes", talk.writing) { talk.writing = it }
            Boxed("Allow file access", talk.direct) { talk.direct = it }
        }
        if (talk.stance == Stance.ANSWERING) {
            // The protocol's cancel: the agent stops where it is and stays running, and the
            // answer so far stays on the screen. Stop is the way to be rid of the agent itself.
            Button(onClick = { scope.launch { conversation.interrupt() } }) { Text("Interrupt") }
        } else {
            Button(
                onClick = { ask(talk, conversation, scope, onTurn) },
                enabled = canAsk,
            ) { Text("Ask") }
        }
    }
}

/** One box an agent's permissions are ticked in, which the whole row toggles. */
@Composable
private fun Boxed(label: String, ticked: Boolean, onTick: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onTick(!ticked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = onTick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One thing that was said, under whose it was. */
@Composable
private fun Spoken(exchange: Exchange, agent: String?, set: ItemSet, onFollow: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = HALF)) {
        saidBy(exchange.who, agent)?.let { who ->
            Text(
                text = who,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        // Only an agent cites items. What the user typed is their own words, and what the window
        // says names nothing it has not already named.
        val parts = remember(exchange, set) {
            if (exchange.who == Turn.AGENT) {
                partsOf(exchange.said, set)
            } else {
                listOf(Part(exchange.said))
            }
        }
        Says(parts, quiet = exchange.who == Turn.WINDOW, onFollow = onFollow)
    }
}

/** What was said, with the items it named leading to them. */
@Composable
private fun Says(parts: List<Part>, quiet: Boolean, onFollow: (String) -> Unit) {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    val said = buildAnnotatedString {
        for (part in parts) {
            val to = part.leadsTo
            if (to == null) {
                append(part.text)
            } else {
                withLink(LinkAnnotation.Clickable(to, link) { onFollow(to) }) { append(part.text) }
            }
        }
    }
    Text(
        text = said,
        style = MaterialTheme.typography.bodyMedium,
        color = if (quiet) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

/** Start the agent named, and say so where it will not start. */
private fun start(
    talk: Talk,
    conversation: Conversation,
    scope: CoroutineScope,
    typed: String,
) {
    val command = typed.trim()
    talk.agent = agentOf(command)
    talk.stance = Stance.STARTING
    talk.exchanges = emptyList()
    talk.shown = 0
    talk.writing = false
    talk.direct = false
    val turn = talk.turn
    scope.launch { started(talk, turn, startedWith(conversation, command)) }
}

/**
 * What a start that has finished does to the panel, where the conversation is still the same one.
 *
 * **A turn that was stopped says nothing.** Starting answers on a coroutine, and a reader who
 * presses Stop while an agent is starting has closed the conversation already: letting the start
 * finish the job would leave the panel saying an agent is ready when its tools are shut and its
 * process is gone. [failed] is what to say where it would not start, or absent where it did.
 */
internal fun started(talk: Talk, turn: Int, failed: String?) {
    if (turn != talk.turn) return
    if (failed == null) {
        talk.stance = Stance.READY
        return
    }
    talk.exchanges += Exchange(Turn.WINDOW, failed)
    talk.agent = null
    talk.stance = Stance.NONE
}

/** Starts what [command] names in [conversation], and answers what to say where it would not start. */
internal suspend fun startedWith(conversation: Conversation, command: String): String? {
    try {
        conversation.start(command)
    } catch (refused: Exception) {
        // Wider than a RuntimeException on purpose: a command nobody has installed fails where
        // the process is started, and that is an IOException.
        return failedOf(command, refused.message)
    }
    return null
}

/**
 * Why the agent cannot be asked, or absent where it can.
 *
 * Said over the greyed button, so a reader who cannot press it learns where to go without leaving
 * the tab they are on. `GUI-38`.
 */
internal fun unaskedOf(logbookOpen: Boolean, hosts: Boolean, command: String?): String? = when {
    !hosts -> "An agent is run on a desktop, and this is not one."
    !logbookOpen -> "Open a logbook first: an agent is asked about one."
    command.isNullOrBlank() -> "Set the command that starts your agent in Settings, on the home screen."
    else -> null
}

/**
 * Stop the agent, leaving what was said.
 *
 * The conversation stays on the screen to be read and copied; it is the agent that has gone.
 * Starting another clears it, that being another conversation.
 */
private fun stop(talk: Talk, conversation: Conversation) {
    conversation.close()
    talk.stopped()
    talk.agent = null
    talk.stance = Stance.NONE
    talk.shown = 0
}

/** Put what is typed to the agent, and take the answer as it arrives. */
private fun ask(
    talk: Talk,
    conversation: Conversation,
    scope: CoroutineScope,
    onTurn: () -> Unit,
) {
    val said = talk.question.trim()
    talk.question = ""
    talk.exchanges += Exchange(Turn.USER, said)
    talk.stance = Stance.ANSWERING
    val turn = talk.turn
    scope.launch {
        try {
            conversation.ask(said) { piece ->
                if (turn != talk.turn) return@ask
                talk.exchanges = heard(talk.exchanges, piece)
                talk.take(conversation.refused)
            }
        } catch (stopped: Exception) {
            if (turn == talk.turn) {
                talk.exchanges += Exchange(Turn.WINDOW, stoppedOf(stopped.message))
            }
        }
        // Stopped while it was answering: the conversation is gone and nothing here speaks for it.
        if (turn != talk.turn) return@launch
        talk.take(conversation.refused)
        talk.stance = Stance.READY
        // An agent stages while it answers, so what a review is showing is out of date the moment
        // a turn ends. `RECON-8`.
        onTurn()
    }
}

/** Say whatever the agent has been refused since the last time it was said. */
private fun Talk.take(refused: List<String>) {
    exchanges += refusalsAfter(refused, shown)
    shown = refused.size
}

/**
 * What to call the agent [command] starts, or absent where it names none.
 *
 * The last word that is not an option, without the folders, the publisher or the program's
 * ending in front of or after it. An agent is run in whatever way its own instructions give, and
 * the part of that a reader recognises is its name. A file called `index.js` or `main.py` names
 * nothing a reader knows, so the folders above it are read instead, skipping the ones a build
 * puts there; a word that still names nothing gives way to the word before it.
 *
 * Examples: `npx @zed-industries/claude-code-acp` is claude-code-acp;
 * `/usr/local/bin/gemini --experimental-acp` is gemini; `C:\Agents\gemini.exe` is gemini; and
 * `C:\node\node.exe C:\acp\@zed-industries\claude-code-acp\dist\index.js` is claude-code-acp.
 */
internal fun agentOf(command: String): String? {
    val words = command.trim().split(SPACES).filter { it.isNotEmpty() && !it.startsWith("-") }
    for (word in words.asReversed()) nameIn(word)?.let { return it }
    // Every word is a generic file with nothing above it, so the last is taken as it is: a
    // program somebody called `agent` is still called agent.
    return words.lastOrNull()?.let { stemOf(segmentsOf(it).lastOrNull().orEmpty()) }
}

/** The name in one word of a command, read as a path, or absent where it holds none. */
private fun nameIn(word: String): String? {
    val segments = segmentsOf(word)
    val stem = stemOf(segments.lastOrNull() ?: return null)
    if (stem.lowercase() !in GENERIC_FILES) return stem.removePrefix("@").ifEmpty { null }
    val above = segments.dropLast(1).dropLastWhile { it.lowercase() in GENERIC_FOLDERS }
    return above.lastOrNull()?.removePrefix("@")?.ifEmpty { null }
}

/** The folders and file of [word] read as a path, without a drive letter. */
private fun segmentsOf(word: String): List<String> =
    word.split('/', '\\').filter { it.isNotEmpty() && !it.endsWith(':') }

/** [file] without an ending that says how it is run. */
private fun stemOf(file: String): String {
    val ending = file.substringAfterLast('.', "").lowercase()
    return if (ending in ENDINGS) file.substringBeforeLast('.') else file
}

/** Endings that say how a program is run and nothing about which it is. */
private val ENDINGS = setOf("exe", "cmd", "bat", "sh", "ps1", "js", "mjs", "cjs", "py")

/** File names a reader learns nothing from, so the folder above them is the name instead. */
private val GENERIC_FILES =
    setOf("index", "main", "cli", "app", "agent", "server", "start", "run")

/** Folders a build or a package manager puts between a package and its entry file. */
private val GENERIC_FOLDERS = setOf("dist", "build", "bin", "lib", "out", "src", "node_modules")

/** What separates a command from its arguments, however many spaces were typed. */
private val SPACES = Regex("\\s+")

/**
 * [exchanges] with [piece] added to what the agent is saying.
 *
 * An answer arrives in pieces and reads as one thing said, so a piece joins the answer already
 * there. Anything said since — a refusal the window reported — ends that answer, and the next
 * piece begins another.
 */
internal fun heard(exchanges: List<Exchange>, piece: String): List<Exchange> {
    val last = exchanges.lastOrNull()
    if (last == null || last.who != Turn.AGENT) return exchanges + Exchange(Turn.AGENT, piece)
    return exchanges.dropLast(1) + Exchange(Turn.AGENT, last.said + piece)
}

/**
 * The refusals past the [shown] already said, as turns of the window's own.
 *
 * What an agent was refused only ever grows while it runs, so how many have been said is all
 * that has to be remembered to say the rest. `GUI-38`.
 */
internal fun refusalsAfter(refused: List<String>, shown: Int): List<Exchange> =
    refused.drop(shown).map { Exchange(Turn.WINDOW, refusalOf(it)) }

/**
 * Who said something, as the conversation names them, or absent where nobody did.
 *
 * The window's own turns are not attributed. They are not part of the conversation: they say what
 * became of it.
 */
internal fun saidBy(who: Turn, agent: String?): String? = when (who) {
    Turn.USER -> "You"
    Turn.AGENT -> agent ?: "The agent"
    Turn.WINDOW -> null
}

/** What to say where an agent stopped part way through answering. */
internal fun stoppedOf(why: String?): String =
    "The agent stopped answering: ${why ?: "it said nothing about why"}. " +
        "Ask again, or stop it and start it afresh."

/** How wide the panel is, beside whatever tab is showing. */
private val PANEL = 360.dp
