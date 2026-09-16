package yemoja.logic

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/*
 * The two clocks a diver keeps beside the decompression one: how much of the central nervous
 * system's tolerance an exposure has used, and how much the lungs have taken.
 *
 * Arithmetic, and from published tables: the NOAA single-exposure limits for the first, and the
 * unit pulmonary toxic dose for the second. A different risk each, on a scale of its own, which is
 * why they are two numbers and not one. `LOGIC-38`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * OxygenClock is how much oxygen an exposure has spent: a percentage of the central nervous
 * system's single-exposure limit, and a count of pulmonary units.
 *
 * Immutable. Breathing gives a new clock rather than moving this one.
 */
class OxygenClock private constructor(
    /** How much of the limit has been used, as a percentage, which runs past 100 on a long dive. */
    val percentCns: Double,
    /** Oxygen tolerance units taken, which is a count rather than a percentage of anything. */
    val otu: Double,
) {

    /**
     * This clock after [seconds] of breathing oxygen whose partial pressure goes from [from] to
     * [to], in bar.
     *
     * The pressure is taken as its average over the stretch, which is exact for a stop and close
     * enough on an ascent, a minute of which spans a tenth of a bar.
     */
    fun breathing(from: Double, to: Double, seconds: Double): OxygenClock {
        require(seconds >= 0) { "seconds should be 0 or more, but was $seconds" }
        if (seconds == 0.0) return this
        val minutes = seconds / SECONDS_IN_MINUTE
        val oxygen = (from + to) / 2
        val spent = limitFor(oxygen)?.let { percentCns + minutes / it * 100 }
            ?: decayed(percentCns, minutes)
        val taken = if (oxygen <= OTU_FLOOR) otu
        else otu + minutes * ((oxygen - OTU_FLOOR) / OTU_FLOOR).pow(OTU_POWER)
        return OxygenClock(spent, taken)
    }

    override fun toString(): String = "CNS ${percentCns.toInt()}%, OTU ${otu.toInt()}"

    companion object {

        /** A clock nothing has been spent on, which is where a dive starts. */
        val CLEAR = OxygenClock(0.0, 0.0)
    }
}

/**
 * How long a diver may breathe oxygen at [partialPressure] bar, in minutes, or null where it is
 * low enough that nothing is being spent.
 *
 * The NOAA single-exposure limits, read straight between the figures they publish. Above the
 * richest of them the limit does not go on falling here: the table stops at 1.6 bar because that
 * is as far as anybody is willing to say, and an evaluation raises a finding at that pressure
 * rather than inventing a number past it.
 */
private fun limitFor(partialPressure: Double): Double? {
    if (partialPressure < LIMITS.first().first) return null
    val past = LIMITS.lastOrNull { it.first <= partialPressure } ?: return null
    val next = LIMITS.firstOrNull { it.first > partialPressure } ?: return LIMITS.last().second
    val across = (partialPressure - past.first) / (next.first - past.first)
    return past.second + (next.second - past.second) * across
}

/**
 * What is left of [percent] after [minutes] of breathing little enough oxygen to recover.
 *
 * The clock runs backwards on the surface, halving what it holds every ninety minutes, which is
 * the figure the same tables give for recovery between dives.
 */
private fun decayed(percent: Double, minutes: Double): Double =
    percent * exp(-ln(2.0) / CNS_HALF_TIME * minutes)

/**
 * The published single-exposure limits: a partial pressure in bar, and how many minutes of it are
 * allowed.
 *
 * Richest last, and nothing below the first of them counts against the clock at all.
 */
private val LIMITS = listOf(
    0.6 to 720.0,
    0.7 to 570.0,
    0.8 to 450.0,
    0.9 to 360.0,
    1.0 to 300.0,
    1.1 to 240.0,
    1.2 to 210.0,
    1.3 to 180.0,
    1.4 to 150.0,
    1.5 to 120.0,
    1.6 to 45.0,
)

/** How long the central nervous system's clock takes to give back half of what it holds. */
private const val CNS_HALF_TIME = 90.0

/** The pressure below which the lungs take nothing, in bar. */
private const val OTU_FLOOR = 0.5

/** The exponent the unit pulmonary toxic dose is raised by. */
private const val OTU_POWER = 5.0 / 6.0

private const val SECONDS_IN_MINUTE = 60.0
