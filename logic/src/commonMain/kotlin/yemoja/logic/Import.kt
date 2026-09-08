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
 * Meeting is what an incoming item finds in the logbook it is going into.
 *
 * Matched by id, which is exact between two Yemoja logbooks and is what sync matches on. A source
 * that has no ids of its own — a dive computer, another application's file — owes a matching rule
 * of its own before this says anything about it.
 */
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
) {

    /** Every incoming item, each type in the order that type asks for. `DATA-89`. */
    val incoming: List<ReferenceableItem>
        get() = staged.logbook.descriptions.flatMap { staged.logbook.inOrder(it) }

    /** What the item called [id] finds in the logbook it is going into. */
    fun meeting(id: String): Meeting {
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
                Meeting.NOTHING -> changes += Change.Add(description, held, id)
                Meeting.THE_SAME -> {
                    val there = into.logbook[id] ?: continue
                    for ((field, value) in held) changes += Change.Write(there, field, value)
                }

                Meeting.SOMETHING_ELSE -> Unit
            }
        }
        return changes
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
        fun begin(incoming: ItemSet, staging: FileStore, into: Universe): Import {
            for (description in incoming.descriptions) {
                for (item in incoming.allOf(description)) {
                    val id = incoming.idOf(item) ?: continue
                    LogbookWriter.write(staging, description, id, item)
                }
            }
            return open(staging, incoming.descriptions, into)
        }

        /**
         * An import already staged in [staging], as it was left.
         *
         * What each item meets is worked out again rather than remembered: the logbook it is
         * going into may have changed while the review was put down. What is left in the folder
         * is what has not been decided, an item taken in or turned down having left it.
         */
        fun open(staging: FileStore, types: List<ItemDescription>, into: Universe): Import =
            Import(Universe(LogbookReader.read(staging, types), null, staging), into)
    }
}
