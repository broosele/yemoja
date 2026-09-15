package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.logic.Types

/*
 * What the logbook owes, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-34`.
 */

/** How much notice an obligation is given before it falls due, in days. A month. `GUI-34`. */
internal const val NOTICE = 31

/** How long a medical is taken to run, in days short of the calendar. `GUI-34`. */
internal const val MEDICAL_MONTHS = 12

/**
 * Owed is one thing that has fallen due or is about to, and how pressing it is.
 *
 * [days] is how many are left, negative once the day has passed, so that one number both sorts
 * the list and says which of the two things happened.
 */
internal class Owed(val said: String, val days: Int) {

    /** Whether the day has passed rather than merely being near. */
    val lapsed: Boolean get() = days < 0
}

/**
 * Everything the logbook owes on [today], the most pressing first.
 *
 * Three things fall due: a piece of gear owing work, the user's medical, and their insurance.
 * Nothing else in the model carries a date it runs out on.
 *
 * **Only the user's own.** A logbook holds other people, and when their medicals run out is
 * their business rather than a banner on somebody else's home screen. Gear is not qualified
 * that way: a logbook's gear is the logbook's.
 *
 * Anything falling due more than [NOTICE] days from now is left out, and so is anything the
 * logbook says nothing about. What lapsed is never left out however long ago it was: a cylinder
 * two years out of test is more of a problem than one due next week, not less.
 */
internal fun owedIn(set: ItemSet, user: Item?, today: Date, notice: Int = NOTICE): List<Owed> {
    val out = ArrayList<Owed>()
    for (gear in set.allOf(Types.GEAR)) {
        if (genericOf(gear)) continue
        for (work in standingIn(gear)) {
            val ends = dateOf(work, "valid_until") ?: continue
            val left = today.daysUntil(ends).toInt()
            if (left > notice) continue
            out += Owed(workSaid(gear, work, left), left)
        }
    }
    if (user != null) {
        ownedIn(user, "medical")?.let { medical ->
            dateOf(medical, "last_medical_check")?.let { checked ->
                val left = today.daysUntil(yearAfter(checked)).toInt()
                if (left <= notice) out += Owed(medicalSaid(left), left)
            }
        }
        ownedIn(user, "insurance")?.let { cover ->
            dateOf(cover, "end_date")?.let { ends ->
                val left = today.daysUntil(ends).toInt()
                if (left <= notice) out += Owed(insuranceSaid(cover, left), left)
            }
        }
    }
    return out.sortedBy { it.days }
}

/**
 * The maintenance entries of [gear] that set a clock, the latest for each thing owed.
 *
 * **The latest work counts.** An inspection done again replaces the one before it, which no
 * longer owes anything: warning about both put an item on the home screen twice, once for work
 * already redone. What is owed is what `follow_up_type` names, or the entry's own `type`, so a
 * pressure test done does not make an inspection look current. Latest is by `date`, and by
 * `valid_until` between entries that give no date. An entry with no `valid_until` starts no
 * clock and is passed over. `GUI-34`.
 */
private fun standingIn(gear: Item): List<Item> =
    entriesOf(gear, "maintenances")
        .filter { dateOf(it, "valid_until") != null }
        .groupBy { oweOf(it)?.lowercase() }
        .values
        .map { owing -> owing.maxWith(LATEST) }

/** Later work first by the day it was done, and by when it runs out where no day is given. */
private val LATEST: Comparator<Item> =
    compareBy({ dateOf(it, "date") }, { dateOf(it, "valid_until") })

/** What a piece of work sets the clock for: its follow-up where it names one, else itself. */
private fun oweOf(work: Item): String? = textOf(work, "follow_up_type") ?: textOf(work, "type")

/**
 * What a piece of gear owes, and when: *Twelve steel: pressure test due in 5 days.*
 *
 * The name, then what is owed, then when, rather than a sentence. What is owed is whatever the
 * reader typed — a service, a visual inspection, a cleaning — and no sentence takes all of those
 * without an article that is wrong for one of them.
 */
private fun workSaid(gear: Item, work: Item, left: Int): String {
    val what = oweOf(work) ?: "work"
    return "${titleOf(gear)}: $what ${dueSaid(left)}."
}

private fun medicalSaid(left: Int): String = "Your medical check is ${dueSaid(left)}."

private fun insuranceSaid(cover: Item, left: Int): String {
    val name = textOf(cover, "name")?.let { " ($it)" } ?: ""
    return if (left < 0) {
        "Your insurance$name ran out ${agoOf(-left)}."
    } else {
        "Your insurance$name runs out ${withinOf(left)}."
    }
}

/** When something falls due, or how long it has been overdue. */
private fun dueSaid(left: Int): String =
    if (left < 0) "${spanOf(-left)} overdue" else "due ${withinOf(left)}"

/** How long there is left, as a reader says it. */
private fun withinOf(left: Int): String = when (left) {
    0 -> "today"
    1 -> "tomorrow"
    else -> "in ${spanOf(left)}"
}

/** How long ago a day was, as a reader says it. */
private fun agoOf(days: Int): String = if (days == 0) "today" else "${spanOf(days)} ago"

/**
 * A run of days as a reader counts it.
 *
 * Coarser the further off it is, because that is how far off it is worth knowing: five days
 * matters to the day and five years does not.
 */
private fun spanOf(days: Int): String = when {
    days == 1 -> "a day"
    days < 14 -> "$days days"
    days < 60 -> "${days / 7} weeks"
    days < 365 -> "${days / 30} months"
    days < 730 -> "a year"
    else -> "${days / 365} years"
}

/**
 * A year after [checked], which is how long a medical is taken to run.
 *
 * **Yemoja is guessing.** Nothing in a logbook says how long a certificate runs, and the answer
 * is not the same everywhere: age, the authority and an employer all change it. A year is the
 * common one and is what the warning leans on, which is why the warning says *falls due* rather
 * than claiming the certificate expired. `GUI-34`.
 *
 * The twenty-ninth of February has no answer a year later, and takes the twenty-eighth.
 */
internal fun yearAfter(checked: Date): Date {
    val year = checked.year + MEDICAL_MONTHS / Date.MONTHS_IN_YEAR
    val day = minOf(checked.day, Date.lengthOfMonth(year, checked.month))
    return Date(year, checked.month, day)
}

private fun genericOf(item: Item): Boolean =
    (item.single<Boolean>("generic") as? Result.Usable)?.value == true

private fun ownedIn(item: Item, name: String): OwnedItem? =
    (item.read(name) as? Result.Usable)?.value as? OwnedItem

private fun entriesOf(item: Item, name: String): List<Item> =
    ((item.keyed<OwnedItem>(name) as? Result.Usable)?.value ?: emptyMap())
        .values.mapNotNull { (it as? Element.Usable)?.value }

private fun dateOf(item: Item, name: String): Date? =
    (item.single<Date>(name) as? Result.Usable)?.value

private fun textOf(item: Item, name: String): String? =
    (item.single<String>(name) as? Result.Usable)?.value?.ifBlank { null }
