package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.DateDescription
import yemoja.data.Direction
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.Ordering
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription

/*
 * What a dive trip is, and what the dives on it make of it.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * DiveTrip is diving done on one occasion or in one place.
 *
 * Absent so far: nothing of its own.
 */
internal val DIVE_TRIP: ItemDescription = ItemDescription(
    "dive_trip",
    listOf(
        TextDescription("name"),
        // From the dives on it, and corrected where the trip was longer than the diving:
        // a travelling day at either end, or a trip set up before anything was logged.
        DateDescription("start_date", role = Role.Overrideable(::tripsStartDate)),
        DateDescription("end_date", role = Role.Overrideable(::tripsEndDate)),
        ReferenceDescription("region", targetType = "region"),
        ReferenceDescription("operator", targetType = "operator"),
        // The larger trip this one is part of, where there is one.
        ReferenceDescription("parent", targetType = "dive_trip"),
        // The other side of `parent`. A leg names the trip; the trip does not name its legs.
        ReferenceDescription(
            "parts",
            targetType = "dive_trip",
            cardinality = Cardinality.LIST,
            role = Role.Derived(::tripsParts),
        ),
        // Every dive naming this trip, and every dive of a trip beneath it. Never written:
        // each dive says which trip it belongs to, so the two cannot disagree.
        ReferenceDescription(
            "dives",
            targetType = "dive",
            cardinality = Cardinality.LIST,
            role = Role.Derived(::tripsDives),
        ),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("start_date", Direction.DESCENDING)),
    proposedId = ::namedById,
)

/**
 * The trips naming [trip] as their parent.
 *
 * The other side of `parent`, gathered the way [regionsChildren] is.
 */
private fun tripsParts(trip: Item): Result<Any> =
    pointingAt(trip, Types.DIVE_TRIP, Naming("parent"))

/**
 * Every dive made on a trip, and on any trip beneath it.
 *
 * A dive names the leg it was on rather than the fortnight, so a trip gathers from itself and
 * from everything naming it, however deep that goes.
 *
 * **A trip that is its own ancestor is a fault, not a shorter answer.** `LOGIC-8` leaves what a
 * cycle means to each derivation, and here it would count some dives twice or never stop, so
 * the walk reports it rather than quietly surviving.
 */
private fun tripsDives(trip: Item): Result<Any> {
    val id = (trip as? ReferenceableItem)?.let { trip.set.idOf(it) } ?: return Result.Absent
    val beneath = LinkedHashSet<String>()
    beneath.add(id)
    var edge = listOf(id)
    while (edge.isNotEmpty()) {
        val next = trip.set.allOf(Types.DIVE_TRIP)
            .filter { child -> namesIn(child, "parent").any { it in edge } }
            .mapNotNull { trip.set.idOf(it) }
        // A trip has one parent, so meeting one twice is a chain that has come back on itself
        // rather than two routes to the same place.
        for (each in next) {
            if (!beneath.add(each)) {
                return unusable("$each is inside itself, so its dives cannot be gathered")
            }
        }
        edge = next
    }
    val found = trip.set.allOf(Types.DIVE)
        .filter { dive -> tripOf(dive) in beneath }
        .mapNotNull { trip.set.idOf(it) }
        .map { Element.Usable(Reference.Identified(it) as Any) }
    return Result.Usable(found, Result.Origin.DERIVED)
}

/** Which trip a dive says it was on, which sits on its details rather than on the dive. */
private fun tripOf(dive: Item): String? {
    val details = (dive.single<OwnedItem>("details") as? Result.Usable)?.value ?: return null
    return namesIn(details, "dive_trip").firstOrNull()
}

/** When a trip ran, taken from the dives on it. */
private fun tripsStartDate(trip: Item): Result<Any> = spanOf(trip) { it.min() }

private fun tripsEndDate(trip: Item): Result<Any> = spanOf(trip) { it.max() }

/**
 * One end of the range of dates the dives on [trip] cover.
 *
 * A dive with no date of its own says nothing about when the trip ran and is passed over. Absent
 * where none of them has one, which a trip set up before any diving has by definition.
 */
private fun spanOf(trip: Item, pick: (List<Date>) -> Date): Result<Any> {
    val gathered = tripsDives(trip)
    if (gathered !is Result.Usable) return gathered
    @Suppress("UNCHECKED_CAST")
    val dates = (gathered.value as List<Element<Any>>)
        .mapNotNull { (it as? Element.Usable)?.value as? Reference.Identified }
        .mapNotNull { trip.set[it.id] }
        .mapNotNull { (it.single<Date>("start_date") as? Result.Usable)?.value }
    if (dates.isEmpty()) return Result.Absent
    return Result.Usable(pick(dates), Result.Origin.DERIVED)
}
