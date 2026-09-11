package yemoja.ui.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yemoja.data.ItemSet
import yemoja.logic.Universe

/*
 * The application's screens, which are one definition for both form factors.
 *
 * Reading only. Nothing here changes anything, which is the first version's whole scope.
 *
 * The look is the platform's, Material in the application's own colours and nothing else of
 * ours. `GUI-3`. Every colour here is a role from the scheme, never a value, so Palette.kt is
 * the one place the colours are.
 *
 * See ../../../../../../gui/doc.md — the tabs, the three views and the shape of each selector
 * are settled there; this draws them.
 */

/**
 * Platform is what the screens are given by whatever hosts them, and know nothing of the source
 * of.
 */
internal class Platform(
    /** The manual's chapters, in reading order. */
    val manual: List<Chapter>,
    /** The map, read when asked and off the interface's thread: it is fifteen megabytes. */
    val atlas: () -> Atlas,
    /** Follows a link that leads out of the application. */
    val open: (String) -> Unit,
)

/** The whole application: a tab across the top, and whatever that tab shows. */
@Composable
internal fun Application(universe: Universe, platform: Platform) {
    // Home, which is where the application opens whatever it holds yet.
    var tab by remember { mutableStateOf(TABS.first()) }
    // Absent until read, and a map drawn before then shows its sites on an empty frame.
    val atlas by produceState<Atlas?>(null, platform) {
        value = withContext(Dispatchers.Default) { platform.atlas() }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Tabs(tab) { tab = it }
            Box(modifier = Modifier.weight(1f)) {
                when (tab.shape) {
                    Shape.NONE -> Owed(tab)
                    Shape.MANUAL -> Manuals(platform.manual, platform.open)
                    else -> Subject(universe.logbook, tab, atlas)
                }
            }
        }
    }
}

/**
 * The tabs, across the top.
 *
 * Seven fit comfortably across a desktop window and do not fit across the foot of a phone.
 * `GUI-13` settled that a phone keeps this a gesture away instead, which is a placement rather
 * than a different set of tabs.
 */
@Composable
private fun Tabs(chosen: Tab, onChoose: (Tab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tabs as wide as their names, so the row's own rule would stop where they do. The
        // rule is drawn below instead, across the whole window.
        ScrollableTabRow(
            selectedTabIndex = TABS.indexOf(chosen),
            edgePadding = GAP,
            divider = {},
        ) {
            for (tab in TABS) {
                LeadingIconTab(
                    selected = tab === chosen,
                    onClick = { onChoose(tab) },
                    text = { Text(tab.name) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                )
            }
        }
        HorizontalDivider()
    }
}

/** A tab with nothing behind it yet, saying what it will hold rather than nothing at all. */
@Composable
private fun Owed(tab: Tab) {
    Middle("${tab.name}: ${tab.owed}")
}

/**
 * A subject: what it holds on the left, and the one chosen on the right.
 *
 * Location chooses two things, a region and something at it, and shows both. `GUI-25`.
 */
@Composable
private fun Subject(set: ItemSet, tab: Tab, atlas: Atlas?) {
    var chosen by remember(tab) { mutableStateOf<Chosen?>(null) }
    // On, so a logbook opens on where its dives were rather than on the whole atlas. `GUI-26`.
    var hideUnused by remember(tab) { mutableStateOf(true) }
    val tree = remember(set, hideUnused) { shownTreeOf(set, hideUnused) }
    // The widest root, which is the world, so the map opens showing everything.
    var place by remember(tab) { mutableStateOf(widestRootIn(tree)) }
    val wide = when (tab.shape) {
        Shape.DIVES -> TABLE
        Shape.PLACES -> TREE + SITES
        else -> SELECTOR
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(wide)) {
            when (tab.shape) {
                Shape.DIVES -> Dives(set, chosen) { chosen = it }
                Shape.GEAR -> Gear(set, chosen) { chosen = it }
                Shape.TYPES -> Types(set, tab, chosen) { chosen = it }
                Shape.PLACES -> Places(
                    set = set,
                    tree = tree,
                    hideUnused = hideUnused,
                    onHide = { hideUnused = it },
                    place = place,
                    chosen = chosen,
                    onPlace = { region ->
                        place = region
                        // What was chosen stays chosen while the new region still lists it.
                        val (sites, wrecks) = atPlaceIn(set, region.id, hideUnused)
                        if ((sites + wrecks).none { it.id == chosen?.id }) chosen = null
                    },
                    onChoose = { chosen = it },
                )
                Shape.MANUAL, Shape.NONE -> Unit
            }
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            if (tab.shape == Shape.PLACES) {
                PlaceView(set, atlas, hideUnused, place, chosen)
            } else {
                chosen?.let { ItemView(it) } ?: Middle("choose something on the left")
            }
        }
    }
}

// --- The dive table.

/**
 * The dive table: the trip, the dive's own number, the date and the site.
 *
 * The trip cell spans the consecutive dives on it, and which column is clicked decides what is
 * selected. `GUI-19`. The span is drawn as one: the cell is tinted down the whole run, its title
 * sits on the first row, and a rule separates one run from the next rather than one row from
 * the next.
 */
@Composable
private fun Dives(set: ItemSet, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    val rows = remember(set) { diveRowsOf(set) }
    Column(modifier = Modifier.fillMaxHeight().padding(horizontal = GAP)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(LINE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Heading("Trip", TRIP)
            Heading("No.", NUMBER, TextAlign.End)
            Heading("Date", DATE)
            Heading("Site", SITE)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxHeight()) {
            itemsIndexed(rows, key = { _, row -> row.dive.id }) { index, row ->
                if (index > 0 && row.run > 0) HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().height(LINE)) {
                    TripCell(row, chosen, onChoose)
                    val here = row.dive.id == chosen?.id
                    Row(
                        modifier = Modifier.fillMaxHeight().clip(SHAPE).background(tint(here))
                            .clickable { onChoose(row.dive) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cell(row.number, NUMBER, here, TextAlign.End)
                        Cell(row.date, DATE, here)
                        Cell(row.site, SITE, here)
                    }
                }
            }
        }
    }
}

/** The trip cell, tinted down the run and clickable wherever the run is. */
@Composable
private fun TripCell(row: DiveRow, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    val trip = row.trip
    val here = trip != null && trip.id == chosen?.id
    Box(
        modifier = Modifier.width(TRIP).fillMaxHeight()
            .background(
                when {
                    here -> MaterialTheme.colorScheme.secondaryContainer
                    trip != null -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                },
            )
            .let { if (trip == null) it else it.clickable { onChoose(trip) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (row.run > 0 && trip != null) {
            Text(
                text = trip.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                color = onTint(here),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HALF),
            )
        }
    }
}

/** A column heading. */
@Composable
private fun Heading(text: String, width: Dp, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = align,
        modifier = Modifier.width(width).padding(horizontal = HALF),
    )
}

/** One cell of a dive's own columns. */
@Composable
private fun Cell(text: String, width: Dp, chosen: Boolean, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = onTint(chosen),
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).padding(horizontal = HALF),
    )
}

// --- The trees and the lists.

/**
 * Gear by category and by the kind within it, each branch closing on a click.
 *
 * Everything opens unfolded. A tree that opens folded hides what it holds behind a click per
 * branch, which for a few dozen items is a cost with nothing bought.
 */
@Composable
private fun Gear(set: ItemSet, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    val (branches, loose) = remember(set) { gearTreeOf(set) }
    var closed by remember(set) { mutableStateOf(emptySet<String>()) }
    val toggle = { key: String -> closed = if (key in closed) closed - key else closed + key }
    LazyColumn(modifier = Modifier.fillMaxHeight().padding(GAP)) {
        // Filed under nothing sits at the top rather than in a bucket called other. `GUI-21`.
        items(loose, key = { "loose:" + it.id }) { Entry(it, chosen, 0, onChoose) }
        // A category is keyed apart from the gear in it, in case one is named like an id.
        for (branch in branches) {
            item(key = "branch:" + branch.key) {
                BranchLine(
                    label = branch.label,
                    depth = 0,
                    open = branch.key !in closed,
                    chosen = false,
                    onToggle = { toggle(branch.key) },
                    onClick = { toggle(branch.key) },
                )
            }
            if (branch.key in closed) continue
            items(branch.held, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
            for (kind in branch.children) {
                item(key = "branch:" + kind.key) {
                    BranchLine(
                        label = kind.label,
                        depth = 1,
                        open = kind.key !in closed,
                        chosen = false,
                        onToggle = { toggle(kind.key) },
                        onClick = { toggle(kind.key) },
                    )
                }
                if (kind.key in closed) continue
                items(kind.held, key = { it.id }) { Entry(it, chosen, 2, onChoose) }
            }
        }
    }
}

/** A subtab per type, which for now is a heading per type. */
@Composable
private fun Types(set: ItemSet, tab: Tab, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxHeight().padding(GAP)) {
        for (type in tab.types) {
            item(key = "head:" + type.name) { Label(labelOf(type), 0) }
            items(entriesOf(set, type), key = { it.id }) { Entry(it, chosen, 1, onChoose) }
        }
    }
}

/**
 * A tree of regions, and what is at the chosen one.
 *
 * A region under every parent that names it, so one in two larger places is reachable twice.
 * `GUI-22`. What is at it is its sites and the wrecks lying at them, in one list. `GUI-20`.
 * The arrow opens a branch and the name chooses it, which are two acts and are two targets.
 *
 * Only the roots open unfolded. The library's regions put the world under this tree, and a
 * region under every parent that names it runs to fifteen hundred lines fully open.
 */
@Composable
private fun Places(
    set: ItemSet,
    tree: List<Branch>,
    hideUnused: Boolean,
    onHide: (Boolean) -> Unit,
    place: Chosen?,
    chosen: Chosen?,
    onPlace: (Chosen) -> Unit,
    onChoose: (Chosen) -> Unit,
) {
    var open by remember(set) { mutableStateOf(tree.map { "/" + it.key }.toSet()) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(TREE).fillMaxHeight().padding(GAP)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onHide(!hideUnused) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hideUnused, onCheckedChange = onHide)
                Text("Hide unused", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                branchesIn(
                    branches = tree,
                    path = "",
                    depth = 0,
                    chosen = place,
                    open = open,
                    onToggle = { key -> open = if (key in open) open - key else open + key },
                    onChoose = onPlace,
                )
            }
        }
        VerticalDivider()
        val what = remember(set, place, hideUnused) {
            place?.let { atPlaceIn(set, it.id, hideUnused) }
        }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            if (what == null) return@LazyColumn
            item(key = "sites") { Label("Sites", 0) }
            if (what.first.isEmpty()) item(key = "nosite") { Aside("none here") }
            items(what.first, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
            if (what.second.isEmpty()) return@LazyColumn
            item(key = "wrecks") { Label("Wrecks", 0) }
            items(what.second, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
        }
    }
}

/** The root region with the widest frame, which is the world wherever the atlas is present. */
private fun widestRootIn(tree: List<Branch>): Chosen? =
    tree.mapNotNull { it.held.firstOrNull() }
        .maxByOrNull { frameOf(it.item, emptyList())?.width ?: 0.0 }

/**
 * Every open branch of a tree, flattened with its depth, since a lazy list holds no nesting.
 *
 * A line is keyed by its whole path from the root, because a region under two parents is in
 * the list twice and a list refuses two lines with one key. The path is also what opens: a
 * branch unfolded under one parent stays folded under the other.
 */
private fun LazyListScope.branchesIn(
    branches: List<Branch>,
    path: String,
    depth: Int,
    chosen: Chosen?,
    open: Set<String>,
    onToggle: (String) -> Unit,
    onChoose: (Chosen) -> Unit,
) {
    for (branch in branches) {
        val key = path + "/" + branch.key
        item(key = key) {
            BranchLine(
                label = branch.label,
                depth = depth,
                open = if (branch.children.isEmpty()) null else key in open,
                chosen = branch.held.any { it.id == chosen?.id },
                onToggle = { onToggle(key) },
                onClick = { branch.held.firstOrNull()?.let(onChoose) },
            )
        }
        if (key in open) {
            branchesIn(branch.children, key, depth + 1, chosen, open, onToggle, onChoose)
        }
    }
}

/** One line of a tree: an arrow where there is something to close, and the name. */
@Composable
internal fun BranchLine(
    label: String,
    depth: Int,
    open: Boolean?,
    chosen: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = INDENT * depth).clip(SHAPE)
            .background(tint(chosen)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(LINE)
                .let { if (open == null) it else it.clickable(onClick = onToggle) },
            contentAlignment = Alignment.Center,
        ) {
            if (open != null) {
                Icon(
                    imageVector = if (open) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight,
                    contentDescription = if (open) "close" else "open",
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = onTint(chosen),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = HALF),
        )
    }
}

/** A heading in a selector: a type, a category, a kind. */
@Composable
private fun Label(text: String, depth: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = INDENT * depth, top = GAP, bottom = HALF),
    )
}

/** One item a selector offers. */
@Composable
private fun Entry(entry: Chosen, chosen: Chosen?, depth: Int, onChoose: (Chosen) -> Unit) {
    Line(entry.title, depth, chosen = entry.id == chosen?.id) { onChoose(entry) }
}

/** One line of a list, tinted where it is the one chosen. */
@Composable
internal fun Line(text: String, depth: Int, chosen: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = onTint(chosen),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(start = INDENT * depth).clip(SHAPE)
            .background(tint(chosen)).clickable(onClick = onClick)
            .padding(horizontal = GAP, vertical = HALF),
    )
}

// --- The item view.

/** One item, arranged for reading. It never changes anything. */
@Composable
private fun ItemView(chosen: Chosen) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ItemCard(chosen)
    }
}

/**
 * A place: the map of the region chosen, then the region, then whatever was chosen at it.
 *
 * Both at once and one below the other, because a site is read against where it is. `GUI-25`.
 */
@Composable
private fun PlaceView(
    set: ItemSet,
    atlas: Atlas?,
    hideUnused: Boolean,
    place: Chosen?,
    chosen: Chosen?,
) {
    if (place == null && chosen == null) {
        Middle("choose a region on the left")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (place != null) {
            val dots = remember(set, place, hideUnused) {
                dotsOf(atPlaceIn(set, place.id, hideUnused).first)
            }
            val frame = remember(set, place) { frameOf(place.item, dots) }
            if (frame != null) RegionMap(atlas?.layerFor(frame), frame, dots, chosen?.id)
            ItemCard(place)
        }
        if (chosen != null) ItemCard(chosen)
    }
}

/**
 * A region's frame drawn from the atlas, its sites as dots on it, and the chosen one marked and
 * named.
 *
 * Land over sea, lakes back in sea, then rivers, borders and the cities the scale names, then
 * the sites, and the marked one last so nothing lies over it. What the frame's fit leaves room
 * for beside it is drawn too, since a map cut off at a box's edge looks like a mistake. The
 * paths are built once per size and frame and only drawn after that, which is what keeps a
 * coastline of a hundred thousand points from being rebuilt on every frame.
 */
@Composable
private fun RegionMap(layer: Layer?, frame: Frame, dots: List<Dot>, marked: String?) {
    val sea = MaterialTheme.colorScheme.primaryContainer
    val land = MaterialTheme.colorScheme.surface
    val border = MaterialTheme.colorScheme.outlineVariant
    val river = MaterialTheme.colorScheme.primary.copy(alpha = RIVER)
    val town = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val mark = MaterialTheme.colorScheme.error
    val townLabel = MaterialTheme.typography.labelSmall.copy(color = town)
    val siteLabel = MaterialTheme.typography.labelMedium.copy(color = ink)
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier = Modifier.fillMaxWidth().height(MAP).clip(MaterialTheme.shapes.medium)
            .drawWithCache {
                val wide = size.width.toDouble()
                val high = size.height.toDouble()
                fun at(latitude: Double, longitude: Double): Offset {
                    val (x, y) = frame.place(latitude, longitude, wide, high)
                    return Offset(x.toFloat(), y.toFloat())
                }
                val shown = frame.shown(wide, high)
                fun paths(shapes: List<Outline>?, closed: Boolean): List<Path> =
                    shapes.orEmpty()
                        .filter { shown.overlaps(it.west, it.east, it.south, it.north) }
                        .map { pathOf(it, closed, frame, wide, high) }
                val lands = paths(layer?.land, closed = true)
                val lakes = paths(layer?.lakes, closed = true)
                val rivers = paths(layer?.rivers, closed = false)
                val borders = paths(layer?.borders, closed = false)
                val rank = ranksNamedIn(frame)
                val towns = layer?.cities.orEmpty().filter {
                    it.rank <= rank &&
                        shown.overlaps(it.longitude, it.longitude, it.latitude, it.latitude)
                }
                val thin = Stroke(THIN.toPx())
                onDrawBehind {
                    drawRect(sea)
                    for (path in lands) drawPath(path, land)
                    for (path in lakes) drawPath(path, sea)
                    for (path in rivers) drawPath(path, river, style = thin)
                    for (path in borders) drawPath(path, border, style = thin)
                    // A name that would lie over one already drawn is left off. The cities
                    // come most important first, so what is dropped is the lesser one.
                    val taken = ArrayList<Rect>()
                    for (city in towns) {
                        val centre = at(city.latitude, city.longitude)
                        drawCircle(town, TOWN.toPx(), centre)
                        val laid = measurer.measure(city.name, townLabel)
                        val corner = beside(centre, TOWN.toPx(), laid.size.height)
                        val box = Rect(corner, laid.size.toSize())
                        if (taken.any { it.overlaps(box) }) continue
                        taken += box
                        drawText(laid, topLeft = corner)
                    }
                    for (dot in dots) {
                        if (dot.id == marked) continue
                        drawCircle(ink, DOT.toPx(), at(dot.latitude, dot.longitude))
                    }
                    dots.firstOrNull { it.id == marked }?.let { dot ->
                        val centre = at(dot.latitude, dot.longitude)
                        drawCircle(mark, MARKED.toPx(), centre)
                        val laid = measurer.measure(dot.title, siteLabel)
                        drawText(laid, topLeft = beside(centre, MARKED.toPx(), laid.size.height))
                    }
                }
            },
    )
}

/** Where a label sits: to the right of a dot of [radius] at [centre], centred on it. */
private fun beside(centre: Offset, radius: Float, height: Int): Offset =
    Offset(centre.x + radius + 3f, centre.y - height / 2f)

/**
 * An outline as one path on the canvas, closed where it is a coastline or a lake.
 *
 * Every ring goes into the one path and the fill is even-odd, which is what makes a ring
 * inside another a hole rather than land twice over. The outline is placed whole, from where
 * its west edge starts, so it never wraps in the middle.
 */
private fun pathOf(
    shape: Outline,
    closed: Boolean,
    frame: Frame,
    wide: Double,
    high: Double,
): Path {
    val path = Path()
    if (closed) path.fillType = PathFillType.EvenOdd
    val start = frame.startOf(shape.west, shape.east)
    for (ring in shape.rings) {
        for (index in 0..<ring.size / 2) {
            val east = start + (ring[2 * index] - shape.west)
            val (x, y) = frame.placeEast(ring[2 * index + 1], east, wide, high)
            if (index == 0) path.moveTo(x.toFloat(), y.toFloat())
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        if (closed) path.close()
    }
    return path
}

/** One item on a card, sized to what it says. */
@Composable
private fun ItemCard(chosen: Chosen) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP * 2)) {
            // The name and nothing beside it. An id is never shown.
            Text(
                text = chosen.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = GAP),
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = GAP))
            val shown = chosen.item.description.fields.mapNotNull { shownOf(it, chosen.item) }
            if (shown.isEmpty()) Aside("this one says nothing yet")
            for (field in shown) Field(field)
        }
    }
}

/** One field: what it is called, and what it says. */
@Composable
private fun Field(shown: Shown) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = shown.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        Text(
            text = shown.text,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                shown.wrong -> MaterialTheme.colorScheme.error
                shown.worked -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}

// --- What every screen shares.

/** A quiet remark where there is nothing else to show. */
@Composable
private fun Aside(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = GAP, vertical = HALF),
    )
}

@Composable
internal fun Middle(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** The background of the one chosen, and of anything else nothing. */
@Composable
private fun tint(chosen: Boolean): Color =
    if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

@Composable
private fun onTint(chosen: Boolean): Color =
    if (chosen) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

internal val SHAPE = RoundedCornerShape(6.dp)
internal val SELECTOR = 280.dp
private val TABLE = 600.dp
private val TREE = 220.dp
private val TRIP = 170.dp
private val NUMBER = 52.dp
private val DATE = 100.dp
private val SITE = 240.dp
private val LABEL = 170.dp
private val SITES = 300.dp
private val MAP = 420.dp
private val DOT = 3.dp
private val MARKED = 5.dp
private val TOWN = 1.5.dp
private val THIN = 1.dp

/** How much of the water colour a river carries, so it reads as a line and not a canal. */
private const val RIVER = 0.6f
internal val LINE = 28.dp
internal val INDENT = 16.dp
internal val GAP = 12.dp
internal val HALF = 4.dp
