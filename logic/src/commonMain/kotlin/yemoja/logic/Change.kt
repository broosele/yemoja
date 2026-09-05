package yemoja.logic

import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.Stored

/*
 * One thing a user did, and the parts it was carried out in.
 *
 * Shaped as the changeset `data/json/doc.md` describes, so that adding the journal is adding a
 * writer rather than rewriting everything that changes a logbook.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * Operation is what kind of thing a change was.
 *
 * One of the three the journal records about a change — `REQ-1` — and the only one that has to be
 * said, since when it happened and which installation did it are known without asking. A download
 * is one labelled thing to review and revert, not four hundred.
 */
enum class Operation {
    EDIT,
    DOWNLOAD,
    IMPORT,
    SYNC,
}

/**
 * Change is one part of an operation: a field written, an item made, an item taken out.
 *
 * The same three the journal's actions are, and named for them, so that recording one is writing
 * down what was already asked for rather than deriving it afterwards.
 *
 * Immutable.
 */
sealed class Change {

    /**
     * Puts [given] in [field] of [item], or clears it where [given] is nothing.
     *
     * [item] may be an owned one — a person's medical, a dive's profile — which is how a field
     * inside an item is reached without an address: the caller already walked there. Writing it
     * saves the file of whatever referenceable item owns it.
     */
    class Write(val item: Item, val field: String, val given: Any?) : Change()

    /** Makes an item of [description] under [id], holding nothing. */
    class Add(val description: ItemDescription, val id: String) : Change()

    /** Takes out whatever [id] names, leaving references to it dangling. */
    class Delete(val id: String) : Change()
}

/**
 * Outcome is what came of a change: it landed, or it was refused and nothing landed.
 *
 * A refusal names one thing and the whole operation is off, which is what makes a change a change
 * rather than a run of them. `REQ-2` restores a changeset whatever it holds, and something that
 * could half-happen would not be one.
 */
sealed class Outcome {

    /** Done is every part applied and every file it touched written. */
    object Done : Outcome()

    /** Refused is nothing applied, and why. */
    data class Refused(val reason: String) : Outcome()
}

/** Whatever referenceable item [item] belongs to, which is the one a file is written for. */
internal fun ownerOf(item: Item): Item {
    var here = item
    while (here is yemoja.data.OwnedItem) here = here.parent
    return here
}

/** An empty set of fields, which is what an item holding nothing is made from. `DATA-85`. */
internal fun nothing(): Stored.Members = Stored.Members(emptyMap())
