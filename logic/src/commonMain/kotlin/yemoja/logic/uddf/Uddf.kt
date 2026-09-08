package yemoja.logic.uddf

import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.ReferenceableItem
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.DIVE
import yemoja.logic.Types
import yemoja.logic.freeName
import yemoja.logic.unknownOf

/*
 * A UDDF document read into this model's items.
 *
 * See ../../../../../../doc.md — the field-by-field mapping is logic/uddf.md, and every row
 * below cites it rather than arguing again.
 */

/**
 * Uddf reads a UDDF document into an item set of this model's own.
 *
 * **Nothing is converted by hand.** UDDF is strict SI and says so, and `DATA-61` already has
 * `K`, `m3` and `Pa` among the names a file may declare, so the reading is handed those units
 * and the arithmetic is the one the model already does. What is left here is names and shape.
 *
 * **What is read is dives.** Sites, people, equipment, trips, operators and wrecks are mapped in
 * `uddf.md` and are not read yet.
 */
object Uddf {

    /** The units a UDDF document is written in, which its own specification fixes. */
    private val SI: Units = Units.of(
        mapOf("temperature" to "K", "volume" to "m3", "pressure" to "Pa"),
    )

    /** What a dive's fields are spread over, and the one part of it not to look in for them. */
    private const val SAMPLES = "samples"

    /**
     * The items [text] holds.
     *
     * Ids are minted the way this model mints them — a dive is named for the day it was made on
     * — rather than taken from the document. A UDDF id is a name for one document's own use and
     * says nothing outside it, so carrying one in would put a stranger's word where this model
     * has a rule.
     */
    fun read(text: String): ItemSet {
        val set = ItemSet(Types.ALL)
        for (tag in tagsIn(text).all("profiledata")) {
            for (group in tag.all("repetitiongroup")) {
                for (dive in group.all("dive")) add(diveOf(dive, set), set)
            }
        }
        return set
    }

    /** Put [item] in [set] under the first id free from what its own type proposes. */
    private fun add(item: ReferenceableItem, set: ItemSet) {
        val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
        set.add(freeName(proposed) { set[it] != null }, item)
    }

    /** One `dive`, with the parts of it this model keeps. */
    private fun diveOf(dive: Tag, set: ItemSet): ReferenceableItem {
        val fields = LinkedHashMap<String, Stored>()
        val said = { name: String -> dive.find(name, setOf(SAMPLES))?.said?.trim()?.ifEmpty { null } }
        said("divenumber")?.let { fields["dive_number"] = Stored.Leaf(it) }
        said("datetime")?.let {
            fields["start_date"] = Stored.Leaf(it.substringBefore('T'))
            timeIn(it)?.let { at -> fields["start_time"] = Stored.Leaf(at) }
        }
        said("greatestdepth")?.let { fields["max_depth"] = Stored.Leaf(it) }
        said("averagedepth")?.let { fields["average_depth"] = Stored.Leaf(it) }
        // `ratingvalue` is where the figure sits; its own text is taken as well, so a document
        // that writes it plainly is read rather than refused.
        val rating = dive.find("rating", setOf(SAMPLES))
        (rating?.text("ratingvalue") ?: rating?.said?.trim()?.ifEmpty { null })
            ?.let { fields["rating"] = Stored.Leaf(it) }
        environmentOf(dive, said)?.let { fields["environment"] = it }
        gearOf(said)?.let { fields["gear"] = it }
        detailsOf(dive)?.let { fields["details"] = it }
        profileOf(dive)?.let { fields["profiles"] = it }
        return ItemReader.read(DIVE, Stored.Members(fields), set, SI)
    }

    /** The conditions of a dive, or nothing where the document said none of them. */
    private fun environmentOf(dive: Tag, said: (String) -> String?): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        said("current")?.let { fields["current"] = Stored.Leaf(it) }
        said("visibility")?.let { fields["visibility"] = Stored.Leaf(it) }
        said("airtemperature")?.let { fields["air_temperature"] = Stored.Leaf(it) }
        said("lowesttemperature")?.let { fields["bottom_temperature"] = Stored.Leaf(it) }
        return fields.ifEmpty { null }?.let { Stored.Members(it) }
    }

    /** What a dive says about how warm the diver was, which this model keeps with the gear. */
    private fun gearOf(said: (String) -> String?): Stored.Members? =
        said("thermalcomfort")?.let {
            Stored.Members(mapOf("temperature_evaluation" to Stored.Leaf(it)))
        }

    /** A dive's notes, which are prose and may be spread over paragraphs. */
    private fun detailsOf(dive: Tag): Stored.Members? {
        val notes = dive.find("notes", setOf(SAMPLES)) ?: return null
        val said = notes.all("para").joinToString("\n") { it.everything() }.ifEmpty {
            notes.everything()
        }
        return said.ifEmpty { null }?.let {
            Stored.Members(mapOf("remarks" to Stored.Leaf(it)))
        }
    }

    /**
     * The one recording a UDDF dive holds, as this model's profiles.
     *
     * UDDF keeps one time-ordered list of waypoints, each carrying whatever was measured at that
     * instant; this model keeps a series per quantity, each with its own times. So a waypoint
     * contributes a point to each series it has a value for and nothing to the others, which is
     * what makes the shapes convertible without padding.
     */
    private fun profileOf(dive: Tag): Stored.Members? {
        val waypoints = dive.one(SAMPLES)?.all("waypoint").orEmpty()
        if (waypoints.isEmpty()) return null
        val fields = LinkedHashMap<String, Stored>()
        for ((ours, theirs) in SERIES) {
            seriesOf(waypoints, theirs)?.let { fields[ours] = it }
        }
        if (fields.isEmpty()) return null
        // One profile, under the key its own type proposes for a recording naming no computer.
        return Stored.Members(mapOf("profile" to Stored.Members(fields)))
    }

    /** One quantity across [waypoints], as a time and a value apiece. */
    private fun seriesOf(waypoints: List<Tag>, name: String): Stored.Elements? {
        val points = ArrayList<Stored>()
        for (waypoint in waypoints) {
            val at = waypoint.text("divetime") ?: continue
            val value = waypoint.text(name) ?: continue
            // The time is made a whole number here rather than left as text for the field to
            // read, because a series refuses one that is not: the times are what holds it
            // together rather than one of the values in it. Where it will not, the text goes in
            // and the refusal says what was written.
            points += Stored.Elements(listOf(Stored.Leaf(at.toIntOrNull() ?: at), Stored.Leaf(value)))
        }
        return if (points.isEmpty()) null else Stored.Elements(points)
    }

    /**
     * The time in a UDDF datetime, without the zone it may carry after it.
     *
     * A time is digits, colons and at most a decimal point, so it ends where the first of
     * anything else begins — `Z`, or the sign of an offset. The offset itself is dropped rather
     * than kept: `gmt_offset` sits on the profile and is not read yet, and putting the local
     * time in while saying nothing about the zone is what the file itself does.
     */
    private fun timeIn(said: String): String? =
        said.substringAfter('T', "")
            .takeWhile { it.isDigit() || it == ':' || it == '.' }
            .ifEmpty { null }

    /** Each series this model keeps, and the waypoint child it is read from. `uddf.md`. */
    private val SERIES = listOf(
        "depth" to "depth",
        "temperature" to "temperature",
        "no_deco_time" to "nodecotime",
        "cns" to "cns",
        "otu" to "otu",
    )
}
