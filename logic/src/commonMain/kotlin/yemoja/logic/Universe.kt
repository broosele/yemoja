package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Units
import yemoja.data.json.DiskFileStore
import yemoja.data.json.FileStore
import yemoja.data.json.LogbookReader
import yemoja.data.json.LogbookWriter

/*
 * The application's working state, and the one door a front end reaches anything through.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md, under "The Universe".
 */

/**
 * Universe is what is open: the logbook, and in time whatever else is being worked on.
 *
 * `ui/doc.md` holds every front end to reaching everything through this, so a front end names a
 * folder and never opens one. [open] is the only place in this layer that names a source;
 * everything else takes an [ItemSet] and runs against items assembled in memory.
 *
 * **An item set is reached through it, not wrapped by it.** [logbook] is handed over whole, so
 * nothing here forwards `get` or `allOf`, and there is nothing to drift out of step with what it
 * forwards.
 *
 * **It delegates and implements nothing**, and so far there is nothing to delegate to. Statistics,
 * decompression and the domain rules are each a service of their own, unbuilt; when they arrive
 * this hands work to them rather than doing any. The smallness is the shape and not a stage of it.
 *
 * Absent so far: an import's candidate set, which is the second thing this is meant to hold and
 * waits on `RECON-2`; the units a user wants shown, which `UI-2` settles as belonging here and
 * which needs somewhere for settings to be read from; and everything computed.
 *
 * Not immutable: a logbook gains items while it is open, and [change] is how.
 */
class Universe(
    val logbook: ItemSet,
    val user: ReferenceableItem?,
    private val store: FileStore,
) {

    /**
     * A number that changes whenever anything in this universe does.
     *
     * `ItemSet` carries one of its own and it counts a narrower thing: an item added or taken
     * out, not a field edited. A view has to refresh for both — a dive whose date was corrected
     * moves in a list ordered by date without the count changing — so this counts every change.
     *
     * Not notification. `DATA-6` settles that nothing is announced and nothing subscribes; this
     * is what lets something that may be out of date ask.
     */
    var revision: Int = 0
        private set

    /**
     * Does [changes] as one [operation], and saves whatever it touched.
     *
     * **The one way anything changes.** Nothing above calls a mutator on an item, and the reason
     * is not tidiness: when `FEAT-4` arrives a changeset has to be recorded for every change, and
     * a front end reaching past this would leave nothing to record it from. The shape here is the
     * changeset's own — an operation, and the actions that carried it out — so the journal is a
     * writer added beside this rather than a rewrite of everything that edits.
     *
     * **It lands whole or not at all.** Every part is judged before any is applied, so a refusal
     * leaves the logbook as it was. What is not guaranteed is the files: a change touching two of
     * them can be interrupted between them, which `JSON-25` defers.
     *
     * **Saving is immediate.** There is no unsaved state and no save to forget, which is what
     * makes every change the journal's unit rather than only the ones somebody remembered.
     *
     * Nothing is announced. Whatever is showing the logbook asks again. `DATA-6`.
     */
    fun change(operation: Operation, vararg changes: Change): Outcome {
        val writes = ArrayList<Pair<Change.Write, Result<Any>>>()
        val adds = ArrayList<Pair<String, ReferenceableItem>>()
        for (change in changes) {
            when (change) {
                is Change.Write -> {
                    val made = change.item.prepared(change.field, change.given, Units.DEFAULT)
                    if (made is Result.Unusable) return Outcome.Refused(made.reason)
                    writes += change to made
                }

                is Change.Add -> {
                    val item =
                        ItemReader.read(change.description, nothing(), logbook, Units.DEFAULT)
                    for ((name, given) in change.fields) {
                        val made = item.prepared(name, given, Units.DEFAULT)
                        if (made is Result.Unusable) return Outcome.Refused(made.reason)
                        item.apply(name, made)
                    }
                    // Taken now rather than when it lands, so two additions in one change cannot
                    // both be given the same id: the ones already minted are counted as taken.
                    adds += freeId(item, adds.map { it.first }) to item
                }

                is Change.Delete -> Unit
            }
        }

        for ((change, made) in writes) change.item.apply(change.field, made)
        for ((id, item) in adds) logbook.add(id, item)

        val touched = LinkedHashSet<Pair<ItemDescription, String>>()
        for ((id, item) in adds) touched += item.description to id
        for (change in changes) {
            when (change) {
                is Change.Write -> {
                    val owner = ownerOf(change.item) as ReferenceableItem
                    logbook.idOf(owner)?.let { touched += owner.description to it }
                }

                is Change.Delete -> {
                    val going = logbook[change.id] ?: continue
                    val description = going.description
                    if (change.alsoReferences) touched += clearedOf(change.id)
                    logbook.remove(change.id)
                    touched.remove(description to change.id)
                    LogbookWriter.delete(store, description, change.id)
                }

                is Change.Add -> Unit
            }
        }
        for ((description, id) in touched) {
            logbook[id]?.let { LogbookWriter.write(store, description, id, it) }
        }
        revision += 1
        return Outcome.Done(adds.map { it.first })
    }

    /**
     * The first id free for [item], from what its type proposes.
     *
     * A proposal is not an answer: two items may propose the same thing and neither knows what is
     * already there. Where it is taken the index moves — `2026-04-28#0` to `#1`, `anna_devries` to
     * `anna_devries#1` — which is one rule for both, since a dive proposes an index and everything
     * else proposes none.
     *
     * **The lowest free index, so a deleted item's id comes back.** An id may be reissued, which
     * `DATA-84` once forbade: deleting a dive entered wrongly and entering it again should heal
     * the references to it rather than leave them dangling for ever. What that costs is that a
     * reference to something deleted can come to name something else.
     */
    private fun freeId(item: ReferenceableItem, minted: List<String>): String {
        val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
        return freeName(proposed) { logbook[it] != null || it in minted }
    }

    /**
     * Clears every reference naming [id], and says which items were changed.
     *
     * Only references. A mention in free text is prose, and `JSON-23` gives it no fixed meaning,
     * so removing one would be editing what somebody wrote.
     */
    private fun clearedOf(id: String): List<Pair<ItemDescription, String>> {
        val changed = ArrayList<Pair<ItemDescription, String>>()
        for (description in logbook.descriptions) {
            for (item in logbook.allOf(description)) {
                if (!clearIn(item, id)) continue
                logbook.idOf(item)?.let { changed += description to it }
            }
        }
        return changed
    }

    /** Takes [id] out of every reference field of [item], and says whether anything moved. */
    private fun clearIn(item: Item, id: String): Boolean {
        var moved = false
        for (field in item.description.fields) {
            if (field !is ReferenceDescription) continue
            when (val read = item.read(field.name)) {
                is Result.Usable -> {
                    if (field.cardinality == Cardinality.LIST) {
                        @Suppress("UNCHECKED_CAST")
                        val held = read.value as List<Element<Any>>
                        val kept = held.filterNot { names(it, id) }
                        if (kept.size != held.size) {
                            item.apply(field.name, Result.Usable(kept, read.origin))
                            moved = true
                        }
                    } else if (names(Element.Usable(read.value), id)) {
                        item.apply(field.name, Result.Absent)
                        moved = true
                    }
                }

                else -> Unit
            }
        }
        return moved
    }

    private fun names(held: Element<Any>, id: String): Boolean =
        held is Element.Usable && (held.value as? Reference.Identified)?.id == id

    companion object {

        /**
         * The logbook in the folder at [path], with the libraries it declares.
         *
         * The owner is whoever `yemoja.json` names, and is absent both where it names nobody and
         * where it names an item this logbook does not hold — a reference that resolves to
         * nothing is a dangling one, not a reason to refuse the logbook. `JSON-22`.
         */
        fun open(path: String): Universe {
            val store = DiskFileStore(path)
            // A folder that is not there answers every question with no, so without this a
            // mistyped path opens as an empty logbook rather than as a mistake.
            require(store.isFolder("")) { "$path should be a folder, and is not" }
            val manifest = LogbookReader.manifest(store)
            val items = LogbookReader.read(store, Types.ALL, manifest)
            val user = manifest.user?.let { items[it.id] }
            return Universe(items, if (user?.description == Types.PERSON) user else null, store)
        }
    }
}
