package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.logic.Ascended
import yemoja.logic.Change
import yemoja.logic.Evaluated
import yemoja.logic.Refusal
import yemoja.logic.Severity

/*
 * What the model says about a run, as a screen needs it: a line to draw over the profile, other
 * lines for the right axis, figures to read beside it, and the findings in words.
 *
 * Worked out without a screen. The arithmetic is the logic layer's — `LOGIC-37` — and none of it
 * is repeated here: this turns one answer into what a reader sees, and turns an ascent into the
 * changes that write it down.
 *
 * See ../../../../../../gui/doc.md — `GUI-40`.
 */

/**
 * The ceiling as a line over the depth graph, or null where the run never owed a stop.
 *
 * Drawn on the depth axis rather than the right one, because it is a depth: the water above it is
 * water the model says not to ascend into, and a reader compares it against the line they swam by
 * looking at the gap. A run owing nothing has a ceiling of nought throughout, and a flat line
 * along the surface tells a reader only that the graph has an extra line in it.
 */
internal fun ceilingLineOf(evaluated: Evaluated.Done): Line? {
    val points = pointsOf(evaluated.ceiling)
    if (points.none { it.value > 0 }) return null
    return Line("Ceiling", points, main = false)
}

/**
 * What the model works out that the right axis can show, in the order offered.
 *
 * Each says it was worked out, because a recording may carry the computer's own beside it and the
 * two disagree on purpose. `manual/decompression.md` says why: the device decided at the time,
 * with settings this cannot reproduce, and this is a second opinion arrived at afterwards.
 */
internal fun workedOverlaysOf(dive: Item, profile: Item, evaluated: Evaluated.Done): List<Overlay> {
    val overlays = ArrayList<Overlay>()
    val limits = pointsOf(evaluated.noDecompressionTime, 1.0 / 60.0)
    if (limits.isNotEmpty()) {
        overlays += Overlay(
            "NDL worked out",
            "min",
            Line("NDL worked out", limits.map { Point(it.minute, minOf(it.value, NO_DECO_CAP)) }),
        )
    }
    val sources = sourcesOf(dive, profile)
    for ((key, series) in evaluated.pressures) {
        val tank = sources[key]?.let { entryLabelOf(key, it) } ?: key
        overlays += Overlay("$tank worked out", "bar", Line(tank, pointsOf(series)))
    }
    overlays += Overlay("CNS worked out", "%", Line("CNS", pointsOf(evaluated.cns)))
    overlays += Overlay("OTU worked out", "", Line("OTU", pointsOf(evaluated.otu)))
    return overlays
}

/**
 * What the model has to say about the run in figures: the deepest stop it asks for, what each
 * cylinder gives up and ends at, the two oxygen clocks, and the two waits it leaves behind.
 *
 * Every one of them says it was worked out. A figure the run gives nothing to work from is left
 * out rather than shown empty: a cylinder with no fill written on it has no gauge to read, and
 * saying so on every plan would be a column of blanks.
 */
internal fun workedFiguresOf(dive: Item, profile: Item, evaluated: Evaluated.Done): List<Shown> {
    val sources = sourcesOf(dive, profile)
    return runFiguresOf(
        evaluated,
        evaluated.gasUsed.keys.associateWith { key ->
            sources[key]?.let { entryLabelOf(key, it) } ?: key
        },
    )
}

/**
 * The same figures for a run that belongs to no dive, its cylinders named by [tanks].
 *
 * One list of figures rather than two: a plan typed into the calculations and a plan on a dive are
 * answered by one walk, `LOGIC-37`, and what a reader is shown of that answer should not depend on
 * which door it came through. What differs is only what the cylinders are called, which a dive
 * knows and a run does not. `GUI-43`.
 */
internal fun runFiguresOf(
    evaluated: Evaluated.Done,
    tanks: Map<String, String>,
    /** Whether to say how deep the stops begin, which a screen showing each of them says twice. */
    stops: Boolean = true,
): List<Shown> {
    val figures = ArrayList<Shown>()
    if (stops) {
        val deepest = pointsOf(evaluated.ceiling).maxOfOrNull { it.value } ?: 0.0
        figures += worked(
            "Stops",
            if (deepest > 0) "from ${metresSaid(deepest)}" else "none",
        )
    }
    for ((key, litres) in evaluated.gasUsed) {
        val tank = tanks[key] ?: key
        val left = evaluated.pressures[key]?.let { ending(it) }
        val said = "${litres.toInt()} l" + if (left == null) "" else ", ending at $left"
        figures += worked("$tank used", said)
    }
    figures += worked("CNS", "${evaluated.oxygen.percentCns.toInt()}%")
    figures += worked("OTU", "${evaluated.oxygen.otu.toInt()}")
    evaluated.noFlight?.let { figures += worked("No-fly time", waitSaid(it)) }
    evaluated.desaturation?.let { figures += worked("Desaturation time", waitSaid(it)) }
    return figures
}

/**
 * What the model objects to, each as a line: when it happened, and what it is.
 *
 * Nothing where it objects to nothing, which is what a plan is adjusted until it says. A warning
 * reads as a value that would not read does, in the error colour, because both are the screen
 * telling a reader that something here is wrong. `GUI-8`.
 */
internal fun findingsSaidOf(evaluated: Evaluated.Done): List<Shown> =
    evaluated.findings.map { finding ->
        Shown(
            atSaid(finding.second),
            listOf(Part(finding.said)),
            wrong = finding.severity == Severity.WARNING,
            worked = true,
        )
    }

/**
 * A run the model would not answer for, as a reader is told anything it cannot work out, or null
 * where there is nothing worth saying.
 *
 * **A recording is told only of a fault.** Most carry nothing to work from — a computer writes no
 * gradient factors — and a red line under every dive would say only that the model was not asked,
 * which teaches a reader to skip the place a real fault appears. A cycle somebody made by hand or
 * a run whose gas nothing names is a different thing, and is said wherever it is found.
 *
 * **A plan is told of both**, because a plan exists to be answered and one that cannot be has
 * something missing that its writer meant to supply.
 */
internal fun refusedSaidOf(refused: Evaluated.Refused, planned: Boolean): Shown? {
    if (!planned && refused.why != Refusal.FAULTY) return null
    return Shown("Decompression", listOf(Part(refused.reason)), wrong = true)
}

/**
 * The changes that write [ascent] into [profile]: its depths carried on to the end of the run, and
 * its switches on to the end of the switches.
 *
 * **Appended, never replacing.** What the reader wrote is the dive they intend and the ascent is
 * the way out of it, so the two are one series afterwards and nothing can tell which part was
 * typed. That is the point: a plan holds points and not a recipe, `LOGIC-35`.
 *
 * Empty where the ascent is empty, a run already at the surface having no way up to write.
 */
internal fun ascentWrittenTo(profile: Item, ascent: Ascended.Done): List<Change> {
    if (ascent.depth.isEmpty()) return emptyList()
    val changes = ArrayList<Change>()
    val depth = samplesOf(profile, "depth") + ascent.depth.map { (second, metres) ->
        sample(second, metres)
    }
    changes += Change.Write(profile, "depth", Stored.Elements(depth))
    if (ascent.switches.isNotEmpty()) {
        val switches = samplesOf(profile, "gas_switches") + ascent.switches.map { (second, key) ->
            sample(second, "*$key")
        }
        changes += Change.Write(profile, "gas_switches", Stored.Elements(switches))
    }
    return changes
}

/** Whether [profile] is one somebody intends rather than one a computer wrote. */
internal fun isPlanned(profile: Item): Boolean =
    (profile.single<Boolean>("planned") as? Result.Usable)?.value == true

/** A figure nobody wrote, which the screen marks as worked out wherever it shows one. */
private fun worked(label: String, said: String): Shown =
    Shown(label, listOf(Part(said)), worked = true)

/** Where a finding sits in the run, as a reader counts: minutes and seconds from the start. */
private fun atSaid(second: Int): String =
    "At ${second / 60}:${(second % 60).toString().padStart(2, '0')}"

/** A depth to a tenth of a metre, which is as fine as a stop is ever read. */
private fun metresSaid(depth: Double): String {
    val tenths = (depth * 10).toLong()
    return "${tenths / 10}.${tenths % 10} m"
}

/** A wait as a reader says one, and *none* where there is nothing to wait for. */
private fun waitSaid(seconds: Double): String =
    if (seconds <= 0) "none" else spanOf(seconds)

/** What a projected gauge ends at, as bar, and *empty* where the run asks for more than it holds. */
private fun ending(pressures: Series): String? {
    val last = pointsOf(pressures).lastOrNull()?.value ?: return null
    return if (last <= 0) "empty" else "${last.toInt()} bar"
}

/** The gas sources a run breathes from: its own where it keeps any, and the dive's otherwise. */
@Suppress("UNCHECKED_CAST")
private fun sourcesOf(dive: Item, profile: Item): Map<String, OwnedItem> {
    val root = profile.rootOf("gas_sources") ?: dive
    val read = (root.read("gas_sources") as? Result.Usable)?.value as? Map<String, Element<Any>>
    return read.orEmpty().mapNotNull { (key, element) ->
        ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
    }.toMap()
}

/** What a series holds, as a file writes it, so an ascent can be added to the end of it. */
private fun samplesOf(profile: Item, field: String): List<Stored> {
    val series = (profile.read(field) as? Result.Usable)?.value as? Series ?: return emptyList()
    return (0..<series.size).mapNotNull { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value ?: return@mapNotNull null
        sample(series.secondAt(at), if (value is Double) value else value.toString())
    }
}

private fun sample(second: Int, value: Any): Stored =
    Stored.Elements(listOf(Stored.Leaf(second), Stored.Leaf(value)))

/** A series as points, its seconds as minutes and each value scaled. */
private fun pointsOf(series: Series, scale: Double = 1.0): List<Point> =
    (0..<series.size).mapNotNull { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value as? Number
            ?: return@mapNotNull null
        Point(series.secondAt(at) / 60.0, value.toDouble() * scale)
    }
