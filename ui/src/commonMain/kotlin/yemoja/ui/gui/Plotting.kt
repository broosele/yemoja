package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.logic.Types
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * Bringing many dives to one figure, which is what a plot draws when it does not draw dots.
 *
 * See ../../../../../../gui/doc.md — `GUI-32`.
 */

/**
 * Gathering is what the plot draws: one dive apiece, or many dives brought to one figure.
 *
 * [EACH] is a dot per dive and gathers nothing. [RUNNING] adds the dives up in the order they
 * were made and draws the line that makes, which is a question about the whole logbook rather
 * than about any stretch of it. The rest cut the bottom axis into bars and answer one question
 * of each: how many dives fell there, what they came to, what the average was, and the two
 * extremes. `GUI-32`.
 */
internal enum class Gathering(val label: String, val reads: Boolean, val bars: Boolean) {
    EACH("Each dive", reads = true, bars = false),
    COUNT("How many", reads = false, bars = true),
    TOTAL("Total", reads = true, bars = true),
    AVERAGE("Average", reads = true, bars = true),
    LARGEST("Largest", reads = true, bars = true),
    SMALLEST("Smallest", reads = true, bars = true),
    RUNNING("Running total", reads = true, bars = false),
}

/**
 * Step is how wide one bar is.
 *
 * A date is cut by the calendar and everything else by a round number, because a month is a
 * month and no fraction of a year is. `GUI-32`.
 */
internal sealed class Step(val label: String) {

    /** A run of whole calendar months, so a bar begins where a month does. */
    class Calendar(val months: Int, label: String) : Step(label)

    /** A width in whatever the axis counts in. */
    class Width(val size: Double, label: String) : Step(label)
}

/** Bar is one stretch of the bottom axis, what the dives in it come to, and how many there were. */
internal class Bar(val from: Double, val to: Double, val value: Double, val dives: Int)

/**
 * The most bars a plot carries before the fit widens them.
 *
 * Ten years of months, which is what a logbook holds before a bar is thinner than the line
 * around it. Past that the fit steps up to quarters and the reader can step back down.
 */
private const val MOST = 120

/** The calendar cuts a reader would recognise, longest last so that the fit widens as it fails. */
private val CALENDARS: List<Step> = listOf(
    Step.Calendar(1, "month"),
    Step.Calendar(3, "quarter"),
    Step.Calendar(12, "year"),
    Step.Calendar(60, "five years"),
)

/** The steps a round number comes in, which is what a width is chosen from. */
private val ROUNDS: List<Double> = listOf(1.0, 2.0, 5.0)

/** How many widths a menu offers, which is as many as the calendar offers cuts. */
private const val WIDTHS = 4

/**
 * The bar widths [across] can be cut into, narrowest first.
 *
 * A date offers the calendar. Anything else offers round numbers over the range the dives
 * actually cover, from the one that would draw about [MOST] bars to the one that would draw a
 * handful: a width finer than that is a plot of noise and a coarser one is a plot of nothing.
 */
internal fun stepsOf(across: Variable, values: List<Double>): List<Step> {
    if (across.dayOf != null) return CALENDARS
    val reach = reachOf(values)
    val out = ArrayList<Step>()
    var power = floor(log10(reach / MOST))
    while (true) {
        for (round in ROUNDS) {
            val size = round * 10.0.pow(power)
            out += Step.Width(size, widthSaid(size, across))
            if (size >= reach || out.size >= WIDTHS) return out
        }
        power += 1.0
    }
}

/** How far the dives reach along the axis, and never nought, there being no bar of no width. */
private fun reachOf(values: List<Double>): Double {
    if (values.isEmpty()) return 1.0
    val low = values.min()
    val high = values.max()
    return if (high > low) high - low else abs(high).coerceAtLeast(1.0)
}

/** What a width is called in a menu: the number, and the unit where the axis has one. */
private fun widthSaid(size: Double, across: Variable): String =
    if (across.unit.isEmpty()) shortOf(size) else shortOf(size) + " " + across.unit

/**
 * Which of [steps] to open on: the narrowest that draws no more than [MOST] bars.
 *
 * Narrowest rather than roundest, because the question a reader asks first is the detailed one
 * and the only reason to widen is that the bars stop being legible. Where even the widest is
 * too fine — a logbook spanning centuries — the widest is used and the plot is crowded, which
 * is a true picture of the logbook rather than a refusal to draw it.
 */
internal fun fittedOf(
    set: ItemSet,
    across: Variable,
    up: Variable,
    gathering: Gathering,
    steps: List<Step>,
): Int {
    for ((at, step) in steps.withIndex()) {
        if (barsOf(set, across, up, gathering, step).size <= MOST) return at
    }
    return steps.size - 1
}

/**
 * The bars [gathering] makes of the dives in [set], left to right.
 *
 * A dive that does not answer the bottom axis is nowhere to put, and one that does not answer
 * the side is nothing to count, so both are left out. `GUI-30` has the reasoning: a plot of
 * what was not recorded reads as a reading.
 *
 * **A bucket nothing fell in is drawn at nought for a count or a total and left out for the
 * rest.** No dives in March is none made and no time spent, which are figures; it is not an
 * average depth of nought, which is a claim about diving that did not happen.
 */
internal fun barsOf(
    set: ItemSet,
    across: Variable,
    up: Variable,
    gathering: Gathering,
    step: Step,
): List<Bar> {
    val heaps = LinkedHashMap<Long, MutableList<Double>>()
    for (dive in divesMadeIn(set)) {
        val at = bucketOf(dive, across, step) ?: continue
        val value = if (gathering.reads) up.of(dive) ?: continue else 0.0
        heaps.getOrPut(at) { ArrayList() }.add(value)
    }
    if (heaps.isEmpty()) return emptyList()
    val first = heaps.keys.min()
    val last = heaps.keys.max()
    val out = ArrayList<Bar>()
    for (at in first..last) {
        val held = heaps[at]
        if (held == null && gathering != Gathering.COUNT && gathering != Gathering.TOTAL) continue
        val value = when (gathering) {
            Gathering.COUNT -> (held?.size ?: 0).toDouble()
            Gathering.TOTAL -> held?.sum() ?: 0.0
            Gathering.AVERAGE -> held!!.average()
            Gathering.LARGEST -> held!!.max()
            Gathering.SMALLEST -> held!!.min()
            else -> return emptyList()
        }
        out += Bar(edgeOf(at, step), edgeOf(at + 1, step), value, held?.size ?: 0)
    }
    return out
}

/** Which bucket a dive falls in, or absent where it does not say where it belongs. */
private fun bucketOf(dive: Item, across: Variable, step: Step): Long? = when (step) {
    is Step.Calendar -> across.dayOf?.invoke(dive)?.let { day ->
        val date = Date.ofEpochDay(day)
        val months = (date.year - 1970L) * Date.MONTHS_IN_YEAR + (date.month - 1)
        floorOf(months.toDouble(), step.months.toDouble())
    }

    is Step.Width -> across.of(dive)?.let { floorOf(it, step.size) }
}

/** Where a bucket begins, in what the bottom axis counts in. */
private fun edgeOf(at: Long, step: Step): Double = when (step) {
    is Step.Calendar -> {
        val months = at * step.months
        val year = 1970L + floorOf(months.toDouble(), Date.MONTHS_IN_YEAR.toDouble())
        val month = months - (year - 1970L) * Date.MONTHS_IN_YEAR
        yearOf(Date(year.toInt(), month.toInt() + 1, 1))
    }

    is Step.Width -> at * step.size
}

/** [value] divided by [size] and rounded down, which is not what integer division does below nought. */
private fun floorOf(value: Double, size: Double): Long = floor(value / size).toLong()

/**
 * Every dive in [set] as a point on a running total, earliest first.
 *
 * Each point carries what the logbook came to once that dive was made, so the line only ever
 * climbs and its last point is the figure the greeting gives. Nothing is bucketed: a running
 * total is about the order dives were made in rather than about any stretch of the axis.
 */
internal fun runningOf(set: ItemSet, across: Variable, up: Variable): List<Spot> {
    val spots = plottedOf(set, across, up).sortedBy { it.across }
    var sum = 0.0
    return spots.map { spot ->
        sum += spot.up
        Spot(spot.across, sum, spot.id, spot.title)
    }
}
