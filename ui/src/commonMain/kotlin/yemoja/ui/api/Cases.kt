package yemoja.ui.api

import yemoja.data.Stored
import kotlin.math.roundToInt
import yemoja.logic.NumberSetting
import yemoja.logic.Settings
import yemoja.ui.gui.Breathed
import yemoja.ui.gui.Planned
import yemoja.ui.gui.Role
import yemoja.ui.gui.Segment
import yemoja.ui.gui.clockOf
import yemoja.ui.gui.shownOf

/*
 * Plans written down as data, so a file of them can be asked for at once.
 *
 * See ../../../../../../ui/api/doc.md — `API-8`. The names are the manual's own, and everything
 * left out is the application's default, so a file comparing a table's column to ours is four
 * lines a case rather than twenty.
 */

/** Case is one plan in a file of them: what to call it, and the plan itself. */
internal class Case(val name: String, val planned: Planned)

/** Read is a file of cases read, or why the first that will not read does not. */
internal sealed class Read {

    class Cases(val cases: List<Case>) : Read()

    class Wrong(val reason: String) : Read()
}

/**
 * The cases [stored] holds: an array of them, or one on its own.
 *
 * A case names its lines and its cylinders and may name any setting. What it does not name the
 * application answers, which is what makes a file of table comparisons short.
 */
internal fun casesOf(stored: Stored): Read {
    val held = when (stored) {
        is Stored.Elements -> stored.elements
        is Stored.Members -> listOf(stored)
        is Stored.Leaf -> return Read.Wrong("a case file holds a plan or a list of them")
    }
    val cases = ArrayList<Case>()
    for ((index, one) in held.withIndex()) {
        val members = (one as? Stored.Members)?.members
            ?: return Read.Wrong("case ${index + 1} should be a plan, written as an object")
        val name = textOf(members["name"]) ?: "case ${index + 1}"
        when (val read = plannedOf(members, name)) {
            is Made.Wrong -> return Read.Wrong(read.reason)
            is Made.Plan -> cases += Case(name, read.planned)
        }
    }
    return Read.Cases(cases)
}

/** Made is one case read, or why it will not read. */
private sealed class Made {

    class Plan(val planned: Planned) : Made()

    class Wrong(val reason: String) : Made()
}

private fun plannedOf(members: Map<String, Stored>, name: String): Made {
    val lines = (members["lines"] as? Stored.Elements)?.elements
        ?: return Made.Wrong("$name should hold lines, written as a list")
    if (lines.isEmpty()) return Made.Wrong("$name has no lines")
    val segments = ArrayList<Segment>()
    for ((index, line) in lines.withIndex()) {
        val held = (line as? Stored.Members)?.members
            ?: return Made.Wrong("$name line ${index + 1} should be an object")
        segments += Segment(
            depth = textOf(held["depth"]).orEmpty(),
            // Seconds, as a logbook writes a time, or minutes and seconds as the form takes them.
            duration = textOf(held["duration"])?.let { written ->
                written.toDoubleOrNull()?.let { clockOf(it.roundToInt()) } ?: written
            }.orEmpty(),
            rate = textOf(held["rate"]).orEmpty(),
            // A cylinder is named by its number, as it is everywhere else here. `API-7`.
            gas = when (val named = textOf(held["gas"])) {
                null -> null
                else -> named.toIntOrNull()?.takeIf { it >= 1 }?.let { it - 1 }
                    ?: return Made.Wrong("$name line ${index + 1} gas should be a cylinder's number, not $named")
            },
        )
    }
    val cylinders = (members["gases"] as? Stored.Elements)?.elements ?: emptyList()
    val gases = ArrayList<Breathed>()
    for ((index, cylinder) in cylinders.withIndex()) {
        val held = (cylinder as? Stored.Members)?.members
            ?: return Made.Wrong("$name gas ${index + 1} should be an object")
        val said = textOf(held["role"])?.lowercase() ?: "bottom"
        val role = Role.entries.firstOrNull { it.name.lowercase() == said }
            ?: return Made.Wrong("$name gas ${index + 1} is $said, not bottom, deco or bailout")
        gases += Breathed(
            gas = textOf(held["gas"]).orEmpty(),
            role = role,
            size = textOf(held["size"]).orEmpty(),
            fill = textOf(held["fill"]).orEmpty(),
            sac = textOf(held["sac"]).orEmpty(),
        )
    }
    val beyond = segments.withIndex().firstOrNull { (_, line) ->
        line.gas != null && line.gas >= gases.ifEmpty { listOf(Breathed()) }.size
    }
    if (beyond != null) {
        return Made.Wrong(
            "$name line ${beyond.index + 1} gas should be one of the ${gases.size.coerceAtLeast(1)} " +
                "cylinders, not ${beyond.value.gas!! + 1}",
        )
    }
    // Written as a logbook writes the field and the setting, and turned into what the form takes: a
    // gradient factor from a proportion to a percentage, and a time from seconds to minutes.
    fun setting(key: String, held: NumberSetting): String {
        val written = textOf(members[key]) ?: return shownOf(held, held.default)
        return written.toDoubleOrNull()?.let { shownOf(held, it) } ?: written
    }
    // A percentage would be read as a proportion a hundred times too large, so it is refused.
    val factors = mapOf(
        "gradient_factor_low" to Settings.DEFAULT_GRADIENT_FACTOR_LOW,
        "gradient_factor_high" to Settings.DEFAULT_GRADIENT_FACTOR_HIGH,
    )
    for ((key, held) in factors) {
        val written = textOf(members[key]) ?: continue
        if (written.toDoubleOrNull()?.let { it in held.range } != true) {
            return Made.Wrong(
                "$name $key should be ${plainOf(held.range.start)} to ${plainOf(held.range.endInclusive)}, " +
                    "but was $written",
            )
        }
    }
    return Made.Plan(
        Planned(
            segments = segments,
            gases = gases.ifEmpty { listOf(Breathed()) },
            gradientLow = setting("gradient_factor_low", Settings.DEFAULT_GRADIENT_FACTOR_LOW),
            gradientHigh = setting("gradient_factor_high", Settings.DEFAULT_GRADIENT_FACTOR_HIGH),
            bottomOxygen = setting("po2_max_bottom", Settings.DEFAULT_PO2_MAX_BOTTOM),
            decoOxygen = setting("po2_max_deco", Settings.DEFAULT_PO2_MAX_DECO),
            leastOxygen = setting("po2_min", Settings.DEFAULT_PO2_MIN),
            descentRate = setting("descent_rate", Settings.DEFAULT_DESCENT_RATE),
            ascentRate = setting("ascent_rate", Settings.DEFAULT_ASCENT_RATE),
            safetyDepth = setting("safety_stop_depth", Settings.DEFAULT_SAFETY_STOP_DEPTH),
            safetyMinutes = setting("safety_stop_duration", Settings.DEFAULT_SAFETY_STOP_DURATION),
            lastStop = setting("last_stop", Settings.DEFAULT_LAST_STOP),
            water = textOf(members["water_type"]) ?: Settings.DEFAULT_WATER_TYPE.default,
        ),
    )
}

/**
 * [held] as the text a plan is described in.
 *
 * A number is taken as well as a string, so `40` and `"40"` say the same depth: a caller writing a
 * case file by hand should not have to remember which fields this application quotes.
 */
private fun textOf(held: Stored?): String? = when (val value = (held as? Stored.Leaf)?.value) {
    null -> null
    is Double -> plainOf(value)
    is Long -> value.toString()
    is String -> value.ifBlank { null }
    else -> value.toString()
}

/** A number with no trailing nought, which is how everything else here writes one. */
private fun plainOf(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
