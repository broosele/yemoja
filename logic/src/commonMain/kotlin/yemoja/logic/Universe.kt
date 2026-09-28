package yemoja.logic

import yemoja.data.OwnedItem
import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.data.Units
import yemoja.data.json.DiskFileStore
import yemoja.data.json.FileStore
import yemoja.data.json.Json
import yemoja.data.json.LogbookFormatException
import yemoja.data.json.LogbookReader
import yemoja.data.json.LogbookWriter
import yemoja.logic.divecomputer.DiveComputer
import yemoja.logic.divecomputer.Devices
import yemoja.logic.divecomputer.Download
import yemoja.logic.divecomputer.Session
import yemoja.logic.uddf.Exported
import yemoja.logic.uddf.Uddf
import yemoja.logic.uddf.UddfFormatException

/*
 * The application's working state, and the one door a front end reaches anything through.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md, under "The Universe".
 */

/**
 * Universe is what is open: the logbook, and in time whatever else is being worked on.
 *
 * `ui/doc.md` holds every front end to reaching everything through this, so a front end names a
 * folder and never opens one. [open] is the only place in this layer that names a source;
 * everything else takes an [ItemSet] and runs against items assembled in memory.
 *
 * **An item set is reached through it, not wrapped by it.** [logbook] is handed over whole, so
 * nothing here forwards `get` or `allOf`, and there is nothing to drift out of step with what it
 * forwards.
 *
 * **It delegates and implements nothing**, and so far there is nothing to delegate to. Statistics,
 * decompression and the domain rules are each a service of their own, unbuilt; when they arrive
 * this hands work to them rather than doing any. The smallness is the shape and not a stage of it.
 *
 * Absent so far: an import's candidate set, which is the second thing this is meant to hold and
 * waits on `RECON-2`; the units a user wants shown, which `UI-2` settles as belonging here and
 * which needs somewhere for settings to be read from; and everything computed.
 *
 * Not immutable: a logbook gains items while it is open, and [change] is how.
 */
class Universe(
    val logbook: ItemSet,
    owner: ReferenceableItem?,
    private val store: FileStore,
    /** The folder this was opened from, or absent where it was not opened from one. */
    val path: String? = null,
    /**
     * What can look for a dive computer, or absent where nothing on this platform can.
     *
     * Handed in rather than reached for: finding a device needs a native library and platform
     * Bluetooth, which this layer declares a port for and does not implement. `LOGIC-2`.
     */
    private val devices: Devices? = null,
    /**
     * Where a review is staged, or absent where it is worked out from [path].
     *
     * A logbook opened from a folder stages beside it. One opened from nowhere has nowhere to
     * stage and says so rather than writing a folder wherever it happens to be running.
     */
    private val stagingStore: FileStore? = null,
    /**
     * Where an agent's changes are staged, or absent where they are staged beside the logbook.
     *
     * Its own rather than [stagingStore]'s: an import and a change are two things pending at once,
     * each with a folder of its own. `RECON-8`.
     */
    private val proposing: FileStore? = null,
) {

    /**
     * A number that changes whenever anything in this universe does.
     *
     * `ItemSet` carries one of its own and it counts a narrower thing: an item added or taken
     * out, not a field edited. A view has to refresh for both — a dive whose date was corrected
     * moves in a list ordered by date without the count changing — so this counts every change.
     *
     * Not notification. `DATA-6` settles that nothing is announced and nothing subscribes; this
     * is what lets something that may be out of date ask.
     */
    var revision: Int = 0
        private set

    /** The logbook's owner, or absent where the manifest names nobody it holds. `JSON-22`. */
    var user: ReferenceableItem? = owner
        private set

    /**
     * Reads the logbook's files again, replacing what is held with what is on disk.
     *
     * **For a change made by another hand.** Everything of the window's own goes through [change]
     * and is already in memory; this is for an agent allowed to edit the files directly, whose
     * edits nothing here saw. Without it the window shows what was, and the next change it saves
     * writes an item from memory over the file, losing the edit. `API-5`.
     *
     * A file that will not read refuses the whole reload and leaves everything as it was, so a
     * half-edited logbook does not replace a whole one. The revision moves whether or not anything
     * differed, since what was read is not compared with what was held.
     */
    fun reload(): Outcome {
        val manifest = LogbookReader.manifest(store)
        val fresh = try {
            LogbookReader.read(store, logbook.descriptions, manifest)
        } catch (refused: RuntimeException) {
            return Outcome.Refused("the logbook could not be read again: ${refused.message}")
        }
        for (description in logbook.descriptions) {
            for (item in logbook.allOf(description)) logbook.idOf(item)?.let { logbook.remove(it) }
        }
        for (description in fresh.descriptions) {
            for (item in fresh.allOf(description)) fresh.idOf(item)?.let { logbook.add(it, item) }
        }
        val named = manifest.user?.let { logbook[it.id] }
        user = if (named?.description == Types.PERSON) named else null
        revision += 1
        return Outcome.Done()
    }

    /**
     * Does [changes] as one [operation], and saves whatever it touched.
     *
     * **The one way anything changes.** Nothing above calls a mutator on an item, and the reason
     * is not tidiness: when `FEAT-4` arrives a changeset has to be recorded for every change, and
     * a front end reaching past this would leave nothing to record it from. The shape here is the
     * changeset's own — an operation, and the actions that carried it out — so the journal is a
     * writer added beside this rather than a rewrite of everything that edits.
     *
     * **It lands whole or not at all.** Every part is judged before any is applied, so a refusal
     * leaves the logbook as it was. What is not guaranteed is the files: a change touching two of
     * them can be interrupted between them, which `JSON-25` defers.
     *
     * **Saving is immediate.** There is no unsaved state and no save to forget, which is what
     * makes every change the journal's unit rather than only the ones somebody remembered.
     *
     * Nothing is announced. Whatever is showing the logbook asks again. `DATA-6`.
     */
    fun change(operation: Operation, vararg changes: Change): Outcome {
        val writes = ArrayList<Pair<Change.Write, Result<Any>>>()
        val adds = ArrayList<Pair<String, ReferenceableItem>>()
        for (change in changes) {
            when (change) {
                is Change.Write -> {
                    val made = change.item.prepared(change.field, change.given, Units.DEFAULT)
                    if (made is Result.Unusable) return Outcome.Refused(made.reason)
                    writes += change to made
                }

                is Change.Add -> {
                    val item =
                        ItemReader.read(change.description, nothing(), logbook, Units.DEFAULT)
                    for ((name, given) in change.fields) {
                        val made = item.prepared(name, given, Units.DEFAULT)
                        if (made is Result.Unusable) return Outcome.Refused(made.reason)
                        item.apply(name, made)
                    }
                    // Taken now rather than when it lands, so two additions in one change cannot
                    // both be given the same id: the ones already minted are counted as taken.
                    val minted = adds.map { it.first }
                    val carried = change.id
                    if (carried != null && (logbook[carried] != null || carried in minted)) {
                        return Outcome.Refused(
                            "$carried is taken, so nothing can be added under it",
                        )
                    }
                    adds += (carried ?: freeId(item, minted)) to item
                }

                is Change.Delete -> Unit
            }
        }
        // A dive given its second profile names the one it had as primary, in the same change.
        // Added to what the change did rather than done beside it, so a journal records it too.
        val done = ArrayList<Change>(changes.asList())
        val written = writes.map { (change, _) -> change.item to change.field }.toSet()
        for ((change, made) in writes.toList()) {
            val kept = primaryKeptBy(change, made, written) ?: continue
            val keptMade = kept.item.prepared(kept.field, kept.given, Units.DEFAULT)
            if (keptMade is Result.Unusable) return Outcome.Refused(keptMade.reason)
            writes += kept to keptMade
            done += kept
        }

        for ((change, made) in writes) change.item.apply(change.field, made)
        for ((id, item) in adds) logbook.add(id, item)

        val touched = LinkedHashSet<Pair<ItemDescription, String>>()
        for ((id, item) in adds) touched += item.description to id
        for (change in done) {
            when (change) {
                is Change.Write -> {
                    val owner = ownerOf(change.item) as ReferenceableItem
                    logbook.idOf(owner)?.let { touched += owner.description to it }
                }

                is Change.Delete -> {
                    val going = logbook[change.id] ?: continue
                    val description = going.description
                    if (change.alsoReferences) touched += clearedOf(change.id)
                    logbook.remove(change.id)
                    touched.remove(description to change.id)
                    LogbookWriter.delete(store, description, change.id)
                }

                is Change.Add -> Unit
            }
        }
        for ((description, id) in touched) {
            logbook[id]?.let { LogbookWriter.write(store, description, id, it) }
        }
        revision += 1
        return Outcome.Done(adds.map { it.first })
    }

    /**
     * The first id free for [item], from what its type proposes.
     *
     * A proposal is not an answer: two items may propose the same thing and neither knows what is
     * already there. Where it is taken the index moves — `2026-04-28#0` to `#1`, `anna_devries` to
     * `anna_devries#1` — which is one rule for both, since a dive proposes an index and everything
     * else proposes none.
     *
     * **The lowest free index, so a deleted item's id comes back.** An id may be reissued, which
     * `DATA-84` once forbade: deleting a dive entered wrongly and entering it again should heal
     * the references to it rather than leave them dangling for ever. What that costs is that a
     * reference to something deleted can come to name something else.
     */
    private fun freeId(item: ReferenceableItem, minted: List<String>): String {
        val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
        return freeName(proposed) { logbook[it] != null || it in minted }
    }

    /**
     * Clears every reference naming [id], and says which items were changed.
     *
     * Only references. A mention in free text is prose, and `JSON-23` gives it no fixed meaning,
     * so removing one would be editing what somebody wrote.
     */
    private fun clearedOf(id: String): List<Pair<ItemDescription, String>> {
        val changed = ArrayList<Pair<ItemDescription, String>>()
        for (description in logbook.descriptions) {
            for (item in logbook.allOf(description)) {
                if (!clearIn(item, id)) continue
                logbook.idOf(item)?.let { changed += description to it }
            }
        }
        return changed
    }

    /**
     * Takes [id] out of every reference field of [item], and says whether anything moved.
     *
     * **Into the owned items too.** Most of a dive's references are not the dive's own fields:
     * the trip and the operator are in `details`, the equipment in `gear`, the cylinder in a gas
     * source, the computer in a profile. Clearing only the top level would leave most of what
     * named a deleted item still naming it, which is worse than not offering to clear at all.
     */
    private fun clearIn(item: Item, id: String): Boolean {
        var moved = false
        for (field in item.description.fields) {
            if (field is OwnedItemDescription) {
                for (owned in ownedIn(item, field)) if (clearIn(owned, id)) moved = true
                continue
            }
            if (field !is ReferenceDescription) continue
            when (val read = item.read(field.name)) {
                // A derived reference is worked out afresh every time it is read, so it cannot
                // dangle: a site's `dives` follows from the dives naming it and answers without
                // the deleted one the moment it is gone. Clearing it would store what it works
                // out, and a stored value on a derived field shadows the working out for good.
                is Result.Usable -> if (read.origin == Result.Origin.DERIVED) {
                    Unit
                } else {
                    if (field.cardinality == Cardinality.LIST) {
                        @Suppress("UNCHECKED_CAST")
                        val held = read.value as List<Element<Any>>
                        val kept = held.filterNot { names(it, id) }
                        if (kept.size != held.size) {
                            item.apply(field.name, Result.Usable(kept, read.origin))
                            moved = true
                        }
                    } else if (names(Element.Usable(read.value), id)) {
                        item.apply(field.name, Result.Absent)
                        moved = true
                    }
                }

                else -> Unit
            }
        }
        return moved
    }

    /** Every owned item [field] holds on [item], however many it holds. */
    private fun ownedIn(item: Item, field: OwnedItemDescription): List<Item> {
        val read = item.read(field.name) as? Result.Usable ?: return emptyList()
        return when (val held = read.value) {
            is OwnedItem -> listOf(held)
            is Map<*, *> -> held.values.mapNotNull { (it as? Element.Usable<*>)?.value as? Item }
            is List<*> -> held.mapNotNull { (it as? Element.Usable<*>)?.value as? Item }
            else -> emptyList()
        }
    }

    private fun names(held: Element<Any>, id: String): Boolean =
        held is Element.Usable && (held.value as? Reference.Identified)?.id == id

    /**
     * The import being reviewed, or absent where none is.
     *
     * Held here because `ui/doc.md` holds every front end to reaching everything through the
     * Universe, and an import is something being worked on rather than something a screen owns.
     */
    var importing: Import? = null
        private set

    /**
     * Stage [source] in [staging], to be reviewed and taken in.
     *
     * [matching] is the source's to say, and saying it wrong loses data: an id minted on the way
     * in names what this model would have called such an item rather than which item it is.
     */
    fun importFrom(
        source: ItemSet,
        staging: FileStore,
        matching: Matching = Matching.BY_ID,
    ) {
        importing = Import.begin(source, staging, this, matching)
    }

    /**
     * Writes this logbook to the file at [to] as a UDDF document, and says what went.
     *
     * The whole logbook, and nothing asked about it first: an export changes nothing here, so
     * there is nothing to review. A file already at [to] is written over, the reader having named
     * it. What UDDF has no place for is left out as `uddf.md` sets out, and the one loss worth
     * saying aloud, a dive's other recordings, is counted on what comes back.
     *
     * Throws where the file cannot be written, which is a fault of the place rather than of the
     * logbook.
     */
    fun exportTo(to: String): Exported {
        val exported = Uddf.write(logbook)
        val cut = maxOf(to.lastIndexOf('/'), to.lastIndexOf('\\'))
        val folder = if (cut < 0) "." else to.substring(0, cut).ifEmpty { "/" }
        DiskFileStore(folder).writeText(to.substring(cut + 1), exported.text)
        return exported
    }

    /**
     * Stage whatever is at [from], beside this logbook.
     *
     * **What is there says how it is read.** A folder is another Yemoja logbook and a file is a
     * UDDF document, which is one question fewer to put to somebody who already knows what they
     * are pointing at. Nothing after that differs: both arrive as a set of items and both are
     * reviewed the same way.
     *
     * The staging folder is this logbook's own with `.import` after it, which puts it outside the
     * logbook: what is being reviewed is not part of it and must not be read as though it were.
     * A logbook opened from nowhere in particular stages beside the source instead.
     */
    fun importFrom(from: String): Outcome {
        val store = DiskFileStore(from)
        val where = stagedIn(from)
            ?: return Outcome.Refused("this logbook has nowhere to stage an import")
        if (store.isFolder("")) {
            val source = try {
                LogbookReader.read(store, logbook.descriptions)
            } catch (refused: LogbookFormatException) {
                return Outcome.Refused(refused.message ?: "$from could not be read")
            }
            importFrom(source, where)
            return Outcome.Done()
        }
        if (!store.isFile("")) {
            return Outcome.Refused("$from should be a folder or a file, and is neither")
        }
        val source = try {
            Uddf.read(store.readText(""))
        } catch (refused: UddfFormatException) {
            return Outcome.Refused("$from should be UDDF: ${refused.message}")
        }
        // A file holding no dive would open a review of the items around one and no dive to
        // hang them on, with no word about why.
        if (source.allOf(Types.DIVE).isEmpty()) {
            return Outcome.Refused("$from holds no dives, and dives are what is read from UDDF")
        }
        // Nothing matches: a UDDF file carries no ids of ours, so the ones its dives have were
        // minted while reading it and say nothing about which dive is which.
        importFrom(source, where, Matching.NONE)
        return Outcome.Done()
    }

    /** Every dive computer this machine can reach now, or none where nothing can look. */
    fun attached(): List<DiveComputer> = devices?.found().orEmpty()

    /**
     * Whether a dive computer can be read here at all, whatever is within reach.
     *
     * False where nothing was given to look with, and false where what does the reading is not
     * on this machine. Both mean the same to a reader: not here, not ever, until something is
     * installed. `LOGIC-28`.
     */
    val readable: Boolean get() = devices?.readable == true

    /**
     * Stage what [computer] holds that this logbook has not seen.
     *
     * **It resumes.** The newest recording that computer made carries the token the device knows
     * it by, and handing that back means only later dives are transferred at all. `DATA-90`. A
     * logbook that has none of its tokens gets everything, which is slow rather than wrong.
     *
     * Nothing is matched by id: a downloaded dive is named by this model on the way in, so its
     * id says what such a dive would be called rather than which dive it is. What a re-download
     * does bring across is proposed against what overlaps it in time.
     */
    fun downloadFrom(computer: DiveComputer, ask: (String) -> String? = { null }): Outcome {
        val where = stagedIn(null)
            ?: return Outcome.Refused("this logbook has nowhere to stage a download")
        val session = Pairing(computer.name, ask)
        val read = Download.read(computer.recordings(session), logbook)
        // Kept once the device has said its serial, which is what says whose it is. `LOGIC-24`.
        val handed = session.kept
        val serial = session.serial
        if (handed != null && serial != null) keepAccessCode(serial, handed)
        if (read.allOf(Types.DIVE).isEmpty()) {
            return Outcome.Refused("${computer.name} holds no dives this logbook has not seen")
        }
        importFrom(read, where, Matching.NONE)
        return Outcome.Done()
    }

    /**
     * What a download asks while it runs, answered from this logbook. `LOGIC-23`, `LOGIC-24`.
     *
     * [ask] puts a question to the user, which only a front end can, and answers nothing where
     * none can: the download is then given up rather than read wrongly.
     */
    private inner class Pairing(
        private val called: String,
        private val ask: (String) -> String?,
    ) : Session {

        /** The serial, once the device has said it. */
        var serial: String? = null

        /** An access code the device handed over, waiting for the serial to say whose it is. */
        var kept: ByteArray? = null

        override fun resume(serial: String?): String? {
            if (serial != null) this.serial = serial
            return Download.after(logbook, called, serial)
        }

        override fun accessCode(name: String): ByteArray? {
            val gear = computerAdvertising(name) ?: return null
            return bytesOf((gear.single<String>("access_code") as? Result.Usable)?.value)
        }

        override fun pin(name: String): String? = ask("type the code $name is showing:")

        override fun keep(name: String, accessCode: ByteArray) {
            kept = accessCode
        }
    }

    /** The gear item carrying [serial], or absent where none does. */
    private fun computerWith(serial: String): ReferenceableItem? =
        logbook.allOf(Types.GEAR).firstOrNull { gear ->
            val held = (gear.single<String>("serial") as? Result.Usable)?.value
            held != null && sameSerial(held, serial)
        }

    /**
     * The gear item the device advertising as [name] is, or absent where none can be told.
     *
     * Asked before the device has said its serial, so by the name: the gear item whose serial
     * the name, or the digits in it, spells. `LOGIC-24`.
     */
    private fun computerAdvertising(name: String): ReferenceableItem? {
        computerWith(name)?.let { return it }
        val digits = name.filter { it.isDigit() }
        return if (digits.isEmpty()) null else computerWith(digits)
    }

    /** Put [accessCode] on the gear item carrying [serial], where one does. `LOGIC-24`. */
    private fun keepAccessCode(serial: String, accessCode: ByteArray) {
        val gear = computerWith(serial) ?: return
        val written = Stored.Leaf(hexOf(accessCode))
        change(Operation.DOWNLOAD, Change.Write(gear, "access_code", written))
    }

    /**
     * Where to stage a review of what came from [source], or absent where there is nowhere.
     *
     * This logbook's own folder with `.import` after it, which puts it outside the logbook: what
     * is being reviewed is not part of it and must not be read as though it were. A logbook
     * opened from nowhere stages beside the source instead, and a download has no source folder
     * to stage beside.
     */
    private fun stagedIn(source: String?): FileStore? {
        stagingStore?.let { return it }
        return (path ?: source)?.let { DiskFileStore("$it.import") }
    }

    /** Put the review down, leaving whatever is staged where it is. */
    fun stopImporting() {
        importing = null
    }

    /**
     * What an agent has staged for this logbook, or absent where it has nowhere to stage it.
     *
     * Held here for the reason an import is: it is something being worked on rather than something
     * a screen owns, and the review of it reaches everything through this. Made on first asking
     * and kept, so the panel and the review are looking at one thing. A logbook opened from
     * nowhere has nowhere beside it and gets none. `RECON-8`.
     */
    val staging: Staging? by lazy {
        val where = proposing ?: path?.let { DiskFileStore(it + Staging.BESIDE) }
        where?.let { Staging.open(this, it) }
    }

    /**
     * What the user chose, from the two settings files beside the logbook and then the defaults.
     *
     * Here rather than in a front end, so that every front end asks one object and none of them can
     * disagree about what the user chose. `UI-2`.
     */
    val settings: Settings by lazy { Settings(store) }

    /**
     * The values [field] suggests: what it ships with, and what this logbook already uses.
     *
     * `DATA-25` writes the presets on the description and leaves the joining to the Universe,
     * which is here because only the Universe knows what is loaded. Empty for a field that
     * suggests nothing, a fixed set being closed rather than suggested.
     *
     * **The presets keep the order they were declared in.** `none, light, moderate, strong` is a
     * scale somebody chose, and sorting it says `light, moderate, none, strong`. What the logbook
     * has added follows, alphabetically, there being no order of its own to keep.
     */
    fun suggested(field: FieldDescription): List<String> {
        val presets = (field as? TextDescription)?.suggestedSet ?: return emptyList()
        return presets.toList() + (used()[field].orEmpty() - presets).sorted()
    }

    // Every value in use, by the field holding it, and the revision it was gathered at.
    private var using = HashMap<FieldDescription, MutableSet<String>>()

    private var gathered = -1

    /**
     * Every value in use, worked out once per change.
     *
     * One walk of the logbook rather than one per field. Most fields that suggest anything sit on
     * an owned item — a profile's model, a maintenance's type — so answering for one of them
     * costs the same walk as answering for all of them.
     *
     * Values pool by field rather than by vocabulary. `type` and `follow_up_type` on a
     * maintenance ship with the same words and are still two fields, so a word typed into one is
     * not offered for the other.
     */
    private fun used(): Map<FieldDescription, Set<String>> {
        if (gathered != revision) {
            using = HashMap()
            for (description in logbook.descriptions) {
                for (item in logbook.allOf(description)) gather(item)
            }
            gathered = revision
        }
        return using
    }

    /** Collect what [item] holds in every field that suggests values, and go into what it owns. */
    private fun gather(item: Item) {
        for (field in item.description.fields) {
            val suggesting = field is TextDescription && field.suggestedSet != null
            if (!suggesting && field !is OwnedItemDescription) continue
            val read = item.read(field.name)
            if (read !is Result.Usable) continue
            for (value in valuesIn(read.value)) {
                when {
                    suggesting && value is String -> using.getOrPut(field) { HashSet() }.add(value)
                    value is Item -> gather(value)
                }
            }
        }
    }

    /**
     * What a field holds, one value at a time, whatever its cardinality.
     *
     * A series is not gone into. It arrives whole and holds numbers, and no field of one
     * suggests anything.
     */
    private fun valuesIn(held: Any): List<Any> = when (held) {
        is List<*> -> held.mapNotNull { (it as? Element.Usable<*>)?.value }
        is Map<*, *> -> held.values.mapNotNull { (it as? Element.Usable<*>)?.value }
        else -> listOf(held)
    }

    companion object {

        /**
         * The logbook in the folder at [path], with the libraries it declares.
         *
         * The owner is whoever `yemoja.json` names, and is absent both where it names nobody and
         * where it names an item this logbook does not hold — a reference that resolves to
         * nothing is a dangling one, not a reason to refuse the logbook. `JSON-22`.
         */
        fun open(path: String, devices: Devices? = null): Universe {
            val store = DiskFileStore(path)
            // A folder that is not there answers every question with no, so without this a
            // mistyped path opens as an empty logbook rather than as a mistake.
            require(store.isFolder("")) { "$path should be a folder, and is not" }
            val manifest = LogbookReader.manifest(store)
            val items = LogbookReader.read(store, Types.ALL, manifest)
            val user = manifest.user?.let { items[it.id] }
            val owner = if (user?.description == Types.PERSON) user else null
            return Universe(items, owner, store, path, devices)
        }

        /**
         * A new logbook in the folder at [path], made and then opened.
         *
         * The folder is made where it is not there, writing the manifest making it. A folder
         * that already holds one is refused: that is a logbook, and opening one is a different
         * thing from making one. Whatever else is in the folder is left alone, a logbook being
         * a folder somebody may keep other things in.
         *
         * **It declares every library the application ships**, so that a new logbook knows the
         * world's regions and the agencies' certifications without anybody editing a file. A
         * user wanting fewer prunes the list, which is what the list is for. `LOGIC-26`.
         *
         * It names no owner. Which of a logbook's people is the user is a thing to say once
         * there are people, and a new logbook has none.
         */
        fun create(path: String, devices: Devices? = null): Universe {
            val store = DiskFileStore(path)
            require(!store.isFile(LogbookReader.MANIFEST)) {
                "$path already holds a logbook, and making one would write over it"
            }
            val declared = shippedIn(store).mapValues { (_, held) ->
                Stored.Elements(held.map { Stored.Leaf(it) })
            }
            val manifest = Stored.Members(mapOf(LIBRARIES to Stored.Members(declared)))
            store.writeText(LogbookReader.MANIFEST, Json.write(manifest) + "\n")
            return open(path, devices)
        }

        /**
         * Every library the application ships, by the type it holds.
         *
         * A folder under the library root named after a type holds libraries of that type, and
         * each file in it is one. That is the whole of the convention, so nothing here lists
         * what ships; a library file sitting loose rather than in such a folder cannot say what
         * type it holds and is not declared. `LOGIC-26`.
         */
        private fun shippedIn(store: FileStore): Map<String, List<String>> {
            val shipped = LinkedHashMap<String, List<String>>()
            for (type in Types.ALL) {
                val folder = "${FileStore.LIBRARIES}/${type.name}"
                if (!store.isFolder(folder)) continue
                val held = store.namesIn(folder)
                    .filter { it.endsWith(SUFFIX) }
                    .sorted()
                    .map { "${type.name}/${it.removeSuffix(SUFFIX)}" }
                if (held.isNotEmpty()) shipped[type.name] = held
            }
            return shipped
        }
    }
}

/** The key a manifest groups its libraries under. */
private const val LIBRARIES = "libraries"

/** What a library file is called. */
private const val SUFFIX = ".json"
