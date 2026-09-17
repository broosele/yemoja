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
 * closing the panel is the end of it. The command that starts an agent is the one exception, and it
 * is kept elsewhere: in this device's settings, once an agent has started from it. `GUI-38`.
 *
 * Not immutable.
 */
private class Talk {

    /** The command that starts an agent, as the user types it. */
    var command: String by mutableStateOf("")

    /** What the agent running is called, or absent where none is running. */
    var agent: String? by mutableStateOf(null)

    var stance: Stance by mutableStateOf(Stance.NONE)

    /** The conversation so far, oldest first. */
    var exchanges: List<Exchange> by mutableStateOf(emptyList())

    /** What is being typed to ask next. */
    var question: String by mutableStateOf("")

    /**
     * Whether the tools may answer with what is personal.
     *
     * Off at the start of every conversation, whatever the last one was allowed. `API-5`.
     */
    var personal: Boolean by mutableStateOf(false)

    /**
     * Whether an agent may stage changes at all, which is the other box and starts off too.
     *
     * Staging is not changing: what an agent stages waits for somebody to look at it. The box is
     * what stands between an agent and the logbook all the same, and the write tools refuse with
     * a reply naming it while it is off. `API-5`, `RECON-8`.
     */
    var writing: Boolean by mutableStateOf(false)

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
    conversing: (personal: () -> Boolean, writing: () -> Boolean) -> Conversation,
    /** How many items an agent has staged, waiting to be reviewed. */
    staged: Int,
    /** What a review came to, which the conversation records as the window's own turn. */
    told: Told?,
    /** Opens the review, which is on the home screen where an import's is. */
    onReview: () -> Unit,
    /** The command an agent was last started from on this device, which the panel opens on. */
    remembered: String?,
    /** Keeps [String] as the command to open on next time, once an agent has started from it. */
    onStarted: (String) -> Unit,
    onFollow: (String) -> Unit,
    onClose: () -> Unit,
) {
    val talk = remember { Talk().also { it.command = remembered.orEmpty() } }
    val conversation = remember { conversing({ talk.personal }, { talk.writing }) }
    // Whatever was said before this panel opened has been read already: a panel opened again is
    // not a conversation carried on, and would otherwise begin with old news.
    val before = remember { told }
    LaunchedEffect(told) {
        if (told != null && told !== before) talk.exchanges += Exchange(Turn.WINDOW, told.said)
    }
    DisposableEffect(conversation) { onDispose { conversation.close() } }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(talk.exchanges) { scroll.scrollTo(scroll.maxValue) }
    Column(modifier = Modifier.width(PANEL).fillMaxHeight().padding(GAP)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Agent",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("Close") }
        }
        HorizontalDivider()
        Starting(talk, conversation, scope, onStarted)
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
        Asking(talk, conversation, scope)
    }
}

/** The agent to run and the deed that starts it, or what is running and the deed that stops it. */
@Composable
private fun Starting(
    talk: Talk,
    conversation: Conversation,
    scope: CoroutineScope,
    onStarted: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(HALF),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (talk.stance == Stance.NONE) {
            Box(modifier = Modifier.weight(1f)) {
                Compact(
                    value = talk.command,
                    onChange = { talk.command = it },
                    hint = "Agent command",
                )
            }
            Button(
                onClick = { start(talk, conversation, scope, onStarted) },
                enabled = talk.command.isNotBlank(),
            ) { Text("Start") }
        } else {
            Text(
                text = talk.agent.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { stop(talk, conversation) }) { Text("Stop") }
        }
    }
}

/** What to ask next, and what the agent is allowed to be told while it answers. */
@Composable
private fun Asking(talk: Talk, conversation: Conversation, scope: CoroutineScope) {
    Boxed("Allow changes", talk.writing) { talk.writing = it }
    Boxed("Include personal details", talk.personal) { talk.personal = it }
    Compact(
        value = talk.question,
        onChange = { talk.question = it },
        hint = "Ask about your logbook",
        lines = 3,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HALF),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = { ask(talk, conversation, scope) },
            enabled = talk.stance == Stance.READY && talk.question.isNotBlank(),
        ) { Text("Ask") }
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
    onStarted: (String) -> Unit,
) {
    val command = talk.command.trim()
    talk.agent = agentOf(command)
    talk.stance = Stance.STARTING
    talk.exchanges = emptyList()
    talk.shown = 0
    talk.personal = false
    talk.writing = false
    scope.launch {
        val failed = startedWith(conversation, command, onStarted)
        if (failed == null) {
            talk.stance = Stance.READY
        } else {
            talk.exchanges += Exchange(Turn.WINDOW, failed)
            talk.agent = null
            talk.stance = Stance.NONE
        }
    }
}

/**
 * Starts what [command] names in [conversation], and answers what to say where it would not start.
 *
 * **Only a command that started is kept**, handed to [onStarted] once it has, so a mistyped one is
 * not what the panel opens on next time. `GUI-38`.
 */
internal suspend fun startedWith(
    conversation: Conversation,
    command: String,
    onStarted: (String) -> Unit,
): String? {
    try {
        conversation.start(command)
    } catch (refused: Exception) {
        // Wider than a RuntimeException on purpose: a command nobody has installed fails where
        // the process is started, and that is an IOException.
        return failedOf(command, refused.message)
    }
    onStarted(command)
    return null
}

/**
 * Stop the agent, leaving what was said.
 *
 * The conversation stays on the screen to be read and copied; it is the agent that has gone.
 * Starting another clears it, that being another conversation.
 */
private fun stop(talk: Talk, conversation: Conversation) {
    conversation.close()
    talk.agent = null
    talk.stance = Stance.NONE
    talk.shown = 0
}

/** Put what is typed to the agent, and take the answer as it arrives. */
private fun ask(talk: Talk, conversation: Conversation, scope: CoroutineScope) {
    val said = talk.question.trim()
    talk.question = ""
    talk.exchanges += Exchange(Turn.USER, said)
    talk.stance = Stance.ANSWERING
    scope.launch {
        try {
            conversation.ask(said) { piece ->
                talk.exchanges = heard(talk.exchanges, piece)
                talk.take(conversation.refused)
            }
        } catch (stopped: Exception) {
            talk.exchanges += Exchange(Turn.WINDOW, stoppedOf(stopped.message))
        }
        talk.take(conversation.refused)
        talk.stance = Stance.READY
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
 * The last word that is not an option, without the folders or the publisher in front of it. An
 * agent is run in whatever way its own instructions give, and the part of that a reader
 * recognises is its name.
 *
 * Examples: `npx @zed-industries/claude-code-acp` is claude-code-acp, and
 * `/usr/local/bin/gemini --experimental-acp` is gemini.
 */
internal fun agentOf(command: String): String? {
    val words = command.trim().split(SPACES).filter { it.isNotEmpty() }
    val last = words.lastOrNull { !it.startsWith("-") } ?: return null
    return last.substringAfterLast('/').substringAfterLast('\\').removePrefix("@").ifEmpty { null }
}

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
