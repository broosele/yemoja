package yemoja.ui.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.logic.Change
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Types
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
    /** What day it is, which only a platform knows. Asked each time, a window outliving one. */
    val today: () -> yemoja.data.Date,
    /**
     * What this platform can do to a logbook as a whole, by deed.
     *
     * A deed the platform cannot do yet is absent, and the home screen offers it greyed: the
     * list of deeds is what the application is for, not what is built. `GUI-30`.
     */
    val deeds: Map<Deed, () -> Unit> = emptyMap(),
)

/**
 * Kept is what a tab holds on to between visits: what is chosen, and how its selector stands.
 *
 * A tab left and returned to is where it was left. One of these per tab lives as long as the
 * application does, above the screens that come and go with the tab, and a field a tab has no
 * use for stays at its start. `GUI-27`.
 */
internal class Kept {
    /** Whether the tab has been opened before, which decides what it opens on the first time. */
    var opened: Boolean by mutableStateOf(false)

    /** The item chosen, shown on the right. */
    var chosen: Chosen? by mutableStateOf(null)

    /** Every dive chosen, where several are: their statistics are shown instead. `GUI-23`. */
    var chosenMany: Set<String> by mutableStateOf(emptySet())

    /** Location's region. */
    var place: Chosen? by mutableStateOf(null)

    /** Location's box. On, so a logbook opens on where its dives were. `GUI-26`. */
    var hideUnused: Boolean by mutableStateOf(true)

    /**
     * Home's plot: which variable runs along the bottom, and which up the side. `GUI-30`.
     *
     * Absent until the reader chooses, the plot opening on a pair of its own.
     */
    var across: Int? by mutableStateOf(null)
    var up: Int? by mutableStateOf(null)

    /** The branches of a tree unfolded, by path; absent until the tree has decided how it opens. */
    var open: Set<String>? by mutableStateOf(null)

    /** Gear's branches folded. */
    var closed: Set<String> by mutableStateOf(emptySet())

    /** Which of its types Community shows. */
    var subtab: Int by mutableStateOf(0)

    /** The chapter Manuals shows, and the chapters unfolded in its tree. */
    var chapter: Chapter? by mutableStateOf(null)
    var unfolded: Set<String> by mutableStateOf(emptySet())

    /** Where the selector's tree and list are scrolled to, and the page on the right. */
    val tree: LazyListState = LazyListState()
    val list: LazyListState = LazyListState()
    val page: ScrollState = ScrollState(0)
}

/**
 * Changer is the one door from the screens to the model's changes, and a count that ticks on
 * each so that whatever shows an item redraws it. `GUI-29`.
 */
internal class Changer(private val universe: Universe?) {
    /** How many changes have landed, read by whatever must redraw when one does. */
    var edition: Int by mutableStateOf(0)

    fun change(changes: List<Change>): Outcome {
        // A window opened on no logbook has nothing to write to, and nothing in it asks. `GUI-30`.
        val open = universe ?: return Outcome.Refused("no logbook is open")
        val outcome = open.change(Operation.EDIT, *changes.toTypedArray())
        if (outcome is Outcome.Done) edition++
        return outcome
    }
}

/** The changer, for whatever is deep enough in a screen to save something. */
internal val LocalChanger = staticCompositionLocalOf<Changer> { error("no changer is provided") }

/**
 * The whole application: a tab across the top, and whatever that tab shows.
 *
 * [universe] is absent where the window was opened on no logbook, and then only the tabs that
 * are not about what a logbook holds are offered. `GUI-30`.
 */
@Composable
internal fun Application(universe: Universe?, platform: Platform) {
    val tabs = remember(universe) { TABS.filter { universe != null || !it.needsLogbook } }
    // Home, which is where the application opens whatever it holds yet.
    var tab by remember { mutableStateOf(tabs.first()) }
    val kept = remember { TABS.associateWith { Kept() } }
    val changer = remember(universe) { Changer(universe) }
    // Absent until read, and a map drawn before then shows its sites on an empty frame.
    val atlas by produceState<Atlas?>(null, platform) {
        value = withContext(Dispatchers.Default) { platform.atlas() }
    }
    // A reference followed: the tab holding the item's type, opened on it. `GUI-28`.
    val follow = { id: String ->
        val item = universe?.logbook?.get(id)
        val to = item?.let { found -> TABS.firstOrNull { found.description in it.types } }
        if (item != null && to != null) {
            val there = kept.getValue(to)
            val chosen = Chosen(id, titleOf(item), item)
            there.opened = true
            when {
                item.description == Types.REGION -> there.place = chosen
                to.shape == Shape.PLACES -> {
                    there.chosen = chosen
                    // The map follows the site: a site in Egypt is looked at on Egypt.
                    homeOf(universe.logbook, item)?.let { home ->
                        universe.logbook[home]?.let { there.place = Chosen(home, titleOf(it), it) }
                    }
                }
                to.shape == Shape.TYPES -> {
                    there.chosen = chosen
                    there.subtab = to.types.indexOf(item.description)
                }
                else -> there.chosen = chosen
            }
            tab = to
        }
    }
    CompositionLocalProvider(LocalChanger provides changer) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Tabs(tabs, tab) { tab = it }
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        tab.shape == Shape.HOME -> Home(universe, platform, kept.getValue(tab))
                        tab.shape == Shape.MANUAL ->
                            Manuals(platform.manual, platform.open, kept.getValue(tab))

                        universe == null -> Unit
                        else -> Subject(
                            set = universe.logbook,
                            user = universe.user,
                            tab = tab,
                            atlas = atlas,
                            kept = kept.getValue(tab),
                            onFollow = follow,
                        )
                    }
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
private fun Tabs(tabs: List<Tab>, chosen: Tab, onChoose: (Tab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Tabs as wide as their names, so the row's own rule would stop where they do. The
        // rule is drawn below instead, across the whole window.
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(chosen),
            edgePadding = GAP,
            divider = {},
        ) {
            for (tab in tabs) {
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

// --- Home: the greeting, what can be done to a logbook, and a plot of it. `GUI-30`.

/**
 * Home: what the logbook comes to in a line, what the application can be asked to do, and any
 * two things a dive answers for, plotted against each other.
 *
 * Nothing is chosen here and nothing is listed. It is the screen a reader meets before they
 * have asked for anything, so it answers the two questions they have not asked yet: how much
 * diving is in here, and what would you like to do.
 */
@Composable
private fun Home(universe: Universe?, platform: Platform, kept: Kept) {
    val set = universe?.logbook
    val edition = LocalChanger.current.edition
    val greeting = remember(set, edition) { set?.let { greetingOf(it) } }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = GAP * 2),
    ) {
        val southern = remember(set, edition) { set != null && southernOf(set) }
        val hail = hailOf(universe?.user, greeting, platform.today(), southern)
        Text(
            text = greeted(hail, platform.open),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = GAP * 2),
        )
        Text(
            text = tellingOf(universe?.user, greeting),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = HALF, bottom = GAP * 2),
        )
        Inset("System") { Deeds(platform.deeds) }
        // Nothing to count where there is no logbook, and nothing to say about that.
        if (set != null) Inset("Statistics") { Plot(set, kept, edition) }
        Spacer(modifier = Modifier.height(GAP * 2))
    }
}

/**
 * The first line, the day's remark leading out to a page about the day.
 *
 * A remark is the one part of a greeting worth following, and following it leaves the
 * application, so it is styled as the manual styles a link out and handed to the same opener.
 */
@Composable
private fun greeted(hail: Hail, open: (String) -> Unit): AnnotatedString {
    val link = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    return buildAnnotatedString {
        append(hail.opening)
        val occasion = hail.occasion
        if (occasion == null) {
            append(".")
            return@buildAnnotatedString
        }
        append(", and ")
        val page = occasion.page
        if (page == null) {
            append(occasion.said)
        } else {
            withLink(LinkAnnotation.Clickable(page, link) { open(page) }) { append(occasion.said) }
        }
        append(".")
    }
}

/**
 * Every deed the application knows, as a button apiece, the ones this platform cannot do yet
 * greyed rather than left out. `GUI-30`.
 */
@Composable
private fun Deeds(deeds: Map<Deed, () -> Unit>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        for (deed in Deed.entries) {
            val act = deeds[deed]
            Button(onClick = { act?.invoke() }, enabled = act != null) { Text(deed.label) }
        }
    }
    if (Deed.entries.any { it !in deeds }) Aside("what is greyed is not built yet")
}

/**
 * Any two things a dive answers for, one against the other, a dot per dive.
 *
 * Which two is the whole of the interaction: the names of the axes are the boxes that choose
 * them, as the profile graph's right axis already is. `GUI-30`.
 */
@Composable
private fun Plot(set: ItemSet, kept: Kept, edition: Int) {
    val variables = remember { variablesOf() }
    if (variables.size < 2) {
        Aside("a dive says too little to plot")
        return
    }
    val opening = remember(variables) { openingOf(variables) }
    val alongAt = (kept.across ?: opening.first).coerceIn(variables.indices)
    val upAt = (kept.up ?: opening.second).coerceIn(variables.indices)
    val across = variables[alongAt]
    val up = variables[upAt]
    val spots = remember(set, edition, across, up) { plottedOf(set, across, up) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Picked(variables, upAt) { kept.up = it }
        Aside("against")
        Picked(variables, alongAt) { kept.across = it }
    }
    if (spots.isEmpty()) Aside("no dive answers both") else Scatter(spots, across, up)
}

/** One variable chosen from all of them, its name being the box that chooses it. */
@Composable
private fun Picked(variables: List<Variable>, chosen: Int, onChoose: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val at = chosen.coerceIn(variables.indices)
    Box {
        Row(
            modifier = Modifier.clip(SHAPE).clickable { picking = true }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = GAP, end = HALF, top = HALF, bottom = HALF),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = variables[at].label, style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "choose what is plotted",
                modifier = Modifier.size(GLYPH),
            )
        }
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
            for ((index, variable) in variables.withIndex()) {
                DropdownMenuItem(
                    text = { Text(variable.label) },
                    onClick = {
                        onChoose(index)
                        picking = false
                    },
                )
            }
        }
    }
}

/**
 * The dives as dots against a grid, each axis marked at values a reader would choose and
 * titled by what it carries.
 */
@Composable
private fun Scatter(spots: List<Spot>, across: Variable, up: Variable) {
    val ink = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelSmall.copy(color = quiet)
    val title = MaterialTheme.typography.labelMedium.copy(color = quiet)
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier = Modifier.fillMaxWidth().height(PLOT).padding(bottom = HALF)
            .drawWithCache {
            val left = AXIS.toPx()
            val right = size.width - HALF.toPx()
            val bottom = size.height - FOOT.toPx()
            val top = HEAD.toPx()
            val alongs = rangeOf(spots.map { it.across })
            val ups = rangeOf(spots.map { it.up })
            fun x(value: Double): Float {
                val part = (value - alongs.start) / (alongs.endInclusive - alongs.start)
                return (left + (right - left) * part).toFloat()
            }
            fun y(value: Double): Float {
                val part = (value - ups.start) / (ups.endInclusive - ups.start)
                return (bottom - (bottom - top) * part).toFloat()
            }
            val along = ticksOf(alongs.start, alongs.endInclusive, 6)
            val side = ticksOf(ups.start, ups.endInclusive, 5)
            onDrawBehind {
                for (tick in side) {
                    val at = y(tick)
                    drawLine(grid, Offset(left, at), Offset(right, at), THIN.toPx())
                    val laid = measurer.measure(shortOf(tick), label)
                    val corner = Offset(
                        x = left - laid.size.width - HALF.toPx(),
                        y = at - laid.size.height / 2f,
                    )
                    drawText(laid, topLeft = corner)
                }
                for (tick in along) {
                    drawLine(grid, Offset(x(tick), top), Offset(x(tick), bottom), THIN.toPx())
                    val laid = measurer.measure(shortOf(tick), label)
                    drawText(laid, topLeft = Offset(x(tick) - laid.size.width / 2f, bottom + 2f))
                }
                for (spot in spots) drawCircle(ink, DOT.toPx(), Offset(x(spot.across), y(spot.up)))
                val upward = measurer.measure(titledOf(up), title)
                drawText(upward, topLeft = Offset(left + HALF.toPx(), 0f))
                val onward = measurer.measure(titledOf(across), title)
                drawText(onward, topLeft = Offset(right - onward.size.width, 0f))
            }
        },
    )
}

/** An axis's title: what it carries, and its unit where it has one. */
private fun titledOf(variable: Variable): String =
    if (variable.unit.isEmpty()) variable.label else variable.label + " (" + variable.unit + ")"

/**
 * A subject: what it holds on the left, and the one chosen on the right.
 *
 * Location chooses two things, a region and something at it, and shows both. `GUI-25`.
 */
@Composable
private fun Subject(
    set: ItemSet,
    user: ReferenceableItem?,
    tab: Tab,
    atlas: Atlas?,
    kept: Kept,
    onFollow: (String) -> Unit,
) {
    val edition = LocalChanger.current.edition
    val tree = remember(set, kept.hideUnused, edition) { shownTreeOf(set, kept.hideUnused) }
    // What a tab opens on the first time: Location on the widest root, which is the world, and
    // Community on the user, whose logbook this is.
    remember(kept) {
        if (!kept.opened) {
            kept.opened = true
            when (tab.shape) {
                Shape.PLACES -> kept.place = widestRootIn(tree)
                Shape.TYPES -> kept.chosen = user?.let { chosenOf(set, it) }
                // The last dive, which is the first in a table kept newest first.
                Shape.DIVES -> kept.chosen = entriesOf(set, Types.DIVE).firstOrNull()
                else -> Unit
            }
        }
        kept
    }
    val chosen = kept.chosen
    val wide = when (tab.shape) {
        Shape.DIVES -> TABLE
        Shape.PLACES -> TREE + SITES
        Shape.TYPES -> SUBTABS
        else -> SELECTOR
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(wide)) {
            when (tab.shape) {
                Shape.DIVES -> Dives(set, kept)
                Shape.GEAR -> Gear(set, chosen, kept) { kept.chosen = it }
                Shape.TYPES -> Types(set, tab, user, chosen, kept) { kept.chosen = it }
                Shape.PLACES -> Places(
                    set = set,
                    tree = tree,
                    kept = kept,
                    onPlace = { region ->
                        kept.place = region
                        // What was chosen stays chosen while the new region still lists it.
                        val (sites, wrecks) = atPlaceIn(set, region.id, kept.hideUnused)
                        if ((sites + wrecks).none { it.id == chosen?.id }) kept.chosen = null
                    },
                    onChoose = { kept.chosen = it },
                )
                Shape.MANUAL, Shape.HOME -> Unit
            }
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            when {
                tab.shape == Shape.PLACES -> {
                    PlaceView(set, atlas, kept.hideUnused, kept.place, chosen, onFollow)
                }
                kept.chosenMany.size > 1 -> ManyView(set, kept.chosenMany, onFollow)
                chosen != null -> ItemView(chosen, onFollow)
                else -> Middle("choose something on the left")
            }
        }
    }
}

/** An item as a selector would offer it, or absent where the set does not hold it. */
private fun chosenOf(set: ItemSet, item: ReferenceableItem): Chosen? =
    set.idOf(item)?.let { Chosen(it, titleOf(item), item) }

// --- The dive table.

/**
 * The dive table: the trip, the dive's own number, the date and the site.
 *
 * The trip cell spans the consecutive dives on it, and which column is clicked decides what is
 * selected. `GUI-19`. The span is drawn as one: the cell is tinted down the whole run, its title
 * sits on the first row, and a rule separates one run from the next rather than one row from
 * the next.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Dives(set: ItemSet, kept: Kept) {
    val edition = LocalChanger.current.edition
    val years = remember(set, edition) { yearsOf(diveRowsOf(set)) }
    // The last year unfolded and the rest folded, until the reader says otherwise.
    val open = kept.open ?: setOfNotNull(years.firstOrNull()?.label)
    val chosen = kept.chosen
    val many = kept.chosenMany
    val window = LocalWindowInfo.current
    // A plain click chooses one dive; with control held it adds or removes one; with shift
    // held it chooses every dive between the one last chosen and this one, the last chosen
    // staying where it is so the next shift-click reaches from the same place. `GUI-23`.
    val choose = { dive: Chosen ->
        val keys = window.keyboardModifiers
        val anchor = chosen?.id
        when {
            keys.isShiftPressed && anchor != null -> {
                val order = years.flatMap { year -> year.rows.map { it.dive.id } }
                val from = order.indexOf(anchor)
                val to = order.indexOf(dive.id)
                if (from >= 0 && to >= 0) {
                    kept.chosenMany = order.subList(minOf(from, to), maxOf(from, to) + 1).toSet()
                }
            }
            keys.isCtrlPressed -> {
                val was = if (many.isEmpty() && chosen != null) setOf(chosen.id) else many
                kept.chosenMany = if (dive.id in was) was - dive.id else was + dive.id
                kept.chosen = dive
            }
            else -> {
                kept.chosenMany = emptySet()
                kept.chosen = dive
            }
        }
    }
    val chooseTrip = { trip: Chosen ->
        kept.chosenMany = emptySet()
        kept.chosen = trip
    }
    // A dive chosen from elsewhere, by following a reference, may sit in a folded year, where
    // there is no line to bring into view. The year unfolds first, and the line is brought into
    // view once it exists. `GUI-28`.
    val holding = yearHolding(years, chosen?.id)
    LaunchedEffect(chosen?.id) {
        if (holding != null && holding !in open) kept.open = open + holding
    }
    Shown(kept.list, chosen?.id?.takeIf { holding == null || holding in open })
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
        LazyColumn(state = kept.list, modifier = Modifier.fillMaxHeight()) {
            for (year in years) {
                val unfolded = year.label in open
                item(key = "year:" + year.label) {
                    YearRow(
                        year = year,
                        open = unfolded,
                        chosen = many.isNotEmpty() && year.rows.all { it.dive.id in many },
                        onToggle = {
                            kept.open = if (unfolded) open - year.label else open + year.label
                        },
                        // The year's dives together, which is what a year is for. `GUI-23`.
                        onChoose = {
                            kept.chosenMany = year.rows.map { it.dive.id }.toSet()
                            kept.chosen = year.rows.firstOrNull()?.dive
                        },
                    )
                }
                if (!unfolded) continue
                val labelled = labelledOf(year.rows)
                itemsIndexed(year.rows, key = { _, row -> row.dive.id }) { index, row ->
                    if (index > 0 && row.run > 0) HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth().height(LINE)) {
                        TripCell(row, labelled[index], chosen, chooseTrip)
                        val here = row.dive.id in many ||
                            (many.isEmpty() && row.dive.id == chosen?.id)
                        Row(
                            modifier = Modifier.fillMaxHeight().clip(SHAPE).background(tint(here))
                                .clickable { choose(row.dive) },
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
}

/** A year's divider in the table: an arrow that folds it, and the year, which chooses it whole. */
@Composable
private fun YearRow(
    year: Year,
    open: Boolean,
    chosen: Boolean,
    onToggle: () -> Unit,
    onChoose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(LINE)
            .background(if (chosen) tint(true) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onChoose),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(LINE).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (open) Icons.Filled.ArrowDropDown else Icons.Filled.ArrowRight,
                contentDescription = if (open) "fold" else "unfold",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = year.label,
            style = MaterialTheme.typography.labelLarge,
            color = onTint(chosen),
        )
        Text(
            text = "  ·  ${year.rows.size} dives",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * Brings the line keyed [key] into view, where the list holds one and it is not in view already.
 *
 * For what was chosen from elsewhere, by following a reference: a click on a line never needs
 * this, the line being where the click was, and is left alone.
 */
@Composable
private fun Shown(list: LazyListState, key: String?) {
    LaunchedEffect(list, key) {
        if (key == null) return@LaunchedEffect
        val seen = list.layoutInfo.visibleItemsInfo
        if (seen.any { it.key == key }) return@LaunchedEffect
        // The keys in order are not known to the state, so the list is asked by scrolling to
        // where the key was last laid out; a key never laid out is found by walking.
        val at = indexOf(list, key) ?: return@LaunchedEffect
        list.animateScrollToItem(at)
    }
}

/** Where [key] is in a list, found by looking through it. Absent where it is not there. */
private suspend fun indexOf(list: LazyListState, key: String): Int? {
    val total = list.layoutInfo.totalItemsCount
    if (total == 0) return null
    for (at in 0..<total step STRIDE) {
        list.scrollToItem(at)
        val found = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        if (found != null) return found.index
    }
    return null
}

/** How many lines a search for a key steps over at a time, fewer than a screen holds. */
private const val STRIDE = 20

/**
 * The trip cell, tinted down the run and clickable wherever the run is, the trip's name in the
 * middle of the run.
 *
 * [nudged] is whether this row carries the name, and if so whether half a line up, which is
 * where the middle of an even run falls; a row is drawn after the one above it, so the name
 * lies over that row's cell rather than under it.
 */
@Composable
private fun TripCell(row: DiveRow, nudged: Boolean?, chosen: Chosen?, onChoose: (Chosen) -> Unit) {
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
        contentAlignment = Alignment.Center,
    ) {
        if (nudged != null && trip != null) {
            Text(
                text = trip.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (here) FontWeight.Bold else FontWeight.Normal,
                color = onTint(here),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HALF)
                    .offset(y = if (nudged) -LINE / 2 else 0.dp),
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
private fun Gear(set: ItemSet, chosen: Chosen?, kept: Kept, onChoose: (Chosen) -> Unit) {
    val (branches, loose) = remember(set, LocalChanger.current.edition) { gearTreeOf(set) }
    val closed = kept.closed
    val toggle = { key: String -> kept.closed = if (key in closed) closed - key else closed + key }
    LazyColumn(state = kept.list, modifier = Modifier.fillMaxHeight().padding(GAP)) {
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

/**
 * A subtab per type, and the list of the one chosen; the user is marked among the people.
 *
 * The user is whoever the logbook names as its own, which is the one person a reader is sure to
 * be looking for and the one the tab opens on.
 */
@Composable
private fun Types(
    set: ItemSet,
    tab: Tab,
    user: ReferenceableItem?,
    chosen: Chosen?,
    kept: Kept,
    onChoose: (Chosen) -> Unit,
) {
    val userId = remember(set, user) { user?.let { set.idOf(it) } }
    Column(modifier = Modifier.fillMaxHeight()) {
        SecondaryTabRow(selectedTabIndex = kept.subtab) {
            for ((index, type) in tab.types.withIndex()) {
                androidx.compose.material3.Tab(
                    selected = index == kept.subtab,
                    onClick = { kept.subtab = index },
                    // Smaller than a tab's own type, since three names share a narrow column.
                    text = {
                        Text(
                            text = pluralOf(type),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }
        val type = tab.types[kept.subtab]
        Shown(kept.list, chosen?.id)
        LazyColumn(state = kept.list, modifier = Modifier.fillMaxHeight().padding(GAP)) {
            items(entriesOf(set, type), key = { it.id }) {
                Entry(it, chosen, 0, onChoose, mark = if (it.id == userId) YOU else null)
            }
        }
    }
}

/** A type as a subtab names it: many of them. */
private fun pluralOf(type: ItemDescription): String = when (type) {
    Types.PERSON -> "People"
    Types.OPERATOR -> "Operators"
    Types.CERTIFICATION -> "Certifications"
    else -> labelOf(type) + "s"
}

/** Mark is a glyph beside a line, and what it says. */
internal class Mark(val glyph: ImageVector, val says: String)

/** The user, whose logbook this is. */
private val YOU = Mark(Icons.Filled.AccountCircle, "you")

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
    kept: Kept,
    onPlace: (Chosen) -> Unit,
    onChoose: (Chosen) -> Unit,
) {
    val open = kept.open ?: tree.map { "/" + it.key }.toSet()
    val place = kept.place
    val chosen = kept.chosen
    val hideUnused = kept.hideUnused
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(TREE).fillMaxHeight().padding(GAP)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { kept.hideUnused = !hideUnused },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hideUnused, onCheckedChange = { kept.hideUnused = it })
                Text("Hide unused", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(state = kept.tree, modifier = Modifier.fillMaxHeight()) {
                branchesIn(
                    branches = tree,
                    path = "",
                    depth = 0,
                    chosen = place,
                    open = open,
                    onToggle = { key -> kept.open = if (key in open) open - key else open + key },
                    onChoose = onPlace,
                )
            }
        }
        VerticalDivider()
        val what = remember(set, place, hideUnused, LocalChanger.current.edition) {
            place?.let { atPlaceIn(set, it.id, hideUnused) }
        }
        LazyColumn(state = kept.list, modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
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
private fun Entry(
    entry: Chosen,
    chosen: Chosen?,
    depth: Int,
    onChoose: (Chosen) -> Unit,
    mark: Mark? = null,
) {
    Line(entry.title, depth, chosen = entry.id == chosen?.id, mark = mark) { onChoose(entry) }
}

/** One line of a list, tinted where it is the one chosen, and marked where there is a mark. */
@Composable
internal fun Line(
    text: String,
    depth: Int,
    chosen: Boolean,
    mark: Mark? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = INDENT * depth).clip(SHAPE)
            .background(tint(chosen)).clickable(onClick = onClick)
            .padding(horizontal = GAP, vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mark != null) {
            Icon(
                imageVector = mark.glyph,
                contentDescription = mark.says,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(GLYPH).padding(end = HALF),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = onTint(chosen),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- The item view.

/** One item, arranged for reading. It never changes anything. */
@Composable
private fun ItemView(chosen: Chosen, onFollow: (String) -> Unit) {
    // The card scrolls its own fields under a title line that stays put; what follows the
    // card scrolls with the fields.
    ItemCard(chosen, onFollow, scrolls = true) {
        // A trip is both an item and a set of dives, and shows as both. `GUI-23`.
        if (chosen.item.description == Types.DIVE_TRIP) {
            val dives = remember(chosen) { divesOf(chosen.item) }
            // The same box as for dives chosen in the table, however they were chosen.
            if (dives.isNotEmpty()) {
                Spacer(modifier = Modifier.height(GAP))
                StatsCard("${dives.size} dives", dives, onFollow)
            }
        }
    }
}

/** The dives a trip's own list names, as items. */
private fun divesOf(trip: Item): List<Item> =
    ((trip.read("dives") as? Result.Usable)?.value as? List<*>).orEmpty()
        .mapNotNull { ((it as? Element.Usable<*>)?.value as? Reference.Identified)?.id }
        .mapNotNull { trip.set[it] }

/**
 * Several dives chosen: what they say together, field by field. `GUI-23`.
 *
 * Titled by how many, since a set has no name; the fields are the dive's own in the dive's
 * order, so a reader who knows where one dive's depth sits knows where all of theirs sits.
 */
@Composable
private fun ManyView(set: ItemSet, ids: Set<String>, onFollow: (String) -> Unit) {
    val dives = remember(set, ids) { ids.mapNotNull { set[it] } }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StatsCard("${dives.size} dives", dives, onFollow)
    }
}

/** What [items] say together, on a card titled [title]. */
@Composable
private fun StatsCard(title: String, items: List<Item>, onFollow: (String) -> Unit) {
    val stats = remember(items) { listOfNotNull(listedOf(items)) + statisticsOf(items) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP * 2)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = GAP),
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = GAP))
            if (stats.isEmpty()) Aside("these say nothing yet")
            for (pair in stats.chunked(COLUMNS)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GAP * 2),
                ) {
                    for (field in pair) {
                        Box(modifier = Modifier.weight(1f)) { Field(field, onFollow) }
                    }
                    repeat(COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
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
    onFollow: (String) -> Unit,
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
            ItemCard(place, onFollow)
        }
        if (chosen != null) ItemCard(chosen, onFollow)
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

/**
 * One item on a card, with a pencil on its title line that turns the card over into the edit
 * form, where the title line carries Cancel and Save instead. `GUI-29`.
 *
 * Where the card [scrolls], it fills what it is given and its fields scroll under the title
 * line, which stays put; otherwise it is as tall as what it says, for a card stacked among
 * others. [after] follows the fields, scrolling with them.
 */
@Composable
private fun ItemCard(
    chosen: Chosen,
    onFollow: (String) -> Unit,
    scrolls: Boolean = false,
    after: @Composable ColumnScope.() -> Unit = {},
) {
    val changer = LocalChanger.current
    val edition = changer.edition
    var editing by remember(chosen) { mutableStateOf(false) }
    var refused by remember(chosen) { mutableStateOf<String?>(null) }
    val draft = remember(chosen) { Draft() }
    Surface(
        modifier = if (scrolls) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP * 2)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The name and nothing beside it. An id is never shown.
                Text(
                    text = titleOf(chosen.item),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (editing) {
                    EditActions(
                        draft = draft,
                        onCancel = {
                            editing = false
                            refused = null
                        },
                        onSave = {
                            when (val outcome = changer.change(draft.writes())) {
                                is Outcome.Done -> {
                                    editing = false
                                    refused = null
                                }
                                is Outcome.Refused -> refused = outcome.reason
                            }
                        },
                    )
                } else {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "edit")
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = GAP))
            val body: @Composable ColumnScope.() -> Unit = {
                refused?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = GAP),
                    )
                }
                // Read again after every change: the item is the same object, changed in place.
                key(edition) {
                    if (editing) EditFields(chosen.item, draft) else Fields(chosen.item, onFollow)
                }
                after()
            }
            if (scrolls) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    content = body,
                )
            } else {
                body()
            }
        }
    }
}

/**
 * An item's fields as a desktop lays them out: the plain ones in two columns, then each owned
 * item in an inset of its own, shown in full, and a keyed one as an inset with a tab per entry.
 *
 * Insets nest, since an owned item may own one: a recording's tolerances sit inside it. `GUI-16`.
 */
@Composable
private fun Fields(item: Item, onFollow: (String) -> Unit) {
    val arranged = remember(item.description) { arrangedOf(item.description) }
    val shown = arranged.plain.mapNotNull { shownOf(it, item) }
    val insets = arranged.insets.filter { item.read(it.name) is Result.Usable }
    if (shown.isEmpty() && insets.isEmpty()) {
        Aside("this one says nothing yet")
        return
    }
    for (pair in shown.chunked(COLUMNS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in pair) Box(modifier = Modifier.weight(1f)) { Field(field, onFollow) }
            repeat(COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
    for (inset in insets) {
        when (inset.cardinality) {
            Cardinality.KEYED -> KeyedInset(inset, item, onFollow)
            else -> {
                val owned = (item.read(inset.name) as? Result.Usable)?.value as? OwnedItem
                if (owned != null) Inset(inset.label) { Fields(owned, onFollow) }
            }
        }
    }
}

/** A keyed owned item as an inset with a tab per entry, the first open. */
@Composable
private fun KeyedInset(inset: OwnedItemDescription, item: Item, onFollow: (String) -> Unit) {
    @Suppress("UNCHECKED_CAST")
    val entries = ((item.read(inset.name) as? Result.Usable)?.value as? Map<String, Element<Any>>)
        .orEmpty().mapNotNull { (key, element) ->
            ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
        }
    if (entries.isEmpty()) return
    var open by remember(item, inset.name) { mutableStateOf(0) }
    val at = open.coerceIn(0, entries.size - 1)
    val tabs: @Composable RowScope.() -> Unit = {
        SmallTabs(
            labels = entries.map { (key, entry) -> entryLabelOf(key, entry) },
            chosen = at,
            marked = pointedEntryOf(item, inset.name),
            onChoose = { open = it },
        )
    }
    Inset(inset.label, beside = tabs) {
        Spacer(modifier = Modifier.height(HALF))
        // A recording is drawn before it is read: the graph is what it is for.
        val entry = entries[at].second
        if (depthLinesOf(entry).isNotEmpty()) ProfileGraph(item, entry)
        Fields(entry, onFollow)
    }
}

/**
 * A row of small tabs, each a button as wide as its name, the chosen one tinted.
 *
 * The platform's tab row spreads its tabs across the width and makes each a touch target, which
 * in a box inside an item is too much furniture for three names; these sit beside the box's
 * title instead.
 */
@Composable
internal fun SmallTabs(
    labels: List<String>,
    chosen: Int,
    onChoose: (Int) -> Unit,
    /** The tab that stands out among the rest, a dive's primary recording; marked with a star. */
    marked: Int? = null,
    /** Takes the tab at an index out, where the tabs can be; shown on the chosen one. */
    onRemove: ((Int) -> Unit)? = null,
    /** Adds a tab, where they can be; shown after the last. */
    onAdd: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HALF * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((index, label) in labels.withIndex()) {
            val here = index == chosen
            Row(
                modifier = Modifier.clip(SHAPE)
                    .background(
                        if (here) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    )
                    .clickable { onChoose(index) }
                    .padding(horizontal = GAP, vertical = HALF),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (index == marked) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "the primary one",
                        tint = onTint(here),
                        modifier = Modifier.padding(end = HALF).size(GLYPH - HALF),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = onTint(here),
                    maxLines = 1,
                    softWrap = false,
                )
                if (here && onRemove != null) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "take out",
                        tint = onTint(true),
                        modifier = Modifier.padding(start = HALF).size(GLYPH - HALF)
                            .clickable { onRemove(index) },
                    )
                }
            }
        }
        if (onAdd != null) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "add",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(SHAPE).clickable(onClick = onAdd).padding(HALF)
                    .size(GLYPH),
            )
        }
    }
}

/**
 * A box set into an item view, titled, holding an owned item in full; [beside] is what sits on
 * the title's line after it, the tabs of a keyed one.
 */
@Composable
internal fun Inset(
    title: String,
    beside: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = GAP),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = HALF),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GAP),
            ) {
                Text(
                    text = if (beside == null) title else "$title:",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                beside?.invoke(this)
            }
            content()
        }
    }
}

/** How many columns the plain fields of an item flow into on a desktop. */
internal const val COLUMNS = 2

/** One field: what it is called, and what it says. */
@Composable
private fun Field(shown: Shown, onFollow: (String) -> Unit) {
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
        // A part that leads somewhere is a link, in the link colour; the rest reads as it is.
        val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
        val said = buildAnnotatedString {
            for (part in shown.parts) {
                val to = part.leadsTo
                if (to == null) {
                    append(part.text)
                } else {
                    val clickable = LinkAnnotation.Clickable(to, link) { onFollow(to) }
                    withLink(clickable) { append(part.text) }
                }
            }
        }
        val rating = shown.rating
        if (rating != null) {
            Stars(rating)
        } else {
            Text(
                text = said,
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
}

/** A rating as five stars, filled, half filled or empty, and nothing else. */
@Composable
private fun Stars(rating: Int) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        for (star in starsOf(rating)) {
            Icon(
                imageVector = when (star) {
                    Star.FULL -> Icons.Filled.Star
                    Star.HALF -> Icons.Filled.StarHalf
                    Star.EMPTY -> Icons.Filled.StarOutline
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(GLYPH),
            )
        }
    }
}

// --- The graph of a recording. `GUI-4`.

/**
 * A recording drawn: depth up the left, and up the right one other thing it wrote, chosen from
 * a box of what it wrote.
 */
@Composable
private fun ProfileGraph(dive: Item, profile: Item) {
    val depth = remember(profile) { depthLinesOf(profile) }
    val overlays = remember(dive, profile) { overlaysOf(dive, profile) }
    val events = remember(dive, profile) { eventsOf(dive, profile) }
    var picked by remember(profile) { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val overlay = overlays.getOrNull(picked.coerceIn(0, maxOf(overlays.size - 1, 0)))
    Box(modifier = Modifier.fillMaxWidth()) {
        Chart(depth, overlay, events)
        // The right axis's title is the box that chooses it: the label says what the red line
        // is, and clicking it says what else it could be.
        if (overlay != null) {
            val red = MaterialTheme.colorScheme.error
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                Row(
                    modifier = Modifier.clip(SHAPE).clickable { picking = true }
                        .padding(start = HALF, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val suffix = if (overlay.unit.isEmpty()) "" else " (${overlay.unit})"
                    Text(
                        text = overlay.title + suffix,
                        style = MaterialTheme.typography.labelMedium,
                        color = red,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "choose the right axis",
                        tint = red,
                        modifier = Modifier.size(GLYPH),
                    )
                }
                DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                    for ((index, choice) in overlays.withIndex()) {
                        DropdownMenuItem(
                            text = { Text(choice.title) },
                            onClick = {
                                picked = index
                                picking = false
                            },
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(GAP))
}

/**
 * The graph: the depth lines against a grid, the overlay's line in its own colour with its own
 * marks up the right, the minutes along the bottom, and on the depth line the gas switches and
 * the alarms, each named. The overlay's title is not drawn here: it is the box that chooses the
 * overlay, laid over the top right corner.
 *
 * The main line is drawn to be read and filled underneath so a dive reads as water; the rest
 * are thin. Paths are built once per size and lines.
 */
@Composable
private fun Chart(depth: List<Line>, overlay: Overlay?, events: List<Event>) {
    val ink = MaterialTheme.colorScheme.primary
    val stop = MaterialTheme.colorScheme.tertiary
    // The right axis in red, the one colour that reads as another line at a glance.
    val other = MaterialTheme.colorScheme.error
    val water = MaterialTheme.colorScheme.primaryContainer
    // Over a deco stop, the water a diver may not ascend into. Red, being a warning.
    val forbidden = MaterialTheme.colorScheme.errorContainer
    val grid = MaterialTheme.colorScheme.outlineVariant
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val label = MaterialTheme.typography.labelSmall.copy(color = quiet)
    val title = MaterialTheme.typography.labelMedium.copy(color = quiet)
    val switched = MaterialTheme.typography.labelSmall.copy(color = stop)
    val alarmed = MaterialTheme.typography.labelSmall.copy(color = other)
    val measurer = rememberTextMeasurer()
    Spacer(
        modifier = Modifier.fillMaxWidth().height(DEPTH_GRAPH).padding(bottom = HALF)
            .drawWithCache {
            val left = AXIS.toPx()
            val right = size.width - (if (overlay == null) HALF.toPx() else AXIS.toPx())
            val bottom = size.height - FOOT.toPx()
            val top = HEAD.toPx()
            val all = depth.flatMap { it.points } +
                overlay?.lines?.flatMap { it.points }.orEmpty()
            val lastMinute = maxOf(all.maxOfOrNull { it.minute } ?: 0.0, 1.0)
            val deepest = depth.firstOrNull { it.main }?.points?.maxOfOrNull { it.value } ?: 1.0
            val depthHigh = maxOf(deepest * (1.0 + AXIS_ROOM), 1.0)
            fun x(minute: Double): Float = (left + (right - left) * (minute / lastMinute)).toFloat()
            fun yDepth(value: Double): Float =
                (top + (bottom - top) * (value / depthHigh)).toFloat()
            val overPoints = overlay?.lines?.flatMap { it.points }.orEmpty()
            val over = rangeOf(overPoints.map { it.value })
            val overLow = over.start
            val overHigh = over.endInclusive
            fun yOver(value: Double): Float {
                val fraction = (value - overLow) / (overHigh - overLow)
                return (bottom - (bottom - top) * fraction).toFloat()
            }
            val depthPaths = depth.map { pathOf(it, ::x, ::yDepth) }
            val overPaths = overlay?.lines?.map { pathOf(it, ::x, ::yOver) }.orEmpty()
            val main = depth.firstOrNull { it.main }?.takeIf { it.points.isNotEmpty() }
            // The water between a line and the surface: over the dive, what it was under, and
            // over a deco stop, what the diver may not ascend into.
            fun areaOf(line: Line): Path? {
                if (line.points.isEmpty()) return null
                return Path().apply {
                    addPath(pathOf(line, ::x, ::yDepth))
                    lineTo(x(line.points.last().minute), yDepth(0.0))
                    lineTo(x(line.points.first().minute), yDepth(0.0))
                    close()
                }
            }
            val fill = main?.let { areaOf(it) }
            val ceilings = depth.filter { !it.main }.mapNotNull { areaOf(it) }
            val minutes = ticksOf(0.0, lastMinute, 6)
            val depths = ticksOf(0.0, depthHigh, 5)
            val overs = if (overlay == null) emptyList() else ticksOf(overLow, overHigh, 4)
            val thin = Stroke(THIN.toPx())
            val thick = Stroke(LINE_WIDTH.toPx())
            onDrawBehind {
                for (tick in depths) {
                    val at = yDepth(tick)
                    drawLine(grid, Offset(left, at), Offset(right, at), THIN.toPx())
                    val laid = measurer.measure(shortOf(tick), label)
                    val corner = Offset(
                        x = left - laid.size.width - HALF.toPx(),
                        y = yDepth(tick) - laid.size.height / 2f,
                    )
                    drawText(laid, topLeft = corner)
                }
                for (tick in minutes) {
                    drawLine(grid, Offset(x(tick), top), Offset(x(tick), bottom), THIN.toPx())
                    val laid = measurer.measure(shortOf(tick), label)
                    drawText(laid, topLeft = Offset(x(tick) - laid.size.width / 2f, bottom + 2f))
                }
                // The unit along the bottom, once, at the end where the marks run out.
                val unit = measurer.measure("min", label)
                drawText(unit, topLeft = Offset(right - unit.size.width, bottom + 2f))
                for (tick in overs) {
                    val laid = measurer.measure(shortOf(tick), label.copy(color = other))
                    val corner = Offset(right + HALF.toPx(), yOver(tick) - laid.size.height / 2f)
                    drawText(laid, topLeft = corner)
                }
                fill?.let { drawPath(it, water) }
                for (ceiling in ceilings) drawPath(ceiling, forbidden)
                for ((index, line) in depth.withIndex()) {
                    val colour = if (line.main) ink else stop
                    drawPath(depthPaths[index], colour, style = if (line.main) thick else thin)
                }
                for (path in overPaths) drawPath(path, other, style = thin)
                // A switch is a dot on the line and an alarm a triangle, each with its word
                // above, in the colour of the deco stops and of the right axis respectively.
                main?.let { line ->
                    for (event in events) {
                        val at = depthAt(line, event.minute) ?: continue
                        val centre = Offset(x(event.minute), yDepth(at))
                        val colour = if (event.marking == Marking.SWITCH) stop else other
                        if (event.marking == Marking.SWITCH) {
                            drawCircle(colour, MARKED.toPx(), centre)
                        } else {
                            drawPath(triangleAt(centre, MARKED.toPx() * 1.4f), colour)
                        }
                        val style = if (event.marking == Marking.SWITCH) switched else alarmed
                        val laid = measurer.measure(event.label, style)
                        // Kept inside the plot: a switch at the start would otherwise put its
                        // word over the depth axis, and one at the surface over the title.
                        val above = centre.y - MARKED.toPx() * 2 - laid.size.height
                        val corner = Offset(
                            x = (centre.x - laid.size.width / 2f)
                                .coerceIn(left, right - laid.size.width),
                            y = if (above >= top) above else centre.y + MARKED.toPx() * 2,
                        )
                        drawText(laid, topLeft = corner)
                    }
                }
                val heading = measurer.measure("Depth (m)", title)
                drawText(heading, topLeft = Offset(left + HALF.toPx(), 0f))
            }
        },
    )
}

/** A triangle pointing up, [size] across, centred on [centre], which is what an alarm is. */
private fun triangleAt(centre: Offset, size: Float): Path = Path().apply {
    moveTo(centre.x, centre.y - size)
    lineTo(centre.x + size, centre.y + size * 0.7f)
    lineTo(centre.x - size, centre.y + size * 0.7f)
    close()
}

/** A line as a path, stepping where it steps and sloping where it slopes. */
private fun pathOf(line: Line, x: (Double) -> Float, y: (Double) -> Float): Path {
    val path = Path()
    var previous: Point? = null
    for (point in line.points) {
        val before = previous
        when {
            before == null -> path.moveTo(x(point.minute), y(point.value))
            line.stepped -> {
                path.lineTo(x(point.minute), y(before.value))
                path.lineTo(x(point.minute), y(point.value))
            }
            else -> path.lineTo(x(point.minute), y(point.value))
        }
        previous = point
    }
    return path
}

/** A tick's value as a mark reads it: whole where it is whole, else to one decimal. */
private fun shortOf(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

// --- What every screen shares.

/** A quiet remark where there is nothing else to show. */
@Composable
internal fun Aside(text: String) {
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
private val SUBTABS = 340.dp
private val TABLE = 600.dp
private val TREE = 220.dp
private val TRIP = 170.dp
private val NUMBER = 52.dp
private val DATE = 100.dp
private val SITE = 240.dp
private val LABEL = 130.dp
private val SITES = 300.dp
private val MAP = 420.dp
private val DOT = 3.dp
private val MARKED = 5.dp
private val TOWN = 1.5.dp
internal val GLYPH = 20.dp
private val DEPTH_GRAPH = 260.dp
private val PLOT = 340.dp
private val AXIS = 40.dp
private val FOOT = 16.dp
private val HEAD = 28.dp
private val LINE_WIDTH = 2.dp
private val THIN = 1.dp

/** How much of the water colour a river carries, so it reads as a line and not a canal. */
private const val RIVER = 0.6f
internal val LINE = 28.dp
internal val INDENT = 16.dp
internal val GAP = 12.dp
internal val HALF = 4.dp
