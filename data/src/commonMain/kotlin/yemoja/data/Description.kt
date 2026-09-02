package yemoja.data

/*
 * The machinery for describing an item type. What a dive or a person *is* belongs to the
 * logic layer, which builds an item set with these. Nothing here names a field.
 *
 * See ../../../../../doc.md — "How a type is described".
 *
 * Absent until settled: the shape of the typed reads (DATA-51).
 */

/**
 * What a number measures, deciding which `units` entry applies to it.
 *
 * [DIMENSIONLESS] is a member, not an absence: a ratio or a percentage takes no unit and needs
 * none. `DATA-8`'s unit table has no row for it.
 */
enum class Dimension {
    LENGTH, MASS, TIME, TEMPERATURE, VOLUME, PRESSURE, ANGLE, DENSITY, DIMENSIONLESS,
}

/**
 * How many, and how reached.
 *
 * Orthogonal to what a field contains. `DATA-67`.
 *
 * [KEYED] entries are addressable, with keys like `@2026-06-21#0*p1`; a [LIST] has no addressable
 * elements. [SERIES] is indexed by time, always in seconds. [KEYED_SERIES] is one series per key.
 */
enum class Cardinality { SINGLE, LIST, KEYED, SERIES, KEYED_SERIES }

/**
 * Text that does not say what it claims to: `EAN200`, or a date that is not a date.
 *
 * A bad value, not a bad program. A parser this project owns throws this, and nothing else does, so
 * catching it catches only that. An `IllegalArgumentException` from somewhere unrelated stays a
 * fault instead of reaching a user as a misspelt gas.
 */
class ValueFormatException(message: String) : RuntimeException(message)

/**
 * Whether a value is acceptable for the field it is offered to, and why not.
 *
 * Not [Result]. That answers what reading a *stored* value gave, and carries the raw text so an
 * interface can show what is in the file. This answers whether a value belongs in a field at all,
 * about something that may never have been in a file: typed into a form, or about to be written.
 * Nothing raw to keep.
 */
sealed interface Validity {

    object Valid : Validity

    /** [reason] is for a user to read. */
    class Invalid(val reason: String) : Validity
}

/**
 * Recorded, worked out, or worked out and correctable.
 *
 * Orthogonal to the kind of field. A derivation carries its own computation.
 */
sealed interface Role {

    /** Recorded and nothing else. */
    object Primary : Role

    /** Worked out from other values, never stored. */
    class Derived(val compute: (Item) -> Result<Any>) : Role

    /** Derived, but a stored value is present sometimes and corrects it when it is. */
    class Overrideable(val compute: (Item) -> Result<Any>) : Role
}

/**
 * One field of one item type, in enough detail for the parser, the checks and every front end to
 * work from.
 *
 * These describe fields; they hold no values.
 *
 * Three implementations: a value, a reference to another item, and an owned item. How many of each
 * is [Cardinality].
 */
sealed class FieldDescription(
    /** As written in a file. */
    val name: String,
    label: String?,
    val role: Role,
    val cardinality: Cardinality,
) {
    /** Used in the UI. */
    val label: String =
        label ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /**
     * Reads [text] as this field's kind, in the written form `manual/data-format.md` defines.
     *
     * One value at a time: a field holding a list or a series parses its elements one by one, so
     * cardinality is the caller's business.
     *
     * [overrides] says the text is a stored value on a field that would otherwise work one out,
     * which decides the origin. It is given, not defaulted. Whoever reads knows which case it is,
     * and the wrong answer here is a correct-looking one.
     *
     * A kind with no written form, such as an owned item, answers unusable.
     */
    open fun parse(text: String, overrides: Boolean): Result<Any> =
        Result.Unusable(text, "$name is not written as text")

    /** The written form of [value], the inverse of [parse]. */
    open fun format(value: Any): String = value.toString()

    /**
     * [value], read from [text], as a result.
     *
     * Runs [validate] on the way, so that what a field refuses when a user types it is
     * exactly what it refuses when a file holds it.
     */
    protected fun accepted(value: Any, text: String, overrides: Boolean): Result<Any> =
        when (val validity = validate(value)) {
            is Validity.Valid -> Result.Usable(value, originOf(overrides))
            is Validity.Invalid -> Result.Unusable(text, validity.reason)
        }

    /** The origin a parsed value carries, by whether it corrects a derivation. */
    protected fun originOf(overrides: Boolean) =
        if (overrides) Result.Origin.OVERRIDDEN else Result.Origin.STORED

    /**
     * Whether [value] belongs in this field.
     *
     * Answers for a value on its own, so it cannot judge a reference. Whether one resolves needs
     * the items, and is asked where they are. `DATA-66`.
     *
     * The parameter is `Any` because an override may not narrow it. A caller holding a description
     * already knows what kind of value it takes.
     */
    open fun validate(value: Any): Validity = Validity.Valid

    /** For test failures. */
    override fun toString() =
        "${this::class.simpleName}($name, ${role::class.simpleName}, $cardinality)"
}

/**
 * A value, rather than a reference or an owned item.
 *
 * The kind is the type, so a property belongs only to the kinds it applies to.
 *
 * A field with a default is [Role.Overrideable] with a constant computation. Nothing written and it
 * works out the same answer every time; something written and that wins. There is no separate
 * default. `DATA-56`.
 */
sealed class ValueDescription(
    name: String,
    label: String? = null,
    role: Role,
    cardinality: Cardinality,
) : FieldDescription(name, label, role, cardinality)

/** A measurement. */
class NumberDescription(
    name: String,
    val dimension: Dimension,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Closed at both ends, where given: a latitude is `-90.0..90.0`. */
    val range: ClosedRange<Double>? = null,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> {
        val number = text.trim().toDoubleOrNull()
            ?: return Result.Unusable(text, "$name is a number")
        return accepted(number, text, overrides)
    }

    override fun validate(value: Any) = when {
        value !is Double -> Validity.Invalid("$name is a number")
        range != null && value !in range -> Validity.Invalid("$name runs $range")
        else -> Validity.Valid
    }
}

/**
 * A count, never a measurement.
 *
 * No dimension, and no `units` declaration reaches it. `DATA-68`.
 */
class WholeNumberDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Whole bounds, where given: a rating is `1..10`, not `1.0..10.0`. */
    val range: IntRange? = null,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> {
        val number = text.trim().toIntOrNull()
            ?: return Result.Unusable(text, "$name is a whole number")
        return accepted(number, text, overrides)
    }

    override fun validate(value: Any) = when {
        value !is Int -> Validity.Invalid("$name is a whole number")
        range != null && value !in range -> Validity.Invalid("$name runs $range")
        else -> Validity.Valid
    }
}

/** One line: no line breaks, no tabs, and no leading `@` or `*`. */
class TextDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Closed: a value outside reads back *unusable*, kept as written. `DATA-24`. */
    val fixedSet: Set<String>? = null,
    /** Offered, not enforced: a value outside is an ordinary value. */
    val suggested: Set<String>? = null,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean) = accepted(text, text, overrides)

    override fun validate(value: Any) = when {
        value !is String -> Validity.Invalid("$name should be text")
        value.any { it == '\n' || it == '\r' || it == '\t' } ->
            Validity.Invalid("$name should be a single line of text")

        value.startsWith('@') || value.startsWith('*') ->
            Validity.Invalid("$name may not begin with @ or *, which name other things")

        fixedSet != null && value !in fixedSet ->
            Validity.Invalid("$name should be one of ${fixedSet.sorted().joinToString(", ")}")

        else -> Validity.Valid
    }
}

/**
 * Prose: line breaks allowed, and a leading `@` or `*`.
 *
 * Carries no vocabulary, closed or offered. `remarks` is nearly the only one in the model.
 */
class MultilineTextDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean) = accepted(text, text, overrides)

    override fun validate(value: Any) = when {
        value !is String -> Validity.Invalid("$name should be text")
        value.contains('\t') -> Validity.Invalid("$name may not contain a tab")
        else -> Validity.Valid
    }
}

/** Always `"2026-02-23"`; no `units` setting reaches it. */
class DateDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            accepted(Date.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name is a date: ${refused.message}")
        }

    override fun validate(value: Any) =
        if (value is Date) Validity.Valid else Validity.Invalid("$name is a date")
}

/** Always `"09:15:00"`. */
class TimeDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            accepted(Time.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name is a time: ${refused.message}")
        }

    override fun validate(value: Any) =
        if (value is Time) Validity.Valid else Validity.Invalid("$name is a time")
}

/** `true` or `false`. */
class BooleanDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> = when (text) {
        "true" -> accepted(true, text, overrides)
        "false" -> accepted(false, text, overrides)
        else -> Result.Unusable(text, "$name is written true or false, and nothing else")
    }

    override fun validate(value: Any) =
        if (value is Boolean) Validity.Valid else Validity.Invalid("$name should be true or false")
}

/** A mix as divers write it — `AIR`, `EAN32`, `TMX18/35` — parsed for its fractions. */
class GasDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            accepted(Gas.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should be a valid gas mix: ${refused.message}")
        }

    override fun validate(value: Any) =
        if (value is Gas) Validity.Valid else Validity.Invalid("$name is a mix")
}

/** An entry inside the same item, written `*p1`. */
class KeyReferenceDescription(
    name: String,
    /** The field holding the collection pointed into. */
    val collection: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            accepted(KeyReference.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name names an entry of $collection: ${refused.message}")
        }

    /** Syntax only. Whether the key exists is asked of the collection, not of a value. */
    override fun validate(value: Any) =
        if (value is KeyReference) Validity.Valid
        else Validity.Invalid("$name should name an entry of $collection")
}

/**
 * Another item, written `@id`.
 *
 * [targetType] is what is expected there, not what is guaranteed: the target may be the wrong type,
 * or not loaded.
 */
class ReferenceDescription(
    name: String,
    val targetType: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** A plain name may stand in, asserting no id. */
    val oneOff: Boolean = false,
) : FieldDescription(name, label, role, cardinality) {

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            accepted(Reference.parse(text, oneOff), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name names another item: ${refused.message}")
        }

    /**
     * Syntax only.
     *
     * Whether the reference resolves is not part of its validity: `@john` names a person
     * whether or not that person is in the logbook yet. `DATA-66`.
     */
    override fun validate(value: Any) = when {
        value !is Reference -> Validity.Invalid("$name should name another item")
        value is Reference.OneOff && !oneOff ->
            Validity.Invalid("$name should be a reference, written with a leading @")
        else -> Validity.Valid
    }
}

/**
 * Fields kept inside their owner and read with it.
 *
 * [Cardinality.SINGLE] is one, a dive's environment; [Cardinality.KEYED] is several under keys, a
 * dive's profiles.
 */
class OwnedItemDescription(
    name: String,
    val description: ItemDescription,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : FieldDescription(name, label, role, cardinality)

/** One item type: its name, and its fields in the order an interface offers them. */
class ItemDescription(
    val name: String,
    val fields: List<FieldDescription>,
) {
    /** Built on first use. Every read is by name, and a scan per read is the wrong shape. */
    val byName: Map<String, FieldDescription> by lazy { fields.associateBy { it.name } }

    operator fun get(name: String): FieldDescription? = byName[name]
}
