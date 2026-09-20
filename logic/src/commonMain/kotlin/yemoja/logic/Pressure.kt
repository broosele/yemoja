package yemoja.logic

/*
 * Depth turned into pressure, which is what a body responds to and what both consumption and
 * decompression work in.
 *
 * Public, because a front end asking what a figure means at a depth is asking this and nothing
 * else: a SAC typed into a box is litres a minute at the surface, and its cost at depth is the
 * same arithmetic the logbook applies to a recording. `LOGIC-39`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** The absolute pressure at [metres] down, in water of [density], under [surface] bar of air. */
fun ambientAt(metres: Double, density: Double, surface: Double): Double =
    surface + density * GRAVITY * metres / PASCALS_IN_BAR

/** How deep [bar] is, in water of [density] under [surface] bar of air, and never above nought. */
fun depthAt(bar: Double, density: Double, surface: Double): Double =
    ((bar - surface) * PASCALS_IN_BAR / (density * GRAVITY)).coerceAtLeast(0.0)

/** Standard gravity, which is what turns a column of water into a pressure. */
internal const val GRAVITY = 9.80665

internal const val PASCALS_IN_BAR = 100_000.0

/** The atmosphere at sea level in bar, where nothing says what the air above a dive was. */
const val SEA_LEVEL = 1.01325

/**
 * What water is taken to weigh where a recording gives no density: the EN 13319 figure, which is
 * the one most computers turn pressure into depth with.
 */
const val NOMINAL_DENSITY = 1020.0
