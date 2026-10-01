package yemoja.ui.gui

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yemoja.data.OwnedItemDescription
import yemoja.data.Cardinality
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.KeyReferenceDescription
import yemoja.data.Layout
import yemoja.data.OwnedItem
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.logic.Change
import yemoja.logic.Outcome
import yemoja.logic.Types

/*
 * The edit form: the item view turned over, every field laid out to be filled in.
 *
 * The same layout as the item view, the same fields in the same places, so a reader who knows
 * where a field sits when reading knows where it sits when editing. Save hands the whole draft
 * to the model as one change; a refusal comes back to the field it was about.
 *
 * See ../../../../../../gui/doc.md — `GUI-29`.
 */

/**
 * Cancel and Save, for the title line of a card being edited, which stays put while the fields
 * scroll under it. Save is offered only once something has been changed.
 */
@Composable
internal fun EditActions(draft: Draft, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Button(onClick = onSave, enabled = !draft.isEmpty) { Text("Save") }
    }
}

/**
 * The fields of [item] as editors, laid out as the item view lays them out.
 *
 * [draft] is the form's, kept by whoever shows the form, so that a change landing while the
 * form is open — an entry taken out of a collection — redraws the form without emptying it.
 */
@Composable
internal fun EditFields(item: Item, draft: Draft) {
    val arranged = remember(item.description) { arrangedOf(item.description, editing = true) }
    // What the form says rather than what the item holds: a category typed a moment ago decides
    // where the fields that follow from it sit, without waiting for Save. `GUI-29`.
    val saying = draft.shownOf(item, "category") as? String
    fun offered(fields: List<FieldDescription>): List<FieldDescription> =
        fields.filter { forwardOf(it, item, saying) && !it.housekeeping }
    Rows(offered(arranged.plain), item, draft)
    for (sectioned in arranged.sections) {
        val fields = offered(sectioned.fields)
        if (fields.isEmpty()) continue
        when (sectioned.section.layout) {
            Layout.BOX -> Inset(sectioned.section.label) { Rows(fields, item, draft) }
            Layout.FLOW -> {
                Caption(sectioned.section.label)
                Rows(fields, item, draft)
            }
        }
    }
    // The fields this kind of item has no use for, and the ones it keeps for the machinery,
    // behind one fold: both are reachable and neither is in the way. `GUI-29`.
    Folded(arranged.flowing.filterNot { forwardOf(it, item, saying) && !it.housekeeping }, item, draft)
    for (inset in arranged.insets) {
        when (inset.cardinality) {
            Cardinality.KEYED -> KeyedEditor(inset.label, inset.name, item, draft)
            else -> SingleEditor(inset, item, draft)
        }
    }
}

/**
 * A small heading over the fields or the boxes it gathers.
 *
 * One of them for the whole application: a section of a card, a section of a form, and a part of
 * the planner are the same thing to a reader. `GUI-16`.
 */
@Composable
internal fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = GAP, bottom = HALF),
    )
}

/** Fields in the flow of a form, two to a row, a wide one taking its own. */
@Composable
private fun Rows(fields: List<FieldDescription>, item: Item, draft: Draft) {
    for (row in rowsOf(fields, ::wideOf)) {
        val wide = row.size == 1 && wideOf(row.first())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in row) Box(modifier = Modifier.weight(1f)) { Editor(field, item, draft) }
            // A wide field spans the columns; a last row one short keeps its place in them.
            if (!wide) repeat(COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/**
 * Whether [field] takes a row of its own in a form.
 *
 * A list is a box to a line with an *add* under them, and long text is a paragraph. Either is
 * taller than the four one-line fields beside it. A card is not the same question: there a list
 * reads as one line of names, and only a paragraph needs the width. `GUI-16`.
 */
internal fun wideOf(field: FieldDescription): Boolean =
    field.cardinality == Cardinality.LIST || kindOf(field) == Kind.LONG_TEXT

/**
 * A singular owned item's fields, whether or not the item has one yet.
 *
 * **An item that has never had one has to be able to get one.** A gear item with no buoyancy
 * block cannot say what it weighs, and until this the form simply left the box out: the fields
 * existed, the manual described them, and there was no way to reach them from the window.
 *
 * So the fields are offered either way, and a block nobody had is held by the draft rather than
 * written into the logbook: it lands on Save if something was typed into it and not otherwise.
 * `GUI-29`.
 */
@Composable
private fun SingleEditor(inset: OwnedItemDescription, item: Item, draft: Draft) {
    val owned = (item.read(inset.name) as? Result.Usable)?.value as? OwnedItem
    val block = owned ?: draft.begin(item, inset)
    Inset(inset.label) { EditFields(block, draft) }
}

/**
 * The fields that do not apply to this kind of item, behind a fold that opens.
 *
 * Shut by default and never taken away: what decides is a `category` a reader typed, and one
 * typed wrongly must not make a field unreachable. `GUI-29`.
 */
@Composable
private fun Folded(fields: List<FieldDescription>, item: Item, draft: Draft) {
    if (fields.isEmpty()) return
    var open by remember(item) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HALF),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { open = !open }) {
            // What they are for is said by what they are: naming the kind here would be
            // right for gear and wrong for whatever else grows a fold.
            Text(if (open) "Fewer fields" else "${fields.size} more fields")
        }
    }
    if (!open) return
    Rows(fields, item, draft)
}

/**
 * A keyed owned item's entries, a tab each, the chosen one's fields as editors, with a way to
 * take the open entry out and to add one.
 *
 * Taking out and adding land at once rather than waiting for Save: either is the collection
 * rewritten whole, which makes every entry a new object, and a draft on one of them would be a
 * draft on nothing. The drafts on the collection's entries are dropped for the same reason.
 * Neither asks: an entry is put back by typing it, as the terminal front end has it.
 */
@Composable
private fun KeyedEditor(label: String, name: String, item: Item, draft: Draft) {
    val changer = LocalChanger.current
    val entries = shownEntriesOf(item, name)
    val tab = "${item.description.name}.$name"
    val at = (draft.tabs[tab] ?: 0).coerceIn(0, maxOf(entries.size - 1, 0))
    val tabs: @Composable RowScope.() -> Unit = {
        SmallTabs(
            labels = entries.map { (key, entry) -> entryLabelOf(key, entry) },
            chosen = at,
            marked = pointedEntryOf(item, name),
            onChoose = { draft.tabs[tab] = it },
            onRemove = { index ->
                entries.forEach { (_, entry) -> draft.dropAll(entry) }
                val without = withoutEntry(item, name, entries[index].first)
                changer.change(listOf(Change.Write(item, name, without)))
                draft.tabs[tab] = maxOf(index - 1, 0)
            },
            onAdd = {
                entries.forEach { (_, entry) -> draft.dropAll(entry) }
                val (_, written) = withEntry(item, name)
                changer.change(listOf(Change.Write(item, name, written)))
                draft.tabs[tab] = entries.size
            },
        )
    }
    Inset(label, beside = tabs) {
        if (entries.isEmpty()) {
            Aside("none yet")
        } else {
            Spacer(modifier = Modifier.height(HALF))
            EditFields(entries[at].second, draft)
        }
    }
}

/**
 * One field as an editor: its label, then the widget its kind gets, then the button that unlocks
 * it, with whatever the model would refuse it for under the widget.
 *
 * **The label sits beside the widget**, ranged against it as a card ranges a label against a
 * value, so a field is in the same place read and written. `GUI-29`.
 *
 * **A field the model fills in is drawn the same whether or not it had an answer today.** It is
 * its own box, locked, holding what was calculated or a dash for nothing, with *override* in a
 * slot of its own beside it. Before this a calculation that succeeded gave a line of text and a
 * button while one that came to nothing gave a plain box, so one field was two controls and
 * neither said which kind it was. *Override* rather than *correct*: a button reading *correct*
 * beside a number reads as saying the number is. Overridden, the box is live and the button
 * reads *revert*, which clears what was written so the calculation returns.
 */
@Composable
private fun Editor(field: FieldDescription, item: Item, draft: Draft) {
    val kind = kindOf(field)
    if (kind == Kind.NONE) return
    val offered = offeredOf(field, item, draft)
    // The slot is kept on every row of a type that has one field to override, so the boxes of
    // that form all end in the same place. A type with none spends no width on it.
    val overriding = remember(item.description) {
        item.description.fields.any { it.role is Role.Overrideable }
    }
    // Outside the view's selection, which every other word is in. `GUI-36`. A field being typed
    // into has a selection of its own, which is what a caret is, and two over one run of text
    // fight: a drag would paint the view's selection across the box rather than move the caret.
    // Nothing is lost, a text field copying what it holds already.
    DisableSelection {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                // Dropped to the first line of the box, which a taller widget grows below.
                modifier = Modifier.width(LABEL).padding(top = SITS),
            )
            Column(modifier = Modifier.weight(1f)) {
                when {
                    offered.locked -> Calculated(field, offered.held, item)
                    field.cardinality == Cardinality.LIST -> ListEditor(field, item, draft, kind)
                    else -> {
                        val shown = if (draft.changed(item, field.name)) {
                            draft.shownOf(item, field.name)?.toString().orEmpty()
                        } else {
                            textOf(field, (offered.held as? Result.Usable)?.value)
                        }
                        SingleEditor(field, kind, item, shown) { text, given ->
                            draft.put(item, field.name, text, given)
                        }
                    }
                }
                draft.refusalOf(item, field.name)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (overriding) {
                Box(modifier = Modifier.width(ACTION)) { Unlock(field, item, offered, draft) }
            }
        }
    }
}

/** The button that turns a calculated field into one being written, and back. */
@Composable
private fun Unlock(field: FieldDescription, item: Item, offered: Offered, draft: Draft) {
    if (!overrideable(field)) return
    if (offered.locked) {
        TextButton(
            onClick = {
                // What the box was showing, to correct rather than to retype. Nothing where the
                // calculation had no answer, which is a box to fill in from empty.
                val shown = (offered.held as? Result.Usable)?.let { textOf(field, it.value) }
                draft.put(item, field.name, shown.orEmpty())
            },
        ) {
            Text("Override")
        }
    } else {
        TextButton(
            onClick = {
                // Clearing a correction the logbook holds is a change Save must carry. Clearing
                // one typed into this form is not: there the draft simply forgets it, and Save
                // goes back to being offered only where something really changed.
                if (offered.overridden) {
                    draft.put(item, field.name, null, null)
                } else {
                    draft.drop(item, field.name)
                }
            },
        ) {
            Text("Revert")
        }
    }
}

/**
 * A field nobody types into, in the box it would be typed in, locked.
 *
 * The box is what makes the row the same shape as its neighbours. [held] is what it says, which is
 * what a card would say of it: the value, the reason it will not read, or a dash where there is
 * nothing. `GUI-29`.
 */
@Composable
private fun Calculated(field: FieldDescription, held: Result<Any>, item: Item) {
    val shown = shownOf(field, held, item)
    Compact(
        value = "",
        onChange = {},
        hint = shown?.text ?: NO_VALUE,
        lines = if (kindOf(field) == Kind.LONG_TEXT) LONG else 1,
        derived = true,
        enabled = false,
        wrong = shown?.wrong == true,
    )
}

/** What a field with nothing in it shows, calculated or written. */
private const val NO_VALUE = "—"

/** How many lines a box for a paragraph is tall for at least. */
private const val LONG = 3

/** How far a label is dropped to meet the first line of the box beside it. */
private val SITS = 7.dp

/** How wide the slot holding *override* or *revert* is, kept the same on every row. */
private val ACTION = 92.dp

/**
 * [base] as a value the model calculated is written: italic, and in an ink of its own.
 *
 * The one place the look is decided, so a plan's runtime, a card and a calculator's answer cannot
 * drift apart. The ink is not the one labels are drawn in: a card would then say a field's name
 * and a field's value in the same colour. `GUI-29`.
 */
@Composable
internal fun calculatedOf(base: TextStyle): TextStyle = base.copy(
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontStyle = FontStyle.Italic,
)

/**
 * [base] as [shown] is written, by where its value came from.
 *
 * Calculated is italic, corrected is bold, and written is neither. A value that would not read
 * stands in the error colour, what is drawn being the reason rather than the value, so it is
 * upright: a sentence is not a figure the model arrived at. `GUI-29`.
 */
@Composable
internal fun styleOf(shown: Shown, base: TextStyle): TextStyle = when {
    shown.wrong -> base.copy(color = MaterialTheme.colorScheme.error)
    shown.worked -> calculatedOf(base)
    shown.overridden -> base.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
    else -> base.copy(color = MaterialTheme.colorScheme.onSurface)
}

/**
 * The widget for one value of [kind], showing [shown] and reporting what it is changed to as
 * the text shown and the value given.
 */
@Composable
private fun SingleEditor(
    field: FieldDescription,
    kind: Kind,
    item: Item,
    shown: String,
    onChange: (shown: String, given: Any?) -> Unit,
) {
    when (kind) {
        Kind.CHOICE -> Choice(
            options = (field as TextDescription).fixedSet.orEmpty().toList(),
            shown = shown,
            onChoose = { onChange(it, givenOf(kind, it)) },
        )
        Kind.SUGGESTED -> Suggested(
            options = (field as TextDescription).suggestedSet.orEmpty().toList(),
            shown = shown,
            onChange = { onChange(it, givenOf(kind, it)) },
        )
        Kind.KEY -> Choice(
            options = keysOf(item, (field as KeyReferenceDescription).collection).map { "*$it" },
            shown = shown,
            onChoose = { onChange(it, givenOf(kind, it)) },
        )
        Kind.YES_NO -> YesNo(shown) { onChange(it?.toString().orEmpty(), it) }
        Kind.RATING -> RatingEditor(shown.toIntOrNull()) { onChange(it.toString(), it.toString()) }
        Kind.REFERENCE -> ReferenceEditor(field as ReferenceDescription, item, shown, onChange)
        Kind.LONG_TEXT -> Compact(
            value = shown,
            onChange = { onChange(it, givenOf(kind, it)) },
            lines = 3,
        )
        else -> Compact(
            value = shown,
            onChange = { onChange(it, givenOf(kind, it)) },
            after = unitOf(field),
            hint = when (kind) {
                Kind.CLOCK -> "m:ss"
                Kind.OFFSET -> "+h:mm"
                else -> ""
            },
        )
    }
}

/**
 * The ground and border a value sits in.
 *
 * In one place, so a box that is typed in and one that is chosen from a menu are the same box. A
 * chooser drawn as a word with an arrow after it was thirty pixels wide beside a text field that
 * filled its column, and a form of ten fields had two shapes in it. `GUI-29`.
 */
@Composable
internal fun Modifier.boxed(wrong: Boolean = false, dense: Boolean = false): Modifier =
    clip(SHAPE)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .border(
            1.dp,
            if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
            SHAPE,
        )
        .padding(horizontal = if (dense) GAP / 2 else GAP, vertical = if (dense) 2.dp else 6.dp)

/**
 * A text field the height of its text, which the platform's own is not: a form of twenty
 * fields in boxes fifty-six pixels tall is a form nobody scrolls to the end of.
 *
 * [after] is written after the text, a unit; [hint] is shown in its place while it is empty;
 * [lines] is how many the field is tall for at least, more than one making it multiline.
 *
 * A [derived] hint is a value calculated from the other boxes rather than a prompt, so it is drawn
 * as a calculated value is drawn everywhere. Typing over it replaces it. `GUI-49`.
 */
@Composable
internal fun Compact(
    value: String,
    onChange: (String) -> Unit,
    after: String = "",
    hint: String = "",
    lines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
    /** Added to the field's own, for a caller that reads its keys. */
    modifier: Modifier = Modifier,
    derived: Boolean = false,
    /** Whether it can be typed in. One that cannot is greyed rather than hidden. */
    enabled: Boolean = true,
    /** Smaller text in a tighter box, for a table whose rows should not each take a form's height. */
    dense: Boolean = false,
    /** Whether what it holds is something the model objects to, which is said in the error colour. */
    wrong: Boolean = false,
) {
    val ink = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val type = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    val style = type.copy(color = ink)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        singleLine = lines == 1,
        minLines = lines,
        enabled = enabled,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().then(modifier),
        decorationBox = { inner ->
            Row(
                // A locked box is faded, except where it holds a reason: the one line of a
                // form that must be read is not the one to fade. `GUI-49`.
                modifier = Modifier.fillMaxWidth().alpha(if (enabled || wrong) 1f else GREYED)
                    .boxed(wrong, dense),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(
                            text = hint,
                            style = when {
                                // The style carries the error ink already, which a reason wants.
                                wrong -> style
                                derived -> calculatedOf(style)
                                else -> style.copy(color = MaterialTheme.colorScheme.outline)
                            },
                        )
                    }
                    inner()
                }
                if (after.isNotEmpty()) {
                    Text(
                        text = after,
                        style = type,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = HALF),
                    )
                }
                trailing?.invoke()
            }
        },
    )
}

/**
 * One of [options], chosen from a menu that names them, shown as the one [chosen].
 *
 * An [italic] choice is one nobody made here: it follows from something else, as a planned line
 * breathes the gas of the line above until it is told otherwise. `GUI-43`.
 */
@Composable
internal fun Pick(
    chosen: String,
    options: List<String>,
    italic: Boolean = false,
    /** Smaller text and no button's height, for a row of a table, as [Compact] has. */
    dense: Boolean = false,
    /** Whether the choice is one the model objects to, which is said in the error colour. */
    wrong: Boolean = false,
    onChoose: (Int) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
    Box {
        if (dense) {
            // A button keeps a minimum height a table row has no room for, so this is a row that
            // is clicked instead. Boxed like the cells beside it: a column of numbers in boxes
            // with one bare word among them is a table nobody thought about. `GUI-29`.
            Row(
                modifier = Modifier.fillMaxWidth().clickable { picking = true }
                    .boxed(wrong, dense = true),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chosen,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = fontStyle),
                    color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(DENSE_GLYPH),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { picking = true }.boxed(wrong),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chosen,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = fontStyle),
                    color = if (wrong) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(GLYPH),
                )
            }
        }
        Menu(expanded = picking, onDismissRequest = { picking = false }) {
            for ((index, option) in options.withIndex()) {
                DropdownMenuItem(
                    text = { Text(option) },
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
 * A suggested set: a text field, with the suggestions offered from an arrow beside it and not
 * enforced, since a value outside the set is an ordinary value.
 */
@Composable
private fun Suggested(options: List<String>, shown: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        Compact(
            value = shown,
            onChange = onChange,
            trailing = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "suggestions",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(GLYPH).clickable { picking = true },
                )
            },
        )
        Menu(expanded = picking, onDismissRequest = { picking = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChange(option)
                        picking = false
                    },
                )
            }
        }
    }
}

/** A list's entries, one editor each, with a way to take one out and to add one. */
@Composable
private fun ListEditor(field: FieldDescription, item: Item, draft: Draft, kind: Kind) {
    @Suppress("UNCHECKED_CAST")
    val entries: List<String> = if (draft.changed(item, field.name)) {
        (draft.shownOf(item, field.name) as? List<String>).orEmpty()
    } else {
        entriesOf(field, (item.read(field.name) as? Result.Usable)?.value)
    }
    val givens = remember(item, field.name) { HashMap<Int, Any?>() }
    fun put(shownNow: List<String>) {
        val given = shownNow.indices.map { givens[it] ?: givenOf(kind, shownNow[it]) }
        draft.put(item, field.name, shownNow, given)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        for ((index, entry) in entries.withIndex()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SingleEditor(field, kind, item, entry) { text, given ->
                        givens[index] = given
                        put(entries.toMutableList().also { it[index] = text })
                    }
                }
                IconButton(
                    onClick = {
                        val kept = withoutEntry(givens, index)
                        givens.clear()
                        givens.putAll(kept)
                        put(entries.filterIndexed { at, _ -> at != index })
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "take out",
                        modifier = Modifier.size(GLYPH),
                    )
                }
            }
        }
        TextButton(onClick = { put(entries + "") }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(GLYPH))
            Text("Add")
        }
    }
}

/**
 * What each entry of a list was given, by position, once the entry at [index] is taken out.
 *
 * Every entry after it moves up a place and takes what it was given with it. Left where they were,
 * taking out the first of three gave the third the second's value, and Save lost the third.
 */
internal fun withoutEntry(givens: Map<Int, Any?>, index: Int): Map<Int, Any?> =
    givens.filterKeys { it != index }.mapKeys { (at, _) -> if (at > index) at - 1 else at }

/** A closed set as a drop-down, with nothing as one of the choices. */
@Composable
private fun Choice(options: List<String>, shown: String, onChoose: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { picking = true }.boxed(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown.ifEmpty { NO_VALUE },
                style = MaterialTheme.typography.bodyMedium,
                color = if (shown.isEmpty()) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "choose",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(GLYPH),
            )
        }
        Menu(expanded = picking, onDismissRequest = { picking = false }) {
            DropdownMenuItem(text = { Text("—") }, onClick = { onChoose(""); picking = false })
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChoose(option)
                        picking = false
                    },
                )
            }
        }
    }
}

/** A yes-or-no with a third state for not saying, which is where a field left out stands. */
@Composable
private fun YesNo(shown: String, onChange: (Boolean?) -> Unit) {
    val state = when (shown.lowercase()) {
        "true" -> ToggleableState.On
        "false" -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TriStateCheckbox(
            state = state,
            onClick = {
                onChange(
                    when (state) {
                        ToggleableState.Indeterminate -> true
                        ToggleableState.On -> false
                        ToggleableState.Off -> null
                    },
                )
            },
        )
        Text(
            text = when (state) {
                ToggleableState.On -> "yes"
                ToggleableState.Off -> "no"
                ToggleableState.Indeterminate -> "not said"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Five stars to click, each worth two points, so a click on the third is a six. */
@Composable
private fun RatingEditor(rating: Int?, onChange: (Int) -> Unit) {
    Row {
        for ((index, star) in starsOf(rating ?: 0).withIndex()) {
            Icon(
                imageVector = when (star) {
                    Star.FULL -> Icons.Filled.Star
                    Star.HALF -> Icons.Filled.StarHalf
                    Star.EMPTY -> Icons.Filled.StarOutline
                },
                contentDescription = "${(index + 1) * 2} of 10",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(GLYPH + HALF).clickable { onChange((index + 1) * 2) },
            )
        }
    }
}

/**
 * A reference as a drop-down and a search: the arrow offers the items of the type it points
 * at, or of the one category of them the field wants, and typing narrows them to the ones whose
 * names hold what was typed; one taken is written as its id. What is typed and not taken stands
 * as a plain name where the field allows one, and is refused where it does not.
 */
@Composable
private fun ReferenceEditor(
    field: ReferenceDescription,
    item: Item,
    shown: String,
    onChange: (shown: String, given: Any?) -> Unit,
) {
    val target = Types.ALL.firstOrNull { it.name == field.targetType }
    val named = remember(item, target) { target?.let { entriesOf(item.set, it) }.orEmpty() }
    val offered = remember(item, field) { candidatesOf(item.set, item.description, field) }
    // What is shown is a title where the reference resolves, and the text otherwise.
    val display = if (shown.startsWith("@")) {
        named.firstOrNull { it.id == shown.drop(1) }?.title ?: shown
    } else {
        shown
    }
    var query by remember(item, field.name, display) { mutableStateOf(display) }
    var open by remember { mutableStateOf(false) }
    // Every item of the type from the arrow, and the ones whose names hold what was typed
    // while typing; either way the first few dozen, a list of three hundred dives being no
    // list to choose from.
    val matches = offered.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .take(MATCHES)
    // Making one is offered whenever the list is, so that a reader who opens it meets the
    // possibility rather than having to know it is there. `GUI-48`.
    val making = target?.takeIf { makeable(it) }
    val naming = query.isNotBlank() && matches.none { it.title.equals(query, ignoreCase = true) }
    val changer = LocalChanger.current
    Box {
        Compact(
            value = query,
            onChange = {
                query = it
                open = true
                onChange(it, givenOf(Kind.TEXT, it))
            },
            trailing = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "choose",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(GLYPH).clickable { open = true },
                )
            },
        )
        // The keyboard stays in the box: a list that narrows as a name is typed cannot take it.
        Menu(
            expanded = open && (matches.isNotEmpty() || making != null),
            onDismissRequest = { open = false },
            takesKeys = false,
        ) {
            for (match in matches) {
                DropdownMenuItem(
                    text = { Text(match.title) },
                    onClick = {
                        query = match.title
                        open = false
                        onChange("@" + match.id, "@" + match.id)
                    },
                )
            }
            // A name typed where one is allowed stays a name, and the line says so, so that
            // keeping one and making an item are two deeds rather than one guess. `GUI-48`.
            if (field.oneOffAllowed) {
                DropdownMenuItem(
                    text = { Text(keepingSaid(query.takeIf { naming })) },
                    enabled = naming,
                    onClick = {
                        open = false
                        onChange(query.trim(), givenOf(Kind.TEXT, query.trim()))
                    },
                )
            }
            if (making != null) {
                DropdownMenuItem(
                    text = { Text(makingSaid(making, query.takeIf { naming })) },
                    enabled = naming,
                    onClick = {
                        open = false
                        val fields = mapOf("name" to Stored.Leaf(query.trim()))
                        val made = changer.change(listOf(Change.Add(making, fields)))
                        val id = (made as? Outcome.Done)?.added?.firstOrNull()
                        if (id != null) onChange("@$id", "@$id")
                    },
                )
            }
        }
    }
}

/** The keys a keyed collection of [item] holds, for a key reference to choose among. */
private fun keysOf(item: Item, collection: String): List<String> {
    // The description is asked first: `read` refuses a field the type does not have, and a key
    // reference may name a collection that sits somewhere else.
    if (item.description[collection] == null) return emptyList()
    val held = (item.read(collection) as? Result.Usable)?.value as? Map<*, *>
    return held?.keys?.map { it.toString() }.orEmpty()
}

/**
 * Whether an item of [type] can be made from a name alone.
 *
 * Every type but the dive is named by the user and proposes its id from that name, so a name is
 * the whole of what making one needs. A dive is named for the day it was made on and has no name
 * to give, so nothing offers to make one here. `GUI-48`.
 */
private fun makeable(type: ItemDescription): Boolean =
    type != Types.DIVE && type["name"] != null

/**
 * What the drop-down calls keeping [name] as a written name, on a field that allows one.
 *
 * A buddy nobody keeps an item for is a name and nothing else, and the field takes it. Saying so
 * beside *make one* is what keeps the two apart: one grows the logbook, the other writes a word.
 * `GUI-48`.
 */
internal fun keepingSaid(name: String?): String =
    if (name.isNullOrBlank()) "Keep as a name: type one" else "Keep \"${name.trim()}\" as a name only"

/**
 * What the drop-down calls making a new item of [type], called [name] where one is typed.
 *
 * The line is there either way, since a reader who has not typed anything still learns that a new
 * one can be made from here. With nothing to call it there is nothing to make, so it says what to
 * do instead of offering. `GUI-48`.
 */
internal fun makingSaid(type: ItemDescription, name: String?): String {
    val what = labelOf(type).lowercase()
    return if (name.isNullOrBlank()) "New $what: type a name" else "New $what: \"${name.trim()}\""
}

/** How many items a reference's drop-down offers at once; typing narrows them. */
private const val MATCHES = 24

/** How much of a greyed box shows, which is Material's own figure for something disabled. */
private const val GREYED = 0.38f

/** How large an arrow or a mark is in a dense row. */
internal val DENSE_GLYPH = 16.dp
