package yemoja.logic

import yemoja.data.ItemDescription

/*
 * Every item type the application knows, gathered. What a dive, a person, a place and a piece
 * of gear are is written one stored type to a file beside this one, each holding its own
 * fields, the words the manual suggests for them, the values worked out from them, and the
 * items it owns.
 *
 * The machinery that reads and resolves these is in the data layer, which knows nothing about
 * diving; the descriptions are here because they do.
 *
 * manual/data-fields.md is the source of truth for every field. Where this and that document
 * disagree, that one is right and this one is a bug.
 *
 * See ../../../../../doc.md — "Scope".
 */

/**
 * Types is every item type the application knows.
 *
 * An `ItemSet` is built with these, so this list is what the whole application shares as its
 * vocabulary. A front end may name a type; it may not describe one.
 *
 * **Every item type the manual defines, and every shape a field can take.** What is absent is
 * a field that is worked out rather than recorded, wherever what it would be worked out from is
 * not there to work from. Each type says which of its own are missing.
 *
 * The nine here are the ones a logbook stores files of. The eleven an item owns are reached
 * through the type that owns them, and are described in that type's file rather than here.
 */
object Types {

    /** Described in `Dive.kt`, with everything worked out from it. */
    val DIVE: ItemDescription = yemoja.logic.DIVE

    /** Described in `DiveSite.kt`, with everything worked out from it. */
    val DIVE_SITE: ItemDescription = yemoja.logic.DIVE_SITE

    /** Described in `DiveTrip.kt`, with everything worked out from it. */
    val DIVE_TRIP: ItemDescription = yemoja.logic.DIVE_TRIP

    /** Described in `Person.kt`, with everything worked out from it. */
    val PERSON: ItemDescription = yemoja.logic.PERSON

    /** Described in `Gear.kt`, with everything worked out from it. */
    val GEAR: ItemDescription = yemoja.logic.GEAR

    /** Described in `Certification.kt`, with everything worked out from it. */
    val CERTIFICATION: ItemDescription = yemoja.logic.CERTIFICATION

    /** Described in `Operator.kt`, with everything worked out from it. */
    val OPERATOR: ItemDescription = yemoja.logic.OPERATOR

    /** Described in `Region.kt`, with everything worked out from it. */
    val REGION: ItemDescription = yemoja.logic.REGION

    /**
     * Every type, which is what an item set is built with.
     *
     * In the order an interface offers them: the dive, then the trip it was made on and the
     * gear it was made in; then where, from the largest thing to the smallest, a region holding
     * sites; then who, and what they award. A front end takes this
     * order as given. `UI-3`.
     */
    val ALL: List<ItemDescription> = listOf(
        DIVE,
        DIVE_TRIP,
        GEAR,
        REGION,
        DIVE_SITE,
        PERSON,
        OPERATOR,
        CERTIFICATION,
    )
}
