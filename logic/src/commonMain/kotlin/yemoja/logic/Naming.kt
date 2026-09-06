package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.Result

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
