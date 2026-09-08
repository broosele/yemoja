package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.ItemWriter
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Time
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
     * [onto] is the item already held that this one turns out to be, whose fields it writes onto.
     * Absent means it is new. Where nothing could be matched by id, only the person reading can
     * say which it is, so this is asked rather than worked out. `RECON-2`.
     *
     * It is out of the staged logbook only where it went into the other, so a refusal leaves the
     * review as it was and the reason can be shown against an item still on the screen.
     */
    fun insert(id: String, onto: String? = null): Outcome {
        val done = into.change(Operation.IMPORT, *changesFor(listOf(id), onto).toTypedArray())
        if (done is Outcome.Done) drop(listOf(id))
        return done
    }

    /**
     * Whether taking [id] in is a question rather than a fact.
     *
     * It is where nothing could be matched by id, which is every source but another Yemoja
     * logbook. A carried id already says which item it is and asking would be noise.
     */
    fun asked(id: String): Boolean =
        matching == Matching.NONE && staged.logbook[id] != null

    /**
     * Which item already held the arriving one called [id] appears to be, or absent where none.
     *
     * **Two rules, and neither has a number in it.**
     *
     * One that says when it was is matched by **overlapping in time**, which is an impossibility
     * rather than a tolerance: nobody is on two dives at once, so two recordings that overlap are
     * two recordings of one dive. What `reconciliation.md` asks for — a start time within a
     * tolerance, a duration, a maximum depth — is three numbers nobody can pick well.
     *
     * Everything else is matched by **the name its type would give it**. Nine of the ten types
     * propose an id from the item's own `name`, so two logbooks each holding a site called Blue
     * Hole both propose `blue_hole`: the proposal is the name match, and it is the one a reader
     * would make by eye. The index a proposal may carry is left off, saying only where an item
     * sat rather than what it is.
     *
     * **It proposes and does not decide**, so being over-eager costs a keystroke. Two people both
     * called John Smith are proposed as one and the reader says otherwise, which is `JSON-6`'s
     * collision met where somebody can answer it.
     */
    fun proposal(id: String): String? {
        val here = staged.logbook[id] ?: return null
        val span = spanOf(here)
        val named = if (span == null) nameOf(here) else null
        if (span == null && named == null) return null
        for (there in into.logbook.allOf(here.description)) {
            val same =
                if (span != null) meets(span, spanOf(there)) else nameOf(there) == named
            if (same) return into.logbook.idOf(there)
        }
        return null
    }

    /** Whether two spans touch or overlap, a missing one meeting nothing. */
    private fun meets(span: Pair<Long, Long>, other: Pair<Long, Long>?): Boolean =
        other != null && span.first <= other.second && other.first <= span.second

    /**
     * What [item]'s type would call it, without the index that says only where it sat.
     *
     * Absent where the type falls back to `unknown_person` and the like: two items nobody named
     * are not one item, and proposing that they are would be worse than proposing nothing.
     */
    private fun nameOf(item: ReferenceableItem): String? {
        val proposed = item.description.proposedId?.invoke(item) ?: return null
        val base = proposed.substringBefore('#')
        return if (base.isEmpty() || base == unknownOf(item)) null else base
    }

    /**
     * When [item] began and ended, in seconds, or absent where it does not say.
     *
     * A missing duration is no length rather than no answer, so two that began at the same
     * instant still meet. `duration` is worked out from the times or the recording where nothing
     * wrote one, so a dive that says when it was usually says how long it took as well.
     */
    private fun spanOf(item: Item): Pair<Long, Long>? {
        if (item.description["start_date"] == null) return null
        val date = (item.read("start_date") as? Result.Usable)?.value as? Date ?: return null
        val time = (item.read("start_time") as? Result.Usable)?.value as? Time ?: return null
        val began = date.epochDay * SECONDS_IN_DAY + time.secondOfDay
        val long = (item.read("duration") as? Result.Usable)?.value as? Double ?: 0.0
        return began to began + long.toLong()
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
    private fun changesFor(ids: List<String>, onto: String? = null): List<Change> {
        val changes = ArrayList<Change>()
        for (id in ids) {
            val item = staged.logbook[id] ?: continue
            val description = item.description
            val held = ItemWriter.write(item, Units.DEFAULT).members
                .filterKeys { description[it] != null }
            val chosen = onto?.let { into.logbook[it] }?.takeIf { it.description == description }
            if (chosen != null) {
                for ((field, value) in held) changes += Change.Write(chosen, field, value)
                continue
            }
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

        private const val SECONDS_IN_DAY = 24L * 60 * 60

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
