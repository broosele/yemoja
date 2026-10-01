package yemoja.ui.gui

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.data.Units
import yemoja.data.ItemReader
import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.Layout
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.logic.DeviceRead
import yemoja.logic.divecomputer.DiveComputer
import yemoja.logic.Change
import yemoja.logic.Evaluated
import yemoja.logic.Import
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Settings
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.evaluate

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
     * Puts [question] to the reader and waits for the answer, or nothing where they give none.
     *
     * Waits, which is what makes it the platform's: a device that guards itself asks for a code
     * part way through a download, from whatever thread the download is running on, and the
     * answer has to come back to that thread before the next byte is read. `LOGIC-24`.
     */
    val ask: (question: String) -> String? = { null },
    /**
     * Asks the reader for a folder or a file to import, or nothing where they name none.
     *
     * One dialog for both, because what is there says how it is read — a folder is another
     * logbook and a file is a UDDF document — and the reader already knows which they have.
     * Absent where the platform cannot ask, and the deed is then greyed like any other.
     * `GUI-33`.
     */
    val pick: ((asking: String) -> String?)? = null,
    /**
     * Asks the reader for a file to write an export to, or nothing where they name none.
     *
     * The platform's, like [pick], and absent where it cannot ask. A name typed without an
     * extension is given `.uddf`, and a file already there is the reader's to confirm. `GUI-37`.
     */
    val save: ((asking: String) -> String?)? = null,
    /**
     * A conversation with the agent the user has installed, or absent where the platform hosts
     * none.
     *
     * An agent is a program on a computer, so a platform that cannot run one has no agent and the
     * deed that opens the panel is greyed. What it is given is the two boxes the user ticks: the
     * tools read them on every call, a conversation being long enough for either answer to change
     * part way through. `GUI-38`, `API-5`.
     */
    val conversing:
        ((writing: () -> Boolean, direct: () -> Boolean, online: () -> Boolean) -> Conversation)? =
        null,
    /**
     * What this platform can do to a logbook as a whole, by deed.
     *
     * A deed the platform cannot do yet is absent, and the home screen offers it greyed: the
     * list of deeds is what the application is for, not what is built. `GUI-30`.
     */
    val deeds: Map<Deed, () -> Unit> = emptyMap(),
    /**
     * A bar beside a box scrolled by [ScrollState] that shows how much more there is, or absent
     * where the platform scrolls without one, as a touch screen does. `GUI-43`.
     */
    val scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)? = null,
    /** Whether the screen is a phone's, which shows one thing at a time. `PHONE-2`. */
    val compact: Boolean = false,
    /**
     * The platform's own back, offered [enabled] and doing [onBack], or absent where it has none.
     *
     * A phone's back button or gesture, which steps back through a tab as the arrow does. `PHONE-2`.
     */
    val back: (@Composable (enabled: Boolean, onBack: () -> Unit) -> Unit)? = null,
    /**
     * Asks for what reading a dive computer needs, and says whether it was given, or absent where
     * nothing needs asking. A phone's Bluetooth and notifications are the user's to allow. `AND-2`.
     */
    val permit: ((granted: (Boolean) -> Unit) -> Unit)? = null,
    /**
     * Told of a read while it runs and once more, with absent, when it stops, or absent where the
     * platform has nothing to do about one. A phone keeps the read alive in the background with a
     * notification of its own. `AND-3`.
     */
    val reading: ((Underway?) -> Unit)? = null,
)

/**
 * Underway is a dive computer being read, as a platform is told of it: which, how far, and how to
 * give it up. `AND-3`. Immutable.
 */
class Underway(val name: String, val done: Long, val total: Long, val cancel: () -> Unit)

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

    /**
     * Whether the branch gathering the sites that name no region is the one chosen.
     *
     * It holds no region, so it cannot be a `place` the way every other branch is. `GUI-25`.
     */
    var unplaced: Boolean by mutableStateOf(false)

    /** Every dive chosen, where several are: their statistics are shown instead. `GUI-23`. */
    var chosenMany: Set<String> by mutableStateOf(emptySet())

    /**
     * The type being made, where **+** has been pressed and nothing saved yet.
     *
     * The item does not exist while this is set: pressing **+** opens an empty form, and the
     * item is made when it is saved, from the fields that were typed. Making it first would
     * mint its id from an item saying nothing — `unknown_person` — and nothing renames it
     * afterwards. `GUI-35`.
     */
    var making: ItemDescription? by mutableStateOf(null)

    /** The item whose card is turned over into its edit form, by id. `GUI-53`. */
    var editing: String? by mutableStateOf(null)

    /** What the tab row's bin asked to delete, while the question is open. `GUI-53`. */
    var deleting: Set<String> by mutableStateOf(emptySet())

    /** Calculations' boxes, and which calculation is chosen. `GUI-43`. */
    val working: Working = Working()

    /** Locations' region. */
    var place: Chosen? by mutableStateOf(null)

    /** Locations' box. On, so a logbook opens on where its dives were. `GUI-26`. */
    var hideUnused: Boolean by mutableStateOf(true)

    /**
     * Home's plot: which variable runs along the bottom, and which up the side. `GUI-30`.
     *
     * Absent until the reader chooses, the plot opening on a pair of its own.
     */
    var across: Int? by mutableStateOf(null)
    var up: Int? by mutableStateOf(null)

    /**
     * Home's plot: how the dives are gathered, and how wide one bar is. `GUI-32`.
     *
     * Absent until the reader chooses. The width is cleared when the bottom axis changes, a
     * width chosen for a calendar meaning nothing on an axis of metres.
     */
    var gathering: Int? by mutableStateOf(null)
    var step: Int? by mutableStateOf(null)

    /** The branches of a tree unfolded, by path; absent until the tree has decided how it opens. */
    var open: Set<String>? by mutableStateOf(null)

    /** Gear's branches folded. */
    var closed: Set<String> by mutableStateOf(emptySet())

    /**
     * The branch of the gear tree chosen, by its path: `cylinder`, or `cylinder/steel`.
     *
     * What a new piece of gear starts out filed under. Choosing an item lets it go, a reader
     * looking at one item no longer being at a category. `GUI-35`.
     */
    var branch: String? by mutableStateOf(null)

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

    /** Say that the logbook changed by some other hand, so that what reads it reads again. */
    fun changed() {
        edition++
    }

    /** What the user chose, or absent where no logbook is open. `UI-2`. */
    val settings: Settings? get() = universe?.settings

    /** What [field] offers: its presets, then what this logbook already holds in it. */
    fun suggested(field: TextDescription): List<String> =
        universe?.suggested(field) ?: field.suggestedSet.orEmpty().toList()

    fun change(changes: List<Change>): Outcome {
        // A window opened on no logbook has nothing to write to, and nothing in it asks. `GUI-30`.
        val open = universe ?: return Outcome.Refused("no logbook is open")
        val outcome = open.change(Operation.EDIT, *changes.toTypedArray())
        if (outcome is Outcome.Done) edition++
        return outcome
    }

    /** Makes the person called [id] the logbook's user. `GUI-51`. */
    fun own(id: String): Outcome {
        val open = universe ?: return Outcome.Refused("no logbook is open")
        val outcome = open.own(id)
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
    // Per logbook: what a tab was left on is an item of the one that was open, and another
    // logbook does not hold it. `GUI-27` keeps a tab's place, within one logbook.
    val kept = remember(universe) { TABS.associateWith { Kept() } }
    val changer = remember(universe) { Changer(universe) }
    // A download outlives the tab it was started from, so it is held here and run in a scope that
    // lasts as long as the window. Another logbook opened gives it up. `GUI-52`.
    val reading = remember(universe) { Reading() }
    val downloads = rememberCoroutineScope()
    DisposableEffect(reading) { onDispose { reading.read?.cancelled = true } }
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
                item.description == Types.REGION -> {
                    there.place = chosen
                    unfold(universe.logbook, there, id)
                }
                to.shape == Shape.PLACES -> {
                    there.chosen = chosen
                    // The map follows the site: a site in Egypt is looked at on Egypt. A site
                    // naming no region is looked at where such sites hang. `GUI-25`.
                    val home = homeOf(universe.logbook, item)
                    there.unplaced = home == null && item.description == Types.DIVE_SITE
                    if (there.unplaced) {
                        there.place = null
                    } else {
                        home?.let { at ->
                            universe.logbook[at]?.let { there.place = Chosen(at, titleOf(it), it) }
                            unfold(universe.logbook, there, at)
                        }
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
    // The agent panel, opened from the tab row and staying open as the reader moves between
    // tabs: a dive the agent names is looked at while the conversation carries on. `GUI-38`.
    val conversing = platform.conversing
    var talking by remember(universe) { mutableStateOf(false) }
    // The command that starts an agent, read again whenever anything changed, since the settings
    // form that sets it says so through the changer. `GUI-42`.
    val command = remember(universe, changer.edition) {
        universe?.settings?.text(Settings.AGENT_COMMAND)
    }
    val unasked = unaskedOf(universe != null, conversing != null, command)
    // What a review came to, carried to the panel so the conversation records what became of what
    // an agent staged. Shown to the reader and never put to the agent. `GUI-38`.
    var told by remember(universe) { mutableStateOf<Told?>(null) }
    // How much an agent has staged, which the panel says and the home screen reviews.
    val staged = remember(universe, changer.edition) { universe?.staging?.staged?.size ?: 0 }
    // A plan is added to a dive, or one saved there edited, in the Calculations tab's planner,
    // which the Dives tab opens by switching to it. `GUI-44`.
    val opener: (Bound) -> Unit = { bound ->
        tabs.firstOrNull { it.shape == Shape.CALCULATIONS }?.let { calculations ->
            val working = kept.getValue(calculations).working
            working.calculation = Calculation.PLAN
            working.saving.open(bound, universe, working.shaping)
            tab = calculations
        }
    }
    // A phone steps back through a tab one page at a time, by the arrow and by its own back.
    val back = if (platform.compact) backOf(tab, kept.getValue(tab)) else null
    platform.back?.invoke(back != null) { back?.invoke() }
    CompositionLocalProvider(
        LocalChanger provides changer,
        LocalPlanOpener provides opener,
        LocalCompact provides platform.compact,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Tabs(
                    tabs = tabs,
                    chosen = tab,
                    onChoose = { tab = it },
                    kept = kept.getValue(tab),
                    ribbon = ribbonOf(tab, kept.getValue(tab)),
                    download = reading.stage,
                    onBack = back,
                    unasked = unasked,
                    asking = talking,
                ) { talking = !talking }
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            tab.shape == Shape.HOME -> Home(
                                universe = universe,
                                platform = platform,
                                kept = kept.getValue(tab),
                                reading = reading,
                                downloads = downloads,
                                onApplied = { said -> told = Told(said) },
                            )

                            tab.shape == Shape.MANUAL ->
                                Manuals(platform.manual, platform.open, kept.getValue(tab))

                            tab.shape == Shape.CALCULATIONS ->
                                Calculations(kept.getValue(tab).working, universe?.settings, platform.scrollbar, universe)

                            universe == null -> Unit
                            else -> Subject(
                                universe = universe,
                                tab = tab,
                                atlas = atlas,
                                kept = kept.getValue(tab),
                                today = platform.today,
                                onFollow = follow,
                            )
                        }
                    }
                    if (talking && universe != null && conversing != null) {
                        VerticalDivider()
                        Panel(
                            set = universe.logbook,
                            conversing = conversing,
                            command = command.orEmpty(),
                            staged = staged,
                            told = told,
                            onReview = { tab = tabs.first() },
                            onTurn = { changer.changed() },
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
 * `PHONE-5` settled that a phone keeps this a gesture away instead, which is a placement rather
 * than a different set of tabs.
 */
/**
 * One view whose words can be selected and copied, apart from every other view's.
 *
 * A selection stays inside the view it began in, so a drag down a site's fields does not run on
 * into the list beside it. The tabs along the top are in none: they are what is pressed to move
 * about, not what is read. `GUI-36`.
 */
@Composable
internal fun Selectable(content: @Composable () -> Unit) {
    SelectionContainer(content = content)
}

/**
 * A dropdown menu whose words are outside every selection.
 *
 * A menu opens in a layer of its own and still inherits the selection of the view it opened from.
 * Pressing on one of its words would then begin a selection in a layer the view cannot measure
 * against, and Compose throws rather than choosing. A menu's words are pressed and not read, so
 * nothing is lost by it. `GUI-36`.
 */
@Composable
internal fun Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    /**
     * Whether the menu takes the keyboard.
     *
     * A menu that narrows as a reader types must not: the platform hands the keyboard to whatever
     * opened last, so an open menu swallows the rest of the word and the box stops filling.
     * `GUI-29`.
     */
    takesKeys: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = takesKeys),
    ) {
        val column = this
        DisableSelection { column.content() }
    }
}

@Composable
private fun Tabs(
    tabs: List<Tab>,
    chosen: Tab,
    onChoose: (Tab) -> Unit,
    /** What the chosen tab holds on to, which the add, edit and delete buttons act on. */
    kept: Kept,
    ribbon: Ribbon,
    /** How far a download has got, which the home tab's icon shows from every tab. `GUI-52`. */
    download: Stage,
    /** One step back on a phone, or absent where there is nowhere back to go. `PHONE-2`. */
    onBack: (() -> Unit)?,
    /** Why the agent cannot be asked, or absent where it can. */
    unasked: String?,
    /** Whether the panel is open, which is what the button would shut. */
    asking: Boolean,
    onAsk: () -> Unit,
) {
    if (LocalCompact.current) {
        CompactTabs(tabs, chosen, onChoose, kept, ribbon, download, onBack)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tabs as wide as their names, so the row's own rule would stop where they do. The
            // rule is drawn below instead, across the whole window.
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(chosen),
                edgePadding = GAP,
                divider = {},
                modifier = Modifier.weight(1f),
            ) {
                for (tab in tabs) {
                    LeadingIconTab(
                        selected = tab === chosen,
                        onClick = { onChoose(tab) },
                        text = { Text(tab.name) },
                        icon = { TabIcon(tab, download) },
                    )
                }
            }
            // Add, edit and delete stand here for every tab rather than on each card, so they
            // are in one place whatever the tab shows. `GUI-53`.
            Buttons(ribbon, kept)
            // On every tab rather than on home, since a question comes up wherever the reader
            // is. An icon, so the row stays the tabs' own; what pressing it would do is said over
            // it while the pointer rests there, and the reason instead while it is greyed. The
            // one button opens the panel and shuts it: the same press either way. `GUI-38`.
            val said = if (asking) CLOSE_THE_AGENT else ASK_AN_AGENT
            Explained(unasked ?: said) {
                IconButton(
                    onClick = onAsk,
                    enabled = unasked == null,
                    modifier = Modifier.padding(horizontal = GAP),
                ) { Icon(Icons.Filled.AutoAwesome, contentDescription = said) }
            }
        }
        HorizontalDivider()
    }
}

/**
 * A tab's glyph, or on Home what a download is doing: a spinner while it reads, a tick once it
 * has finished. `GUI-52`.
 */
@Composable
private fun TabIcon(tab: Tab, download: Stage) {
    val busy = busyOf(download).takeIf { tab.shape == Shape.HOME }
    when {
        busy == null -> Icon(tab.icon, contentDescription = null)
        download == Stage.READY -> Explained(busy) {
            Icon(Icons.Filled.DownloadDone, contentDescription = busy)
        }

        else -> Explained(busy) {
            CircularProgressIndicator(modifier = Modifier.size(TAB_ICON), strokeWidth = 2.dp)
        }
    }
}

/**
 * The tab row as a phone has it: back, the tab open with a menu of the others, then add, edit and
 * delete.
 *
 * Seven tabs do not fit across a phone, so they are a press away rather than in view, `PHONE-5`.
 * The agent's button is not there, an agent being a program a phone cannot start. `PHONE-1`.
 */
@Composable
private fun CompactTabs(
    tabs: List<Tab>,
    chosen: Tab,
    onChoose: (Tab) -> Unit,
    kept: Kept,
    ribbon: Ribbon,
    download: Stage,
    onBack: (() -> Unit)?,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.clickable { choosing = true }.padding(GAP),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HALF),
                ) {
                    TabIcon(chosen, download)
                    Text(chosen.name, style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "other tabs")
                }
                Menu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    for (tab in tabs) {
                        DropdownMenuItem(
                            text = { Text(tab.name) },
                            leadingIcon = { TabIcon(tab, download) },
                            onClick = {
                                choosing = false
                                onChoose(tab)
                            },
                        )
                    }
                }
            }
            Buttons(ribbon, kept)
        }
        HorizontalDivider()
    }
}

/**
 * [content] with [said] shown over it while the pointer rests there, or plain where there is
 * nothing to say.
 *
 * A greyed button says nothing on its own, and a reader who cannot press it wants to know why
 * without leaving the tab they are on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Explained(said: String?, content: @Composable () -> Unit) {
    if (said == null) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        // Outside every selection, as a menu's words are. `GUI-36`. A tooltip opens in a layer of
        // its own and would join the selection of the view under it, and a press on words it
        // covers would then measure across two layers and throw.
        tooltip = { PlainTooltip { DisableSelection { Text(said) } } },
        // Kept while the pointer rests there, rather than for a second and a half: a reader is
        // being told where to go, and reads at their own pace.
        state = rememberTooltipState(isPersistent = true),
        content = content,
    )
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
private fun Home(
    universe: Universe?,
    platform: Platform,
    kept: Kept,
    /** The download, which is the window's rather than this tab's. `GUI-52`. */
    reading: Reading,
    downloads: CoroutineScope,
    onApplied: (String) -> Unit = {},
) {
    val set = universe?.logbook
    val changer = LocalChanger.current
    val edition = changer.edition
    val greeting = remember(set, edition) { set?.let { greetingOf(it) } }
    val taking = remember(universe) { Taking() }
    val giving = remember(universe) { Giving() }
    val choosing = remember(universe) { Choosing() }
    val scope = rememberCoroutineScope()
    // A finished read is staged on arriving here, and staged afresh on every arrival after, so a
    // review left half done meets the logbook as it is now. `GUI-52`. It waits while a file's
    // import is under review, the two sharing the one staging.
    val ready = reading.read != null &&
        (reading.stage == Stage.READY || reading.stage == Stage.DONE)
    LaunchedEffect(universe, ready, taking.open) {
        if (ready && universe != null && !taking.open) arrive(universe, reading, changer)
    }
    Selectable {
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
                modifier = Modifier.padding(top = HALF),
            )
            val owed = remember(set, edition, universe?.user) {
                set?.let { owedIn(it, universe?.user, platform.today()) }.orEmpty()
            }
            Owing(owed)
            Spacer(modifier = Modifier.height(GAP * 2))
            // Reading a computer is this layer's own: everything it needs is on the universe, and
            // what a platform adds is only the asking. `GUI-31`.
            val deeds = platform.deeds + buildMap {
                if (universe != null) {
                    // One download at a time. Another started over a finished one replaces it.
                    if (reading.stage != Stage.LOOKING && reading.stage != Stage.READING) {
                        put(Deed.DOWNLOAD) {
                            val start = { downloads.launch { look(universe, platform, reading) } }
                            val permit = platform.permit
                            if (permit == null) {
                                start()
                            } else {
                                permit { granted ->
                                    if (granted) {
                                        start()
                                    } else {
                                        reading.said = REFUSED_BLUETOOTH
                                        reading.stage = Stage.DONE
                                    }
                                }
                            }
                        }
                    }
                    put(Deed.SETTINGS) {
                        if (!choosing.open) choosing.fill(universe.settings)
                        choosing.open = true
                    }
                    if (platform.pick != null) {
                        put(Deed.IMPORT) { take(universe, platform, taking, changer) }
                    }
                    platform.save?.let { save ->
                        put(Deed.EXPORT) {
                            // Asked here rather than inside the writing: a platform's dialog
                            // waits, and waiting inside a coroutine the window is running
                            // breaks the window's own machinery.
                            save("Export this logbook to UDDF")?.let { to ->
                                scope.launch { give(universe, to, giving) }
                            }
                        }
                    }
                }
            }
            Inset("System") {
                Deeds(deeds)
                Reader(universe, platform, reading, changer, downloads)
                Taker(universe, taking, changer)
                // Said through the changer, so a command set here reaches the button on the tab
                // row without a change to the logbook.
                Chooser(universe, choosing) { changer.changed() }
                Review(universe, changer, onApplied)
                giving.said?.let { Aside(it) }
            }
            // Nothing to count where there is no logbook, and nothing to say about that.
            if (set != null) Inset("Statistics") { Plot(set, kept, edition) }
            Spacer(modifier = Modifier.height(GAP * 2))
        }
    }
}

/**
 * What the logbook owes, under the greeting, in two boxes rather than one.
 *
 * What has lapsed is read as an error and what is merely near is read as a caution, and the two
 * are separated because a box can only be one colour: putting a service due next week inside a
 * red one says it is already a problem. Nothing at all is shown where nothing is due, which is
 * the ordinary case and should stay silent. `GUI-34`.
 */
@Composable
private fun Owing(owed: List<Owed>) {
    if (owed.isEmpty()) return
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Warned(
        owed.filter { it.lapsed },
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
    )
    Warned(
        owed.filterNot { it.lapsed },
        if (dark) CAUTION_DARK else CAUTION_LIGHT,
        if (dark) ON_CAUTION_DARK else ON_CAUTION_LIGHT,
    )
}

/** One box of warnings, a line apiece, or nothing at all where there are none. */
@Composable
private fun Warned(owed: List<Owed>, behind: Color, infront: Color) {
    if (owed.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = GAP),
        shape = MaterialTheme.shapes.medium,
        color = behind,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP)) {
            for (one in owed) {
                Text(
                    text = one.said,
                    style = MaterialTheme.typography.bodyMedium,
                    color = infront,
                )
            }
        }
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
 * Reading is where a download has got to, and what it is waiting on.
 *
 * Its own object rather than a handful of states, because a download outlives every redraw and
 * the screen is only ever a reading of it. `GUI-31`.
 */
private class Reading {
    var stage: Stage by mutableStateOf(Stage.IDLE)
    var found: List<DiveComputer> by mutableStateOf(emptyList())
    var reading: String? by mutableStateOf(null)
    var said: String? by mutableStateOf(null)
    var arrived: Int by mutableStateOf(0)

    /** The read under way or finished, held until its dives are reviewed or it is put aside. */
    var read: DeviceRead? by mutableStateOf(null)

    /** How far the read has got, as the device counts. */
    var done: Long by mutableStateOf(0L)
    var total: Long by mutableStateOf(0L)

    /** Give the read up, or put a finished one aside, and go back to the button. */
    fun drop() {
        read?.cancelled = true
        read = null
        stage = Stage.IDLE
    }
}

/**
 * Taking is where an import has got to: whether one is open, and what it has to say.
 *
 * Apart from [Reading] because the two are different errands that happen to end in the same
 * review. An import has nothing to look for and nothing to choose between; it is a path, and
 * then a list. `GUI-33`.
 */
private class Taking {
    var open: Boolean by mutableStateOf(false)
    var said: String? by mutableStateOf(null)
    var arrived: Int by mutableStateOf(0)
}

/**
 * Stage what is at the path the reader names, and open the review on it.
 *
 * What a folder or a file turns out to hold is the model's to say, and so is refusing it, so
 * what is here is the asking and the showing. `GUI-33`.
 */
private fun take(universe: Universe, platform: Platform, taking: Taking, changer: Changer) {
    val pick = platform.pick ?: return
    val from = pick("Import a logbook or a UDDF file") ?: return
    taking.open = true
    when (val done = universe.importFrom(from)) {
        is Outcome.Refused -> {
            taking.said = done.reason
            taking.arrived = 0
        }

        is Outcome.Done -> {
            val import = universe.importing
            taking.arrived = arrivedIn(import)
            taking.said = if (import == null || taking.arrived == 0) {
                "Yemoja found nothing to import in $from. It reads a logbook folder " +
                    "and a UDDF file."
            } else {
                summaryOf(countedIn(import))
            }
        }
    }
    changer.changed()
}

/** What the last export said, which stays under the deeds until the next. `GUI-37`. */
private class Giving {
    var said: String? by mutableStateOf(null)
}

/**
 * Write the logbook to [to], off the interface's thread, and say what went.
 *
 * Nothing in the logbook changes, so there is nothing to review and nothing to refresh. `GUI-37`.
 */
private suspend fun give(universe: Universe, to: String, giving: Giving) {
    giving.said = "Writing $to…"
    giving.said = withContext(Dispatchers.Default) {
        try {
            exportSaid(universe.exportTo(to), to)
        } catch (refused: Exception) {
            "$to could not be written: ${refused.message}"
        }
    }
}

/** Look for what is within reach, and read it where exactly one thing is. */
private suspend fun look(universe: Universe, platform: Platform, reading: Reading) {
    reading.stage = Stage.LOOKING
    reading.said = null
    val found = withContext(Dispatchers.Default) { universe.attached() }
    reading.found = found
    when (found.size) {
        0 -> {
            reading.stage = Stage.DONE
            reading.said = emptyOf(universe.readable)
        }

        1 -> read(universe, platform, reading, found.single())
        else -> reading.stage = Stage.CHOOSING
    }
}

/**
 * Read [computer], which takes minutes, off the window's thread and touching no logbook.
 *
 * What the read needs of the logbook is gathered first, here. The logbook stays open to editing
 * while it runs, and nothing is staged until the user comes back to Home. `GUI-52`.
 */
private suspend fun read(
    universe: Universe,
    platform: Platform,
    reading: Reading,
    computer: DiveComputer,
) {
    val read = universe.readerOf(computer) { platform.ask(it) }
    reading.read = read
    reading.reading = computer.name
    reading.said = null
    reading.done = 0
    reading.total = 0
    reading.stage = Stage.READING
    val told = platform.reading
    val cancel = { reading.drop() }
    told?.invoke(Underway(computer.name, 0, 0, cancel))
    val failed = try {
        coroutineScope {
            // Progress is written on the window's thread, which is the one that redraws for it.
            val window = this
            withContext(Dispatchers.Default) {
                read.run { done, total ->
                    window.launch {
                        reading.done = done
                        reading.total = total
                        if (reading.read === read) told?.invoke(Underway(computer.name, done, total, cancel))
                    }
                }
            }
        }
        null
    } catch (stopped: Exception) {
        // Nothing is waiting on this read but the window, so what stopped it is said there.
        "The download from ${computer.name} stopped: ${stopped.message ?: stopped::class.simpleName}"
    } finally {
        told?.invoke(null)
    }
    // Given up meanwhile, or replaced by another: what came back is nobody's.
    if (reading.read !== read) return
    if (failed != null) {
        reading.read = null
        reading.said = failed
        reading.stage = Stage.DONE
        return
    }
    reading.stage = Stage.READY
}

/**
 * Stage what a finished read brought and open the review on it, on the window's thread.
 *
 * Whatever an earlier visit staged is put down first, so what is shown is this visit's. A read
 * that leaves nothing to review is let go, since coming back would find nothing either. `GUI-52`.
 */
private fun arrive(universe: Universe, reading: Reading, changer: Changer) {
    val read = reading.read ?: return
    universe.stopImporting()
    val outcome = universe.arrive(read)
    reading.arrived = arrivedIn(universe.importing)
    reading.said = outcomeOf(outcome, reading.arrived)
    reading.stage = Stage.DONE
    if (reading.arrived == 0) reading.read = null
    changer.changed()
}

/**
 * A download, from the button being pressed to the dives being taken in.
 *
 * Nothing at all until one is started, so the section is a row of buttons on an ordinary day.
 */
@Composable
private fun Reader(
    universe: Universe?,
    platform: Platform,
    reading: Reading,
    changer: Changer,
    scope: CoroutineScope,
) {
    if (universe == null || reading.stage == Stage.IDLE) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        sayingOf(reading.stage, reading.reading)?.let { Aside(it) }
        if (reading.stage == Stage.CHOOSING) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (computer in reading.found) {
                    Button(
                        onClick = { scope.launch { read(universe, platform, reading, computer) } },
                    ) { Text(namedOf(computer)) }
                }
            }
        }
        if (reading.stage == Stage.READING) {
            if (reading.total > 0) {
                LinearProgressIndicator(
                    progress = { (reading.done.toFloat() / reading.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
                )
            }
            progressOf(reading.done, reading.total)?.let { Aside(it) }
            TextButton(onClick = { reading.drop() }) { Text("Cancel") }
        }
        reading.said?.let { Aside(it) }
        if (reading.stage == Stage.DONE && reading.arrived > 0) {
            Arrived(
                universe = universe,
                changer = changer,
                after = { taken ->
                    reading.arrived = arrivedIn(universe.importing)
                    if (reading.arrived == 0) {
                        universe.stopImporting()
                        reading.read = null
                    }
                    reading.said = taken.refusal ?: takenSaid(taken.many)
                },
                leave = { reading.drop() },
            )
        } else if (reading.stage == Stage.DONE) {
            TextButton(onClick = { reading.drop() }) { Text("Close") }
        }
    }
}

/**
 * What an import brought: the dives to review, and a line counting everything else.
 *
 * The same list a download is reviewed with, because the question is the same one — which of
 * these am I already holding — and the answer machinery does not care what put them there.
 * What differs is the rest: a download makes dives and sites and nothing else, `LOGIC-20`,
 * while another logbook can bring every type there is. Those are counted rather than listed,
 * and they come in with the dives. `GUI-33`.
 */
@Composable
private fun Taker(universe: Universe?, taking: Taking, changer: Changer) {
    if (universe == null || !taking.open) return
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        taking.said?.let { Aside(it) }
        if (taking.arrived > 0) {
            Arrived(
                universe = universe,
                changer = changer,
                after = { taken ->
                    val decided = afterDeciding(universe, taken)
                    taking.arrived = decided.arrived
                    taking.said = decided.said
                },
                leave = { taking.open = false },
            )
        } else {
            TextButton(onClick = {
                universe.stopImporting()
                taking.open = false
            }) { Text("Close") }
        }
    }
}

/**
 * Every deed the application knows, as a button apiece, the ones this platform cannot do yet
 * greyed rather than left out. `GUI-30`.
 */
@Composable
private fun Deeds(deeds: Map<Deed, () -> Unit>) {
    // Wrapping onto a second line where the window is too narrow for one: a phone, or a desktop
    // window made small.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(HALF),
    ) {
        for (deed in Deed.entries) {
            val act = deeds[deed]
            Button(onClick = { act?.invoke() }, enabled = act != null) { Text(deed.label) }
        }
    }
    if (Deed.entries.any { it !in deeds }) {
        Aside(
            "The greyed-out actions need a logbook open, or this platform cannot offer them, or " +
                "one is already under way.",
        )
    }
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
        Aside("There is not enough in this logbook yet to draw this.")
        return
    }
    val opening = remember(variables) { openingOf(variables) }
    val alongAt = (kept.across ?: opening.first).coerceIn(variables.indices)
    val upAt = (kept.up ?: opening.second).coerceIn(variables.indices)
    val across = variables[alongAt]
    val up = variables[upAt]
    val gathering = Gathering.entries[
        (kept.gathering ?: Gathering.COUNT.ordinal).coerceIn(Gathering.entries.indices),
    ]
    val steps = remember(set, edition, across) {
        stepsOf(across, divesMadeIn(set).mapNotNull(across.of))
    }
    val fitted = remember(set, edition, across, up, gathering, steps) {
        if (gathering.bars) fittedOf(set, across, up, gathering, steps) else 0
    }
    val stepAt = (kept.step ?: fitted).coerceIn(steps.indices)
    // Onto further lines where the screen is too narrow for one, as on a phone.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = HALF),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Picked(Gathering.entries.map { it.label }, gathering.ordinal) { kept.gathering = it }
        if (gathering.reads) Picked(variables.map { it.label }, upAt) { kept.up = it }
        Aside("against")
        Picked(variables.map { it.label }, alongAt) {
            kept.across = it
            kept.step = null
        }
        if (gathering.bars) {
            Aside("by")
            Picked(steps.map { it.label }, stepAt) { kept.step = it }
        }
    }
    when (gathering) {
        Gathering.EACH -> {
            val spots = remember(set, edition, across, up) { plottedOf(set, across, up) }
            if (spots.isEmpty()) Aside("No dive in this logbook records both of the things chosen.")
            else Scatter(spots, titledOf(across), titledOf(up), joined = false)
        }

        Gathering.RUNNING -> {
            val spots = remember(set, edition, across, up) { runningOf(set, across, up) }
            if (spots.isEmpty()) Aside("No dive in this logbook records both of the things chosen.")
            else Scatter(spots, titledOf(across), titledOf(up), joined = true)
        }

        else -> {
            val bars = remember(set, edition, across, up, gathering, steps, stepAt) {
                barsOf(set, across, up, gathering, steps[stepAt])
            }
            if (bars.isEmpty()) Aside("No dive in this logbook records both of the things chosen.")
            else Bars(bars, titledOf(across), sideOf(gathering, up))
        }
    }
}

/**
 * What the side of a bar chart is titled.
 *
 * Counting dives reads *Dives*, there being no variable up the side to name. Everything else
 * names the variable and nothing more: the gathering is already said in the row above, and
 * *Largest max depth* is a worse sentence than the two halves apart.
 */
private fun sideOf(gathering: Gathering, up: Variable): String =
    if (gathering.reads) titledOf(up) else "Dives"

/** One thing chosen from a list, its name being the box that chooses it. */
@Composable
internal fun Picked(labels: List<String>, chosen: Int, onChoose: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    if (labels.isEmpty()) return
    val at = chosen.coerceIn(labels.indices)
    Box {
        Row(
            modifier = Modifier.clip(SHAPE).clickable { picking = true }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = GAP, end = HALF, top = HALF, bottom = HALF),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = labels[at], style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "choose what is plotted",
                modifier = Modifier.size(GLYPH),
            )
        }
        Menu(expanded = picking, onDismissRequest = { picking = false }) {
            for ((index, label) in labels.withIndex()) {
                DropdownMenuItem(
                    text = { Text(label) },
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
 * titled by what it carries. [joined] draws the line through them that a running total is.
 */
@Composable
private fun Scatter(spots: List<Spot>, across: String, up: String, joined: Boolean) {
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
                if (joined) {
                    val path = Path()
                    for ((at, spot) in spots.withIndex()) {
                        val point = Offset(x(spot.across), y(spot.up))
                        if (at == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, ink, style = Stroke(width = LINE_WIDTH.toPx()))
                } else {
                    for (spot in spots) {
                        drawCircle(ink, DOT.toPx(), Offset(x(spot.across), y(spot.up)))
                    }
                }
                val upward = measurer.measure(up, title)
                drawText(upward, topLeft = Offset(left + HALF.toPx(), 0f))
                val onward = measurer.measure(across, title)
                drawText(onward, topLeft = Offset(right - onward.size.width, 0f))
            }
        },
    )
}

/**
 * The gathered dives as bars standing on nought, each covering the stretch of axis it gathers.
 *
 * The side always begins at nought, whatever the bars reach: a bar read against an axis that
 * starts elsewhere says the wrong thing about how big it is, which is the whole of what a bar
 * is for.
 */
@Composable
private fun Bars(bars: List<Bar>, across: String, up: String) {
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
            val from = bars.minOf { it.from }
            val to = bars.maxOf { it.to }
            val high = maxOf(bars.maxOf { it.value }, 0.0)
            val low = minOf(bars.minOf { it.value }, 0.0)
            val ups = if (high > low) low..high else low..(low + 1.0)
            fun x(value: Double): Float {
                val part = if (to > from) (value - from) / (to - from) else 0.5
                return (left + (right - left) * part).toFloat()
            }
            fun y(value: Double): Float {
                val part = (value - ups.start) / (ups.endInclusive - ups.start)
                return (bottom - (bottom - top) * part).toFloat()
            }
            val along = ticksOf(from, to, 6)
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
                val ground = y(0.0)
                for (bar in bars) {
                    val begins = x(bar.from)
                    val ends = x(bar.to)
                    val wide = maxOf(ends - begins - THIN.toPx(), 1f)
                    val reaches = y(bar.value)
                    drawRect(
                        color = ink,
                        topLeft = Offset(begins, minOf(ground, reaches)),
                        size = Size(wide, maxOf(kotlin.math.abs(reaches - ground), THIN.toPx())),
                    )
                }
                val upward = measurer.measure(up, title)
                drawText(upward, topLeft = Offset(left + HALF.toPx(), 0f))
                val onward = measurer.measure(across, title)
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
 * Locations chooses two things, a region and something at it, and shows both. `GUI-25`.
 */
@Composable
private fun Subject(
    universe: Universe,
    tab: Tab,
    atlas: Atlas?,
    kept: Kept,
    today: () -> yemoja.data.Date,
    onFollow: (String) -> Unit,
) {
    val set = universe.logbook
    val user = universe.user
    val edition = LocalChanger.current.edition
    val tree = remember(set, kept.hideUnused, edition) { shownTreeOf(set, kept.hideUnused) }
    // What a tab opens on the first time: Locations on the widest root, which is the world, and
    // Community on the user, whose logbook this is.
    val compact = LocalCompact.current
    remember(kept) {
        if (!kept.opened) {
            kept.opened = true
            // A phone opens on the list, there being no room to show what is chosen beside it.
            if (!compact) when (tab.shape) {
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
    // A phone shows the list or what was chosen from it, never both. `PHONE-2`.
    val page = if (compact) pageOf(tab, kept) else null
    Row(modifier = Modifier.fillMaxSize()) {
        if (page == null || page == Page.LIST) Box(
            modifier = if (page == null) Modifier.width(wide) else Modifier.fillMaxSize(),
        ) {
            when (tab.shape) {
                Shape.DIVES -> Selectable {
                    Dives(set, kept)
                }
                Shape.GEAR -> Selectable {
                    Gear(set, chosen, kept) { kept.chosen = it }
                }
                Shape.TYPES -> Selectable {
                    Types(set, tab, user, chosen, kept) { kept.chosen = it }
                }
                // Two views of its own, the regions and what is at the one chosen.
                Shape.PLACES -> Places(
                    set = set,
                    tree = tree,
                    kept = kept,
                    treeOnly = page != null,
                    onPlace = { region ->
                        kept.place = region
                        // What was chosen stays chosen while the new region still lists it.
                        val (sites, wrecks) = atPlaceIn(set, region.id, kept.hideUnused)
                        if ((sites + wrecks).none { it.id == chosen?.id }) kept.chosen = null
                    },
                    onChoose = { kept.chosen = it },
                )
                Shape.MANUAL, Shape.HOME, Shape.CALCULATIONS -> Unit
            }
        }
        if (page == null) VerticalDivider()
        val changer = LocalChanger.current
        val making = remember(tab, chosen) { makingOf(tab, chosen) }
        val makeable = remember(tab, chosen) { makeableIn(tab, chosen) }
        // Pressing + opens a form and makes nothing: the item is made on Save, so its id is
        // minted from what was typed rather than from an item saying nothing. `GUI-35`.
        val add: ((ItemDescription) -> Unit)? =
            if (makeable.isEmpty()) null else { type -> kept.making = type }
        // An edit left unsaved is given up when the reader chooses something else, there being
        // no card left on screen to save or cancel it from. `GUI-53`.
        LaunchedEffect(chosen?.id, kept.place?.id) {
            if (kept.editing != null && kept.editing != chosen?.id && kept.editing != kept.place?.id) {
                kept.editing = null
            }
        }
        val going = kept.deleting
        var deleteRefused by remember(going) { mutableStateOf<String?>(null) }
        var clearing by remember(going) { mutableStateOf(false) }
        if (going.isNotEmpty()) {
            deleteAsked(set, going)?.let { asked ->
                Confirm(
                    asked = asked,
                    // A refusal is said where the question was asked, and the question stays.
                    warned = listOfNotNull(deleteRefused, deleteWarned(set, going))
                        .joinToString("\n\n").ifEmpty { null },
                    clearing = clearing,
                    onClearing = { clearing = it },
                    onNo = { kept.deleting = emptySet() },
                    onYes = {
                        when (val done = changer.change(going.map { Change.Delete(it, clearing) })) {
                            is Outcome.Refused -> deleteRefused = done.reason
                            is Outcome.Done -> {
                                kept.deleting = emptySet()
                                kept.chosen = null
                                kept.chosenMany = emptySet()
                                // A region deleted from its own card takes the card with it.
                                if (kept.place?.id in going) kept.place = null
                            }
                        }
                    },
                )
            }
        }
        if (page != Page.LIST) Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            Selectable {
                when {
                    // A form being filled in comes before the tab's own view, Locations included:
                    // pressing + there would otherwise open a form nothing showed. `GUI-35`.
                    kept.making != null -> {
                        NewCard(
                            type = kept.making!!,
                            set = set,
                            started = startedOf(kept.making!!, kept.branch),
                            onCancel = { kept.making = null },
                            onMade = { id ->
                                kept.making = null
                                kept.branch = null
                                set[id]?.let { kept.chosen = Chosen(id, titleOf(it), it) }
                                kept.chosenMany = emptySet()
                            },
                        )
                    }
                    // A phone shows the site or wreck chosen on a page of its own, after the
                    // page for the region it was chosen from.
                    page == Page.ITEM && chosen != null && tab.shape == Shape.PLACES -> ItemView(
                        chosen = chosen,
                        onFollow = onFollow,
                        kept = kept,
                    )
                    tab.shape == Shape.PLACES -> {
                        PlaceView(
                            set = set,
                            atlas = atlas,
                            hideUnused = kept.hideUnused,
                            place = kept.place,
                            chosen = chosen,
                            onFollow = onFollow,
                            kept = kept,
                        )
                    }
                    kept.chosenMany.size > 1 -> ManyView(set, kept.chosenMany, onFollow)
                    chosen != null -> ItemView(
                        chosen = chosen,
                        onFollow = onFollow,
                        kept = kept,
                        onOwn = if (owningOf(chosen.item, user, set)) ({ changer.own(chosen.id) }) else null,
                    )
                    // Where nothing is chosen the middle still offers to make one, which is the
                    // only way a logbook with nothing in it grows a first item. `GUI-35`.
                    making != null && add != null -> Empty(makeSaid(making)) { add(making) }
                    else -> Middle("Select an item")
                }
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
 * selected. `DESK-7`. The span is drawn as one: the cell is tinted down the whole run, its title
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
                        chosen = many.isNotEmpty() && year.made.all { it.dive.id in many },
                        onToggle = {
                            kept.open = if (unfolded) open - year.label else open + year.label
                        },
                        // The year's dives together, which is what a year is for; the ones made,
                        // since what they come to is a figure over diving. `GUI-23`, `GUI-39`.
                        onChoose = {
                            kept.chosenMany = year.made.map { it.dive.id }.toSet()
                            kept.chosen = year.made.firstOrNull()?.dive
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
                            Cell(row.number, NUMBER, here, TextAlign.End, quiet = row.planned)
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
            text = "  ·  ${year.made.size} dives",
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
private fun Cell(
    text: String,
    width: Dp,
    chosen: Boolean,
    align: TextAlign = TextAlign.Start,
    /** Whether it is said rather than read: the word a plan carries where a number would be. */
    quiet: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (quiet && !chosen) MaterialTheme.colorScheme.outline else onTint(chosen),
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
    // A branch is chosen as a region is on Location: the arrow folds it, the line chooses it, and
    // what is chosen is what a new piece of gear starts out filed under. `GUI-35`.
    val choose = { key: String ->
        kept.branch = key
        kept.chosen = null
        kept.chosenMany = emptySet()
    }
    val took = { item: Chosen ->
        kept.branch = null
        onChoose(item)
    }
    LazyColumn(state = kept.list, modifier = Modifier.fillMaxHeight().padding(GAP)) {
        // Filed under nothing sits at the top rather than in a bucket called other. `GUI-21`.
        items(loose, key = { "loose:" + it.id }) { Entry(it, chosen, 0, took) }
        // A category is keyed apart from the gear in it, in case one is named like an id.
        for (branch in branches) {
            item(key = "branch:" + branch.key) {
                BranchLine(
                    label = branch.label,
                    depth = 0,
                    open = branch.key !in closed,
                    chosen = kept.branch == branch.key,
                    onToggle = { toggle(branch.key) },
                    onClick = { choose(branch.key) },
                )
            }
            if (branch.key in closed) continue
            items(branch.held, key = { it.id }) { Entry(it, chosen, 1, took) }
            for (kind in branch.children) {
                item(key = "branch:" + kind.key) {
                    BranchLine(
                        label = kind.label,
                        depth = 1,
                        open = kind.key !in closed,
                        chosen = kept.branch == kind.key,
                        onToggle = { toggle(kind.key) },
                        onClick = { choose(kind.key) },
                    )
                }
                if (kind.key in closed) continue
                items(kind.held, key = { it.id }) { Entry(it, chosen, 2, took) }
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
    /** The regions alone, filling the width, which is a phone's first page. `PHONE-2`. */
    treeOnly: Boolean = false,
    onPlace: (Chosen) -> Unit,
    onChoose: (Chosen) -> Unit,
) {
    val open = kept.open ?: rootsOf(tree)
    val place = kept.place
    // The tree scrolls to whatever was chosen elsewhere: unfolding towards a branch a reader
    // cannot see would be answering *where is this* off the bottom of the list. `GUI-26`.
    LaunchedEffect(place?.id, open, tree) {
        val at = place?.id?.let { held -> pathTo(tree, held) }?.let { lineOf(tree, open, it) }
        if (at != null) kept.tree.scrollToItem(at)
    }
    val chosen = kept.chosen
    val hideUnused = kept.hideUnused
    Row(modifier = Modifier.fillMaxSize()) {
        Selectable {
            val width = if (treeOnly) Modifier.fillMaxWidth() else Modifier.width(TREE)
            Column(modifier = width.fillMaxHeight().padding(GAP)) {
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
                        onToggle = { key ->
                            kept.open = if (key in open) open - key else open + key
                        },
                        onChoose = { place ->
                            kept.unplaced = false
                            onPlace(place)
                        },
                        keyChosen = UNPLACED.takeIf { kept.unplaced },
                        onChooseKey = { key ->
                            if (key == UNPLACED) {
                                kept.unplaced = true
                                kept.place = null
                            }
                        },
                    )
                }
            }
        }
        if (treeOnly) return@Row
        VerticalDivider()
        val what = remember(set, place, kept.unplaced, hideUnused, LocalChanger.current.edition) {
            when {
                kept.unplaced -> atPlaceIn(set, UNPLACED, hideUnused)
                else -> place?.let { atPlaceIn(set, it.id, hideUnused) }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Selectable {
                LazyColumn(state = kept.list, modifier = Modifier.fillMaxSize().padding(GAP)) {
                    if (what == null) return@LazyColumn
                    item(key = "sites") { Label("Sites", 0) }
                    if (what.first.isEmpty()) item(key = "nosite") { Aside("No sites") }
                    items(what.first, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
                    item(key = "wrecks") { Label("Wrecks", 0) }
                    if (what.second.isEmpty()) item(key = "nowreck") { Aside("No wrecks") }
                    items(what.second, key = { it.id }) { Entry(it, chosen, 1, onChoose) }
                }
            }
        }
    }
}

/** What is at a place, sites then wrecks, as a phone lists it on the region's own page. */
@Composable
private fun PlaceEntries(
    what: Pair<List<Chosen>, List<Chosen>>,
    chosen: Chosen?,
    onChoose: (Chosen) -> Unit,
) {
    Selectable {
        Column(modifier = Modifier.fillMaxWidth()) {
            Label("Sites", 0)
            if (what.first.isEmpty()) Aside("No sites")
            for (site in what.first) Entry(site, chosen, 1, onChoose)
            Label("Wrecks", 0)
            if (what.second.isEmpty()) Aside("No wrecks")
            for (wreck in what.second) Entry(wreck, chosen, 1, onChoose)
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
    /** Which branch holding no item of its own is chosen, and what choosing one does. */
    keyChosen: String? = null,
    onChooseKey: (String) -> Unit = {},
) {
    for (branch in branches) {
        val key = path + "/" + branch.key
        item(key = key) {
            BranchLine(
                label = branch.label,
                depth = depth,
                open = if (branch.children.isEmpty()) null else key in open,
                chosen = branch.held.any { it.id == chosen?.id } ||
                    (branch.held.isEmpty() && branch.key == keyChosen),
                onToggle = { onToggle(key) },
                // A branch with no region of its own is still a place to stand: the one that
                // gathers the sites naming no region. `GUI-25`.
                onClick = {
                    branch.held.firstOrNull()?.let(onChoose) ?: onChooseKey(branch.key)
                },
            )
        }
        if (key in open) {
            branchesIn(
                branch.children, key, depth + 1, chosen, open, onToggle, onChoose,
                keyChosen, onChooseKey,
            )
        }
    }
}

/**
 * Unfolds the region tree as far as the region called [id], so following a link shows where it is.
 *
 * The tree keeps which branches are open, and setting the place left that alone: a link to a site
 * in Catalonia chose the region and left it folded four levels down, so the card on the right
 * changed and the tree beside it did not. `GUI-26`.
 *
 * **Hide unused is turned off where it would hide the answer.** A site nobody has dived is in no
 * region the filtered tree shows, and unfolding towards a branch that is not there would leave the
 * reader looking at the same tree again.
 */
private fun unfold(set: ItemSet, kept: Kept, id: String) {
    var tree = shownTreeOf(set, kept.hideUnused)
    var opening = openingTo(tree, id)
    if (opening == null && kept.hideUnused) {
        kept.hideUnused = false
        tree = shownTreeOf(set, false)
        opening = openingTo(tree, id)
    }
    kept.open = (kept.open ?: rootsOf(tree)) + (opening ?: return)
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
private fun ItemView(
    chosen: Chosen,
    onFollow: (String) -> Unit,
    /** The tab's, which says whether this card is turned over. `GUI-53`. */
    kept: Kept,
    /** Makes this person the user, offered on a person who is not. `GUI-51`. */
    onOwn: (() -> Outcome)? = null,
) {
    // The card scrolls its own fields under a title line that stays put; what follows the
    // card scrolls with the fields.
    ItemCard(
        chosen = chosen,
        onFollow = onFollow,
        scrolls = true,
        kept = kept,
        onOwn = onOwn,
    ) {
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
            if (stats.isEmpty()) Aside("No values")
            val columns = columns()
            for (pair in stats.chunked(columns)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GAP * 2),
                ) {
                    for (field in pair) {
                        Box(modifier = Modifier.weight(1f)) { Field(field, onFollow) }
                    }
                    repeat(columns - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    kept: Kept,
) {
    // A phone has no list beside this, so what is at the region is listed on it. `PHONE-2`.
    val compact = LocalCompact.current
    if (place == null && chosen == null && !(compact && kept.unplaced)) {
        Middle("choose a region on the left")
        return
    }
    val edition = LocalChanger.current.edition
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (place != null) {
            val dots = remember(set, place, hideUnused, edition) {
                dotsOf(atPlaceIn(set, place.id, hideUnused).first)
            }
            val frame = remember(set, place, edition) { frameOf(place.item, dots) }
            if (frame != null) RegionMap(atlas?.layerFor(frame), frame, dots, chosen?.id)
        }
        if (compact) {
            val at = place?.id ?: UNPLACED
            val what = remember(set, at, hideUnused, edition) { atPlaceIn(set, at, hideUnused) }
            PlaceEntries(what, chosen) { kept.chosen = it }
        }
        if (place != null) ItemCard(chosen = place, onFollow = onFollow, kept = kept)
        if (chosen != null) ItemCard(chosen = chosen, onFollow = onFollow, kept = kept)
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
 * A delete button, red under the pointer, which is the one thing on a card that cannot be undone.
 *
 * Red only while the pointer is on it. A button that is red all the time is a card with an alarm
 * on it, read a hundred times a day and meant once; one that reddens as it is reached for says
 * the same thing at the moment it matters. `GUI-35`.
 */
/**
 * The **+** on a card: another of what is being looked at, and a menu where the tab makes more
 * than one type.
 *
 * A tab holding two types cannot offer the second through *another of this*, a logbook with no
 * trip in it having no trip to press it on. `GUI-35`.
 */
@Composable
private fun Adder(makeable: List<ItemDescription>, onAdd: (ItemDescription) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box {
        Explained(makeSaid(makeable.first()).takeIf { makeable.size == 1 }) {
            IconButton(
                onClick = { if (makeable.size == 1) onAdd(makeable.first()) else choosing = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "add another")
            }
        }
        Menu(expanded = choosing, onDismissRequest = { choosing = false }) {
            for (type in makeable) {
                DropdownMenuItem(
                    text = { Text(makeSaid(type)) },
                    onClick = {
                        choosing = false
                        onAdd(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun Deleter(onDelete: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val over by interaction.collectIsHoveredAsState()
    IconButton(onClick = onDelete, enabled = enabled, interactionSource = interaction) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "delete",
            tint = if (over && enabled) {
                MaterialTheme.colorScheme.error
            } else {
                LocalContentColor.current
            },
        )
    }
}

/**
 * Add, edit and delete on the tab row, acting on what the tab has in front of the reader.
 *
 * Greyed rather than left out where they cannot be pressed, with the reason over them, so the row
 * keeps its shape from tab to tab. `GUI-53`.
 */
@Composable
private fun Buttons(ribbon: Ribbon, kept: Kept) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ribbon.addWhy == null && ribbon.makeable.isNotEmpty()) {
            Adder(ribbon.makeable) { type -> kept.making = type }
        } else {
            Explained(ribbon.addWhy) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Filled.Add, contentDescription = "add another")
                }
            }
        }
        val edited = ribbon.edited
        Explained(ribbon.editWhy ?: "Edit ${edited?.title.orEmpty()}") {
            IconButton(onClick = { kept.editing = edited?.id }, enabled = edited != null) {
                Icon(Icons.Filled.Edit, contentDescription = "edit")
            }
        }
        Explained(ribbon.deleteWhy ?: "Delete") {
            Deleter(onDelete = { kept.deleting = ribbon.deleted }, enabled = ribbon.deleted.isNotEmpty())
        }
    }
}

/**
 * What a delete asks before it happens, and what it warns of.
 *
 * Asked because it cannot be undone: there is no journal yet, `RECON-1`, so a deletion is the
 * one thing a reader cannot take back. Named where there is one and counted where there are
 * several, and what points at it is counted too, a reference to something deleted being left
 * dangling rather than hunted down. `GUI-35`.
 */
@Composable
private fun Confirm(
    asked: String,
    warned: String?,
    clearing: Boolean,
    onClearing: (Boolean) -> Unit,
    onNo: () -> Unit,
    onYes: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(asked) },
        text = warned?.let {
            {
                Column {
                    Text(it)
                    // Offered where there is something to clear, and off until it is ticked:
                    // rewriting other items is a second thing being asked for, not a tidier way
                    // of doing the first. `DATA-17`, `GUI-35`.
                    Row(
                        modifier = Modifier.padding(top = HALF).clickable { onClearing(!clearing) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = clearing, onCheckedChange = onClearing)
                        Text("Also remove references to it")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onYes) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancel") } },
    )
}

/**
 * One item on a card, turned over into the edit form by the pencil on the tab row, where the
 * card's title line carries Cancel and Save. `GUI-29`, `GUI-53`.
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
    /** The tab's, whose `editing` says whether this card is turned over. `GUI-53`. */
    kept: Kept,
    /** Makes this person the user. Absent on everything that is not a person, and on the user. */
    onOwn: (() -> Outcome)? = null,
    after: @Composable ColumnScope.() -> Unit = {},
) {
    val changer = LocalChanger.current
    val edition = changer.edition
    val editing = kept.editing == chosen.id
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
                            kept.editing = null
                            refused = null
                            draft.clear()
                        },
                        onSave = {
                            when (val outcome = changer.change(draft.writes())) {
                                is Outcome.Done -> {
                                    kept.editing = null
                                    refused = null
                                    draft.clear()
                                }
                                is Outcome.Refused -> refused = outcome.reason
                            }
                        },
                    )
                } else {
                    // The glyph the list marks the user with, so pressing it puts the mark here.
                    onOwn?.let { own ->
                        Explained("This is me") {
                            // A refusal is said where Save says its own, above the fields.
                            IconButton(onClick = { refused = (own() as? Outcome.Refused)?.reason }) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = "this is me")
                            }
                        }
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
 * Last come the fields at the foot, each in a box as wide as the card and left out where it holds
 * nothing.
 *
 * Insets nest, since an owned item may own one: a recording's tolerances sit inside it. `GUI-16`.
 */
@Composable
private fun Fields(item: Item, onFollow: (String) -> Unit) {
    val arranged = remember(item.description) { arrangedOf(item.description) }
    val shown = shownAllOf(arranged.plain, item)
    val insets = arranged.insets.filter { item.read(it.name) is Result.Usable }
    val foot = arranged.foot.mapNotNull { shownOf(it, item) }
    val sections = arranged.sections.map { it.section to shownAllOf(it.fields, item) }
        .filter { (_, shown) -> shown.isNotEmpty() }
    if (shown.isEmpty() && insets.isEmpty() && foot.isEmpty() && sections.isEmpty()) {
        Aside("No values")
        return
    }
    Flowing(shown, onFollow)
    for ((section, held) in sections) {
        when (section.layout) {
            Layout.BOX -> Inset(section.label) { Flowing(held, onFollow) }
            Layout.FLOW -> {
                Caption(section.label)
                Flowing(held, onFollow)
            }
        }
    }
    val opener = LocalPlanOpener.current
    val dive = diveIdOf(item)
    val profiles = item.description[PROFILES] as? OwnedItemDescription
    if (dive != null && opener != null && profiles != null && profiles !in insets) {
        Inset(profiles.label) { AddPlan { opener(Bound.Adding(dive)) } }
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
    for (footing in foot) Inset(footing.label) { Said(footing, onFollow) }
}

/** Fields in the flow of a card, two to a row, a wide one taking its own. */
@Composable
private fun Flowing(shown: List<Shown>, onFollow: (String) -> Unit) {
    val columns = columns()
    for (row in rowsOf(shown, columns) { it.wide }) {
        val wide = row.size == 1 && row.first().wide
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in row) Box(modifier = Modifier.weight(1f)) { Field(field, onFollow) }
            // A wide field spans the columns; a last row one short keeps its place in them.
            if (!wide) repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** A keyed owned item as an inset with a tab per entry, the first open. */
@Composable
private fun KeyedInset(inset: OwnedItemDescription, item: Item, onFollow: (String) -> Unit) {
    val entries = shownEntriesOf(item, inset.name)
    if (entries.isEmpty()) return
    var open by remember(item, inset.name) { mutableStateOf(0) }
    val at = open.coerceIn(0, entries.size - 1)
    val opener = LocalPlanOpener.current
    val dive = if (inset.name == PROFILES) diveIdOf(item) else null
    val tabs: @Composable RowScope.() -> Unit = {
        SmallTabs(
            labels = entries.map { (key, entry) -> entryLabelOf(key, entry) },
            chosen = at,
            marked = pointedEntryOf(item, inset.name),
            onChoose = { open = it },
        )
        if (dive != null && opener != null) AddPlan { opener(Bound.Adding(dive)) }
    }
    Inset(inset.label, beside = tabs) {
        Spacer(modifier = Modifier.height(HALF))
        // A recording is drawn before it is read: the graph is what it is for.
        val entry = entries[at].second
        val drawn = depthLinesOf(entry).isNotEmpty()
        // What the model says is worked out once a run or an edit, never once a repaint: it walks
        // the whole profile and searches at every sample. `LOGIC-37`.
        val evaluated = if (!drawn) null else {
            val edition = LocalChanger.current.edition
            remember(entry, edition) { evaluate(entry) }
        }
        // A dive's profiles are drawn to one scale, so a shorter or shallower one looks it.
        val span = if (dive != null && entries.size > 1) {
            val edition = LocalChanger.current.edition
            remember(item, edition) { reachOf(item, entries.map { it.second }) }
        } else {
            null
        }
        if (drawn) ProfileGraph(item, entry, evaluated as? Evaluated.Done, span)
        Fields(entry, onFollow)
        if (evaluated != null) WorkedOut(item, entry, evaluated, onFollow)
        if (dive != null && opener != null && isPlanned(entry)) {
            val key = entries[at].first
            TextButton(onClick = { opener(Bound.Editing(dive, key)) }) { Text("Edit plan") }
        }
    }
}

/**
 * The deed that adds a plan to a dive, beside its profiles: it opens the planner in the
 * Calculations tab, bound to the dive. `GUI-44`.
 */
@Composable
private fun AddPlan(onAdd: () -> Unit) {
    TextButton(onClick = onAdd) { Text("Add plan") }
}

/** The id of [item] where it is a dive, which is the only thing a plan can be added to. */
private fun diveIdOf(item: Item): String? =
    (item as? ReferenceableItem)?.takeIf { it.description == Types.DIVE }?.let { item.set.idOf(it) }

/** What a dive's profiles are called, and the one keyed inset a plan can be added to. */
internal const val PROFILES: String = "profiles"

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
        Said(shown, onFollow, Modifier.weight(1f))
    }
}

/** What a field says, without what it is called. */
@Composable
private fun Said(shown: Shown, onFollow: (String) -> Unit, modifier: Modifier = Modifier) {
    val rating = shown.rating
    if (rating != null) {
        Stars(rating)
        return
    }
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
    Text(
        text = said,
        style = styleOf(shown, MaterialTheme.typography.bodyMedium),
        modifier = modifier,
    )
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

// --- What the model makes of a run. `GUI-40`.

/**
 * What the model says about a run, under its fields: the figures it works out, what it objects to,
 * and on a plan the way out of it.
 *
 * **A recording it cannot answer for says nothing**, since most cannot: a computer that wrote no
 * gradient factors leaves nothing to work from, and a red line under every dive in the logbook
 * would say only that the model was not asked. A plan says why, because a plan exists to be
 * answered and one that cannot be is a plan with something missing.
 */
@Composable
private fun WorkedOut(dive: Item, profile: Item, evaluated: Evaluated, onFollow: (String) -> Unit) {
    val planned = isPlanned(profile)
    when (evaluated) {
        is Evaluated.Refused -> refusedSaidOf(evaluated, planned)?.let { Field(it, onFollow) }
        is Evaluated.Done -> {
            for (figure in workedFiguresOf(dive, profile, evaluated)) Field(figure, onFollow)
            for (finding in findingsSaidOf(evaluated)) Field(finding, onFollow)
        }
    }
}

/** How fast a plan descends where there is no logbook to ask. */
internal val FALLBACK_DESCENT_RATE: Double = Settings.DEFAULT_DESCENT_RATE.default!!


// --- The graph of a recording. `GUI-4`.

/**
 * A recording drawn: depth up the left, and up the right one other thing it wrote, chosen from
 * a box of what it wrote.
 */
@Composable
private fun ProfileGraph(dive: Item, profile: Item, evaluated: Evaluated.Done?, span: Reach? = null) {
    // The ceiling is a depth, so it goes on the depth axis: the gap between it and the line a
    // diver swam is what a reader is looking at. `GUI-40`.
    val depth = remember(profile, evaluated) {
        depthLinesOf(profile) + listOfNotNull(evaluated?.let { ceilingLineOf(it) })
    }
    val planned = isPlanned(profile)
    val overlays = remember(dive, profile, evaluated) {
        overlaysOf(dive, profile) +
            evaluated?.let { workedOverlaysOf(dive, profile, it) }.orEmpty()
    }
    val events = remember(dive, profile) { eventsOf(dive, profile) }
    Graphed(depth, overlays, events, planned, chosenFor = profile, span = span)
}

/**
 * A depth graph with a right axis chosen from [overlays], as a recording is drawn and as a plan
 * worked out in the calculations is.
 *
 * The right axis goes back to the first of [overlays] whenever [chosenFor] changes, so a reader
 * moving to another dive meets that dive's graph as it opens rather than as the last one was left.
 */
@Composable
internal fun Graphed(
    depth: List<Line>,
    overlays: List<Overlay>,
    events: List<Event>,
    planned: Boolean,
    chosenFor: Any?,
    /** How far other graphs set beside this one reach, so that all are drawn to one scale. */
    span: Reach? = null,
) {
    var picked by remember(chosenFor) { mutableStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    val overlay = overlays.getOrNull(picked.coerceIn(0, maxOf(overlays.size - 1, 0)))
    Box(modifier = Modifier.fillMaxWidth()) {
        Chart(depth, overlay, events, planned, span)
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
                Menu(expanded = picking, onDismissRequest = { picking = false }) {
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
private fun Chart(
    depth: List<Line>,
    overlay: Overlay?,
    events: List<Event>,
    /** Whether the recording is a plan, which is drawn as a dashed line: it has not happened. */
    planned: Boolean = false,
    /** How far to draw the axes at least, where this graph is one of several read side by side. */
    span: Reach? = null,
) {
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
            val lastMinute = maxOf(all.maxOfOrNull { it.minute } ?: 0.0, span?.minutes ?: 0.0, 1.0)
            val deepest = maxOf(depth.firstOrNull { it.main }?.points?.maxOfOrNull { it.value } ?: 1.0, span?.deepest ?: 0.0)
            val depthHigh = maxOf(deepest * (1.0 + AXIS_ROOM), 1.0)
            fun x(minute: Double): Float = (left + (right - left) * (minute / lastMinute)).toFloat()
            fun yDepth(value: Double): Float =
                (top + (bottom - top) * (value / depthHigh)).toFloat()
            val overPoints = overlay?.lines?.flatMap { it.points }.orEmpty()
            val over = rangeOf(overPoints.map { it.value } + overlay?.let { span?.readings?.get(it.title) }.orEmpty())
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
            val dashes = LINE_WIDTH.toPx() * 3
            val thick = if (!planned) Stroke(LINE_WIDTH.toPx()) else Stroke(
                width = LINE_WIDTH.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashes, dashes)),
            )
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
internal fun shortOf(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

// --- What every screen shares.

/**
 * An item being made: an empty form, and no item until it is saved.
 *
 * **Nothing is added until Save.** An id is minted once, from what the item says at the moment
 * it is made, and nothing renames it afterwards — so making the item first and typing into it
 * second would leave every item made from this window called `unknown_person`. The form is
 * therefore over an item nobody holds, and Save hands the fields to `Change.Add`, which mints
 * the id from them. Cancel leaves no trace. `GUI-35`.
 */
@Composable
private fun NewCard(
    type: ItemDescription,
    set: ItemSet,
    started: Map<String, String> = emptyMap(),
    onCancel: () -> Unit,
    onMade: (String) -> Unit,
) {
    val changer = LocalChanger.current
    val draft = remember(type) { Draft() }
    val item = remember(type) {
        ItemReader.read(type, Stored.Members(emptyMap()), set, Units.DEFAULT)
    }
    // What the tree already said, typed into the form rather than written behind it: a reader
    // sees it, and changes it where the branch was not what they meant. `GUI-35`.
    remember(type, started) {
        for ((field, value) in started) draft.put(item, field, value)
        started
    }
    var refused by remember(type) { mutableStateOf<String?>(null) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(GAP * 2)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New " + labelOf(type).lowercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                EditActions(
                    draft = draft,
                    onCancel = onCancel,
                    onSave = {
                        val made = changer.change(
                            listOf(Change.Add(type, draft.fieldsOf(item))),
                        )
                        when (made) {
                            is Outcome.Done -> made.added.firstOrNull()?.let(onMade)
                            is Outcome.Refused -> refused = made.reason
                        }
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = GAP))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {
                refused?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = GAP),
                    )
                }
                EditFields(item, draft)
            }
        }
    }
}

/** Nothing chosen, and an offer to make the first of what this tab holds. `GUI-35`. */
@Composable
private fun Empty(said: String, onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Select an item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = HALF)) { Text(said) }
        }
    }
}

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

/** What a tab's icon is drawn at, which a spinner in its place matches. */
private val TAB_ICON = 24.dp
private val TABLE = 600.dp
private val TREE = 220.dp
private val TRIP = 80.dp
private val NUMBER = 52.dp
private val DATE = 100.dp
private val SITE = 330.dp
/** How wide the column a field's name sits in is, which a review lines its own up with. */
internal val LABEL = 130.dp
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

/** What the agent button is called, said over it since it shows only an icon. `GUI-38`. */
private const val ASK_AN_AGENT = "Ask an agent"

/** What the same button does while the panel is open, which is shut it. */
private const val CLOSE_THE_AGENT = "Close the agent panel"

/** What a download says where the phone was not allowed to use Bluetooth. `AND-2`. */
internal const val REFUSED_BLUETOOTH: String =
    "Yemoja was not allowed to use Bluetooth, which reading a dive computer needs. It can be " +
        "allowed in the phone's settings, under the app's permissions."
