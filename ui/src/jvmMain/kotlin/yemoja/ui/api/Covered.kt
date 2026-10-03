package yemoja.ui.api

import yemoja.logic.Universe
import yemoja.logic.coverageOf

/*
 * How often each worked-out field of a logbook gets a value, as a command.
 *
 * See ../../../../../../../testing.md — what a real logbook shows that a fixture does not.
 */

/**
 * Writes, for the logbook in [folder], how often each worked-out field was worked out, written,
 * absent, or would not read.
 *
 * Reads and writes nothing else. A field absent on most items is where to look: either the
 * logbook lacks what it is worked out from, or the working out asks for more than a logbook has.
 */
internal fun covered(folder: String): Int {
    val universe = try {
        Universe.open(folder)
    } catch (refused: Exception) {
        System.err.println("$folder could not be read: ${refused.message}")
        return 1
    }
    val rows = coverageOf(universe.logbook)
    val width = rows.maxOfOrNull { it.path.length } ?: 0
    println("${"field".padEnd(width)}  ${HEADINGS.joinToString("  ") { it.padStart(COLUMN) }}")
    for (row in rows) {
        val counts = listOf(row.worked, row.written, row.absent, row.unusable)
        println("${row.path.padEnd(width)}  ${counts.joinToString("  ") { it.toString().padStart(COLUMN) }}")
    }
    return 0
}

private val HEADINGS = listOf("worked out", "written", "absent", "unusable")

/** How wide a column of counts is, which is its widest heading. */
private val COLUMN = HEADINGS.maxOf { it.length }
