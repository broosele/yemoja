package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
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
 * Every item of [type] whose [field] names [item], as references to them.
 *
 * One walk for both sides of a back-reference, whether the naming field holds one or several: a
 * region has many `parents` and a trip has one `parent`, and each is asked the same question. An
 * entry that would not read names nothing and is passed over. Where the naming field sits on an
 * item the other owns, a dive's `operator` on its `details`, [inside] names the owned item.
 */
internal fun pointingAt(
    item: Item,
    type: ItemDescription,
    field: String,
    inside: String? = null,
): Result<Any> {
    val id = (item as? ReferenceableItem)?.let { item.set.idOf(it) } ?: return Result.Absent
    val found = item.set.allOf(type)
        .filter { other -> id in namesIn(within(other, inside) ?: return@filter false, field) }
        .mapNotNull { other -> item.set.idOf(other) }
        .map { Element.Usable(Reference.Identified(it) as Any) }
    return Result.Usable(found, Result.Origin.DERIVED)
}

/** [item] itself, or the item it owns under [inside]; absent where it owns none there. */
private fun within(item: Item, inside: String?): Item? {
    if (inside == null) return item
    return (item.single<OwnedItem>(inside) as? Result.Usable)?.value
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
