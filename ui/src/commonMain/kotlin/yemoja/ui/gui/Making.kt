package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.logic.Types

/*
 * Making an item and unmaking one, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-35`.
 */

/**
 * Which type a tab's **+** makes, given what is chosen there.
 *
 * **Another of what you are looking at.** A tab may hold three types and the one worth adding is
 * the one already in front of the reader, so the chosen item answers it. Where nothing is
 * chosen the tab's first type answers instead, that being the one its selector is built around
 * — the dives in the table, the regions the sites hang from — and the one a logbook with
 * nothing in it needs first.
 */
internal fun makingOf(tab: Tab, chosen: Chosen?): ItemDescription? {
    chosen?.let { return it.item.description }
    return tab.types.firstOrNull()
}

/** What the button that makes one is called: *Add a dive*, *Add a person*. */
internal fun makeSaid(type: ItemDescription): String = "Add a " + labelOf(type).lowercase()

/**
 * What deleting [ids] from [set] asks, or nothing where there is nothing to ask about.
 *
 * Named where there is one, counted where there are several: a reader deleting a single dive
 * wants to see which, and one deleting twenty wants to know it is twenty rather than reading a
 * list they cannot check anyway.
 */
internal fun deleteAsked(set: ItemSet, ids: Set<String>): String? {
    val items = ids.mapNotNull { set[it] }
    if (items.isEmpty()) return null
    if (items.size == 1) return "Delete ${titleOf(items.first())}?"
    val type = items.map { it.description }.distinct().singleOrNull()
    val what = if (type == null) "items" else severalOf(type).lowercase()
    return "Delete ${items.size} $what?"
}

/**
 * What deleting says beyond the question.
 *
 * **What pointed at it goes on pointing.** A reference to something deleted is left dangling
 * rather than hunted down, which is a state the model already carries — a buddy not entered yet
 * looks the same from here — and a reader about to delete a person who is on forty dives should
 * know that before rather than after. `DATA-17`.
 */
internal fun deleteWarned(set: ItemSet, ids: Set<String>): String? {
    val pointing = ids.sumOf { pointingAt(set, it) }
    if (pointing == 0) return null
    // Both halves agree with what they count: how many point, and how many are going.
    val naming = if (pointing == 1) "One item names" else "$pointing items name"
    val going = if (ids.size == 1) "it" else "them"
    return "$naming $going, and will go on naming what is no longer there."
}

/** How many items in [set] name [id], which is what a delete leaves dangling. */
private fun pointingAt(set: ItemSet, id: String): Int =
    Types.ALL.sumOf { type ->
        set.allOf(type).count { item -> item !== set[id] && names(item, id) }
    }

/** Whether [item] names [id] anywhere a reference can sit. */
private fun names(item: Item, id: String): Boolean =
    item.description.fields.any { field ->
        val read = item.read(field.name)
        read is Result.Usable && mentions(read.value, id)
    }

/** Whether a value read off a field is, or holds, a reference to [id]. */
private fun mentions(value: Any?, id: String): Boolean = when (value) {
    is Reference.Identified -> value.id == id
    is Element.Usable<*> -> mentions(value.value, id)
    is List<*> -> value.any { mentions(it, id) }
    is Map<*, *> -> value.values.any { mentions(it, id) }
    is OwnedItem -> names(value, id)
    else -> false
}
