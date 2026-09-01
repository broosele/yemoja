package yemoja.data

import kotlin.math.roundToInt

/**
 * A breathing mix, held as the parts of it that matter to decompression: oxygen and
 * helium, with nitrogen making up the rest.
 *
 * A value rather than a label — `DATA-55`. `EAN32` is how a diver writes it and what
 * [toString] gives back, but what is kept is the quantity, so a mix converts to any format
 * that states its fractions and back again — to whole percentages, which is as fine as
 * this one goes.
 *
 * Held in **whole percentages**, because that is what the format writes: `EAN32` and
 * nothing finer. Anything more precise could not be saved, so a mix that held it would
 * change on the first write — and [toString] would no longer name one mix only.
 *
 * Integers also compare exactly, which fractions do not.
 */
data class Gas(val percentO2: Int, val percentHe: Int) {

    init {
        require(percentO2 in 0..100) { "oxygen is $percentO2 percent" }
        require(percentHe in 0..100) { "helium is $percentHe percent" }
        require(percentO2 + percentHe <= 100) {
            "oxygen and helium come to more than the whole of it"
        }
    }

    /**
     * From fractions, which is how every interchange format states a mix. Rounded to whole
     * percentages, which is all this one can keep.
     */
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

        /** Air is written as air, not as `EAN21`. */
        val AIR = Gas(21, 0)

        val OXYGEN = Gas(100, 0)
    }
}
