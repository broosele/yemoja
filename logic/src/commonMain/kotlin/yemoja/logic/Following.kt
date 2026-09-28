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

/** When [dive] ended, in its local time, or null where it does not say. */
fun endOf(dive: Item): Moment? {
    val date = (dive.single<Date>("end_date") as? Result.Usable)?.value ?: return null
    val time = (dive.single<Time>("end_time") as? Result.Usable)?.value ?: return null
    return Moment(date, time)
}

/** The run [reference] names, as `@2026-09-20#0*b`, or null where the logbook holds none. */
fun runOf(logbook: ItemSet, reference: KeyReference): Item? {
    val dive = reference.id?.let { logbook[it] } ?: return null
    val runs = (dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value ?: return null
    return (runs[reference.key] as? Element.Usable)?.value
}
