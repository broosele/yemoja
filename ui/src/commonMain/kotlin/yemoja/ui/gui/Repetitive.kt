package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.Moment
import yemoja.data.Time
import yemoja.data.ValueFormatException
import yemoja.logic.Earlier
import yemoja.logic.Residual
import yemoja.logic.SEA_LEVEL
import yemoja.logic.Universe
import yemoja.logic.earlierRuns
import yemoja.logic.endOf
import yemoja.logic.residualAfter
import yemoja.logic.runOf

/*
 * A planned dive's start, and the earlier run it follows. See ../../../../../../gui/doc.md —
 * `GUI-43`.
 */

/**
 * Following is the earlier run a plan follows: the dive it is on, and its key there.
 *
 * Immutable.
 */
internal data class Following(val dive: String, val key: String)

/** Start is when a plan begins as it is typed: not given, given, or given wrongly. */
internal sealed class Start {

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
internal fun startOf(shaping: Planned): Start {
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
internal sealed class Followed {

    object Fresh : Followed()

    class After(val residual: Residual.Done, val intervalSeconds: Long, val dive: Item, val following: Following) :
        Followed()

    data class Wrong(val reason: String) : Followed()
}

/**
 * What [shaping] starts from, in [universe]: fresh where it follows nothing, and otherwise what the
 * run it follows leaves after the interval between that dive's end and this start. `LOGIC-37`.
 */
internal fun followedOf(shaping: Planned, universe: Universe?): Followed {
    val following = shaping.following ?: return Followed.Fresh
    val logbook = universe?.logbook ?: return Followed.Wrong("After needs the logbook its dive is in open")
    val start = when (val read = startOf(shaping)) {
        Start.Unset -> return Followed.Wrong("Start is missing, and a plan following another needs one")
        is Start.Wrong -> return Followed.Wrong(read.reason)
        is Start.At -> read.moment
    }
    val dive = logbook[following.dive] ?: return Followed.Wrong("After should name a dive in this logbook")
    val ended = endOf(dive) ?: return Followed.Wrong("${diveSaid(dive)} should say when it ended")
    if (ended >= start) return Followed.Wrong("Start should be after ${momentSaid(ended)}, when ${diveSaid(dive)} ended")
    val run = runOf(logbook, KeyReference(following.key, following.dive))
        ?: return Followed.Wrong("After should name a run of ${diveSaid(dive)}")
    val interval = ended.secondsUntil(start)
    return when (val left = residualAfter(run, interval.toDouble(), SEA_LEVEL)) {
        is Residual.Done -> Followed.After(left, interval, dive, following)
        is Residual.Refused -> Followed.Wrong("${runSaid(dive, following.key)}: ${left.reason}")
    }
}

/** How far before a start the runs offered under *After* may have ended: two days. */
internal const val OFFERED_SECONDS = 2L * 24 * 60 * 60

/** How far before a start a dive that ended is worth a note: a day. */
internal const val NOTED_SECONDS = 24L * 60 * 60

/** The runs [shaping] may follow in [universe], latest first, or none before a start is given. */
internal fun offeredOf(shaping: Planned, universe: Universe?): List<Earlier> {
    val logbook = universe?.logbook ?: return emptyList()
    val start = (startOf(shaping) as? Start.At)?.moment ?: return emptyList()
    return earlierRuns(logbook, start, OFFERED_SECONDS)
}

/**
 * What the start row says after its boxes: the interval and what the plan starts with where it
 * follows a run, a note where it follows none but a dive ended in the day before, and nothing else.
 *
 * Examples: `Surface interval 1 hour and 43 minutes after 2026-10-03#0, Plan A; CNS 18 % at the
 * start`, and `2026-10-03#0 ended 1 hour and 43 minutes before this start: choose it under After if
 * this dive follows it`.
 */
internal fun followedSaid(shaping: Planned, universe: Universe?, followed: Followed): String? = when (followed) {
    is Followed.Wrong -> followed.reason
    is Followed.After ->
        "Surface interval ${waitSaid(followed.intervalSeconds.toDouble())} after " +
            "${runSaid(followed.dive, followed.following.key)}; CNS ${followed.residual.oxygen.percentCns.toInt()} % at the start"
    Followed.Fresh -> {
        val logbook = universe?.logbook
        val start = (startOf(shaping) as? Start.At)?.moment
        val latest = if (logbook == null || start == null) null else earlierRuns(logbook, start, NOTED_SECONDS).firstOrNull()
        latest?.let {
            val dive = logbook!![it.dive]!!
            "${diveSaid(dive)} ended ${waitSaid(it.ended.secondsUntil(start!!).toDouble())} before this start: " +
                "choose it under After if this dive follows it"
        }
    }
}

/** A run as the list under *After* names it: its dive, and the run by name. */
internal fun runSaid(dive: Item, key: String): String = "${diveSaid(dive)}, ${prettyOf(key)}"

private fun diveSaid(dive: Item): String = titleOf(dive)

private fun momentSaid(moment: Moment): String =
    "${moment.date} ${moment.time.hour.toString().padStart(2, '0')}:${moment.time.minute.toString().padStart(2, '0')}"

private fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

/**
 * The start row, under the plan's heading: when it begins, the run it follows, and what that comes
 * to. Its choice starts at *None* and is never made for the reader. `GUI-43`.
 */
@Composable
internal fun StartRow(shaping: Shaping, universe: Universe?, followed: Followed) {
    val planned = shaping.described()
    val offered = offeredOf(planned, universe)
    val chosen = shaping.following
    // The run already chosen stays offered when a new start leaves it out, so it can be seen and
    // undone rather than silently kept.
    val runs = offered.map { Following(it.dive, it.key) }.let { if (chosen != null && chosen !in it) it + chosen else it }
    val logbook = universe?.logbook
    fun labelOf(run: Following): String =
        logbook?.get(run.dive)?.let { runSaid(it, run.key) } ?: "${run.dive}, ${prettyOf(run.key)}"
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Explained(PlannerTips.DIVE_START) {
            Text("Start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        val wrongStart = startOf(planned) is Start.Wrong
        Box(modifier = Modifier.width(DATE_BOX)) {
            Compact(value = shaping.startDate, onChange = { shaping.startDate = it }, hint = "2026-10-03", dense = true, wrong = wrongStart)
        }
        Box(modifier = Modifier.width(TIME_BOX)) {
            Compact(value = shaping.startTime, onChange = { shaping.startTime = it }, hint = "14:30", dense = true, wrong = wrongStart)
        }
        Explained(PlannerTips.AFTER) {
            Text("After", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        // A dive is named in full, so this box is wider than a setting's and not the row's.
        Box(modifier = Modifier.width(FOLLOWED)) {
            Pick(
                dense = true,
                chosen = chosen?.let { labelOf(it) } ?: NO_EARLIER,
                options = listOf(NO_EARLIER) + runs.map { labelOf(it) },
            ) { picked -> shaping.following = if (picked == 0) null else runs[picked - 1] }
        }
        followedSaid(planned, universe, followed)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (followed is Followed.Wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** What *After* offers for following no run, which is a plan starting fresh. */
private const val NO_EARLIER = "None"

/** How wide the box naming the dive this plan follows is: a date, a number and a plan's name. */
private val FOLLOWED = 220.dp

private val DATE_BOX = 110.dp
private val TIME_BOX = 72.dp
