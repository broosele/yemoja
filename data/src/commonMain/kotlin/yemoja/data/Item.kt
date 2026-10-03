package yemoja.data

import kotlin.reflect.KClass

/**
 * Item is one thing of one type.
 *
 * There is no class per type. What a dive is comes from its [ItemDescription], which is data and
 * lives in the logic layer, so nothing here names a field.
 *
 * An item holds no id. A referenceable one is named by the file it sits in, an owned one by the key
 * it sits under, and neither is inside the item. Ask the [set] instead.
 *
 * Connected rather than free-standing: an item answers questions it cannot answer alone by asking
 * outward, which is how a reference resolves and how a region finds its children. **Writing an item
 * out must stop at its own fields** and never follow [set] or a parent.
 *
 * **Not immutable: an item changes in place**, which is what lets a reference held to one stay
 * current. [write] is the whole of it, and it asks the field to judge what it is given, so the
 * one door in is the one a file comes through. Nothing announces the change. `DATA-6`.
 *
 * Removing an item is not here. An id and every reference to it move together, which needs
 * something holding all the items, and that is the Universe.
 *
 * **Nothing outside an item is read while it is being built.** The fields are built by a function
 * the constructor hands itself to, so an owned item takes the parent it belongs to and the parent
 * is finished the moment its own constructor returns. That works only because the building never
 * looks outward: not at [set], not at a parent's fields, not at another item. Every one of those
 * is still being assembled, and a property read in that window has not been given its value yet.
 * `DATA-85`.
 *
 * Examples of a type include a dive, a person and a profile.
 */
sealed class Item(
    val description: ItemDescription,
    fields: (Item) -> Map<String, Result<Any>>,
    unrecognisedFields: Map<String, Stored>,
) {

    private val stored: MutableMap<String, Result<Any>> = LinkedHashMap(fields(this))

    /**
     * Every field the description names that was stored, by name, untyped.
     *
     * The first of the three ways in. Writing back reads it, together with
     * [unrecognisedFields]. `DATA-51`.
     *
     * Only stored fields. A derived one is worked out and never kept, and an absent one is not a
     * key, so [Result.Absent] never appears as a value here.
     *
     * Read-only to anything holding it, and not a copy: [write] changes what this shows, which is
     * what makes a view of an item that was edited show the edit. Nothing is announced. `DATA-6`.
     */
    val fields: Map<String, Result<Any>> get() = stored

    /**
     * Fields the description does not name, kept as the source handed them over.
     *
     * A newer version's field and a hand-typed misspelling are both here and neither can be told
     * from the other, which is why they are kept rather than judged. `DATA-65`.
     *
     * Held as [Stored], which every source produces and none of them owns, so a writer for one
     * source can put back what a reader for another handed over. Nothing here looks inside one:
     * an unrecognised field is never derived from, shown, checked or read as a value. An item
     * assembled in memory has none.
     */
    val unrecognisedFields: Map<String, Stored> = unrecognisedFields.toMap()

    /** The items this one belongs to, and the only way to reach anything outside it. */
    abstract val set: ItemSet

    /**
     * What reading [name] gives, by the rules its description sets.
     *
     * The second way in: it accepts only names the description carries, so a misspelt one is a
     * fault in the code rather than a silent absent. `DATA-51`.
     *
     * A primary field gives what was stored. A derived one is worked out every time, because
     * nothing announces that an item changed. An overrideable one gives what was stored where there
     * is anything, and works it out otherwise.
     */
    fun read(name: String): Result<Any> =
        when (val role = fieldDescription(name).role) {
            is Role.Primary -> fields[name] ?: Result.Absent
            is Role.Derived -> role.compute(this)
            is Role.Overrideable -> fields[name] ?: role.compute(this)
        }

    /**
     * Puts [given] in [name], or says why it does not belong there.
     *
     * The other way through the door [read] comes in by, and the same one a file goes through:
     * the field parses and judges what it is given, so a value typed into a form is taken no more
     * on trust than a value in a file. `DATA-66`. [units] is what the value is expressed in, which
     * is the user's when a form hands one over and the file's when a file does.
     *
     * **Null clears the field.** On a recorded one that leaves nothing; on a correctable one it
     * restores what the application works out, so deleting a correction is writing nothing over
     * it rather than an operation of its own.
     *
     * **A worked-out field is refused as a fault**, not as a value that will not do. Nothing
     * offers to edit one, and asking to is the same kind of mistake as naming a field that does
     * not exist. A file holding one is a different matter: it is kept, ignored, and written back.
     *
     * An owned item is made by writing an empty set of fields, which is what `"medical": {}` says
     * in a file. It cannot arrive ready-made, because an owned item is built by its owner and
     * this is the owner. `DATA-85`.
     *
     * Nothing is announced, so whatever showed this asks again. `DATA-6`.
     */
    fun write(name: String, given: Any?, units: Units = Units.DEFAULT): Validity {
        val made = prepared(name, given, units)
        if (made is Result.Unusable) return Validity.Invalid(made.reason)
        apply(name, made)
        return Validity.Valid
    }

    /**
     * What writing [given] to [name] would put there, without putting it.
     *
     * The half of [write] that can refuse. It exists apart so that a change touching several
     * fields can be judged whole before any of it lands: applying in order and stopping at the
     * first refusal would leave an item half changed, and nothing above could tell.
     *
     * [Result.Absent] is a field being cleared, which cannot be refused.
     *
     * **This pair is the Universe's**, and [write] is everything else's. Nothing is enforced —
     * `internal` does not reach across a module — so it is a rule like the one holding a front
     * end to the Universe at all.
     */
    fun prepared(name: String, given: Any?, units: Units): Result<Any> {
        val field = fieldDescription(name)
        require(field.role !is Role.Derived) {
            "$name is derived, and writing it would be writing to nothing"
        }
        if (given == null) return Result.Absent
        // A value already made travels as a leaf, which is what a source hands over for one. A
        // caller with a whole shape to put in -- an empty owned item, a list -- gives the Stored.
        val held = if (given is Stored) given else Stored.Leaf(given)
        return ItemReader.fieldOf(field, held, this, units)
    }

    /** Puts what [prepared] made, or clears the field where it made an absent. */
    fun apply(name: String, made: Result<Any>) {
        if (made is Result.Absent) stored.remove(name) else stored[name] = made
    }

    /** A single value: `single<Double>("max_depth")`. */
    inline fun <reified T : Any> single(name: String): Result<T> =
        readAs(name, Cardinality.SINGLE, T::class)

    /** Several, in the order written: `list<Reference>("buddies")`. */
    inline fun <reified T : Any> list(name: String): Result<List<Element<T>>> =
        readAs(name, Cardinality.LIST, T::class)

    /** Several under keys: `keyed<Item>("profiles")`. */
    inline fun <reified T : Any> keyed(name: String): Result<Map<String, Element<T>>> =
        readAs(name, Cardinality.KEYED, T::class)

    /** Against time: `series<Double>("depth")`. [T] is what sits on the value axis. */
    inline fun <reified T : Any> series(name: String): Result<Series> =
        readAs(name, Cardinality.SERIES, T::class)

    /**
     * One series per key: `keyedSeries<Double>("pressures")`.
     *
     * Each key keeps its own fate, as a keyed collection does: one gas source whose pressures
     * cannot be read leaves the others alone. `DATA-77`.
     */
    inline fun <reified T : Any> keyedSeries(name: String): Result<Map<String, Element<Series>>> =
        readAs(name, Cardinality.KEYED_SERIES, T::class)

    /**
     * The item a key reference into [collection] names an entry of: this one where it holds any,
     * and its owner otherwise.
     *
     * A recording's `gas_switches` name the dive's gas sources, because a recording keeps none of
     * its own. One that keeps its own names those instead, and the same field reads both.
     *
     * Where nothing from here outwards declares the collection, null.
     */
    fun rootOf(collection: String): Item? {
        var nearest: Item? = null
        var at: Item? = this
        while (at != null) {
            if (at.description[collection] != null) {
                if (nearest == null) nearest = at
                val held = (at.read(collection) as? Result.Usable)?.value as? Map<*, *>
                if (!held.isNullOrEmpty()) return at
            }
            at = (at as? OwnedItem)?.parent
        }
        return nearest
    }

    /**
     * The description of the field called [name], or a fault where the type has no such field.
     *
     * Not an absent. A name the description does not carry is a mistake in the code that asked.
     * `DATA-51`.
     */
    @PublishedApi
    internal fun fieldDescription(name: String): FieldDescription =
        description[name] ?: throw IllegalArgumentException(
            "${description.name} has no field called $name"
        )

    /**
     * [read], having first confirmed the field is declared the way the caller is asking for it.
     *
     * The check is against the description rather than the value, so asking for the wrong shape or
     * the wrong kind is a fault even where the field is absent.
     */
    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <R : Any> readAs(
        name: String,
        cardinality: Cardinality,
        type: KClass<*>,
    ): Result<R> {
        val field = fieldDescription(name)
        require(field.cardinality == cardinality) {
            "$name is written as $cardinality, so it should not be read as ${field.cardinality}"
        }
        require(field.valueType == type) {
            "$name holds ${field.valueType.simpleName}," +
                " so it should not be read as ${type.simpleName}"
        }
        return read(name) as Result<R>
    }
}

/**
 * ReferenceableItem is an item with an id of its own, which anything may point at.
 *
 * The id is not here. It is the name of the file the item sits in, and [ItemSet] answers in both
 * directions.
 *
 * Examples include a dive, a person and a dive site.
 */
class ReferenceableItem(
    description: ItemDescription,
    fields: (Item) -> Map<String, Result<Any>>,
    override val set: ItemSet,
    unrecognisedFields: Map<String, Stored> = emptyMap(),
) : Item(description, fields, unrecognisedFields) {

    /** An item whose fields hold no owned item needs nothing from itself to build them. */
    constructor(
        description: ItemDescription,
        fields: Map<String, Result<Any>>,
        set: ItemSet,
        unrecognisedFields: Map<String, Stored> = emptyMap(),
    ) : this(description, { fields }, set, unrecognisedFields)
}

/**
 * OwnedItem is an item that exists only inside another.
 *
 * Created and destroyed with its [parent], never pointed at from outside it, and named by the key
 * it sits under where it sits in a collection at all.
 *
 * Examples include a dive's profile and a cylinder on a dive.
 *
 * The link to the parent is not stored — it would be circular on disk and says nothing the file
 * structure does not — and it is load-bearing rather than tidy: a profile's `gas_switches` name
 * entries of `gas_sources`, which is a collection on the dive.
 */
class OwnedItem(
    description: ItemDescription,
    fields: (Item) -> Map<String, Result<Any>>,
    val parent: Item,
    unrecognisedFields: Map<String, Stored> = emptyMap(),
) : Item(description, fields, unrecognisedFields) {

    /** An owned item may own others, and nests the same way. */
    constructor(
        description: ItemDescription,
        fields: Map<String, Result<Any>>,
        parent: Item,
        unrecognisedFields: Map<String, Stored> = emptyMap(),
    ) : this(description, { fields }, parent, unrecognisedFields)

    // The owner's, by definition. Holding a second copy is a second thing to keep in step.
    override val set: ItemSet get() = parent.set
}

/**
 * The owned item [name] holds: the one stored, or an empty one standing in where none is stored
 * and one of its fields is worked out all the same.
 *
 * A dive with nothing written about its conditions holds no `environment`, and its recording still
 * says how cold the water was. Reading the field gives absent, which is true of what is stored and
 * hides what is worked out, so whatever shows, counts or exports an owned item asks here instead.
 * **Nothing is written.** The stand-in exists for the reading and is made again each time.
 *
 * Null where nothing is stored and nothing is worked out, where what is stored will not read, and
 * for a field holding more than one. `DATA-124`.
 */
fun Item.ownedOrWorked(name: String): OwnedItem? {
    val field = description[name] as? OwnedItemDescription ?: return null
    if (field.cardinality != Cardinality.SINGLE) return null
    when (val stored = read(name)) {
        is Result.Usable -> return stored.value as? OwnedItem
        is Result.Unusable -> return null
        Result.Absent -> Unit
    }
    val standIn = OwnedItem(field.description, emptyMap(), this)
    val worked = field.description.fields.any {
        it.role !is Role.Primary && standIn.read(it.name) is Result.Usable
    }
    return standIn.takeIf { worked }
}
