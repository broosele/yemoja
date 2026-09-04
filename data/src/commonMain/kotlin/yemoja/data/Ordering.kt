/*
 * Listing items of one type in an order a reader expects, described rather than coded.
 *
 * A front end asks a type how its items go and applies the answer. It never names a field, so
 * a type that changes its mind about its own order changes nothing above. `DATA-89`.
 */

package yemoja.data

/** Which end of a field comes first. */
enum class Direction {
    ASCENDING,
    DESCENDING,
}

/**
 * Ordering is one step of an item order: a field, and which end of it comes first.
 *
 * Ascending by default, because that is what a reader takes *ordered by* to mean. A type
 * listing newest first says so. `DATA-89`.
 *
 * Immutable.
 *
 * Examples: `Ordering("name")` lists alphabetically, and
 * `Ordering("start_date", Direction.DESCENDING)` lists recent first.
 */
class Ordering(val field: String, val direction: Direction = Direction.ASCENDING)

/**
 * Everything of one type, in the order that type asks for.
 *
 * Built from the two questions an item set answers rather than being a third: it lists a type
 * and it names an item, and this arranges what comes back. `DATA-4` is untouched.
 *
 * **A key is read once per item, not once per comparison.** A sort field may be worked out —
 * a dive's `start_date` comes from its primary profile — and a comparator that read as it went
 * would walk every profile as many times as the sort compared it.
 *
 * Ties fall to the id, so the order is total and does not depend on which file an item was
 * written in.
 */
fun ItemSet.inOrder(description: ItemDescription): List<ReferenceableItem> {
    val items = allOf(description)
    if (description.orderedBy.isEmpty()) return items
    val sorted = items.map { item ->
        Sorted(item, description.orderedBy.map { keyOf(item, it.field) }, idOf(item).orEmpty())
    }
    return sorted.sortedWith(order(description.orderedBy)).map { it.item }
}

/** An item with its sort keys already read, so that sorting only compares. */
private class Sorted(val item: ReferenceableItem, val keys: List<Any?>, val id: String)

/**
 * The comparison [orderedBy] describes, each step in its own direction, then the id.
 *
 * **An item missing a key goes last whichever way the step points**, an unknown date being
 * neither the oldest nor the newest. That is settled here rather than in [compared], because
 * the sign that reverses a descending step would otherwise reverse this too and lift the
 * unknown to the top.
 */
private fun order(orderedBy: List<Ordering>): Comparator<Sorted> =
    Comparator { one, other ->
        for ((at, step) in orderedBy.withIndex()) {
            val mine = one.keys[at]
            val theirs = other.keys[at]
            if (mine == null || theirs == null) {
                if (mine == null && theirs == null) continue
                return@Comparator if (mine == null) 1 else -1
            }
            val by = compared(mine, theirs)
            if (by != 0) return@Comparator if (step.direction == Direction.ASCENDING) by else -by
        }
        one.id.compareTo(other.id)
    }

/** What a field holds on an item, or nothing where the field is unknown or holds nothing usable. */
private fun keyOf(item: Item, field: String): Any? =
    if (item.description[field] == null) null
    else (item.read(field) as? Result.Usable)?.value

/**
 * Two values of one field.
 *
 * Text compares without case, a list of names being read as a reader writes them rather than as
 * a machine encodes them. Accents sort by their code points, there being no collation in common
 * Kotlin to do better.
 *
 * Values of two different classes compare equal. One field holds one kind, so this is reachable
 * only through a description that changed under a set that was already loaded.
 */
@Suppress("UNCHECKED_CAST")
private fun compared(one: Any, other: Any): Int = when {
    one is String && other is String -> one.compareTo(other, ignoreCase = true)
    one is Comparable<*> && one::class == other::class -> (one as Comparable<Any>).compareTo(other)
    else -> 0
}
