package yemoja.data.json

import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemWriter
import yemoja.data.Stored
import yemoja.data.Units

/*
 * Putting an item back into the logbook's files: the inverse of LogbookReader.
 *
 * See ../../../../../../json/doc.md.
 */

/**
 * LogbookWriter puts one item into the logbook, or takes one out.
 *
 * **It works from the file rather than from the set**, and that is what makes it correct rather
 * than merely convenient. Two things a reader does not keep are still in the file: what units it
 * is written in, and which items are the logbook's own rather than a library's. A writer that
 * rebuilt a file from `allOf` would answer neither — it would rewrite an imperial logbook into
 * metres, and copy every supplied region into somebody's own `region.json`. Reading the file it
 * is about to replace answers both, and costs one read of a small file. `JSON-26`.
 *
 * What follows is worth having on its own: every item the writer did not touch goes back as the
 * raw it was read as, so a save changes only what was changed.
 *
 * **A file is replaced whole**, and a change spanning two files can be interrupted between them.
 * `JSON-25` deferred that deliberately.
 */
object LogbookWriter {

    /**
     * Puts [item] in the logbook under [id], replacing whatever was there.
     *
     * The layout is the one the type already uses, `JSON-21` — a file in the type's folder, or a
     * member of the type's own file. A type stored neither way yet is written grouped, since a
     * logbook with three dives in it should not be a folder of three files, and `JSON-21` lets a
     * user split it by hand afterwards.
     */
    fun write(store: FileStore, description: ItemDescription, id: String, item: Item) {
        val type = description.name
        val folder = "$type/$id.json"
        if (store.isFolder(type)) {
            val (held, units) = openedOr(store, folder)
            store.writeText(folder, fileOf(withUnits(ItemWriter.write(item, units), held)))
            return
        }
        val file = "$type.json"
        val (held, units) = openedOr(store, file)
        val members = LinkedHashMap(held.members)
        members[id] = ItemWriter.write(item, units)
        store.writeText(file, fileOf(Stored.Members(members)))
    }

    /**
     * Puts every one of [items] in the logbook, each under its id, telling [told] after each.
     *
     * What [write] does for one, done for many in one pass. A type stored in one file is read once
     * and written once rather than once an item, which for a few hundred dives is the difference
     * between a moment and minutes: each write of a shared file reads and writes all of it.
     *
     * [apart] puts each item in a file of its own where the type has no file yet, for a logbook
     * whose items will be taken out one at a time: taking one out of a shared file writes the
     * rest of it back. `JSON-21` reads either layout.
     */
    fun writeAll(
        store: FileStore,
        description: ItemDescription,
        items: List<Pair<String, Item>>,
        apart: Boolean = false,
        told: () -> Unit = {},
    ) {
        if (items.isEmpty()) return
        val type = description.name
        if (store.isFolder(type)) {
            for ((id, item) in items) {
                write(store, description, id, item)
                told()
            }
            return
        }
        if (apart && !store.isFile("$type.json")) {
            for ((id, item) in items) {
                store.writeText("$type/$id.json", fileOf(ItemWriter.write(item, Units.DEFAULT)))
                told()
            }
            return
        }
        val file = "$type.json"
        val (held, units) = openedOr(store, file)
        val members = LinkedHashMap(held.members)
        for ((id, item) in items) {
            members[id] = ItemWriter.write(item, units)
            told()
        }
        store.writeText(file, fileOf(Stored.Members(members)))
    }

    /**
     * Takes the item called [id] out of the logbook.
     *
     * A file of its own goes; a member of a shared file is removed and the file written back. The
     * file is left where it holds nothing, since an empty `dive.json` is a logbook with no dives
     * and `JSON-21` reads it as one.
     */
    fun delete(store: FileStore, description: ItemDescription, id: String) {
        val type = description.name
        if (store.isFolder(type)) {
            store.delete("$type/$id.json")
            return
        }
        val file = "$type.json"
        if (!store.isFile(file)) return
        val (held, _) = openedOr(store, file)
        if (id !in held.members) return
        store.writeText(file, fileOf(Stored.Members(held.members - id)))
    }

    /**
     * The units the file holding the item called [id] declares, or the defaults where it declares
     * none or does not exist yet.
     *
     * Asked before a change lands, so a measurement is not written in a unit the file cannot say:
     * a file declaring length in fathoms gives up its depths as unusable, `DATA-87`, and a depth
     * typed into one in metres would otherwise be saved as metres under the fathom declaration.
     */
    fun unitsOf(store: FileStore, description: ItemDescription, id: String): Units {
        val type = description.name
        val path = if (store.isFolder(type)) "$type/$id.json" else "$type.json"
        return try {
            openedOr(store, path).second
        } catch (unreadable: LogbookFormatException) {
            Units.DEFAULT
        }
    }

    /** What is in the file at [path] now, or nothing where there is no file yet. */
    private fun openedOr(store: FileStore, path: String): Pair<Stored.Members, Units> {
        if (!store.isFile(path)) return Stored.Members(emptyMap()) to Units.DEFAULT
        val read = Json.parse(store.readText(path)) as? Stored.Members
            ?: throw LogbookFormatException("$path should hold a set of fields")
        return read to unitsIn(read)
    }

    /**
     * The `units` block a file already carries, kept on the file it belongs to.
     *
     * A declaration reaches no further than its own file, so it travels with the file rather than
     * with the item. Keeping it is what holds `DATA-76`'s promise that a logbook written in feet
     * stays in feet.
     */
    private fun withUnits(written: Stored.Members, was: Stored.Members): Stored.Members {
        val declared = was.members[LogbookReader.UNITS] ?: return written
        val members = LinkedHashMap<String, Stored>()
        members[LogbookReader.UNITS] = declared
        members.putAll(written.members)
        return Stored.Members(members)
    }

    private fun unitsIn(read: Stored.Members): Units {
        val declared = read.members[LogbookReader.UNITS] as? Stored.Members ?: return Units.DEFAULT
        return Units.of(declared.members.mapValues { (_, held) -> (held as? Stored.Leaf)?.value })
    }
}

/**
 * [stored] as a file holds it, ending with a newline.
 *
 * What a person writes by hand, what the fixtures hold, and what the manifest and settings are
 * written as. Without it the first save of a hand-written file changes a line nobody touched.
 */
private fun fileOf(stored: Stored): String = Json.write(stored) + "\n"
