package yemoja.ui.api

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.Units

/*
 * An item as an agent is sent it: every field it holds, worked-out values included.
 *
 * See ../../../../../../api/doc.md — `API-4` and `API-5`.
 */

/**
 * [item] as the members an agent reads.
 *
 * Written the way a file writes it, so the agent can read it against `manual/data-format.md`:
 * references as `@id`, a mix as `EAN32`, numbers in the units a file uses by default. Unlike a
 * file, a worked-out value is there, since a question about a dive's SAC is a question about what
 * the application works out.
 *
 * **A series is not sent**, only how many samples it holds. A profile is thousands of them, and
 * `series` fetches one when a question needs it.
 */
internal fun sentOf(item: Item): Stored.Members {
    val members = LinkedHashMap<String, Stored>()
    for (field in item.description.fields) {
        when (val read = item.read(field.name)) {
            Result.Absent -> Unit
            is Result.Unusable -> members[field.name] = unusable(read.raw, read.reason)
            is Result.Usable -> members[field.name] = heldOf(field, read.value)
        }
    }
    return Stored.Members(members)
}

@Suppress("UNCHECKED_CAST")
private fun heldOf(field: FieldDescription, value: Any): Stored =
    when (field.cardinality) {
        Cardinality.SINGLE -> oneOf(field, value)
        Cardinality.LIST -> Stored.Elements(
            (value as List<Element<Any>>).map { elementOf(field, it) },
        )

        Cardinality.KEYED -> Stored.Members(
            (value as Map<String, Element<Any>>).mapValues { elementOf(field, it.value) },
        )

        Cardinality.SERIES -> countOf(value as Series)
        Cardinality.KEYED_SERIES -> Stored.Members(
            (value as Map<String, Element<Series>>).mapValues { (_, held) ->
                when (held) {
                    is Element.Usable -> countOf(held.value)
                    is Element.Unusable -> unusable(held.raw, held.reason)
                }
            },
        )
    }

private fun elementOf(field: FieldDescription, held: Element<Any>): Stored =
    when (held) {
        is Element.Usable -> oneOf(field, held.value)
        is Element.Unusable -> unusable(held.raw, held.reason)
    }

private fun oneOf(field: FieldDescription, value: Any): Stored =
    if (field is OwnedItemDescription) sentOf(value as Item) else leafOf(field, value)

/**
 * One value as a file writes it: a number and a boolean as themselves, anything else as text.
 *
 * Rounded to its unit's figure by `format`, which is what keeps a SAC from arriving with sixteen
 * decimals. `DATA-88`.
 */
internal fun leafOf(field: FieldDescription, value: Any): Stored.Leaf {
    val written = field.format(value, Units.DEFAULT)
    return when (field.valueType) {
        Boolean::class -> Stored.Leaf(value as Boolean)
        Int::class -> Stored.Leaf(written.toLong())
        // A whole one is written whole, `DATA-88`, so the point is what tells them apart.
        Double::class ->
            if ('.' in written) Stored.Leaf(written.toDouble())
            else Stored.Leaf(written.toLong())

        else -> Stored.Leaf(written)
    }
}

private fun countOf(series: Series): Stored =
    Stored.Members(mapOf("samples" to Stored.Leaf(series.size.toLong())))

private fun unusable(raw: Stored, reason: String): Stored =
    Stored.Members(mapOf("unusable" to raw, "reason" to Stored.Leaf(reason)))
