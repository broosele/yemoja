package yemoja.logic.uddf

import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Stored
import yemoja.data.Units
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
 * **Items are read in the order they point in**, so that a reference is resolved by the time
 * something needs it: a wreck before the site that names it, an operator before the trip, and
 * dives last, since a dive points at almost everything. A `link`'s `ref` is looked up in what has
 * been read so far and becomes this model's own reference.
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
        val root = tagsIn(text)
        val set = ItemSet(Types.ALL)
        // What this reader called each thing the document names, by the name the document used.
        val ours = HashMap<String, String>()
        for (tag in root.everywhere("wreck")) put(Types.WRECK, wreckIn(tag), tag, set, ours)
        for (tag in root.everywhere("site")) {
            val wreck = tag.one("wreck")?.attributes?.get("id")?.let { ours[it] }
            put(Types.DIVE_SITE, siteIn(tag, wreck), tag, set, ours)
        }
        for (tag in root.everywhere("divebase")) {
            put(Types.OPERATOR, operatorIn(tag), tag, set, ours)
        }
        for (name in listOf("owner", "buddy")) {
            for (tag in root.everywhere(name)) {
                put(Types.PERSON, personIn(tag), tag, set, ours)
            }
        }
        for (name in GEAR) {
            for (tag in root.everywhere(name)) put(Types.GEAR, gearIn(tag), tag, set, ours)
        }
        // A trip's dives are read the other way about: a dive names its trip and the trip gathers
        // them, so what a `relateddives` says is remembered and used when the dives are read.
        val trips = HashMap<String, String>()
        for (tag in root.everywhere("trippart")) {
            val id = put(Types.DIVE_TRIP, tripIn(tag, ours), tag, set, ours) ?: continue
            for (link in tag.find("relateddives", APART)?.all("link").orEmpty()) {
                link.attributes["ref"]?.let { trips[it] = id }
            }
        }
        for (group in root.everywhere("repetitiongroup")) {
            var before: String? = null
            for (dive in group.all("dive")) {
                val id = put(Types.DIVE, diveIn(dive, ours, trips, before), dive, set, ours)
                before = id ?: before
            }
        }
        return set
    }

    /**
     * Put an item of [description] holding [fields] in [set], and remember what it was called.
     *
     * The id is this model's own, minted from what the item says: a UDDF id is a name for one
     * document's use and says nothing outside the file. What it *was* called is kept only so that
     * the links pointing at it can be followed.
     */
    private fun put(
        description: ItemDescription,
        fields: Map<String, Stored>,
        tag: Tag,
        set: ItemSet,
        ours: MutableMap<String, String>,
    ): String? {
        if (fields.isEmpty()) return null
        val item = ItemReader.read(description, Stored.Members(fields), set, SI)
        val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
        val id = freeName(proposed) { set[it] != null }
        set.add(id, item)
        tag.attributes["id"]?.let { ours[it] = id }
        return id
    }

    /**
     * One `dive`, with the parts of it this model keeps.
     *
     * [before] is the dive read just before it in the same `repetitiongroup`, which is what
     * `previous_dive` is: this model does not group, and the chain a group describes carries the
     * same information. `DATA-60`. The first of a group takes none, which is what `<infinity/>`
     * says there.
     */
    private fun diveIn(
        dive: Tag,
        ours: Map<String, String>,
        trips: Map<String, String>,
        before: String?,
    ): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        val said = { name: String -> dive.said(name) }
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
        fields.put("dive_site", dive.points("informationbeforedive", ours))
        if (before != null) fields["previous_dive"] = Stored.Leaf("@$before")
        // Kept only on the first of a group, where it describes a dive the file does not hold.
        // Anywhere else the two dives it sits between are both here and their clocks say it.
        if (before == null) {
            fields.put("surface_interval", dive.find("surfaceintervalbeforedive", SAMPLES_APART)
                ?.said("passedtime"))
        }
        environmentOf(dive, said)?.let { fields["environment"] = it }
        gearOf(dive, said, ours)?.let { fields["gear"] = it }
        detailsOf(dive, trips)?.let { fields["details"] = it }
        profileOf(dive)?.let { fields["profiles"] = it }
        gasesOf(dive, ours)?.let { fields["gas_sources"] = it }
        return fields
    }

    private val SAMPLES_APART = setOf(SAMPLES)

    /** The conditions of a dive, or nothing where the document said none of them. */
    private fun environmentOf(dive: Tag, said: (String) -> String?): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        said("current")?.let { fields["current"] = Stored.Leaf(it) }
        said("visibility")?.let { fields["visibility"] = Stored.Leaf(it) }
        said("airtemperature")?.let { fields["air_temperature"] = Stored.Leaf(it) }
        said("lowesttemperature")?.let { fields["bottom_temperature"] = Stored.Leaf(it) }
        return fields.ifEmpty { null }?.let { Stored.Members(it) }
    }

    /** What a dive wore, and what it says about how warm the diver was. */
    private fun gearOf(
        dive: Tag,
        said: (String) -> String?,
        ours: Map<String, String>,
    ): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        fields.put("temperature_evaluation", said("thermalcomfort"))
        val used = dive.find("equipmentused", SAMPLES_APART)?.all("link").orEmpty()
            .mapNotNull { it.attributes["ref"] }.mapNotNull { ours[it] }
        if (used.isNotEmpty()) {
            fields["items"] = Stored.Elements(used.map { Stored.Leaf("@$it") })
        }
        return fields.ifEmpty { null }?.let { Stored.Members(it) }
    }

    /**
     * The gas a dive breathed, which UDDF keeps in two places and this model in one.
     *
     * A `mix` defines a gas once for the whole file and a `tankdata` holds one cylinder's
     * pressures for one dive; a gas source is both at once. `uddf.md`.
     */
    private fun gasesOf(dive: Tag, ours: Map<String, String>): Stored.Members? {
        val tanks = dive.everywhere("tankdata", SAMPLES_APART)
        if (tanks.isEmpty()) return null
        val sources = LinkedHashMap<String, Stored>()
        for ((at, tank) in tanks.withIndex()) {
            val fields = LinkedHashMap<String, Stored>()
            fields.put("start_pressure", tank.said("tankpressurebegin"))
            fields.put("end_pressure", tank.said("tankpressureend"))
            fields.put("volume", tank.said("tankvolume"))
            tank.all("link").firstNotNullOfOrNull { ours[it.attributes["ref"]] }
                ?.let { fields["cylinder"] = Stored.Leaf("@$it") }
            if (fields.isEmpty()) continue
            sources["gas${if (at == 0) "" else "#$at"}"] = Stored.Members(fields)
        }
        return sources.ifEmpty { null }?.let { Stored.Members(it) }
    }

    /** A dive's notes and the trip it belonged to, which this model keeps together. */
    private fun detailsOf(dive: Tag, trips: Map<String, String>): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        fields.put("remarks", dive.prose("notes"))
        fields.put("dive_trip", dive.attributes["id"]?.let { trips[it] }?.let { "@$it" })
        return fields.ifEmpty { null }?.let { Stored.Members(it) }
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
            val when_ = Stored.Leaf(at.toIntOrNull() ?: at)
            points += Stored.Elements(listOf(when_, Stored.Leaf(value)))
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
