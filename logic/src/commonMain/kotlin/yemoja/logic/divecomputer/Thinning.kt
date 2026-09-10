package yemoja.logic.divecomputer

/*
 * Dropping the points a recording does not need, and saying what that cost.
 *
 * See ../../../../../../doc.md — `LOGIC-15` in logic/doc.md is the decision.
 */

/**
 * The points of [held] that carry something, within [tolerance] of the line the rest describe.
 *
 * A computer sampling every two seconds gives eighteen hundred depths in an hour, and a profile
 * drawn from those is no better than one drawn from a couple of hundred: points lying on a line
 * the others already describe carry nothing. This is not about disk — a logbook is small either
 * way — it is about a recording being legible by hand, which is what the format exists for.
 * `LOGIC-15`.
 *
 * The first and the last are always kept, and so is any point further than [tolerance] from the
 * straight line between the two points that would otherwise stand for it. What survives therefore
 * differs from the original by no more than [tolerance] anywhere, which is exactly what the
 * `tolerances` written beside the series then claims.
 *
 * **A tolerance of zero is a real answer and loses nothing.** It drops the points that cost
 * exactly nothing to drop: the middle of three equal readings, or of any run lying on one
 * straight line. That is most of what a computer reports for a value it works out rather than
 * measures — a no-decompression limit sits unchanged while the depth does — and dropping them
 * changes nothing that can be read back. Only a negative tolerance means *do not thin*.
 */
internal fun thinned(held: List<Pair<Int, Double>>, tolerance: Double): List<Pair<Int, Double>> {
    if (held.size <= 2 || tolerance < 0.0) return held
    val keeping = BooleanArray(held.size)
    keeping[0] = true
    keeping[held.size - 1] = true
    keep(held, 0, held.size - 1, tolerance, keeping)
    return held.filterIndexed { at, _ -> keeping[at] }
}

/**
 * Mark the point between [from] and [to] that is furthest off the line, where any is far enough.
 *
 * Recursive, and it terminates because each call works on a strictly shorter run: the point kept
 * is inside the pair, so both halves are shorter than what they were cut from.
 */
private fun keep(
    held: List<Pair<Int, Double>>,
    from: Int,
    to: Int,
    tolerance: Double,
    keeping: BooleanArray,
) {
    if (to <= from + 1) return
    var furthest = from
    var worst = 0.0
    for (at in from + 1..<to) {
        val off = offBy(held[from], held[to], held[at])
        if (off > worst) {
            worst = off
            furthest = at
        }
    }
    if (worst <= tolerance) return
    keeping[furthest] = true
    keep(held, from, furthest, tolerance, keeping)
    keep(held, furthest, to, tolerance, keeping)
}

/**
 * How far [point] is from what the line between [from] and [to] says it should be.
 *
 * Vertically rather than perpendicularly, because the two axes are not the same quantity: one is
 * seconds and the other is metres, and the distance between them is not a length. What is being
 * asked is how wrong the value would be if this point were dropped, which is a value's error.
 */
private fun offBy(
    from: Pair<Int, Double>,
    to: Pair<Int, Double>,
    point: Pair<Int, Double>,
): Double {
    val across = (to.first - from.first).toDouble()
    if (across == 0.0) return kotlin.math.abs(point.second - from.second)
    val along = (point.first - from.first) / across
    return kotlin.math.abs(point.second - (from.second + along * (to.second - from.second)))
}

/**
 * What a series is thinned to, by what it holds.
 *
 * **Provisional.** `LOGIC-15` says the figures themselves are a setting and are not chosen; these
 * are what a first import uses so that something is written rather than nothing, and they are the
 * numbers to argue about rather than an answer to it. Each is about the precision the quantity is
 * worth reading at: a tenth of a metre, a fifth of a degree, half a bar.
 */
internal val TOLERANCES: Map<String, Double> = mapOf(
    "depth" to 0.1,
    "temperature" to 0.2,
    "pressures" to 0.5,
    // Nothing may be lost from a value the computer worked out rather than measured, because
    // there is no measurement error to hide a rounding in. Zero still drops most of them: a
    // no-decompression limit is recomputed from the depth, so it repeats while the depth holds
    // and runs straight while the depth changes evenly. Four fifths of these points go and the
    // series reads back identically.
    "no_deco_time" to 0.0,
    "cns" to 0.0,
    "decostop" to 0.0,
)
