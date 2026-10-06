package yemoja.logic

import yemoja.data.Stored
import kotlin.math.roundToInt

/*
 * Plans written down as data, so a file of them can be asked for at once.
 *
 * See ../../../../../../ui/api/doc.md — `API-8`. The names are the manual's own, and everything
 * left out is the application's default, so a file comparing a table's column to ours is four
 * lines a case rather than twenty.
 */

/** Case is one plan in a file of them: what to call it, the plan itself, and what it comes after. */
class Case(val name: String, val planned: Planned, val follows: Follows? = null)

/**
 * Follows is which earlier case a case comes after, by its position in the file, and the surface
 * interval between the two in seconds. `LOGIC-43`.
 */
class Follows(val earlier: Int, val intervalSeconds: Double)

/** Read is a file of cases read, or why the first that will not read does not. */
sealed class Read {

    class Cases(val cases: List<Case>) : Read()

    class Wrong(val reason: String) : Read()
}

/**
 * The cases [stored] holds: an array of them, or one on its own.
 *
 * A case names its lines and its cylinders and may name any setting. What it does not name the
 * application answers, which is what makes a file of table comparisons short.
 */
fun casesOf(stored: Stored): Read {
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
        // Only a case already read can be followed, so order and existence are one check.
        val named = textOf(members["follows"])
        val interval = textOf(members["surface_interval"])
        val follows: Follows?
        if (named == null) {
            if (interval != null) {
                return Read.Wrong("$name has a surface_interval but no follows, so there is nothing it comes after")
            }
            follows = null
        } else {
            val earlier = cases.indexOfFirst { it.name == named }
            if (earlier < 0) return Read.Wrong("$name follows $named, which is no earlier case in this file")
            if (interval == null) {
                return Read.Wrong("$name follows $named, but nothing says how long the surface interval was")
            }
            val seconds = interval.toDoubleOrNull()
            if (seconds == null || seconds < 0) {
                return Read.Wrong("$name surface_interval should be 0 seconds or more, but was $interval")
            }
            follows = Follows(earlier, seconds)
        }
        when (val read = plannedOf(members, name)) {
            is Made.Wrong -> return Read.Wrong(read.reason)
            is Made.Plan -> cases += Case(name, read.planned, follows)
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
    val lines = (members["runtime"] as? Stored.Elements)?.elements
        ?: return Made.Wrong("$name should hold a runtime, written as a list of lines")
    if (lines.isEmpty()) return Made.Wrong("$name's runtime is empty")
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
        val gas = line.gas
        gas != null && gas >= gases.ifEmpty { listOf(Breathed()) }.size
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
    val oxygenNarcotic = when (val written = (members["oxygen_narcotic"] as? Stored.Leaf)?.value) {
        null -> Settings.DEFAULT_OXYGEN_NARCOTIC.default
        is Boolean -> written
        else -> return Made.Wrong("$name oxygen_narcotic should be true or false, but was $written")
    }
    // The diluent is a cylinder named by its number, as everything here names one. `API-7`.
    val diluent = textOf(members["diluent"])?.let { written ->
        written.toIntOrNull()?.takeIf { it >= 1 }?.let { it - 1 }
            ?: return Made.Wrong("$name diluent should be a cylinder's number, not $written")
    } ?: 0
    val switchStops = when (val written = (members["gas_switch_stops"] as? Stored.Leaf)?.value) {
        null -> false
        is Boolean -> written
        else -> return Made.Wrong("$name gas_switch_stops should be true or false, but was $written")
    }
    val bailoutReserve = when (val written = (members["bailout_reserve"] as? Stored.Leaf)?.value) {
        null -> true
        is Boolean -> written
        else -> return Made.Wrong("$name bailout_reserve should be true or false, but was $written")
    }
    val sharedGasReserve = when (val written = (members["shared_gas_reserve"] as? Stored.Leaf)?.value) {
        null -> true
        is Boolean -> written
        else -> return Made.Wrong("$name shared_gas_reserve should be true or false, but was $written")
    }
    val lostGasReserve = when (val written = (members["lost_gas_reserve"] as? Stored.Leaf)?.value) {
        null -> true
        is Boolean -> written
        else -> return Made.Wrong("$name lost_gas_reserve should be true or false, but was $written")
    }
    // The cylinder it loses, by its number counted from 1 as every cylinder here is. Left out, the
    // first deco cylinder, as the window leaves it.
    val lostGas = textOf(members["lost_gas"])?.let { written ->
        val number = written.toIntOrNull()?.takeIf { it >= 1 }
            ?: return Made.Wrong("$name lost_gas should be a cylinder's number, not $written")
        if (number > gases.size.coerceAtLeast(1)) {
            return Made.Wrong(
                "$name lost_gas should be one of the ${gases.size.coerceAtLeast(1)} cylinders, not $number",
            )
        }
        number - 1
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
            narcoticDepth = setting("end_max", Settings.DEFAULT_END_MAX),
            oxygenNarcotic = oxygenNarcotic,
            descentRate = setting("descent_rate", Settings.DEFAULT_DESCENT_RATE),
            ascentRate = setting("ascent_rate", Settings.DEFAULT_ASCENT_RATE),
            safetyDepth = setting("safety_stop_depth", Settings.DEFAULT_SAFETY_STOP_DEPTH),
            safetyMinutes = setting("safety_stop_duration", Settings.DEFAULT_SAFETY_STOP_DURATION),
            lastStop = setting("last_stop", Settings.DEFAULT_LAST_STOP),
            switchStops = switchStops,
            water = textOf(members["water_type"]) ?: Settings.DEFAULT_WATER_TYPE.default,
            atmosphericPressure = setting("atmospheric_pressure", Settings.DEFAULT_ATMOSPHERIC_PRESSURE),
            stressFactor = setting("stress_factor", Settings.DEFAULT_STRESS_FACTOR),
            problemMinutes = setting("problem_solving_time", Settings.DEFAULT_PROBLEM_SOLVING_TIME),
            lostGasScenario = lostGasReserve,
            lostGas = lostGas,
            diveMode = textOf(members["dive_mode"]) ?: OPEN_CIRCUIT,
            setpointLow = textOf(members["setpoint_low"]) ?: Planned().setpointLow,
            setpointHigh = textOf(members["setpoint_high"]) ?: Planned().setpointHigh,
            setpointSwitchDepth = textOf(members["setpoint_switch_depth"]) ?: Planned().setpointSwitchDepth,
            diluent = diluent,
            sharedScenario = sharedGasReserve,
            bailoutScenario = bailoutReserve,
            co2HitFactor = textOf(members["co2_hit_factor"]) ?: Planned().co2HitFactor,
            // In seconds, as every time in a plan file is, and in minutes as the plan holds it.
            co2HitMinutes = textOf(members["co2_hit_time"])?.let { written ->
                written.toDoubleOrNull()?.let { plain(it / 60.0) } ?: written
            } ?: Planned().co2HitMinutes,
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
