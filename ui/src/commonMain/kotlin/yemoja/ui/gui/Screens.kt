package yemoja.ui.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yemoja.data.ItemSet
import yemoja.logic.Universe

/*
 * The application's screens, which are one definition for both form factors.
 *
 * Reading only. Nothing here changes anything, which is the first version's whole scope.
 *
 * See ../../../../../../gui/doc.md — the tabs, the three views and the shape of each selector
 * are settled there; this draws them.
 */

/** The whole application: a tab down the side, and whatever that tab shows. */
@Composable
internal fun Application(universe: Universe) {
    // Home, which is where the application opens whatever it holds yet.
    var tab by remember { mutableStateOf(TABS.first()) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            Rail(tab) { tab = it }
            Box(modifier = Modifier.weight(1f)) {
                if (tab.shape == Shape.NONE) Owed(tab) else Subject(universe.logbook, tab)
            }
        }
    }
}

/**
 * The tabs, down the side.
 *
 * Down rather than across, because seven labels fit comfortably beside a desktop window and do
 * not fit across the foot of a phone. `GUI-13` settled that a phone keeps this a gesture away
 * instead, which is a placement rather than a different set of tabs.
 */
@Composable
private fun Rail(chosen: Tab, onChoose: (Tab) -> Unit) {
    Column(
        modifier = Modifier.width(RAIL).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = GAP),
    ) {
        for (tab in TABS) {
            val here = tab === chosen
            Text(
                text = tab.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                color = if (here) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth().clickable { onChoose(tab) }
                    .padding(horizontal = GAP, vertical = HALF),
            )
        }
    }
}

/** A tab with nothing behind it yet, saying what it will hold rather than nothing at all. */
@Composable
private fun Owed(tab: Tab) {
    Middle("${tab.name}: ${tab.owed}")
}

/** A subject: what it holds on the left, and the one chosen on the right. */
@Composable
private fun Subject(set: ItemSet, tab: Tab) {
    var chosen by remember(tab) { mutableStateOf<Chosen?>(null) }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(if (tab.shape == Shape.DIVES) TABLE else SELECTOR)) {
            when (tab.shape) {
                Shape.DIVES -> Dives(set, chosen) { chosen = it }
                Shape.GEAR -> Gear(set, chosen) { chosen = it }
                Shape.TYPES -> Types(set, tab, chosen) { chosen = it }
                Shape.PLACES -> Places(set, chosen) { chosen = it }
                Shape.NONE -> Unit
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            chosen?.let { ItemView(it) } ?: Middle("choose something on the left")
        }
    }
}

/**
 * The dive table: the trip, the dive's own number, the date and the site.
 *
 * The trip cell spans the consecutive dives on it, and which column is clicked decides what is
 * selected. `GUI-19`.
 */
@Composable
private fun Dives(set: ItemSet, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    val rows = remember(set) { diveRowsOf(set) }
    LazyColumn(modifier = Modifier.fillMaxHeight().padding(GAP)) {
        items(rows, key = { it.dive.id }) { row ->
            Row(modifier = Modifier.fillMaxWidth().height(LINE)) {
                val trip = row.trip
                Cell(
                    text = if (row.run > 0 && trip != null) trip.title else "",
                    width = TRIP,
                    chosen = trip != null && trip.id == chosen?.id,
                    onClick = if (trip == null) null else ({ onChoose(trip) }),
                )
                val here = row.dive.id == chosen?.id
                Cell(row.number, NUMBER, here) { onChoose(row.dive) }
                Cell(row.date, DATE, here) { onChoose(row.dive) }
                Cell(row.site, SITE, here) { onChoose(row.dive) }
            }
        }
    }
}

/** One cell of the table. A cell with nothing to open is not clickable. */
@Composable
private fun Cell(text: String, width: Dp, chosen: Boolean, onClick: (() -> Unit)?) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.width(width)
            .let { if (onClick == null) it else it.clickable(onClick = onClick) }
            .padding(horizontal = HALF, vertical = HALF),
    )
}

/** Gear by category and by the kind within it. */
@Composable
private fun Gear(set: ItemSet, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    val (branches, loose) = remember(set) { gearTreeOf(set) }
    LazyColumn(modifier = Modifier.fillMaxHeight().padding(GAP)) {
        // Filed under nothing sits at the top rather than in a bucket called other. `GUI-21`.
        items(loose, key = { "loose:" + it.id }) { Entry(it, chosen, 0, onChoose) }
        for (branch in branches) {
            item(key = branch.key) { Label(branch.label, 0) }
            items(branch.held, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
            for (kind in branch.children) {
                item(key = kind.key) { Label(kind.label, 1) }
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
 */
@Composable
private fun Places(set: ItemSet, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
    var place by remember(set) { mutableStateOf<Chosen?>(null) }
    val tree = remember(set) { regionTreeOf(set) }
    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.width(TREE).fillMaxHeight().padding(GAP)) {
            branchesIn(tree, "", 0) { region ->
                place = region
                onChoose(region)
            }
        }
        val here = place
        val what = remember(set, here) { here?.let { atPlaceIn(set, it.id) } }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            if (what == null) return@LazyColumn
            item(key = "sites") { Label("Sites", 0) }
            items(what.first, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
            if (what.second.isEmpty()) return@LazyColumn
            item(key = "wrecks") { Label("Wrecks", 0) }
            items(what.second, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
        }
    }
}

/**
 * Every branch of a tree, flattened with its depth, since a lazy list holds no nesting.
 *
 * A line is keyed by its whole path from the root, because a region under two parents is in
 * the list twice and a list refuses two lines with one key.
 */
private fun LazyListScope.branchesIn(
    branches: List<Branch>,
    path: String,
    depth: Int,
    onChoose: (Chosen) -> Unit,
) {
    for (branch in branches) {
        val key = path + "/" + branch.key
        item(key = key) {
            Text(
                text = branch.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
                    .clickable { branch.held.firstOrNull()?.let(onChoose) }
                    .padding(start = GAP * depth, top = HALF, bottom = HALF),
            )
        }
        branchesIn(branch.children, key, depth + 1, onChoose)
    }
}

/** A heading in a selector: a type, a category, a kind. */
@Composable
private fun Label(text: String, depth: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = GAP * depth, top = GAP, bottom = HALF),
    )
}

/** One item a selector offers. */
@Composable
private fun Entry(entry: Chosen, chosen: Chosen?, depth: Int, onChoose: (Chosen) -> Unit) {
    Text(
        text = entry.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (entry.id == chosen?.id) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().clickable { onChoose(entry) }
            .padding(start = GAP * depth + HALF, top = HALF, bottom = HALF),
    )
}

/** One item, arranged for reading. It never changes anything. */
@Composable
private fun ItemView(chosen: Chosen) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // The name and nothing beside it. An id is never shown.
        Text(
            text = chosen.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = GAP),
        )
        val shown = chosen.item.description.fields.mapNotNull { shownOf(it, chosen.item) }
        if (shown.isEmpty()) {
            Text(
                text = "this one says nothing yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        for (field in shown) Field(field)
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

@Composable
private fun Middle(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private val RAIL = 140.dp
private val SELECTOR = 260.dp
private val TABLE = 520.dp
private val TREE = 200.dp
private val TRIP = 150.dp
private val NUMBER = 46.dp
private val DATE = 92.dp
private val SITE = 210.dp
private val LABEL = 170.dp
private val LINE = 24.dp
private val GAP = 12.dp
private val HALF = 4.dp
