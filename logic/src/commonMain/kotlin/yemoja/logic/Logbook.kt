package yemoja.logic

import yemoja.data.Date
import yemoja.data.ItemSet
import yemoja.data.ReferenceableItem
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader

/**
 * Logbook is one open logbook: everything in it, and who it belongs to.
 *
 * **A stand-in for the Universe**, which `logic/doc.md` describes and nothing has built. The rule
 * it stands in for is the one that matters here: a front end names a folder and never opens it,
 * so no interface reaches a file, a store or a reader. When the Universe arrives this goes.
 *
 * [open] is the one place in this layer that names a source. Everything else takes an [ItemSet]
 * and runs against items assembled in memory, which is what `logic/doc.md` requires of the layer.
 */
class Logbook(val items: ItemSet, val user: ReferenceableItem?) {

    companion object {

        /**
         * The logbook in the folder at [path], with the libraries it declares.
         *
         * [today] is the day it is being opened on, which four fields are worked out against
         * and nothing else uses. It is taken rather than read from a clock, so that a test
         * states the day it means. `LOGIC-9`.
         *
         * The owner is whoever `yemoja.json` names, and is absent both where it names nobody and
         * where it names an item this logbook does not hold — a reference that resolves to
         * nothing is a dangling one, not a reason to refuse the logbook. `JSON-22`.
         */
        fun open(path: String, today: Date? = null): Logbook {
            val store = DiskFileStore(path)
            // A folder that is not there answers every question with no, so without this a
            // mistyped path opens as an empty logbook rather than as a mistake.
            require(store.isFolder("")) { "$path should be a folder, and is not" }
            val manifest = LogbookReader.manifest(store)
            val items = LogbookReader.read(store, Types.ALL, manifest, today)
            val user = manifest.user?.let { items[it.id] }
            return Logbook(items, if (user?.description == Types.PERSON) user else null)
        }
    }
}
