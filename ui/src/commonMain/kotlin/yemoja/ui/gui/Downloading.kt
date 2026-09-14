package yemoja.ui.gui

import yemoja.logic.Import
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.divecomputer.DiveComputer

/*
 * Reading a dive computer, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-31`.
 */

/**
 * Stage is how far a download has got, which is the whole of what the screen shows of it.
 *
 * A download is the one thing the application does that takes minutes, so where it has got to
 * is a thing in its own right rather than a flag on a button. `GUI-31`.
 */
internal enum class Stage {
    /** Nothing is happening, and the button offers to start. */
    IDLE,

    /** Looking for what this machine can reach, which takes seconds. */
    LOOKING,

    /** More than one was found, and the reader says which. */
    CHOOSING,

    /** Being read, which takes minutes. */
    READING,

    /** Read, and what came of it is being shown. */
    DONE,
}

/** What to say while a download is at [stage], or absent where the screen says it another way. */
internal fun sayingOf(stage: Stage, computer: String?): String? = when (stage) {
    Stage.LOOKING -> "Looking for a dive computer…"
    Stage.CHOOSING -> "More than one is within reach. Which of them?"
    Stage.READING -> "Reading ${computer ?: "it"}. A full computer takes minutes."
    else -> null
}

/**
 * What to say of a download that finished: how many dives came across, or why none did.
 *
 * A refusal is the reason as the model gave it, since the model knows why and this does not.
 */
internal fun outcomeOf(outcome: Outcome, arrived: Int): String = when (outcome) {
    is Outcome.Refused -> outcome.reason
    is Outcome.Done ->
        if (arrived == 1) "1 dive came across." else "$arrived dives came across."
}

/** How many dives a staged import holds, which is what a download brought. */
internal fun arrivedIn(import: Import?): Int =
    import?.staged?.logbook?.allOf(Types.DIVE)?.size ?: 0

/** What was taken in, and what stopped it where something did. */
internal class Taken(val many: Int, val refusal: String?)

/**
 * Take everything that arrived into the logbook, each as a dive of its own.
 *
 * **As new, every one.** A download matches nothing by id, `LOGIC-20`, so nothing here claims
 * to know which arriving dive is one already held. Deciding that dive by dive is a review, and
 * this is the blunt answer for the common case: a computer read for the first time, or read
 * again after a resume, holds dives the logbook has not seen.
 *
 * It stops at the first refusal and says so, leaving the rest staged: a reader who is told the
 * fourth would not go in can look at it, and what is left in the folder is what has not been
 * decided. `RECON-1`.
 */
internal fun takenIn(import: Import): Taken {
    var many = 0
    for (item in import.incoming.toList()) {
        val id = import.staged.logbook.idOf(item) ?: continue
        when (val done = import.insert(id)) {
            is Outcome.Refused -> return Taken(many, done.reason)
            is Outcome.Done -> many++
        }
    }
    return Taken(many, null)
}

/** What a computer is called in a list of them, which is the only thing one says of itself. */
internal fun namedOf(computer: DiveComputer): String = computer.name
