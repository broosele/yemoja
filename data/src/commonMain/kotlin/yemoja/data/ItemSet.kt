package yemoja.data

/**
 * Every item that is loaded: the logbook's own, and those of each library it uses.
 *
 * Shadowing is applied before an item gets here, so an id names exactly one of them. One id names
 * one item across every type, because a reference carries the id and nothing else.
 *
 * **Two questions, and no more.** Resolve an id, and list everything of a type. `DATA-4`. A logbook
 * is small enough to hold entirely, so filtering, sorting and searching happen above this layer
 * over ordinary collections, and items navigate themselves. The surface stays small because there
 * is nothing to add to it.
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
 * **Minting an id, renaming one and removing an item are absent.** Minting has to know which
 * indices were ever used, since one is never reissued, and renaming carries every reference with it
 * as a single act. Both wait on the journal.
 *
 * Not immutable: reading a logbook fills a set, and a logbook gains items while it is open.
 */
class ItemSet(descriptions: List<ItemDescription>) {

    // Copied. A List is read-only, not immutable.
    /** The types this set may hold. They come from the logic layer, since a dive is its subject. */
    val descriptions: List<ItemDescription> = descriptions.toList()

    private val byId = LinkedHashMap<String, ReferenceableItem>()

    /** How many items are loaded, of every type together. */
    val size: Int get() = byId.size

    /**
     * Puts [item] in under [id].
     *
     * Refuses an id already taken, which is what makes one id name one item. Resolving a clash by
     * appending `#1` belongs here too and is not written yet.
     */
    fun add(id: String, item: ReferenceableItem) {
        require(id.isNotEmpty()) { "an id should not be empty" }
        require(id.none { it == '*' || it.isWhitespace() }) {
            "an id should hold no spaces and no *, but was $id"
        }
        require(id !in byId) { "$id names an item already, and one id names one item" }
        byId[id] = item
    }

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
