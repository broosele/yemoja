package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Units

/*
 * What a new item should be called, which is a proposal rather than an answer.
 *
 * `DATA-84` fixes what an id may contain and says outright that the cost of the narrow rule falls
 * where the proposal is made, which is here: a name written in Greek or Japanese becomes a
 * transliteration or a fallback, and the `name` field keeps the real spelling.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** What an id may hold, `DATA-84`, with `#` left out: it separates the index and is not a base. */
private const val ALLOWED = "abcdefghijklmnopqrstuvwxyz0123456789_-."

/** What an id may not begin or end with, since a file name should not either. */
private const val EDGES = "_-."

/**
 * [text] as an id may be written, or empty where nothing of it survives.
 *
 * Lowercased and reduced to what `DATA-84` allows, with every run of anything else becoming one
 * `_` and the edges trimmed. Nothing is transliterated: a name in an alphabet this cannot carry
 * comes back empty and the caller falls back, which is the honest outcome rather than a mangled
 * one.
 *
 * For example, `De Vries, Anna` becomes `de_vries_anna` and `Ελλάδα` becomes nothing at all.
 */
internal fun slug(text: String): String {
    val out = StringBuilder(text.length)
    for (character in text.lowercase()) {
        if (character in ALLOWED) out.append(character) else if (out.lastOrNull() != '_') {
            out.append('_')
        }
    }
    return out.toString().trim { it in EDGES }
}

/**
 * What an item with nothing to be named after is called: `unknown_person`, `unknown_dive_site`.
 *
 * The type's own name, so nothing here lists the types. The manual promises this to users.
 */
internal fun unknownOf(item: Item): String = "unknown_${item.description.name}"

/**
 * The id proposed for an item named by its `name` field, which is seven of the nine types.
 *
 * A person's `name` is assembled from the parts, so this works for one who has only a first name
 * and for one whose name was corrected by hand.
 */
internal fun namedById(item: Item): String {
    val name = (item.single<String>("name") as? Result.Usable)?.value ?: return unknownOf(item)
    return slug(name).ifEmpty { unknownOf(item) }
}

/**
 * The id proposed for a dive: the day it began, and an index.
 *
 * **Always indexed**, from `#0`, because several dives in a day is ordinary rather than
 * exceptional — `DATA-84` makes dives the exception to leaving index zero off. The index is
 * assignment order and says nothing about which dive of the day it was; the times say that.
 *
 * Not from `name`, which a dive works out *from* its id and would be circular.
 */
internal fun divesProposedId(dive: Item): String {
    val began = (dive.single<Date>("start_date") as? Result.Usable)?.value
        ?: return "${unknownOf(dive)}#0"
    return "$began#0"
}

/**
 * The first name free from [proposed], by [taken].
 *
 * A proposal is not an answer: two things may propose the same and neither knows what is already
 * there. Where it is taken the index moves on, from one past whatever the proposal carried — so a
 * proposal with no index goes to `#1` and a dive's `#0` goes to `#1`. `DATA-84` leaves index zero
 * off unless a type always writes one, and a type that does says so by proposing it.
 *
 * The lowest free, so a name given up comes back. `JSON-18` says a *key* is not reused once its
 * entry is gone and says in the same breath that the obligation arrives with `FEAT-4`: only
 * history can say which keys were ever used, and until there is history there is nothing to
 * protect.
 */
fun freeName(proposed: String, taken: (String) -> Boolean): String {
    if (!taken(proposed)) return proposed
    val base = proposed.substringBefore('#')
    var at = (proposed.substringAfter('#', "").toIntOrNull() ?: 0) + 1
    while (taken("$base#$at")) at += 1
    return "$base#$at"
}

/** What [field] on [item] points at, by id or by the plain name a one-off carries. */
private fun pointedAt(item: Item, field: String): String? =
    when (val held = (item.single<Reference>(field) as? Result.Usable)?.value) {
        is Reference.Identified -> held.id
        is Reference.OneOff -> held.name
        null -> null
    }

/** What [field] on [item] says, as it is written, or nothing where it says nothing. */
private fun said(item: Item, field: String): String? {
    val read = item.read(field)
    if (read !is Result.Usable) return null
    return item.description[field]?.format(read.value, Units.DEFAULT)
}

/**
 * A profile's key: the computer that made the recording.
 *
 * A dive has more than one profile precisely when more than one computer was worn, so that is
 * what tells them apart, and it is what a diver would say. `JSON-18`.
 */
internal fun profilesProposedKey(profile: Item): String =
    slug(pointedAt(profile, "dive_computer").orEmpty()).ifEmpty { "profile" }

/**
 * A gas source's key: what it was for, and failing that the gas in it.
 *
 * `bottom` and `deco` are what a diver calls them, and what divides a set of cylinders. The gas
 * is the fallback because a download carries one per source and need not say what it was for.
 */
internal fun gasSourcesProposedKey(source: Item): String =
    slug(said(source, "usage").orEmpty()).ifEmpty {
        slug(said(source, "gas_type").orEmpty()).ifEmpty { "gas" }
    }

/**
 * A course's key: the certification it was for.
 *
 * How somebody refers to one: my open water course, not my 2022 course. The date is the fallback,
 * being present on a course whose certification was never entered.
 */
internal fun coursesProposedKey(course: Item): String =
    slug(pointedAt(course, "certification").orEmpty()).ifEmpty {
        slug(said(course, "date").orEmpty()).ifEmpty { "course" }
    }

/**
 * A maintenance's key: what was done and when.
 *
 * The one collection where the same thing happens again and again, so the type alone would count
 * and the date alone would hide what was done — a yearly inspection and a five-yearly test would
 * read as bare dates with nothing to tell them apart. Nothing points at a maintenance by key, so
 * the length costs little.
 */
internal fun maintenancesProposedKey(work: Item): String {
    val done = slug(said(work, "type").orEmpty())
    val date = slug(said(work, "date").orEmpty())
    return listOf(done, date).filter { it.isNotEmpty() }.joinToString("_")
        .ifEmpty { "maintenance" }
}
