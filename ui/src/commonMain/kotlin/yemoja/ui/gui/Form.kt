package yemoja.ui.gui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarHalf
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.KeyReferenceDescription
import yemoja.data.OwnedItem
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.TextDescription
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

/** An item's fields, to be filled in, with Save and Cancel on the title line. */
@Composable
internal fun EditForm(item: Item, onDone: () -> Unit) {
    val changer = LocalChanger.current
    val draft = remember(item) { Draft() }
    var refused by remember(item) { mutableStateOf<String?>(null) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = GAP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = "Editing",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDone) { Text("Cancel") }
        Button(
            onClick = {
                when (val outcome = changer.change(draft.writes())) {
                    is Outcome.Done -> onDone()
                    is Outcome.Refused -> refused = outcome.reason
                }
            },
            enabled = !draft.isEmpty,
        ) {
            Text("Save")
        }
    }
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

/** The fields of [item] as editors, laid out as the item view lays them out. */
@Composable
private fun EditFields(item: Item, draft: Draft) {
    val arranged = remember(item.description) { arrangedOf(item.description) }
    for (pair in arranged.plain.chunked(COLUMNS)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GAP * 2),
        ) {
            for (field in pair) Box(modifier = Modifier.weight(1f)) { Editor(field, item, draft) }
            repeat(COLUMNS - pair.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
    for (inset in arranged.insets) {
        when (inset.cardinality) {
            Cardinality.KEYED -> KeyedEditor(inset.label, inset.name, item, draft)
            else -> {
                val owned = (item.read(inset.name) as? Result.Usable)?.value as? OwnedItem
                if (owned != null) Inset(inset.label) { EditFields(owned, draft) }
            }
        }
    }
}

/** A keyed owned item's entries, a tab each, the chosen one's fields as editors. */
@Composable
private fun KeyedEditor(label: String, name: String, item: Item, draft: Draft) {
    @Suppress("UNCHECKED_CAST")
    val entries = ((item.read(name) as? Result.Usable)?.value as? Map<String, Element<Any>>)
        .orEmpty().mapNotNull { (key, element) ->
            ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
        }
    if (entries.isEmpty()) return
    var open by remember(item, name) { mutableStateOf(0) }
    val at = open.coerceIn(0, entries.size - 1)
    val tabs: @Composable RowScope.() -> Unit = {
        SmallTabs(
            labels = entries.map { (key, entry) -> entryLabelOf(key, entry) },
            chosen = at,
            onChoose = { open = it },
        )
    }
    Inset(label, beside = tabs) {
        Spacer(modifier = Modifier.height(HALF))
        EditFields(entries[at].second, draft)
    }
}

/**
 * One field as an editor: its label, then the widget its kind gets, and under it whatever the
 * model would refuse it for.
 *
 * A field the model works out and nobody may write is shown as read. One it works out unless
 * told otherwise shows what it worked out with a *correct* beside it; corrected, it is edited
 * like any other, with a *revert* that clears the correction so the worked-out value returns.
 */
@Composable
private fun Editor(field: FieldDescription, item: Item, draft: Draft) {
    val kind = kindOf(field)
    if (kind == Kind.NONE) return
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
                TextButton(onClick = { draft.put(item, field.name, textOf(field, stored.value)) }) {
                    Text("correct")
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
        Kind.KEY -> Choice(
            options = keysOf(item, (field as KeyReferenceDescription).collection).map { "*$it" },
            shown = shown,
            onChoose = { onChange(it, givenOf(kind, it)) },
        )
        Kind.YES_NO -> YesNo(shown) { onChange(it?.toString().orEmpty(), it) }
        Kind.RATING -> RatingEditor(shown.toIntOrNull()) { onChange(it.toString(), it.toString()) }
        Kind.REFERENCE -> ReferenceEditor(field as ReferenceDescription, item, shown, onChange)
        Kind.LONG_TEXT -> OutlinedTextField(
            value = shown,
            onValueChange = { onChange(it, givenOf(kind, it)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> OutlinedTextField(
            value = shown,
            onValueChange = { onChange(it, givenOf(kind, it)) },
            singleLine = true,
            suffix = unitOf(field).takeIf { it.isNotEmpty() }?.let { { Text(it) } },
            placeholder = if (kind == Kind.CLOCK) ({ Text("m:ss") }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
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
        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
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
 * A reference as a search: type, and the items of the type it points at whose names match are
 * offered; one taken is written as its id. What is typed and not taken stands as a plain name
 * where the field allows one, and is refused where it does not.
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
    // What is shown is a title where the reference resolves, and the text otherwise.
    val display = if (shown.startsWith("@")) {
        named.firstOrNull { it.id == shown.drop(1) }?.title ?: shown
    } else {
        shown
    }
    var query by remember(item, field.name) { mutableStateOf(display) }
    var open by remember { mutableStateOf(false) }
    val matches = if (query.isBlank()) {
        emptyList()
    } else {
        named.filter { it.title.contains(query, ignoreCase = true) }.take(MATCHES)
    }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                open = true
                onChange(it, givenOf(Kind.TEXT, it))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open && matches.isNotEmpty(), onDismissRequest = { open = false }) {
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

/** How many matching items a reference search offers at once. */
private const val MATCHES = 8
