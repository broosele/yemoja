package yemoja.logic

/*
 * Depth turned into pressure, which is what a body responds to and what both consumption and
 * decompression work in.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** The absolute pressure at [metres] down, in water of [density], under [surface] bar of air. */
internal fun ambientAt(metres: Double, density: Double, surface: Double): Double =
    surface + density * GRAVITY * metres / PASCALS_IN_BAR

/** How deep [bar] is, in water of [density] under [surface] bar of air, and never above nought. */
internal fun depthAt(bar: Double, density: Double, surface: Double): Double =
    ((bar - surface) * PASCALS_IN_BAR / (density * GRAVITY)).coerceAtLeast(0.0)

/** Standard gravity, which is what turns a column of water into a pressure. */
internal const val GRAVITY = 9.80665

internal const val PASCALS_IN_BAR = 100_000.0

/** The atmosphere at sea level in bar, where nothing says what the air above a dive was. */
internal const val SEA_LEVEL = 1.01325

/**
 * What water is taken to weigh where a recording gives no density: the EN 13319 figure, which is
 * the one most computers turn pressure into depth with.
 */
internal const val NOMINAL_DENSITY = 1020.0
