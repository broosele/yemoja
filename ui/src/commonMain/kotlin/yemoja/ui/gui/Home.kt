package yemoja.ui.gui

import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.NumberDescription
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.WholeNumberDescription
import yemoja.logic.Types

/*
 * What the home screen says, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-30`.
 */

/**
 * Greeting is what a logbook comes to in one line: how many dives, how many places, how long.
 *
 * The places are the ones dived rather than the ones known, since a site nobody has been to
 * says nothing about the diving that was done.
 */
internal class Greeting(val dives: Int, val places: Int, val underwater: Double)

/** What [set] amounts to. */
internal fun greetingOf(set: ItemSet): Greeting {
    val dives = set.allOf(Types.DIVE)
    val places = LinkedHashSet<String>()
    var underwater = 0.0
    for (dive in dives) {
        val site = (dive.read("dive_site") as? Result.Usable)?.value
        (site as? Reference.Identified)?.let { places += it.id }
        underwater += (dive.read("duration") as? Result.Usable)?.value as? Double ?: 0.0
    }
    return Greeting(dives.size, places.size, underwater)
}

/** The first line, addressed to whoever the logbook belongs to. */
internal fun hailOf(user: ReferenceableItem?, greeting: Greeting): String {
    val name = user?.let { titleOf(it) }
    val opening = if (name == null) "You have" else "Hello $name, you have"
    return "$opening ${counted(greeting.dives, "logged dive")} on " +
        "${counted(greeting.places, "different location")} for a total of " +
        "${spanOf(greeting.underwater)} underwater."
}

/** [many] of [word], the word made plural where it needs to be. */
private fun counted(many: Int, word: String): String =
    if (many == 1) "1 $word" else "$many ${word}s"

/**
 * A long time as a reader says one: hours and minutes, and minutes alone under an hour.
 *
 * Not the clock a dive's own times use. A logbook's whole diving runs to hundreds of hours,
 * and `306:12` is a number nobody reads as a length.
 */
internal fun spanOf(seconds: Double): String {
    val minutes = (seconds / 60.0).toLong()
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> counted(rest.toInt(), "minute")
        rest == 0L -> counted(hours.toInt(), "hour")
        else -> "${counted(hours.toInt(), "hour")} and ${counted(rest.toInt(), "minute")}"
    }
}

/**
 * Deed is something the application can be asked to do to a logbook as a whole.
 *
 * Named here and offered on the home screen whether or not this platform can do it yet, so
 * that what the application is for is one list rather than the part that happens to be built.
 * `GUI-30`.
 */
internal enum class Deed(val label: String) {
    NEW("New logbook"),
    OPEN("Open a logbook"),
    IMPORT("Import a logbook"),
    DOWNLOAD("Download from a computer"),
}

/**
 * Variable is one thing about a dive that a plot can put on an axis.
 *
 * A date reads as a year and a fraction of one, so that the marks along the axis fall on years
 * and the axis needs no calendar of its own.
 */
internal class Variable(val label: String, val unit: String, val of: (Item) -> Double?)

/**
 * Everything about a dive that can go on an axis, in the order a dive holds them.
 *
 * The numbers and the dates a dive answers for, its own first and then those of the items it
 * owns — the conditions, the gear — each under the name it has there. What is kept for the
 * machinery is left out, being left out everywhere. `GUI-30`, `DATA-115`.
 */
internal fun variablesOf(): List<Variable> {
    val out = ArrayList<Variable>()
    for (field in fieldsShownOf(Types.DIVE)) {
        if (field.cardinality != Cardinality.SINGLE) continue
        if (field is OwnedItemDescription) {
            for (inner in fieldsShownOf(field.description)) {
                if (inner.cardinality != Cardinality.SINGLE) continue
                readerOf(inner)?.let { read ->
                    out += Variable(inner.label, unitAlong(inner)) { dive ->
                        ownedIn(dive, field.name)?.let(read)
                    }
                }
            }
        } else {
            readerOf(field)?.let { out += Variable(field.label, unitAlong(field), it) }
        }
    }
    return out
}

/**
 * How to get [field] out of whatever holds it as a number, or absent where it is not one.
 *
 * A time reads in minutes rather than in the seconds a file holds, an axis marked every 600
 * being unreadable; everything else reads in the unit the model's own is written in.
 */
private fun readerOf(field: FieldDescription): ((Item) -> Double?)? = when (field) {
    is NumberDescription, is WholeNumberDescription -> { held ->
        val read = ((held.read(field.name) as? Result.Usable)?.value as? Number)?.toDouble()
        read?.let { it / scaleOf(field) }
    }

    is DateDescription -> { held ->
        ((held.read(field.name) as? Result.Usable)?.value as? yemoja.data.Date)?.let(::yearOf)
    }

    else -> null
}

/** What a reading is divided by to reach the unit an axis carries. */
private fun scaleOf(field: FieldDescription): Double =
    if (field is NumberDescription && field.dimension == Dimension.TIME) SECONDS_IN_MINUTE else 1.0

/** What an axis of times is titled: the model writes seconds and a reader reads minutes. */
private fun unitAlong(field: FieldDescription): String =
    if (field is NumberDescription && field.dimension == Dimension.TIME) "min" else unitOf(field)

private const val SECONDS_IN_MINUTE = 60.0

/** A date as a year and a fraction of one, which is what an axis of dates counts in. */
private fun yearOf(date: yemoja.data.Date): Double = date.epochDay / DAYS_IN_YEAR + 1970.0

/** The Gregorian year, averaged over its cycle, which is what a fraction of one divides by. */
private const val DAYS_IN_YEAR = 365.2425

private fun ownedIn(dive: Item, name: String): OwnedItem? =
    (dive.read(name) as? Result.Usable)?.value as? OwnedItem

/**
 * Which two variables the plot opens on, along the bottom and up the side.
 *
 * How deep against when, which is the shape of a diver's own diving and the one plot worth
 * drawing before anybody has asked for one. By name, since that is what a reader chooses by;
 * where a model has neither, the first two it has.
 */
internal fun openingOf(variables: List<Variable>): Pair<Int, Int> {
    val across = variables.indexOfFirst { it.label == "Start date" }
    val up = variables.indexOfFirst { it.label == "Max depth" }
    return (if (across >= 0) across else 0) to (if (up >= 0) up else minOf(1, variables.size - 1))
}

/** Spot is one dive on a plot: where it sits, and which dive it is. */
internal class Spot(val across: Double, val up: Double, val id: String, val title: String)

/**
 * Every dive in [set] that answers both [across] and [up], as a dot apiece.
 *
 * A dive missing either is left out rather than drawn at nought: a plot of what was not
 * recorded would be read as a reading.
 */
internal fun plottedOf(set: ItemSet, across: Variable, up: Variable): List<Spot> =
    set.allOf(Types.DIVE).mapNotNull { dive ->
        val x = across.of(dive) ?: return@mapNotNull null
        val y = up.of(dive) ?: return@mapNotNull null
        val id = (dive as? ReferenceableItem)?.let { set.idOf(it) } ?: return@mapNotNull null
        Spot(x, y, id, titleOf(dive))
    }
