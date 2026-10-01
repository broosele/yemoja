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

    /**
     * How many decimals [dimension] is written with here, in this file's own unit.
     *
     * The one figure behind both showing a value and storing one, which is why a number on the
     * screen and the same number in the file agree. `DATA-88`.
     *
     * A dimension taking no unit falls back, there being no scale to ask. Only `DIMENSIONLESS`
     * is such a dimension, and its values are proportions: a gradient factor of `0.3`, a CNS
     * percentage.
     */
    fun decimalsOf(dimension: Dimension): Int =
        scaleOf(dimension)?.decimals
            ?: SCALES[dimension]?.values?.first()?.decimals
            ?: WITHOUT_A_UNIT

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

/** What a dimension taking no unit is written with, there being no scale to carry a figure. */
private const val WITHOUT_A_UNIT = 3

/**
 * Scale is how one unit's numbers become the default unit's, and how finely it is written.
 *
 * Affine, not a factor: Fahrenheit and kelvin are offset from Celsius as well as scaled, and a
 * factor alone would read 32 F as 17.8 C.
 *
 * [decimals] is the precision the unit is required to carry, and it governs both showing a value
 * and storing one. One of 0, 3, 6 and 9. `DATA-88`.
 */
private class Scale(val factor: Double, val offset: Double = 0.0, val decimals: Int = 3)

/**
 * `DATA-8`'s unit set, the default of each dimension first.
 *
 * Values are exact where the definition is: a foot is 0.3048 metres and a pound 0.45359237
 * kilograms by definition, and a pound-force per square inch is 6894.757293168361 pascal.
 *
 * **A precision is 0, 3, 6 or 9, and a unit takes the smallest that holds what is written in it.**
 * Three decimals suits nearly everything: it keeps the `0.04` litres a supplied weight displaces
 * and the `0.88` bar of an atmospheric pressure, both of which a figure chosen for how a depth
 * reads would have rounded away. Where three is not enough the next step is six, and where the
 * unit is already fine enough the step below is nothing.
 *
 * Steps of three rather than a figure per unit, because eighteen hand-picked numbers are
 * eighteen small arguments, and the four that matter are `0.001`, `0.000001` and their ends.
 */
private val SCALES: Map<Dimension, Map<String, Scale>> = mapOf(
    Dimension.LENGTH to mapOf("m" to Scale(1.0, decimals = 3), "ft" to Scale(0.3048, 0.0, 3)),
    Dimension.MASS to mapOf("kg" to Scale(1.0, decimals = 3), "lb" to Scale(0.45359237, 0.0, 3)),
    Dimension.TIME to mapOf(
        // A second is the model's own unit and nothing is written in fractions of one, so it
        // takes none. An hour needs six: three would be steps of 3.6 seconds.
        "s" to Scale(1.0, decimals = 0),
        "min" to Scale(60.0, decimals = 3),
        "h" to Scale(3600.0, decimals = 6),
    ),
    Dimension.TEMPERATURE to mapOf(
        "C" to Scale(1.0, decimals = 3),
        "F" to Scale(5.0 / 9.0, -32.0, 3),
        "K" to Scale(1.0, -273.15, 3),
    ),
    Dimension.VOLUME to mapOf(
        "l" to Scale(1.0, decimals = 3),
        // Nine. A weight displacing 0.0456 litres is 0.0000456 cubic metres, and six decimals
        // would write it as 0.000046 — a digit off the values with none to spare.
        "m3" to Scale(1000.0, 0.0, 9),
    ),
    Dimension.PRESSURE to mapOf(
        "bar" to Scale(1.0, decimals = 3),
        "psi" to Scale(0.06894757293168361, 0.0, 3),
        // A pascal is a hundred-thousandth of a bar, which is finer than anything measured.
        "Pa" to Scale(0.00001, 0.0, 0),
    ),
    // Six decimals of a degree is about a tenth of a metre; three would be a hundred metres,
    // which would not tell two ends of a wreck apart.
    Dimension.ANGLE to mapOf("deg" to Scale(1.0, decimals = 6)),
    Dimension.DENSITY to mapOf("kg/m3" to Scale(1.0, decimals = 3)),
    // A volume over time, which is what breathing gas is. Nine for cubic metres a second, the
    // unit UDDF writes: twenty litres a minute is 0.000333333 of one.
    Dimension.FLOW to mapOf("l/min" to Scale(1.0, decimals = 3), "m3/s" to Scale(60_000.0, 0.0, 9)),
    // A rise or a descent, in the minutes a diver counts them in. `DATA-129`.
    Dimension.SPEED to mapOf("m/min" to Scale(1.0, decimals = 3), "ft/min" to Scale(0.3048, 0.0, 3)),
)
