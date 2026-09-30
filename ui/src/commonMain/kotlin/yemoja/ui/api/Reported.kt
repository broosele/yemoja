package yemoja.ui.api

import yemoja.data.Stored
import kotlin.math.round

/*
 * A calculated plan written out, as a table to compare and as the whole answer.
 *
 * See ../../../../../../ui/api/doc.md — `API-8`.
 */

/**
 * The columns a row of the table carries, in order.
 *
 * A published air table has a column for stop minutes and one for the deepest stop, which is what
 * the first of these are for. The rest are what a second program would be asked for beside them.
 */
internal val COLUMNS: List<String> = listOf(
    "name",
    "max_depth_m",
    "bottom_minutes",
    "runtime_minutes",
    "stop_minutes",
    "deepest_stop_m",
    "stops",
    "cns_percent",
    "otu",
    "gas_litres",
    "refused",
)

/** [schedule] as one row of the table, under [name], in the order [COLUMNS] gives. */
internal fun rowOf(name: String, schedule: Schedule): List<String> = listOf(
    name,
    plain(schedule.maxDepthMetres),
    plain(bottomMinutesOf(schedule)),
    plain(schedule.runtimeSeconds / SECONDS_A_MINUTE),
    plain(schedule.stopSeconds / SECONDS_A_MINUTE),
    schedule.deepestStop?.let { plain(it) }.orEmpty(),
    schedule.stops.joinToString(" ") { "${plain(it.metres)}@${plain(it.seconds / SECONDS_A_MINUTE)}" },
    plain(schedule.cnsPercent),
    plain(schedule.otu),
    schedule.gasUsedLitres.entries.sortedBy { it.key }
        .joinToString(" ") { "${it.key}:${plain(it.value)}" },
    "",
)

/** A row for a plan that would not calculate: its name, nothing else, and the reason. */
internal fun rowOf(name: String, refused: String): List<String> =
    listOf(name) + List(COLUMNS.size - 2) { "" } + refused

/**
 * How long the dive was at its deepest before the way up began, in minutes.
 *
 * What a table's row is chosen by, and not a figure the model keeps: it is the runtime at the end
 * of the last line a caller described, everything after that being the way up.
 */
private fun bottomMinutesOf(schedule: Schedule): Double {
    val last = schedule.lines.lastOrNull { !it.added } ?: return 0.0
    return (last.beginsAt + last.seconds) / SECONDS_A_MINUTE
}

/** [rows] as a comma-separated table, the headings first. */
internal fun tableOf(rows: List<List<String>>): String =
    (listOf(COLUMNS) + rows).joinToString("\n") { row -> row.joinToString(",") { quoted(it) } }

/** A field as a table writes it: quoted where it holds a comma, a quote or a line break. */
private fun quoted(field: String): String =
    if (field.any { it == ',' || it == '"' || it == '\n' }) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else {
        field
    }

/** [schedule] as the whole answer, for a caller that wants every line of it. */
internal fun saidOf(name: String, schedule: Schedule): Stored = Stored.Members(
    mapOf(
        "name" to Stored.Leaf(name),
        "max_depth_m" to Stored.Leaf(schedule.maxDepthMetres),
        "runtime_seconds" to Stored.Leaf(schedule.runtimeSeconds.toLong()),
        "stop_seconds" to Stored.Leaf(schedule.stopSeconds.toLong()),
        "deepest_stop_m" to Stored.Leaf(schedule.deepestStop),
        "cns_percent" to Stored.Leaf(schedule.cnsPercent),
        "otu" to Stored.Leaf(schedule.otu),
        "no_flight_seconds" to Stored.Leaf(schedule.noFlightSeconds),
        "desaturation_seconds" to Stored.Leaf(schedule.desaturationSeconds),
        "lines" to Stored.Elements(
            schedule.lines.map { line ->
                Stored.Members(
                    mapOf(
                        "from_m" to Stored.Leaf(line.fromMetres),
                        "to_m" to Stored.Leaf(line.toMetres),
                        "begins_at_seconds" to Stored.Leaf(line.beginsAt.toLong()),
                        "seconds" to Stored.Leaf(line.seconds.toLong()),
                        "direction" to Stored.Leaf(line.direction),
                        "gas" to Stored.Leaf(line.gas),
                        "added" to Stored.Leaf(line.added),
                    ),
                )
            },
        ),
        "stops" to Stored.Elements(
            schedule.stops.map {
                Stored.Members(
                    mapOf(
                        "metres" to Stored.Leaf(it.metres),
                        "seconds" to Stored.Leaf(it.seconds.toLong()),
                    ),
                )
            },
        ),
        "gas_litres" to Stored.Members(
            schedule.gasUsedLitres.mapValues { Stored.Leaf(it.value) },
        ),
        "warnings" to Stored.Elements(
            schedule.warnings.map {
                Stored.Members(
                    mapOf(
                        "second" to Stored.Leaf(it.second.toLong()),
                        "severity" to Stored.Leaf(it.severity.name.lowercase()),
                        "said" to Stored.Leaf(it.said),
                    ),
                )
            },
        ),
    ),
)

/** A plan that would not calculate, as the whole answer: its name and the reason. */
internal fun saidOf(name: String, refused: String): Stored = Stored.Members(
    mapOf("name" to Stored.Leaf(name), "refused" to Stored.Leaf(refused)),
)

/** A number to one decimal, without a trailing nought. */
private fun plain(value: Double): String {
    val rounded = round(value * 10) / 10
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

private const val SECONDS_A_MINUTE = 60.0
