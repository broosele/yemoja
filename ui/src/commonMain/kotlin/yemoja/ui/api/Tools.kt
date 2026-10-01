package yemoja.ui.api

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.TextDescription
import yemoja.data.TimeDescription
import yemoja.data.Units
import yemoja.data.WholeNumberDescription
import yemoja.data.inOrder
import yemoja.data.json.Json
import yemoja.logic.Calculated
import yemoja.logic.Figured
import yemoja.logic.Measure
import yemoja.logic.Outcome
import yemoja.logic.Read
import yemoja.logic.Staging
import yemoja.logic.Types
import yemoja.ui.gui.PROFILES
import yemoja.logic.Universe
import yemoja.logic.calculated
import yemoja.logic.casesOf
import yemoja.logic.fieldAt
import yemoja.logic.figureOf
import yemoja.logic.saidOf

/*
 * What an agent can ask of a logbook, before any protocol carries the asking.
 *
 * See ../../../../../../api/doc.md — `API-4` and `API-5`.
 */

/**
 * Reply is what one tool answered: JSON for the agent to read, and whether it is a refusal.
 *
 * Every reply carries the Universe's revision, so an agent can tell that what it read earlier may
 * be out of date. `API-4`.
 *
 * Immutable.
 */
data class Reply(val text: String, val refused: Boolean = false)

/**
 * Tools is what an agent is given: `describe`, `list`, `get`, `series` and `aggregate` to read
 * with, and `stage_set`, `stage_add`, `stage_delete` and `staged` to propose a change with.
 *
 * **Nothing here changes the logbook.** A write tool stages, and what is staged happens only when
 * somebody looks at it and applies it. `RECON-8`.
 *
 * [writing] is asked on every call rather than once, because the user may tick or untick the box
 * while a conversation is under way. `API-5`.
 *
 * **Nothing is held back from an agent.** Everything the logbook holds about a person — an
 * address, a telephone number, a medical — is sent like anything else, and the user is warned of
 * that rather than protected from it. `API-5`.
 *
 * **Not safe to call from two threads.** The Universe is not, and `LOGIC-5` has one operation at a
 * time, so whoever carries a call here carries it onto the thread the Universe lives on.
 */
class Tools(
    private val universe: Universe,
    /** Whether the user allows changes to be staged, asked on every call. */
    private val writing: () -> Boolean = { false },
    /** Whether the user allows the logbook's files to be read and edited, asked on every call. */
    private val direct: () -> Boolean = { false },
) {

    /**
     * Where the logbook's files are and how to treat them, while the user allows direct access.
     *
     * Refused while they do not, naming the box, and refused for a logbook that is not on disk.
     * An agent may already know where the logbook is, being started beside it; what this adds is
     * how to treat the files, and what keeps it out is the box rather than the path. `API-10`.
     */
    fun files(): Reply {
        if (!direct()) return notDirect()
        val folder = universe.path ?: return refused("this logbook is not on disk")
        return replied(
            "folder" to Stored.Leaf(folder),
            "format" to Stored.Leaf("$RESOURCES$FORMAT"),
            "rules" to Stored.Elements(FILE_RULES.map { Stored.Leaf(it) }),
        )
    }

    /** Every type, or the one called [type], with each field's kind, unit and vocabulary. */
    fun describe(type: String? = null): Reply {
        val types = universe.logbook.descriptions
        val chosen = if (type == null) {
            types
        } else {
            listOf(typeCalled(type) ?: return unknownType(type))
        }
        return replied(
            "types" to Stored.Elements(
                chosen.map { description ->
                    Stored.Members(
                        mapOf(
                            "type" to Stored.Leaf(description.name),
                            "fields" to fieldsOf(description),
                        ),
                    )
                },
            ),
        )
    }

    /**
     * The items of [type], [PAGE] at a time, in the order the type lists them.
     *
     * Whole, unless [fields] names what is wanted: a question about depths over eight hundred
     * dives needs `max_depth` and `start_date` of each, and reading the rest to find them is what
     * made an agent look as though it had to work everything out for itself. A field inside a
     * block is named by its path, `environment.visibility`, and one an item has not got is left
     * out of that item.
     *
     * [cursor] continues a listing and is refused once the logbook has changed, so one listing
     * never mixes two states of it.
     */
    fun list(type: String, cursor: String? = null, fields: List<String> = emptyList()): Reply {
        val description = typeCalled(type) ?: return unknownType(type)
        val from = if (cursor == null) {
            0
        } else {
            val (revision, offset) = cursor.split(':').mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 2 }
                ?: return refused("cursor should be one a listing handed out, but was $cursor")
            if (revision != universe.revision) {
                return refused(
                    "cursor should be from revision ${universe.revision}, but was from " +
                        "$revision: the logbook changed since this listing began, so list again " +
                        "from the start",
                )
            }
            offset
        }
        val items = universe.logbook.inOrder(description)
        val page = items.drop(from).take(PAGE)
        val next = from + page.size
        val members = linkedMapOf<String, Stored>(
            "type" to Stored.Leaf(description.name),
            "total" to Stored.Leaf(items.size.toLong()),
            "items" to Stored.Members(
                page.associate {
                    universe.logbook.idOf(it).orEmpty() to chosenOf(sentOf(it), fields)
                },
            ),
        )
        if (next < items.size) members["next"] = Stored.Leaf("${universe.revision}:$next")
        return replied(*members.toList().toTypedArray())
    }

    /** The item called [id], whole. */
    fun get(id: String): Reply {
        val item = universe.logbook[id] ?: return nothingCalled(id)
        val members = linkedMapOf<String, Stored>(
            "id" to Stored.Leaf(id),
            "type" to Stored.Leaf(item.description.name),
            "item" to sentOf(item),
        )
        return replied(*members.toList().toTypedArray())
    }

    /**
     * The samples of the series [path] reaches on the item called [id], as `[second, value]` pairs.
     *
     * A path is written as `aggregate`'s is, naming one entry at each keyed collection:
     * `profiles.p1.depth`, or `profiles.p1.pressures.g1` for one key of a keyed series.
     */
    fun series(id: String, path: String): Reply {
        val item = universe.logbook[id] ?: return nothingCalled(id)
        val segments = path.split('.')
        var at: Item = item
        var index = 0
        while (index < segments.size) {
            val name = segments[index]
            val field = at.description[name]
                ?: return refused(
                    "${at.description.name} should have a field called $name, but has none",
                )
            val read = at.read(name)
            if (read is Result.Unusable) return refused("$path cannot be read: ${read.reason}")
            val value = (read as? Result.Usable)?.value ?: return refused("$path holds nothing")
            when {
                field is OwnedItemDescription && field.cardinality == Cardinality.SINGLE -> {
                    at = value as Item
                    index += 1
                }

                field is OwnedItemDescription -> {
                    val key = segments.getOrNull(index + 1)
                        ?: return refused("$path should name an entry of $name, but stops there")
                    at = entryOf(value, key) as? Item
                        ?: return refused("$name should have an entry $key, but has none")
                    index += 2
                }

                field.cardinality == Cardinality.SERIES && index == segments.lastIndex ->
                    return samplesOf(path, field, value as Series)

                field.cardinality == Cardinality.KEYED_SERIES &&
                    index == segments.lastIndex - 1 -> {
                    val key = segments[index + 1]
                    val series = entryOf(value, key) as? Series
                        ?: return refused("$name should have a series under $key, but has none")
                    return samplesOf(path, field, series)
                }

                else -> return refused("$path should end at a series, but $name is not one")
            }
        }
        return refused("$path should end at a series, but ends at an owned item")
    }

    /**
     * [measure] of what [path] reaches on each of [ids], and what it was based on. `LOGIC-34`.
     *
     * The value is in the units a file uses by default, which `describe` names.
     */
    fun aggregate(ids: List<String>, path: String, measure: String, weight: String? = null): Reply {
        val chosen = Measure.entries.firstOrNull { it.called == measure.replace('_', ' ') }
            ?: return refused(
                "measure should be one of ${Measure.entries.joinToString(", ") { it.called }}, " +
                    "but was $measure",
            )
        val type = ids.firstNotNullOfOrNull { universe.logbook[it] }?.description
        val figure = when (val figured = figureOf(universe.logbook, ids, path, chosen, weight)) {
            is Figured.Refused -> return refused(figured.reason)
            is Figured.Taken -> figured.figure
        }
        val field = type?.let { fieldAt(it, path) }
        val members = linkedMapOf<String, Stored>("measure" to Stored.Leaf(chosen.called))
        if (chosen == Measure.WEIGHTED_MEAN) members["weighted_by"] = Stored.Leaf(weight)
        members["value"] = when {
            figure.value == null -> Stored.Leaf(null)
            chosen == Measure.COUNT -> Stored.Leaf(figure.value!!.toLong())
            field is NumberDescription -> leafOf(field, figure.value!!)
            else -> Stored.Leaf(figure.value)
        }
        (field as? NumberDescription)?.let { number ->
            if (chosen != Measure.COUNT) {
                Units.defaultName(number.dimension)?.let { members["unit"] = Stored.Leaf(it) }
            }
        }
        members["used"] = Stored.Leaf(figure.used.toLong())
        members["skipped"] = Stored.Elements(
            figure.skipped.map {
                Stored.Members(
                    mapOf("at" to Stored.Leaf(it.at), "reason" to Stored.Leaf(it.reason)),
                )
            },
        )
        return replied(*members.toList().toTypedArray())
    }

    /**
     * The plan [stored] describes, calculated, with nothing read and nothing changed.
     *
     * **Always answered**, ticks or none: a calculation is a question about arithmetic, not about
     * this logbook, and there is nothing in it to allow. `API-9`.
     */
    fun plan(stored: Stored): Reply {
        val case = when (val read = casesOf(stored)) {
            is Read.Wrong -> return refused(read.reason)
            is Read.Cases -> read.cases.firstOrNull() ?: return refused("no plan was given")
        }
        return when (val answer = calculated(case.planned)) {
            is Calculated.Refused -> refused(answer.reason)
            is Calculated.Done -> Reply(Json.write(saidOf(case.name, answer.schedule)))
        }
    }

    /**
     * Stage the plan [stored] describes as a profile on the dive called [dive], or on a new dive.
     *
     * **Staged like every other change**, so a plan an agent makes waits for the user to look at
     * it. `RECON-8` has no exception for a big one, and a dive plan is exactly the change somebody
     * should read before it lands.
     *
     * A plan that will not calculate is refused rather than staged, as it is refused rather than
     * written. `API-9`.
     */
    fun createPlan(stored: Stored, dive: String?, name: String): Reply {
        if (!writing()) return notWriting()
        val case = when (val read = casesOf(stored)) {
            is Read.Wrong -> return refused(read.reason)
            is Read.Cases -> read.cases.firstOrNull() ?: return refused("no plan was given")
        }
        val made = when (val ready = preparedOf(universe, case.planned, name)) {
            is Prepared.Refused -> return refused(ready.reason)
            is Prepared.Plan -> ready
        }
        val block = Stored.Members(made.fields)
        if (dive == null) {
            return staging {
                it.add(
                    Types.DIVE.name,
                    mapOf(
                        "primary_profile" to Stored.Leaf("*${made.key}"),
                        "profiles" to Stored.Members(mapOf(made.key to block)),
                    ) + made.dive,
                )
            }
        }
        val held = universe.logbook[dive] ?: return refused("$dive names nothing")
        if (held.description != Types.DIVE) {
            return refused("$dive is a ${held.description.name} rather than a dive")
        }
        // A new entry, never one already there: staging over a plan the user has would be
        // rewriting it field by field, which is a different request and should be asked as one.
        val profiles = held.read(PROFILES)
        if ((profiles as? Result.Usable)?.value.let { it as? Map<*, *> }?.containsKey(made.key) == true) {
            return refused(
                "$dive already has a plan called ${made.key}. Give this one another name.",
            )
        }
        val first = (profiles as? Result.Usable)?.value.let { it as? Map<*, *> }.isNullOrEmpty()
        // Each field of the plan is staged on its own path, the entry being made on the way to
        // the first of them, which is how a review shows it and how applying lands it. `RECON-8`.
        val profile = (Types.DIVE[PROFILES] as OwnedItemDescription).description
        return staging { staging ->
            // Where the dive had nothing staged, a refusal part-way takes back what this staged:
            // half a plan waiting to be applied is worse than none. Where it had something, that
            // is the agent's earlier work and stays; the refusal says what did not go.
            val clean = staging.staged.none { it.id == dive }
            fun refused(done: Outcome.Refused): Outcome {
                if (clean) staging.drop(dive)
                return done
            }
            for ((path, value) in leavesOf(profile, "$PROFILES.${made.key}", made.fields)) {
                val done = staging.set(dive, path, value)
                if (done is Outcome.Refused) return@staging refused(done)
            }
            // A dive with no profile until now works from this one, as `onDiveOf` has it.
            if (first) {
                val done = staging.set(dive, "primary_profile", "*${made.key}")
                if (done is Outcome.Refused) return@staging refused(done)
            }
            Outcome.Done()
        }
    }

    /**
     * The fields [members] holds as paths under [prefix], each ending at a value.
     *
     * A block is gone into and a keyed collection is gone into entry by entry, because staging
     * reaches a value and refuses a whole block. The order is the members' own.
     */
    private fun leavesOf(
        description: ItemDescription,
        prefix: String,
        members: Map<String, Stored>,
    ): List<Pair<String, Stored>> {
        val out = ArrayList<Pair<String, Stored>>()
        for ((name, held) in members) {
            val field = description[name]
            val inside = (held as? Stored.Members)?.members
            when {
                field !is OwnedItemDescription || inside == null -> out += "$prefix.$name" to held
                field.cardinality == Cardinality.SINGLE ->
                    out += leavesOf(field.description, "$prefix.$name", inside)
                else -> for ((key, entry) in inside) {
                    val entryFields = (entry as? Stored.Members)?.members ?: continue
                    out += leavesOf(field.description, "$prefix.$name.$key", entryFields)
                }
            }
        }
        return out
    }

    /**
     * Stage [value] in the field [path] names on the item called [id], for the user to review.
     *
     * A null clears the field. Nothing changes until the user applies what is staged.
     */
    fun stageSet(id: String, path: String, value: Any?): Reply =
        staging { it.set(id, path, value) }

    /** Stage an item of [type] holding [fields], to be added when the user applies it. */
    fun stageAdd(type: String, fields: Map<String, Any?>): Reply = staging { it.add(type, fields) }

    /** Stage the item called [id] to be deleted when the user applies it. */
    fun stageDelete(id: String): Reply = staging { it.delete(id) }

    /** What is staged so far, item by item, with each field as it is and as it would be. */
    fun staged(): Reply {
        if (!writing()) return notWriting()
        val staging = universe.staging ?: return refused(NOWHERE)
        return replied(
            "staged" to Stored.Elements(
                staging.staged.map { item ->
                    Stored.Members(
                        linkedMapOf(
                            "id" to Stored.Leaf(item.id),
                            "type" to Stored.Leaf(item.type),
                            "doing" to Stored.Leaf(item.kind.name.lowercase()),
                            "fields" to Stored.Elements(
                                item.fields.map { field ->
                                    val said = linkedMapOf<String, Stored>(
                                        "at" to Stored.Leaf(field.at),
                                        "from" to Stored.Leaf(field.from),
                                        "to" to Stored.Leaf(field.to),
                                    )
                                    // Only where it matters: the logbook has moved under this
                                    // change, so applying will leave it alone and it is worth
                                    // staging again against what is there now.
                                    if (field.moved) said["now"] = Stored.Leaf(field.now)
                                    Stored.Members(said)
                                },
                            ),
                        ),
                    )
                },
            ),
        )
    }

    /** One staging call, refused where the user has not allowed changes or there is nowhere. */
    private fun staging(doing: (Staging) -> Outcome): Reply {
        if (!writing()) return notWriting()
        val staging = universe.staging ?: return refused(NOWHERE)
        return when (val done = doing(staging)) {
            is Outcome.Refused -> refused(done.reason)
            is Outcome.Done -> staged()
        }
    }

    /**
     * What a write tool answers while the user has not allowed changes.
     *
     * **What to do comes first.** An agent asked to correct forty dives should tell the user how
     * to let it, not report a policy at them: *ask the user to tick…* is something they can act
     * on, where *changing data is not allowed* is a thing to be sorry about. `API-5`.
     */
    private fun notWriting(): Reply = refused(
        "ask the user to tick *Allow logbook edits* beside the conversation. Until they do, " +
            "nothing can be staged; once they have, what you stage still waits for them to " +
            "review it.",
    )

    /** What `files` answers while the user has not allowed direct access. */
    private fun notDirect(): Reply = refused(
        "ask the user to tick *Allow raw file access* beside the conversation, and say why the " +
            "tools were not enough. Until they do, the logbook's files are not yours to read " +
            "or edit.",
    )

    /**
     * What an agent is told before it is asked anything: how to behave, and what the logbook holds.
     *
     * The instructions the server carries, then every type with its fields — name, kind, unit,
     * the words a closed field takes — written as a page an agent reads at the start rather than
     * as a reply it has to ask for. It is written into the folder the agent is started in under
     * the names agents read on their own (`CLAUDE.md`, `AGENTS.md`), served as a resource, and
     * answered by the `guide` tool, so it reaches the model whichever of those its agent honours.
     * `API-4`.
     */
    fun briefing(): String {
        val page = StringBuilder()
        page.append("# Working with this Yemoja logbook\n\n")
        page.append(INSTRUCTIONS.trim()).append("\n\n")
        page.append("## What the logbook holds\n\n")
        page.append("Every item is one of these types, and holds only the fields listed. ")
        page.append("A number is sent and given in the unit written after it. ")
        page.append("The describe tool answers the same in JSON, with the words each field ")
        page.append("suggests from this logbook.\n")
        for (description in universe.logbook.descriptions) {
            page.append("\n### ").append(description.name).append("\n\n")
            for (field in description.fields) page.append(lineOf(field, ""))
        }
        return page.toString()
    }

    /** One field as a line of the briefing, and the fields of a block indented under it. */
    private fun lineOf(field: FieldDescription, indent: String): String {
        val said = StringBuilder("$indent- `${field.name}`: ${kindOf(field)}")
        if (field.cardinality != Cardinality.SINGLE) {
            said.append(", ${HOLDS.getValue(field.cardinality)}")
        }
        when (field) {
            is NumberDescription ->
                Units.defaultName(field.dimension)?.let { said.append(" in $it") }
            is TextDescription ->
                field.fixedSet?.let { said.append(", one of ${it.joinToString(", ")}") }
            is ReferenceDescription -> said.append(" to a ${field.targetType}")
            is KeyReferenceDescription -> said.append(" into `${field.collection}`")
            else -> Unit
        }
        when (field.role) {
            is Role.Derived -> said.append("; derived, never written")
            is Role.Overrideable -> said.append("; derived unless written")
            is Role.Primary -> Unit
        }
        said.append("\n")
        if (field is OwnedItemDescription) {
            for (inner in field.description.fields) said.append(lineOf(inner, "$indent  "))
        }
        return said.toString()
    }

    private fun typeCalled(name: String): ItemDescription? =
        universe.logbook.descriptions.firstOrNull { it.name == name }

    private fun unknownType(name: String): Reply {
        val types = universe.logbook.descriptions.joinToString(", ") { it.name }
        return refused("type should be one of $types, but was $name")
    }

    private fun nothingCalled(id: String): Reply =
        refused("$id should name an item, but names nothing")

    private fun fieldsOf(description: ItemDescription): Stored =
        Stored.Elements(description.fields.map { fieldOf(it) })

    private fun fieldOf(field: FieldDescription): Stored {
        val members = linkedMapOf<String, Stored>(
            "name" to Stored.Leaf(field.name),
            "kind" to Stored.Leaf(kindOf(field)),
            "holds" to Stored.Leaf(HOLDS.getValue(field.cardinality)),
            "role" to Stored.Leaf(
                when (field.role) {
                    is Role.Primary -> "recorded"
                    is Role.Derived -> "derived"
                    is Role.Overrideable -> "derived, and correctable"
                },
            ),
        )
        when (field) {
            is NumberDescription -> {
                Units.defaultName(field.dimension)?.let { members["unit"] = Stored.Leaf(it) }
                field.range?.let { members["range"] = rangeOf(it.start, it.endInclusive) }
            }

            is WholeNumberDescription ->
                field.range?.let {
                    members["range"] = rangeOf(it.first.toDouble(), it.last.toDouble())
                }

            is TextDescription -> {
                field.fixedSet?.let { members["one_of"] = wordsOf(it) }
                universe.suggested(field).takeIf { it.isNotEmpty() }
                    ?.let { members["suggested"] = wordsOf(it) }
            }

            is ReferenceDescription -> members["names_a"] = Stored.Leaf(field.targetType)
            is KeyReferenceDescription ->
                members["names_an_entry_of"] = Stored.Leaf(field.collection)
            is OwnedItemDescription -> members["fields"] = fieldsOf(field.description)
            else -> Unit
        }
        return Stored.Members(members)
    }

    private fun samplesOf(path: String, field: FieldDescription, series: Series): Reply {
        val members = linkedMapOf<String, Stored>("at" to Stored.Leaf(path))
        (field as? NumberDescription)?.let { number ->
            Units.defaultName(number.dimension)?.let { members["unit"] = Stored.Leaf(it) }
        }
        members["samples"] = Stored.Elements(
            (0..<series.size).map { index ->
                val value = when (val held = series.valueAt(index)) {
                    is Element.Usable -> leafOf(field, held.value)
                    is Element.Unusable -> Stored.Leaf(null)
                }
                Stored.Elements(listOf(Stored.Leaf(series.secondAt(index).toLong()), value))
            },
        )
        return replied(*members.toList().toTypedArray())
    }

    private fun replied(vararg members: Pair<String, Stored>): Reply =
        Reply(Json.write(Stored.Members(linkedMapOf("revision" to revisionLeaf()) + members)))

    private fun refused(reason: String): Reply =
        Reply(
            Json.write(
                Stored.Members(
                    linkedMapOf("revision" to revisionLeaf(), "refused" to Stored.Leaf(reason)),
                ),
            ),
            refused = true,
        )

    private fun revisionLeaf(): Stored = Stored.Leaf(universe.revision.toLong())

    companion object {

        /** How many items one page of a listing holds. */
        const val PAGE: Int = 50

        /** What is said where a logbook has nowhere beside it to stage a change. */
        private const val NOWHERE = "this logbook has nowhere to stage a change"

        /** Where the manual's chapters are served as resources. */
        const val RESOURCES: String = "yemoja://manual/"

        /** The chapter that says how the files are written. */
        const val FORMAT: String = "data-format.md"

        /**
         * What an agent allowed at the files is told to do with them.
         *
         * Said with the path rather than in the briefing, so they arrive at the moment they apply.
         */
        private val FILE_RULES: List<String> = listOf(
            "Read the format chapter before touching a file. An item that no longer reads makes " +
                "the whole logbook refuse to open.",
            "Use the tools for anything they can do. Edit a file only for what they cannot, and " +
                "say which file you changed and why.",
            "Edit in place with the smallest change. Do not rewrite a file you did not need to.",
            "Leave the folders beside the logbook alone: .proposed, .import and .agent are not " +
                "part of it.",
            "Yemoja reads the logbook again when your turn ends. Until then the tools answer " +
                "from what was there before your edit, so do not mix the two in one answer.",
        )
    }
}

/** What a field is, in the words `manual/data-fields.md` uses for it. */
private fun kindOf(field: FieldDescription): String = when (field) {
    is NumberDescription -> "number"
    is WholeNumberDescription -> "whole number"
    is TextDescription -> "text"
    is MultilineTextDescription -> "multiline text"
    is DateDescription -> "date"
    is TimeDescription -> "time"
    is BooleanDescription -> "yes or no"
    is GasDescription -> "gas mix"
    is KeyReferenceDescription -> "key reference"
    is ReferenceDescription -> "reference"
    is OwnedItemDescription -> "owned item"
}

private val HOLDS: Map<Cardinality, String> = mapOf(
    Cardinality.SINGLE to "one",
    Cardinality.LIST to "list",
    Cardinality.KEYED to "keyed",
    Cardinality.SERIES to "series",
    Cardinality.KEYED_SERIES to "keyed series",
)

private fun rangeOf(least: Double, most: Double): Stored =
    Stored.Elements(listOf(Stored.Leaf(least), Stored.Leaf(most)))

private fun wordsOf(words: Collection<String>): Stored =
    Stored.Elements(words.map { Stored.Leaf(it) })

/**
 * [sent] cut down to [fields], or whole where none are named.
 *
 * A path names a field inside a block, and lands in the reply under the same path: asking for
 * `environment.visibility` answers `{"environment": {"visibility": 12}}`, so what comes back reads
 * as the item does. A field the item has not got is left out rather than sent as nothing.
 */
private fun chosenOf(sent: Stored.Members, fields: List<String>): Stored.Members {
    if (fields.isEmpty()) return sent
    val wanted = fields.map { it.split('.') }
    return reachedIn(sent, wanted) ?: Stored.Members(emptyMap())
}

/** [sent] with only what [paths] reach, or absent where they reach nothing in it. */
private fun reachedIn(sent: Stored.Members, paths: List<List<String>>): Stored.Members? {
    val chosen = LinkedHashMap<String, Stored>()
    val grouped = paths.filter { it.isNotEmpty() }.groupBy({ it.first() }, { it.drop(1) })
    for ((first, below) in grouped) {
        val held = sent.members[first] ?: continue
        if (below.any { it.isEmpty() }) {
            chosen[first] = held
        } else if (held is Stored.Members) {
            reachedIn(held, below)?.let { chosen[first] = it }
        }
    }
    return if (chosen.isEmpty()) null else Stored.Members(chosen)
}

/** The entry under [key] in a keyed collection, whether it holds items or series. */
private fun entryOf(collection: Any, key: String): Any? =
    ((collection as? Map<*, *>)?.get(key) as? Element.Usable<*>)?.value
