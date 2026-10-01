package yemoja.ui.gui

import yemoja.data.Cardinality
import yemoja.data.Date
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
import yemoja.logic.titleOf

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
    // What was dived, not what is intended: a logbook claiming a dive nobody made is wrong.
    // `GUI-39`.
    val dives = divesMadeIn(set)
    val places = LinkedHashSet<String>()
    var underwater = 0.0
    for (dive in dives) {
        val site = (dive.read("dive_site") as? Result.Usable)?.value
        (site as? Reference.Identified)?.let { places += it.id }
        underwater += (dive.read("duration") as? Result.Usable)?.value as? Double ?: 0.0
    }
    return Greeting(dives.size, places.size, underwater)
}

/**
 * The first line: who is being greeted, and whatever the day is worth remarking on.
 *
 * Three of whom. Whoever the logbook names as its own, by name; a reader whose logbook names
 * nobody, as a diver; and a reader with no logbook open at all, welcomed. The last is not
 * reachable until the home screen's buttons are built, and is written because the greeting is
 * the first thing a new reader meets and should not be written twice. `GUI-30`.
 */
internal fun hailOf(
    user: ReferenceableItem?,
    greeting: Greeting?,
    today: Date,
    southern: Boolean,
): Hail {
    val opening = when {
        greeting == null -> "Hello"
        user == null -> "Hello diver"
        else -> "Hello ${titleOf(user)}"
    }
    val occasion = occasionOf(today, user?.let(::birthdayOf), southern)
        ?: if (greeting == null) Occasion("welcome") else null
    return Hail(opening, occasion)
}

/**
 * Hail is the first line: who is greeted, and the day's remark where the day earns one.
 *
 * The two are kept apart because the remark leads out to a page about the day and the rest of
 * the line leads nowhere. `GUI-30`.
 */
internal class Hail(val opening: String, val occasion: Occasion?) {

    /** The line as one sentence, which is what it reads as with nothing to click. */
    val sentence: String =
        if (occasion == null) "$opening." else "$opening, and ${occasion.said}."
}

/**
 * Occasion is a day worth remarking on: what to say of it, and a page to read about it.
 *
 * The page is absent where there is nothing to read: the reader's own birthday is theirs, and
 * a welcome is not a day at all.
 */
internal class Occasion(val said: String, val page: String? = null)

/**
 * The second line: what the logbook amounts to, or what to do about there not being one.
 *
 * A logbook that names nobody as its reader is told how to, rather than counted at: the count
 * is there every other day, and the one thing worth saying is the thing that is missing.
 */
internal fun tellingOf(user: ReferenceableItem?, greeting: Greeting?): String = when {
    greeting == null -> "You can make a new logbook, or open one you already have."
    user == null ->
        "Open the Community tab and mark one of its people as yourself, to be greeted by name."

    else -> "You have ${counted(greeting.dives, "logged dive")} on " +
            "${counted(greeting.places, "different location")} for a total of " +
            "${spanOf(greeting.underwater)} underwater."
}

/** When [user] was born, where the logbook says. */
private fun birthdayOf(user: ReferenceableItem): Date? =
    (user.read("birthday") as? Result.Usable)?.value as? Date

/**
 * What today is worth remarking on, as a phrase that follows *and*, or absent on a plain day.
 *
 * The reader's own birthday first, then the new year, then a day the sea has been given, then
 * the turn of a season. Most days are plain and the greeting says nothing but hello, which is
 * what keeps the rest worth reading.
 */
internal fun occasionOf(today: Date, born: Date?, southern: Boolean): Occasion? {
    if (born != null && born.month == today.month && born.day == today.day) {
        return Occasion("happy birthday")
    }
    if (today.month == 1 && today.day == 1) {
        return Occasion("a happy new year", wiki("New_Year%27s_Day"))
    }
    OBSERVED[today.month to today.day]?.let { return it }
    return seasonOf(today, southern)
}

/** An article on the English Wikipedia, which is where a day worth remarking on is explained. */
private fun wiki(title: String): String = "https://en.wikipedia.org/wiki/$title"

/**
 * The days of the sea, by month and day, each with somewhere to read about it.
 *
 * Two are the United Nations', the rest are observances divers keep among themselves and are
 * of varying standing; the one date here that is a plain fact is Jacques Cousteau's, born on
 * the eleventh of June in 1910. A day without an article of its own leads to what it is about
 * instead, a shark's day to the sharks.
 */
private val OBSERVED: Map<Pair<Int, Int>, Occasion> = mapOf(
    (3 to 22) to Occasion("happy World Water Day", wiki("World_Water_Day")),
    (4 to 22) to Occasion("happy Earth Day", wiki("Earth_Day")),
    (6 to 8) to Occasion("happy World Oceans Day", wiki("World_Oceans_Day")),
    (6 to 11) to Occasion(
        "a thought for Jacques Cousteau, born on this day in 1910",
        wiki("Jacques_Cousteau"),
    ),
    (6 to 16) to Occasion("happy World Sea Turtle Day", wiki("Sea_turtle")),
    (7 to 14) to Occasion("happy Shark Awareness Day", wiki("Shark")),
    (9 to 17) to Occasion("happy World Manta Day", wiki("Manta_ray")),
    (10 to 8) to Occasion("happy World Octopus Day", wiki("Octopus")),
    (11 to 3) to Occasion("happy World Jellyfish Day", wiki("Jellyfish")),
)

/**
 * The first day of a season, which is a season later in the south.
 *
 * The meteorological seasons rather than the astronomical ones: they begin on the first of a
 * month every year, where an equinox wanders over three days and would need an almanac.
 */
private fun seasonOf(today: Date, southern: Boolean): Occasion? {
    if (today.day != 1) return null
    val month = if (!southern) today.month else (today.month + 5) % 12 + 1
    val (season, page) = SEASONS[month] ?: return null
    return Occasion("happy first day of $season", wiki(page))
}

/** The month each season begins in, north of the equator, and what it is called. */
private val SEASONS: Map<Int, Pair<String, String>> = mapOf(
    3 to ("spring" to "Spring_(season)"),
    6 to ("summer" to "Summer"),
    9 to ("autumn" to "Autumn"),
    12 to ("winter" to "Winter"),
)

/**
 * Whether this logbook's diving is south of the equator, which is what turns the seasons round.
 *
 * By the mean of what its sites say, so that a northern diver's week in the tropics does not
 * move their winter. A logbook whose sites say nothing is taken to be northern, there being
 * nothing better to go on and most divers being there.
 */
internal fun southernOf(set: ItemSet): Boolean {
    var sum = 0.0
    var known = 0
    for (site in set.allOf(Types.DIVE_SITE)) {
        val latitude = (site.read("latitude") as? Result.Usable)?.value as? Double ?: continue
        sum += latitude
        known++
    }
    return known > 0 && sum / known < 0.0
}

/** [many] of [word], the word made plural where it needs to be. */
internal fun counted(many: Int, word: String): String =
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
    OPEN("Open logbook"),
    IMPORT("Import"),
    EXPORT("Export to UDDF"),
    DOWNLOAD("Download from dive computer"),
    SETTINGS("Settings"),
}

/**
 * Variable is one thing about a dive that a plot can put on an axis.
 *
 * A date reads as a year and a fraction of one, so that the marks along the axis fall on years
 * and the axis needs no calendar of its own.
 */
internal class Variable(
    val label: String,
    val unit: String,
    val of: (Item) -> Double?,
    /**
     * The day this reads, where what it reads is a date. Absent on everything else.
     *
     * A fraction of a year is what an axis counts in, and it is no use to a calendar: months
     * are not the same length, so a bar cut at a twelfth of a year begins two days into March.
     * `GUI-32`.
     */
    val dayOf: ((Item) -> Long?)? = null,
)

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
                variableOf(inner) { dive -> ownedIn(dive, field.name) }?.let { out += it }
            }
        } else {
            variableOf(field) { it }?.let { out += it }
        }
    }
    return out
}

/**
 * [field] as an axis, or absent where it is not one; [reach] is what holds it, given a dive.
 *
 * A dive's own field is reached from the dive itself and an owned item's through the item,
 * which is the only difference between the two and is why they are read the same way here.
 */
private fun variableOf(field: FieldDescription, reach: (Item) -> Item?): Variable? {
    // How far one clock is ahead of another says nothing about the diving. `LOGIC-32`.
    if (field.name in OFFSETS) return null
    val read = readerOf(field) ?: return null
    val day = if (field is DateDescription) dayReaderOf(field) else null
    return Variable(
        field.label,
        unitAlong(field),
        { dive -> reach(dive)?.let(read) },
        day?.let { one -> { dive: Item -> reach(dive)?.let(one) } },
    )
}

/** The day [field] reads, which is what a calendar cuts its bars on. `GUI-32`. */
private fun dayReaderOf(field: DateDescription): (Item) -> Long? = { held ->
    ((held.read(field.name) as? Result.Usable)?.value as? Date)?.epochDay
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
        ((held.read(field.name) as? Result.Usable)?.value as? Date)?.let(::yearOf)
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
internal fun yearOf(date: Date): Double = date.epochDay / DAYS_IN_YEAR + 1970.0

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
    divesMadeIn(set).mapNotNull { dive ->
        val x = across.of(dive) ?: return@mapNotNull null
        val y = up.of(dive) ?: return@mapNotNull null
        val id = (dive as? ReferenceableItem)?.let { set.idOf(it) } ?: return@mapNotNull null
        Spot(x, y, id, titleOf(dive))
    }
