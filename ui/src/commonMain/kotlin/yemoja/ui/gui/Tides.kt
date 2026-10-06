package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yemoja.data.Date
import yemoja.data.ItemSet
import yemoja.data.Moment
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.ValueFormatException
import yemoja.logic.Extreme
import yemoja.logic.Slack
import yemoja.logic.Tidal
import yemoja.logic.TideCalculator
import yemoja.logic.Tides
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The Tides form of the Calculations tab: a dive site, a tide model and a day, and under them the
 * day's high and low waters and the curve between.
 *
 * See ../../../../../../gui/doc.md — `GUI-55`. The calculators are the logic layer's, `LOGIC-44`.
 */

/**
 * Tiding is what the Tides form holds while the tab is open: what is chosen, and what each
 * calculator answered. `GUI-27`.
 *
 * Not immutable.
 */
internal class Tiding {
    /** The id of the site chosen, or absent until one is, the first listed then standing in. */
    var site: String? by mutableStateOf(null)

    /** The name of the model chosen, or absent for the most accurate one on offer. */
    var calculator: String? by mutableStateOf(null)

    /** The day as typed, and empty for today. */
    var day: String by mutableStateOf("")

    /** What each question came to, kept so that looking back at a day asks nobody again. */
    val answers = mutableStateMapOf<Asked, Tidal>()

    /** Whether a model that needs the network last found none, which greys every such model. */
    var offline: Boolean by mutableStateOf(false)
}

/** Asked is one question put to a calculator: which, where, and for what day. */
internal data class Asked(val calculator: String, val latitude: Double, val longitude: Double, val day: Date)

/** Sited is a dive site a tide can be asked for: its id, its name, and where it is. */
internal class Sited(val id: String, val title: String, val latitude: Double, val longitude: Double)

/**
 * The dive sites of [set] that have a position one of [calculators] covers, in the logbook's order.
 *
 * A site with no position cannot be asked about, and one no model covers would be offered only to
 * be refused.
 */
internal fun sitedIn(set: ItemSet, calculators: List<TideCalculator>): List<Sited> =
    entriesOf(set, Types.DIVE_SITE).mapNotNull { site ->
        val latitude = (site.item.read("latitude") as? Result.Usable)?.value as? Double ?: return@mapNotNull null
        val longitude = (site.item.read("longitude") as? Result.Usable)?.value as? Double ?: return@mapNotNull null
        if (calculators.none { it.covers(latitude, longitude) }) return@mapNotNull null
        Sited(site.id, site.title, latitude, longitude)
    }

/** The calculators answering for [site] on [day], the most accurate first. */
internal fun offeredFor(calculators: List<TideCalculator>, site: Sited, day: Date, today: Date): List<TideCalculator> =
    calculators.filter { it.covers(site.latitude, site.longitude) && it.coversDay(day, today) }
        .sortedByDescending { it.accuracy }

/**
 * The model the form asks: the one named in [chosen] where it is on offer, else the most accurate
 * that is not greyed, else the most accurate.
 */
internal fun calculatorOf(offered: List<TideCalculator>, chosen: String?, offline: Boolean): TideCalculator? =
    offered.firstOrNull { it.name == chosen }
        ?: offered.firstOrNull { !(offline && it.needsNetwork) }
        ?: offered.firstOrNull()

/** The day [typed] names, [today] where the box is empty, or absent where it names none. */
internal fun dayAsked(typed: String, today: Date): Date? = when {
    typed.isBlank() -> today
    else -> try {
        Date.parse(typed)
    } catch (refused: ValueFormatException) {
        null
    }
}

/** TideRow is one line of the table: a turn of the tide as the form writes it. */
internal data class TideRow(val turn: String, val time: String, val height: String, val difference: String)

/**
 * The table for [extremes] and [slacks]: each turn of the water and each turn of the current, in
 * the order of the day.
 *
 * A turn of the water says when, how high, and how far the water moved since the turn before; the
 * first has no turn before it and so no difference. A slack says when, and which way the current
 * sets after it, and has no height. A slack to be avoided says so in place of the way it sets.
 */
internal fun rowsOf(extremes: List<Extreme>, slacks: List<Slack> = emptyList()): List<TideRow> {
    val turns = extremes.mapIndexed { index, extreme ->
        val before = extremes.getOrNull(index - 1)
        extreme.at to TideRow(
            turn = if (extreme.high) "High water" else "Low water",
            time = clockOf(extreme.at.time),
            height = signedOf(extreme.height),
            difference = before?.let { hundredthsOf(abs(extreme.height - it.height)) }.orEmpty(),
        )
    }
    val turnings = slacks.map { slack ->
        val turn = when {
            slack.avoid -> if (slack.toFlood) "Flood: no dive" else "Ebb: no dive"
            slack.toFlood -> "Flood begins"
            else -> "Ebb begins"
        }
        slack.at to TideRow(turn, clockOf(slack.at.time), "", "")
    }
    return (turns + turnings).sortedBy { it.first }.map { it.second }
}

/** A distance in kilometres as the form writes it: to a tenth under ten, whole above. */
internal fun distanceOf(kilometres: Double): String = plain(kilometres, if (kilometres < TENTH_UNDER) 1 else 0)

/** The distance under which the form gives a tenth of a kilometre. */
private const val TENTH_UNDER = 10.0

/** What the form says under a station's tide. */
internal const val STATION_CAUTION: String =
    "The station's tide, not the site's: the water at a site turns earlier or later, and slack " +
        "water is not the same moment as high or low water. Wind and air pressure move the real " +
        "tide away from a prediction. Check local knowledge before a dive that depends on it."

/** What the form says under slack reckoned from a club's table of offsets. */
internal const val TABLE_CAUTION: String =
    "Slack from a dive club's table: the reference station's predicted high and low water, moved by " +
        "the minutes its divers found the current least at the site. \"No dive\" marks a turn the club " +
        "says not to dive at; it is shown at the station's time. The club accepts no liability for " +
        "deviations, and wind and air pressure move the real tide away from a prediction. Check local " +
        "knowledge before a dive that depends on it."

/** What the form says under [tides], by what they hold. */
internal fun cautionOf(tides: Tides): String = when {
    tides.flows.isNotEmpty() -> MODEL_CAUTION
    tides.slacks.isNotEmpty() -> TABLE_CAUTION
    else -> STATION_CAUTION
}

/** What the form says under a model's tide and current at the site. */
internal const val MODEL_CAUTION: String =
    "A model's figures for the site, with the weather in them. Flood is the current running in as " +
        "the water rises, ebb running out, and slack the moment it turns. A model can be wrong by " +
        "half an hour or more, and close to the bottom or behind a pier the water runs otherwise. " +
        "Check local knowledge before a dive that depends on it."

/** What the form says of a curve that is a gauge's as far as [until] and a forecast after. */
internal fun measuredSaid(until: Moment): String =
    "Measured until ${clockOf(until.time)}, forecast after that. The plot draws the forecast dashed."

/** A time as a tide table writes it, to the minute. */
internal fun clockOf(time: Time): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** A height against a datum, to the centimetre and with its sign, since half of them are below it. */
internal fun signedOf(metres: Double): String {
    val written = hundredthsOf(abs(metres))
    return when {
        written == "0.00" -> written
        metres < 0 -> "−$written"
        else -> "+$written"
    }
}

/** A length to the centimetre, both decimals always written so a column lines up. */
private fun hundredthsOf(metres: Double): String {
    val hundredths = (metres * HUNDREDTHS).roundToLong()
    return "${hundredths / HUNDREDTHS.toLong()}.${(hundredths % HUNDREDTHS.toLong()).toString().padStart(2, '0')}"
}

private const val HUNDREDTHS = 100.0

/**
 * A dive site, a model and a day, and the day's tide under them.
 *
 * [calculators] are what the platform can ask; where there are none the form says so rather than
 * offering a choice of nothing. A model is asked off the screen's thread, the ones built reaching
 * over a network. `GUI-55`.
 */
@Composable
internal fun TidesForm(tiding: Tiding, universe: Universe?, calculators: List<TideCalculator>, today: Date) {
    Heading("Tides")
    Aside("High and low water on a day near a dive site, and the current where a model gives it.")
    if (calculators.isEmpty()) {
        Aside("No tide model is available here.")
        return
    }
    if (universe == null) {
        Aside("Open a logbook to choose one of its dive sites.")
        return
    }
    val sites = sitedIn(universe.logbook, calculators)
    if (sites.isEmpty()) {
        Aside("No dive site in this logbook has a position that a tide model covers.")
        return
    }
    val site = sites.firstOrNull { it.id == tiding.site } ?: sites.first()
    val day = dayAsked(tiding.day, today)
    val offered = if (day == null) emptyList() else offeredFor(calculators, site, day, today)
    val calculator = calculatorOf(offered, tiding.calculator, tiding.offline)

    Choice("Dive site") {
        Picked(sites.map { it.title }, sites.indexOf(site)) { tiding.site = sites[it].id }
    }
    Choice("Model") {
        if (calculator == null) {
            if (day != null) Aside("None covers this day.")
        } else {
            Picked(
                labels = offered.map { "${it.name} (${it.accuracy.label})" },
                chosen = offered.indexOf(calculator),
                greyed = offered.indices.filter { tiding.offline && offered[it].needsNetwork }.toSet(),
            ) { tiding.calculator = offered[it].name }
        }
    }
    Choice("Day") {
        Box(modifier = Modifier.width(FIGURE)) {
            Compact(
                value = tiding.day,
                onChange = { tiding.day = it },
                hint = today.toString(),
                derived = true,
                wrong = day == null,
            )
        }
    }
    if (day == null) {
        Refused("Day should be a date written 2026-02-23, not \"${tiding.day.trim()}\"")
        return
    }
    if (calculator == null) return

    val asked = Asked(calculator.name, site.latitude, site.longitude, day)
    val answer = tiding.answers[asked]
    LaunchedEffect(asked, answer == null) {
        if (answer != null) return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { calculator.tides(site.latitude, site.longitude, day) }
        tiding.offline = found is Tidal.Offline
        tiding.answers[asked] = found
    }
    when (answer) {
        null -> Aside("Asking…")
        is Tidal.Offline -> {
            Refused(answer.reason)
            // Forgetting the answer is what asks again: the effect above runs for a question with none.
            TextButton(onClick = { tiding.answers.remove(asked) }) { Text("Try again") }
        }
        is Tidal.None -> Refused(answer.reason)
        is Tidal.Found -> TideAnswer(answer.tides)
    }
}

/** One labelled choice of the form, its name where a box's name stands. */
@Composable
private fun Choice(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        content()
    }
}

/** What a model answered: which station spoke, the turns as a table, and the day's curve. */
@Composable
private fun TideAnswer(tides: Tides) {
    Aside(
        "${tides.station}, ${distanceOf(tides.kilometres)} km from the site. Times in ${tides.clock}, " +
            "heights in metres against ${tides.datum}.",
    )
    tides.measuredUntil?.let { Aside(measuredSaid(it)) }
    val rows = rowsOf(tides.extremes, tides.slacks)
    if (rows.isEmpty()) {
        Aside("The water did not turn on this day in what the model holds.")
    } else {
        Lined(TideRow("", "Time", "Height", "Difference"), heading = true)
        HorizontalDivider(modifier = Modifier.widthIn(max = TABLE))
        for (row in rows) Lined(row, heading = false)
    }
    if (tides.curve.size > 1) {
        DayPlot(
            points = tides.curve.map { it.at to it.height },
            marks = tides.extremes.map { it.at to it.height },
            dashedAfter = tides.measuredUntil,
            above = "Height (m)",
            below = null,
        )
    }
    if (tides.flows.size > 1) {
        DayPlot(
            points = tides.flows.map { it.at to it.speed },
            marks = tides.slacks.map { it.at to 0.0 },
            dashedAfter = null,
            above = "Flood (m/s)",
            below = "Ebb",
        )
    }
    tides.remark?.let { Aside(it) }
    Aside(cautionOf(tides))
}

/** One line of the table, its figures drawn as calculated values are. */
@Composable
private fun Lined(row: TideRow, heading: Boolean) {
    val base = MaterialTheme.typography.bodyMedium
    val quiet = base.copy(color = MaterialTheme.colorScheme.outline)
    val figure = if (heading) quiet else calculatedOf(base)
    // A phone's screen is narrower than the table at its desktop widths, so each column is cut to
    // what its widest entry needs. `PHONE-2`.
    val widths = if (LocalCompact.current) COMPACT_CELLS else CELLS
    Row(
        modifier = Modifier.padding(horizontal = GAP, vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(text = row.turn, style = if (heading) quiet else base, maxLines = 1, modifier = Modifier.width(widths[0]))
        for ((at, cell) in listOf(row.time, row.height, row.difference).withIndex()) {
            Text(
                text = cell,
                style = figure,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(widths[at + 1]),
            )
        }
    }
}

/**
 * A value through the day against the hour, with a dot on each of [marks].
 *
 * Across is the whole day whatever [points] cover, so a forecast that stops at noon is seen to stop
 * there. What comes after [dashedAfter] is a forecast and is drawn dashed, joined to the last
 * reading before it so the step between the two is seen as a step and not as a gap.
 *
 * [above] titles the plot's top, and [below], where there is one, its bottom: a signed value is
 * read by which side of nought it stands, so nought is drawn as a line of its own.
 */
@Composable
private fun DayPlot(
    points: List<Pair<Moment, Double>>,
    marks: List<Pair<Moment, Double>>,
    dashedAfter: Moment?,
    above: String,
    below: String?,
) {
    val ink = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axisInk = MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.surface
    val label = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier = Modifier.widthIn(max = PLOT_WIDE).fillMaxWidth().height(PLOT_HIGH).padding(vertical = HALF)
            .drawWithCache {
                val left = AXIS.toPx()
                val right = size.width - GAP.toPx()
                val top = HALF.toPx()
                val bottom = size.height - FOOT.toPx()
                val values = points.map { it.second } + marks.map { it.second }
                // A signed value is drawn with nought in view, so a current that runs one way all
                // day is still read against the other.
                val heights = rangeOf(if (below == null) values else values + 0.0)
                fun x(time: Time): Float =
                    left + (right - left) * time.secondOfDay / Time.SECONDS_IN_DAY.toFloat()

                fun y(height: Double): Float {
                    val part = (height - heights.start) / (heights.endInclusive - heights.start)
                    return (bottom - (bottom - top) * part).toFloat()
                }

                val side = ticksOf(heights.start, heights.endInclusive, 5)
                fun pathOf(line: List<Pair<Moment, Double>>): Path = Path().also { path ->
                    for ((at, point) in line.withIndex()) {
                        val placed = Offset(x(point.first.time), y(point.second))
                        if (at == 0) path.moveTo(placed.x, placed.y) else path.lineTo(placed.x, placed.y)
                    }
                }

                val read = if (dashedAfter == null) points else points.filter { it.first <= dashedAfter }
                val drawn = pathOf(read)
                val forecast = pathOf(read.takeLast(1) + points.drop(read.size))
                val topTitle = measurer.measure(above, label)
                val bottomTitle = below?.let { measurer.measure(it, label) }
                val dashes = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))
                onDrawBehind {
                    for (tick in side) {
                        val at = y(tick)
                        drawLine(grid, Offset(left, at), Offset(right, at), THIN.toPx())
                        val laid = measurer.measure(shortOf(tick), label)
                        drawText(laid, topLeft = Offset(left - laid.size.width - HALF.toPx(), at - laid.size.height / 2f))
                    }
                    for (hour in 0..Time.HOURS_IN_DAY step HOURS_A_MARK) {
                        val at = left + (right - left) * hour / Time.HOURS_IN_DAY.toFloat()
                        drawLine(grid, Offset(at, top), Offset(at, bottom), THIN.toPx())
                        val laid = measurer.measure(hour.toString().padStart(2, '0'), label)
                        drawText(laid, topLeft = Offset(at - laid.size.width / 2f, bottom + 2f))
                    }
                    if (below != null) drawLine(axisInk, Offset(left, y(0.0)), Offset(right, y(0.0)), CURVE_LINE.toPx() / 2)
                    drawPath(drawn, ink, style = Stroke(width = CURVE_LINE.toPx()))
                    if (dashedAfter != null) {
                        drawPath(forecast, ink, style = Stroke(width = CURVE_LINE.toPx(), pathEffect = dashes))
                    }
                    for ((at, value) in marks) {
                        drawCircle(ink, DOT.toPx(), Offset(x(at.time), y(value)))
                    }
                    // On the ground's own colour, so a curve running under a title does not cross it.
                    fun titled(laid: TextLayoutResult, corner: Offset) {
                        drawRect(ground, corner, Size(laid.size.width.toFloat(), laid.size.height.toFloat()))
                        drawText(laid, topLeft = corner)
                    }
                    titled(topTitle, Offset(right - topTitle.size.width, top))
                    bottomTitle?.let { titled(it, Offset(right - it.size.width, bottom - it.size.height)) }
                }
            },
    )
}

/** How wide the table's four columns are: the turn, the time, the height and the difference. */
private val CELLS = listOf(100.dp, 80.dp, 80.dp, 80.dp)

/** The same four on a phone, which add up to what a screen 360 wide has left. */
private val COMPACT_CELLS = listOf(84.dp, 52.dp, 60.dp, 80.dp)

/** How wide the table is, which its rule is drawn to. */
private val TABLE = 400.dp

private val PLOT_HIGH = 220.dp

/** The widest the curve is drawn: a day stretched across a whole window says nothing more. */
private val PLOT_WIDE = 640.dp

/** The room left of the plot for the heights. */
private val AXIS = 40.dp

/** The room under the plot for the hours. */
private val FOOT = 16.dp

private val CURVE_LINE = 2.dp

/** How long a dash of the forecast's line is, and the gap after it. */
private val DASH = 5.dp
private val THIN = 1.dp
private val DOT = 4.dp

/** How many hours stand between two marks along the day. */
private const val HOURS_A_MARK = 3
