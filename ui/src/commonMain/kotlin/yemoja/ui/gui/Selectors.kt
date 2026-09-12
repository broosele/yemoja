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

/** Year is one year's rows of the dive table, in the order the table keeps them. */
internal class Year(val label: String, val rows: List<DiveRow>)

/**
 * The dive table by year, newest year first, each year's trip runs counted within it.
 *
 * A trip that ran over New Year is two runs, one in each year, since a year is a division of
 * the table and a cell does not cross one. A dive with no date is under a year of its own,
 * named for having none.
 */
internal fun yearsOf(rows: List<DiveRow>): List<Year> {
    val years = LinkedHashMap<String, MutableList<DiveRow>>()
    for (row in rows) {
        val year = row.date.take(4).ifBlank { UNDATED }
        years.getOrPut(year) { ArrayList() } += row
    }
    return years.map { (year, its) -> Year(year, runsIn(its)) }
}

/** The year a dive with no date is filed under. */
internal const val UNDATED = "undated"

/** The label of the year among [years] holding the dive [id], or absent where none does. */
internal fun yearHolding(years: List<Year>, id: String?): String? =
    id?.let { years.firstOrNull { year -> year.rows.any { it.dive.id == id } }?.label }

/**
 * Which rows carry a trip's name, so it sits in the middle of its run: the row at the middle of
 * each run, and whether the name is to be nudged up half a line because the run has an even
 * number of rows and its middle is a boundary.
 */
internal fun labelledOf(rows: List<DiveRow>): Map<Int, Boolean> {
    val labelled = LinkedHashMap<Int, Boolean>()
    for ((index, row) in rows.withIndex()) {
        if (row.run == 0 || row.trip == null) continue
        labelled[index + row.run / 2] = row.run % 2 == 0
    }
    return labelled
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
 * What is at a place: the dive sites in [region] or anywhere inside it, and the wrecks lying at
 * those sites.
 *
 * Inside it, because a site in Zeeland is in the Netherlands and in Europe, and a reader who
 * opens Europe is asking what there is to dive there. One list for sites and wrecks, because a
 * wreck has no place of its own — it has no region and no position, and a site names the wrecks
 * at it. `GUI-20`. So a wreck at no site is under no region, and one named by two sites is under
 * both.
 */
internal fun atPlaceIn(
    set: ItemSet,
    region: String,
    hideUnused: Boolean = false,
): Pair<List<Chosen>, List<Chosen>> {
    val within = withinOf(set, region)
    val used = if (hideUnused) usedSitesIn(set) else null
    val sites = entriesOf(set, Types.DIVE_SITE)
        .filter { used == null || it.id in used }
        .filter { pointedAtAll(it.item, "regions").any { named -> named in within } }
    val wrecks = LinkedHashMap<String, Chosen>()
    for (site in sites) {
        for (id in pointedAtAll(site.item, "wrecks")) {
            set[id]?.let { wrecks.getOrPut(id) { Chosen(id, titleOf(it), it) } }
        }
    }
    return sites to wrecks.values.toList()
}

/** [region] and every region inside it, however deep. A cycle is walked once. `LOGIC-8`. */
internal fun withinOf(set: ItemSet, region: String): Set<String> {
    val within = LinkedHashSet<String>()
    val pending = ArrayDeque(listOf(region))
    while (pending.isNotEmpty()) {
        val next = pending.removeFirst()
        if (!within.add(next)) continue
        set[next]?.let { pending += childrenOf(it) }
    }
    return within
}

// --- What is used. `GUI-26`.

/** The dive sites any dive names. */
internal fun usedSitesIn(set: ItemSet): Set<String> =
    entriesOf(set, Types.DIVE).mapNotNullTo(HashSet()) { pointedAt(it.item, "dive_site") }

/**
 * The region tree with what is unused taken out, or whole where [hideUnused] is off.
 *
 * Unused is a site no dive names, and a region with no such site at it or anywhere inside it.
 * A region left with one child and no site of its own is cut out and the child takes its
 * place, since a branch that only leads on is a click for nothing. A child reached twice that
 * way, through two parents both cut out, is kept once.
 */
internal fun shownTreeOf(set: ItemSet, hideUnused: Boolean): List<Branch> {
    val tree = regionTreeOf(set)
    if (!hideUnused) return tree
    val used = usedSitesIn(set)
    // How many used sites name each region directly, which is what keeps a region its own line.
    val direct = HashMap<String, Int>()
    for (site in entriesOf(set, Types.DIVE_SITE)) {
        if (site.id !in used) continue
        for (region in pointedAtAll(site.item, "regions")) {
            direct[region] = (direct[region] ?: 0) + 1
        }
    }
    return tree.flatMap { prunedOf(it, direct) }.distinctBy { it.key }
}

/** A branch pruned: nothing, itself trimmed, or the one child that stands in for it. */
private fun prunedOf(branch: Branch, direct: Map<String, Int>): List<Branch> {
    val children = branch.children.flatMap { prunedOf(it, direct) }.distinctBy { it.key }
    val own = branch.held.sumOf { direct[it.id] ?: 0 }
    return when {
        children.isEmpty() && own == 0 -> emptyList()
        children.size == 1 && own == 0 -> children
        else -> listOf(Branch(branch.key, branch.label, children, branch.held))
    }
}

/**
 * The region a site is best looked at on: the most specific of the ones it names, which is the
 * one with the smallest frame. A wreck is looked at on the first site it lies at.
 *
 * A site in Egypt and in the Red Sea has no one region, and a map of either would hold it; the
 * smaller is the closer look, and a region with no frame at all comes last. Absent where the
 * item names no region. `GUI-28`.
 */
internal fun homeOf(set: ItemSet, item: Item): String? {
    val regions = when (item.description) {
        Types.DIVE_SITE -> pointedAtAll(item, "regions")
        Types.WRECK -> pointedAtAll(item, "dive_sites").firstOrNull()
            ?.let { set[it] }?.let { pointedAtAll(it, "regions") }.orEmpty()
        else -> emptyList()
    }
    return regions.minByOrNull { id ->
        val region = set[id] ?: return@minByOrNull Double.MAX_VALUE
        val frame = frameOf(region, dotsOf(atPlaceIn(set, id).first))
        frame?.let { it.width * it.height } ?: Double.MAX_VALUE
    }
}

// --- The map. `GUI-25`.

/** A dive site as a dot on a map: where it is, and what to call it. */
internal class Dot(val id: String, val title: String, val latitude: Double, val longitude: Double)

/** The sites among [sites] that say where they are. A site without a position is not on a map. */
internal fun dotsOf(sites: List<Chosen>): List<Dot> = sites.mapNotNull { site ->
    val latitude = numberOf(site.item, "latitude") ?: return@mapNotNull null
    val longitude = numberOf(site.item, "longitude") ?: return@mapNotNull null
    Dot(site.id, site.title, latitude, longitude)
}

/**
 * Frame is the box a map shows: four edges in degrees.
 *
 * [east] is reached travelling east from [west], so a frame across the date line has an east
 * below its west, and a longitude is placed by how far east of [west] it lies.
 */
internal class Frame(val west: Double, val east: Double, val south: Double, val north: Double) {

    /** How far east of the west edge, in degrees, wrapping once. */
    fun eastOf(longitude: Double): Double = ((longitude - west) % 360.0 + 360.0) % 360.0

    val width: Double get() = eastOf(east).let { if (it == 0.0) 360.0 else it }

    val height: Double get() = north - south

    /**
     * Where a point lands on a canvas of [wide] by [high], in pixels from the top left.
     *
     * The frame is fitted inside the canvas and centred, a degree of longitude drawn narrower
     * than one of latitude by the cosine of the middle latitude, which is what keeps a square
     * bay square. A point is drawn on whichever side of the frame is nearer, so what lies just
     * west of the west edge is just off the left, not a world away on the right.
     */
    fun place(
        latitude: Double,
        longitude: Double,
        wide: Double,
        high: Double,
    ): Pair<Double, Double> = placeEast(latitude, nearest(eastOf(longitude)), wide, high)

    /** As [place], for a point [eastDegrees] east of the west edge, which may be negative. */
    fun placeEast(
        latitude: Double,
        eastDegrees: Double,
        wide: Double,
        high: Double,
    ): Pair<Double, Double> {
        val squeeze = kotlin.math.cos(Math.toRadians((south + north) / 2))
        val scale = minOf(wide / (width * squeeze), high / height)
        val drawnWide = width * squeeze * scale
        val drawnHigh = height * scale
        val x = (wide - drawnWide) / 2 + eastDegrees * squeeze * scale
        val y = (high - drawnHigh) / 2 + (north - latitude) * scale
        return x to y
    }

    /**
     * How far east of the west edge an outline from [west] to [east] starts, unwrapped so the
     * outline is drawn whole and on the side of the frame nearer its middle.
     *
     * Per outline rather than per point, because a coast that straddles the west edge has
     * points on both sides of it, and wrapping them one by one would draw the edges between
     * them right across the map.
     */
    fun startOf(west: Double, east: Double): Double {
        val start = eastOf(west)
        return if (start + (east - west) / 2 - width / 2 > 180.0) start - 360.0 else start
    }

    /** An offset east of the west edge, brought within half a turn of the frame's middle. */
    private fun nearest(eastDegrees: Double): Double =
        if (eastDegrees - width / 2 > 180.0) eastDegrees - 360.0 else eastDegrees

    /**
     * The frame a canvas of [wide] by [high] actually shows: this one, and whatever the fit
     * leaves room for beside or above it. Never wider than the world or taller than the poles.
     */
    fun shown(wide: Double, high: Double): Frame {
        val squeeze = kotlin.math.cos(Math.toRadians((south + north) / 2))
        val scale = minOf(wide / (width * squeeze), high / height)
        val lonSpan = minOf(360.0, wide / (squeeze * scale))
        val latSpan = high / scale
        val midLat = (south + north) / 2
        val whole = lonSpan >= 360.0
        return Frame(
            west = if (whole) -180.0 else wrapped(west + width / 2 - lonSpan / 2),
            east = if (whole) 180.0 else wrapped(west + width / 2 + lonSpan / 2),
            south = maxOf(-90.0, midLat - latSpan / 2),
            north = minOf(90.0, midLat + latSpan / 2),
        )
    }
}

/** A longitude brought back into -180 to 180. */
private fun wrapped(longitude: Double): Double {
    val turned = (longitude + 180.0) % 360.0
    return (turned + 360.0) % 360.0 - 180.0
}

/**
 * The frame a region's map shows: the region's own box where it has one, and otherwise a box
 * drawn round its dots with room to spare, or nothing where there is neither.
 */
internal fun frameOf(region: Item, dots: List<Dot>): Frame? {
    val west = numberOf(region, "west")
    val east = numberOf(region, "east")
    val south = numberOf(region, "south")
    val north = numberOf(region, "north")
    if (west != null && east != null && south != null && north != null) {
        return Frame(west, east, south, north)
    }
    if (dots.isEmpty()) return null
    val latitudes = dots.map { it.latitude }
    val longitudes = dots.map { it.longitude }
    val tall = maxOf(latitudes.max() - latitudes.min(), LEAST_SPAN)
    val wide = maxOf(longitudes.max() - longitudes.min(), LEAST_SPAN)
    return Frame(
        west = longitudes.min() - wide * ROOM,
        east = longitudes.max() + wide * ROOM,
        south = latitudes.min() - tall * ROOM,
        north = latitudes.max() + tall * ROOM,
    )
}

/** Degrees a drawn-round frame is never narrower than, so one site is not a map of one pixel. */
private const val LEAST_SPAN = 0.05

/** How much of the span is left round the dots on each side. */
private const val ROOM = 0.15

private fun numberOf(item: Item, field: String): Double? =
    ((item.read(field) as? Result.Usable)?.value as? Number)?.toDouble()

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
