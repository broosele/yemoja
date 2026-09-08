package yemoja.logic

import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.ItemWriter
import yemoja.data.ReferenceableItem
import yemoja.data.Units
import yemoja.data.inOrder
import yemoja.data.json.FileStore
import yemoja.data.json.LogbookReader
import yemoja.data.json.LogbookWriter

/*
 * Items arriving from somewhere else, and what becomes of them.
 *
 * See ../../../../../doc.md — the layer's own document is logic/reconciliation.md.
 */

/**
 * Matching is how an incoming item is recognised as one already held, which only its source knows.
 *
 * **An id means something only where it was carried.** Two Yemoja logbooks name the same item the
 * same way, so an id matches exactly and is what sync matches on. A source with no ids of its own
 * has them minted on the way in, and a minted id says only what this model would have called such
 * an item — so two dives on one day are both `2024-06-15#0` whether or not they are the same
 * dive, and matching on that would fold a stranger's dive into the user's.
 */
enum class Matching {

    /** By the id it came with, which is what another Yemoja logbook offers. */
    BY_ID,

    /**
     * Not at all: everything is new, and an id already taken is minted afresh.
     *
     * The honest position for a source whose ids were minted here. Importing the same file twice
     * puts everything in twice, which is visible and can be deleted, where the alternative is
     * quietly writing over dives the user already had. A rule that recognises a dive by when it
     * was and how deep it went is what `reconciliation.md` asks for and it is not built.
     */
    NONE,
}

/** Meeting is what an incoming item finds in the logbook it is going into. */
enum class Meeting {

    /** Nothing answers to its id, so it goes in under the id it came with. */
    NOTHING,

    /** One of its own type under its own id, whose fields it writes onto. */
    THE_SAME,

    /** Its id is taken by something of another type, so it cannot go in at all. */
    SOMETHING_ELSE,
}

/**
 * Import is a set of items arriving from somewhere else, and what is to be done with each.
 *
 * **The incoming items are a logbook of their own.** They are written to a folder of their own and
 * read back from it, so reviewing them costs nothing new: everything that reads, edits and saves a
 * logbook reads, edits and saves these. That is what [staged] is, and it is why every edit made
 * while reviewing is on disk the moment it is made rather than held in memory until the end.
 * `RECON-1`.
 *
 * **Deciding is editing.** An item taken in leaves the staged logbook, and so does one turned
 * down, so what is left in the folder is exactly what has not been decided. There is no list of
 * decisions to keep beside the items, and a review put down halfway is taken up where it stopped
 * without anything having been written to remember that.
 *
 * **Not immutable.** A review is a thing being worked on.
 */
class Import private constructor(
    /** The incoming items, as a logbook of their own. */
    val staged: Universe,
    private val into: Universe,
    private val matching: Matching,
) {

    /** Every incoming item, each type in the order that type asks for. `DATA-89`. */
    val incoming: List<ReferenceableItem>
        get() = staged.logbook.descriptions.flatMap { staged.logbook.inOrder(it) }

    /** What the item called [id] finds in the logbook it is going into. */
    fun meeting(id: String): Meeting {
        if (matching == Matching.NONE) return Meeting.NOTHING
        val here = staged.logbook[id] ?: return Meeting.NOTHING
        val there = into.logbook[id] ?: return Meeting.NOTHING
        return if (there.description == here.description) Meeting.THE_SAME
        else Meeting.SOMETHING_ELSE
    }

    /**
     * Put the item called [id] into the logbook, and take it out of the staged one.
     *
     * It is out of the staged logbook only where it went into the other, so a refusal leaves the
     * review as it was and the reason can be shown against an item still on the screen.
     */
    fun insert(id: String): Outcome {
        val done = into.change(Operation.IMPORT, *changesFor(listOf(id)).toTypedArray())
        if (done is Outcome.Done) drop(listOf(id))
        return done
    }

    /** Take [id] out of the staged logbook without putting it anywhere. */
    fun remove(id: String) {
        drop(listOf(id))
    }

    /**
     * Put every accepted item into the logbook, as one change.
     *
     * An item nothing answers to is added under the id it came with. One the logbook already
     * holds has each field the incoming one holds written onto it, and **the fields it does not
     * hold are left alone**: a source has opinions about some fields and none about others, and
     * silence is not an instruction to erase. A dive computer knows nothing about buddies, and
     * re-downloading a dive must not take them out.
     *
     * One change, so it lands whole or not at all, and so the journal has one thing to record and
     * one thing to take back. `REQ-2`.
     *
     * **Applying twice does nothing the second time.** What was added answers to its id
     * afterwards, so it is met as [Meeting.THE_SAME] and writes the same values it already holds.
     */
    fun apply(): Outcome {
        val ids = staged.logbook.descriptions
            .flatMap { staged.logbook.allOf(it) }
            .mapNotNull { staged.logbook.idOf(it) }
        val done = into.change(Operation.IMPORT, *changesFor(ids).toTypedArray())
        if (done is Outcome.Done) drop(ids)
        return done
    }

    /** What putting the items called [ids] into the logbook comes to. */
    private fun changesFor(ids: List<String>): List<Change> {
        val changes = ArrayList<Change>()
        for (id in ids) {
            val item = staged.logbook[id] ?: continue
            val description = item.description
            val held = ItemWriter.write(item, Units.DEFAULT).members
                .filterKeys { description[it] != null }
            when (meeting(id)) {
                // A name already taken is minted afresh, which only arises where nothing is
                // matched: under `BY_ID` a taken id is a match rather than a clash.
                Meeting.NOTHING -> changes += Change.Add(description, held, freeIn(id, changes))
                Meeting.THE_SAME -> {
                    val there = into.logbook[id] ?: continue
                    for ((field, value) in held) changes += Change.Write(there, field, value)
                }

                Meeting.SOMETHING_ELSE -> Unit
            }
        }
        return changes
    }

    /**
     * The first name free from [id], counting what [going] is already adding.
     *
     * **A reference to a renamed item is not followed.** Nothing read from a source with minted
     * ids points at anything else read from it yet, so there is nothing to follow; a source that
     * did would need this to rewrite what names it, and that is not built.
     */
    private fun freeIn(id: String, going: List<Change>): String {
        val minted = going.filterIsInstance<Change.Add>().mapNotNull { it.id }
        return freeName(id) { into.logbook[it] != null || it in minted }
    }

    /** Take [ids] out of the staged logbook, which is what leaves the review shorter. */
    private fun drop(ids: List<String>) {
        val going = ids.filter { staged.logbook[it] != null }
        if (going.isEmpty()) return
        staged.change(Operation.EDIT, *going.map { Change.Delete(it) }.toTypedArray())
    }

    companion object {

        /**
         * Stage [incoming] in [staging], to go into [into].
         *
         * The items are written out as an ordinary logbook and read back from it, which is what
         * makes them resolvable in their own right: a dive that names a person finds that person
         * among the items that arrived with it rather than in the logbook it is going into.
         */
        fun begin(
            incoming: ItemSet,
            staging: FileStore,
            into: Universe,
            matching: Matching = Matching.BY_ID,
        ): Import {
            for (description in incoming.descriptions) {
                for (item in incoming.allOf(description)) {
                    val id = incoming.idOf(item) ?: continue
                    LogbookWriter.write(staging, description, id, item)
                }
            }
            return open(staging, incoming.descriptions, into, matching)
        }

        /**
         * An import already staged in [staging], as it was left.
         *
         * What each item meets is worked out again rather than remembered: the logbook it is
         * going into may have changed while the review was put down. What is left in the folder
         * is what has not been decided, an item taken in or turned down having left it.
         */
        fun open(
            staging: FileStore,
            types: List<ItemDescription>,
            into: Universe,
            matching: Matching = Matching.BY_ID,
        ): Import = Import(
            Universe(LogbookReader.read(staging, types), null, staging),
            into,
            matching,
        )
    }
}
