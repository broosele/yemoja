package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.NumberDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.WholeNumberDescription
import kotlin.math.sqrt

/*
 * A figure taken over items somebody else chose: a count, a sum, a mean and the rest.
 *
 * See ../../../../../doc.md — `LOGIC-34`.
 */

/** Measure is which figure is taken over a set of values. */
enum class Measure(
    /** As a caller names it, and as an answer says which it took. */
    val called: String,
) {
    COUNT("count"),
    SUM("sum"),
    MINIMUM("minimum"),
    MAXIMUM("maximum"),

    /** The maximum less the minimum. */
    RANGE("range"),

    /** Every value counting once, however long the dive it came from. */
    MEAN("mean"),

    /** Each value counting as much as the weight found beside it. */
    WEIGHTED_MEAN("weighted mean"),
    MEDIAN("median"),

    /** Of the values as the whole population, not as a sample of a larger one. */
    STANDARD_DEVIATION("standard deviation"),
}

/**
 * Figure is a measure taken over values, and what it was based on.
 *
 * [value] is absent where nothing was left to take it over, and for a weighted mean whose weights
 * add up to nothing. A count is never absent. [skipped] names every place a value was looked for
 * and not used, which is `GUI-9`'s rule for a figure shown on a screen.
 *
 * Immutable.
 */
data class Figure(
    val measure: Measure,
    val value: Double?,
    val used: Int,
    val skipped: List<Skipped>,
)

/**
 * Skipped is one place a value was looked for and not used, and why.
 *
 * [at] is the item as a mention and the path within it: `@2026-06-01#0 gas_sources.g2.sac`.
 */
data class Skipped(val at: String, val reason: String)

/** Figured is what asking for a figure gave: the figure, or why the question cannot be put. */
sealed interface Figured {

    data class Taken(val figure: Figure) : Figured

    /** Refused is a question that names no field a figure can be taken of. */
    data class Refused(val reason: String) : Figured
}

/**
 * [measure] over what [path] reaches on each item [ids] names in [set].
 *
 * A path is written the way the journal addresses a field: `max_depth` on the item,
 * `environment.bottom_temperature` inside a singular owned item, and `gas_sources.g1.sac` inside
 * an entry of a keyed collection. `*` in place of a key reaches every entry. `JSON-14`.
 *
 * **The type is the first item's.** The path is checked against it before anything is read, so a
 * misspelt field is refused rather than answered with every item skipped. An item of another type
 * is skipped with the reason.
 *
 * [weight] is a second path, needed by a weighted mean and ignored by every other measure. A value
 * is weighted by the weight on the same item whose entries are the value's own or fewer, so
 * `gas_sources.*.sac` can be weighted by the dive's `duration` or by each entry's `volume`.
 */
fun figureOf(
    set: ItemSet,
    ids: List<String>,
    path: String,
    measure: Measure,
    weight: String? = null,
): Figured {
    val type = ids.firstNotNullOfOrNull { set[it] }?.description
    if (type != null) {
        refusalOf(type, path, measure != Measure.COUNT)?.let { return Figured.Refused(it) }
    }
    val weighting = if (measure == Measure.WEIGHTED_MEAN) {
        weight ?: return Figured.Refused("a weighted mean should name the field it is weighted by")
    } else {
        null
    }
    if (type != null && weighting != null) {
        refusalOf(type, weighting, numeric = true)?.let { return Figured.Refused(it) }
    }

    val values = ArrayList<Double>()
    val weights = ArrayList<Double>()
    val skipped = ArrayList<Skipped>()
    for (id in ids) {
        val item = set[id]
        if (item == null) {
            skipped += Skipped("@$id", "names nothing")
            continue
        }
        if (item.description !== type) {
            skipped += Skipped(
                "@$id",
                "should be a ${type?.name}, like the first, but was a ${item.description.name}",
            )
            continue
        }
        val found = foundOn(item, id, path, skipped)
        if (weighting == null) {
            for (each in found) values += each.value ?: 0.0
            continue
        }
        val weighed = foundOn(item, id, weighting, ArrayList())
        for (each in found) {
            val matching = weighed.filter { each.keys.take(it.keys.size) == it.keys }
            val beside = matching.singleOrNull()?.value
            when {
                beside == null -> skipped += Skipped(each.at, "has no $weighting to weigh it by")
                beside < 0 -> skipped += Skipped(
                    each.at,
                    "should be weighed by no less than nothing, but $weighting was $beside",
                )
                else -> {
                    values += each.value ?: 0.0
                    weights += beside
                }
            }
        }
    }
    return Figured.Taken(Figure(measure, taken(measure, values, weights), values.size, skipped))
}

/**
 * The field [path] ends at on an item of [type], or absent where it names none.
 *
 * Walks the description alone, so it answers before any item is read.
 */
fun fieldAt(type: ItemDescription, path: String): FieldDescription? {
    val segments = path.split('.')
    var description = type
    var index = 0
    while (index < segments.size) {
        val field = description[segments[index]] ?: return null
        if (field !is OwnedItemDescription) return field.takeIf { index == segments.lastIndex }
        index += if (field.cardinality == Cardinality.SINGLE) 1 else 2
        description = field.description
    }
    return null
}

/** Why no figure can be taken of what [path] reaches on [type], or absent where one can. */
private fun refusalOf(type: ItemDescription, path: String, numeric: Boolean): String? {
    val segments = path.split('.')
    var description = type
    var index = 0
    while (true) {
        val name = segments[index]
        val field = description[name]
            ?: return "${description.name} should have a field called $name, but has none"
        if (field is OwnedItemDescription) {
            index += if (field.cardinality == Cardinality.SINGLE) 1 else 2
            if (index >= segments.size) {
                return "$path should go on into a field of $name, but stops there"
            }
            description = field.description
            continue
        }
        if (index != segments.lastIndex) {
            return "$path should end at $name, which holds no fields, but goes on"
        }
        val series = field.cardinality == Cardinality.SERIES ||
            field.cardinality == Cardinality.KEYED_SERIES
        if (series) return "$name should be one value to an item, but is a series"
        val number = field is NumberDescription || field is WholeNumberDescription
        if (numeric && (!number || field.cardinality != Cardinality.SINGLE)) {
            return "$name should be a single number, but is not"
        }
        return null
    }
}

/**
 * Found is one value reached on an item, and the entries passed on the way.
 *
 * [value] is absent for something counted rather than measured. [keys] is what lets a weight be
 * matched to the value it belongs beside.
 */
private class Found(val keys: List<String>, val value: Double?, val at: String)

/** Every value [path] reaches on [item], which is called [id], noting in [skipped] what is not. */
private fun foundOn(
    item: Item,
    id: String,
    path: String,
    skipped: MutableList<Skipped>,
): List<Found> {
    val found = ArrayList<Found>()
    walk(item, id, path.split('.'), 0, emptyList(), emptyList(), found, skipped)
    return found
}

private fun walk(
    item: Item,
    id: String,
    segments: List<String>,
    index: Int,
    keys: List<String>,
    walked: List<String>,
    found: MutableList<Found>,
    skipped: MutableList<Skipped>,
) {
    val name = segments[index]
    val field = item.description[name] ?: return
    val here = walked + name
    val at = "@$id ${here.joinToString(".")}"
    val read = item.read(name)
    if (read is Result.Absent) {
        skipped += Skipped(at, "holds nothing")
        return
    }
    if (read is Result.Unusable) {
        skipped += Skipped(at, read.reason)
        return
    }
    val value = (read as Result.Usable).value
    if (field !is OwnedItemDescription) {
        if (field.cardinality == Cardinality.LIST) {
            @Suppress("UNCHECKED_CAST")
            for (element in value as List<Element<Any>>) {
                if (element is Element.Usable) found += Found(keys, numberOf(element.value), at)
            }
        } else {
            found += Found(keys, numberOf(value), at)
        }
        return
    }
    if (field.cardinality == Cardinality.SINGLE) {
        walk(value as Item, id, segments, index + 1, keys, here, found, skipped)
        return
    }
    val wanted = segments[index + 1]
    @Suppress("UNCHECKED_CAST")
    val entries: Map<String, Element<Any>> = when (value) {
        is Map<*, *> -> value as Map<String, Element<Any>>
        is List<*> -> (value as List<Element<Any>>)
            .withIndex()
            .associate { "${it.index}" to it.value }

        else -> emptyMap()
    }
    val chosen = if (wanted == EVERY) entries else entries.filterKeys { it == wanted }
    if (chosen.isEmpty() && wanted != EVERY) {
        skipped += Skipped(at, "should have an entry $wanted, but has none")
    }
    for ((key, element) in chosen) {
        val within = here + key
        when (element) {
            is Element.Unusable ->
                skipped += Skipped("@$id ${within.joinToString(".")}", element.reason)

            is Element.Usable -> {
                val entry = element.value as Item
                walk(entry, id, segments, index + 2, keys + key, within, found, skipped)
            }
        }
    }
}

private fun numberOf(value: Any): Double? = when (value) {
    is Double -> value
    is Int -> value.toDouble()
    else -> null
}

/** What stands in for a key to reach every entry. */
private const val EVERY = "*"

private fun taken(measure: Measure, values: List<Double>, weights: List<Double>): Double? {
    if (measure == Measure.COUNT) return values.size.toDouble()
    if (values.isEmpty()) return null
    return when (measure) {
        Measure.COUNT -> values.size.toDouble()
        Measure.SUM -> values.sum()
        Measure.MINIMUM -> values.min()
        Measure.MAXIMUM -> values.max()
        Measure.RANGE -> values.max() - values.min()
        Measure.MEAN -> values.average()
        Measure.WEIGHTED_MEAN -> {
            val total = weights.sum()
            if (total == 0.0) null else values.indices.sumOf { values[it] * weights[it] } / total
        }

        Measure.MEDIAN -> {
            val sorted = values.sorted()
            val middle = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
        }

        Measure.STANDARD_DEVIATION -> {
            val mean = values.average()
            sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        }
    }
}
