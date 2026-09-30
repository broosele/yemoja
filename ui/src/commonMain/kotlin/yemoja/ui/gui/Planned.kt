package yemoja.ui.gui

/*
 * A dive plan as it is described, apart from the form that describes it.
 *
 * See ../../../../../../ui/api/doc.md — `API-6`. The window keeps a [Shaping], which is the same
 * fields in Compose state so that typing redraws; everything that reads a plan rather than editing
 * one takes this, so a caller with no window can ask for the same calculation.
 */

/**
 * Planned is a dive plan as somebody wrote it down: the lines, the cylinders and the settings.
 *
 * **Text, not numbers.** Every field is held as it would be typed, so one description is read one
 * way however it arrived: a caller sending `soon` for a duration is refused in the words the form
 * refuses it in, rather than in whatever a second parser would have said. What a field may say is
 * `manual/planning.md`.
 *
 * Immutable.
 */
internal data class Planned(
    /** The lines of the runtime, in order, the first of them leaving the surface. */
    val segments: List<Segment> = listOf(Segment()),
    /** The cylinders, in the order they are listed, which is the order they are named in. */
    val gases: List<Breathed> = listOf(Breathed()),
    val gradientLow: String = "",
    val gradientHigh: String = "",
    val bottomOxygen: String = "",
    val decoOxygen: String = "",
    val leastOxygen: String = "",
    val descentRate: String = "",
    val ascentRate: String = "",
    val safetyDepth: String = "",
    val safetyMinutes: String = "",
    val lastStop: String = "",
    val panicFactor: String = "",
    /** Minutes the gas reserve spends at the depth trouble starts before the way up begins. */
    val problemMinutes: String = "",
    /** Whether the gas reserve tries losing a cylinder. */
    val lostGasScenario: Boolean = true,
    /** The cylinder lost, by its place in the list, or absent for the first deco cylinder. */
    val lostGas: Int? = null,
    /** Whether the gas reserve tries a buddy out of gas, sharing this diver's. */
    val sharedScenario: Boolean = true,
    /** In the words `water_type` uses. */
    val water: String = "salt",
    /** When the plan begins: a date such as `2026-10-03`, and a time such as `14:30`. */
    val startDate: String = "",
    val startTime: String = "",
    /** The earlier run this one follows, or absent for a dive started clean. `GUI-43`. */
    val following: Following? = null,
)

/** This form as a plan anything may read: the same fields, out of the window's state. */
internal fun Shaping.described(): Planned = Planned(
    segments = segments.toList(),
    gases = gases.toList(),
    gradientLow = gradientLow,
    gradientHigh = gradientHigh,
    bottomOxygen = bottomOxygen,
    decoOxygen = decoOxygen,
    leastOxygen = leastOxygen,
    descentRate = descentRate,
    ascentRate = ascentRate,
    safetyDepth = safetyDepth,
    safetyMinutes = safetyMinutes,
    lastStop = lastStop,
    panicFactor = panicFactor,
    problemMinutes = problemMinutes,
    lostGasScenario = lostGasScenario,
    lostGas = lostGas,
    sharedScenario = sharedScenario,
    water = water,
    startDate = startDate,
    startTime = startTime,
    following = following,
)
