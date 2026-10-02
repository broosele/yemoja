package yemoja.ui.gui

import yemoja.logic.Planned

/*
 * A dive plan as it is described, apart from the form that describes it.
 *
 * See ../../../../../../ui/api/doc.md — `API-6`. The window keeps a [Shaping], which is the same
 * fields in Compose state so that typing redraws; everything that reads a plan rather than editing
 * one takes [Planned] itself, in the logic layer, so a caller with no window can ask for the same
 * calculation.
 */

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
    switchStops = switchStops,
    stressFactor = stressFactor,
    problemMinutes = problemMinutes,
    lostGasScenario = lostGasScenario,
    lostGas = lostGas,
    sharedScenario = sharedScenario,
    water = water,
    startDate = startDate,
    startTime = startTime,
    following = following,
)
