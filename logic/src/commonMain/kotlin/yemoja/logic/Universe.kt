package yemoja.logic

import yemoja.data.ItemSet
import yemoja.data.ReferenceableItem
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader

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
 * Not immutable: a logbook gains items while it is open.
 */
class Universe(val logbook: ItemSet, val user: ReferenceableItem?) {

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
            return Universe(items, if (user?.description == Types.PERSON) user else null)
        }
    }
}
