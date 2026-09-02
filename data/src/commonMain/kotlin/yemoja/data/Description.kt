package yemoja.data

import kotlin.reflect.KClass

/*
 * The machinery for describing an item type. What a dive or a person *is* belongs to the
 * logic layer, which builds an item set with these. Nothing here names a field.
 *
 * See ../../../../../doc.md — "How a type is described".
 */

/**
 * Dimension is what a number measures, and it decides which `units` entry applies to it.
 *
 * [DIMENSIONLESS] is a member, not an absence: a ratio or a percentage takes no unit and needs
 * none. `DATA-8`'s unit table has no row for it.
 */
enum class Dimension {
    LENGTH, MASS, TIME, TEMPERATURE, VOLUME, PRESSURE, ANGLE, DENSITY, DIMENSIONLESS,
}

/**
 * Cardinality is how many values a field holds, and how each of them is reached.
 *
 * Any kind of value may have any of them: a number can be one, a list, or a series through a
 * dive. `DATA-67`.
 *
 * [KEYED] entries are addressable, with keys like `@2026-06-21#0*p1`; a [LIST] has no addressable
 * elements. [SERIES] is indexed by time, always in seconds. [KEYED_SERIES] is one series per key.
 */
enum class Cardinality { SINGLE, LIST, KEYED, SERIES, KEYED_SERIES }

/**
 * Validity is whether a value is acceptable for the field it is offered to, and why not.
 *
 * Not [Result]. That answers what reading a *stored* value gave, and carries the raw text so an
 * interface can show what is in the file. This answers whether a value belongs in a field at all,
 * about something that may never have been in a file: typed into a form, or about to be written.
 * So there is nothing raw to keep.
 *
 * Immutable.
 */
sealed interface Validity {

    object Valid : Validity

    /** Invalid is a refusal, carrying the [reason] for a user to read. */
    data class Invalid(val reason: String) : Validity
}

/**
 * Role is whether a field is recorded, worked out, or worked out and correctable.
 *
 * Any kind of field may have any of them, and one that is worked out carries its own
 * computation.
 */
sealed interface Role {

    /** Primary is recorded and nothing else. */
    object Primary : Role

    /** Derived is worked out from other values and never stored. */
    class Derived(val compute: (Item) -> Result<Any>) : Role

    /** Overrideable is derived, except that a stored value corrects it where there is one. */
    class Overrideable(val compute: (Item) -> Result<Any>) : Role
}

/**
 * FieldDescription is one field of one item type, in enough detail for the parser, the checks
 * and every front end to work from.
 *
 * These describe fields; they hold no values.
 *
 * Three implementations: a value, a reference to another item, and an owned item. How many of each
 * is [Cardinality].
 *
 * Immutable, as is every description below it. One is built once and shared by everything that
 * reads that field.
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
     */
    abstract fun parse(text: String, overrides: Boolean): Result<Any>

    /** The written form of [value], the inverse of [parse]. */
    open fun format(value: Any): String = value.toString()

    /**
     * The result of reading [text] as [value], which is what every kind's [parse] ends with.
     *
     * Runs [validate] on the way, so that what a field refuses when a user types it is exactly
     * what it refuses when a file holds it.
     */
    protected fun resultOf(value: Any, text: String, overrides: Boolean): Result<Any> {
        val origin = if (overrides) Result.Origin.OVERRIDDEN else Result.Origin.STORED
        return when (val validity = validate(value)) {
            is Validity.Valid -> Result.Usable(value, origin)
            is Validity.Invalid -> Result.Unusable(text, validity.reason)
        }
    }

    /**
     * The type one value of this field has once it is read.
     *
     * What a typed read is checked against, so asking for the wrong kind is a fault whether or not
     * the field has anything stored. Cardinality is separate: a list of numbers says [Double] here
     * and says how many in [cardinality]. `DATA-51`.
     */
    abstract val valueType: KClass<*>

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
    override fun toString(): String =
        "${this::class.simpleName}($name, ${role::class.simpleName}, $cardinality)"
}

/**
 * ValueDescription is a field holding a value, rather than a reference or an owned item.
 *
 * Each kind is a class of its own, so a property belongs only to the kinds it applies to: a range
 * to numbers, a fixed vocabulary to text. A boolean can be given neither.
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

/** NumberDescription is a field holding a measurement. */
class NumberDescription(
    name: String,
    val dimension: Dimension,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Closed at both ends, where given: a latitude is `-90.0..90.0`. */
    val range: ClosedRange<Double>? = null,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Double::class

    override fun parse(text: String, overrides: Boolean): Result<Any> {
        val number = text.trim().toDoubleOrNull()
            ?: return Result.Unusable(text, "$name should be a number")
        return resultOf(number, text, overrides)
    }

    override fun validate(value: Any): Validity = when {
        value !is Double -> Validity.Invalid("$name should be a number")
        range != null && value !in range -> Validity.Invalid("$name should be within $range")
        else -> Validity.Valid
    }
}

/**
 * WholeNumberDescription is a field holding a count, never a measurement.
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

    override val valueType: KClass<*> get() = Int::class

    override fun parse(text: String, overrides: Boolean): Result<Any> {
        val number = text.trim().toIntOrNull()
            ?: return Result.Unusable(text, "$name should be a whole number")
        return resultOf(number, text, overrides)
    }

    override fun validate(value: Any): Validity = when {
        value !is Int -> Validity.Invalid("$name should be a whole number")
        range != null && value !in range -> Validity.Invalid("$name should be within $range")
        else -> Validity.Valid
    }
}

/**
 * Characters that make stored text read as something other than what is stored.
 *
 * The first two rows reorder what is shown, so a name could appear as something the file does not
 * say. The third is invisible and joins nothing, so two names can look identical and be different,
 * which matters because an id is proposed from a name.
 *
 * The zero-width joiner and non-joiner are deliberately absent: emoji sequences and Persian and
 * Indic text need them.
 */
private val MISLEADING = setOf(
    '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
    '\u2066', '\u2067', '\u2068', '\u2069',
    '\u200B', '\u2060', '\uFEFF',
)

/** Whether this ends a line, in any of the four ways one can be written. */
private fun Char.breaksLine(): Boolean =
    this == '\n' || this == '\r' ||
        category == CharCategory.LINE_SEPARATOR ||
        category == CharCategory.PARAGRAPH_SEPARATOR

/** Whether this would not be seen, or would not be seen for what it is. */
private fun Char.isHidden(): Boolean =
    category == CharCategory.CONTROL ||
        category == CharCategory.LINE_SEPARATOR ||
        category == CharCategory.PARAGRAPH_SEPARATOR ||
        this in MISLEADING

/**
 * TextDescription is a field holding one line of text: no line breaks, no tabs, and no
 * leading `@` or `*`.
 */
class TextDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    fixedSet: Set<String>? = null,
    suggested: Set<String>? = null,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = String::class

    // Copied. A Set is read-only, not immutable.
    /** Closed: a value outside reads back *unusable*, kept as written. `DATA-24`. */
    val fixedSet: Set<String>? = fixedSet?.toSet()

    // Copied. A Set is read-only, not immutable.
    /**
     * Offered, not enforced: a value outside is an ordinary value.
     *
     * The presets the application ships. A user is offered these joined with what is already
     * in use, which happens above this layer, so the set itself never grows. `DATA-25`.
     */
    val suggested: Set<String>? = suggested?.toSet()

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        resultOf(text, text, overrides)

    override fun validate(value: Any): Validity = when {
        value !is String -> Validity.Invalid("$name should be text")
        value.any { it.breaksLine() } ->
            Validity.Invalid("$name should be a single line of text")

        value.any { it.isHidden() } ->
            Validity.Invalid("$name should not contain characters that cannot be seen")

        value.startsWith('@') || value.startsWith('*') ->
            Validity.Invalid(
                "$name should not begin with @ or *, which are references to other items"
            )

        fixedSet != null && value !in fixedSet ->
            Validity.Invalid("$name should be one of ${fixedSet.sorted().joinToString(", ")}")

        else -> Validity.Valid
    }
}

/**
 * MultilineTextDescription is a field holding prose: line breaks are allowed, and so is a
 * leading `@` or `*`.
 *
 * Carries no vocabulary, closed or offered. `remarks` is nearly the only one in the model.
 */
class MultilineTextDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = String::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        resultOf(text, text, overrides)

    override fun validate(value: Any): Validity = when {
        value !is String -> Validity.Invalid("$name should be text")
        value.any { it != '\n' && it.isHidden() } ->
            Validity.Invalid("$name should not contain characters that cannot be seen")
        else -> Validity.Valid
    }
}

/**
 * DateDescription is a field holding a date, always written `"2026-02-23"`. No `units`
 * setting reaches it.
 */
class DateDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Date::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            resultOf(Date.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should be a date: ${refused.message}")
        }

    override fun validate(value: Any): Validity =
        if (value is Date) Validity.Valid else Validity.Invalid("$name should be a date")
}

/** TimeDescription is a field holding a time of day, always written `"09:15:00"`. */
class TimeDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Time::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            resultOf(Time.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should be a time: ${refused.message}")
        }

    override fun validate(value: Any): Validity =
        if (value is Time) Validity.Valid else Validity.Invalid("$name should be a time")
}

/** BooleanDescription is a field holding `true` or `false`. */
class BooleanDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Boolean::class

    override fun parse(text: String, overrides: Boolean): Result<Any> = when (text) {
        "true" -> resultOf(true, text, overrides)
        "false" -> resultOf(false, text, overrides)
        else -> Result.Unusable(text, "$name should be true or false")
    }

    override fun validate(value: Any): Validity =
        if (value is Boolean) Validity.Valid else Validity.Invalid("$name should be true or false")
}

/**
 * GasDescription is a field holding a mix as divers write it, parsed for its fractions.
 *
 * Examples include `AIR`, `EAN32` and `TMX18/35`.
 */
class GasDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Gas::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            resultOf(Gas.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should be a gas mix: ${refused.message}")
        }

    override fun validate(value: Any): Validity =
        if (value is Gas) Validity.Valid else Validity.Invalid("$name should be a gas mix")
}

/** KeyReferenceDescription is a field naming an entry inside the same item, written `*p1`. */
class KeyReferenceDescription(
    name: String,
    /** The field holding the collection pointed into. */
    val collection: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = KeyReference::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            resultOf(KeyReference.parse(text), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should name an entry of $collection: ${refused.message}")
        }

    /**
     * Whether the text is written as a key reference, and nothing more.
     *
     * Whether the key exists is asked of the collection, not of a value.
     */
    override fun validate(value: Any): Validity =
        if (value is KeyReference) Validity.Valid
        else Validity.Invalid("$name should name an entry of $collection")
}

/**
 * ReferenceDescription is a field naming another item, written `@id`.
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

    override val valueType: KClass<*> get() = Reference::class

    override fun parse(text: String, overrides: Boolean): Result<Any> =
        try {
            resultOf(Reference.parse(text, oneOff), text, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(text, "$name should name another item: ${refused.message}")
        }

    /**
     * Whether the text is written as a reference, and nothing more.
     *
     * Whether it resolves is not part of its validity: `@john` names a person whether or not
     * that person is in the logbook yet. `DATA-66`.
     */
    override fun validate(value: Any): Validity = when {
        value !is Reference -> Validity.Invalid("$name should name another item")
        value is Reference.OneOff && !oneOff ->
            Validity.Invalid("$name should be a reference, written with a leading @")

        else -> Validity.Valid
    }
}

/**
 * OwnedItemDescription is a field whose value is an item kept inside its owner and read with it.
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
) : FieldDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = OwnedItem::class

    /** An owned item is a set of fields, so no text is ever one. */
    override fun parse(text: String, overrides: Boolean): Result<Any> =
        Result.Unusable(text, "$name should be a set of fields, not text")
}

/**
 * ItemDescription is one item type: its name, and its fields in the order an interface offers
 * them.
 *
 * Immutable.
 */
class ItemDescription(
    val name: String,
    fields: List<FieldDescription>,
) {

    // Copied. A List is read-only, not immutable.
    val fields: List<FieldDescription> = fields.toList()

    /** Built on first use. Every read is by name, and a scan per read is the wrong shape. */
    val byName: Map<String, FieldDescription> by lazy { fields.associateBy { it.name } }

    operator fun get(name: String): FieldDescription? = byName[name]
}
