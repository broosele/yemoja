package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.ItemWriter
import yemoja.data.inOrder
import yemoja.data.MultilineTextDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.Change
import yemoja.logic.freeName
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Universe

/**
 * Screen is the whole terminal interface: which tab is open, which item and field are chosen, and
 * what that looks like as rows of text.
 *
 * **Deliberately not immutable.** Where the user is in the interface is presentation state, which
 * `ui/doc.md` allows a front end to hold, and it changes on every keystroke.
 *
 * It holds no terminal. [paint] answers with rows and [press] takes a key, so what reads the
 * keyboard and what puts characters on a screen is somebody else's, and this is testable without
 * one.
 *
 * **The layout comes from the descriptions and nowhere else.** A tab per type in the order the
 * set was built with, and every single-valued field of the chosen item, in the order the type
 * declares them. Nothing here names a type or a field, so a type added to the logic layer appears
 * without this changing.
 */
class Screen(private val universe: Universe) {

    // Everything shown comes from the logbook the universe holds; everything changed goes back
    // through the universe itself. `ui/doc.md` holds every front end to reaching both this way.
    private val set: ItemSet get() = universe.logbook

    // The set's own, so a tab cannot name a type the set could never hold. The order is the
    // logic layer's, which is what built the set. `UI-3`.
    private val types: List<ItemDescription> = universe.logbook.descriptions

    init {
        require(types.isNotEmpty()) { "a screen should have at least one type, and had none" }
    }

    /** Which tab is open, as an index into the set's own types. */
    var tab: Int = 0
        private set

    /** Whether the interface is still running. False once the user has asked to leave. */
    var running: Boolean = true
        private set

    // One item, one field and one scroll offset per tab, so that leaving a tab and coming back
    // returns to where the user was rather than to the top.
    private val chosen = IntArray(types.size)

    private val chosenField = IntArray(types.size)

    private val first = IntArray(types.size)

    // How far down the column of fields is scrolled, which an item holding items can need.
    private val deep = IntArray(types.size)

    // How far into the chosen item a reader has gone: a field, then a key inside it, then a
    // field of what was under that key, and so on down. Empty where the list is being shown.
    private val path = ArrayList<Pair<FieldDescription, String?>>()

    // How far down what is open is scrolled, and which of the things in it is chosen. One of
    // each, since a reader is in one place at a time.
    private var within = 0

    private var chosenEntry = 0

    // What is being typed into the open field, or absent where nothing is. Presentation state
    // like the rest of it: nothing below this knows an edit is under way until it is saved.
    private var editing: String? = null

    // Which suggestion the cursor is on while a reference is typed, or absent where it is on
    // what is being typed. A suggestion is taken by moving onto it, so the typed text is a stop
    // of its own in the same ring.
    private var suggesting: Int? = null

    // Which of a chooser's rows the cursor is on, or absent where no chooser is open. A field
    // whose values are known is picked from rather than typed into.
    private var choosing: Int? = null

    // Which source the import screen's cursor is on, or absent where that screen is not up.
    private var sourcing: Int? = null

    // What went wrong with the last thing asked for, shown until the next key. A refusal from
    // below has to reach the reader, and an arriving item that cannot go in is the one place
    // where the thing refused stays on the screen to be refused again.
    private var message: String? = null

    // Whether delete has been pressed once on what the cursor is on. Nothing can be undone yet,
    // there being no journal -- `FEAT-4` -- so removing an item asks, and any other key answers
    // no. `TUI-6` refused a confirmation for quitting, where nothing is lost by pressing again.
    private var confirming: Boolean = false

    /** Whether something is open on its own, rather than the list being shown. */
    val opened: Boolean get() = path.isNotEmpty()

    /** What is being typed, or absent where nothing is being typed. */
    val typing: String? get() = editing

    /** Which choice the cursor is on, or absent where nothing is being chosen. */
    val choice: Int? get() = choosing

    /** The type whose tab is open. */
    val type: ItemDescription get() = types[tab]

    // What each tab last listed, and which revision of the universe it was. Sorting reads every
    // sort key, and a key can be worked out from a whole profile, so doing it per keypress would
    // walk the logbook to redraw one row. Nothing is announced -- `DATA-6` -- so the universe
    // carries a number that moves on every change, and this is kept against it.
    private val listed = HashMap<ItemDescription, List<ReferenceableItem>>()

    private val coming = HashMap<ItemDescription, List<ReferenceableItem>>()

    private var counted = -1 to -1

    /**
     * Every item of the open type: what is arriving first, then what the logbook holds.
     *
     * An import is not a screen of its own. Its items sit in the tab their type would put them
     * in, above the ones already there and set in italic, so what is arriving is read where it
     * will end up and against what is already there.
     */
    val items: List<ReferenceableItem> get() = arriving() + listing(type)

    /** The items of the open type that are arriving, or none where nothing is. */
    private fun arriving(): List<ReferenceableItem> {
        val import = universe.importing ?: return emptyList()
        fresh()
        return coming.getOrPut(type) { import.staged.logbook.inOrder(type) }
    }

    /** Every item of [description] the logbook holds, in its own order. */
    private fun listing(description: ItemDescription): List<ReferenceableItem> {
        fresh()
        return listed.getOrPut(description) { set.inOrder(description) }
    }

    // Both sets are sorted once per change rather than per keystroke, and either changing is a
    // reason to sort again: taking an item in moves it from one list to the other.
    private fun fresh() {
        val now = universe.revision to (universe.importing?.staged?.revision ?: -1)
        if (counted == now) return
        listed.clear()
        coming.clear()
        counted = now
    }

    /** What [item] is called, whichever of the two sets it is in. */
    private fun idOf(item: ReferenceableItem): String? =
        set.idOf(item) ?: universe.importing?.staged?.logbook?.idOf(item)

    /** Whether the chosen item is arriving rather than held. */
    private val chosenIsArriving: Boolean
        get() = item?.let { universe.importing?.staged?.logbook?.idOf(it) != null } ?: false

    /**
     * The universe the chosen item lives in, which is where a change to it goes.
     *
     * An arriving item is edited in the logbook it arrived as, not in the one it is going into:
     * correcting a downloaded dive before taking it in must not touch anything yet.
     */
    private fun holder(): Universe =
        if (chosenIsArriving) universe.importing!!.staged else universe

    /** The chosen item, or absent where the open type has none. */
    val item: ReferenceableItem? get() = items.getOrNull(chosen[tab])

    /**
     * The rows of the chosen item: one per field, and one per field of anything inside it.
     *
     * Every one of them can be chosen, which is what makes a field of a medical reachable
     * without its being a place of its own.
     */
    internal val rows: List<Row> get() = item?.let { rowsOf(it) }.orEmpty()

    /** The chosen row, or absent where the open type has no items. */
    internal val row: Row? get() = rows.getOrNull(chosenField[tab])

    /** The chosen field, or absent where the open type has none. */
    val field: FieldDescription? get() = row?.field

    /** The fields of the open type, for anything counting them. */
    val fields: List<FieldDescription> get() = type.fields

    /**
     * How many values the chosen field holds, or absent where it does not hold a list of them.
     *
     * What decides whether there is anything to move between where a reader is: the values of
     * a list, the fields of an item, or the keys of a field holding several. A single value is
     * one thing however many rows it wraps over, so there is nothing to move between and up and
     * down scroll instead.
     */
    private fun entries(): Int? {
        if (!opened) return null
        return endsOf(item ?: return null, path)?.count()
    }

    /**
     * Answer [key], and say whether the interface is still running.
     *
     * **Each key does one job, and does it wherever the reader is.** Tab moves between tabs — the
     * types, or the keys of an open field. Left and right move through the list of items. Up and
     * down move through the fields in front of the reader.
     *
     * Choosing goes round the ends and scrolling stops at them, so either end of any list is one
     * press from the other. `TUI-7`.
     */
    fun press(key: Key): Boolean {
        when {
            sourcing != null -> sourceKey(key)
            editing != null -> typedKey(key)
            choosing != null -> chosenKey(key)
            else -> browsedKey(key)
        }
        if (key != Key.DELETE) confirming = false
        if (sourcing == null && key != Key.INSERT) message = null
        if (!opened) {
            within = 0
            chosenEntry = 0
        }
        // An emptied tab leaves the chosen row past the end, and nothing else corrects it.
        chosen[tab] = chosen[tab].coerceIn(0, (items.size - 1).coerceAtLeast(0))
        chosenField[tab] = chosenField[tab].coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        return running
    }

    /** What each key does where nothing is being edited, which is most of this interface. */
    private fun browsedKey(key: Key) {
        when (key) {
            Key.LEFT -> alongItems(-1)
            Key.RIGHT -> alongItems(1)
            Key.UP -> alongFields(-1)
            Key.DOWN -> alongFields(1)
            Key.NEXT_TAB -> alongTabs(1)
            Key.PREVIOUS_TAB -> alongTabs(-1)
            Key.OPEN -> open()
            Key.CLOSE -> close()
            Key.LEAVE -> running = false
            is Key.Typed -> typed(key.character)
            Key.DELETE -> remove()
            Key.INSERT -> takeIn()
            Key.BACKSPACE -> Unit
        }
    }

    /**
     * What each key does on the import screen, which is where an import is started.
     *
     * Two rows and, once *another logbook* is chosen, a folder to type. Nothing else on the
     * screen answers while it is up: an import is begun or it is not.
     */
    private fun sourceKey(key: Key) {
        val typed = editing
        if (typed != null) {
            when (key) {
                Key.OPEN -> from(typed)
                Key.CLOSE -> {
                    editing = null
                    message = null
                }

                Key.BACKSPACE -> editing = typed.dropLast(1)
                Key.LEAVE -> running = false
                is Key.Typed -> editing = typed + key.character
                else -> Unit
            }
            return
        }
        when (key) {
            Key.UP -> sourcing = (sourcing!! + SOURCES.size - 1) % SOURCES.size
            Key.DOWN -> sourcing = (sourcing!! + 1) % SOURCES.size
            Key.OPEN -> chose()
            Key.CLOSE -> {
                sourcing = null
                message = null
            }

            Key.LEAVE -> running = false
            else -> Unit
        }
    }

    /** Take the source the cursor is on, of which only the first is built. */
    private fun chose() {
        message = null
        if (sourcing == 0) editing = "" else message = NO_DEVICE
    }

    /** Open the logbook in [folder] as an import, or say why it could not be. */
    private fun from(folder: String) {
        when (val done = universe.importFrom(folder)) {
            is Outcome.Refused -> message = done.reason
            is Outcome.Done -> {
                editing = null
                sourcing = null
                message = null
            }
        }
    }

    /**
     * Take the arriving item the cursor is on into the logbook.
     *
     * It leaves the list as it goes, so what is left above the line is what has not been decided.
     * A refusal leaves it where it is and says why, since an item that cannot go in is one a
     * reader has to see in order to fix it.
     */
    private fun takeIn() {
        if (opened) return
        val import = universe.importing ?: return
        val id = item?.let { import.staged.logbook.idOf(it) } ?: return
        message = (import.insert(id) as? Outcome.Refused)?.reason
    }

    /**
     * What each key does while a value is being typed.
     *
     * **Every printable character is a character**, which is what `TUI-6` said would happen to
     * `q`. Nothing moves: a key that changed which field was chosen would leave the editor
     * saving into somewhere the reader was no longer looking at.
     */
    private fun typedKey(key: Key) {
        when (key) {
            Key.OPEN -> if (suggesting == null) save() else takeSuggestion()
            Key.CLOSE -> {
                editing = null
                suggesting = null
            }

            Key.BACKSPACE -> {
                editing = editing?.dropLast(1)
                suggesting = null
            }

            Key.UP -> alongSuggestions(-1)
            Key.DOWN -> alongSuggestions(1)
            Key.LEAVE -> running = false
            is Key.Typed -> {
                editing = editing?.plus(key.character)
                suggesting = null
            }

            else -> Unit
        }
    }

    /**
     * The items a reference being typed could name, narrowed by what has been typed so far.
     *
     * Empty for every other kind: a date and a remark have nothing to be completed from, and a
     * field whose values are known opens a chooser instead of an editor.
     *
     * Matched anywhere in the id rather than at its front, since a dive's id opens with its date
     * and somebody looking for one is as likely to remember the rest of it. In the type's own
     * order, `DATA-89`, which is the order its tab lists it in.
     */
    private fun suggestionsOf(ends: Ends.Value): List<String> {
        val field = ends.field as? ReferenceDescription ?: return emptyList()
        val typed = editing ?: return emptyList()
        val named = set.descriptions.firstOrNull { it.name == field.targetType }
            ?: return emptyList()
        val looking = typed.removePrefix("@").lowercase()
        return listing(named).mapNotNull { set.idOf(it) }
            .filter { looking in it.lowercase() }
    }

    /** As many suggestions as are shown, the rest being reached by typing more. */
    private fun shownSuggestions(ends: Ends.Value): List<String> =
        suggestionsOf(ends).take(MOST_SUGGESTIONS)

    /**
     * Move [by] over the suggestions, round the ends, with the typed text among them.
     *
     * The text is a stop like any other, so moving past the last suggestion arrives back at what
     * was typed rather than sticking. `TUI-7`.
     */
    private fun alongSuggestions(by: Int) {
        val ends = editable() ?: return
        val many = shownSuggestions(ends).size
        if (many == 0) return
        val ring = many + 1
        val next = ((suggesting?.plus(1) ?: 0) + by + ring) % ring
        suggesting = if (next == 0) null else next - 1
    }

    /** Take the suggestion the cursor is on, which is an answer rather than a way to type one. */
    private fun takeSuggestion() {
        val ends = editable() ?: return
        val taken = shownSuggestions(ends).getOrNull(suggesting ?: return) ?: return
        editing = "@" + taken
        suggesting = null
        save()
    }

    /** What each key does while a value is being chosen: up and down move, enter takes one. */
    private fun chosenKey(key: Key) {
        when (key) {
            Key.UP -> alongChoices(-1)
            Key.DOWN -> alongChoices(1)
            Key.OPEN -> take()
            Key.CLOSE -> choosing = null
            Key.LEAVE -> running = false
            else -> Unit
        }
    }

    /**
     * Take the choice the cursor is on.
     *
     * The *other* row is not a value but a way to reach one, so enter there opens the editor
     * rather than saving. The chooser stays open under it, which is what escape comes back to:
     * a step in is undone by a step out, and picking *other* was a step in.
     */
    private fun take() {
        val ends = editable() ?: return
        if (choices(ends.field)?.getOrNull(choosing ?: return) is Choice.Other) {
            editing = otherOf(ends)
            return
        }
        saveChoice()
    }

    /**
     * What [field] offers to choose between, or absent where its values are not known.
     *
     * The Universe joins a field's presets with what the logbook already uses — `DATA-25` — so
     * this asks it rather than the description, which knows only the half it ships with.
     */
    private fun choices(field: FieldDescription): List<Choice>? =
        choicesOf(field, universe.suggested(field))

    /** What the field holds, where the rows do not offer it, and nothing where they do. */
    private fun otherOf(ends: Ends.Value): String {
        val here = written(ends)
        val offered = choices(ends.field).orEmpty()
            .filterIsInstance<Choice.Value>().map { it.said }
        return if (here in offered) "" else here
    }

    /** Move [by] choices, round the ends, as everything chosen between does. `TUI-7`. */
    private fun alongChoices(by: Int) {
        val ends = editable() ?: return
        val many = choices(ends.field)?.size ?: return
        choosing = ((choosing ?: 0) + by + many) % many
    }

    /**
     * What a typed character means where the reader is.
     *
     * Space follows a reference and `n` makes something. `q` leaves, and only from the list.
     */
    private fun typed(character: Char) {
        when {
            character == ' ' -> follow()
            character == '+' -> sourcing = 0
            character.equals('n', ignoreCase = true) -> make()
            // The top level's key. Escape is what comes out of anything opened, and it comes
            // out to the one place `q` means something.
            character.equals('q', ignoreCase = true) -> if (!opened) running = false
        }
    }

    /**
     * Where the reader is standing, as far as making and removing go.
     *
     * The keys that do the work and the bar that offers them ask this one question, so the bar
     * cannot offer what the key would not do.
     */
    private fun standing(): Standing {
        if (!opened) return Standing.Items
        val item = item ?: return Standing.Nowhere
        val owner = itemAt(path.size - 1) ?: return Standing.Nowhere
        val (naming, key) = path.last()
        return when (val ends = endsOf(item, path)) {
            is Ends.Value -> when {
                ends.field.cardinality == Cardinality.LIST -> Standing.Entries(ends)
                ends.field is OwnedItemDescription -> Standing.Empty(ends)
                else -> Standing.Nowhere
            }

            is Ends.Keys -> Standing.Collection(ends.field, owner, ends.held, null)
            // A key is a tab rather than a stop of its own, so a reader at one is inside the
            // entry under it, and what they are standing on is still the collection.
            is Ends.Within -> when {
                key != null ->
                    Standing.Collection(naming, owner, keyedOf(held(owner, naming)), key)

                naming is OwnedItemDescription -> Standing.Owned(naming, owner)
                else -> Standing.Nowhere
            }

            null -> Standing.Nowhere
        }
    }

    /** What [owner] holds in [field] now, or nothing where it holds nothing usable. */
    private fun held(owner: Item, field: FieldDescription): Any? =
        (owner.read(field.name) as? Result.Usable)?.value

    /**
     * Make a new one of whatever the reader is looking at.
     *
     * An item of the open type where the list is shown, an entry where a list or a keyed
     * collection is open, and the owned item a field holds where it holds none. One key for one
     * job wherever it is done, which is what the rest of the map is held to.
     */
    private fun make() {
        when (val at = standing()) {
            is Standing.Items -> {
                val made = universe.change(Operation.EDIT, Change.Add(type))
                if (made is Outcome.Done) made.added.singleOrNull()?.let { open(it) }
            }

            is Standing.Entries -> added(at.ends)
            is Standing.Empty -> owned(at.ends)
            is Standing.Collection -> keyedEntry(at.field, at.held)
            is Standing.Owned, is Standing.Nowhere -> Unit
        }
    }

    /** An empty entry at the end of an open list, and the cursor on it. */
    private fun added(ends: Ends.Value) {
        val out = ArrayList<Stored>(entriesIn(ends).map { asStored(ends, it) })
        out.add(chosenEntry.coerceIn(0, out.size), Stored.Leaf(""))
        write(ends, Stored.Elements(out))
    }

    /** The values an open list holds now, as elements. */
    @Suppress("UNCHECKED_CAST")
    private fun entriesIn(ends: Ends.Value): List<Element<Any>> =
        ((ends.read as? Result.Usable)?.value as? List<Element<Any>>).orEmpty()

    /** One value of an open list, back in the form a file holds it in. */
    private fun asStored(ends: Ends.Value, one: Element<Any>): Stored = when (one) {
        is Element.Usable -> Stored.Leaf(ends.field.format(one.value, Units.DEFAULT))
        is Element.Unusable -> one.raw
    }

    /**
     * The owned item a field holds, where it holds none.
     *
     * An empty set of fields, which is what `"medical": {}` says in a file: an owned item cannot
     * arrive ready-made, being built by the item that owns it. `DATA-85`.
     */
    private fun owned(ends: Ends.Value) {
        if (ends.read is Result.Usable) return
        write(ends, Stored.Members(emptyMap()))
    }

    private fun write(ends: Ends.Value, given: Stored) {
        val owner = ends.item ?: return
        holder().change(Operation.EDIT, Change.Write(owner, ends.field.name, given))
    }

    /**
     * Remove whatever the reader is on.
     *
     * An item asks first, since nothing can be undone until `FEAT-4` and a dive holds a
     * recording nobody can type again. An entry of a list or a collection does not: it is one
     * value, and putting it back is typing it.
     */
    private fun remove() {
        when (val at = standing()) {
            is Standing.Items -> deleted()
            is Standing.Entries ->
                if (onEntry(at.ends)) write(at.ends, listed(at.ends, "")) else Unit
            is Standing.Collection -> if (at.at != null) unkeyedEntry(at.field, at.held)
            is Standing.Owned -> {
                holder().change(Operation.EDIT, Change.Write(at.owner, at.field.name, null))
                chosenEntry = 0
            }

            is Standing.Empty, is Standing.Nowhere -> Unit
        }
    }

    /**
     * Deletes the chosen item, on the second press of the key.
     *
     * An arriving item is left out of the import instead, which takes it out of the staged
     * logbook rather than out of this one. It asks first for the same reason: what is staged may
     * have been corrected by hand, and that correction is not somewhere else.
     */
    private fun deleted() {
        val here = item ?: return
        val import = universe.importing
        val arriving = import?.staged?.logbook?.idOf(here)
        if (!confirming) {
            confirming = true
            return
        }
        confirming = false
        if (arriving != null) {
            import.remove(arriving)
            return
        }
        val id = set.idOf(here) ?: return
        universe.change(Operation.EDIT, Change.Delete(id))
    }

    /**
     * An empty entry under a key of its own, and the cursor moved to it.
     *
     * The key is proposed by the type of the entry and the first free one is taken, which is the
     * same rule an id follows one level up. `JSON-18`. A hand-made entry is empty, so it takes
     * its type's fallback — `profile`, `gas` — exactly as a hand-made item becomes
     * `unknown_person`, and like an id it is not renamed when the entry is filled in.
     */
    private fun keyedEntry(field: FieldDescription, held: List<Pair<String, Any>>) {
        val owner = itemAt(path.size - 1) ?: return
        val within = (field as? OwnedItemDescription)?.description ?: return
        val written = writtenEntries(held) { true }
        val empty = ItemReader.read(within, Stored.Members(emptyMap()), set, Units.DEFAULT)
        val key = freeName(within.proposedId?.invoke(empty) ?: within.name) { it in written }
        written[key] = Stored.Members(emptyMap())
        holder().change(Operation.EDIT, Change.Write(owner, field.name, Stored.Members(written)))
        path[path.size - 1] = field to key
    }

    /** Takes the entry under the chosen key out, and moves to whatever is left. */
    private fun unkeyedEntry(field: FieldDescription, held: List<Pair<String, Any>>) {
        val owner = itemAt(path.size - 1) ?: return
        val going = path.last().second ?: return
        val kept = writtenEntries(held) { it != going }
        holder().change(Operation.EDIT, Change.Write(owner, field.name, Stored.Members(kept)))
        path[path.size - 1] = field to kept.keys.firstOrNull()
        chosenEntry = 0
    }

    /** The entries [held] whose key [keeping] accepts, back in the form a file holds them in. */
    private fun writtenEntries(
        held: List<Pair<String, Any>>,
        keeping: (String) -> Boolean,
    ): LinkedHashMap<String, Stored> {
        val out = LinkedHashMap<String, Stored>()
        for ((key, entry) in held) {
            if (!keeping(key)) continue
            if (entry is Item) out[key] = ItemWriter.write(entry, Units.DEFAULT)
        }
        return out
    }

    /** The item the step at [at] hangs off, which is what a change to that field is written on. */
    private fun itemAt(at: Int): Item? {
        val above = item ?: return null
        if (at == 0) return above
        return (endsOf(above, path.take(at)) as? Ends.Within)?.item
    }

    /**
     * What an edit would go into, or absent where there is nothing here to edit.
     *
     * A value with an item to hold it, on a field that is written rather than worked out. A
     * series is excluded because nothing types three thousand samples into a terminal, which
     * `TUI-3` calls a gap in this front end rather than a hazard from it.
     */
    private fun editable(): Ends.Value? {
        val ends = endsOf(item ?: return null, path) as? Ends.Value ?: return null
        if (ends.item == null || ends.field.role is Role.Derived) return null
        if (ends.field is OwnedItemDescription) return null
        val series = ends.field.cardinality == Cardinality.SERIES ||
            ends.field.cardinality == Cardinality.KEYED_SERIES
        return if (series) null else ends
    }

    /**
     * Start editing [ends], in the shape its field asks for.
     *
     * A field whose values are known opens a chooser and every other opens a text editor. The
     * cursor starts on what the field holds; where that is nothing, or is something the set does
     * not contain, it starts on the row that clears it, which says *none of these* by sitting
     * there.
     */
    private fun edit(ends: Ends.Value) {
        suggesting = null
        val choices = choices(ends.field)
        if (choices == null) {
            editing = written(ends)
            return
        }
        val here = written(ends).ifEmpty { null }
        val at = choices.indexOfFirst { it is Choice.Value && it.said == here }
        val other = choices.indexOfFirst { it is Choice.Other }
        choosing = when {
            at >= 0 -> at
            here != null && other >= 0 -> other
            else -> choices.lastIndex
        }
    }

    /**
     * What the field being edited holds now, as a user would have typed it.
     *
     * The written form, `DATA-76`, which is what a row shows and what reading it back accepts.
     * A value that would not read is offered as the source held it, so correcting a typo is
     * editing the typo rather than starting again.
     */
    private fun written(ends: Ends.Value): String {
        val read = ends.read
        if (read is Result.Unusable) return leafOf(read.raw)
        if (read !is Result.Usable) return ""
        if (ends.field.cardinality != Cardinality.LIST) {
            return ends.field.format(read.value, Units.DEFAULT)
        }
        @Suppress("UNCHECKED_CAST")
        return when (val held = (read.value as List<Element<Any>>).getOrNull(chosenEntry)) {
            is Element.Usable -> ends.field.format(held.value, Units.DEFAULT)
            is Element.Unusable -> leafOf(held.raw)
            null -> ""
        }
    }

    private fun leafOf(raw: Stored): String = (raw as? Stored.Leaf)?.value?.toString().orEmpty()

    /**
     * Why what is being typed will not do, or absent where it will.
     *
     * The same door the save goes through: a field reads what it is given and says why it cannot,
     * so what a reader is told while typing is what happens when they press enter. `DATA-66`.
     */
    private fun refusal(): String? {
        val ends = editable() ?: return null
        val text = editing ?: return null
        if (text.isEmpty()) return null
        val overrides = ends.field.role is Role.Overrideable
        val read = ends.field.read(typedAs(ends.field, text), overrides, Units.DEFAULT)
        return (read as? Result.Unusable)?.reason
    }

    /**
     * The typed text as the field should receive it.
     *
     * A line break cannot be typed, since enter saves. So in a field that allows one, `\n`
     * becomes a break: the same two characters a row shows a break as, read the other way.
     */
    private fun typedAs(field: FieldDescription, text: String): String =
        if (field is MultilineTextDescription) text.replace("\\n", "\n") else text

    /**
     * Save what is being typed, and stop typing.
     *
     * An empty box clears the field, which on a corrected one puts back what the application
     * works out: deleting a correction is writing nothing over it rather than an act of its own.
     */
    private fun save() {
        val ends = editable() ?: return
        val text = editing ?: return
        val given: Any? = if (ends.field.cardinality != Cardinality.LIST) {
            typedAs(ends.field, text).ifEmpty { null }
        } else {
            listed(ends, text)
        }
        if (saved(ends, given)) {
            editing = null
            choosing = null
            suggesting = null
        }
    }

    /** Take the choice the cursor is on, the last of which is the field holding nothing. */
    private fun saveChoice() {
        val ends = editable() ?: return
        val taken = choices(ends.field)?.getOrNull(choosing ?: return)
        val chosen = (taken as? Choice.Value)?.said
        val given: Any? = if (ends.field.cardinality != Cardinality.LIST) {
            chosen
        } else {
            listed(ends, chosen.orEmpty())
        }
        if (saved(ends, given)) choosing = null
    }

    /** Write [given] to the field [ends] is at, and say whether it was taken. */
    private fun saved(ends: Ends.Value, given: Any?): Boolean {
        val owner = ends.item ?: return false
        return holder().change(
            Operation.EDIT,
            Change.Write(owner, ends.field.name, given),
        ) is Outcome.Done
    }

    /**
     * The whole list, with the entry the cursor is on replaced by [text], or dropped where it
     * is empty.
     *
     * A list is one field, so writing one entry is writing the list. The others go back as the
     * text they are shown as, which reads back as what they were: `DATA-76` makes that a round
     * trip rather than a hope.
     */
    private fun listed(ends: Ends.Value, text: String): Stored {
        val held = entriesIn(ends)
        val out = ArrayList<Stored>(held.size + 1)
        for (at in held.indices) {
            if (at == chosenEntry) {
                if (text.isNotEmpty()) out.add(Stored.Leaf(text))
                continue
            }
            out.add(asStored(ends, held[at]))
        }
        // The place after the last value holds nothing to replace, so what is typed there is a
        // value being added. It is the only place an empty list has.
        if (chosenEntry >= held.size && text.isNotEmpty()) out.add(Stored.Leaf(text))
        return Stored.Elements(out)
    }

    /**
     * Go one step further in, where there is one.
     *
     * From the list that is the chosen field. Inside an item it is the field the cursor is on,
     * and at a set of keys it is the key. At values there is nowhere further to go, an open
     * field already showing the whole of what it holds.
     */
    private fun open() {
        val item = item ?: return
        editable()?.let {
            edit(it)
            return
        }
        if (!opened) {
            val steps = (row ?: return).steps
            path.addAll(steps.dropLast(1))
            add(steps.last().first)
        } else {
            when (val ends = endsOf(item, path)) {
                is Ends.Within -> add(ends.item.description.fields[chosenEntry])
                else -> return
            }
        }
        within = 0
        chosenEntry = 0
    }

    /**
     * Go one step in, to [field] and, where it holds one thing per key, to the first of them.
     *
     * A key is not a stop of its own. The keys are a row of tabs above what is under the chosen
     * one, so arriving at a field holding several is arriving at one of them.
     */
    private fun add(field: FieldDescription) {
        val keyed = field.cardinality == Cardinality.KEYED ||
            field.cardinality == Cardinality.KEYED_SERIES
        val first = if (!keyed) null else {
            val ends = endsOf(item ?: return, path + (field to null))
            (ends as? Ends.Keys)?.held?.firstOrNull()?.first
        }
        path.add(field to first)
    }

    /**
     * Come one step back out, and do nothing where there is nothing to come out of.
     *
     * **Escape never leaves.** It used to, where nothing was open, which made one key too many
     * out of a step back the mildest thing a reader does. Leaving is [Key.QUIT]'s, and that
     * works from wherever the reader is.
     */
    private fun close() {
        if (!opened) return
        val last = path.removeAt(path.size - 1)
        // The cursor lands on what was just left rather than at the top, the way each tab keeps
        // the row it was on: coming out of something is not the same as arriving somewhere.
        chosenEntry = when (val ends = item?.let { endsOf(it, path) }) {
            is Ends.Within ->
                ends.item.description.fields.indexOfFirst { it.name == last.first.name }

            is Ends.Keys -> ends.held.indexOfFirst { it.first == last.second }
            else -> 0
        }.coerceAtLeast(0)
        within = 0
    }

    /**
     * Move [by] tabs, round the end.
     *
     * The types where nothing is open, and the keys of a field holding several where one is:
     * both are a row of names with one of them in brackets, so both are tabs and both answer to
     * the key named after them.
     */
    private fun alongTabs(by: Int) {
        if (!opened) {
            tab = (tab + by + types.size) % types.size
            return
        }
        val keys = keysHere()
        if (keys.isEmpty()) return
        val at = keys.indexOf(path.last().second).coerceAtLeast(0)
        val last = path.removeAt(path.size - 1)
        path.add(last.first to keys[(at + by + keys.size) % keys.size])
        within = 0
        chosenEntry = 0
    }

    /**
     * Move [by] through the list of items, round the ends.
     *
     * Only where the list is what a reader is looking at. Inside an open field the item beneath
     * it is what the path was worked out against, and moving it would leave the path pointing at
     * an item that never had it.
     */
    private fun alongItems(by: Int) {
        if (opened) return
        val many = items.size
        if (many == 0) return
        chosen[tab] = (chosen[tab] + by + many) % many
    }

    /**
     * The keys of the field the reader is inside, or none where they are not inside one.
     *
     * Asked of the same walk that resolves everything else, one step shorter: what the keys of
     * a field are is what that field looks like before a key is chosen.
     */
    private fun keysHere(): List<String> {
        val last = path.lastOrNull() ?: return emptyList()
        if (last.second == null) return emptyList()
        val ends = endsOf(item ?: return emptyList(), path.dropLast(1) + (last.first to null))
        return (ends as? Ends.Keys)?.held?.map { it.first }.orEmpty()
    }

    /**
     * Move [by] through whatever fields are in front of the reader.
     *
     * The chosen item's, where the list is what is shown; and where a field is open, the fields
     * of the item inside it, the values of a list, or the rows of a value too long to see at
     * once. One key for one job, wherever it is done.
     *
     * Choosing goes round the ends; scrolling stops at them. A value too long to see is one
     * thing being looked through rather than several being picked between, and running off the
     * bottom of it back to the top would hide that there was no more to read.
     */
    private fun alongFields(by: Int) {
        if (!opened) {
            val many = rows.size
            if (many == 0) return
            chosenField[tab] = (chosenField[tab] + by + many) % many
            return
        }
        val count = entries()
        if (count == null) within = (within + by).coerceAtLeast(0)
        else chosenEntry = if (count == 0) 0 else (chosenEntry + by + count) % count
    }

    /**
     * Open the item the chosen field names, where it names one that is there.
     *
     * **The first one that can be opened**, which for a field naming several is the one the row
     * is showing. A field that is not a reference, a reference to nothing, a plain name asserting
     * no id, and a reference to a type this screen has no tab for are all the same answer: stay
     * where we are.
     *
     * **Following is one way and stays that way.** Nothing records where the reader came from,
     * because nothing needs to: every item is a tab and a few rows away, so a stack would be
     * state kept for a journey nobody has to retrace. `TUI-5`.
     */
    private fun follow() {
        val item = item ?: return
        val steps = if (opened) path else row?.steps ?: return
        // Inside an item the path ends at the item, and what a reader is pointing at is the
        // field the cursor is on. One step further is what they meant.
        val at = endsOf(item, steps)
        val ends = when {
            opened && at is Ends.Within -> at.item.description.fields.getOrNull(chosenEntry)
                ?.let { endsOf(item, steps + (it to null)) }

            else -> at
        } as? Ends.Value ?: return
        // A mention is not a reference field, so it is asked before the field's own kind is.
        val mentions = mentionsOf(ends.field, ends.read, ends.item)
        if (mentions.isNotEmpty()) {
            mentions.getOrNull(if (opened) chosenEntry else 0)?.let { open(it.id) }
            return
        }
        val naming = ends.field as? ReferenceDescription ?: return
        val read = ends.read as? Result.Usable ?: return
        // The cursor picks a value only where the values are what is in front of the reader.
        // Inside an item it picks a field, and which of that field's values to follow is the
        // same question a row asks: the first that can be opened.
        val order: Iterable<Int> =
            if (opened && at is Ends.Value) listOf(chosenEntry) else 0..<howMany(read.value)
        for (at in order) {
            val named = namedAt(read.value, at) ?: continue
            if (open(named.id)) return
        }
    }

    /**
     * Show the item called [id], and say whether there was one to show.
     *
     * Where the user has arrived is a different item, so the way into the last one is not a way
     * into this one and the path is dropped.
     */
    private fun open(id: String): Boolean {
        val target = set[id] ?: return false
        val to = types.indexOf(target.description)
        if (to < 0) return false
        path.clear()
        tab = to
        chosen[tab] = items.indexOf(target)
        chosenField[tab] = 0
        return true
    }

    /** How many values a field holds: one, unless it holds a list of them. */
    private fun howMany(value: Any): Int = if (value is List<*>) value.size else 1

    /**
     * The item named at [at], or absent where nothing is named there.
     *
     * Absent covers a plain name asserting no id and a value that would not read, neither of
     * which names anything to open, and both of which still count as an entry: a cursor moves
     * over what a reader sees, not over what happens to be followable.
     */
    private fun namedAt(value: Any, at: Int): Reference.Identified? = when (value) {
        is Reference.Identified -> if (at == 0) value else null
        is List<*> -> (value.getOrNull(at) as? Element.Usable<*>)?.value as? Reference.Identified
        else -> null
    }

    /**
     * The screen as [height] rows, each exactly [width] characters wide.
     *
     * A tab bar, a rule under it, and then the list on the left against the chosen item's fields
     * on the right. Every row is padded and cut to [width], so what draws this puts down a
     * rectangle and never has to measure anything.
     */
    fun paint(width: Int, height: Int): List<Line> {
        require(width >= LEAST_WIDTH) {
            "a screen should be $LEAST_WIDTH wide at least, was $width"
        }
        require(height >= LEAST_HEIGHT) {
            "a screen should be $LEAST_HEIGHT high at least, was $height"
        }
        val rows = height - CHROME
        val body = when {
            sourcing != null -> sourced(width, rows)
            opened -> opened(width, rows)
            else -> listed(width, rows)
        }
        val lines = ArrayList<Line>(height)
        lines.add(Line(fitted(listOf(Span(where())), width)))
        lines.add(rule(width))
        lines.addAll(body)
        lines.add(rule(width))
        lines.add(Line(fitted(listOf(Span(actions(width))), width)))
        return lines
    }

    private fun rule(width: Int): Line = Line(listOf(Span("-".repeat(width))))

    /** The import screen: where a set of items can come from, and the folder one comes from. */
    private fun sourced(width: Int, rows: Int): List<Line> {
        val at = sourcing ?: 0
        val body = ArrayList<Line>()
        for ((which, name) in SOURCES.withIndex()) {
            val here = if (which == at) setOf(Style.SELECTED) else emptySet()
            body += Line(listOf(Span("  $name", here)))
        }
        editing?.let {
            body += blank()
            body += Line(listOf(Span("  folder: $it$CURSOR", setOf(Style.SELECTED))))
        }
        message?.let {
            body += blank()
            body += Line(listOf(Span("  ! $it")))
        }
        return List(rows) { row -> Line(fitted(body.getOrNull(row)?.spans.orEmpty(), width)) }
    }

    /**
     * A tab per key above what is under the chosen one, where the reader is inside such a field.
     *
     * The same row of names the types get, and the same key moves along it, because it is the
     * same thing: several of a kind, one of them open.
     */
    private fun keyBar(): List<Line> {
        val keys = keysHere()
        if (keys.isEmpty()) return emptyList()
        val here = path.last().second
        // No blank under it: what follows opens with one of its own.
        val said = keys.joinToString("  ") { if (it == here) "[$it]" else " $it " }
        return listOf(Line(listOf(Span(said))))
    }

    /** What is being looked at: the types, or how far into an item a reader has gone. */
    private fun where(): String {
        if (sourcing != null) return "import"
        if (!opened) return tabs()
        val said = path.flatMap { (naming, key) -> listOfNotNull(naming.name, key) }
        return (listOf(type.name, item?.let { idOf(it) }) + said).joinToString(" / ")
    }

    /**
     * What the keys do, as many of them as the screen is wide enough to say.
     *
     * Cut from the end rather than squeezed, because half a word about a key is worse than no
     * word about it, and it stops at the first that will not fit rather than passing over it for
     * a shorter one: a bar that keeps its order is one a reader learns the front of. What a key
     * does depends on what is in front of the user, so this says what it does here.
     *
     * **The order is what a reader cannot work out for themselves.** Leaving leads it, then the
     * keys that change something, then the ones that go somewhere. Anybody presses the arrows
     * without being told to; nobody presses `n`.
     */
    private fun actions(width: Int): String {
        val said = if (sourcing != null) {
            if (editing == null) listOf("[enter] choose", "[esc] back", "$FIELDS source")
            else listOf("[enter] open", "[esc] back", "[backspace] rub out")
        } else if (message != null) {
            listOf("! $message")
        } else if (confirming) {
            // The one place a key needs an answer rather than a label. What it is about is named,
            // since the cursor moved to whatever the list shows after it would be a poor thing to
            // guess from.
            listOf("delete ${item?.let { set.idOf(it) }}?", "[del] delete", "[any key] keep")
        } else if (editing != null) {
            // No quit: `q` is a letter being typed here. Ctrl-C still leaves and is not said,
            // being the one key nobody has to be told about. `TUI-6`.
            // Escape goes back to the choices where that is where the editor was opened from,
            // a step in being undone by a step out.
            val back = if (choosing == null) "[esc] cancel" else "[esc] back"
            val over = editable()?.let { shownSuggestions(it) }.orEmpty()
            listOfNotNull(
                "[enter] save",
                back,
                "[backspace] rub out",
                if (over.isEmpty()) null else "$FIELDS suggestion",
            )
        } else if (choosing != null) {
            listOf("[enter] take it", "[esc] cancel", "$FIELDS choice")
        } else if (opened) {
            // No quit either: `q` is the top level's key, and escape is the way back to it.
            listOfNotNull(
                madeHere()?.let { "[n] new $it" },
                removedHere()?.let { "[del] delete $it" },
                opening(),
                "[space] follow",
                "[esc] back",
                if (keysHere().isEmpty()) null else "$TABS key",
                "$FIELDS " + moving(),
            )
        } else {
            listOfNotNull(
                QUITS,
                if (!chosenIsArriving) null else "[ins] take it in",
                madeHere()?.let { "[n] new $it" },
                if (chosenIsArriving) "[del] leave it out"
                else removedHere()?.let { "[del] delete $it" },
                "[+] import",
                "[enter] open",
                "[space] follow",
                "$TABS type",
                "$ITEMS item",
                "$FIELDS field",
            )
        }
        var shown = said.first()
        for (next in said.drop(1)) {
            if (shown.length + BETWEEN.length + next.length > width) break
            shown += BETWEEN + next
        }
        return shown
    }

    /**
     * The word for what `n` would make where the reader is, or absent where it would make nothing.
     *
     * A field's own name where the thing made is that field's, so the bar says what will appear
     * rather than naming a kind the reader would have to place.
     */
    private fun madeHere(): String? = when (val at = standing()) {
        is Standing.Items -> "item"
        // Not at the place after the last value, where enter already makes one by being typed
        // into. `n` still works there; the bar teaches rather than enumerates.
        is Standing.Entries -> "entry".takeIf { onEntry(at.ends) }
        is Standing.Collection -> "entry"
        is Standing.Empty -> at.ends.field.name
        is Standing.Owned, is Standing.Nowhere -> null
    }

    /** Whether the cursor is on a value of an open list rather than at the place after them. */
    private fun onEntry(ends: Ends.Value): Boolean = chosenEntry < entriesIn(ends).size

    /** The word for what the delete key would take out, or absent where it would take out none. */
    private fun removedHere(): String? = when (val at = standing()) {
        is Standing.Items -> "item".takeIf { items.isNotEmpty() }
        is Standing.Entries -> "entry".takeIf { onEntry(at.ends) }
        is Standing.Collection -> "entry".takeIf { at.at != null }
        is Standing.Owned -> at.field.name
        is Standing.Empty, is Standing.Nowhere -> null
    }

    /**
     * What enter does here, or absent where it does nothing.
     *
     * *New entry* at the place after the last value of a list, since there is nothing there to
     * edit and what is typed becomes a value.
     */
    private fun opening(): String? {
        if (deeper()) return "[enter] open"
        val ends = editable() ?: return null
        val adding = ends.field.cardinality == Cardinality.LIST && !onEntry(ends)
        return if (adding) "[enter] new entry" else "[enter] edit"
    }

    /** What up and down move over where the reader is, in the word for that thing. */
    private fun moving(): String = when (val ends = endsOf(item ?: return "scroll", path)) {
        is Ends.Within -> "field"
        is Ends.Value -> when {
            entries() == null -> "scroll"
            mentionsOf(ends.field, ends.read, ends.item).isNotEmpty() -> "mention"
            else -> "value"
        }
        // A field holding several, holding none: there is nothing to move between.
        is Ends.Keys, null -> "scroll"
    }

    /** Whether there is anywhere further in to go from where the reader is. */
    private fun deeper(): Boolean = endsOf(item ?: return false, path) is Ends.Within

    /** The list of items against the chosen one's fields. */
    private fun listed(width: Int, rows: Int): List<Line> {
        val listWidth = (width / 3).coerceIn(LEAST_LIST_WIDTH, MOST_LIST_WIDTH)
        scrollTo(chosen[tab], rows)
        val list = ids(rows, listWidth)
        val detail = detailed(rows, width - listWidth - 1)
        return List(rows) { row ->
            Line(listOf(Span(list[row].text + " ", list[row].styles)) + detail[row])
        }
    }

    /** What the open field holds, which is what is shown while nothing is being typed. */
    private fun whatIsThere(ends: Ends): List<Line> = when (ends) {
        is Ends.Value -> {
            val field = ends.field
            fieldLines(field, ends.read, ends.item, chosenEntry, universe.suggested(field))
        }
        is Ends.Within -> withinLines(ends.item, chosenEntry)
        // A field holding several, holding none. There is no key to be at.
        is Ends.Keys -> listOf(Line(listOf(Span("  (empty)"))))
    }

    /**
     * What is being typed, with a cursor after it, and why it will not do.
     *
     * The refusal is shown where the value is rather than on the bar, because it is about what
     * is on the screen and because a bar too narrow for it would drop it silently.
     */
    /**
     * The choices, with whatever is being typed on the *other* row, and why it will not do.
     *
     * The refusal goes under the rows for the reason it goes under a typed value: it is about
     * what is on the screen, and a bar too narrow for it would drop it silently.
     */
    private fun chosenLines(ends: Ends.Value, at: Int): List<Line> {
        val offered = choices(ends.field).orEmpty()
        val lines = chooserLines(ends.field, offered, at, editing, otherOf(ends))
        val why = refusal() ?: return lines
        return lines + Line(listOf(Span(""))) + Line(listOf(Span("  ! " + why)))
    }

    private fun typedLines(ends: Ends.Value?): List<Line> {
        val at = suggesting
        val here = if (at == null) setOf(Style.SELECTED) else emptySet()
        val said = Line(listOf(Span("  " + editing.orEmpty() + CURSOR, here)))
        val why = refusal()?.let { listOf(blank(), Line(listOf(Span("  ! " + it)))) }.orEmpty()
        return listOf(said) + why + (ends?.let { offered(it, at) }.orEmpty())
    }

    /**
     * The suggestions under what is being typed, with the one at [at] set apart.
     *
     * How many were left out is said rather than shown, the way a row says it of a list: a
     * column that stopped without a word would read as the whole of what there is.
     */
    private fun offered(ends: Ends.Value, at: Int?): List<Line> {
        val shown = shownSuggestions(ends)
        if (shown.isEmpty()) return emptyList()
        val rest = suggestionsOf(ends).size - shown.size
        val rows = shown.mapIndexed { which, id ->
            val here = if (which == at) setOf(Style.SELECTED) else emptySet()
            Line(listOf(Span("  @" + id, here)))
        }
        return listOf(blank()) + rows +
            if (rest == 0) emptyList() else listOf(Line(listOf(Span("  ($rest others)"))))
    }

    private fun blank(): Line = Line(listOf(Span("")))

    /**
     * The chosen field on its own, whole.
     *
     * Where it came from sits at the top, so that a reader who followed a reference into
     * this knows where they are. A value too long for the screen is wrapped and scrolled
     * rather than cut, this being the one place that shows all of it. What is being typed
     * stands in place of what is there, an editor being a value part-written.
     */
    private fun opened(width: Int, rows: Int): List<Line> {
        val item = item
        val ends = if (item == null) null else endsOf(item, path)
        if (ends == null) return List(rows) { Line(fitted(emptyList(), width)) }
        val at = choosing
        val under = when {
            at != null && ends is Ends.Value -> chosenLines(ends, at)
            editing != null -> typedLines(ends as? Ends.Value)
            else -> whatIsThere(ends)
        }
        val body = (keyBar() + under).flatMap { wrapped(it, width) }
        val cursor = body.indexOfFirst { row -> row.spans.any { Style.SELECTED in it.styles } }
        showing(cursor, rows, body.size)
        return List(rows) { row ->
            val spans = body.getOrNull(within + row)?.spans.orEmpty()
            val here = spans.firstOrNull()?.styles.orEmpty().intersect(setOf(Style.SELECTED))
            Line(fitted(spans, width, here))
        }
    }

    /**
     * Move the window as little as it takes to show the row at [at], and no further than the end.
     *
     * Nothing chosen leaves the window alone, which is what scrolling a single value does.
     */
    private fun showing(at: Int, rows: Int, of: Int) {
        if (at >= 0) {
            if (at < within) within = at
            if (at >= within + rows) within = at - rows + 1
        }
        within = within.coerceIn(0, (of - rows).coerceAtLeast(0))
    }

    /**
     * [line] as as many rows as it takes, each at most [width] wide.
     *
     * **Broken at a space where there is one near the end of the row, and between characters
     * where there is not.** `remarks` is the one field long enough to wrap and it is prose, so
     * breaking it mid-word reads as damage. A value that is not prose — an id, a position, a
     * gas mix — holds no space to break at and falls through to the character break, which is
     * what it wanted anyway.
     *
     * Near the end means the last quarter of the row. A space earlier than that is not a
     * better break than a clean one at the edge; it is a ragged row with a hole in it.
     */
    private fun wrapped(line: Line, width: Int): List<Line> {
        // Flattened whether or not it fits. Measuring the escaped form and painting the
        // unescaped one let a short break through, and the terminal took it: the rest of the
        // value landed at column 0, outside the rectangle this draws. Nothing reaches here
        // holding one now, since a remark is broken into rows before this and every other
        // text is refused a break. The flattening stays as the guarantee rather than the
        // repair: this is what makes a Line one row, and it should not rest on who calls it.
        // Span by span throughout, so that what each one is underlined or set apart for
        // survives the wrap. Collapsing to the first span's styles was easier and lost the
        // underline off any reference long enough to need a second row.
        var rest = line.spans.map { Span(flat(it.text), it.styles) }
        if (widthOf(rest) <= width) return listOf(Line(rest))
        val indent = " ".repeat(
            textOf(rest).takeWhile { it == ' ' }.length.coerceAtMost(width / 4),
        )
        val rows = ArrayList<Line>()
        var room = width
        while (widthOf(rest) > room) {
            val at = breakAt(textOf(rest), room)
            rows.add(Line(trimmed(cut(rest, 0, at))))
            // The space broken at is the break, so it is not carried to the next row.
            val on = cut(rest, at, widthOf(rest))
            val from = textOf(on).indexOfFirst { it != ' ' }.coerceAtLeast(0)
            rest = listOf(Span(indent)) + cut(on, from, widthOf(on))
            room = width
        }
        rows.add(Line(rest))
        return rows
    }

    /** How many characters [spans] paint. */
    private fun widthOf(spans: List<Span>): Int = spans.sumOf { it.text.length }

    /** What [spans] paint, as one piece of text, for finding a place to break. */
    private fun textOf(spans: List<Span>): String = spans.joinToString("") { it.text }

    /**
     * The characters of [spans] from [from] up to [to], each keeping its own styles.
     *
     * A break falls wherever the text allows and takes no notice of where one span ends, so a
     * span straddling it is cut and both halves keep what it was.
     */
    private fun cut(spans: List<Span>, from: Int, to: Int): List<Span> {
        val taken = ArrayList<Span>()
        var at = 0
        for (span in spans) {
            val starts = maxOf(from, at)
            val ends = minOf(to, at + span.text.length)
            if (ends > starts) {
                taken.add(Span(span.text.substring(starts - at, ends - at), span.styles))
            }
            at += span.text.length
        }
        return taken
    }

    /** [spans] with the trailing spaces off the last of them, a row not ending in a break. */
    private fun trimmed(spans: List<Span>): List<Span> {
        val last = spans.lastOrNull() ?: return spans
        val kept = last.text.trimEnd()
        if (kept == last.text) return spans
        val rest = spans.dropLast(1)
        return if (kept.isEmpty()) rest else rest + Span(kept, last.styles)
    }

    /**
     * How much of [text] the next row of [room] characters takes.
     *
     * The last space in the final quarter, or the whole row where there is none. Measured from
     * one past the row so that a space landing exactly at the edge counts as the break rather
     * than as the first character of the next row.
     */
    private fun breakAt(text: String, room: Int): Int {
        val edge = text.take(room + 1)
        val space = edge.lastIndexOf(' ')
        return if (space > room - room / 4) space else room
    }

    /** The tab bar: every type in order, the open one in brackets. */
    private fun tabs(): String =
        types.withIndex().joinToString("  ") { (at, type) ->
            if (at == tab) "[${type.name}]" else " ${type.name} "
        }

    /** The ids of the open type, the chosen one marked, as many as fit. */
    private fun ids(rows: Int, width: Int): List<Span> {
        val ids = items.map { idOf(it) ?: "?" }
        val many = arriving().size
        return List(rows) { row ->
            val at = first[tab] + row
            val text = when {
                ids.isEmpty() && row == 0 -> "  (no ${type.name})"
                at >= ids.size -> ""
                at == chosen[tab] -> "> ${ids[at]}"
                else -> "  ${ids[at]}"
            }
            // Italic where it is not in the logbook yet, which is what it means on a value too:
            // a row set in italic is one this logbook does not hold as written.
            val here = if (at < many && at < ids.size) setOf(Style.ITALIC) else emptySet()
            Span(fit(flat(text), width), here)
        }
    }

    /**
     * The chosen item's fields, and the fields of anything inside them.
     *
     * A field with nothing in it is still listed, because what a type *can* hold is half of what
     * this interface is for. The chosen one is set apart across its own row, so that the cursor
     * reads as a bar rather than as another mark like the list's; only that row and none of the
     * rows under it, since a whole item reversed is a wall rather than a cursor.
     *
     * Values line up in one column however deep their names sit, which is what lets a reader run
     * an eye down them.
     */
    private fun detailed(rows: Int, width: Int): List<List<Span>> {
        val all = this.rows
        if (all.isEmpty()) return List(rows) { fitted(emptyList(), width) }
        val labelWidth = all.maxOf { it.indent * STEP + it.label.length }
        scrollFields(all, rows)
        return List(rows) { row ->
            val at = deep[tab] + row
            // Marked by where it sits rather than by which object it is: the rows are worked out
            // afresh each time they are asked for, so no two calls give back the same one.
            val here = if (at == chosenField[tab]) setOf(Style.SELECTED) else emptySet()
            val there = all.getOrNull(at)
            if (there == null) fitted(emptyList(), width)
            else fitted(spansOf(there, labelWidth, here), width, here)
        }
    }

    /** One row: its name where its depth puts it, and what it holds in the value column. */
    private fun spansOf(row: Row, labelWidth: Int, here: Set<Style>): List<Span> {
        val name = " ".repeat(row.indent * STEP) + row.label
        return listOf(Span(name.padEnd(labelWidth) + "  ", here)) +
            row.value.map { Span(it.text, it.styles + here) }
    }

    /** Move the column as little as it takes to show the chosen row. */
    private fun scrollFields(all: List<Row>, rows: Int) {
        val at = chosenField[tab]
        if (at >= 0) {
            if (at < deep[tab]) deep[tab] = at
            if (at >= deep[tab] + rows) deep[tab] = at - rows + 1
        }
        deep[tab] = deep[tab].coerceIn(0, (all.size - rows).coerceAtLeast(0))
    }

    /** Move the window as little as the chosen row allows, so the list stays where it was. */
    private fun scrollTo(row: Int, rows: Int) {
        if (row < first[tab]) first[tab] = row
        if (row >= first[tab] + rows) first[tab] = row - rows + 1
        first[tab] = first[tab].coerceIn(0, (items.size - rows).coerceAtLeast(0))
    }

    /**
     * [spans] cut and padded to exactly [width] characters, the padding carrying [fill].
     *
     * The padding is styled with the rest of its row so that a chosen field reads as a bar across
     * the screen rather than stopping where its value happens to end.
     */
    private fun fitted(spans: List<Span>, width: Int, fill: Set<Style> = emptySet()): List<Span> {
        val cut = ArrayList<Span>(spans.size + 1)
        var left = width
        for (span in spans) {
            if (left == 0) break
            cut.add(if (span.text.length <= left) span else Span(span.text.take(left), span.styles))
            left -= cut.last().text.length
        }
        if (left > 0) cut.add(Span(" ".repeat(left), fill))
        return cut
    }

    private fun fit(text: String, width: Int): String =
        if (text.length > width) text.take(width) else text.padEnd(width)

    companion object {

        /** Narrower than this and the list and the fields have nothing to stand in. */
        const val LEAST_WIDTH: Int = 20

        /** The rows that are not body: where the reader is and a rule, a rule and the keys. */
        private const val CHROME = 4

        /** Where the reader is, a rule, one row of body, a rule, and what the keys do. */
        const val LEAST_HEIGHT: Int = CHROME + 1

        /** What stands between one key and the next in the bar. */
        private const val BETWEEN = " | "

        /**
         * The keys the bar names, both directions in one: a key that only goes forwards is
         * half a key.
         *
         * The arrows are drawn rather than spelled, which is what their key caps show, and no
         * arrow character survives a console on code page 437. `<` and `^` are what is left.
         */
        private const val TABS = "[(shift)-tab]"

        private const val ITEMS = "[<,>]"

        private const val FIELDS = "[^,v]"

        /**
         * Leaving, which is said first in both bars.
         *
         * The one key a reader wants without hunting is the one that gets them out, so it is
         * the one thing a screen too narrow for anything else still says. Ctrl-C leaves too and
         * is not named: a bar has room for the key somebody would look for.
         */
        private const val QUITS = "[q] quit"

        /** What a line break is shown as, which is how a file writes one. */
        // Enough to recognise the one wanted, and few enough to leave the typed line in view
        // on a short terminal. What is past them is reached by typing more of the id.
        // The two an import can come from. Only the first is built; `FEAT-3` is the other.
        private val SOURCES = listOf("another logbook", "a dive computer")

        private const val NO_DEVICE = "reading a dive computer is not built yet. FEAT-3."

        private const val MOST_SUGGESTIONS = 8

        private const val ESCAPED = "\\n"

        private const val LEAST_LIST_WIDTH = 12

        private const val MOST_LIST_WIDTH = 40
    }
}

/**
 * Standing is what the reader is on, in the terms that making and removing one need.
 *
 * A place rather than a thing: [Items] is the list of items, [Entries] an open list of values,
 * and [Collection] a keyed field whether the reader is at its keys or inside one of its entries.
 * [Screen] classifies once, and both the keys and the bar work from the answer.
 */
private sealed class Standing {

    /** The list of items of the open type. */
    object Items : Standing()

    /** An open list of values. */
    class Entries(val ends: Ends.Value) : Standing()

    /** A field that would hold one owned item and holds none. */
    class Empty(val ends: Ends.Value) : Standing()

    /**
     * A keyed collection of owned items.
     *
     * [at] is the key the reader is inside. It is absent at the keys themselves, which is where a
     * collection holding nothing leaves them.
     */
    class Collection(
        val field: FieldDescription,
        val owner: Item,
        val held: List<Pair<String, Any>>,
        val at: String?,
    ) : Standing()

    /** Inside the one owned item a field holds. */
    class Owned(val field: OwnedItemDescription, val owner: Item) : Standing()

    /** Somewhere neither key does anything. */
    object Nowhere : Standing()
}
