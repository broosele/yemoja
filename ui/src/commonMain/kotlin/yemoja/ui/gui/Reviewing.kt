package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import yemoja.data.ItemSet
import yemoja.logic.Applied
import yemoja.logic.Changed
import yemoja.logic.Staged
import yemoja.logic.Universe

/*
 * Looking at what an agent would change before any of it happens.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`, and `RECON-8` in
 * ../../../../../../../logic/reconciliation.md for what a staging is.
 */

/**
 * What an agent has staged, and the deeds that take it or drop it.
 *
 * In the home screen's System box, where an import's review already sits: a reader reviews a
 * pending change in one place whatever proposed it, and a table of before and after wants the
 * window's width rather than the panel's column. `GUI-38`.
 */
@Composable
internal fun Review(universe: Universe?, changer: Changer, onApplied: (String) -> Unit = {}) {
    val staging = universe?.staging ?: return
    // What the last apply came to. Said whether or not anything is still staged: an apply that
    // left every row alone changes nothing on the screen, and pressing a deed that visibly does
    // nothing is the one outcome a reader cannot tell from a fault.
    var said by remember(staging) { mutableStateOf<String?>(null) }
    // What is staged is read afresh whenever the logbook changes and whenever the staging does,
    // since an agent goes on staging while somebody reads. The edition is what says the second.
    // `RECON-8`.
    val shown = staging.edition
    val staged = remember(staging, changer.edition, shown) { staging.staged }
    if (staged.isEmpty()) {
        said?.let { Aside(it) }
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        said?.let { Aside(it) }
        Aside(stagedSaidOf(staged))
        for (item in staged) {
            Proposal(item, universe.logbook) {
                staging.drop(item.id)
                said = null
                changer.changed()
            }
        }
        Row(
            modifier = Modifier.padding(top = HALF),
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Button(
                onClick = {
                    // Only what this list was drawn from: an agent may have staged something
                    // since, and applying that would be applying what nobody was shown.
                    val done = appliedSaidOf(staging.apply(shown))
                    said = done
                    onApplied(done)
                    changer.changed()
                },
            ) { Text("Apply all") }
            TextButton(
                onClick = {
                    staging.clear()
                    said = null
                    changer.changed()
                },
            ) { Text("Discard all") }
        }
    }
}

/** One item a staging would change: what it is, what would happen to it, and every field of it. */
@Composable
private fun Proposal(staged: Staged, set: ItemSet, onDrop: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = proposalSaidOf(staged, set),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDrop) { Text("Discard") }
        }
        for (field in staged.fields) Line(field, staged.kind)
    }
}

/** One field: what it is called, what would become of it, and whether it has moved since. */
@Composable
private fun Line(field: Changed, kind: Staged.Kind) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = fieldSaidOf(field.at),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        // One run of text, so that a value that moved is said beside the change it spoils rather
        // than at the far edge of the window.
        val warning = MaterialTheme.colorScheme.error
        Text(
            text = buildAnnotatedString {
                append(changeSaidOf(field, kind))
                movedSaidOf(field)?.let { moved ->
                    append("   ")
                    withStyle(SpanStyle(color = warning)) { append(moved) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * What a staging comes to in a line, by what it would do rather than by how many items.
 *
 * Adding, changing and deleting are three different things to agree to, so they are counted apart:
 * a reader who sees *1 item to delete* among nine edits has been told the thing they most need to
 * know before pressing anything.
 */
internal fun stagedSaidOf(staged: List<Staged>): String {
    if (staged.isEmpty()) return "An agent has staged nothing."
    val counts = Staged.Kind.entries.mapNotNull { kind ->
        val many = staged.count { it.kind == kind }
        if (many == 0) null else "${counted(many, "item")} ${kindSaidOf(kind)}"
    }
    val fields = staged.sumOf { it.fields.size }
    return "An agent has staged ${joinedSaying(counts)}, ${counted(fields, "field")} in all."
}

/** What a staging would do to one item, as it reads after a count of them. */
internal fun kindSaidOf(kind: Staged.Kind): String = when (kind) {
    Staged.Kind.ADD -> "to add"
    Staged.Kind.EDIT -> "to change"
    Staged.Kind.DELETE -> "to delete"
}

/**
 * What one staged item is called, and what would become of it.
 *
 * An item being added has no name until it lands, so it is called by its type: an id is never
 * shown, and the one it is staged under is not the one it will have. An item already in the
 * logbook is called what the logbook calls it, and one deleted since it was staged says so.
 */
internal fun proposalSaidOf(staged: Staged, set: ItemSet): String {
    val held = set[staged.id]
    val type = prettyOf(staged.type)
    val name = when {
        staged.kind == Staged.Kind.ADD -> "a new ${type.lowercase()}"
        held == null -> "a ${type.lowercase()} no longer in the logbook"
        else -> "$type ${titleOf(held)}"
    }
    return "${kindSaidOf(staged.kind).replaceFirstChar { it.uppercase() }} $name"
}

/**
 * A field path as a reader reads it: `environment.visibility` is *Environment · Visibility*.
 *
 * The path's own words rather than the field's label, because a path reaches inside owned items
 * and through the keys of a collection, and what a reader needs is where in the item it sits.
 */
internal fun fieldSaidOf(at: String): String =
    at.split('.').joinToString(" · ") { prettyOf(it) }

/**
 * What would become of one field: what it says now, and what it would say.
 *
 * An item being added says only what it would hold, and one being deleted only what it holds, in
 * both cases because the other half is the whole item rather than anything about this field.
 */
internal fun changeSaidOf(field: Changed, kind: Staged.Kind): String = when (kind) {
    Staged.Kind.ADD -> field.to ?: NOTHING
    Staged.Kind.DELETE -> field.from ?: NOTHING
    Staged.Kind.EDIT -> "${field.from ?: NOTHING} → ${field.to ?: NOTHING}"
}

/**
 * That the logbook has moved under a field since it was staged, or absent where it has not.
 *
 * Said before anything is pressed rather than reported afterwards: a change decided about a value
 * nobody holds any more is one to look at again, and applying will leave it alone. `RECON-8`.
 */
internal fun movedSaidOf(field: Changed): String? =
    if (!field.moved) null else "it is ${field.now ?: NOTHING} now"

/**
 * What applying came to: what landed, and every field left alone.
 *
 * Each refusal carries the reason the model phrased, which already says what the value was and
 * what it is, so nothing is put into other words here.
 */
internal fun appliedSaidOf(applied: Applied): String {
    val landed = if (applied.items == 0) {
        "Nothing was applied."
    } else {
        "Applied ${counted(applied.items, "item")}, ${counted(applied.fields, "field")}."
    }
    if (applied.refused.isEmpty()) return landed
    val left = applied.refused.joinToString(" ") { refusal ->
        val where = if (refusal.at.isEmpty()) "" else "${fieldSaidOf(refusal.at)}: "
        "$where${refusal.reason}."
    }
    return "$landed ${counted(applied.refused.size, "change")} left alone — $left"
}

/** A list as a sentence says one: commas between, and *and* before the last. */
internal fun joinedSaying(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts.single()
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

/**
 * Told is something the window has to say once, carried from wherever it happened.
 *
 * Its identity is what makes it one saying rather than another, so the same words said twice are
 * said twice. What a review came to reaches the agent panel this way, where it is a turn of the
 * window's own in the conversation. Shown to the reader, and never put to the agent, which learns
 * that the logbook moved from the revision on its next reply. `GUI-38`, `API-4`.
 *
 * Immutable.
 */
internal class Told(val said: String)

/** How much is staged, as the panel says it beside the deed that opens the review. */
internal fun stagedLineOf(many: Int): String =
    "${counted(many, "item")} staged, waiting to be looked at."

/** A field holding nothing, which reads as a word rather than as a blank. */
private const val NOTHING = "nothing"
