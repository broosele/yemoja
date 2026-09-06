package yemoja.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
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
 * Any kind of value may have any of them: a number can be single, a list, or a series through
 * a dive. `DATA-67`.
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
     * Reads [given] as this field's kind, whatever shape it arrives in.
     *
     * A source hands over what it has, and they differ: JSON has no date, so one arrives as
     * `"2026-02-23"` and is parsed, while a database column hands one over already made and it is
     * taken as it is. A date picker is a source in the same sense. Each kind says here which it
     * accepts, and text is only one of them. `DATA-64`.
     *
     * **Shape is not this method's business.** A field holding a list or a series is walked by
     * whatever owns cardinality, and this is handed one piece at a time. Being given a group where
     * a single value belongs is a shape the walk should have caught, so it answers unusable rather
     * than trying to make sense of it.
     *
     * [overrides] says the value is a stored one on a field that would otherwise work one out,
     * which decides the origin. It is given, not defaulted. Whoever reads knows which case it is,
     * and the wrong answer here is a correct-looking one.
     */
    protected abstract fun interpret(given: Any?, overrides: Boolean): Result<Any>

    /**
     * [given], read as this field, in the units [units] gives.
     *
     * The one way in. Reading a value happens in the unit context of the file it came from, and a
     * kind with a dimension is the only one that has anything to do about it — so this carries
     * the units and [interpret] does not, and every other kind never mentions them. `DATA-8`.
     *
     * Everything [interpret] says about [given] and [overrides] holds here.
     */
    open fun read(given: Any?, overrides: Boolean, units: Units): Result<Any> =
        interpret(given, overrides)

    /**
     * The written form of [value], in the units [units] gives.
     *
     * The inverse of reading text, and only of that. Reading is wider than writing: a date may
     * arrive already made, and this has no counterpart for it. So `format(read(x))` returns text
     * for any `x` a kind accepts, while `read(format(v, units), units)` is the round trip a writer
     * relies on, in any units. That identity is what keeps a file the user wrote in feet in feet.
     * `DATA-76`.
     */
    open fun format(value: Any, units: Units): String = value.toString()

    /**
     * The result of reading [given] as [value], which is what every kind's [interpret] ends with.
     *
     * Runs [validate] on the way, so that what a field refuses when a user types it is exactly
     * what it refuses when a file holds it.
     */
    protected fun resultOf(value: Any, given: Any?, overrides: Boolean): Result<Any> {
        val origin = if (overrides) Result.Origin.OVERRIDDEN else Result.Origin.STORED
        return when (val validity = validate(value)) {
            is Validity.Valid -> Result.Usable(value, origin)
            is Validity.Invalid -> Result.Unusable(Stored.Leaf(given), validity.reason)
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

/**
 * [value] to [decimals] places, with no trailing zeros.
 *
 * The unit says how many, and the same figure serves a file and a screen: a number a user reads
 * and the number their file holds are the same number. `DATA-88`.
 *
 * The precision is a unit's own, so it is the finest thing anyone writes in it rather than the
 * finest anyone reads. A displaced volume of `0.04` litres decides the litre; a depth of `22.1`
 * would have decided it far more coarsely and rounded that volume away.
 *
 * Rounding here is also what keeps a value from growing a tail. Converting into a unit and back
 * is exact only to about the sixteenth digit, so `18.3` in pounds returns as `18.300000000000004`
 * and versioning would show a change nobody made.
 *
 * A whole one is written whole, since `108000` is what a file holds and `108000.0` is noise.
 */
private fun written(value: Double, decimals: Int): String {
    if (value == 0.0 || !value.isFinite()) return "0"
    val scale = 10.0.pow(decimals)
    return plain((round(value * scale) / scale).toString())
}

/**
 * [written] with its exponent spelled out, and no trailing zeros.
 *
 * Kotlin writes a small number as `4.56E-5` and a large one as `2.0E7`. Both are JSON and both
 * are read back correctly, and neither belongs in a file a diver opens in a text editor. A
 * pressure in pascal is `20000000`.
 */
private fun plain(written: String): String {
    val mark = written.indexOfFirst { it == 'e' || it == 'E' }
    if (mark < 0) return written.removeSuffix(".0")
    val exponent = written.substring(mark + 1).toInt()
    val mantissa = written.substring(0, mark)
    val body = mantissa.removePrefix("-")
    val dot = body.indexOf('.')
    val digits = body.replace(".", "")
    val point = (if (dot < 0) body.length else dot) + exponent
    val spelled = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    val trimmed = if ('.' in spelled) spelled.trimEnd('0').trimEnd('.') else spelled
    return if (mantissa.startsWith("-")) "-$trimmed" else trimmed
}

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

    /**
     * The dimension refuses first, then the number is read, then it is converted.
     *
     * Converting before [validate] runs is what makes a range mean one thing: `0.0..90.0` is
     * ninety metres whatever the file is written in. Converting after would check a depth in feet
     * against a bound in metres.
     */
    override fun read(given: Any?, overrides: Boolean, units: Units): Result<Any> {
        units.refusal(dimension)?.let {
            return Result.Unusable(Stored.Leaf(given), "$name cannot be read: $it")
        }
        val written = when (given) {
            // A whole one is a number too. Every file writes 108000 rather than 108000.0.
            is Double -> given
            is Long -> given.toDouble()
            is Int -> given.toDouble()
            is String -> given.trim().toDoubleOrNull()
            else -> null
        } ?: return Result.Unusable(Stored.Leaf(given), "$name should be a number")
        return resultOf(units.toDefault(dimension, written), given, overrides)
    }

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> =
        read(given, overrides, Units.DEFAULT)

    override fun format(value: Any, units: Units): String =
        if (value is Double) {
            written(units.fromDefault(dimension, value), units.decimalsOf(dimension))
        } else {
            value.toString()
        }

    override fun validate(value: Any): Validity = when {
        value !is Double -> Validity.Invalid("$name should be a number")
        range != null && value !in range ->
            Validity.Invalid("$name should be within $range$unit, but was $value$unit")

        else -> Validity.Valid
    }

    /** The unit a bound and a value are stated in, for a message. Empty where there is none. */
    private val unit: String get() = Units.defaultName(dimension)?.let { " $it" } ?: ""
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

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        // A fraction is refused however it arrives, so 7.0 is no more a rating than "7.0" is.
        is Int -> resultOf(given, given, overrides)
        is Long -> {
            // Narrow, widen, compare: anything that does not survive the trip did not fit.
            val narrowed = given.toInt()
            if (narrowed.toLong() == given) resultOf(narrowed, given, overrides)
            else Result.Unusable(
                Stored.Leaf(given),
                "$name should be a whole number this machine can hold",
            )
        }

        is String -> given.trim().toIntOrNull()
            ?.let { resultOf(it, given, overrides) }
            ?: Result.Unusable(Stored.Leaf(given), "$name should be a whole number")

        else -> Result.Unusable(Stored.Leaf(given), "$name should be a whole number")
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
    suggestedSet: Set<String>? = null,
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
    val suggestedSet: Set<String>? = suggestedSet?.toSet()

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> =
        if (given is String) resultOf(given, given, overrides)
        else Result.Unusable(Stored.Leaf(given), "$name should be text")

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
            Validity.Invalid("$name should be one of ${fixedSet.joinToString(", ")}")

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

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> =
        if (given is String) resultOf(given, given, overrides)
        else Result.Unusable(Stored.Leaf(given), "$name should be text")

    override fun validate(value: Any): Validity = when {
        value !is String -> Validity.Invalid("$name should be text")
        value.any { it != '\n' && it.isHidden() } ->
            Validity.Invalid("$name should not contain characters that cannot be seen")

        else -> Validity.Valid
    }
}

/**
 * DateDescription is a field holding a date, always written `"2026-02-23"`. No `units`
 * setting is applicable.
 */
class DateDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Date::class

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is Date -> resultOf(given, given, overrides)
        is String -> try {
            resultOf(Date.parse(given), given, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(Stored.Leaf(given), "$name should be a date: ${refused.message}")
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should be a date")
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

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is Time -> resultOf(given, given, overrides)
        is String -> try {
            resultOf(Time.parse(given), given, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(Stored.Leaf(given), "$name should be a time: ${refused.message}")
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should be a time")
    }

    override fun validate(value: Any): Validity =
        if (value is Time) Validity.Valid else Validity.Invalid("$name should be a time")
}

/** The written forms that mean true. Surrounding spaces and case are removed before matching. */
private val TRUE_FORMS = setOf("true", "t", "yes", "y")

/** The written forms that mean false. */
private val FALSE_FORMS = setOf("false", "f", "no", "n")

/**
 * BooleanDescription is a field holding `true` or `false`.
 *
 * Reading is forgiving, as `manual/data-format.md` promises for a mix: case is ignored and the
 * short and spoken forms are understood. A number is not one of them — `1` would invite `2`, and
 * a count is a different kind.
 */
class BooleanDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Boolean::class

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is Boolean -> resultOf(given, given, overrides)
        is String -> when (given.trim().lowercase()) {
            in TRUE_FORMS -> resultOf(true, given, overrides)
            in FALSE_FORMS -> resultOf(false, given, overrides)
            else -> Result.Unusable(Stored.Leaf(given), "$name should be true or false")
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should be true or false")
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

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is Gas -> resultOf(given, given, overrides)
        is String -> try {
            resultOf(Gas.parse(given), given, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(Stored.Leaf(given), "$name should be a gas mix: ${refused.message}")
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should be a gas mix")
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

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is KeyReference -> resultOf(given, given, overrides)
        is String -> try {
            resultOf(KeyReference.parse(given), given, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(
                Stored.Leaf(given),
                "$name should name an entry of $collection: ${refused.message}",
            )
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should name an entry of $collection")
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
    /** Whether a plain name may stand in, asserting no id. */
    val oneOffAllowed: Boolean = false,
) : FieldDescription(name, label, role, cardinality) {

    override val valueType: KClass<*> get() = Reference::class

    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> = when (given) {
        is Reference -> resultOf(given, given, overrides)
        is String -> try {
            resultOf(Reference.parse(given, oneOffAllowed), given, overrides)
        } catch (refused: ValueFormatException) {
            Result.Unusable(
                Stored.Leaf(given),
                "$name should name another item: ${refused.message}",
            )
        }

        else -> Result.Unusable(Stored.Leaf(given), "$name should name another item")
    }

    /**
     * Whether the text is written as a reference, and nothing more.
     *
     * Whether it resolves is not part of its validity: `@john` names a person whether or not
     * that person is in the logbook yet. `DATA-66`.
     */
    override fun validate(value: Any): Validity = when {
        value !is Reference -> Validity.Invalid("$name should name another item")
        value is Reference.OneOff && !oneOffAllowed ->
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

    /** An owned item is a set of fields, so no single value is ever one. */
    protected override fun interpret(given: Any?, overrides: Boolean): Result<Any> =
        Result.Unusable(Stored.Leaf(given), "$name should be a set of fields, not one value")
}

/**
 * ItemDescription is one item type: its name, its fields in the order an interface offers them,
 * the order its items are listed in, and what a new one of it should be called.
 *
 * Immutable.
 */
class ItemDescription(
    val name: String,
    fields: List<FieldDescription>,
    orderedBy: List<Ordering> = emptyList(),
    val proposedId: ((Item) -> String)? = null,
) {

    // Copied. A List is read-only, not immutable.
    val fields: List<FieldDescription> = fields.toList()

    /**
     * How to sort items of this type, most significant first, or empty to leave them as read.
     *
     * `DATA-89`.
     */
    val orderedBy: List<Ordering> = orderedBy.toList()

    /**
     * What a new item of this type should be called, from the item itself.
     *
     * A *proposal*: whoever mints takes the first id free from it, so this need not check what is
     * already there and two items may propose the same thing. It runs against a built item, which
     * works because an item holds no id — one is made, asked what it should be called, and added
     * under the answer.
     *
     * It sits here rather than in the data layer because it is domain knowledge: a dive is named
     * for the day it was made on, a person for their name, and `DATA-84` says the cost of the
     * narrow character rule falls where the proposal is made. Absent on an owned type, which is
     * never named at all.
     */

    /** Built on first use. Every read is by name, and a scan per read is the wrong shape. */
    val byName: Map<String, FieldDescription> by lazy { fields.associateBy { it.name } }

    operator fun get(name: String): FieldDescription? = byName[name]
}
