package yemoja.data

/**
 * Units is what the numbers of one file are written in.
 *
 * Immutable.
 *
 * A declaration reaches no further than the file it is written in, so one of these belongs to one
 * file and is carried to every value read out of it. `DATA-8` fixes the names and which of each
 * dimension is the default; the model holds the default and converts at the edges, so nothing
 * above a description ever meets a foot.
 *
 * **A name this build does not know refuses its own dimension and nothing else.** A file declaring
 * a length nobody recognises gives up its depths and its distances, and its temperatures read
 * normally. A key naming no dimension at all is passed over the same way. `DATA-87`.
 *
 * For example, `of(mapOf("length" to "ft"))` reads 100 as 30.48 and writes 30.48 back as 100.
 */
class Units private constructor(private val declared: Map<Dimension, String>) {

    /** [value], written in this file's unit, as the model holds it. */
    fun toDefault(dimension: Dimension, value: Double): Double {
        val scale = scaleOf(dimension) ?: return value
        return (value + scale.offset) * scale.factor
    }

    /** [value], as the model holds it, written in this file's unit. */
    fun fromDefault(dimension: Dimension, value: Double): Double {
        val scale = scaleOf(dimension) ?: return value
        return value / scale.factor - scale.offset
    }

    /**
     * Why [dimension] cannot be read here, or absent where it can.
     *
     * A reason rather than a flag, because it names the unit that was not understood, and that
     * name is the whole of what a user needs to fix it.
     */
    fun refusal(dimension: Dimension): String? {
        val written = declared[dimension] ?: return null
        if (scaleOf(dimension) != null) return null
        return "$written is not a unit of ${dimension.name.lowercase()}"
    }

    private fun scaleOf(dimension: Dimension): Scale? =
        SCALES[dimension]?.get(declared[dimension] ?: return null)

    companion object {

        /** Every dimension in `DATA-8`'s default unit, which is what a file declaring nothing
         * is written in. */
        val DEFAULT: Units = Units(emptyMap())

        /**
         * The units a `units` block declares, by the dimension each names.
         *
         * A key naming no dimension is passed over, which is how a build meets a dimension a
         * later one added. A value that is not one of `DATA-8`'s names is kept as written, so
         * that [refusal] can say what it was.
         */
        fun of(declared: Map<String, Any?>): Units {
            val chosen = LinkedHashMap<Dimension, String>()
            for ((key, given) in declared) {
                val dimension = SCALES.keys.firstOrNull { it.name.lowercase() == key } ?: continue
                chosen[dimension] = if (given is String) given else given.toString()
            }
            return if (chosen.isEmpty()) DEFAULT else Units(chosen)
        }

        /** What [dimension] is written in when nothing says otherwise, or absent where it
         * takes no unit. */
        fun defaultName(dimension: Dimension): String? = SCALES[dimension]?.keys?.first()
    }
}

/**
 * Scale is how one unit's numbers become the default unit's.
 *
 * Affine, not a factor: Fahrenheit and kelvin are offset from Celsius as well as scaled, and a
 * factor alone would read 32 F as 17.8 C.
 */
private class Scale(val factor: Double, val offset: Double = 0.0)

/**
 * `DATA-8`'s unit set, the default of each dimension first.
 *
 * Values are exact where the definition is: a foot is 0.3048 metres and a pound 0.45359237
 * kilograms by definition, and a pound-force per square inch is 6894.757293168361 pascal.
 */
private val SCALES: Map<Dimension, Map<String, Scale>> = mapOf(
    Dimension.LENGTH to mapOf("m" to Scale(1.0), "ft" to Scale(0.3048)),
    Dimension.MASS to mapOf("kg" to Scale(1.0), "lb" to Scale(0.45359237)),
    Dimension.TIME to mapOf("s" to Scale(1.0), "min" to Scale(60.0), "h" to Scale(3600.0)),
    Dimension.TEMPERATURE to mapOf(
        "C" to Scale(1.0),
        "F" to Scale(5.0 / 9.0, -32.0),
        "K" to Scale(1.0, -273.15),
    ),
    Dimension.VOLUME to mapOf("l" to Scale(1.0), "m3" to Scale(1000.0)),
    Dimension.PRESSURE to mapOf(
        "bar" to Scale(1.0),
        "psi" to Scale(0.06894757293168361),
        "Pa" to Scale(0.00001),
    ),
    Dimension.ANGLE to mapOf("deg" to Scale(1.0)),
    Dimension.DENSITY to mapOf("kg/m3" to Scale(1.0)),
)
