package yemoja.data

/*
 * The machinery for describing an item type. What a dive or a person *is* belongs to the
 * logic layer, which builds an item set with these. Nothing here names a field.
 *
 * See ../../../../../doc.md — "How a type is described".
 *
 * Absent until settled: validation (DATA-66), `required`, and the shape of the typed
 * reads (DATA-51).
 */

/**
 * What a number measures, deciding which `units` entry applies to it.
 *
 * [DIMENSIONLESS] is a member, not an absence: a ratio or a percentage takes no unit and
 * needs none. `DATA-8`'s unit table has no row for it.
 */
enum class Dimension {
    LENGTH, MASS, TIME, TEMPERATURE, VOLUME, PRESSURE, ANGLE, DENSITY, DIMENSIONLESS,
}

/**
 * How many, and how reached. Orthogonal to what a field contains — `DATA-67`.
 *
 * [KEYED] entries are addressable, which `@2026-06-21#0*p1` reaches; a [LIST] has no
 * addressable elements. [SERIES] is indexed by time, always in seconds. [KEYED_SERIES] is
 * one series per key, used by `pressures` and nothing else.
 *
 * Unused combinations stay sayable; meaningless ones are prevented by the types.
 */
enum class Cardinality { SINGLE, LIST, KEYED, SERIES, KEYED_SERIES }

/**
 * Recorded, worked out, or worked out and correctable. Orthogonal to the kind of field.
 * A derivation carries its own computation.
 */
sealed interface Role {

    /** Recorded and nothing else. */
    object Primary : Role

    /** Worked out from other values, never stored. */
    class Derived(val compute: (Item) -> Result) : Role

    /** Derived, but a stored value is present sometimes and corrects it when it is. */
    class Overrideable(val compute: (Item) -> Result) : Role
}

/**
 * One field of one item type, in enough detail for the parser, the checks and every front
 * end to work from.
 *
 * Three implementations: a value, a reference to another item, and an owned item. How many
 * of each is [Cardinality].
 *
 * These describe fields and hold no values of their own, which is what the names say.
 */
sealed class FieldDescription(
    /** As written in a file. */
    val name: String,
    label: String?,
    val role: Role,
    val cardinality: Cardinality,
) {
    /**
     * Shown to a diver. Defaults from [name] — underscores to spaces, first letter up —
     * which works because field names are never abbreviated. Acronyms need one given:
     * `cns` would otherwise show as "Cns".
     */
    val label: String =
        label ?: name.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** For test failures. */
    override fun toString() =
        "${this::class.simpleName}($name, ${role::class.simpleName}, $cardinality)"
}

/**
 * A value rather than a reference or an owned item. The kind is the type, so a property
 * belongs only to the kinds it applies to.
 *
 * [default] is returned where nothing is written, so such a field never reads absent.
 * Nothing is stored to make that happen — `DATA-56`.
 */
sealed class ValueDescription(
    name: String,
    label: String? = null,
    role: Role,
    cardinality: Cardinality,
    val default: Any? = null,
) : FieldDescription(name, label, role, cardinality)

/** A measurement. */
class NumberDescription(
    name: String,
    val dimension: Dimension,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Inclusive bounds, where given. */
    val min: Double? = null,
    val max: Double? = null,
    default: Any? = null,
) : ValueDescription(name, label, role, cardinality, default)

/**
 * A count, never a measurement: no dimension, and no `units` declaration reaches it.
 * `DATA-68`.
 */
class WholeNumberDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** Whole bounds: a rating runs 1 to 10, not 1.0 to 10.0. */
    val min: Int? = null,
    val max: Int? = null,
    default: Any? = null,
) : ValueDescription(name, label, role, cardinality, default)

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
    default: Any? = null,
) : ValueDescription(name, label, role, cardinality, default)

/**
 * Prose: line breaks allowed, and a leading `@` or `*`. Carries no vocabulary, closed or
 * offered. `remarks` is nearly the only one in the model.
 */
class MultilineTextDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality)

/** Always `"2026-02-23"`; no `units` setting reaches it. */
class DateDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality)

/** Always `"09:15:00"`. */
class TimeDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality)

/** `true` or `false`. */
class BooleanDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    default: Any? = null,
) : ValueDescription(name, label, role, cardinality, default)

/** A mix as divers write it — `AIR`, `EAN32`, `TMX18/35` — parsed for its fractions. */
class GasDescription(
    name: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality)

/** An entry inside the same item, written `*p1`. */
class KeyReferenceDescription(
    name: String,
    /** The field holding the collection pointed into. */
    val collection: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
) : ValueDescription(name, label, role, cardinality)

/**
 * Another item, written `@id`. [targetType] is what is expected there, not what is
 * guaranteed: the target may be the wrong type, or not loaded.
 */
class ReferenceDescription(
    name: String,
    val targetType: String,
    label: String? = null,
    role: Role = Role.Primary,
    cardinality: Cardinality = Cardinality.SINGLE,
    /** A plain name may stand in, asserting no id. */
    val oneOff: Boolean = false,
) : FieldDescription(name, label, role, cardinality)

/**
 * Fields kept inside their owner and read with it. [Cardinality.SINGLE] is one, a dive's
 * environment; [Cardinality.KEYED] is several under keys, a dive's profiles.
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
