package yemoja.logic

import yemoja.data.Gas

/*
 * The depths of air a mix is equivalent to. See ../../../../../doc.md — `LOGIC-41`.
 */

/**
 * The depth at which air holds as much nitrogen as [gas] does at [metres], in metres.
 *
 * What divers call the equivalent air depth, which is how a nitrox dive is read against air tables.
 * Sea water at sea level unless told otherwise, as [maximumOperatingDepth] assumes.
 *
 * Nought where [gas] holds less nitrogen at [metres] than air does at the surface, which a rich mix
 * at a shallow depth does, since [depthAt] gives no depth above the surface.
 */
fun equivalentAirDepth(
    metres: Double,
    gas: Gas,
    density: Double = NOMINAL_DENSITY,
    surface: Double = SEA_LEVEL,
): Double = depthAt(gas.fractionN2 * ambientAt(metres, density, surface) / Gas.AIR.fractionN2, density, surface)

/**
 * The depth at which air is as narcotic as [gas] is at [metres], in metres.
 *
 * What divers call the equivalent narcotic depth. **Whether oxygen is narcotic is a convention,
 * not a fact the model settles**, and agencies teach both, so [oxygenNarcotic] chooses. Counted, the
 * narcotic part of a mix is everything but its helium and air's is all of it, which gives the
 * deeper and more cautious figure. Not counted, only nitrogen is narcotic, and the answer is the
 * [equivalentAirDepth]. Helium is taken as not narcotic either way.
 *
 * Nought where [gas] is less narcotic at [metres] than air is at the surface. Sea water at sea
 * level unless told otherwise.
 */
fun equivalentNarcoticDepth(
    metres: Double,
    gas: Gas,
    oxygenNarcotic: Boolean = true,
    density: Double = NOMINAL_DENSITY,
    surface: Double = SEA_LEVEL,
): Double {
    if (!oxygenNarcotic) return equivalentAirDepth(metres, gas, density, surface)
    val narcotic = gas.fractionN2 + gas.fractionO2
    return depthAt(narcotic * ambientAt(metres, density, surface), density, surface)
}
