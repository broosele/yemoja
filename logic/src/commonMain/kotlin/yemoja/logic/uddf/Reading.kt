package yemoja.logic.uddf

import yemoja.data.Stored

/*
 * Reading one tag's fields, which every type's mapping does the same way.
 *
 * See ../../../../../../doc.md — the field-by-field mapping is logic/uddf.md.
 */

/**
 * The tags this reader never looks through when hunting for a field by name.
 *
 * Each of them is an item of its own or holds items of its own, so crossing one would read a
 * wreck's name as its site's. It is one set rather than a set per type because the boundary is a
 * property of the document rather than of what is being read out of it.
 */
internal val APART: Set<String> = setOf(
    "wreck", "equipment", "education", "medical", "insurance", "trippart", "relateddives",
    "samples", "buddy", "owner", "site", "divebase", "trip", "dive", "tankdata", "mix",
) + GEAR

/** What the tag called [name] inside this one says, or absent where it says nothing. */
internal fun Tag.said(name: String): String? =
    find(name, APART)?.said?.trim()?.ifEmpty { null }

/**
 * The date the tag called [name] carries, or absent where it carries none.
 *
 * UDDF wraps a date in a `datetime` inside the element that names it — `birthdate`, `issuedate`,
 * `sunk` — and writes it as an instant, so the day is what is in front of the `T`.
 */
internal fun Tag.date(name: String): String? {
    val held = find(name, APART) ?: return null
    val said = held.said("datetime") ?: held.said.trim().ifEmpty { null } ?: return null
    return said.substringBefore('T').ifEmpty { null }
}

/** Everything the tag called [name] says, its children included, which is what prose is. */
internal fun Tag.prose(name: String): String? {
    val held = find(name, APART) ?: return null
    val paragraphs = held.all("para").joinToString("\n") { it.everything() }
    return paragraphs.ifEmpty { held.everything() }.ifEmpty { null }
}

/** What the `link` inside the tag called [name] points at, as this reader named it. */
internal fun Tag.points(name: String, ours: Map<String, String>): String? {
    val held = find(name, APART) ?: return null
    val ref = held.one("link")?.attributes?.get("ref") ?: return null
    return ours[ref]?.let { "@$it" }
}

/** Put [value] under [field] where there is one, which is how every mapping is written. */
internal fun MutableMap<String, Stored>.put(field: String, value: String?) {
    if (value != null) this[field] = Stored.Leaf(value)
}

/** Put every value of [said] under [field], which holds a list of them. */
internal fun MutableMap<String, Stored>.putAll(field: String, said: List<String>) {
    if (said.isNotEmpty()) this[field] = Stored.Elements(said.map { Stored.Leaf(it) })
}

/** Put the members [held] under [field] where it holds any. */
internal fun MutableMap<String, Stored>.put(field: String, held: Map<String, Stored>) {
    if (held.isNotEmpty()) this[field] = Stored.Members(held)
}
