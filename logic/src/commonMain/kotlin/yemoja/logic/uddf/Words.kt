package yemoja.logic.uddf

/*
 * The closed vocabularies UDDF and this model both have, and how a word crosses between them.
 *
 * See ../../../../../../doc.md — the comparison is logic/uddf.md.
 */

/**
 * Words is one vocabulary on both sides: this model's word for each of UDDF's, and back.
 *
 * **Going out is a table and coming in is another**, because some words are joined there and
 * apart here. `ocean` and `sea` both leave as `ocean-sea`, and `ocean-sea` comes back as the
 * first of the two; a round trip cannot tell which half it started from. `uddf.md`, under *Where
 * the two disagree*.
 *
 * A word neither table knows is left out rather than carried across, since a closed list on
 * either side refuses it anyway.
 */
internal class Words(
    private val outward: Map<String, String>,
    private val inward: Map<String, String>,
) {

    /** UDDF's word for [ours], or absent where it has none. */
    fun theirs(ours: String): String? = outward[ours.lowercase()]

    /** This model's word for [theirs], or absent where it has none. */
    fun ours(theirs: String): String? = inward[theirs.trim().lowercase()]
}

/** A current, which this model took from UDDF step for step. `DATA-45`. */
internal val CURRENTS: Words = Words(
    outward = mapOf(
        "none" to "no-current",
        "very mild" to "very-mild-current",
        "mild" to "mild-current",
        "moderate" to "moderate-current",
        "hard" to "hard-current",
        "very hard" to "very-hard-current",
    ),
    inward = mapOf(
        "no-current" to "none",
        "very-mild-current" to "very mild",
        "mild-current" to "mild",
        "moderate-current" to "moderate",
        "hard-current" to "hard",
        "very-hard-current" to "very hard",
    ),
)

/**
 * How warm the diver was, which UDDF says in four words and this model suggests five for.
 *
 * `warm` and `too warm` both leave as `hot`, and `hot` comes back as `too warm`: UDDF offers it
 * beside `comfortable` as the complaint, which is what `too warm` is.
 */
internal val WARMTHS: Words = Words(
    outward = mapOf(
        "very cold" to "very-cold",
        "cold" to "cold",
        "good" to "comfortable",
        "warm" to "hot",
        "too warm" to "hot",
    ),
    inward = mapOf(
        "very-cold" to "very cold",
        "cold" to "cold",
        "comfortable" to "good",
        "hot" to "too warm",
    ),
)

/** What a dive site is, eleven words here against seven there. `uddf.md`. */
internal val ENVIRONMENTS: Words = Words(
    outward = mapOf(
        "ocean" to "ocean-sea",
        "sea" to "ocean-sea",
        "lake" to "lake-quarry",
        "quarry" to "lake-quarry",
        "river" to "river-spring",
        "spring" to "river-spring",
        "cave" to "cave-cavern",
        "cavern" to "cave-cavern",
        "under ice" to "under-ice",
        "pool" to "pool",
        "hyperbaric chamber" to "hyperbaric-chamber",
    ),
    inward = mapOf(
        "ocean-sea" to "ocean",
        "lake-quarry" to "lake",
        "river-spring" to "river",
        "cave-cavern" to "cave",
        "under-ice" to "under ice",
        "pool" to "pool",
        "hyperbaric-chamber" to "hyperbaric chamber",
    ),
)
