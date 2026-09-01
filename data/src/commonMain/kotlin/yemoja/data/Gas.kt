package yemoja.data

import kotlin.math.roundToInt

/**
 * A breathing mix, held as the parts of it that matter to decompression: oxygen and helium, with
 * nitrogen making up the rest.
 *
 * A value, not a label. `DATA-55`. `EAN32` is how a diver writes it and what [toString] gives back.
 * What is kept is the quantity, so a mix converts to and from any format that states its fractions.
 *
 * Held in **whole percentages**, because that is what our own format writes. Anything finer could
 * not be saved. A mix holding tenths would change on its first write, and [toString] would no
 * longer name one mix only. Integers also compare exactly, which fractions do not.
 */
data class Gas(val percentO2: Int, val percentHe: Int) {

    init {
        require(percentO2 in 0..100) { "oxygen is $percentO2%" }
        require(percentHe in 0..100) { "helium is $percentHe%" }
        require(percentO2 + percentHe <= 100) {
            "oxygen and helium together are more than 100%"
        }
    }

    /** From fractions, as interchange formats state a mix. Rounded to whole percentages. */
    constructor(fractionO2: Double, fractionHe: Double) :
            this((fractionO2 * 100).roundToInt(), (fractionHe * 100).roundToInt())

    val fractionO2: Double get() = percentO2.toDouble() / 100

    val fractionHe: Double get() = percentHe.toDouble() / 100

    /** What is left when oxygen and helium are taken out. */
    val fractionN2: Double get() = percentN2.toDouble() / 100

    val percentN2: Int get() = 100 - percentO2 - percentHe

    /** As divers write it: `AIR`, `O2`, `EAN32`, `TMX18/35`. */
    override fun toString(): String = when {
        percentHe > 0 -> "TMX$percentO2/$percentHe"
        this == OXYGEN -> "O2"
        this == AIR -> "AIR"
        else -> "EAN$percentO2"
    }

    companion object {

        val AIR = Gas(21, 0)

        val OXYGEN = Gas(100, 0)

        private val NITROX = Regex("""(?:EANX?|NX|NITROX)?(\d{1,3})%?""")
        private val TRIMIX = Regex("""(?:TMX|TX|TRIMIX)?(\d{1,3})/(\d{1,3})(?:/(\d{1,3}))?""")

        /**
         * Reads a mix as divers write it.
         *
         * Throws where the text is not one.
         *
         * This is the utility, not the parser. Whatever reads a field catches and answers
         * *unusable*. Nothing should call this without catching.
         *
         * Deliberately forgiving, as `manual/data-format.md` promises: case and spaces are ignored,
         * and the marker before the numbers is optional wherever the numbers alone are unambiguous.
         * `EAN32`, `nx 32`, `32%` and `Nitrox32` are one mix; `TMX18/35`, `18/35` and `18/35/47`
         * are another.
         *
         * A bare `32` is refused: it could be a cylinder size. So is a mix naming a gas this model
         * does not hold. A three-part trimix must agree with itself.
         */
        fun parse(text: String): Gas = try {
            read(text)
        } catch (impossible: IllegalArgumentException) {
            // The constructor guards against a caller's bug. Here the numbers came from a
            // file, so the complaint is about the text.
            throw ValueFormatException("$text: ${impossible.message}")
        }

        private fun read(text: String): Gas {
            val written = text.filterNot { it.isWhitespace() }.uppercase()
            if (written == "AIR") return AIR
            if (written == "O2" || written == "OXYGEN") return OXYGEN

            TRIMIX.matchEntire(written)?.let { match ->
                val (oxygen, helium, nitrogen) = match.destructured
                val gas = Gas(oxygen.toInt(), helium.toInt())
                if (nitrogen.isNotEmpty() && nitrogen.toInt() != gas.percentN2) {
                    throw ValueFormatException(
                        "$text gives ${gas.percentN2}% nitrogen, not $nitrogen%"
                    )
                }
                return gas
            }
            // Bare digits fall through. No marker and no percent sign, so nothing says a
            // mix is meant.
            NITROX.matchEntire(written)?.let { match ->
                if (!written.all { it.isDigit() }) return Gas(match.groupValues[1].toInt(), 0)
            }
            throw ValueFormatException("$text is not a mix")
        }
    }
}
