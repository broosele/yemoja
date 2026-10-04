package yemoja.logic.uddf

import yemoja.data.Gas
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.Types
import yemoja.logic.freeName
import yemoja.logic.unknownOf
import yemoja.logic.untilSurfaced

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
 * something needs it: a site before the dive at it, an operator before the trip, and
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
        for (tag in root.everywhere("site")) {
            put(Types.DIVE_SITE, siteIn(tag), tag, set, ours)
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
        // A `mix` is defined once for the whole document and named by id from within a dive,
        // so which ids are mixes has to be known before a dive's switches can be read.
        val mixes = HashMap<String, String?>()
        for (mix in root.everywhere("mix")) mix.attributes["id"]?.let { mixes[it] = gasIn(mix) }
        for (group in root.everywhere("repetitiongroup")) {
            var before: String? = null
            for (dive in group.all("dive")) {
                val fields = diveIn(dive, ours, trips, before, mixes, set)
                val id = put(Types.DIVE, fields, dive, set, ours)
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
        mixes: Map<String, String?>,
        set: ItemSet,
    ): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        val said = { name: String -> dive.said(name) }
        said("divenumber")?.let { fields["dive_number"] = Stored.Leaf(it) }
        said("datetime")?.let {
            fields["start_date"] = Stored.Leaf(it.substringBefore('T'))
            timeIn(it)?.let { at -> fields["start_time"] = Stored.Leaf(at) }
            zoneIn(it)?.let { offset -> fields["time_zone_offset"] = Stored.Leaf(offset) }
        }
        said("greatestdepth")?.let { fields["max_depth"] = Stored.Leaf(it) }
        said("averagedepth")?.let { fields["average_depth"] = Stored.Leaf(it) }
        // `ratingvalue` is where the figure sits; its own text is taken as well, so a document
        // that writes it plainly is read rather than refused.
        val rating = dive.find("rating", setOf(SAMPLES))
        (rating?.text("ratingvalue") ?: rating?.said?.trim()?.ifEmpty { null })
            ?.let { fields["rating"] = Stored.Leaf(it) }
        val linked = linksIn(dive, ours, set)
        linked[Types.DIVE_SITE]?.firstOrNull()?.let { fields["dive_site"] = Stored.Leaf("@$it") }
        linked[Types.PERSON]?.let { buddies ->
            fields["buddies"] = Stored.Elements(buddies.map { Stored.Leaf("@$it") })
        }
        if (before != null) fields["previous_dive"] = Stored.Leaf("@$before")
        // Kept only on the first of a group, where it describes a dive the file does not hold.
        // Anywhere else the two dives it sits between are both here and their clocks say it.
        if (before == null) {
            fields.put("surface_interval", dive.find("surfaceintervalbeforedive", SAMPLES_APART)
                ?.said("passedtime"))
        }
        environmentOf(dive, said)?.let { fields["environment"] = it }
        gearOf(dive, said, ours)?.let { fields["gear"] = it }
        detailsOf(dive, fields, trips, linked[Types.OPERATOR]?.firstOrNull())
        val profiles = profileOf(dive, keysOf(dive, mixes.keys), tanksOf(dive))
        val gases = gasesOf(dive, ours, mixes)
        // A recording keeps its own gases, and the dive reads them from it. A dive with no
        // recording keeps them itself, as the user's. `RECON-7`.
        if (profiles != null) {
            fields["profiles"] = gases?.let { withSources(profiles, it) } ?: profiles
        } else {
            gases?.let { fields["gas_sources"] = it }
        }
        return fields
    }

    private val SAMPLES_APART = setOf(SAMPLES)

    /** Every profile in [profiles] given [sources] as its own cylinders. */
    private fun withSources(profiles: Stored.Members, sources: Stored): Stored.Members =
        Stored.Members(profiles.members.mapValues { (_, profile) ->
            val fields = (profile as? Stored.Members)?.members ?: return@mapValues profile
            Stored.Members(fields + ("gas_sources" to sources))
        })

    /**
     * What the links before a dive point at, by the type of what each resolves to.
     *
     * A `link` carries no type and its parent is the same for all of them, so what one means is
     * what it lands on: a site is where the dive was, a person a buddy, a divebase the operator.
     * `uddf.md`, under *How references work on both sides*. One that resolves to nothing read is
     * passed over.
     */
    private fun linksIn(
        dive: Tag,
        ours: Map<String, String>,
        set: ItemSet,
    ): Map<ItemDescription, List<String>> {
        val before = dive.find("informationbeforedive", SAMPLES_APART) ?: return emptyMap()
        val out = LinkedHashMap<ItemDescription, MutableList<String>>()
        for (link in before.all("link")) {
            val id = link.attributes["ref"]?.let { ours[it] } ?: continue
            val type = set[id]?.description ?: continue
            out.getOrPut(type) { ArrayList() } += id
        }
        return out
    }

    /** The conditions of a dive, or nothing where the document said none of them. */
    private fun environmentOf(dive: Tag, said: (String) -> String?): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        said("current")?.let { CURRENTS.ours(it) }?.let { fields["current"] = Stored.Leaf(it) }
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
        fields.put("temperature_evaluation", said("thermalcomfort")?.let { WARMTHS.ours(it) })
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
    private fun gasesOf(
        dive: Tag,
        ours: Map<String, String>,
        mixes: Map<String, String?>,
    ): Stored.Members? {
        val tanks = dive.everywhere("tankdata", SAMPLES_APART)
        if (tanks.isEmpty()) return null
        val sources = LinkedHashMap<String, Stored>()
        for ((at, tank) in tanks.withIndex()) {
            val fields = LinkedHashMap<String, Stored>()
            fields.put("start_pressure", tank.said("tankpressurebegin"))
            fields.put("end_pressure", tank.said("tankpressureend"))
            fields.put("volume", tank.said("tankvolume"))
            val links = tank.all("link").mapNotNull { it.attributes["ref"] }
            fields.put("gas_type", links.firstNotNullOfOrNull { mixes[it] })
            tank.all("link").firstNotNullOfOrNull { ours[it.attributes["ref"]] }
                ?.let { fields["cylinder"] = Stored.Leaf("@$it") }
            if (fields.isEmpty()) continue
            sources[keyAt(at)] = Stored.Members(fields)
        }
        return sources.ifEmpty { null }?.let { Stored.Members(it) }
    }

    /**
     * The gas a `mix` defines, as this model writes one, or absent where it says no oxygen.
     *
     * Whole percentages, as fine as this model goes: an `o2` of 0.318 arrives as `EAN32`. Argon
     * and hydrogen have nowhere to go and are not read. `uddf.md`, under *Gas and cylinders*. A
     * mix whose parts do not add up is left unread rather than refused.
     */
    private fun gasIn(mix: Tag): String? {
        val oxygen = mix.text("o2")?.toDoubleOrNull() ?: return null
        val helium = mix.text("he")?.toDoubleOrNull() ?: 0.0
        return try {
            Gas(oxygen, helium).toString()
        } catch (refused: IllegalArgumentException) {
            null
        }
    }

    /** What the gas source made from the tank at [at] is keyed by. */
    private fun keyAt(at: Int): String = "gas" + if (at == 0) "" else "#$at"

    /**
     * Which gas source each of [mixes] was breathed from, by the mix's own id.
     *
     * A `switchmix` names a mix and this model's switches name a cylinder, so the two are joined
     * through the `tankdata` that links the mix. Where two tanks carry one mix the first wins,
     * which is the same imprecision `LOGIC-12` records for a download: the document says which
     * gas was switched to and not which cylinder the diver reached for.
     */
    private fun keysOf(dive: Tag, mixes: Set<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((at, tank) in dive.everywhere("tankdata", SAMPLES_APART).withIndex()) {
            for (link in tank.all("link")) {
                val ref = link.attributes["ref"] ?: continue
                if (ref in mixes && ref !in out) out[ref] = keyAt(at)
            }
        }
        return out
    }

    /** A dive's notes, its trip and who ran it, which this model keeps together. */
    private fun detailsOf(
        dive: Tag,
        fields: MutableMap<String, Stored>,
        trips: Map<String, String>,
        operator: String?,
    ) {
        fields.put("remarks", dive.prose("notes"))
        fields.put("dive_trip", dive.attributes["id"]?.let { trips[it] }?.let { "@$it" })
        fields.put("operator", operator?.let { "@$it" })
    }

    /**
     * The one recording a UDDF dive holds, as this model's profiles.
     *
     * UDDF keeps one time-ordered list of waypoints, each carrying whatever was measured at that
     * instant; this model keeps a series per quantity, each with its own times. So a waypoint
     * contributes a point to each series it has a value for and nothing to the others, which is
     * what makes the shapes convertible without padding.
     *
     * The waypoints after the surfacing are dropped, a file being as free as a device to keep
     * recording once the diving stopped. A depth that will not read as a number is not evidence
     * of floating and stops the cut where it stands, so a file this cannot parse keeps
     * everything and says what it says through the field that refuses it. `LOGIC-30`.
     */
    private fun profileOf(
        dive: Tag,
        keys: Map<String, String>,
        tanks: Map<String, String>,
    ): Stored.Members? {
        val all = dive.one(SAMPLES)?.all("waypoint").orEmpty()
        if (all.isEmpty()) return null
        // What the computer recorded while floating after the dive is not the dive. `LOGIC-30`.
        val waypoints = all.take(untilSurfaced(all.map { it.text("depth")?.toDoubleOrNull() }))
        val fields = LinkedHashMap<String, Stored>()
        for ((ours, theirs) in SERIES) {
            seriesOf(waypoints, theirs)?.let { fields[ours] = it }
        }
        switchesOf(waypoints, keys)?.let { fields["gas_switches"] = it }
        stopsOf(waypoints)?.let { fields["decostop"] = it }
        alarmsOf(waypoints)?.let { fields["alarms"] = it }
        pressuresOf(waypoints, tanks)?.let { fields["pressures"] = it }
        val after = dive.find("informationafterdive", SAMPLES_APART)
        fields.put("no_flight_time", after?.text("noflighttime"))
        fields.put("desaturation_time", after?.text("desaturationtime"))
        if (fields.isEmpty()) return null
        // One profile, under the key its own type proposes for a recording naming no computer.
        return Stored.Members(mapOf("profile" to Stored.Members(fields)))
    }

    /**
     * Which gas was being breathed and when, from the `switchmix` a waypoint may carry.
     *
     * **The first is kept whatever it names**: a document putting one on the first waypoint is
     * saying what the dive began on, which is where that fact lives — a collection has no
     * inherent order, so the gas list cannot say it. `LOGIC-31`. A switch naming the gas already
     * being breathed is a repeat and is dropped, and one naming a mix no cylinder carried is
     * dropped too, there being no gas source to point at.
     */
    private fun switchesOf(waypoints: List<Tag>, keys: Map<String, String>): Stored.Elements? {
        val points = ArrayList<Stored>()
        var breathing: String? = null
        for (waypoint in waypoints) {
            val at = waypoint.text("divetime") ?: continue
            val ref = waypoint.one("switchmix")?.attributes?.get("ref") ?: continue
            val key = keys[ref] ?: continue
            if (key == breathing) continue
            breathing = key
            val when_ = Stored.Leaf(at.toIntOrNull() ?: at)
            points += Stored.Elements(listOf(when_, Stored.Leaf("*$key")))
        }
        return if (points.isEmpty()) null else Stored.Elements(points)
    }

    /**
     * The stops a computer set, as the depth of the one standing at each moment.
     *
     * UDDF's `decostop` is a stop with a depth and how long it lasts; this model's is the depth
     * of the stop standing, through time, nought where none is. So each mandatory stop is its
     * depth at the waypoint carrying it, and where it runs out before another begins, a nought
     * where it ran out. A safety stop is not read: a dive with one is not a decompression dive,
     * and this series is what says a dive was. `uddf.md`, under *Where the two disagree*.
     */
    private fun stopsOf(waypoints: List<Tag>): Stored.Elements? {
        val points = ArrayList<Pair<Int, String>>()
        var until: Int? = null
        for (waypoint in waypoints) {
            val at = waypoint.text("divetime")?.toDoubleOrNull()?.toInt() ?: continue
            val stop = waypoint.all("decostop").firstOrNull { it.attributes["kind"] == "mandatory" }
            val ended = until
            if (ended != null && ended <= at && (stop == null || ended < at)) {
                points += ended to "0"
                until = null
            }
            if (stop == null) continue
            val depth = stop.attributes["decodepth"] ?: continue
            points += at to depth
            val lasting = stop.attributes["duration"]?.toDoubleOrNull()?.toInt() ?: 0
            until = if (lasting > 0) at + lasting else null
        }
        until?.let { points += it to "0" }
        if (points.isEmpty()) return null
        return Stored.Elements(points.map { (at, value) ->
            Stored.Elements(listOf(Stored.Leaf(at), Stored.Leaf(value)))
        })
    }

    /** The alarms a computer raised, the first a waypoint carries. */
    private fun alarmsOf(waypoints: List<Tag>): Stored.Elements? {
        val points = ArrayList<Stored>()
        for (waypoint in waypoints) {
            val at = waypoint.text("divetime")?.toDoubleOrNull()?.toInt() ?: continue
            val alarm = waypoint.text("alarm") ?: continue
            points += Stored.Elements(listOf(Stored.Leaf(at), Stored.Leaf(alarm)))
        }
        return if (points.isEmpty()) null else Stored.Elements(points)
    }

    /**
     * The pressure left in each cylinder, a series per gas source.
     *
     * A `tankpressure` names the `tankdata` it belongs to by `tankref`, and one naming none belongs
     * to the only one there is; where there are several, it belongs to nobody and is not read.
     */
    private fun pressuresOf(waypoints: List<Tag>, tanks: Map<String, String>): Stored.Members? {
        val by = LinkedHashMap<String, MutableList<Stored>>()
        val only = tanks.values.distinct().singleOrNull()
        for (waypoint in waypoints) {
            val at = waypoint.text("divetime")?.toDoubleOrNull()?.toInt() ?: continue
            for (held in waypoint.all("tankpressure")) {
                val key = held.attributes["tankref"]?.let { tanks[it] } ?: only ?: continue
                val value = held.said.trim().ifEmpty { null } ?: continue
                by.getOrPut(key) { ArrayList() } +=
                    Stored.Elements(listOf(Stored.Leaf(at), Stored.Leaf(value)))
            }
        }
        if (by.isEmpty()) return null
        return Stored.Members(by.mapValues { Stored.Elements(it.value) })
    }

    /** What each `tankdata` of [dive] became, by its own id, and by nothing where it has none. */
    private fun tanksOf(dive: Tag): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((at, tank) in dive.everywhere("tankdata", SAMPLES_APART).withIndex()) {
            out[tank.attributes["id"] ?: "#$at"] = keyAt(at)
        }
        return out
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
     * anything else begins — `Z`, or the sign of an offset. The time before it is local, which is
     * what a dive's own time is. `LOGIC-32`.
     */
    private fun timeIn(said: String): String? =
        said.substringAfter('T', "")
            .takeWhile { it.isDigit() || it == ':' || it == '.' }
            .ifEmpty { null }

    /**
     * [set] as a UDDF document, and what could not go into it.
     *
     * What reading takes in is what this puts out, so a logbook exported and imported again keeps
     * whatever the importer reads. Everything is written, the user as the owner and everybody else
     * as a buddy; what UDDF has no place for is left out as `uddf.md` sets out, item by item.
     */
    fun write(set: ItemSet): Exported {
        val writer = Writer(set)
        val text = writer.document()
        val made = set.allOf(Types.DIVE).size - writer.plans
        return Exported(text, made, writer.leftOut, writer.plans)
    }

    /**
     * The zone a UDDF datetime carries after its time, in seconds ahead of GMT, or absent where it
     * carries none.
     *
     * `Z` is GMT, and `+02:00`, `+0200` and `+02` all say two hours ahead. What will not read as
     * one is left out rather than guessed at, a dive saying nothing being taken to be on GMT.
     */
    private fun zoneIn(said: String): Int? {
        val after = said.substringAfter('T', "")
            .dropWhile { it.isDigit() || it == ':' || it == '.' }
        if (after.isEmpty()) return null
        if (after == "Z") return 0
        val sign = when (after.first()) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val digits = after.drop(1).filter { it != ':' }
        if (digits.any { !it.isDigit() } || digits.length !in listOf(2, 4)) return null
        val hours = digits.take(2).toInt()
        val minutes = if (digits.length == 4) digits.drop(2).toInt() else 0
        return sign * (hours * 3600 + minutes * 60)
    }

    /** Each series this model keeps, and the waypoint child it is read from. `uddf.md`. */
    private val SERIES = listOf(
        "depth" to "depth",
        "temperature" to "temperature",
        "no_deco_time" to "nodecotime",
        "cns" to "cns",
        "otu" to "otu",
    )
}

/**
 * Exported is a logbook written out as a UDDF document, and what the user should hear about it.
 *
 * Immutable.
 *
 * [dives] counts what was written, which is the dives that were made. [leftOut] counts those
 * recorded on more than one computer, which went out with their primary recording and no other:
 * UDDF holds one `samples` to a dive. [plans] counts the dives left where they are, being intended
 * rather than made. `uddf.md`.
 */
class Exported(val text: String, val dives: Int, val leftOut: Int, val plans: Int = 0)
