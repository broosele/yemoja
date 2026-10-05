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
    fun breathing(gas: Gas, from: Double, to: Double, seconds: Double): Tissues =
        breathing(Inspiration.OpenCircuit(gas), from, to, seconds)

    /**
     * These tissues after [seconds] of breathing what [inspiration] gives, with the pressure around
     * them going from [from] to [to] in bar along a straight line.
     *
     * **Split where the inspired gas bends.** Within each piece the inspired pressures are straight
     * lines in time, which is what the loading equation solves exactly, so a loop crossing from its
     * setpoint to its diluent on one descent is loaded as exactly as an open-circuit mix.
     */
    fun breathing(inspiration: Inspiration, from: Double, to: Double, seconds: Double): Tissues {
        require(seconds >= 0) { "seconds should be 0 or more, but was $seconds" }
        if (seconds == 0.0) return this
        val low = minOf(from, to)
        val high = maxOf(from, to)
        val bends = inspiration.bends().filter { it > low && it < high }.sortedBy { if (to > from) it else -it }
        var tissues = this
        var pressure = from
        var spent = 0.0
        for (bend in bends + to) {
            val share = if (to == from) 1.0 else (bend - pressure) / (to - from)
            val part = if (bend == to) seconds - spent else seconds * share
            tissues = tissues.straight(inspiration, pressure, bend, part)
            spent += part
            pressure = bend
        }
        return tissues
    }

    /** These tissues after [seconds] of [inspiration] between [from] and [to], with no bend between. */
    private fun straight(inspiration: Inspiration, from: Double, to: Double, seconds: Double): Tissues {
        if (seconds <= 0.0) return this
        val minutes = seconds / SECONDS_IN_MINUTE
        val loadedNitrogen = DoubleArray(COMPARTMENTS)
        val loadedHelium = DoubleArray(COMPARTMENTS)
        for (index in 0..<COMPARTMENTS) {
            loadedNitrogen[index] = loaded(
                nitrogen[index],
                inspiration.nitrogen(from),
                inspiration.nitrogen(to),
                minutes,
                HALF_TIMES_N2[index],
            )
            loadedHelium[index] = loaded(
                helium[index],
                inspiration.helium(from),
                inspiration.helium(to),
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
     * The gradient factor these tissues stand at in [ambient] bar: how far the most loaded
     * compartment is from the ambient pressure towards its M-value, as a fraction.
     *
     * Nought is no supersaturation and one is the M-value itself. A negative figure is a
     * compartment still taking gas on, and is the answer rather than an error. Divers call this
     * GF99 when it is read at the current depth.
     */
    fun gradientFactorIn(ambient: Double): Double {
        var most = Double.NEGATIVE_INFINITY
        for (index in 0..<COMPARTMENTS) {
            val total = nitrogen[index] + helium[index]
            if (total <= 0) continue
            val a = (A_N2[index] * nitrogen[index] + A_HE[index] * helium[index]) / total
            val b = (B_N2[index] * nitrogen[index] + B_HE[index] * helium[index]) / total
            val factor = (total - ambient) / (a + ambient / b - ambient)
            if (factor > most) most = factor
        }
        return most
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
    ): Double? = noDecompressionSeconds(Inspiration.OpenCircuit(gas), ambient, surface, gradientFactor)

    /** [noDecompressionSeconds] for whatever [gas] gives, a loop included. */
    fun noDecompressionSeconds(
        gas: Inspiration,
        ambient: Double,
        surface: Double,
        gradientFactor: Double,
    ): Double? {
        requireFactor(gradientFactor)
        if (ceiling(gradientFactor) > surface) return 0.0
        var reached = this
        var elapsed = 0.0
        while (elapsed < LONGEST_SEARCH) {
            val swept = reached.breathing(gas, ambient, ambient, SWEEP)
            if (swept.ceiling(gradientFactor) > surface) {
                return elapsed + reached.crossingIn(gas, ambient, surface, gradientFactor)
            }
            reached = swept
            elapsed += SWEEP
        }
        return null
    }

    /**
     * Where inside one sweep the ceiling passes [surface], in seconds from these tissues.
     *
     * Minute by minute to find which one it happens in, and then to the second inside that. The
     * sweep before it is what keeps a shallow dive cheap: a limit of hours is found in tens of
     * steps rather than hundreds, and no crossing can hide inside a sweep, a compartment at a
     * steady depth moving one way throughout.
     */
    private fun crossingIn(
        gas: Inspiration,
        ambient: Double,
        surface: Double,
        gradientFactor: Double,
    ): Double {
        var walked = this
        var at = 0.0
        while (at < SWEEP) {
            val next = walked.breathing(gas, ambient, ambient, STEP)
            if (next.ceiling(gradientFactor) > surface) {
                return at + walked.crossing(gas, ambient, surface, gradientFactor)
            }
            walked = next
            at += STEP
        }
        return at
    }

    /**
     * How long these tissues need at [surface] bar before they may be taken up to [cabin] bar, in
     * seconds, allowing [gradientFactor] of what the model permits.
     *
     * Nought where they may go now. Null where two days of breathing air would not be enough, which
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
     * settles them to, in seconds, or null where two days would not do it.
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
        gas: Inspiration,
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
 * How long a diver starting fresh may stay at [metres] on [gas], in seconds from leaving the
 * surface, or null where a day at that depth would owe no stop.
 *
 * The figure a table gives for a depth. The descent counts, as it does in a table: a diver going
 * down at [descentMetresAMinute] breathes on the way, so the limit is the descent and then what is
 * left at the bottom. A limit already spent on arrival is the descent alone.
 *
 * **Only the high gradient factor bears on it.** A limit is the moment a stop becomes owed, and
 * whether one is owed is the high factor's question; the low one says how deep a first stop is
 * taken and nothing about whether there is one, which is `LOGIC-37`'s rule for a profile applied
 * to a depth. A screen offering both factors passes the high.
 *
 * Sea water at sea level unless told otherwise, which is what a table assumes too.
 */
fun noDecompressionLimit(
    metres: Double,
    gas: Gas,
    gradientFactorHigh: Double,
    descentMetresAMinute: Double,
    density: Double = NOMINAL_DENSITY,
    surface: Double = SEA_LEVEL,
): Double? {
    require(metres > 0) { "a depth should be more than nought, but was $metres" }
    require(descentMetresAMinute > 0) {
        "a descent rate should be more than nought, but was $descentMetresAMinute"
    }
    val descending = metres / descentMetresAMinute * SECONDS_IN_MINUTE
    val ambient = ambientAt(metres, density, surface)
    val arrived = Tissues.saturated(surface).breathing(gas, surface, ambient, descending)
    val left = arrived.noDecompressionSeconds(gas, ambient, surface, gradientFactorHigh)
        ?: return null
    return descending + left
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
    dryOf(ambient) * fraction

/** What is left of [ambient] bar once the lungs have wetted the gas. */
private fun dryOf(ambient: Double): Double = (ambient - WATER_VAPOUR).coerceAtLeast(0.0)

/**
 * Inspiration is what a breath holds at a given ambient pressure: the oxygen, and the inert gas the
 * tissues take up.
 *
 * Immutable.
 */
sealed class Inspiration {

    /** Bar of nitrogen breathed at [ambient] bar. */
    abstract fun nitrogen(ambient: Double): Double

    /** Bar of helium breathed at [ambient] bar. */
    abstract fun helium(ambient: Double): Double

    /** Bar of oxygen breathed at [ambient] bar, as an oxygen limit judges it. */
    abstract fun oxygen(ambient: Double): Double

    /** The ambient pressures between which [nitrogen], [helium] and [oxygen] are straight lines. */
    abstract fun bends(): List<Double>

    /**
     * OpenCircuit is a fixed mix breathed from a cylinder, the same fractions at every depth.
     *
     * Immutable.
     */
    class OpenCircuit(val gas: Gas) : Inspiration() {

        override fun nitrogen(ambient: Double): Double = inspired(ambient, gas.fractionN2)

        override fun helium(ambient: Double): Double = inspired(ambient, gas.fractionHe)

        override fun oxygen(ambient: Double): Double = gas.fractionO2 * ambient

        override fun bends(): List<Double> = emptyList()
    }

    /**
     * Loop is a closed-circuit rebreather holding its oxygen at [setpoint] bar, the rest of the dry
     * gas inert in the proportion the [diluent] holds it. `LOGIC-46`.
     *
     * **Three stretches, each a straight line.** Where the diluent alone holds more oxygen than the
     * setpoint, deep on a rich diluent, the loop holds the diluent itself. Where the dry gas is no
     * more than the setpoint, near the surface on a high one, it is all oxygen. Between the two, the
     * oxygen is the setpoint and the inert gas everything else.
     *
     * Immutable.
     */
    class Loop(val diluent: Gas, val setpoint: Double) : Inspiration() {

        init {
            require(setpoint > 0) { "a setpoint should be more than nought, but was $setpoint" }
        }

        override fun nitrogen(ambient: Double): Double = inert(ambient) * shareOf(diluent.fractionN2)

        override fun helium(ambient: Double): Double = inert(ambient) * shareOf(diluent.fractionHe)

        // The setpoint, or the diluent's own where it holds more, and no more than the dry breath:
        // continuous, so a stretch read at its middle reads what it breathed. A loop on nearly pure
        // oxygen is held to the dry breath at every depth, which is what such a loop holds.
        override fun oxygen(ambient: Double): Double =
            minOf(dryOf(ambient), maxOf(setpoint, diluent.fractionO2 * ambient))

        override fun bends(): List<Double> = listOfNotNull(
            setpoint + WATER_VAPOUR,
            diluent.fractionO2.takeIf { it > 0 }?.let { setpoint / it + WATER_VAPOUR },
            diluent.fractionO2.takeIf { it > 0 }?.let { setpoint / it },
            // Where the diluent's own oxygen meets the dry breath, which only a nearly pure
            // oxygen diluent reaches at a depth anybody dives.
            diluent.fractionO2.takeIf { it < 1 }?.let { WATER_VAPOUR / (1 - it) },
        )

        /** Bar of inert gas in the dry breath at [ambient] bar. */
        private fun inert(ambient: Double): Double {
            val dry = dryOf(ambient)
            return if (diluentHolds(dry)) dry * (1 - diluent.fractionO2) else (dry - setpoint).coerceAtLeast(0.0)
        }

        private fun diluentHolds(dry: Double): Boolean = diluent.fractionO2 * dry >= setpoint

        /** What share of the diluent's inert gas [fraction] is. */
        private fun shareOf(fraction: Double): Double {
            val inert = diluent.fractionN2 + diluent.fractionHe
            return if (inert <= 0) 0.0 else fraction / inert
        }
    }
}

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
    1.2599, 1.0000, 0.8618, 0.7562, 0.6200, 0.5043, 0.4410, 0.4000,
    0.3750, 0.3500, 0.3295, 0.3065, 0.2835, 0.2610, 0.2480, 0.2327,
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
internal const val WATER_VAPOUR = 0.0627

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

/** How far it leaps while nothing has happened, in seconds, before stepping through a sweep. */
private const val SWEEP = 8 * STEP
