package yemoja.logic

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.KeyReference
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.ValueFormatException

/*
 * The runs a planned dive may follow. See ../../../../../doc.md — `LOGIC-37`.
 */

/**
 * Earlier is a run a planned dive may follow: the dive it is on, its key there, and when that dive
 * ended.
 *
 * [ended] is the dive's own end, in its local time, which is what a surface interval is measured
 * from whichever of its runs is followed, as `surface_interval` measures it.
 *
 * Immutable.
 */
class Earlier(
    val dive: String,
    val key: String,
    val planned: Boolean,
    /** Whether this is the run the dive is worked from. */
    val primary: Boolean,
    val ended: Moment,
)

/**
 * Every run on a dive that ended before [start] and no more than [withinSeconds] before it, the
 * latest dive first and its primary run first among its own.
 *
 * Local times are compared as they are written, so a plan's start is read in the time of the dive
 * it follows. A dive that says nothing of when it ended is left out, there being no interval to
 * measure. `LOGIC-37`.
 */
fun earlierRuns(logbook: ItemSet, start: Moment, withinSeconds: Long): List<Earlier> {
    val found = ArrayList<Earlier>()
    for (dive in logbook.allOf(Types.DIVE)) {
        val id = logbook.idOf(dive) ?: continue
        val ended = endOf(dive) ?: continue
        if (ended >= start || ended.secondsUntil(start) > withinSeconds) continue
        val primary = (primaryProfile(dive) as? Result.Usable)?.value
        val runs = (dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value.orEmpty()
        for ((key, entry) in runs) {
            val run = (entry as? Element.Usable)?.value ?: continue
            val planned = (run.single<Boolean>("planned") as? Result.Usable)?.value == true
            found += Earlier(id, key, planned, run === primary, ended)
        }
    }
    return found.sortedWith(compareByDescending<Earlier> { it.ended }.thenBy { !it.primary }.thenBy { it.key })
}

/**
 * When [dive] ended, in its local time, or null where it does not say.
 *
 * Its start and how long it ran. A dive that crossed midnight needs nothing said about the day,
 * which is what a stored end could never settle. `DATA-125`.
 */
fun endOf(dive: Item): Moment? {
    val date = (dive.single<Date>("start_date") as? Result.Usable)?.value ?: return null
    val time = (dive.single<Time>("start_time") as? Result.Usable)?.value ?: return null
    val ran = (dive.single<Double>("duration") as? Result.Usable)?.value ?: return null
    return Moment(date, time).plusSeconds(ran.toLong())
}

/** The run [reference] names, as `@2026-09-20#0*b`, or null where the logbook holds none. */
fun runOf(logbook: ItemSet, reference: KeyReference): Item? {
    val dive = reference.id?.let { logbook[it] } ?: return null
    val runs = (dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value ?: return null
    return (runs[reference.key] as? Element.Usable)?.value
}

/**
 * Following is the earlier run a plan follows: the dive it is on, and its key there.
 *
 * Immutable.
 */
data class Following(val dive: String, val key: String)

/** Start is when a plan begins as it is typed: not given, given, or given wrongly. */
sealed class Start {

    object Unset : Start()

    data class At(val moment: Moment) : Start()

    data class Wrong(val reason: String) : Start()
}

/**
 * When [shaping] begins, read from its two boxes.
 *
 * Both empty is no start, which a plan following nothing does not need. A time is written `14:30`
 * or `14:30:00`.
 */
fun startOf(shaping: Planned): Start {
    val date = shaping.startDate.trim()
    val time = shaping.startTime.trim()
    if (date.isEmpty() && time.isEmpty()) return Start.Unset
    if (date.isEmpty()) return Start.Wrong("Start date is missing")
    if (time.isEmpty()) return Start.Wrong("Start time is missing")
    val day = try {
        Date.parse(date)
    } catch (refused: ValueFormatException) {
        return Start.Wrong("Start date should be a date such as 2026-10-03, not ${said(shaping.startDate)}")
    }
    val clock = try {
        Time.parse(if (time.count { it == ':' } == 1) "$time:00" else time)
    } catch (refused: ValueFormatException) {
        return Start.Wrong("Start time should be a time such as 14:30, not ${said(shaping.startTime)}")
    }
    return Start.At(Moment(day, clock))
}

/** Followed is what a plan starts from: fresh, after an earlier run, or why it cannot be said. */
sealed class Followed {

    object Fresh : Followed()

    class After(val residual: Residual.Done, val intervalSeconds: Long, val dive: Item, val following: Following) :
        Followed()

    data class Wrong(val reason: String) : Followed()
}

/**
 * What [shaping] starts from, in [universe]: fresh where it follows nothing, and otherwise what the
 * run it follows leaves after the interval between that dive's end and this start. `LOGIC-37`.
 */
fun followedOf(shaping: Planned, universe: Universe?): Followed {
    val following = shaping.following ?: return Followed.Fresh
    val logbook = universe?.logbook ?: return Followed.Wrong("After needs the logbook its dive is in open")
    val start = when (val read = startOf(shaping)) {
        Start.Unset -> return Followed.Wrong("Start is missing, and a plan following another needs one")
        is Start.Wrong -> return Followed.Wrong(read.reason)
        is Start.At -> read.moment
    }
    val dive = logbook[following.dive] ?: return Followed.Wrong("After should name a dive in this logbook")
    val ended = endOf(dive) ?: return Followed.Wrong("${titleOf(dive)} should say when it ended")
    if (ended >= start) return Followed.Wrong("Start should be after ${momentSaid(ended)}, when ${titleOf(dive)} ended")
    val run = runOf(logbook, KeyReference(following.key, following.dive))
        ?: return Followed.Wrong("After should name a run of ${titleOf(dive)}")
    val interval = ended.secondsUntil(start)
    return when (val left = residualAfter(run, interval.toDouble(), SEA_LEVEL)) {
        is Residual.Done -> Followed.After(left, interval, dive, following)
        is Residual.Refused -> Followed.Wrong("${titleOf(dive)}, ${prettyOf(following.key)}: ${left.reason}")
    }
}

/** A moment, as a start or an end is said: the date, and the time to the minute. */
private fun momentSaid(moment: Moment): String =
    "${moment.date} ${moment.time.hour.toString().padStart(2, '0')}:${moment.time.minute.toString().padStart(2, '0')}"

/** A key as a reader sees it: a word on its own capitalised, `plan_a` shown as `Plan a`. */
private fun prettyOf(key: String): String = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
