package yemoja.data

/**
 * ItemSet holds every item that is loaded: the logbook's own, and those of each library it uses.
 *
 * Shadowing is applied before an item gets here, so an id names exactly one of them. One id names
 * one item across every type, because a reference carries the id and nothing else.
 *
 * **Two questions, and no more.** Resolve an id, and list everything of a type. `DATA-4`. A logbook
 * is small enough to hold entirely, so filtering, sorting and searching happen over ordinary
 * collections rather than here, and items navigate themselves. [inOrder] is such a thing: a
 * function beside this class that composes both questions, not a third one on it.
 *
 * It answers in both directions. An item cannot say what it is called, but an interface offering
 * "add this person as a buddy" holds one and has to write a reference to it.
 *
 * Nothing is announced when anything changes. There are no subscriptions here: whatever shows
 * something worked out asks again. `DATA-6`.
 *
 * More than one set can exist at a time. An import is read into its own, so candidates are whole
 * and resolvable before anything is merged.
 *
 * **Minting an id is absent.** Which id a new item takes is a question about what is already
 * there and what a type's ids look like, and the second half is the logic layer's.
 *
 * Renaming is not absent so much as elsewhere. An id and every reference to it change as one act,
 * and the layer document puts that at the Universe, above here.
 *
 * Not immutable: reading a logbook fills a set, and a logbook gains items while it is open.
 */
class ItemSet(descriptions: List<ItemDescription>) {

    // Copied. A List is read-only, not immutable.
    /**
     * The types this set may hold, in the order they were given.
     *
     * From the logic layer, since a dive is its subject. The order is kept because a front end
     * offers the types in it, and taking that from here rather than beside it is what stops an
     * interface showing a tab for a type the set could never hold. `UI-3`.
     *
     * **Nothing is refused by it yet.** [add] checks the id and not the type, so this says what
     * the set was built for rather than what it enforces. `DATA-66`.
     */
    val descriptions: List<ItemDescription> = descriptions.toList()

    private val byId = LinkedHashMap<String, ReferenceableItem>()

    /** How many items are loaded, of every type together. */
    val size: Int get() = byId.size

    /**
     * Puts [item] in under [id].
     *
     * Refuses an id already taken: one id names one item. Resolving a clash by appending `#1`
     * belongs here too and is not written yet.
     */
    fun add(id: String, item: ReferenceableItem) {
        require(id.isNotEmpty()) { "an id should not be empty" }
        require(id.none { it == '*' || it.isWhitespace() }) {
            "an id should hold no spaces and no *, but was $id"
        }
        require(id !in byId) { "$id names an item already, and one id names one item" }
        byId[id] = item
        revision += 1
    }

    /**
     * Takes out what [id] names, and says whether anything was there.
     *
     * **References to it are left dangling**, which is a state the model already carries and an
     * interface already shows: a reference that names nothing is a person not entered yet, and
     * a person deleted looks the same from the other end. Hunting them down would be the set
     * deciding what a reference means, which is above it.
     */
    fun remove(id: String): Boolean {
        if (byId.remove(id) == null) return false
        revision += 1
        return true
    }

    /**
     * A number that changes whenever this set does.
     *
     * Not notification — `DATA-6` settles that nothing is announced and nothing subscribes. This
     * is the other half of that answer: something showing a list has to ask whether what it has is
     * still current, and counting the items does not tell it. An edit that changes a dive's date
     * reorders the list without changing how many there are.
     *
     * It counts additions and removals, not edits to an item's fields, so a view keyed on it is
     * refreshed by less than every change. `DATA-6` allows that: a view that may be out of date
     * asks again.
     */
    var revision: Int = 0
        private set

    /** What [id] names, or nothing where no item has it. */
    operator fun get(id: String): ReferenceableItem? = byId[id]

    /** Everything of one type, in the order it was added. */
    fun allOf(description: ItemDescription): List<ReferenceableItem> =
        byId.values.filter { it.description === description }

    /**
     * What [item] is called here, or nothing where it is not in this set.
     *
     * A scan. A second map would answer faster and would be a second thing to keep in step, and
     * this is asked when a reference is written rather than in a loop.
     */
    fun idOf(item: ReferenceableItem): String? =
        byId.entries.firstOrNull { it.value === item }?.key
}
