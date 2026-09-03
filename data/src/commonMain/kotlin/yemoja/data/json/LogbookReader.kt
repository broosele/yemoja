package yemoja.data.json

import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Stored
import yemoja.data.Units

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
 * It starts at [MANIFEST], which names the libraries the logbook uses. Which files then hold a
 * type is [FileStore.getPaths]'s to answer, and it answers in reading order. This turns each of
 * them into items: a file inside a type's folder is one item under the name of the file, and any
 * other file holds many under ids of their own.
 *
 * **A type is stored under its own name.** `dive` holds dives, so nothing here maps one name to
 * another and nothing here holds a list of type names — which it could not, being a source that
 * must work for a subject other than diving. `JSON-21`.
 */
object LogbookReader {

    /** The file at the top of a logbook, saying whose it is and which libraries it uses. */
    const val MANIFEST: String = "yemoja.json"

    /**
     * The key a file declares its units under, in either shape.
     *
     * Reserved, so no item and no field may be called this. In a file holding many items it sits
     * among their ids, and in a file holding one it sits among that item's fields. `DATA-46`.
     */
    const val UNITS: String = "units"

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
        val declared = librariesOf(store)
        val set = ItemSet(types)
        for (description in types) {
            val named = declared[description.name].orEmpty()
            for ((id, held) in itemsOf(store, description.name, named)) {
                set.add(id, ItemReader.read(description, held.members, set, held.units))
            }
        }
        return set
    }

    /**
     * The libraries [MANIFEST] declares, by the type they hold.
     *
     * **A logbook without a manifest reads as one declaring nothing.** Whether the file is
     * required has never been settled, and being strict would make a folder of dives unreadable
     * for want of a file saying only what it has none of.
     */
    private fun librariesOf(store: FileStore): Map<String, List<String>> {
        if (!store.isFile(MANIFEST)) return emptyMap()
        val declared = membersOf(store, MANIFEST).members[FileStore.LIBRARIES] ?: return emptyMap()
        if (declared !is Stored.Members) {
            throw LogbookFormatException(
                "${FileStore.LIBRARIES} in $MANIFEST should hold a list of names for each type",
            )
        }
        return declared.members.mapValues { (type, named) -> namesOf(type, named) }
    }

    private fun namesOf(type: String, named: Stored): List<String> {
        if (named !is Stored.Elements) throw namesExpected(type)
        return named.elements.map {
            (it as? Stored.Leaf)?.value as? String ?: throw namesExpected(type)
        }
    }

    private fun namesExpected(type: String): LogbookFormatException = LogbookFormatException(
        "${FileStore.LIBRARIES}.$type in $MANIFEST should be a list of library names",
    )

    /** One item as a file held it, with what the numbers of that file are written in. */
    private class Held(val members: Stored.Members, val units: Units)

    private fun itemsOf(
        store: FileStore,
        type: String,
        libraries: List<String>,
    ): Map<String, Held> {
        val paths = try {
            store.getPaths(type, libraries)
        } catch (stored: FileStoreAmbiguous) {
            throw LogbookFormatException(stored.message!!)
        }
        val items = LinkedHashMap<String, Held>()
        for (path in paths) {
            val read = membersOf(store, path)
            val units = unitsOf(read, path)
            val rest = Stored.Members(read.members - UNITS)
            // A file inside the type's own folder is one item and its name is the id. A file's own
            // order is kept otherwise: unlike a folder listing it is an order somebody chose, and a
            // writer emits it settled so that saving an unchanged logbook produces no diff.
            if (path.startsWith("$type/")) {
                val id = path.removePrefix("$type/").removeSuffix(".json")
                items.putIfAbsent(id, Held(rest, units))
            } else {
                for ((id, held) in rest.members) {
                    val members = held as? Stored.Members ?: throw fieldsExpected(id, path)
                    items.putIfAbsent(id, Held(members, units))
                }
            }
        }
        return items
    }

    /**
     * What the numbers of one file are written in.
     *
     * A file declaring nothing is written in the defaults. A name nobody knows is kept and refuses
     * its own dimension when a value is read, so it does not stop the file. `DATA-87`.
     */
    private fun unitsOf(read: Stored.Members, path: String): Units {
        val declared = read.members[UNITS] ?: return Units.DEFAULT
        if (declared !is Stored.Members) {
            throw LogbookFormatException("$UNITS in $path should name a unit for each dimension")
        }
        return Units.of(declared.members.mapValues { (it.value as? Stored.Leaf)?.value })
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
