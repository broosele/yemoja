package yemoja.logic

import yemoja.data.Gas
import kotlin.math.exp
import kotlin.math.ln

/*
 * Bühlmann ZH-L16C: what sixteen compartments hold, and how shallow they may be brought.
 *
 * Arithmetic, and nothing else. No item is read here, so nothing this says about a dive can be
 * stored. Only this model is implemented, `deco_model` naming others because computers run them.
 * `LOGIC-3`.
 *
 * A schedule is absent: this answers what a profile costs, and the profile is given rather than
 * produced. Written from the published coefficients.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md, and
 * manual/decompression.md explains the model for divers.
 */

/**
 * Tissues is what the compartments of one body hold, in bar of nitrogen and of helium.
 *
 * Each compartment is a half-time and a pair of coefficients and nothing besides. They are not
 * organs: real tissue is a continuum, and sixteen points cover the behaviour that matters.
 *
 * Immutable. Breathing gives new tissues rather than changing these.
 */
class Tissues private constructor(
    private val nitrogen: DoubleArray,
    private val helium: DoubleArray,
) {

    /** What compartment [number] holds of nitrogen, in bar. */
    fun nitrogenIn(number: Int): Double = nitrogen[indexOf(number)]

    /** What compartment [number] holds of helium, in bar. */
    fun heliumIn(number: Int): Double = helium[indexOf(number)]

    /**
     * These tissues after [seconds] of breathing [gas], with the pressure around them going from
     * [from] to [to] in bar.
     *
     * The pressure moves along the straight line between the two, which is how a series is read
     * between two samples. A steady depth is the same call with one pressure twice.
     */
    fun breathing(gas: Gas, from: Double, to: Double, seconds: Double): Tissues {
        require(seconds >= 0) { "seconds should be 0 or more, but was $seconds" }
        if (seconds == 0.0) return this
        val minutes = seconds / SECONDS_IN_MINUTE
        val loadedNitrogen = DoubleArray(COMPARTMENTS)
        val loadedHelium = DoubleArray(COMPARTMENTS)
        for (index in 0..<COMPARTMENTS) {
            loadedNitrogen[index] = loaded(
                nitrogen[index],
                inspired(from, gas.fractionN2),
                inspired(to, gas.fractionN2),
                minutes,
                HALF_TIMES_N2[index],
            )
            loadedHelium[index] = loaded(
                helium[index],
                inspired(from, gas.fractionHe),
                inspired(to, gas.fractionHe),
                minutes,
                HALF_TIMES_HE[index],
            )
        }
        return Tissues(loadedNitrogen, loadedHelium)
    }

    /**
     * The shallowest pressure these tissues may be brought to, in bar, allowing
     * [gradientFactor] of the overpressure the model permits.
     *
     * Above the pressure at the surface it means stops are required. A factor of 1 is the bare
     * limit Bühlmann gives, and a lower one holds the ascent deeper.
     *
     * The answer is the most demanding compartment's, which early in an ascent is a fast one and
     * hours later a slow one.
     */
    fun ceiling(gradientFactor: Double): Double {
        requireFactor(gradientFactor)
        var shallowest = 0.0
        for (index in 0..<COMPARTMENTS) {
            val total = nitrogen[index] + helium[index]
            if (total <= 0) continue
            val a = (A_N2[index] * nitrogen[index] + A_HE[index] * helium[index]) / total
            val b = (B_N2[index] * nitrogen[index] + B_HE[index] * helium[index]) / total
            val tolerated =
                (total - a * gradientFactor) / (gradientFactor / b + 1.0 - gradientFactor)
            if (tolerated > shallowest) shallowest = tolerated
        }
        return shallowest
    }

    /**
     * How long these tissues may go on breathing [gas] at [ambient] bar before their ceiling
     * passes [surface], in seconds, or null where a day of it would not.
     *
     * The no-decompression limit: the boundary after which going straight up stops being an
     * option, rather than a countdown to danger.
     *
     * Null is the honest answer for a depth shallow enough that the ceiling settles below the
     * surface, since every limit there is the length of the search rather than the model's.
     */
    fun noDecompressionSeconds(
        gas: Gas,
        ambient: Double,
        surface: Double,
        gradientFactor: Double,
    ): Double? {
        requireFactor(gradientFactor)
        if (ceiling(gradientFactor) > surface) return 0.0
        var reached = this
        var elapsed = 0.0
        while (elapsed < LONGEST_SEARCH) {
            val next = reached.breathing(gas, ambient, ambient, STEP)
            if (next.ceiling(gradientFactor) > surface) {
                return elapsed + reached.crossing(gas, ambient, surface, gradientFactor)
            }
            reached = next
            elapsed += STEP
        }
        return null
    }

    /**
     * How long these tissues need at [surface] bar before they may be taken up to [cabin] bar, in
     * seconds, allowing [gradientFactor] of what the model permits.
     *
     * Nought where they may go now. Null where a day of breathing air would not be enough, which
     * says the question is the wrong one rather than giving a figure nobody should plan on.
     *
     * A cabin is an altitude like any other: what a flight does is take the surface away, and the
     * model has always handled that.
     */
    fun noFlightSeconds(surface: Double, cabin: Double, gradientFactor: Double): Double? {
        requireFactor(gradientFactor)
        return waitingAt(surface) { it.ceiling(gradientFactor) <= cabin }
    }

    /**
     * How long these tissues need at [surface] bar to come back to what breathing air there
     * settles them to, in seconds, or null where a day would not do it.
     *
     * **Within a hundredth of a bar**, which is a definition rather than a standard: a compartment
     * approaches its equilibrium and never quite arrives, so somebody has to say how close counts.
     * A hundredth is finer than any decision anybody makes from the answer.
     */
    fun desaturationSeconds(surface: Double): Double? {
        val settled = inspired(surface, Gas.AIR.fractionN2)
        return waitingAt(surface) { tissues ->
            (1..COMPARTMENTS).all {
                tissues.nitrogenIn(it) - settled <= SETTLED && tissues.heliumIn(it) <= SETTLED
            }
        }
    }

    /** How long breathing air at [ambient] takes to make [enough] true, to the minute. */
    private fun waitingAt(ambient: Double, enough: (Tissues) -> Boolean): Double? {
        if (enough(this)) return 0.0
        var waited = 0.0
        var tissues = this
        while (waited < LONGEST_WAIT) {
            tissues = tissues.breathing(Gas.AIR, ambient, ambient, STEP)
            waited += STEP
            if (enough(tissues)) return waited
        }
        return null
    }

    /** Where inside one step the ceiling passes [surface], in seconds from these tissues. */
    private fun crossing(
        gas: Gas,
        ambient: Double,
        surface: Double,
        gradientFactor: Double,
    ): Double {
        var under = 0.0
        var over = STEP
        while (over - under > 1.0) {
            val between = (under + over) / 2
            if (breathing(gas, ambient, ambient, between).ceiling(gradientFactor) > surface) {
                over = between
            } else {
                under = between
            }
        }
        return under
    }

    private fun indexOf(number: Int): Int {
        require(number in 1..COMPARTMENTS) {
            "compartment should be 1 to $COMPARTMENTS, but was $number"
        }
        return number - 1
    }

    companion object {

        /** How many compartments the model has. */
        const val COMPARTMENTS: Int = 16

        /**
         * Tissues that have breathed air at [atmospheric] bar long enough to settle.
         *
         * Where a dive starts from, unless what came before it is known.
         */
        fun saturated(atmospheric: Double): Tissues {
            val settled = inspired(atmospheric, Gas.AIR.fractionN2)
            return Tissues(DoubleArray(COMPARTMENTS) { settled }, DoubleArray(COMPARTMENTS))
        }
    }
}

/**
 * The gradient factor to allow at [ambient] bar, [low] at the first stop and [high] at the
 * surface, sliding between the two.
 *
 * [firstStop] is the deepest pressure a stop was required at. Where nothing required one, or where
 * the ascent has passed it, the factor is [high]: there is no depth left to hold anything back
 * from.
 */
fun gradientFactorAt(
    ambient: Double,
    firstStop: Double,
    surface: Double,
    low: Double,
    high: Double,
): Double {
    requireFactor(low)
    requireFactor(high)
    if (firstStop <= surface) return high
    val through = ((ambient - surface) / (firstStop - surface)).coerceIn(0.0, 1.0)
    return high + (low - high) * through
}

/**
 * What one compartment holds after [minutes] of breathing an inert gas whose pressure in the lungs
 * goes from [inspiredFrom] to [inspiredTo].
 *
 * The Schreiner equation, which takes a pressure that is moving. A steady one is the same
 * arithmetic with a rate of nought.
 */
private fun loaded(
    current: Double,
    inspiredFrom: Double,
    inspiredTo: Double,
    minutes: Double,
    halfTime: Double,
): Double {
    val constant = LN_2 / halfTime
    val rate = (inspiredTo - inspiredFrom) / minutes
    return inspiredTo - rate / constant -
        (inspiredFrom - current - rate / constant) * exp(-constant * minutes)
}

/**
 * The pressure of an inert gas in the lungs at [ambient] bar, where it is [fraction] of the mix.
 *
 * The lungs are wet, so water vapour takes its share of the pressure before the mix does. At the
 * shallow end that share is not small: at 1 bar it is a sixteenth of everything breathed.
 */
private fun inspired(ambient: Double, fraction: Double): Double =
    (ambient - WATER_VAPOUR).coerceAtLeast(0.0) * fraction

private fun requireFactor(gradientFactor: Double) {
    require(gradientFactor in 0.0..1.0) {
        "gradient factor should be 0 to 1, but was $gradientFactor"
    }
}

/**
 * Half-times in minutes, and the coefficients that say how much overpressure each compartment
 * tolerates.
 *
 * The published ZH-L16C set, sixteen deep, fastest first. C is the third revision of the limits
 * and the most conservative of the three. Helium moves about two and a half times faster than
 * nitrogen, which is why it has half-times of its own.
 */
private val HALF_TIMES_N2 = doubleArrayOf(
    4.0, 8.0, 12.5, 18.5, 27.0, 38.3, 54.3, 77.0,
    109.0, 146.0, 187.0, 239.0, 305.0, 390.0, 498.0, 635.0,
)

private val A_N2 = doubleArrayOf(
    1.2599, 1.0000, 0.8618, 0.7562, 0.6667, 0.5600, 0.4947, 0.4500,
    0.4187, 0.3798, 0.3497, 0.3223, 0.2850, 0.2737, 0.2523, 0.2327,
)

private val B_N2 = doubleArrayOf(
    0.5050, 0.6514, 0.7222, 0.7825, 0.8126, 0.8434, 0.8693, 0.8910,
    0.9092, 0.9222, 0.9319, 0.9403, 0.9477, 0.9544, 0.9602, 0.9653,
)

private val HALF_TIMES_HE = doubleArrayOf(
    1.51, 3.02, 4.72, 6.99, 10.21, 14.48, 20.53, 29.11,
    41.20, 55.19, 70.69, 90.34, 115.29, 147.42, 188.24, 240.03,
)

private val A_HE = doubleArrayOf(
    1.7424, 1.3830, 1.1919, 1.0458, 0.9220, 0.8205, 0.7305, 0.6502,
    0.5950, 0.5545, 0.5333, 0.5189, 0.5181, 0.5176, 0.5172, 0.5119,
)

private val B_HE = doubleArrayOf(
    0.4245, 0.5747, 0.6527, 0.7223, 0.7582, 0.7957, 0.8279, 0.8553,
    0.8757, 0.8903, 0.8997, 0.9073, 0.9122, 0.9171, 0.9217, 0.9267,
)

/** Water vapour in the lungs at body temperature, in bar. */
private const val WATER_VAPOUR = 0.0627

private const val SECONDS_IN_MINUTE = 60.0

private val LN_2 = ln(2.0)

/** How far a no-decompression limit is looked for before it is called no limit at all. */
private const val LONGEST_SEARCH = 24 * 60 * SECONDS_IN_MINUTE

/** How long a wait on the surface is followed before it is called one that does not end. */
private const val LONGEST_WAIT = 48 * 60 * SECONDS_IN_MINUTE

/** How near its own equilibrium a compartment has to come to count as settled, in bar. */
private const val SETTLED = 0.01

/** How coarsely that search steps before it narrows down to the second. */
private const val STEP = SECONDS_IN_MINUTE
