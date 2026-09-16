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
 * Two things are said aloud, since the reader cannot see either in the file and would otherwise
 * find out in the other application: a dive on two computers, and a dive left behind because it
 * has not been made. `uddf.md`, `GUI-39`.
 */
internal fun exportSaid(exported: Exported, to: String): String {
    val said = StringBuilder("Wrote ${counted(exported.dives, "dive")} to $to.")
    if (exported.leftOut > 0) {
        val (was, its) = if (exported.leftOut == 1) "was" to "its" else "were" to "their"
        said.append(" ${counted(exported.leftOut, "dive")} recorded on more than one computer ")
            .append("$was written with $its primary recording only, UDDF holding one to a dive.")
    }
    if (exported.plans > 0) {
        val was = if (exported.plans == 1) "was" else "were"
        said.append(" ${counted(exported.plans, "planned dive")} $was left behind.")
    }
    return said.toString()
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
