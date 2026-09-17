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
import yemoja.logic.Figured
import yemoja.logic.Measure
import yemoja.logic.Outcome
import yemoja.logic.Staging
import yemoja.logic.Universe
import yemoja.logic.fieldAt
import yemoja.logic.figureOf

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
 * **Nothing is held back from an agent.** Everything the logbook holds about a person — an address,
 * a telephone number, a medical — is sent like anything else, and the user is warned of that rather
 * than protected from it. `API-5`.
 *
 * **Not safe to call from two threads.** The Universe is not, and `LOGIC-5` has one operation at a
 * time, so whoever carries a call here carries it onto the thread the Universe lives on.
 */
class Tools(
    private val universe: Universe,
    /** Whether the user allows changes to be staged, asked on every call. */
    private val writing: () -> Boolean = { false },
    // Going, and ignored already: nothing is held back from an agent. It stays only until the
    // window stops passing it, so that the tree compiles between the two commits.
    @Suppress("UNUSED_PARAMETER") personal: () -> Boolean = { false },
) {

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
     * The items of [type], whole, [PAGE] at a time, in the order the type lists them.
     *
     * [cursor] continues a listing and is refused once the logbook has changed, so one listing
     * never mixes two states of it.
     */
    fun list(type: String, cursor: String? = null): Reply {
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
                page.associate { universe.logbook.idOf(it).orEmpty() to sentOf(it) },
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
        "ask the user to tick *Allow changes* beside the conversation. Until they do, " +
            "nothing can be staged; once they have, what you stage still waits for them to " +
            "review it.",
    )

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
                    is Role.Derived -> "worked out"
                    is Role.Overrideable -> "worked out, and correctable"
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

/** The entry under [key] in a keyed collection, whether it holds items or series. */
private fun entryOf(collection: Any, key: String): Any? =
    ((collection as? Map<*, *>)?.get(key) as? Element.Usable<*>)?.value
