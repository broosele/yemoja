package yemoja.logic

import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
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
        val made = ArrayList<Pair<Change.Write, Result<Any>>>()
        for (change in changes) {
            if (change !is Change.Write) continue
            val prepared = change.item.prepared(change.field, change.given, Units.DEFAULT)
            if (prepared is Result.Unusable) return Outcome.Refused(prepared.reason)
            made += change to prepared
        }
        for ((change, prepared) in made) change.item.apply(change.field, prepared)

        val touched = LinkedHashSet<Pair<ItemDescription, String>>()
        for (change in changes) {
            when (change) {
                is Change.Write -> {
                    val owner = ownerOf(change.item)
                    logbook.idOf(owner as ReferenceableItem)
                        ?.let { touched += owner.description to it }
                }

                is Change.Add -> {
                    logbook.add(change.id, ItemReader.read(
                        change.description, nothing(), logbook, Units.DEFAULT,
                    ))
                    touched += change.description to change.id
                }

                is Change.Delete -> {
                    val going = logbook[change.id] ?: continue
                    val description = going.description
                    logbook.remove(change.id)
                    LogbookWriter.delete(store, description, change.id)
                }
            }
        }
        for ((description, id) in touched) {
            logbook[id]?.let { LogbookWriter.write(store, description, id, it) }
        }
        return Outcome.Done
    }

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
