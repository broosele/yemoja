package yemoja.ui.gui

import yemoja.data.ItemSet
import yemoja.data.mentionsIn

/*
 * Talking to an agent about the logbook, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */

/**
 * Conversation is an agent the window is running, as the screen needs it.
 *
 * The window implements it, because starting a program and speaking a protocol to it is a
 * platform's work and this layer holds no domain rules about either. What is behind it is
 * `yemoja.ui.api.Hosted`, and what that gives the agent is `API-4` and `API-5`.
 *
 * Not immutable: an agent runs from [start] until [close].
 */
internal interface Conversation {

    /**
     * Starts the agent [command] names, and opens a conversation with it.
     *
     * Throws where it cannot be started, which is what a mistyped command and an agent nobody
     * installed both look like from here.
     */
    suspend fun start(command: String)

    /** Puts [said] to the agent, handing each piece of the answer to [heard] as it arrives. */
    suspend fun ask(said: String, heard: (String) -> Unit)

    /**
     * Asks the agent to stop answering, leaving it running and the conversation open.
     *
     * The answer under way ends where it has got to, and [ask] returns as it does for one that
     * finished. Nothing where no answer is under way.
     */
    suspend fun interrupt()

    /** Stops the agent. */
    fun close()

    /**
     * Every request of the agent's own that was refused, most recent last. `GUI-38`.
     *
     * **It only ever grows while an agent runs**, so whatever shows these can remember how many it
     * has shown and take what is new. Starting another agent, or closing this one, empties it.
     */
    val refused: List<String>
}

/** Turn is who said one thing in a conversation. */
internal enum class Turn { USER, AGENT, WINDOW }

/**
 * Exchange is one thing that was said, and who said it.
 *
 * [Turn.WINDOW] is the application itself saying something neither party did: that an agent could
 * not be started, or that it asked for something and was refused.
 *
 * Immutable.
 */
internal data class Exchange(val who: Turn, val said: String)

/**
 * Stance is how far a conversation has got, which decides what the panel offers.
 *
 * An agent is a program that has to be started, and starting one takes seconds, so where it has
 * got to is a thing in its own right rather than a flag on a button. The same shape as a
 * download's `Stage`, for the same reason. `GUI-31`.
 */
internal enum class Stance {
    /** No agent is running, and the panel offers to start the one named. */
    NONE,

    /** Being started, which takes seconds. */
    STARTING,

    /** Running, and waiting to be asked something. */
    READY,

    /** Asked, and answering. A question about the whole logbook takes as long as the reading. */
    ANSWERING,
}

/** What to say while a conversation is at [stance], or absent where the panel says it itself. */
internal fun sayingOf(stance: Stance, agent: String?): String? = when (stance) {
    Stance.STARTING -> "Starting ${agent ?: "the agent"}…"
    Stance.ANSWERING ->
        "Working on your question. One that covers the whole logbook can take a minute or more."
    else -> null
}

/**
 * What to say when starting an agent failed.
 *
 * The reason as the machine gave it, since it is the machine that knows: a command nobody has
 * installed and a command spelled wrongly are the same failure from here, and neither is ours to
 * explain away.
 */
internal fun failedOf(command: String, why: String?): String =
    "$command could not be started: ${why ?: "it said nothing about why"}. " +
        "An agent is a program you install yourself; Yemoja supplies none."

/**
 * What to say of a request of the agent's own that was refused.
 *
 * Said rather than hidden, because an agent meant for writing code will try to read the logbook's
 * files, and a user watching it get nowhere deserves to know why. `GUI-38`.
 */
internal fun refusalOf(what: String): String =
    "The agent asked to $what, and was refused. It reaches the logbook through Yemoja's tools " +
        "and no other way."

/**
 * What an agent said, split into the text and the items it named.
 *
 * A mention that resolves is shown as the item's name and leads to it, which is how an ordinary
 * reference is shown and what keeps *an id is never shown* holding here. One that resolves to
 * nothing stays as it was written, being text that happens to look like a mention. `JSON-23`.
 *
 * Examples: `I counted 3 dives: @2026-04-28#1 …` gives the words, then that dive as a part
 * leading to it.
 */
internal fun partsOf(said: String, set: ItemSet): List<Part> {
    val parts = ArrayList<Part>()
    var at = 0
    for (mention in mentionsIn(said)) {
        val item = set[mention.id] ?: continue
        if (mention.at.first > at) parts += Part(said.substring(at, mention.at.first))
        parts += Part(titleOf(item), leadsTo = mention.id)
        at = mention.at.last + 1
    }
    if (at < said.length) parts += Part(said.substring(at))
    return parts
}
