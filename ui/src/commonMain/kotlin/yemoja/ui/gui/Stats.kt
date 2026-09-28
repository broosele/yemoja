package yemoja.ui.gui

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.NumberDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.TextDescription
import yemoja.data.TimeDescription
import yemoja.data.Units
import yemoja.data.WholeNumberDescription
import kotlin.math.roundToInt

/*
 * What a set of items has to say, field by field: the statistic each kind of field gets.
 *
 * The same fields in the same order as one item's view, reading a set instead of one, so a
 * reader who knows where a dive's maximum depth sits knows where twenty dives' deepest sits.
 *
 * See ../../../../../../gui/doc.md — `GUI-23`.
 */

/**
 * Field by field, what [items] say together; a field none of them answers is left out.
 *
 * A number has a range and an average, unless it names rather than measures, as a dive's number
 * does, when the range is all; a date or a time a range; a yes-or-no a count of the yeses; and
 * anything named — a site, a buddy, a word from a list — how many different ones and which, the
 * first few. The count of items answering is added where not all of them did. The name is not
 * a statistic: the list of the items, each a link, says it once already.
 */
internal fun statisticsOf(items: List<Item>): List<Shown> {
    val type = items.firstOrNull()?.description ?: return emptyList()
    return arrangedOf(type).flowing
        .filter { it.name != TITLE }
        .mapNotNull { field -> statisticOf(field, items) }
}

private fun statisticOf(field: FieldDescription, items: List<Item>): Shown? {
    val answers = items.mapNotNull { item ->
        (item.read(field.name) as? Result.Usable)?.let { it.value to it.origin }
    }
    if (answers.isEmpty()) return null
    val values = answers.map { it.first }
    val worked = answers.all { it.second == Result.Origin.DERIVED }
    val parts: List<Part> = when {
        field.cardinality == Cardinality.LIST -> {
            named(field, values.flatMap { listedIn(it) }, items)
        }
        field.cardinality != Cardinality.SINGLE -> return null
        field is NumberDescription || field is WholeNumberDescription -> numbers(field, values)
        field is DateDescription || field is TimeDescription -> range(field, values)
        field is BooleanDescription -> {
            listOf(Part("${values.count { it == true }} of ${values.size}"))
        }
        field is ReferenceDescription || field is TextDescription -> named(field, values, items)
        else -> return null
    }
    val answered = if (answers.size < items.size) {
        listOf(Part(" (${answers.size} of ${items.size})"))
    } else {
        emptyList()
    }
    val rating = if (field.name == RATING) {
        values.mapNotNull { (it as? Number)?.toDouble() }.average().roundToInt()
    } else {
        null
    }
    return Shown(field.label, parts + answered, worked = worked, rating = rating)
}

/** Least to most, and the average. */
private fun numbers(field: FieldDescription, values: List<Any>): List<Part> {
    val numbers = values.mapNotNull { (it as? Number)?.toDouble() }
    if (numbers.isEmpty()) return emptyList()
    // A whole-number field is given whole numbers back where they are whole, since it writes
    // what it is given and would write 2.0 for a dive number.
    fun written(number: Double): String {
        val whole = field is WholeNumberDescription && number == number.toLong().toDouble()
        return numberOf(field, if (whole) number.toLong() else number)
    }
    // The unit once after the range and once after the average, not after every number.
    val unit = unitOf(field).let { if (it.isEmpty()) "" else " $it" }
    val least = written(numbers.min())
    val most = written(numbers.max())
    if (numbers.size == 1) return listOf(Part(least + unit))
    if (field.name in RANGE_ONLY) return listOf(Part("$least – $most$unit"))
    return listOf(Part("$least – $most$unit, $AVERAGE ${written(numbers.average())}$unit"))
}

/** Earliest to latest. */
@Suppress("UNCHECKED_CAST")
private fun range(field: FieldDescription, values: List<Any>): List<Part> {
    val ordered = values.filterIsInstance<Comparable<Any>>().sorted()
    if (ordered.isEmpty()) return emptyList()
    val first = field.format(ordered.first(), Units.DEFAULT)
    val last = field.format(ordered.last(), Units.DEFAULT)
    return if (first == last) listOf(Part(first)) else listOf(Part("$first – $last"))
}

/**
 * Every different one, a reference leading to its item, and how many that is.
 *
 * Counted by what was written, so a name written two ways is two; the model has no say on
 * whether they are one person.
 */
private fun named(field: FieldDescription, values: List<Any>, items: List<Item>): List<Part> {
    val set = items.first().set
    val distinct = LinkedHashMap<String, Part>()
    for (value in values) {
        val part = when (value) {
            is Reference.Identified -> set[value.id]?.let { Part(titleOf(it), leadsTo = value.id) }
                ?: Part(value.toString())
            else -> Part(numberOf(field, value))
        }
        distinct.putIfAbsent(part.text, part)
    }
    if (distinct.isEmpty()) return emptyList()
    val parts = joined(distinct.values.toList())
    return if (distinct.size > 1) parts + Part(" (${distinct.size} different)") else parts
}

/** The items themselves, each a link, under the label of their type, which heads a set's view. */
internal fun listedOf(items: List<Item>): Shown? {
    val type = items.firstOrNull()?.description ?: return null
    val set = items.first().set
    val parts = items.mapNotNull { item ->
        set.idOf(item as? yemoja.data.ReferenceableItem ?: return@mapNotNull null)
            ?.let { Part(titleOf(item), leadsTo = it) }
    }
    return Shown(labelOf(type) + "s", joined(parts))
}

/** The field an item is called by, listed with the items rather than counted. */
private const val TITLE = "name"

/** The one field that is a rating, whose average reads as stars too. */
private const val RATING = "rating"

/** The sign an average is written with, in place of the word. */
private const val AVERAGE = "⌀"

/** Numbers that name rather than measure, whose average would mean nothing: a dive's number. */
private val RANGE_ONLY: Set<String> = setOf("dive_number")

@Suppress("UNCHECKED_CAST")
private fun listedIn(value: Any): List<Any> =
    (value as? List<Element<Any>>).orEmpty().mapNotNull { (it as? Element.Usable)?.value }
