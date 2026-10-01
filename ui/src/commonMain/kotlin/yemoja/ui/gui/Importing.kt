package yemoja.ui.gui

import yemoja.data.ItemDescription
import yemoja.logic.Import
import yemoja.logic.Meeting
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe

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

/** Decided is where an import's review stands once a dive has been answered, and what it says. */
internal class Decided(val arrived: Int, val said: String?)

/**
 * Where an import stands once [taken] has been decided, taking in the rest once nothing is left.
 *
 * **The rest waits for the last dive.** What the review does not list — a site, a person, a piece
 * of gear — goes in once no dive is left to decide, in one change, and not before: taking it in
 * with the first answer took every dive still waiting in with it, since the rest is everything
 * still staged. `GUI-33`.
 */
internal fun afterDeciding(universe: Universe, taken: Taken): Decided {
    val import = universe.importing ?: return Decided(0, taken.refusal ?: takenSaid(taken.many))
    val arrived = arrivedIn(import)
    if (taken.refusal != null || arrived > 0) {
        return Decided(arrived, taken.refusal ?: takenSaid(taken.many))
    }
    val rest = theRest(import)
    universe.stopImporting()
    return Decided(0, rest.refusal ?: takenSaid(taken.many + rest.many))
}

internal fun takenSaid(many: Int): String = when (many) {
    0 -> "Nothing was added to your logbook."
    1 -> "1 item was added to your logbook."
    else -> "$many items were added to your logbook."
}

/**
 * Take in whatever is still staged once the dives are decided, as one change.
 *
 * Only what the review did not list: a site, a person, a piece of gear. Each was matched by the
 * id it came with or is new, so there is nothing to ask and nothing to choose. `RECON-6`.
 */
private fun theRest(import: Import): Taken {
    val many = import.incoming.size
    if (many == 0) return Taken(0, null)
    return when (val done = import.apply()) {
        is Outcome.Refused -> Taken(0, done.reason)
        is Outcome.Done -> Taken(many, null)
    }
}
