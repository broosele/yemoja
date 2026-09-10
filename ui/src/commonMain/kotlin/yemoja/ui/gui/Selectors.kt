package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.inOrder
import yemoja.logic.Types

/*
 * What each tab's selector holds, worked out from the logbook and holding no screen in it.
 *
 * Four shapes rather than four variations on a list, which is what `GUI-14` settled the question
 * was. Each is built here and drawn in Screens.kt, so what a selector *is* can be tested without
 * an interface at all — which ../../../../../../gui/doc.md gives as the reason for the split.
 *
 * See ../../../../../../gui/doc.md — `GUI-19` to `GUI-22`.
 */

/** One item a selector offers: what it is called, and how to reach it. */
internal class Chosen(val id: String, val title: String, val item: ReferenceableItem)

/** Everything of one type, in the order the type asks for. `DATA-89`. */
internal fun entriesOf(set: ItemSet, type: ItemDescription): List<Chosen> =
    set.inOrder(type).mapNotNull { item -> set.idOf(item)?.let { Chosen(it, titleOf(item), item) } }

// --- The dive table. `GUI-19`.

/**
 * One row of the dive table: a trip, a number, a date and a site.
 *
 * [run] is how many rows the trip cell spans, counted on the first row of each run and zero
 * after it. A run is *consecutive* dives on one trip, so the same trip met again later is a
 * second run, and a dive on no trip is a run of one. `GUI-24` is what becomes of this when the
 * table is sorted by something other than the date.
 */
internal class DiveRow(
    val dive: Chosen,
    val trip: Chosen?,
    val number: String,
    val date: String,
    val site: String,
    val run: Int,
)

/** The dive table, in the order dives are kept: newest first. */
internal fun diveRowsOf(set: ItemSet): List<DiveRow> {
    val rows = entriesOf(set, Types.DIVE).map { dive ->
        val trip = tripOf(set, dive.item)
        DiveRow(
            dive = dive,
            trip = trip,
            number = read(dive.item, "dive_number"),
            date = read(dive.item, "start_date"),
            site = named(set, dive.item, "dive_site"),
            run = 0,
        )
    }
    return runsIn(rows)
}

/** The same rows, each run of one trip counted on the row that opens it. */
private fun runsIn(rows: List<DiveRow>): List<DiveRow> {
    val out = ArrayList<DiveRow>(rows.size)
    var at = 0
    while (at < rows.size) {
        var to = at + 1
        // A dive on no trip never joins the run above it: nothing is the same as nothing.
        while (rows[at].trip != null && to < rows.size && rows[to].trip?.id == rows[at].trip?.id) {
            to += 1
        }
        out += rows[at].copy(run = to - at)
        for (after in at + 1..<to) out += rows[after].copy(run = 0)
        at = to
    }
    return out
}

private fun DiveRow.copy(run: Int) = DiveRow(dive, trip, number, date, site, run)

/** The trip a dive was made on, which sits inside the item it owns rather than on the dive. */
private fun tripOf(set: ItemSet, dive: Item): Chosen? {
    val details = (dive.single<yemoja.data.OwnedItem>("details") as? Result.Usable)?.value
        ?: return null
    val id = pointedAt(details, "dive_trip") ?: return null
    val trip = set[id] ?: return null
    return Chosen(id, titleOf(trip), trip)
}

// --- The gear tree. `GUI-21`.

/** A branch of a tree: what it is called, what hangs under it, and what it holds. */
internal class Branch(
    val key: String,
    val label: String,
    val children: List<Branch> = emptyList(),
    val held: List<Chosen> = emptyList(),
)

/**
 * Gear by category, and by kind within a category.
 *
 * Both words are the logbook's rather than this file's, so the branches come from what is there.
 * Gear filed under no category sits at the top rather than in a bucket called *other*: it is not
 * filed yet, which is a different thing from being filed as miscellaneous.
 */
internal fun gearTreeOf(set: ItemSet): Pair<List<Branch>, List<Chosen>> {
    val all = entriesOf(set, Types.GEAR)
    val categories = LinkedHashMap<String, MutableList<Chosen>>()
    val loose = ArrayList<Chosen>()
    for (item in all) {
        val category = read(item.item, "category").ifBlank { null }
        if (category == null) {
            loose += item
        } else {
            categories.getOrPut(category) { ArrayList() } += item
        }
    }
    val branches = categories.entries.sortedBy { it.key.lowercase() }.map { (category, held) ->
        val kinds = LinkedHashMap<String, MutableList<Chosen>>()
        val plain = ArrayList<Chosen>()
        for (item in held) {
            val kind = read(item.item, "kind").ifBlank { null }
            if (kind == null) plain += item else kinds.getOrPut(kind) { ArrayList() } += item
        }
        Branch(
            key = category,
            label = category,
            children = kinds.entries.sortedBy { it.key.lowercase() }
                .map { (kind, its) -> Branch("$category/$kind", kind, held = its) },
            held = plain,
        )
    }
    return branches to loose
}

// --- The region tree. `GUI-22`.

/**
 * Regions as a tree, a region appearing under every parent that names it.
 *
 * **A branch that meets a region already above it stops.** `parents` may return to where it
 * started, which `LOGIC-8` says nothing prevents, and a tree drawn from a cycle would not
 * terminate. Stopping is not silence: the branch is marked so a reader sees the mistake rather
 * than a tree that quietly ends.
 */
internal fun regionTreeOf(set: ItemSet): List<Branch> {
    val all = entriesOf(set, Types.REGION)
    val byId = all.associateBy { it.id }
    val roots = all.filter { parentsOf(it.item).none { parent -> parent in byId } }
    val tree = roots.map { branchOf(it, byId, emptySet()) }
    // A cycle has no root, so a region caught in one is under nothing and would be in no tree
    // at all. Unreachable is worse than wrong, so what the roots did not place stands as a root
    // of its own, marked. `LOGIC-8`.
    val placed = HashSet<String>()
    for (branch in tree) gather(branch, placed)
    val lost = all.filter { it.id !in placed }
    return tree + lost.map {
        Branch(it.id, it.title + CIRCULAR, held = listOf(it))
    }
}

/** Every region a branch placed, itself and everything under it. */
private fun gather(branch: Branch, into: MutableSet<String>) {
    into += branch.held.map { it.id }
    for (child in branch.children) gather(child, into)
}

private fun branchOf(region: Chosen, byId: Map<String, Chosen>, above: Set<String>): Branch {
    if (region.id in above) return Branch(region.id, region.title + CIRCULAR, held = listOf(region))
    val within = above + region.id
    val children = childrenOf(region.item).mapNotNull { byId[it] }
        .map { branchOf(it, byId, within) }
    return Branch(region.id, region.title, children = children, held = listOf(region))
}

/** That a region lies inside itself, which is a mistake worth saying rather than hiding. */
private const val CIRCULAR = " (inside itself)"

private fun parentsOf(region: Item): List<String> = pointedAtAll(region, "parents")

private fun childrenOf(region: Item): List<String> = pointedAtAll(region, "children")

/**
 * What is at a place: the dive sites in [region], and the wrecks lying at those sites.
 *
 * One list, because a wreck has no place of its own — it has no region and no position, and a
 * site names the wrecks at it. `GUI-20`. So a wreck at no site is under no region, and one named
 * by two sites is under both.
 */
internal fun atPlaceIn(set: ItemSet, region: String): Pair<List<Chosen>, List<Chosen>> {
    val sites = entriesOf(set, Types.DIVE_SITE)
        .filter { region in pointedAtAll(it.item, "regions") }
    val wrecks = LinkedHashMap<String, Chosen>()
    for (site in sites) {
        for (id in pointedAtAll(site.item, "wrecks")) {
            set[id]?.let { wrecks.getOrPut(id) { Chosen(id, titleOf(it), it) } }
        }
    }
    return sites to wrecks.values.toList()
}

// --- Reading a field, which every shape above does and none of them does differently.

/** One field as a file would write it, or empty where it says nothing. */
private fun read(item: Item, field: String): String {
    val described = item.description[field] ?: return ""
    val held = item.read(field) as? Result.Usable ?: return ""
    return described.format(held.value, yemoja.data.Units.DEFAULT)
}

/** What a reference points at, by its name rather than by the id it is written as. */
private fun named(set: ItemSet, item: Item, field: String): String {
    val id = pointedAt(item, field) ?: return read(item, field)
    return set[id]?.let { titleOf(it) } ?: read(item, field)
}

private fun pointedAt(item: Item, field: String): String? {
    val held = (item.single<Reference>(field) as? Result.Usable)?.value
    return (held as? Reference.Identified)?.id
}

@Suppress("UNCHECKED_CAST")
private fun pointedAtAll(item: Item, field: String): List<String> {
    val held = (item.list<Reference>(field) as? Result.Usable)?.value ?: return emptyList()
    return held.mapNotNull { ((it as? Element.Usable)?.value as? Reference.Identified)?.id }
}
