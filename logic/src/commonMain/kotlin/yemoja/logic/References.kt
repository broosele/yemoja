package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result

/*
 * The far side of a reference, gathered by asking every item of a type.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * Naming is a field that may name an item, and the owned items it is written on where it is not
 * written on the item itself.
 *
 * A dive's `operator` sits on its `details`, which is singular, and a profile's `dive_computer`
 * sits on each of a dive's `profiles`, which are keyed. Either way [inside] names the field
 * holding them and every owned item in it is asked.
 */
internal class Naming(val field: String, val inside: String? = null)

/**
 * Every item of [type] naming [item] in any of [namings], as references to them.
 *
 * One walk for both sides of a back-reference, whether the naming field holds one or several: a
 * region has many `parents` and a trip has one `parent`, and each is asked the same question. An
 * entry that would not read names nothing and is passed over. An item naming [item] in two ways
 * is listed once, in the order [type] is held.
 */
internal fun pointingAt(item: Item, type: ItemDescription, vararg namings: Naming): Result<Any> {
    val found = itemsPointingAt(item, type, *namings) ?: return Result.Absent
    return referencesTo(item.set, found)
}

/**
 * Every dive naming [item] in any of [namings], leaving out the ones nobody has made yet.
 *
 * A list of dives is a list of diving done, so a plan does not put its dive in the site's list or
 * in a buddy's. It is shown on its own dive and counted nowhere else. `LOGIC-36`.
 */
internal fun divesPointingAt(item: Item, vararg namings: Naming): Result<Any> {
    val found = itemsPointingAt(item, Types.DIVE, *namings) ?: return Result.Absent
    return referencesTo(item.set, found.filter(::wasMade))
}

/** The items themselves, or null where [item] is not one anything could name. */
private fun itemsPointingAt(
    item: Item,
    type: ItemDescription,
    vararg namings: Naming,
): List<ReferenceableItem>? {
    val id = (item as? ReferenceableItem)?.let { item.set.idOf(it) } ?: return null
    return item.set.allOf(type).filter { other ->
        namings.any { naming ->
            within(other, naming.inside).any { owned -> id in namesIn(owned, naming.field) }
        }
    }
}

/** [items] as a derived list of references to them. */
internal fun referencesTo(set: ItemSet, items: List<ReferenceableItem>): Result<Any> {
    val found = items
        .mapNotNull { set.idOf(it) }
        .map { Element.Usable(Reference.Identified(it) as Any) }
    return Result.Usable(found, Result.Origin.DERIVED)
}

/** [item] itself, or the items it owns under [inside]; none where it owns none there. */
private fun within(item: Item, inside: String?): List<Item> {
    if (inside == null) return listOf(item)
    return when (val owned = (item.read(inside) as? Result.Usable)?.value) {
        is OwnedItem -> listOf(owned)
        is Map<*, *> -> owned.values.mapNotNull { (it as? Element.Usable<*>)?.value as? OwnedItem }
        else -> emptyList()
    }
}

/** The ids [field] names on [item], however many it holds and whatever it failed to read. */
internal fun namesIn(item: Item, field: String): List<String> {
    val naming = item.description[field] ?: return emptyList()
    val read = item.read(field) as? Result.Usable ?: return emptyList()
    val values = if (naming.cardinality == Cardinality.LIST) {
        (read.value as? List<*>).orEmpty().mapNotNull { (it as? Element.Usable<*>)?.value }
    } else {
        listOf(read.value)
    }
    return values.filterIsInstance<Reference.Identified>().map { it.id }
}
