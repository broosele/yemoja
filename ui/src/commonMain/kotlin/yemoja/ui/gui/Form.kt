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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import yemoja.data.OwnedItemDescription
import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.KeyReferenceDescription
import yemoja.data.OwnedItem
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.logic.Change
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
    val forward = arranged.plain.filter { forwardOf(it, item) }
    for (pair in forward.chunked(COLUMNS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in pair) Box(modifier = Modifier.weight(1f)) { Editor(field, item, draft) }
            repeat(COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
    Folded(arranged.plain.filterNot { forwardOf(it, item) }, item, draft)
    for (inset in arranged.insets) {
        when (inset.cardinality) {
            Cardinality.KEYED -> KeyedEditor(inset.label, inset.name, item, draft)
            else -> SingleEditor(inset, item, draft)
        }
    }
}

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
    for (pair in fields.chunked(COLUMNS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in pair) Box(modifier = Modifier.weight(1f)) { Editor(field, item, draft) }
            repeat(COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
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
    var open by remember(item, name) { mutableStateOf(0) }
    val at = open.coerceIn(0, maxOf(entries.size - 1, 0))
    val tabs: @Composable RowScope.() -> Unit = {
        SmallTabs(
            labels = entries.map { (key, entry) -> entryLabelOf(key, entry) },
            chosen = at,
            marked = pointedEntryOf(item, name),
            onChoose = { open = it },
            onRemove = { index ->
                entries.forEach { (_, entry) -> draft.dropAll(entry) }
                val without = withoutEntry(item, name, entries[index].first)
                changer.change(listOf(Change.Write(item, name, without)))
                open = maxOf(index - 1, 0)
            },
            onAdd = {
                entries.forEach { (_, entry) -> draft.dropAll(entry) }
                val (_, written) = withEntry(item, name)
                changer.change(listOf(Change.Write(item, name, written)))
                open = entries.size
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
 * One field as an editor: its label, then the widget its kind gets, and under it whatever the
 * model would refuse it for.
 *
 * A field the model works out and nobody may write is shown as read. One it works out unless
 * told otherwise shows what it worked out with an *override* beside it; overridden, it is
 * edited like any other, with a *revert* that clears the override so the worked-out value
 * returns. *Override* rather than *correct*: a button reading *correct* beside a number reads
 * as saying the number is.
 */
@Composable
private fun Editor(field: FieldDescription, item: Item, draft: Draft) {
    val kind = kindOf(field)
    if (kind == Kind.NONE) return
    // Outside the view's selection, which every other word is in. `GUI-36`. A field being typed
    // into has a selection of its own, which is what a caret is, and two over one run of text
    // fight: a drag would paint the view's selection across the box rather than move the caret.
    // Nothing is lost, a text field copying what it holds already.
    DisableSelection {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = HALF)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            if (!editable(field)) {
                Read(field, item)
                return@Column
            }
            val stored = item.read(field.name)
            val workedOut = stored is Result.Usable && stored.origin == Result.Origin.DERIVED
            if (overrideable(field) && workedOut && !draft.changed(item, field.name)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Read(field, item)
                    Spacer(modifier = Modifier.width(GAP))
                    TextButton(
                        onClick = { draft.put(item, field.name, textOf(field, stored.value)) },
                    ) {
                        Text("override")
                    }
                }
                return@Column
            }
            if (field.cardinality == Cardinality.LIST) {
                ListEditor(field, item, draft, kind)
            } else {
                val shown = if (draft.changed(item, field.name)) {
                    draft.shownOf(item, field.name)?.toString().orEmpty()
                } else {
                    textOf(field, (stored as? Result.Usable)?.value)
                }
                SingleEditor(field, kind, item, shown) { text, given ->
                    draft.put(item, field.name, text, given)
                }
            }
            val overridden = stored is Result.Usable && stored.origin == Result.Origin.OVERRIDDEN
            if (overrideable(field) && (overridden || draft.changed(item, field.name))) {
                TextButton(onClick = { draft.put(item, field.name, null, null) }) {
                    Text("revert to what is worked out")
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
    }
}

/** A field as read, in an editor: what the item view would show, greyed. */
@Composable
private fun Read(field: FieldDescription, item: Item) {
    val shown = shownOf(field, item)
    Text(
        text = shown?.text ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
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
 * A text field the height of its text, which the platform's own is not: a form of twenty
 * fields in boxes fifty-six pixels tall is a form nobody scrolls to the end of.
 *
 * [after] is written after the text, a unit; [hint] is shown in its place while it is empty;
 * [lines] is how many the field is tall for at least, more than one making it multiline.
 */
@Composable
internal fun Compact(
    value: String,
    onChange: (String) -> Unit,
    after: String = "",
    hint: String = "",
    lines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodyMedium.copy(color = ink)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        singleLine = lines == 1,
        minLines = lines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(SHAPE)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SHAPE)
                    .padding(horizontal = GAP, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(hint, style = style, color = MaterialTheme.colorScheme.outline)
                    }
                    inner()
                }
                if (after.isNotEmpty()) {
                    Text(
                        text = after,
                        style = MaterialTheme.typography.bodyMedium,
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
                        givens.remove(index)
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
            Text("add")
        }
    }
}

/** A closed set as a drop-down, with nothing as one of the choices. */
@Composable
private fun Choice(options: List<String>, shown: String, onChoose: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clip(SHAPE).clickable { picking = true }
                .padding(horizontal = HALF, vertical = HALF),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = shown.ifEmpty { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "choose",
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
    var query by remember(item, field.name) { mutableStateOf(display) }
    var open by remember { mutableStateOf(false) }
    // Every item of the type from the arrow, and the ones whose names hold what was typed
    // while typing; either way the first few dozen, a list of three hundred dives being no
    // list to choose from.
    val matches = offered.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        .take(MATCHES)
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
        Menu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
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
        }
    }
}

/** The keys a keyed collection of [item] holds, for a key reference to choose among. */
private fun keysOf(item: Item, collection: String): List<String> {
    val held = (item.read(collection) as? Result.Usable)?.value as? Map<*, *>
    return held?.keys?.map { it.toString() }.orEmpty()
}

/** How many items a reference's drop-down offers at once; typing narrows them. */
private const val MATCHES = 24
