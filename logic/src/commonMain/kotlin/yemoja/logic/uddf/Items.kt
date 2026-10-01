package yemoja.logic.uddf

import yemoja.data.Stored

/*
 * One tag of a UDDF document as one item's fields, a type at a time.
 *
 * Each mapping is `logic/uddf.md`'s table written out, and cites it rather than arguing again.
 * Nothing converts a unit: UDDF is strict SI, the reading is handed those units, and the model
 * does the arithmetic it already does.
 *
 * See ../../../../../../doc.md.
 */

/**
 * What a `wreck` inside a site says, as a paragraph for the site's remarks.
 *
 * There is no wreck here: a ship is a thing a site is dived for, and what is known of it is said
 * where the site is. Each part is written as UDDF names it, with its value as written there, so a
 * reader can judge what a figure means. `uddf.md`.
 */
internal fun wreckSaid(tag: Tag): String? {
    val built = tag.find("built", APART)
    val size = tag.find("shipdimension", APART)
    val parts = listOfNotNull(
        tag.said("name")?.let { "Wreck: $it" },
        aliasesIn(tag).takeIf { it.isNotEmpty() }?.let { "also called ${it.joinToString(", ")}" },
        tag.said("shiptype")?.let { "ship type $it" },
        tag.said("nationality")?.let { "nationality $it" },
        built?.said("shipyard")?.let { "built by $it" },
        built?.date("launchingdate")?.let { "launched $it" },
        tag.date("sunk")?.let { "sunk $it" },
        size?.said("length")?.let { "length $it m" },
        size?.said("beam")?.let { "beam $it m" },
        size?.said("draught")?.let { "draught $it m" },
        size?.said("displacement")?.let { "displacement $it kg" },
        tag.said("tonnage")?.let { "tonnage $it" },
    )
    val notes = tag.prose("notes")
    val said = listOfNotNull(parts.joinToString(", ").ifEmpty { null }, notes).joinToString("\n")
    return said.ifEmpty { null }
}

/** A `site`, the wreck it holds said in its remarks. */
internal fun siteIn(tag: Tag): Map<String, Stored> {
    val fields = LinkedHashMap<String, Stored>()
    fields.put("name", tag.said("name"))
    fields.putAll("alternative_names", aliasesIn(tag))
    val where = tag.find("geography", APART)
    fields.put("latitude", where?.said("latitude"))
    fields.put("longitude", where?.said("longitude"))
    fields.put("elevation", where?.said("altitude"))
    fields.put("environment_type", tag.said("environment")?.let { ENVIRONMENTS.ours(it) })
    fields.put("max_depth", tag.said("maximumdepth"))
    fields.put("substrate", tag.said("bottom"))
    fields.put("rating", ratingIn(tag))
    val wreck = tag.find("wreck", APART)?.let { wreckSaid(it) }
    val said = listOfNotNull(tag.prose("notes"), wreck).joinToString("\n\n")
    fields.put("remarks", said.ifEmpty { null })
    return fields
}

/** A `divebase`, which is the only one of UDDF's two operators that is an item. `uddf.md`. */
internal fun operatorIn(tag: Tag): Map<String, Stored> {
    val fields = LinkedHashMap<String, Stored>()
    fields.put("name", tag.said("name"))
    fields.putAll("alternative_names", aliasesIn(tag))
    fields.put("address", addressIn(tag))
    fields.put("phone", tag.said("phone"))
    fields.put("email", tag.said("email"))
    fields.put("website", tag.said("homepage") ?: tag.said("website"))
    fields.put("rating", ratingIn(tag))
    fields.put("remarks", tag.prose("notes"))
    return fields
}

/** An `owner` or a `buddy`, which are one type here named once in the manifest. `uddf.md`. */
internal fun personIn(tag: Tag): Map<String, Stored> {
    val fields = LinkedHashMap<String, Stored>()
    fields.put("first_name", tag.said("firstname"))
    fields.put("middle_names", tag.said("middlename"))
    fields.put("last_name", tag.said("lastname"))
    fields.put("birthday", tag.date("birthdate"))
    fields.put("address", addressIn(tag))
    fields.put("email", tag.said("email"))
    fields.put("phone", tag.said("phone"))
    fields.put("medical", medicalIn(tag))
    fields.put("courses", coursesIn(tag))
    fields.put("remarks", tag.prose("notes"))
    return fields
}

/**
 * A piece of equipment, as the category and kind its element stands for.
 *
 * Purchase information has nowhere to go: `FEAT-20` rules money out.
 */
internal fun gearIn(tag: Tag): Map<String, Stored> {
    val fields = LinkedHashMap<String, Stored>()
    val (category, kind) = KIT[tag.name] ?: (null to null)
    fields.put("name", tag.said("name"))
    fields.put("brand", tag.find("manufacturer", APART)?.said("name"))
    fields.put("model", tag.said("model"))
    fields.put("serial", tag.said("serialnumber"))
    fields.put("category", category)
    fields.put("kind", kind)
    fields.put("remarks", tag.prose("notes"))
    return fields
}

/**
 * A `trippart`, which is where everything of a trip lands.
 *
 * A trip carries the same fields whether or not it has a parent, so the part is the item and the
 * `trip` above it is one too where it names anything of its own. `uddf.md`.
 */
internal fun tripIn(tag: Tag, ours: Map<String, String>): Map<String, Stored> {
    val fields = LinkedHashMap<String, Stored>()
    fields.put("name", tag.said("name"))
    val dates = tag.find("dateoftrip", APART)
    fields.put("start_date", dates?.date("startdate") ?: dates?.date("datetime"))
    fields.put("end_date", dates?.date("enddate"))
    fields.put("operator", tag.points("operator", ours))
    fields.put("remarks", tag.prose("notes"))
    return fields
}

/** Every name besides the first that a tag goes by. */
private fun aliasesIn(tag: Tag): List<String> =
    tag.all("aliasname").mapNotNull { it.said.trim().ifEmpty { null } }

/** The address UDDF keeps in parts and this model keeps as prose. Lossy going back. `uddf.md`. */
private fun addressIn(tag: Tag): String? {
    val held = tag.find("address", APART) ?: return null
    val parts = listOf("street", "city", "postcode", "country").mapNotNull { held.said(it) }
    return parts.joinToString(", ").ifEmpty { null }
}

/**
 * The rating a tag carries, which UDDF may hold more than one of.
 *
 * The latest by `datetime` is taken and the rest dropped, which loses history without misstating
 * anything: this model holds the rating that stands rather than every one ever formed.
 */
private fun ratingIn(tag: Tag): String? {
    val ratings = tag.everywhere("rating", APART)
    if (ratings.isEmpty()) return null
    val latest = ratings.maxByOrNull { it.said("datetime").orEmpty() } ?: return null
    return latest.said("ratingvalue") ?: latest.said.trim().ifEmpty { null }
}

/**
 * What a person's examinations say, which here is one date rather than a history.
 *
 * `DATA-37` settled that this model records the state rather than the examinations, so the latest
 * one's date lands and the doctor, the result and the rest do not.
 */
private fun medicalIn(tag: Tag): Map<String, Stored> {
    val held = tag.find("medical", setOf("buddy", "owner")) ?: return emptyMap()
    val latest = held.everywhere("examination").mapNotNull { it.date("datetime") }.maxOrNull()
        ?: return emptyMap()
    return mapOf("last_medical_check" to Stored.Leaf(latest))
}

/**
 * What a person's education says, as this model's courses.
 *
 * UDDF puts the qualification and the holding of it in one place; here a course points at a
 * certification a library supplies, which a document cannot name. So what survives is the date
 * and the number, and the certification is left for the user to fill in. `uddf.md`.
 */
private fun coursesIn(tag: Tag): Map<String, Stored> {
    val held = tag.find("education", setOf("buddy", "owner")) ?: return emptyMap()
    val courses = LinkedHashMap<String, Stored>()
    for ((at, one) in held.everywhere("certification").withIndex()) {
        val fields = LinkedHashMap<String, Stored>()
        fields.put("date", one.date("issuedate"))
        fields.put("number", one.said("certificatenumber"))
        fields.put("remarks", listOfNotNull(one.said("level"), one.said("organisation"))
            .joinToString(", ").ifEmpty { null })
        if (fields.isEmpty()) continue
        courses["course${if (at == 0) "" else "#$at"}"] = Stored.Members(fields)
    }
    return courses
}
