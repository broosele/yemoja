package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Role

/*
 * How often each worked-out field of a logbook gets a value.
 *
 * See ../../../../../../testing.md — the check a real logbook is needed for.
 */

/**
 * Coverage is how one worked-out field fared across a logbook: on how many items it was worked
 * out, written by hand, absent, or held something that would not read.
 *
 * [path] is the type and the way into it, `*` standing for every entry of a keyed collection:
 * `dive.gas_sources.*.sac`.
 *
 * Immutable.
 */
class Coverage(
    val path: String,
    val worked: Int,
    val written: Int,
    val absent: Int,
    val unusable: Int,
) {

    /** How many items the field was asked of. */
    val asked: Int get() = worked + written + absent + unusable
}

/**
 * Every worked-out field of every item in [set], in the order the types and their fields are
 * declared.
 *
 * An owned item nobody wrote is asked all the same, as an empty one, so a dive with no conditions
 * counts towards how often a dive's bottom temperature is absent rather than not at all.
 */
fun coverageOf(set: ItemSet): List<Coverage> {
    val counts = LinkedHashMap<String, IntArray>()
    fun walk(item: Item, under: String) {
        for (field in item.description.fields) {
            val path = "$under.${field.name}"
            if (field.role !is Role.Primary) {
                val slots = counts.getOrPut(path) { IntArray(SLOTS) }
                when (val read = item.read(field.name)) {
                    is Result.Usable -> slots[if (read.origin == Result.Origin.DERIVED) WORKED else WRITTEN]++
                    Result.Absent -> slots[ABSENT]++
                    is Result.Unusable -> slots[UNUSABLE]++
                }
            }
            if (field !is OwnedItemDescription) continue
            when (field.cardinality) {
                Cardinality.SINGLE -> walk(
                    (item.read(field.name) as? Result.Usable)?.value as? OwnedItem
                        ?: OwnedItem(field.description, emptyMap(), item),
                    path,
                )

                Cardinality.KEYED -> ((item.keyed<OwnedItem>(field.name) as? Result.Usable)?.value.orEmpty())
                    .values.mapNotNull { (it as? Element.Usable)?.value }
                    .forEach { walk(it, "$path.*") }

                else -> Unit
            }
        }
    }
    for (description in set.descriptions) {
        for (item in set.allOf(description)) walk(item, description.name)
    }
    return counts.map { (path, slots) ->
        Coverage(path, slots[WORKED], slots[WRITTEN], slots[ABSENT], slots[UNUSABLE])
    }
}

private const val WORKED = 0
private const val WRITTEN = 1
private const val ABSENT = 2
private const val UNUSABLE = 3
private const val SLOTS = 4
