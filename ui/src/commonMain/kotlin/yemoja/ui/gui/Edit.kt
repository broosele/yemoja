package yemoja.ui.gui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.ItemReader
import yemoja.data.ItemWriter
import yemoja.data.KeyReference
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.data.TimeDescription
import yemoja.data.Units
import yemoja.data.WholeNumberDescription
import yemoja.logic.Change
import yemoja.logic.freeName
import kotlin.math.roundToLong

/*
 * What an edit form holds and hands to the model: the draft of every field changed, and the
 * text each kind of field is edited as. Nothing here draws; the form in Form.kt does.
 *
 * Every value travels as the text a file would hold it as, which is what the model reads and
 * what the terminal front end already writes, so the two front ends cannot disagree about what
 * `12.3` or `@anna` means. A yes-or-no travels as itself, and a list as its entries.
 *
 * See ../../../../../../gui/doc.md — `GUI-29`.
 */

/** Slot is one field of one item in a form, the item by identity: an owned item has no id. */
internal class Slot(val item: Item, val field: String) {
    override fun equals(other: Any?): Boolean =
        other is Slot && other.item === item && other.field == field

    override fun hashCode(): Int = System.identityHashCode(item) * 31 + field.hashCode()
}

/**
 * Drafted is one field as changed in a form: what the form shows for it, and what the model is
 * given for it, which differ where the form's text is not the file's — a clock for a time.
 */
internal class Drafted(val shown: Any?, val given: Any?)

/**
 * Draft is what an edit form holds before it is saved: for each field changed, on the item or
 * on anything it owns, what it was changed to, an absent being a field cleared.
 *
 * Not immutable: the form writes into it as the user types, and reads from it to draw.
 */
internal class Draft {
    private val held: SnapshotStateMap<Slot, Drafted> = mutableStateMapOf()

    /** The blocks this form began, which the item has none of until something is typed in. */
    private val begun: MutableList<Begun> = ArrayList()

    /** Begun is an owned item drawn for an item that has none: what it is, and whose it is. */
    private class Begun(val block: Item, val owner: Item, val name: String)

    /**
     * A block to draw the fields of [inset] into, which [owner] may not have yet.
     *
     * **Detached until something is typed.** The form offers an absent block's fields the same
     * way it offers a present one's, so a gear item with no buoyancy can be given a mass by
     * typing one; what it must not do is write an empty block into the logbook on the way past.
     * So the block lives here until Save, and lands only if it has anything in it. `GUI-29`.
     */
    fun begin(owner: Item, inset: OwnedItemDescription): Item {
        begun.firstOrNull { it.owner === owner && it.name == inset.name }?.let { return it.block }
        val block = ItemReader.read(
            inset.description,
            Stored.Members(emptyMap()),
            owner.set,
            Units.DEFAULT,
        )
        begun += Begun(block, owner, inset.name)
        return block
    }

    /** Whether [field] of [item] has been changed in this form. */
    fun changed(item: Item, field: String): Boolean = Slot(item, field) in held

    /** What the form shows for [field] of [item], where it has been changed; absent otherwise. */
    fun shownOf(item: Item, field: String): Any? = held[Slot(item, field)]?.shown

    /** Changes [field] of [item] to [given], the form showing [shown] for it. */
    fun put(item: Item, field: String, shown: Any?, given: Any? = shown) {
        held[Slot(item, field)] = Drafted(shown, given)
    }

    /** Forgets the change to [field] of [item], leaving what was stored. */
    fun drop(item: Item, field: String) {
        held.remove(Slot(item, field))
    }

    /** Forgets every change to [item], for an entry that is no longer the one in the logbook. */
    fun dropAll(item: Item) {
        held.keys.filter { it.item === item }.forEach { held.remove(it) }
    }

    /**
     * Forgets every change and every block begun, which is what closing the form does.
     *
     * Cancel and a Save that went through both close it. Without this the form reopened on the
     * same item with what had been cancelled still typed in it, and Save offered to write it.
     */
    fun clear() {
        held.clear()
        begun.clear()
    }

    val isEmpty: Boolean get() = held.isEmpty()

    /**
     * Every change as the model takes it; the model judges them whole.
     *
     * One per field changed, bar the fields of a block the form began: those become one write of
     * the whole block onto its owner, and no write at all where nothing was typed into it. An
     * empty block says exactly what no block says. `DATA-116`.
     */
    fun writes(): List<Change> {
        val out = ArrayList<Change>()
        for ((slot, drafted) in held) {
            if (begun.any { it.block === slot.item }) continue
            out += Change.Write(slot.item, slot.field, storedOf(drafted.given))
        }
        for (one in begun) {
            val members = LinkedHashMap<String, Stored>()
            for ((slot, drafted) in held) {
                if (slot.item !== one.block) continue
                storedOf(drafted.given)?.let { members[slot.field] = it }
            }
            if (members.isNotEmpty()) {
                out += Change.Write(one.owner, one.name, Stored.Members(members))
            }
        }
        return out
    }

    /**
     * What was typed into [item], as the change that makes it would take it.
     *
     * For an item being made, which is not in the logbook and has no id: the fields it was given
     * go into `Change.Add`, and the id is minted from them. Blocks begun inside it fold in as
     * their own members, there being no owner yet to write them onto. `GUI-35`.
     */
    fun fieldsOf(item: Item): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((slot, drafted) in held) {
            if (slot.item === item) out[slot.field] = storedOf(drafted.given)
        }
        for (one in begun) {
            if (one.owner !== item) continue
            val members = LinkedHashMap<String, Stored>()
            for ((slot, drafted) in held) {
                if (slot.item !== one.block) continue
                storedOf(drafted.given)?.let { members[slot.field] = it }
            }
            if (members.isNotEmpty()) out[one.name] = Stored.Members(members)
        }
        return out
    }

    /**
     * What the model would refuse [field] of [item] for as drafted, which is found before
     * anything is saved; absent where the field is not drafted or the model takes it.
     */
    fun refusalOf(item: Item, field: String): String? {
        val drafted = held[Slot(item, field)] ?: return null
        val made = item.prepared(field, storedOf(drafted.given), Units.DEFAULT)
        return (made as? Result.Unusable)?.reason
    }
}

/** A drafted value as the model takes it: nothing for a clearing, a list as its entries. */
internal fun storedOf(value: Any?): Stored? = when (value) {
    null -> null
    is Stored -> value
    is List<*> -> Stored.Elements(value.map { Stored.Leaf(it ?: "") })
    else -> Stored.Leaf(value)
}

/**
 * The keyed collection [name] of [item] as it would be written without the entry under [key]:
 * every other entry as its stored form, which is how a collection is changed at all.
 */
internal fun withoutEntry(item: Item, name: String, key: String): Stored.Members {
    val kept = LinkedHashMap<String, Stored>()
    for ((held, entry) in keyedEntriesOf(item, name)) {
        if (held != key) kept[held] = ItemWriter.write(entry, Units.DEFAULT)
    }
    return Stored.Members(kept)
}

/**
 * The keyed collection [name] of [item] with an empty entry added, and the key it was added
 * under: the key its type proposes for an entry holding nothing, the first free one taken, which
 * is the rule an id follows one level up.
 */
internal fun withEntry(item: Item, name: String): Pair<String, Stored.Members> {
    val within = (item.description[name] as OwnedItemDescription).description
    val written = LinkedHashMap<String, Stored>()
    for ((key, entry) in keyedEntriesOf(item, name)) {
        written[key] = ItemWriter.write(entry, Units.DEFAULT)
    }
    val empty = ItemReader.read(within, Stored.Members(emptyMap()), item.set, Units.DEFAULT)
    val key = freeName(within.proposedId?.invoke(empty) ?: within.name) { it in written }
    written[key] = Stored.Members(emptyMap())
    return key to Stored.Members(written)
}

/** The entries of the keyed collection [name] of [item], in the order held. */
@Suppress("UNCHECKED_CAST")
internal fun keyedEntriesOf(item: Item, name: String): List<Pair<String, OwnedItem>> =
    ((item.read(name) as? Result.Usable)?.value as? Map<String, Element<Any>>).orEmpty()
        .mapNotNull { (key, element) ->
            ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
        }

/** Kind is how a field is edited, decided from its description. */
internal enum class Kind {
    TEXT, LONG_TEXT, CHOICE, SUGGESTED, NUMBER, CLOCK, WHOLE, RATING, DATE, TIME, YES_NO, REFERENCE,
    KEY, GAS,
    /** How far one clock is ahead of another, typed as hours and minutes: `+2:00`. */
    OFFSET,
    /** Not as a field: a series is read on the graph. */
    NONE,
}

/** How [field] is edited. */
internal fun kindOf(field: FieldDescription): Kind = when {
    field.cardinality == Cardinality.SERIES -> Kind.NONE
    field.cardinality == Cardinality.KEYED_SERIES -> Kind.NONE
    field is MultilineTextDescription -> Kind.LONG_TEXT
    field is TextDescription -> when {
        field.fixedSet != null -> Kind.CHOICE
        field.suggestedSet != null -> Kind.SUGGESTED
        else -> Kind.TEXT
    }
    field is NumberDescription -> when {
        field.name in OFFSETS -> Kind.OFFSET
        field.dimension == Dimension.TIME -> Kind.CLOCK
        else -> Kind.NUMBER
    }
    field is WholeNumberDescription -> if (field.name == "rating") Kind.RATING else Kind.WHOLE
    field is DateDescription -> Kind.DATE
    field is TimeDescription -> Kind.TIME
    field is BooleanDescription -> Kind.YES_NO
    field is ReferenceDescription -> Kind.REFERENCE
    // Its own entries can be offered in a list; another item's cannot, there being no item
    // chosen to take them from, so those are typed as `@2026-09-20#0*b`.
    field is KeyReferenceDescription ->
        if (field.targetType == null) Kind.KEY else Kind.TEXT
    field is GasDescription -> Kind.GAS
    else -> Kind.NONE
}

/** Whether [field] can be written at all: not one the model works out with no say for a user. */
internal fun editable(field: FieldDescription): Boolean = field.role !is Role.Derived

/** Whether [field] is one the model works out unless told otherwise. */
internal fun overrideable(field: FieldDescription): Boolean = field.role is Role.Overrideable

/**
 * Offered is what a form offers for a field: whether its box is locked, and what it says.
 *
 * Immutable.
 */
internal class Offered(
    /** Whether the box is the model's to fill in rather than the reader's to type into. */
    val locked: Boolean,
    /** What the box says, which for a locked one is the value it shows. */
    val held: Result<Any>,
    /** Whether the logbook holds a correction on this field, which Save would have to clear. */
    val overridden: Boolean,
)

/**
 * What the row for [field] of [item] looks like, given what [draft] has been told.
 *
 * A calculated field is locked and a written one is not, and the form decides that from the draft
 * rather than from the logbook: a correction typed here has not landed yet, and one reverted here
 * has not gone yet.
 *
 * **Reverted, a field locks again and shows the calculation.** The logbook still holds the
 * correction until Save, so reading the item would give the correction back and the box would say
 * the very thing the reader had just dropped. It is the computation that is shown instead.
 * `GUI-29`.
 */
internal fun offeredOf(field: FieldDescription, item: Item, draft: Draft): Offered {
    val stored = item.read(field.name)
    val overridden = stored is Result.Usable && stored.origin == Result.Origin.OVERRIDDEN
    val drafted = draft.changed(item, field.name)
    val reverted = drafted && draft.shownOf(item, field.name) == null
    val written = if (drafted) !reverted else overridden
    val role = field.role
    return Offered(
        locked = !editable(field) || (overrideable(field) && !written),
        held = if (reverted && role is Role.Overrideable) role.compute(item) else stored,
        overridden = overridden,
    )
}

/**
 * A stored value as the text a field is edited as: a time as a clock, a reference as it is
 * written, and the rest as the file holds it.
 */
internal fun textOf(field: FieldDescription, value: Any?): String = when (value) {
    null -> ""
    is Reference.Identified -> "@" + value.id
    is Reference.OneOff -> value.name
    is KeyReference -> "*" + value.key
    is Number -> {
        when (kindOf(field)) {
            Kind.CLOCK -> clockOf(value.toDouble())
            Kind.OFFSET -> offsetOf(value.toDouble())
            else -> numberOf(field, value)
        }
    }
    else -> field.format(value, Units.DEFAULT)
}

/** What a field of [kind] is given from the text typed for it, which for a clock is seconds. */
internal fun givenOf(kind: Kind, text: String): Any? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return when (kind) {
        Kind.CLOCK -> secondsOf(trimmed)?.toString() ?: trimmed
        Kind.OFFSET -> offsetSecondsOf(trimmed)?.toString() ?: trimmed
        else -> trimmed
    }
}

/**
 * Seconds from an offset as typed: `+2:00`, `-3:30`, `5:45`, or a bare number of hours, `2` or
 * `-1.5`; absent where it is none of those.
 */
internal fun offsetSecondsOf(typed: String): Long? {
    val sign = if (typed.startsWith("-")) -1 else 1
    val parts = typed.removePrefix("-").removePrefix("+").split(':')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()?.let { (it * 3600).roundToLong() * sign }
        2 -> {
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            if (minutes !in 0..59) return null
            (hours * 3600 + minutes * 60) * sign
        }
        else -> null
    }
}

/** Seconds from `m:ss`, or from a bare number of minutes; absent where it is neither. */
internal fun secondsOf(clock: String): Long? {
    val sign = if (clock.startsWith("-")) -1 else 1
    val parts = clock.removePrefix("-").split(':')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()?.let { (it * 60).toLong() * sign }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            if (seconds !in 0..59) return null
            (minutes * 60 + seconds) * sign
        }
        else -> null
    }
}

/** The entries of a stored list as the texts they are edited as. */
@Suppress("UNCHECKED_CAST")
internal fun entriesOf(field: FieldDescription, value: Any?): List<String> =
    (value as? List<Element<Any>>).orEmpty().map {
        when (it) {
            is Element.Usable -> textOf(field, it.value)
            is Element.Unusable -> (it.raw as? Stored.Leaf)?.value?.toString().orEmpty()
        }
    }
