package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yemoja.data.ItemSet
import yemoja.logic.Import
import yemoja.logic.Outcome
import yemoja.logic.Universe

/*
 * What a download or an import brought, reviewed dive by dive and taken in together.
 *
 * See ../../../../../../gui/doc.md — `GUI-31` and `GUI-33`. A decision here is a choice held
 * until *Apply*, not a button that acts: every row keeps its shape whatever is chosen, and any
 * choice can be changed until the moment the whole review is applied.
 */

/** What a reader decides about one arriving dive. */
internal enum class Decision(val label: String) {

    /** Put it together with the dive already held that it appears to be. */
    MERGE("Merge"),

    /** Take it in as a dive of its own. */
    IMPORT("Import"),

    /** Leave it staged, to be found again. */
    SKIP("Skip"),
}

/** Placing is where an arriving dive that came with a fix is said to have been. */
internal sealed class Placing {

    /** A site already held, by its id. */
    data class At(val site: String) : Placing()

    /** No site at all: the fix is dropped. */
    object Nowhere : Placing()

    /** A new site, called whatever is typed beside the choice. */
    object Named : Placing()
}

/**
 * Reviewing is what a reader has decided so far about the dives of one import, and nothing else.
 *
 * **Nothing here reaches the import until [applied].** The model's own answers — `answered`,
 * `namedAs`, `takeIn` — each land the moment they are called and cannot be taken back, which is
 * why the review holds its own copy of every choice: a dive marked *Skip* by mistake is a click
 * away from *Import* again, right up to *Apply*.
 *
 * Not immutable: the review writes into it as choices are made.
 */
internal class Reviewing {
    /** Each dive's decision, by its id; absent where the reader has not touched it. */
    val decisions: SnapshotStateMap<String, Decision> = mutableStateMapOf()

    /** Where each dive with a fix was, by its id; absent where the reader has not said. */
    val placings: SnapshotStateMap<String, Placing> = mutableStateMapOf()

    /** The name typed for a new site, by dive id. */
    val names: SnapshotStateMap<String, String> = mutableStateMapOf()

    /** The dive *Apply* stopped at, and why, or absent where nothing was refused. */
    var refusedAt: String? by mutableStateOf(null)
    var refusal: String? by mutableStateOf(null)
}

/**
 * What is decided about [dive], said or not.
 *
 * **Merge where it appears to be a dive already held, otherwise import.** That is what *Import
 * all* did for every row, and a review a reader applies without touching is that review.
 */
internal fun decisionOf(dive: Arriving, reviewing: Reviewing): Decision =
    reviewing.decisions[dive.id] ?: if (dive.onto != null) Decision.MERGE else Decision.IMPORT

/**
 * Where [dive] is said to have been, or nowhere where nobody has said.
 *
 * Nowhere rather than the nearest site: a site metres away is the likeliest answer and still a
 * guess, and `LOGIC-18` refuses to make one on a reader's behalf.
 */
internal fun placingOf(dive: Arriving, reviewing: Reviewing): Placing =
    reviewing.placings[dive.id] ?: Placing.Nowhere

/**
 * Applies every decision in [reviewing] to what is waiting in [import], oldest dive first.
 *
 * Each dive is placed where it was said to be and then taken in as decided, or left where it is.
 * **Taking in stops at the first refusal** and says which dive it stopped at, so the rows after
 * it are still there to be looked at: what is in the folder is what has not been decided,
 * `RECON-1`. [told] hears how many dives of how many have been gone through.
 */
internal fun applied(
    import: Import,
    into: ItemSet,
    reviewing: Reviewing,
    told: (done: Int, of: Int) -> Unit = { _, _ -> },
): Taken {
    reviewing.refusedAt = null
    reviewing.refusal = null
    var many = 0
    val ids = arrivingIn(import, into, nextNumberIn(into)).map { it.id }
    for ((at, id) in ids.withIndex()) {
        told(at, ids.size)
        // Read again once the dives before it have landed: the number it would take and the dive
        // it appears to be both depend on what the logbook holds by now.
        val dive = arrivingIn(import, into, nextNumberIn(into), only = id).firstOrNull() ?: continue
        val decision = decisionOf(dive, reviewing)
        if (decision == Decision.SKIP) continue
        val placed = if (dive.fix == null) Outcome.Done() else placed(import, dive, reviewing)
        val done = when (placed) {
            is Outcome.Refused -> placed
            is Outcome.Done -> takeIn(import, dive, dive.onto.takeIf { decision == Decision.MERGE })
        }
        when (done) {
            is Outcome.Refused -> {
                reviewing.refusedAt = dive.id
                reviewing.refusal = done.reason
                return Taken(many, done.reason)
            }
            is Outcome.Done -> many++
        }
    }
    return Taken(many, null)
}

/** Says where [dive] was, as the review has it, and refuses a new site with no name. */
private fun placed(import: Import, dive: Arriving, reviewing: Reviewing): Outcome =
    when (val placing = placingOf(dive, reviewing)) {
        is Placing.At -> answered(import, dive, placing.site)
        Placing.Nowhere -> answered(import, dive, null)
        Placing.Named -> {
            val name = reviewing.names[dive.id].orEmpty().trim()
            if (name.isEmpty()) {
                Outcome.Refused("name the new site, or choose one already held")
            } else {
                namedAs(import, dive, name)
            }
        }
    }

/**
 * What a download or an import brought, a dive to a box, each with what it appears to be and a
 * choice of what to do with it, and one button that does all of it.
 *
 * **A row never changes shape.** The three choices are there whether or not one is chosen, the
 * question of where the dive was is a chooser that stays put once answered, and nothing lands
 * until *Apply*. A row that grew a button when its site was picked and lost one when it was
 * taken in made the list jump under the pointer. `GUI-31`.
 */
@Composable
internal fun Arrived(
    universe: Universe,
    changer: Changer,
    after: (Taken) -> Unit,
    leave: () -> Unit,
) {
    val import = universe.importing ?: return
    val reviewing = remember(import) { Reviewing() }
    // Off the screen's thread, both: comparing a few hundred dives with a logbook takes seconds
    // on a phone, and a phone stops an app whose screen waits that long.
    val arriving by produceState<List<Arriving>?>(null, import, changer.edition) {
        value = withContext(Dispatchers.Default) {
            arrivingIn(import, universe.logbook, nextNumberIn(universe.logbook))
        }
    }
    var applying by remember(import) { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    val shown = arriving
    if (shown == null) {
        Busy("Comparing with your logbook", null)
        return
    }
    applying?.let { (done, of) ->
        Busy("Taking in", done to of)
        return
    }
    val numbers = numbersOf(shown, reviewing, nextNumberIn(universe.logbook))
    // A few hundred rows drawn at once hold a phone's screen for seconds, so they come a batch a
    // frame, and the screen answers between.
    var drawn by remember(shown) { mutableStateOf(minOf(ROWS_AT_ONCE, shown.size)) }
    LaunchedEffect(shown) {
        while (drawn < shown.size) {
            withFrameNanos {}
            drawn = minOf(drawn + ROWS_AT_ONCE, shown.size)
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        for (dive in shown.take(drawn)) Arrival(dive, numbers.getValue(dive.id), reviewing)
        if (drawn < shown.size) {
            Busy("Listing", drawn to shown.size)
            return@Column
        }
        Row(
            modifier = Modifier.padding(top = GAP),
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    applying = 0 to shown.size
                    scope.launch {
                        // What follows the dives, the rest of what arrived, is taken in there too.
                        withContext(Dispatchers.Default) {
                            after(applied(import, universe.logbook, reviewing) { done, of -> applying = done to of })
                        }
                        applying = null
                        changer.changed()
                    }
                },
            ) { Text("Apply") }
            TextButton(onClick = leave) { Text("Close") }
            Aside(appliedSaid(shown, reviewing))
        }
    }
}

/** How many rows of a review are drawn in one frame. */
private const val ROWS_AT_ONCE = 20

/** A long piece of work under way: what it is, and how many of how many where that is known. */
@Composable
private fun Busy(said: String, counted: Pair<Int, Int>?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        if (counted == null || counted.second <= 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = HALF))
            Aside("$said…")
        } else {
            LinearProgressIndicator(
                progress = { (counted.first.toFloat() / counted.second).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            )
            Aside("$said: ${counted.first} of ${counted.second} dives")
        }
    }
}

/**
 * The number each dive would take as a dive of its own, by id, given what is decided above it.
 *
 * Counted from [next] and advanced only by a dive decided *Import*: a merged dive keeps the
 * number of the dive it joins, and a skipped one takes none. So every row says what pressing
 * *Import* on it would give, whatever the rows above it are set to. A merged row and the row
 * under it therefore say the same number, and only one of them will take it; switch the merge
 * off and the rows below move down as you watch. The model's own numbering, read once, gave two
 * landed dives one number the moment a merge was declined.
 */
internal fun numbersOf(arriving: List<Arriving>, reviewing: Reviewing, next: Int): Map<String, Int> {
    var number = next
    val out = LinkedHashMap<String, Int>()
    for (dive in arriving) {
        out[dive.id] = number
        if (decisionOf(dive, reviewing) == Decision.IMPORT) number++
    }
    return out
}

/** What *Apply* would do, counted, so a reader can check the review before pressing it. */
internal fun appliedSaid(arriving: List<Arriving>, reviewing: Reviewing): String {
    val counted = arriving.groupingBy { decisionOf(it, reviewing) }.eachCount()
    val parts = listOfNotNull(
        counted[Decision.MERGE]?.let { "$it merged" },
        counted[Decision.IMPORT]?.let { "$it imported" },
        counted[Decision.SKIP]?.let { "$it left staged" },
    )
    return if (parts.isEmpty()) "Nothing is waiting." else parts.joinToString(", ") + "."
}

/** One arriving dive: what it is, what is known about it, and the choices made about it. */
@Composable
private fun Arrival(dive: Arriving, number: Int, reviewing: Reviewing) {
    val decision = decisionOf(dive, reviewing)
    val refused = reviewing.refusedAt == dive.id
    Inset(dive.said) {
        if (dive.glued > 1) Aside("Pieced together from ${dive.glued} recordings on the computer.")
        dive.ontoSaid?.let { Aside("This looks like $it, which is in your logbook already.") }
        if (dive.held) Aside("This dive is in your logbook already, and taking it in updates it.")
        Choices(dive, number, decision) { reviewing.decisions[dive.id] = it }
        if (dive.fix != null) Where(dive, reviewing)
        if (refused) reviewing.refusal?.let { Warning(it, wrong = true) }
    }
}

/**
 * The three things that can be done with a dive, one of them chosen, all of them always there.
 *
 * *Merge* is greyed rather than left out where the dive appears to be nobody's: the row keeps its
 * shape, and a reader learns what the greyed word would have meant from the rows where it is not.
 */
@Composable
private fun Choices(dive: Arriving, number: Int, chosen: Decision, onChoose: (Decision) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Choice(
            label = "Merge",
            chosen = chosen == Decision.MERGE,
            enabled = dive.onto != null,
        ) { onChoose(Decision.MERGE) }
        Choice(
            label = "Import as dive $number",
            chosen = chosen == Decision.IMPORT,
        ) { onChoose(Decision.IMPORT) }
        Choice(label = "Skip", chosen = chosen == Decision.SKIP) { onChoose(Decision.SKIP) }
    }
}

/** One of a row of choices: a dot and a word, the dot filled where it is the one chosen. */
@Composable
private fun Choice(label: String, chosen: Boolean, enabled: Boolean = true, onChoose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            RadioButton(selected = chosen, onClick = onChoose, enabled = enabled)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = HALF),
        )
    }
}

/**
 * Where an arriving dive was made, asked of the reader and held until *Apply*.
 *
 * The device said where it was and that is all it said. A fix is not a site: a site has a name,
 * a water type and its regions, and none of that is in a pair of coordinates. So the nearest
 * sites already held are offered with their distances, a new one can be named, and neither is
 * forced. `LOGIC-18`, `GUI-31`.
 *
 * **One chooser and one box, both always there.** The box is for a new site's name and is read
 * only where *New site* is chosen; typing in it chooses that, since a name typed is an answer.
 */
@Composable
private fun Where(dive: Arriving, reviewing: Reviewing) {
    val placing = placingOf(dive, reviewing)
    val options = dive.nearby.map { "${it.name}, ${apartOf(it.metres)}" } + NO_SITE + NEW_SITE
    val chosen = when (placing) {
        is Placing.At -> dive.nearby.indexOfFirst { it.id == placing.site }.takeIf { it >= 0 } ?: 0
        Placing.Nowhere -> dive.nearby.size
        Placing.Named -> dive.nearby.size + 1
    }
    Aside("The computer recorded this dive at ${dive.fix}. Which site was it?")
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GAP),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(SITE)) {
            Pick(chosen = options[chosen], options = options) { at ->
                reviewing.placings[dive.id] = when {
                    at < dive.nearby.size -> Placing.At(dive.nearby[at].id)
                    at == dive.nearby.size -> Placing.Nowhere
                    else -> Placing.Named
                }
            }
        }
        Box(modifier = Modifier.width(SITE)) {
            Compact(
                value = reviewing.names[dive.id].orEmpty(),
                onChange = {
                    reviewing.names[dive.id] = it
                    if (it.isNotBlank()) reviewing.placings[dive.id] = Placing.Named
                },
                hint = "Name for a new site",
            )
        }
    }
}

/** The answer that drops the fix. */
private const val NO_SITE = "No site"

/** The answer that makes a site of the fix, called what the box beside it says. */
private const val NEW_SITE = "New site"

/** How wide the site chooser and the name box are, which is the width of a long site name. */
private val SITE = 240.dp
