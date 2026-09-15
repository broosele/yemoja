package yemoja.ui.gui

import yemoja.logic.uddf.Exported

/*
 * Writing a logbook out, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-37`.
 */

/**
 * What an export says once it is written: how many dives went, where, and what did not.
 *
 * A dive on two computers is the one loss said aloud, since the reader cannot see it in the file
 * and would otherwise find out in the other application. `uddf.md`.
 */
internal fun exportSaid(exported: Exported, to: String): String {
    val went = "Wrote ${counted(exported.dives, "dive")} to $to."
    if (exported.leftOut == 0) return went
    val (was, its) = if (exported.leftOut == 1) "was" to "its" else "were" to "their"
    return "$went ${counted(exported.leftOut, "dive")} recorded on more than one computer " +
        "$was written with $its primary recording only, UDDF holding one to a dive."
}

/**
 * [path] with `.uddf` after it, where the name the reader typed has no extension of its own.
 *
 * A name with a point in it already says what it is, and is left as typed.
 */
internal fun uddfNamed(path: String): String {
    val name = path.substringAfterLast('/').substringAfterLast('\\')
    return if ('.' in name) path else "$path.uddf"
}
