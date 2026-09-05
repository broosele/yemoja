package yemoja.data

/**
 * ItemReader builds an item out of what a source held.
 *
 * The walk from a description to an item's fields, written once because the rules it applies are
 * the model's rather than any source's. A source produces a [Stored] tree and this reads it, so a
 * second source implements nothing of this. `DATA-64`.
 *
 * **Shape here, kind there.** This decides that a leaf belongs where a single value is described
 * and a group does not, then hands the leaf's value to the description, which decides whether it
 * is a date. Nothing here knows what a date is and nothing in a description knows what a list is.
 *
 * **A bad piece stops at its own level.** A member that will not read is an unusable member and
 * its neighbours are untouched; a field whose shape is wrong is an unusable field. Either way what
 * was there is kept. `DATA-77`.
 */
object ItemReader {

    /**
     * The item [stored] describes, of the type [description] names.
     *
     * Fields the description does not name are kept apart and untouched. `DATA-65`.
     */
    fun read(
        description: ItemDescription,
        stored: Stored.Members,
        set: ItemSet,
        units: Units,
    ): ReferenceableItem = ReferenceableItem(
        description,
        { item -> fieldsOf(description, stored, item, units) },
        set,
        unrecognisedOf(description, stored),
    )

    /** An owned item, built against the owner that is asking for it. `DATA-85`. */
    private fun owned(
        description: ItemDescription,
        stored: Stored.Members,
        parent: Item,
        units: Units,
    ): OwnedItem = OwnedItem(
        description,
        { item -> fieldsOf(description, stored, item, units) },
        parent,
        unrecognisedOf(description, stored),
    )

    private fun unrecognisedOf(
        description: ItemDescription,
        stored: Stored.Members,
    ): Map<String, Stored> = stored.members.filterKeys { description[it] == null }

    private fun fieldsOf(
        description: ItemDescription,
        stored: Stored.Members,
        item: Item,
        units: Units,
    ): Map<String, Result<Any>> {
        val fields = LinkedHashMap<String, Result<Any>>()
        for ((name, held) in stored.members) {
            val field = description[name] ?: continue
            fields[name] = fieldOf(field, held, item, units)
        }
        return fields
    }

    /**
     * What one field holds, read from [held] against [field].
     *
     * Reachable so that [Item.write] can put a value in by the same door a file comes through.
     * An owned item is built here and needs [parent] to build it, `DATA-85`, which is the one
     * thing a description cannot do on its own.
     */
    internal fun fieldOf(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        units: Units,
    ): Result<Any> {
        val origin =
            if (field.role is Role.Overrideable) Result.Origin.OVERRIDDEN else Result.Origin.STORED
        return when (field.cardinality) {
            Cardinality.SINGLE -> single(field, held, parent, origin, units)
            Cardinality.LIST -> list(field, held, parent, origin, units)
            Cardinality.KEYED -> keyed(field, held, parent, origin, units)
            Cardinality.SERIES -> series(field, held, parent, origin, units)
            Cardinality.KEYED_SERIES -> keyedSeries(field, held, parent, origin, units)
        }
    }

    private fun single(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        origin: Result.Origin,
        units: Units,
    ): Result<Any> = when (val member = memberOf(field, held, parent, units)) {
        is Element.Usable -> Result.Usable(member.value, origin)
        is Element.Unusable -> Result.Unusable(member.raw, member.reason)
    }

    /**
     * A list, and a single value read as a list of one.
     *
     * The forgiveness is for a leaf only. A group where a list belongs is a shape nobody meant to
     * write, while a bare name where a list of names belongs is an edit somebody makes. `DATA-77`.
     */
    private fun list(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        origin: Result.Origin,
        units: Units,
    ): Result<Any> = when (held) {
        is Stored.Elements ->
            Result.Usable(held.elements.map { memberOf(field, it, parent, units) }, origin)

        is Stored.Leaf -> Result.Usable(listOf(memberOf(field, held, parent, units)), origin)
        is Stored.Members -> Result.Unusable(held, "${field.name} should be a list")
    }

    private fun keyed(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        origin: Result.Origin,
        units: Units,
    ): Result<Any> =
        if (held is Stored.Members) {
            Result.Usable(
                held.members.mapValues { memberOf(field, it.value, parent, units) },
                origin,
            )
        } else {
            Result.Unusable(held, "${field.name} should be entries under keys")
        }

    /**
     * A series, out of pairs of a time and a value.
     *
     * A sample that is not a time and a value makes the **field** unusable rather than the sample,
     * because a value with no time has nowhere to sit and dropping it would quietly redraw the
     * curve. `DATA-58` settles that a series cannot say something is missing. A value that will
     * not read is an unusable sample in its place, which the series carries.
     */
    private fun series(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        origin: Result.Origin,
        units: Units,
    ): Result<Any> {
        if (held !is Stored.Elements) {
            return Result.Unusable(held, "${field.name} should be a series of times and values")
        }
        val seconds = IntArray(held.elements.size)
        val values = ArrayList<Element<Any>>(held.elements.size)
        for ((index, sample) in held.elements.withIndex()) {
            if (sample !is Stored.Elements || sample.elements.size != 2) {
                return Result.Unusable(
                    held,
                    "${field.name} should hold a time and a value at each sample",
                )
            }
            seconds[index] = secondOf(sample.elements[0])
                ?: return Result.Unusable(held, "${field.name} should be timed in whole seconds")
            values.add(memberOf(field, sample.elements[1], parent, units))
        }
        return try {
            Result.Usable(Series(seconds, values), origin)
        } catch (refused: IllegalArgumentException) {
            // Out of order, which the constructor guards. From a file it is a bad value.
            Result.Unusable(held, "${field.name}: ${refused.message}")
        }
    }

    private fun keyedSeries(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        origin: Result.Origin,
        units: Units,
    ): Result<Any> =
        if (held is Stored.Members) {
            Result.Usable(
                held.members.mapValues { seriesMemberOf(field, it.value, parent, units) },
                origin,
            )
        } else {
            Result.Unusable(held, "${field.name} should be a series under each key")
        }

    private fun seriesMemberOf(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        units: Units,
    ): Element<Series> =
        when (val read = series(field, held, parent, Result.Origin.STORED, units)) {
        is Result.Usable -> Element.Usable(read.value as Series)
        is Result.Unusable -> Element.Unusable(read.raw, read.reason)
        Result.Absent -> Element.Unusable(held, "${field.name} should be a series")
    }

    /**
     * One piece read as the field's kind: an owned item where that is what the field holds, and
     * otherwise a leaf handed to the description.
     */
    private fun memberOf(
        field: FieldDescription,
        held: Stored,
        parent: Item,
        units: Units,
    ): Element<Any> =
        if (field is OwnedItemDescription) {
            if (held is Stored.Members) {
                Element.Usable(owned(field.description, held, parent, units))
            }
            else Element.Unusable(held, "${field.name} should be a set of fields")
        } else if (held is Stored.Leaf) {
            // The origin belongs to the field, not to one of its pieces, so it is settled above.
            when (val read = field.read(held.value, false, units)) {
                is Result.Usable -> Element.Usable(read.value)
                is Result.Unusable -> Element.Unusable(read.raw, read.reason)
                Result.Absent -> Element.Unusable(held, "${field.name} should be a value")
            }
        } else {
            Element.Unusable(held, "${field.name} should be one value")
        }

    /** The time of a sample: a whole number of seconds, and small enough to be one. */
    private fun secondOf(held: Stored): Int? {
        val value = (held as? Stored.Leaf)?.value
        return when (value) {
            is Int -> value
            is Long -> if (value.toInt().toLong() == value) value.toInt() else null
            else -> null
        }
    }
}
