package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.ItemWriter
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.data.json.FileStore
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.LogbookWriter

/*
 * What an agent would change, kept where somebody can look at it before it happens.
 *
 * See ../../../../../reconciliation.md — `RECON-8`.
 */

/**
 * Changed is one field a staging would change, as a reader is shown it.
 *
 * [at] is where the field sits inside its item, written the way the journal addresses one:
 * `max_depth`, `environment.bottom_temperature`, `gas_sources.g1.usage`. `JSON-14`.
 *
 * [from] and [to] are what a file would write, so they read as the user's own files do. No [from]
 * is a field that held nothing; no [to] is one being cleared. [now] is what the logbook holds
 * there at the moment of asking, which is [from] unless somebody has been editing.
 *
 * Immutable.
 */
data class Changed(val at: String, val from: String?, val to: String?, val now: String? = from) {

    /**
     * Whether the logbook has moved under this change since it was staged.
     *
     * A reviewer is shown it before applying rather than told afterwards: a change decided about a
     * value nobody holds any more is one to look at again, and [Staging.apply] will leave it
     * alone. `RECON-8`.
     */
    val moved: Boolean get() = now != from
}

/**
 * Staged is one item a staging would change, and how.
 *
 * Which of the three it is follows from what the staging holds rather than from anything marked:
 * an item with no copy of how it was is one to add, an item with no copy of how it would be is one
 * to delete, and an item with both is one to edit.
 *
 * Immutable.
 */
data class Staged(val id: String, val type: String, val kind: Kind, val fields: List<Changed>) {

    /** Kind is what a staging would do to one item. */
    enum class Kind { ADD, EDIT, DELETE }
}

/**
 * Refused is one thing a staging could not do, and why.
 *
 * [at] is empty where what was refused is a whole item rather than one of its fields.
 *
 * Immutable.
 */
data class Refused(val id: String, val at: String, val reason: String)

/**
 * Applied is what came of applying a staging: what landed, and what did not.
 *
 * Immutable.
 */
data class Applied(val items: Int, val fields: Int, val refused: List<Refused>)

/**
 * Staging is a set of changes staged by an agent, waiting for somebody to look at them.
 *
 * **It is two copies of every item it touches**: the item as it was when the change was staged,
 * and the item as the agent would have it. Both are logbooks of their own in a folder beside the
 * real one, so nothing new reads or writes them — `RECON-1`'s answer for an import, applied to a
 * change. What is in them is not part of the logbook and is not read as though it were.
 *
 * Three things follow from the pair, with nothing marked. An item with no *before* is one to add,
 * an item with no *after* is one to delete, and an item in both is one to edit whose changed
 * fields are the difference between the copies. The *before* copy is also what makes a collision
 * visible: a field whose value in the logbook has moved since it was staged is left alone, because
 * what was decided was decided about a value nobody holds any more. `RECON-8`.
 *
 * **Nothing here changes the logbook** but [apply], and only when it is called.
 *
 * Not immutable: a staging is built, looked at, and then applied or dropped.
 */
class Staging private constructor(
    private val into: Universe,
    private val before: Half,
    private val after: Half,
) {

    /** Every item this staging would touch, in the order their types are declared in. */
    val staged: List<Staged>
        get() = (before.ids() + after.ids()).distinct().mapNotNull { stagedOf(it) }

    /** Whether nothing at all is staged. */
    val empty: Boolean get() = staged.isEmpty()

    /**
     * Stage [value] in the field [path] names on the item called [id].
     *
     * A null clears the field. What is given is read by the field itself, so what a file would
     * refuse is refused here with the field's own reason. Staging the same field twice keeps the
     * last of them, and the copy of how the item was stays as it was when the first was staged.
     */
    fun set(id: String, path: String, value: Any?): Outcome {
        val held = into.logbook[id] ?: return Outcome.Refused("$id names nothing")
        before.keep(id, held)
        val copy = after.copyOf(id, held)
        val at = walkTo(copy, path)
            ?: return Outcome.Refused("${held.description.name} has no field at $path")
        val made = at.item.prepared(at.field, value, Units.DEFAULT)
        if (made is Result.Unusable) return Outcome.Refused(made.reason)
        at.item.apply(at.field, made)
        after.write(id, copy)
        return Outcome.Done()
    }

    /**
     * Stage an item of [type] holding [fields], to be added under an id minted when it is applied.
     *
     * It is staged under a name of its own — `new#0` and the like — because an item has no id
     * until it is added and a review has to call it something. Nothing may point at it while it is
     * staged: the id it will have is not known yet, and inventing one would be inventing the
     * reference too.
     */
    fun add(type: String, fields: Map<String, Any?> = emptyMap()): Outcome {
        val description = into.logbook.descriptions.firstOrNull { it.name == type }
            ?: return Outcome.Refused("$type is not a type this logbook holds")
        val made = ItemReader.read(description, Stored.Members(emptyMap()), into.logbook, Units.DEFAULT)
        for ((name, value) in fields) {
            if (description[name] == null) {
                return Outcome.Refused("$type has no field called $name")
            }
            val read = made.prepared(name, value, Units.DEFAULT)
            if (read is Result.Unusable) return Outcome.Refused(read.reason)
            made.apply(name, read)
        }
        after.write(freshId(), made)
        return Outcome.Done()
    }

    /**
     * Stage the item called [id] to be deleted.
     *
     * What it holds is kept as it was, so a review can say what would go. References to it are
     * left dangling, which is a state the model carries and an interface shows. `DATA-117`.
     */
    fun delete(id: String): Outcome {
        val held = into.logbook[id] ?: return Outcome.Refused("$id names nothing")
        before.keep(id, held)
        after.remove(id, held.description)
        return Outcome.Done()
    }

    /** Take the item called [id] out of the staging, changing nothing in the logbook. */
    fun drop(id: String) {
        val description = (before.set[id] ?: after.set[id])?.description ?: return
        before.remove(id, description)
        after.remove(id, description)
    }

    /** Forget everything staged, changing nothing in the logbook. */
    fun clear() {
        for (id in (before.ids() + after.ids()).distinct()) drop(id)
    }

    /**
     * Do what is staged, as one change, and say what landed.
     *
     * **A field whose value has moved since it was staged is left alone**, and the rest still
     * lands: an agent that corrected forty dives should not have all forty refused because
     * somebody edited one of them meanwhile. What was refused is named, so it can be looked at
     * and staged again against what is there now.
     *
     * Applying empties the staging, what has happened being no longer proposed.
     */
    fun apply(): Applied {
        val refused = ArrayList<Refused>()
        val changes = ArrayList<Change>()
        var items = 0
        var fields = 0
        for (proposed in staged) {
            when (proposed.kind) {
                Staged.Kind.DELETE -> {
                    if (into.logbook[proposed.id] == null) {
                        refused += Refused(proposed.id, "", "it has gone already")
                        continue
                    }
                    changes += Change.Delete(proposed.id)
                    items += 1
                }

                Staged.Kind.ADD -> {
                    val staged = after.set[proposed.id] ?: continue
                    changes += Change.Add(staged.description, storedOf(staged).members)
                    items += 1
                }

                Staged.Kind.EDIT -> {
                    val held = into.logbook[proposed.id]
                    if (held == null) {
                        refused += Refused(proposed.id, "", "it has been deleted since")
                        continue
                    }
                    val landing = ArrayList<Change>()
                    for (change in proposed.fields) {
                        val now = writtenAt(held, change.at)
                        if (now != change.from) {
                            refused += Refused(
                                proposed.id,
                                change.at,
                                "it was ${said(change.from)} when this was staged, and is " +
                                    "${said(now)} now",
                            )
                            continue
                        }
                        val at = walkTo(held, change.at) ?: continue
                        landing += Change.Write(at.item, at.field, storedAt(proposed.id, change.at))
                    }
                    if (landing.isNotEmpty()) {
                        changes += landing
                        fields += landing.size
                        items += 1
                    }
                }
            }
        }
        if (changes.isEmpty()) return Applied(0, 0, refused)
        return when (val done = into.change(Operation.EDIT, *changes.toTypedArray())) {
            is Outcome.Refused -> Applied(0, 0, refused + Refused("", "", done.reason))
            is Outcome.Done -> {
                clear()
                Applied(items, fields, refused)
            }
        }
    }

    /** What is staged for the item called [id], or absent where nothing is. */
    private fun stagedOf(id: String): Staged? {
        val was = before.set[id]
        val would = after.set[id]
        if (was == null && would == null) return null
        if (was == null) {
            return Staged(id, would!!.description.name, Staged.Kind.ADD, fieldsOf(null, would))
        }
        if (would == null) {
            return Staged(id, was.description.name, Staged.Kind.DELETE, asNow(id, fieldsOf(was, null)))
        }
        val fields = fieldsOf(was, would)
        return if (fields.isEmpty()) null
        else Staged(id, was.description.name, Staged.Kind.EDIT, asNow(id, fields))
    }

    /** [fields] with what the logbook holds now beside what it held when each was staged. */
    private fun asNow(id: String, fields: List<Changed>): List<Changed> {
        val held = into.logbook[id] ?: return fields.map { it.copy(now = null) }
        return fields.map { it.copy(now = writtenAt(held, it.at)) }
    }

    /** An id for an item that has none yet, which is what a review calls it. */
    private fun freshId(): String {
        var at = 0
        while (after.set["$NEW$at"] != null) at += 1
        return "$NEW$at"
    }

    /** What walking a path reached: the item holding the field, and the field's own name. */
    private class At(val item: Item, val field: String)

    /** The item and field [path] names inside [item], or absent where it names none. */
    private fun walkTo(item: Item, path: String): At? {
        val segments = path.split('.')
        var at: Item = item
        var index = 0
        while (index < segments.size) {
            val field = at.description[segments[index]] ?: return null
            if (field !is OwnedItemDescription) {
                return if (index == segments.lastIndex) At(at, field.name) else null
            }
            val value = (at.read(field.name) as? Result.Usable)?.value ?: return null
            if (field.cardinality == Cardinality.SINGLE) {
                at = value as? Item ?: return null
                index += 1
            } else {
                val key = segments.getOrNull(index + 1) ?: return null
                at = ((value as? Map<*, *>)?.get(key) as? Element.Usable<*>)?.value as? Item
                    ?: return null
                index += 2
            }
        }
        return null
    }

    /** Every field that differs between [was] and [would], as a file would write each of them. */
    private fun fieldsOf(was: Item?, would: Item?): List<Changed> {
        val from = was?.let { storedOf(it).members }.orEmpty()
        val to = would?.let { storedOf(it).members }.orEmpty()
        val changed = ArrayList<Changed>()
        for (name in from.keys + to.keys) differences(name, from[name], to[name], changed)
        return changed
    }

    /**
     * What differs between [from] and [to] under [at], one line per field however deep it sits.
     *
     * An owned item is gone into, so a review says `environment.visibility` rather than saying that
     * the environment changed. Everything else is one value to a reader — a list, a series, a gas
     * mix — and is compared whole. `JSON-14`.
     */
    private fun differences(at: String, from: Stored?, to: Stored?, into: MutableList<Changed>) {
        if (from == to) return
        if (from is Stored.Members && to is Stored.Members) {
            for (name in from.members.keys + to.members.keys) {
                differences("$at.$name", from.members[name], to.members[name], into)
            }
            return
        }
        into += Changed(at, written(from), written(to))
    }

    /** What the staged copy of [id] holds at [path], as something a change can be given. */
    private fun storedAt(id: String, path: String): Stored? {
        var stored: Stored? = after.set[id]?.let { storedOf(it) }
        for (segment in path.split('.')) {
            stored = (stored as? Stored.Members)?.members?.get(segment) ?: return null
        }
        return stored
    }

    /** What [item] holds at [path], written as a file would write it. */
    private fun writtenAt(item: Item, path: String): String? {
        var stored: Stored? = storedOf(item)
        for (segment in path.split('.')) {
            stored = (stored as? Stored.Members)?.members?.get(segment) ?: return null
        }
        return written(stored)
    }

    /** [item] as a file would write it. */
    private fun storedOf(item: Item): Stored.Members = ItemWriter.write(item, Units.DEFAULT)

    /** What a stored value reads as, or absent where there is none. */
    private fun written(stored: Stored?): String? = when (stored) {
        null -> null
        is Stored.Leaf -> stored.value?.toString()
        else -> Json.write(stored)
    }

    /** What is said of a value that is not there. */
    private fun said(written: String?): String = written ?: "nothing"

    companion object {

        /** What the folder holding a staging is called, beside the logbook. */
        const val BESIDE: String = ".proposed"

        /**
         * A staging for [into], kept in [store], taking up whatever is already staged there.
         *
         * [store] is a folder beside the logbook rather than inside it, for the reason staging an
         * import is: what is in it is not part of the logbook.
         */
        fun open(into: Universe, store: FileStore): Staging {
            val types = into.logbook.descriptions
            return Staging(into, Half(store, BEFORE, types), Half(store, AFTER, types))
        }

        /** Where the copies of how items were are kept. */
        private const val BEFORE = "before"

        /** Where the copies of how they would be are kept. */
        private const val AFTER = "after"

        /** What an item to be added is called until it is added and named properly. */
        private const val NEW = "new#"
    }
}

/**
 * Staged is one half of a staging: copies of items, in a folder of their own.
 *
 * Both halves are ordinary logbooks, so what reads and writes one reads and writes these.
 * Everything is written the moment it is staged, so a staging outlives the window closing the way
 * a review of an import does. `RECON-1`.
 *
 * Not immutable.
 */
private class Half(
    store: FileStore,
    within: String,
    private val types: List<ItemDescription>,
) {

    private val store = Within(store, within)

    /** What is staged, read from the folder the first time it is asked for. */
    val set: ItemSet by lazy {
        if (this.store.isFolder("")) LogbookReader.read(this.store, types) else ItemSet(types)
    }

    /** Every id staged here, each type in the order it was declared in. */
    fun ids(): List<String> = types.flatMap { type -> set.allOf(type).mapNotNull { set.idOf(it) } }

    /** Keep [item] as it is now, unless a copy of it is kept already. */
    fun keep(id: String, item: ReferenceableItem) {
        if (set[id] == null) write(id, item)
    }

    /** The copy of [item] this half holds, made from the item where there is none yet. */
    fun copyOf(id: String, item: ReferenceableItem): ReferenceableItem {
        keep(id, item)
        return set.getValue(id)
    }

    /** Write [item] in under [id], over whatever was there. */
    fun write(id: String, item: Item) {
        val copy = ItemReader.read(
            item.description,
            ItemWriter.write(item, Units.DEFAULT),
            set,
            Units.DEFAULT,
        )
        set.remove(id)
        set.add(id, copy)
        LogbookWriter.write(store, item.description, id, copy)
    }

    /** Take [id] out of this half, and off the disk. */
    fun remove(id: String, description: ItemDescription) {
        set.remove(id)
        LogbookWriter.delete(store, description, id)
    }
}

/** What [id] names here, which a caller that has just written it knows is there. */
private fun ItemSet.getValue(id: String): ReferenceableItem =
    this[id] ?: error("$id was written and is not here")

/** A folder inside a store, so that one folder can hold the two halves of a staging. */
private class Within(private val store: FileStore, private val within: String) : FileStore {

    override fun isFile(path: String): Boolean = store.isFile(at(path))

    override fun isFolder(path: String): Boolean = store.isFolder(at(path))

    override fun namesIn(path: String): List<String> = store.namesIn(at(path))

    override fun readText(path: String): String = store.readText(at(path))

    override fun writeText(path: String, text: String) = store.writeText(at(path), text)

    override fun delete(path: String) = store.delete(at(path))

    private fun at(path: String): String = if (path.isEmpty()) within else "$within/$path"
}
