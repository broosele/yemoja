package yemoja.ui.gui

import yemoja.data.ItemDescription
import yemoja.logic.Import
import yemoja.logic.Meeting
import yemoja.logic.Types

/*
 * Taking in another logbook, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-33`. What arrives as a dive is reviewed by the
 * machinery a download already has, in Downloading.kt; this is the rest of what arrives.
 */

/**
 * Counted is what arrived of one type: how many, and how many answer to something already held.
 *
 * The second is what a reader needs in order to know whether an import is new diving or the
 * same logbook again. `RECON-1` asks for exactly this summary.
 */
internal class Counted(
    val type: String,
    /** The same word for more than one of them, which is not always the word with an s on it. */
    val several: String,
    val many: Int,
    val known: Int,
)

/**
 * What arrived besides the dives, a line per type, in the order the model lists its types.
 *
 * Dives are left out: they are reviewed one by one and counting them beside the list would say
 * the same thing twice. A type nothing arrived of is left out too.
 */
internal fun countedIn(import: Import): List<Counted> {
    val out = ArrayList<Counted>()
    for (description in Types.ALL) {
        if (description == Types.DIVE) continue
        var many = 0
        var known = 0
        for (item in import.incoming.filter { it.description == description }) {
            val id = import.staged.logbook.idOf(item) ?: continue
            many++
            if (import.meeting(id) == Meeting.THE_SAME) known++
        }
        if (many > 0) {
            out += Counted(prettyOf(description.name), severalOf(description), many, known)
        }
    }
    return out
}

/** A type as a reader counts them: an s on it, bar the two that do not take one. */
internal fun severalOf(description: ItemDescription): String = when (description) {
    Types.PERSON -> "People"
    // Gear is what one piece of it is called and what any number of it is called.
    Types.GEAR -> "Gear"
    else -> prettyOf(description.name) + "s"
}

/**
 * [counted] as a sentence, or nothing where a dive was all that arrived.
 *
 * What answers to something already held is said as *already held* rather than as a number
 * beside a number: an import of a logbook onto itself is every line reading that way, which is
 * the answer to the question the reader is actually asking.
 */
internal fun summaryOf(counted: List<Counted>): String? {
    if (counted.isEmpty()) return null
    val said = counted.joinToString(", ") { one ->
        val word = if (one.many == 1) one.type else one.several
        val many = "${one.many} ${word.lowercase()}"
        if (one.known == 0) many else "$many (${one.known} already held)"
    }
    return "Also arriving: $said."
}

/** How many dives arrived, which is what the review lists. */
internal fun divesIn(import: Import): Int =
    import.incoming.count { it.description == Types.DIVE }
