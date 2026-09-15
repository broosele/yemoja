package yemoja.data

/*
 * An item back into the neutral form a source writes: the inverse of ItemReader.
 *
 * See ../../../../../doc.md — the layer's own document is data/doc.md.
 */

/**
 * ItemWriter turns an item back into [Stored], in the units a file is written in.
 *
 * The inverse of [ItemReader], and it holds the same division: nothing here knows what JSON is,
 * so a second source can put back what this hands over. `DATA-86`.
 *
 * **Only what was stored is written.** [Item.fields] holds no derived value and no absent one, so
 * a field the application works out never reaches a file and never has to be recognised there as
 * something to ignore. A corrected one is stored and is written like any other.
 *
 * **A value that would not read is written back as it came.** [Result.Unusable] carries the raw it
 * was made from, so a file holding `"max_depth": "deep"` still holds it after a save. Losing it
 * would be the application quietly deciding that what it cannot use is not worth keeping.
 *
 * **A name this version does not recognise survives too**, from [Item.unrecognisedFields], though
 * it moves to the end of the item: the position a name held in a file is not kept anywhere.
 * `DATA-65`.
 */
object ItemWriter {

    /** [item] as the members a source writes, in [units]. */
    fun write(item: Item, units: Units): Stored.Members {
        val members = LinkedHashMap<String, Stored>()
        for ((name, read) in item.fields) {
            val field = item.description[name] ?: continue
            storedOf(field, read, units)?.let { members[name] = it }
        }
        // After the recognised ones, since where they sat is not something a reader kept.
        for ((name, held) in item.unrecognisedFields) members[name] = held
        return Stored.Members(members)
    }

    private fun storedOf(field: FieldDescription, read: Result<Any>, units: Units): Stored? =
        when (read) {
            is Result.Unusable -> read.raw
            Result.Absent -> null
            is Result.Usable -> heldOf(field, read.value, units).takeUnless { emptily(field, it) }
        }

    /**
     * Whether [held] is a singular owned item with nothing in it, which is not written.
     *
     * **An empty block says exactly what no block says.** A dive whose `environment` holds no
     * reading is a dive nobody wrote the conditions of, and `"environment": {}` in the file is
     * that same fact spelled at length. Writing it would also make an interface that offers the
     * fields of a block before anything is typed into them leave a trail of empty ones.
     *
     * Only the singular: a keyed collection with no entries is a list somebody may have emptied
     * on purpose, and telling that from one nobody made is not this function's to guess.
     * `DATA-116`.
     */
    private fun emptily(field: FieldDescription, held: Stored): Boolean =
        field is OwnedItemDescription &&
            field.cardinality == Cardinality.SINGLE &&
            held is Stored.Members &&
            held.members.isEmpty()

    /** What one field holds, by the shape its cardinality gives it. */
    @Suppress("UNCHECKED_CAST")
    private fun heldOf(field: FieldDescription, value: Any, units: Units): Stored =
        when (field.cardinality) {
            Cardinality.SINGLE -> oneOf(field, value, units)
            Cardinality.LIST -> Stored.Elements(
                (value as List<Element<Any>>).map { elementOf(field, it, units) },
            )

            Cardinality.KEYED -> Stored.Members(
                (value as Map<String, Element<Any>>)
                    .mapValues { (_, held) -> elementOf(field, held, units) },
            )

            Cardinality.SERIES -> samplesOf(field, value as Series, units)
            Cardinality.KEYED_SERIES -> Stored.Members(
                (value as Map<String, Element<Series>>).mapValues { (_, held) ->
                    when (held) {
                        is Element.Usable -> samplesOf(field, held.value, units)
                        is Element.Unusable -> held.raw
                    }
                },
            )
        }

    private fun elementOf(field: FieldDescription, held: Element<Any>, units: Units): Stored =
        when (held) {
            is Element.Usable -> oneOf(field, held.value, units)
            is Element.Unusable -> held.raw
        }

    /**
     * A series as the pairs a file holds: the second it was taken at, and what was read.
     *
     * A time in a series is seconds from the start of the recording whatever the file declares,
     * so it is written as it is held. `DATA-58`.
     */
    private fun samplesOf(field: FieldDescription, series: Series, units: Units): Stored =
        Stored.Elements(
            (0..<series.size).map { at ->
                Stored.Elements(
                    listOf(
                        Stored.Leaf(series.secondAt(at).toLong()),
                        elementOf(field, series.valueAt(at), units),
                    ),
                )
            },
        )

    /**
     * One value as a source holds it.
     *
     * An owned item is a set of fields and goes round again. Everything else is written by its
     * field, which is what puts a number in the file's units and to its precision — `DATA-88` —
     * and what makes `format` and `read` the round trip `DATA-76` relies on.
     *
     * The *type* it lands as is the field's rather than the text's: a number is a number in the
     * file and not a quoted one, and so is a boolean. Anything else is text, whatever it holds.
     */
    private fun oneOf(field: FieldDescription, value: Any, units: Units): Stored {
        if (value is Item) return write(value, units)
        val written = field.format(value, units)
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
}
