package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription
import yemoja.data.WholeNumberDescription

/*
 * What a person is, with the medical, the insurance and the courses they own.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * Medical is a person's health details, kept together rather than scattered through the item.
 *
 * Yemoja does not work out whether one is still valid. How long a check counts for depends on
 * who is asking — the agency, the operator, the country — rather than on the examination.
 */
private val MEDICAL = ItemDescription(
    "medical",
    listOf(
        DateDescription("last_medical_check"),
        TextDescription("blood_group"),
        NumberDescription("height", Dimension.LENGTH),
        NumberDescription("body_mass", Dimension.MASS),
        REMARKS,
    ),
)

/**
 * Insurance is the cover a person holds.
 *
 * Absent so far: nothing of its own.
 */
private val INSURANCE = ItemDescription(
    "insurance",
    listOf(
        TextDescription("name"),
        TextDescription("policy"),
        DateDescription("start_date"),
        DateDescription("end_date"),
        // Against today, which is the only thing here worked out from outside the logbook.
        WholeNumberDescription("days_left", role = Role.Derived(::insurancesDaysLeft)),
        BooleanDescription("expired", role = Role.Derived(::insurancesExpired)),
        REMARKS,
    ),
)

private fun insurancesDaysLeft(cover: Item): Result<Any> = remaining(cover, "end_date")

private fun insurancesExpired(cover: Item): Result<Any> = passed(cover, "end_date")

/** Course is one qualification a person earned, under a key on that person. */
private val COURSE = ItemDescription(
    "course",
    listOf(
        ReferenceDescription("certification", targetType = "certification"),
        // The number on the card, which names this award rather than the qualification.
        TextDescription("number"),
        DateDescription("date"),
        // Whose own number is on them rather than here: a course points at the person.
        ReferenceDescription("instructor", targetType = "person"),
        ReferenceDescription("dives", targetType = "dive", cardinality = Cardinality.LIST),
        REMARKS,
    ),
    proposedId = ::coursesProposedKey,
)

/**
 * Person is anyone who appears in a logbook, whether or not they dive.
 *
 * Absent so far: nothing of their own.
 */
internal val PERSON: ItemDescription = ItemDescription(
    "person",
    listOf(
        // Worked out from the parts, and corrected where the assembly reads wrong: names do
        // not all follow one pattern. An id is worked out from this.
        TextDescription("name", role = Role.Overrideable(::assembledName)),
        TextDescription("first_name"),
        // All of them together, where there are several.
        TextDescription("middle_names"),
        TextDescription("last_name"),
        DateDescription("birthday"),
        TextDescription("email"),
        TextDescription("phone"),
        TextDescription("address"),
        // Their own number as an instructor. Text: letters and leading zeros are ordinary
        // and nothing is added up.
        TextDescription("instructor_number"),
        // A plain name stands in for someone with no item of their own, which is what makes
        // this the one reference here that allows one.
        ReferenceDescription(
            "emergency_contacts",
            targetType = "person",
            cardinality = Cardinality.LIST,
            oneOffAllowed = true,
        ),
        OwnedItemDescription("medical", MEDICAL),
        OwnedItemDescription("insurance", INSURANCE),
        OwnedItemDescription("courses", COURSE, cardinality = Cardinality.KEYED),
        // Every dive naming this person among its buddies, and for the logbook's user every
        // dive, each one being theirs. Never written: each dive says who was there. `DATA-118`.
        ReferenceDescription(
            "dives",
            targetType = "dive",
            cardinality = Cardinality.LIST,
            role = Role.Derived(::personsDives),
        ),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)

/**
 * A person's full name, assembled from the parts.
 *
 * The parts in order, separated by spaces, and whichever of them are missing left out. Absent
 * where none of them is there, so an empty person is not named after a run of spaces.
 */
private fun assembledName(person: Item): Result<Any> {
    val parts = NAME_PARTS
        .mapNotNull { (person.single<String>(it) as? Result.Usable)?.value }
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return Result.Absent
    return Result.Usable(parts.joinToString(" "), Result.Origin.DERIVED)
}

private val NAME_PARTS = listOf("first_name", "middle_names", "last_name")

/** The dives naming [person] as a buddy, or every dive where [person] is the user. */
private fun personsDives(person: Item): Result<Any> {
    val id = (person as? ReferenceableItem)?.let { person.set.idOf(it) }
    if (id != null && id == person.set.user?.id) {
        return referencesTo(person.set, person.set.allOf(Types.DIVE))
    }
    return pointingAt(person, Types.DIVE, Naming("buddies"))
}
