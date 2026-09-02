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
 * **Changing one is absent.** Nothing here sets a field, adds an owned item or deletes anything;
 * that waits on the journal.
 *
 * Examples of a type include a dive, a person and a profile.
 */
sealed class Item(
    val description: ItemDescription,
    fields: Map<String, Result<Any>>,
    unrecognisedFields: Map<String, Any>,
) {

    /**
     * Every field the description names that was stored, by name, untyped.
     *
     * The first of the three ways in. Writing back reads it, together with
     * [unrecognisedFields]. `DATA-51`.
     *
     * Only stored fields. A derived one is worked out and never kept, and an absent one is not a
     * key, so [Result.Absent] never appears as a value here.
     */
    val fields: Map<String, Result<Any>> = fields.toMap()

    /**
     * Fields the description does not name, kept as the source handed them over.
     *
     * A newer version's field and a hand-typed misspelling are both here and neither can be told
     * from the other, which is why they are kept rather than judged. `DATA-65`.
     *
     * **The values are the source's own** — a reader's tree, for a file — and nothing here looks
     * inside one. Only a writer from the same source ever touches them, so no derivation, no
     * check and no interface has a second shape to handle, which is what `DATA-64` refused when
     * it kept a source's types out of a result. An item assembled in memory has none.
     */
    val unrecognisedFields: Map<String, Any> = unrecognisedFields.toMap()

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

    /** One series per key: `keyedSeries<Double>("pressures")`. */
    inline fun <reified T : Any> keyedSeries(name: String): Result<Map<String, Series>> =
        readAs(name, Cardinality.KEYED_SERIES, T::class)

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
    fields: Map<String, Result<Any>>,
    override val set: ItemSet,
    unrecognisedFields: Map<String, Any> = emptyMap(),
) : Item(description, fields, unrecognisedFields)

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
    fields: Map<String, Result<Any>>,
    val parent: Item,
    unrecognisedFields: Map<String, Any> = emptyMap(),
) : Item(description, fields, unrecognisedFields) {

    // The owner's, by definition. Holding a second copy is a second thing to keep in step.
    override val set: ItemSet get() = parent.set
}
