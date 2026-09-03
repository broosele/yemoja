package yemoja.data.json

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
 * LogbookReader reads a logbook's files, and the libraries it declares, into a set of items.
 *
 * Which files those are is [FileStore.getPaths]'s to answer, and it answers in reading order. This
 * turns each of them into items: a file inside a type's folder is one item under the name of the
 * file, and any other file holds many under ids of their own.
 *
 * **A type is stored under its own name.** `dive` holds dives, so nothing here maps one name to
 * another and nothing here holds a list of type names — which it could not, being a source that
 * must work for a subject other than diving. `JSON-21`.
 */
object LogbookReader {

    /**
     * Every item in [store], of the [types] given.
     *
     * Where two files give one id the first wins and the second is passed over, which is how a
     * logbook item shadows a supplied one. Two items of one id within a single file are refused by
     * the parser, and one id used by two types in a logbook is refused by [ItemSet].
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

    private fun itemsOf(store: FileStore, type: String): Map<String, Stored.Members> {
        val paths = try {
            store.getPaths(type)
        } catch (stored: FileStoreAmbiguous) {
            throw LogbookFormatException(stored.message!!)
        }
        val items = LinkedHashMap<String, Stored.Members>()
        for (path in paths) {
            val read = membersOf(store, path)
            // A file inside the type's own folder is one item and its name is the id. A file's own
            // order is kept otherwise: unlike a folder listing it is an order somebody chose, and a
            // writer emits it settled so that saving an unchanged logbook produces no diff.
            if (path.startsWith("$type/")) {
                items.putIfAbsent(path.removePrefix("$type/").removeSuffix(".json"), read)
            } else {
                for ((id, held) in read.members) {
                    items.putIfAbsent(id, held as? Stored.Members ?: throw fieldsExpected(id, path))
                }
            }
        }
        return items
    }

    private fun fieldsExpected(id: String, path: String): LogbookFormatException =
        LogbookFormatException("$id in $path should be a set of fields")

    private fun membersOf(store: FileStore, path: String): Stored.Members {
        val read = try {
            Json.parse(store.readText(path))
        } catch (refused: JsonFormatException) {
            throw LogbookFormatException("$path should be JSON: ${refused.message}")
        }
        return read as? Stored.Members
            ?: throw LogbookFormatException("$path should hold a set of fields")
    }
}
