package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.logic.Types
import yemoja.logic.titleOf

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

/**
 * What a new item starts out holding, given the branch of the gear tree that is chosen.
 *
 * **A category or a kind chosen in the tree is an answer already given.** A reader looking at the
 * cylinders who presses **+** is adding a cylinder, and typing the word again is work the screen
 * watched them do. The branch's own words are used, not a vocabulary's: the tree is built from
 * what the logbook says, so what comes back is what is already there. `GUI-35`.
 *
 * Nothing but gear has such a tree, and a branch of none leaves the form empty.
 */
internal fun startedOf(type: ItemDescription, branch: String?): Map<String, String> {
    if (type != Types.GEAR || branch == null) return emptyMap()
    val category = branch.substringBefore('/').ifBlank { return emptyMap() }
    val kind = branch.substringAfter('/', "").ifBlank { null }
    return if (kind == null) mapOf("category" to category) else {
        mapOf("category" to category, "kind" to kind)
    }
}

/**
 * Ribbon is what the add, edit and delete buttons on the tab row act on, and why any of them is
 * greyed.
 *
 * Worked out from the tab and what it has chosen, so the buttons stand in one place whatever the
 * tab shows. `GUI-53`. A reason is absent where its button can be pressed. Immutable.
 */
internal class Ribbon(
    /** The types **+** offers, the one being looked at first. */
    val makeable: List<ItemDescription>,
    val addWhy: String?,
    /** The item the pencil turns over into its form. */
    val edited: Chosen?,
    val editWhy: String?,
    /** What the bin asks about: one item, or every dive chosen. */
    val deleted: Set<String>,
    val deleteWhy: String?,
)

/**
 * What the tab row's buttons act on in [tab], given what [kept] has chosen.
 *
 * **The item in front of the reader.** On Locations that is the site chosen, and the
 * region where none is. Several dives chosen are deleted together and edited one at a time.
 * While a form is open, for a new item or an edited one, all three wait for it to be saved or
 * cancelled, since each would take the form's place and lose what was typed. `GUI-53`.
 */
internal fun ribbonOf(tab: Tab, kept: Kept): Ribbon {
    if (tab.types.isEmpty()) {
        return Ribbon(emptyList(), NOTHING_HERE, null, NOTHING_HERE, emptySet(), NOTHING_HERE)
    }
    val here = kept.chosen ?: kept.place.takeIf { tab.shape == Shape.PLACES }
    val busy = kept.making != null || (kept.editing != null && kept.editing == here?.id)
    if (busy) return Ribbon(emptyList(), FORM_OPEN, null, FORM_OPEN, emptySet(), FORM_OPEN)
    val many = kept.chosenMany.size > 1
    val edited = here.takeIf { !many }
    val deleted = if (many) kept.chosenMany else setOfNotNull(here?.id)
    return Ribbon(
        makeable = makeableIn(tab, kept.chosen),
        addWhy = null,
        edited = edited,
        editWhy = when {
            many -> "Several dives are chosen. Choose one to edit it."
            edited == null -> "Choose something to edit."
            else -> null
        },
        deleted = deleted,
        deleteWhy = if (deleted.isEmpty()) "Choose something to delete." else null,
    )
}

private const val NOTHING_HERE = "Nothing on this tab is added, edited or deleted."

private const val FORM_OPEN = "Save or cancel the form first."

/** What the button that makes one is called: *Add a dive*, *Add a person*. */
internal fun makeSaid(type: ItemDescription): String = "Add a " + labelOf(type).lowercase()

/**
 * Every type a tab can make, the one being looked at first.
 *
 * **A tab holding two types can make either.** *Another of what you are looking at* answers the
 * common case and cannot answer the first: a logbook with no trip in it has no trip to look at,
 * so a trip could not be made at all. The rest of the tab's types follow the first, and a tab
 * holding one offers one. `GUI-35`.
 */
internal fun makeableIn(tab: Tab, chosen: Chosen?): List<ItemDescription> {
    val first = makingOf(tab, chosen) ?: return emptyList()
    return listOf(first) + tab.types.filter { it != first }
}

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
 * How many references a delete would leave dangling, said in the model's own word.
 *
 * A reference to something deleted is left dangling rather than hunted down, which is a state
 * the model already carries — a buddy not entered yet looks the same from here — and a reader
 * about to delete a person on forty dives should know before rather than after. `DATA-17`.
 *
 * **Counted as references rather than as the items holding them.** One dive naming a person
 * twice is two references and one item, and *reference* is the word the format uses for the
 * thing being counted.
 */
internal fun deleteWarned(set: ItemSet, ids: Set<String>): String? {
    val holding = holdersOf(set, ids)
    val many = holding.sumOf { it.second }
    if (many == 0) return null
    val what = if (many == 1) "One other item names" else "$many other items name"
    val going = if (ids.size == 1) "it" else "them"
    val said = "$what $going, and will be left naming something that is no longer here"
    if (holding.size > FEW) return "$said."
    return "$said: ${listedOf(holding.map { titleOf(it.first) })}."
}

/**
 * How many items may be named before they are merely counted.
 *
 * A reader can check three names against what they meant to delete; they cannot check forty,
 * and a dialog that tries turns the question into a wall. The same handful `LOGIC-18` offers of
 * the sites nearest a fix, for the same reason.
 */
private const val FEW = 3

/** Each item in [set] holding a reference to any of [ids], and how many it holds. */
private fun holdersOf(set: ItemSet, ids: Set<String>): List<Pair<Item, Int>> {
    val going = ids.mapNotNull { set[it] }
    val out = ArrayList<Pair<Item, Int>>()
    for (type in Types.ALL) {
        for (item in set.allOf(type)) {
            if (going.any { it === item }) continue
            val many = ids.sumOf { referencesIn(item, it) }
            if (many > 0) out += item to many
        }
    }
    return out
}

/** A handful of names as a sentence reads them: *a*, *a and b*, *a, b and c*. */
private fun listedOf(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.first()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/**
 * How many references to [id] sit anywhere in [item], its owned items included.
 *
 * **A derived reference is not counted.** It is worked out afresh every time it is read, so it
 * cannot be left pointing at nothing: a site's `dives` follows from the dives naming the site
 * and answers without the deleted one the moment it is gone. Counting it would warn a reader
 * about something that puts itself right.
 */
private fun referencesIn(item: Item, id: String): Int =
    item.description.fields.sumOf { field ->
        when (val read = item.read(field.name)) {
            is Result.Usable ->
                if (read.origin == Result.Origin.DERIVED) 0 else referencesAmong(read.value, id)

            else -> 0
        }
    }

/** How many references to [id] a value read off a field is, or holds. */
private fun referencesAmong(value: Any?, id: String): Int = when (value) {
    is Reference.Identified -> if (value.id == id) 1 else 0
    is Element.Usable<*> -> referencesAmong(value.value, id)
    is List<*> -> value.sumOf { referencesAmong(it, id) }
    is Map<*, *> -> value.values.sumOf { referencesAmong(it, id) }
    is OwnedItem -> referencesIn(value, id)
    else -> 0
}
