package yemoja.logic

import yemoja.data.ItemSet
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader

/**
 * Logbook is opening one, which a front end asks for and does not do itself.
 *
 * **A stand-in for the Universe**, which `logic/doc.md` describes and nothing has built. The
 * rule it stands in for is the one that matters here: a front end names a folder and never opens
 * it, so no interface reaches a file, a store or a reader. When the Universe arrives this goes.
 *
 * It is the one place in this layer that names a source. Everything else takes an [ItemSet] and
 * runs against items assembled in memory, which is what `logic/doc.md` requires of the layer.
 */
object Logbook {

    /**
     * Every item in the logbook at [path], of every type the application knows.
     *
     * **Nothing supplied is loaded.** A library lives with the installation rather than with the
     * logbook, and nothing here knows where this installation keeps its own, so the libraries a
     * logbook declares are all passed over.
     */
    fun open(path: String): ItemSet {
        val store = DiskFileStore(path)
        // A folder that is not there answers every question with no, so without this a mistyped
        // path opens as an empty logbook rather than as a mistake.
        require(store.isFolder("")) { "$path should be a folder, and is not" }
        return LogbookReader.read(store, Types.ALL)
    }
}
