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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import yemoja.data.ItemDescription
import yemoja.data.ReferenceableItem
import yemoja.data.inOrder
import yemoja.logic.Universe

/*
 * The application's screens, which are one definition for both form factors.
 *
 * Reading only. Nothing here changes anything, which is the first version's whole scope: what a
 * user can do with it is open the logbook and look.
 *
 * See ../../../../../../gui/doc.md — the three views and what each is for are settled there.
 */

/** One item in a selector: what it is called, and how to reach it. */
internal class Chosen(val id: String, val title: String, val item: ReferenceableItem)

/** The whole application: a tab down the side, and whatever that tab shows. */
@Composable
internal fun Application(universe: Universe) {
    // Home, which is where the application opens whatever it holds yet.
    var tab by remember { mutableStateOf(TABS.first()) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(modifier = Modifier.fillMaxSize()) {
            Rail(tab) { tab = it }
            Box(modifier = Modifier.weight(1f)) {
                if (tab.types.isEmpty()) Owed(tab) else Subject(universe, tab)
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "${tab.name}: ${tab.owed}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** A subject: what it holds down the left, and the one chosen on the right. */
@Composable
private fun Subject(universe: Universe, tab: Tab) {
    var chosen by remember(tab) { mutableStateOf<Chosen?>(null) }
    Row(modifier = Modifier.fillMaxSize()) {
        Selector(universe, tab, chosen) { chosen = it }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            chosen?.let { ItemView(it) } ?: Nothing()
        }
    }
}

/**
 * What the tab holds, one type after another.
 *
 * The types are headed even where a tab holds one, so that a tab holding several does not look
 * like a different kind of thing from a tab holding one.
 */
@Composable
private fun Selector(
    universe: Universe,
    tab: Tab,
    chosen: Chosen?,
    onChoose: (Chosen) -> Unit,
) {
    LazyColumn(modifier = Modifier.width(SELECTOR).fillMaxHeight().padding(GAP)) {
        for (type in tab.types) {
            item(key = "head:" + type.name) { Heading(type) }
            items(entriesOf(universe, type), key = { it.id }) { entry ->
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.id == chosen?.id) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { onChoose(entry) }
                        .padding(horizontal = HALF, vertical = HALF),
                )
            }
        }
    }
}

@Composable
private fun Heading(type: ItemDescription) {
    Text(
        text = labelOf(type),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = GAP, bottom = HALF),
    )
}

/** One item, arranged for reading. It never changes anything. */
@Composable
private fun ItemView(chosen: Chosen) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // The name and nothing beside it. An id is never shown, which this had broken.
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
private fun Nothing() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "choose something on the left",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Everything of one type the logbook holds, in the order the type asks for. `DATA-89`. */
internal fun entriesOf(universe: Universe, type: ItemDescription): List<Chosen> =
    universe.logbook.inOrder(type).mapNotNull { item ->
        universe.logbook.idOf(item)?.let { Chosen(it, titleOf(item), item) }
    }

private val RAIL = 140.dp
private val SELECTOR = 260.dp
private val LABEL = 170.dp
private val GAP = 12.dp
private val HALF = 4.dp
