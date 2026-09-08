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

    /**
     * Makes an item of [description] holding [fields], under [id] or under one worked out.
     *
     * **A front end does not give the id.** What a new item is called is domain knowledge — a
     * dive is named for the day it was made on, a person for their name — and `ui/doc.md` lets a
     * front end name a type without describing one. So the caller gives what the item holds and
     * the Universe asks the description to propose, then takes the first id free from that
     * proposal.
     *
     * **An item arriving from somewhere else carries one**, and it is given here. An id is what
     * matches an item across two logbooks, and it is what the references in the same import point
     * at: mint a new one and the dive that names the person no longer finds them. So an import
     * hands over the id it was given rather than proposing a name it already knows. The id must
     * be free — one taken refuses the whole change, since two items cannot answer to it.
     *
     * Which id it was is in [Outcome.Done], in the order the additions were asked for.
     */
    class Add(
        val description: ItemDescription,
        val fields: Map<String, Any?> = emptyMap(),
        val id: String? = null,
    ) : Change()

    /**
     * Takes out whatever [id] names.
     *
     * References to it are **left dangling by default**, which is the honest outcome: a reference
     * naming nothing is a state the model carries and an interface shows, and it is the same
     * state as a person not entered yet. Rewriting other items to hide a deletion changes things
     * the user did not ask about.
     *
     * [alsoReferences] clears them instead, for the case where the deletion is meant to leave no
     * trace. It reaches only references, never a mention in free text: `JSON-23` gives a mention
     * no fixed meaning, so removing one would be editing somebody's prose.
     */
    class Delete(val id: String, val alsoReferences: Boolean = false) : Change()
}

/**
 * Outcome is what came of a change: it landed, or it was refused and nothing landed.
 *
 * A refusal names one thing and the whole operation is off, which is what makes a change a change
 * rather than a run of them. `REQ-2` restores a changeset whatever it holds, and something that
 * could half-happen would not be one.
 */
sealed class Outcome {

    /**
     * Done is every part applied and every file it touched written.
     *
     * [added] holds the id of each item made, in the order the additions were asked for, since
     * the caller did not choose them and has no other way to find out.
     */
    data class Done(val added: List<String> = emptyList()) : Outcome()

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
