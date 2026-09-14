package yemoja.logic

/*
 * Where a recording stops being a dive.
 *
 * See ../../../../../doc.md — `LOGIC-30` in logic/doc.md is the decision, and both readers that
 * take recordings in use this: a download in divecomputer/Download.kt and a file in uddf/Uddf.kt.
 */

/**
 * The depth below which a recording is diving rather than floating, in metres. `LOGIC-30`.
 *
 * A metre is far shallower than the shallowest stop anyone holds and deeper than what a
 * computer reads on a wrist bobbing at the surface.
 */
internal const val SURFACE = 1.0

/**
 * How many of [depths] are the dive: everything up to the surfacing, and nothing after it.
 *
 * A computer does not end a dive the moment a diver reaches the surface. It waits, in case the
 * diver goes back down, and the wait is a setting: the same computer wrote eight minutes of
 * floating onto the end of one dive and two onto the next. That tail is not diving and does not
 * belong in a profile. `LOGIC-30`.
 *
 * The surfacing itself is kept, so the depth series still runs to the surface rather than
 * stopping on the way up. **Nothing is cut unless it is known to be at the surface**: a depth
 * nobody recorded, or one that cannot be read, is not evidence of floating, and a recording
 * that never went below [SURFACE] is left whole.
 */
internal fun untilSurfaced(depths: List<Double?>): Int {
    val under = depths.indexOfLast { it == null || it > SURFACE }
    if (under < 0) return depths.size
    return minOf(under + 2, depths.size)
}
