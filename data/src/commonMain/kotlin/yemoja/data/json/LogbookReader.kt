package yemoja.data.json

import yemoja.data.FileStore
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Stored

/**
 * LogbookFormatException is thrown when a folder is not a logbook this version can read.
 *
 * A whole logbook rather than a value or a file: something about the shape of the folder stops the
 * reading before any item exists. A value that cannot be believed is kept and reported instead,
 * which is `DATA-24` and a different matter entirely.
 */
class LogbookFormatException(message: String) : RuntimeException(message)

/**
 * LogbookReader reads a folder of files into a set of items.
 *
 * A logbook says nothing about its own layout. Each type is stored under a fixed name, as either a
 * folder of one file per item or a single file holding them all, and this uses whichever is there.
 * `JSON-21`.
 *
 * **A type is stored under its own name.** `dive` holds dives, so nothing here maps one name to
 * another and nothing here holds a list of type names — which it could not, being a source that
 * must work for a subject other than diving. `JSON-21`.
 */
object LogbookReader {

    /**
     * Every item in [store], of the [types] given.
     *
     * A type with neither a folder nor a file contributes nothing, which is how a logbook with no
     * wrecks in it reads.
     *
     * Throws where the folder is not a logbook: a type stored both ways at once, a file that is
     * not JSON, or a file whose shape is not a set of items. **One unreadable file stops the whole
     * read**, which is the strict answer and probably not the last word — reporting the file and
     * carrying on would need somewhere to report to, and nothing has settled what that is.
     */
    fun read(store: FileStore, types: List<ItemDescription>): ItemSet {
        val set = ItemSet(types)
        for (description in types) {
            for ((id, stored) in itemsOf(store, description.name)) {
                set.add(id, ItemReader.read(description, stored, set))
            }
        }
        return set
    }

    private fun itemsOf(store: FileStore, name: String): Map<String, Stored.Members> {
        val file = "$name.json"
        val asFolder = store.isFolder(name)
        val asFile = store.isFile(file)
        return when {
            asFolder && asFile -> throw LogbookFormatException(
                "$name is stored as a folder and as $file, and only one of them can be the $name",
            )

            asFolder -> onePerFile(store, name)
            asFile -> grouped(store, file)
            else -> emptyMap()
        }
    }

    /**
     * A folder of one file per item, the id being the file name without `.json`.
     *
     * Sorted, because what a folder lists first is the platform's business and two machines
     * reading the same logbook should hold it the same way. Anything not ending in `.json` is
     * passed over rather than complained about: a logbook is a folder someone else may also use.
     */
    private fun onePerFile(store: FileStore, folder: String): Map<String, Stored.Members> {
        val items = LinkedHashMap<String, Stored.Members>()
        for (fileName in store.namesIn(folder).filter { it.endsWith(".json") }.sorted()) {
            val path = "$folder/$fileName"
            items[fileName.removeSuffix(".json")] = membersOf(store, path, path)
        }
        return items
    }

    /**
     * One file holding every item of a type, each under its id.
     *
     * The file's own order is kept. Unlike a folder listing it is an order somebody chose, and a
     * writer emits it settled so that saving an unchanged logbook produces no diff.
     */
    private fun grouped(store: FileStore, path: String): Map<String, Stored.Members> {
        val items = LinkedHashMap<String, Stored.Members>()
        for ((id, held) in membersOf(store, path, path).members) {
            items[id] = held as? Stored.Members
                ?: throw LogbookFormatException("$id in $path should be a set of fields")
        }
        return items
    }

    private fun membersOf(store: FileStore, path: String, what: String): Stored.Members {
        val read = try {
            Json.parse(store.readText(path))
        } catch (refused: JsonFormatException) {
            throw LogbookFormatException("$what should be JSON: ${refused.message}")
        }
        return read as? Stored.Members
            ?: throw LogbookFormatException("$what should hold a set of fields")
    }
}
