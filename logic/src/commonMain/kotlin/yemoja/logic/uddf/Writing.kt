package yemoja.logic.uddf

import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.KeyReference
import yemoja.data.NumberDescription
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Units
import yemoja.data.ownedOrWorked
import yemoja.logic.Types
import yemoja.logic.primaryProfile
import kotlin.math.abs
import kotlin.math.round

/*
 * This model's items written out as a UDDF document.
 *
 * See ../../../../../../doc.md — the field-by-field mapping is logic/uddf.md, and what reading
 * takes in is what writing puts out, so that a logbook exported and imported again keeps what
 * the importer reads.
 */

/**
 * Built is one tag of a document being written, and what goes inside it.
 *
 * Not immutable: a tag is filled as the items are walked. A tag left holding nothing is dropped
 * when its parent is written, so a dive nobody described the conditions of has no empty
 * `informationafterdive`. [kept] marks the one tag that says something by being there,
 * `<infinity/>`.
 */
internal class Built(val name: String, private val kept: Boolean = false) {

    private val attributes = LinkedHashMap<String, String>()
    private val children = ArrayList<Built>()
    private var said: String? = null

    /** A tag called [name] inside this one, filled by [fill]. */
    fun tag(name: String, kept: Boolean = false, fill: Built.() -> Unit = {}): Built {
        val child = Built(name, kept)
        child.fill()
        children += child
        return child
    }

    /** A tag called [name] saying [value], or nothing where there is no value. */
    fun say(name: String, value: String?) {
        if (value.isNullOrEmpty()) return
        tag(name).said = value
    }

    /** A `link` to [id], the one way one part of a document names another. */
    fun link(id: String) {
        tag("link").attributes["ref"] = id
    }

    /** Makes this tag say [value]. */
    fun says(value: String) {
        said = value
    }

    /** Puts [child], built apart from this tag, inside it. */
    fun add(child: Built) {
        children += child
    }

    /** Sets an attribute of this tag. */
    fun attribute(name: String, value: String) {
        attributes[name] = value
    }

    /** Whether writing this would say nothing at all. */
    private val empty: Boolean
        get() = !kept && attributes.isEmpty() && said == null && children.all { it.empty }

    /** This tag as text, indented [depth] levels. */
    fun written(depth: Int = 0): String {
        val out = StringBuilder()
        writeTo(out, depth)
        return out.toString()
    }

    private fun writeTo(out: StringBuilder, depth: Int) {
        if (empty) return
        val indent = "  ".repeat(depth)
        out.append(indent).append('<').append(name)
        for ((key, value) in attributes) {
            out.append(' ').append(key).append("=\"").append(escaped(value, true)).append('"')
        }
        val inside = children.filterNot { it.empty }
        when {
            said != null -> out.append('>').append(escaped(said!!, false))
                .append("</").append(name).append(">\n")

            inside.isEmpty() -> out.append("/>\n")
            else -> {
                out.append(">\n")
                for (child in inside) child.writeTo(out, depth + 1)
                out.append(indent).append("</").append(name).append(">\n")
            }
        }
    }
}

/** [text] as XML carries it, quotes included where it sits in an attribute. */
private fun escaped(text: String, quoted: Boolean): String {
    val out = StringBuilder()
    for (character in text) {
        when (character) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> if (quoted) out.append("&quot;") else out.append(character)
            else -> out.append(character)
        }
    }
    return out.toString()
}

/**
 * One export of [set]: the names the document uses and the gases it defines.
 *
 * Not immutable: both are gathered as the items are walked.
 */
internal class Writer(private val set: ItemSet) {

    /** The units the document is written in, which UDDF's own specification fixes. */
    private val si: Units = Units.of(
        mapOf("temperature" to "K", "volume" to "m3", "pressure" to "Pa"),
    )

    /** What the document calls each item, by this model's id. */
    private val names = HashMap<String, String>()
    private val taken = HashSet<String>()

    /** Every gas breathed, by what the document calls its `mix`, in the order first met. */
    private val mixes = LinkedHashMap<String, Gas>()

    /**
     * Every gear item a recording names as the computer that made it.
     *
     * Such an item is a dive computer whatever its kind says, which the table cannot tell from a
     * kind of `wrist`.
     */
    private val computers: Set<Item> by lazy {
        set.allOf(Types.DIVE).flatMap { dive ->
            keyedEntries(dive, "profiles").mapNotNull { (_, recording) ->
                pointed(recording, "dive_computer")
            }
        }.toSet()
    }

    /** How many dives went out with some of their recordings left behind. */
    var leftOut: Int = 0
        private set

    /** The whole document. */
    fun document(): String {
        val root = Built("uddf")
        root.attribute("xmlns", "http://www.streit.cc/uddf/3.2/")
        root.attribute("version", "3.2.3")
        root.tag("generator") {
            say("name", "Yemoja")
            say("type", "logbook")
        }
        root.tag("diver") { people() }
        root.tag("divesite") {
            for (operator in set.allOf(Types.OPERATOR)) tag("divebase") { operatorOut(operator) }
            for (site in set.allOf(Types.DIVE_SITE)) tag("site") { siteOut(site) }
        }
        // Written after the dives are, since only walking them says which gases were breathed,
        // and put before them in the document, where the specification orders it.
        val profiles = Built("profiledata")
        groupsOut(profiles)
        root.tag("gasdefinitions") {
            for ((id, gas) in mixes) tag("mix") { mixOut(id, gas) }
        }
        root.add(profiles)
        root.tag("divetrip") { tripsOut() }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + root.written()
    }

    /** The owner first, holding every piece of gear, then everybody else as a buddy. `uddf.md`. */
    private fun Built.people() {
        val user = set.user?.id?.let { set[it] }?.takeIf { it.description == Types.PERSON }
        tag("owner") {
            attribute("id", user?.let { nameOf(it) } ?: fresh("owner"))
            personOut(user) {
                tag("equipment") {
                    for (gear in set.allOf(Types.GEAR)) {
                        val (element, notes) = elementOf(gear)
                        tag(element) { gearOut(gear, notes) }
                    }
                }
            }
        }
        for (person in set.allOf(Types.PERSON)) {
            if (person === user) continue
            tag("buddy") {
                attribute("id", nameOf(person))
                personOut(person)
            }
        }
    }

    /**
     * What UDDF keeps of [person], with [equipment] where the specification puts it.
     *
     * The owner holds every piece of gear, so it goes between the contact and the medical. An
     * owner the logbook names nobody for is only that.
     */
    private fun Built.personOut(person: Item?, equipment: Built.() -> Unit = {}) {
        if (person == null) {
            equipment()
            return
        }
        tag("personal") {
            say("firstname", text(person, "first_name"))
            say("middlename", text(person, "middle_names"))
            say("lastname", text(person, "last_name"))
            dated("birthdate", said(person, "birthday"))
        }
        // One piece of prose here, four parts there: the whole of it goes in the first.
        tag("address") { say("street", text(person, "address")) }
        tag("contact") {
            say("email", text(person, "email"))
            say("phone", text(person, "phone"))
        }
        equipment()
        owned(person, "medical")?.let { medical ->
            tag("medical") {
                tag("examination") { say("datetime", said(medical, "last_medical_check")) }
            }
        }
        tag("education") {
            for (course in entries(person, "courses")) {
                tag("certification") {
                    say("level", pointed(course, "certification")?.let { text(it, "name") })
                    say("certificatenumber", text(course, "number"))
                    dated("issuedate", said(course, "date"))
                }
            }
        }
        notes(text(person, "remarks"))
    }

    /**
     * Which of UDDF's equipment elements [gear] is, and what its notes must say besides.
     *
     * The pair of category and kind is looked up in the table reading uses, the kind first and
     * then the category alone. What matches neither goes out as `variouspieces` with the kind in
     * its notes, so a person reading the file still learns what it was. `uddf.md`.
     */
    private fun elementOf(gear: Item): Pair<String, String?> {
        if (gear in computers) return "divecomputer" to text(gear, "kind")?.let { "kind: $it" }
        val category = text(gear, "category")?.lowercase()
        val kind = text(gear, "kind")?.lowercase()
        val exact = KIT.entries.firstOrNull { (_, pair) ->
            pair.first?.lowercase() == category && pair.second?.lowercase() == kind
        }
        if (exact != null && (category != null || kind != null)) return exact.key to null
        val broad = KIT.entries.firstOrNull { (_, pair) ->
            category != null && pair.first?.lowercase() == category && pair.second == null
        }
        if (broad != null) return broad.key to kind?.let { "kind: $it" }
        val what = listOfNotNull(text(gear, "category"), text(gear, "kind")).joinToString(", ")
        return "variouspieces" to what.ifEmpty { null }
    }

    private fun Built.gearOut(gear: Item, besides: String?) {
        attribute("id", nameOf(gear))
        say("name", text(gear, "name"))
        tag("manufacturer") { say("name", text(gear, "brand")) }
        say("model", text(gear, "model"))
        say("serialnumber", text(gear, "serial"))
        notes(listOfNotNull(text(gear, "remarks"), besides).joinToString("\n").ifEmpty { null })
    }

    private fun Built.operatorOut(operator: Item) {
        attribute("id", nameOf(operator))
        say("name", text(operator, "name"))
        for (alias in texts(operator, "alternative_names")) say("aliasname", alias)
        tag("address") { say("street", text(operator, "address")) }
        tag("contact") {
            say("email", text(operator, "email"))
            say("phone", text(operator, "phone"))
            say("homepage", text(operator, "website"))
        }
        rated(said(operator, "rating"))
        notes(text(operator, "remarks"))
    }

    private fun Built.siteOut(site: Item) {
        attribute("id", nameOf(site))
        say("name", text(site, "name"))
        for (alias in texts(site, "alternative_names")) say("aliasname", alias)
        say("environment", text(site, "environment_type")?.let { ENVIRONMENTS.theirs(it) })
        tag("geography") {
            say("latitude", number(site, "latitude"))
            say("longitude", number(site, "longitude"))
            say("altitude", number(site, "elevation"))
        }
        tag("sitedata") {
            say("maximumdepth", number(site, "max_depth"))
            say("bottom", text(site, "substrate"))
        }
        rated(said(site, "rating"))
        notes(text(site, "remarks"))
    }

    private fun Built.mixOut(id: String, gas: Gas) {
        attribute("id", id)
        say("name", gas.toString())
        say("o2", gas.fractionO2.toString())
        say("n2", gas.fractionN2.toString())
        say("he", gas.fractionHe.toString())
    }

    /**
     * Every dive, earliest first, in the groups the chains of `previous_dive` make.
     *
     * A dive continues the group open where the dive before it is the one it names, and opens a
     * new one otherwise. A group opened by a dive naming nobody says `<infinity/>`; one opened by
     * a dive whose predecessor is not the dive before it carries its `surface_interval` instead,
     * which tells a reader there was an earlier dive without showing one. `DATA-60`.
     */
    private fun groupsOut(profiles: Built) {
        val dives = made().sortedWith(
            compareBy({ said(it, "start_date") }, { said(it, "start_time") }),
        )
        var group: Built? = null
        var last: ReferenceableItem? = null
        for (dive in dives) {
            val before = pointed(dive, "previous_dive")
            val continues = group != null && before != null && before === last
            if (!continues) {
                group = profiles.tag("repetitiongroup") { attribute("id", fresh("group")) }
            }
            group!!.tag("dive") { diveOut(dive, opens = !continues) }
            last = dive
        }
    }

    private fun Built.diveOut(dive: ReferenceableItem, opens: Boolean) {
        attribute("id", nameOf(dive))
        tag("informationbeforedive") {
            pointed(dive, "dive_site")?.let { link(nameOf(it)) }
            for (buddy in pointedAll(dive, "buddies")) link(nameOf(buddy))
            pointed(dive, "operator")?.let { link(nameOf(it)) }
            say("divenumber", said(dive, "dive_number"))
            owned(dive, "environment")?.let { say("airtemperature", number(it, "air_temperature")) }
            val date = said(dive, "start_date")
            val time = said(dive, "start_time")
            val zone = if (time == null) "" else zoneOf(dive)
            say("datetime", date?.let { if (time == null) it else "${it}T$time$zone" })
            if (opens) {
                // Only an interval the user wrote or one from a named dive: the gap since whatever
                // dive came last says nothing about whether this one began clean. `DATA-60`.
                val counted = pointed(dive, "previous_dive") != null ||
                    (dive.read("surface_interval") as? Result.Usable)?.origin == Result.Origin.STORED
                val interval = if (counted) number(dive, "surface_interval") else null
                tag("surfaceintervalbeforedive") {
                    if (interval != null) {
                        say("passedtime", interval)
                    } else {
                        tag("infinity", kept = true)
                    }
                }
            }
        }
        val mixesByKey = HashMap<String, String>()
        val tanksByKey = HashMap<String, String>()
        for ((key, source) in keyedEntries(dive, "gas_sources")) {
            tag("tankdata") {
                val tank = fresh(nameOf(dive) + "_" + key)
                tanksByKey[key] = tank
                attribute("id", tank)
                (source.single<Gas>("gas_type") as? Result.Usable)?.value?.let { gas ->
                    val id = mixOf(gas)
                    mixesByKey[key] = id
                    link(id)
                }
                pointed(source, "cylinder")?.let { link(nameOf(it)) }
                say("tankvolume", number(source, "volume"))
                say("tankpressurebegin", number(source, "start_pressure"))
                say("tankpressureend", number(source, "end_pressure"))
            }
        }
        samplesOut(dive, mixesByKey, tanksByKey)
        tag("informationafterdive") {
            val environment = owned(dive, "environment")
            val gear = owned(dive, "gear")
            environment?.let { say("lowesttemperature", number(it, "bottom_temperature")) }
            say("greatestdepth", number(dive, "max_depth"))
            say("averagedepth", number(dive, "average_depth"))
            environment?.let { say("visibility", number(it, "visibility")) }
            say("current", environment?.let { text(it, "current") }?.let { CURRENTS.theirs(it) })
            gear?.let { worn ->
                tag("equipmentused") { for (item in pointedAll(worn, "items")) link(nameOf(item)) }
            }
            val warmth = gear?.let { text(it, "temperature_evaluation") }
            say("thermalcomfort", warmth?.let { WARMTHS.theirs(it) })
            (primaryProfile(dive) as? Result.Usable)?.value?.let { profile ->
                say("desaturationtime", number(profile, "desaturation_time"))
                say("noflighttime", number(profile, "no_flight_time"))
            }
            notes(text(dive, "remarks"))
            rated(said(dive, "rating"))
        }
    }

    /**
     * The primary recording as waypoints, one at every second any of its series was measured.
     *
     * UDDF holds one list where this model holds a series per quantity, so a waypoint goes out
     * wherever anything was measured, and a depth is read off the line between its neighbours
     * where only something else was: every waypoint carries one. What else a waypoint carries is
     * only what was measured at that second. `uddf.md`, under *Where the two disagree*.
     *
     * **The primary recording and no other.** UDDF holds one `samples` to a dive, and a dive on
     * two computers leaves its second behind; counted in [leftOut] so the user is told once.
     */
    private fun Built.samplesOut(
        dive: Item,
        mixesByKey: Map<String, String>,
        tanksByKey: Map<String, String>,
    ) {
        val recordings = keyedEntries(dive, "profiles").size
        val profile = (primaryProfile(dive) as? Result.Usable)?.value
        if (recordings > (if (profile == null) 0 else 1)) leftOut++
        if (profile == null) return
        val depth = seriesOf(profile, "depth") ?: return
        val measured = SAMPLED.associate { (ours, theirs) -> theirs to seriesOf(profile, ours) }
        val switches = (profile.series<KeyReference>("gas_switches") as? Result.Usable)?.value
        val stops = seriesOf(profile, "decostop")
        val alarms = (profile.series<String>("alarms") as? Result.Usable)?.value
        val held = (profile.keyedSeries<Double>("pressures") as? Result.Usable)?.value.orEmpty()
        val pressures = held.mapNotNull { (key, entry) ->
            (entry as? Element.Usable)?.let { key to it.value }
        }
        val seconds = HashSet<Int>()
        val every = listOfNotNull(depth, switches, stops, alarms) +
            measured.values.filterNotNull() + pressures.map { it.second }
        for (series in every) seconds += series.indices().map(series::secondAt)
        val depthDimension = dimensionOf(profile, "depth")
        tag("samples") {
            for (second in seconds.sorted()) {
                val at = depthAt(depth, second) ?: continue
                tag("waypoint") {
                    say("depth", figure(depthDimension, at))
                    say("divetime", second.toString())
                    switches?.let { held ->
                        val key = valueAtSecond(held, second) as? KeyReference
                        key?.let { mixesByKey[it.key] }?.let { id ->
                            tag("switchmix").attribute("ref", id)
                        }
                    }
                    for ((ours, theirs) in SAMPLED) {
                        val value = measured[theirs]?.let { valueAtSecond(it, second) } as? Double
                        say(theirs, value?.let { figure(dimensionOf(profile, ours), it) })
                    }
                    for ((key, series) in pressures) {
                        val value = valueAtSecond(series, second) as? Double ?: continue
                        tag("tankpressure") {
                            says(figure(dimensionOf(profile, "pressures"), value))
                            tanksByKey[key]?.let { attribute("tankref", it) }
                        }
                    }
                    stops?.let { stopAt(it, second, depthDimension) }
                    (alarms?.let { valueAtSecond(it, second) } as? String)?.let { say("alarm", it) }
                }
            }
        }
    }

    /**
     * The stop [stops] sets at [second], as UDDF writes one: its depth and how long it stands.
     *
     * How long is until the series next says anything, which is what a series held as steps
     * means. A nought is no stop and writes nothing, and the last stop of all lasts no time, there
     * being nothing after it to say when it ended. `uddf.md`, under *Where the two disagree*.
     */
    private fun Built.stopAt(stops: Series, second: Int, dimension: Dimension?) {
        for (at in stops.indices()) {
            if (stops.secondAt(at) != second) continue
            val depth = (stops.valueAt(at) as? Element.Usable)?.value as? Double ?: return
            if (depth <= 0.0) return
            val next = if (at + 1 < stops.size) stops.secondAt(at + 1) else second
            tag("decostop") {
                attribute("kind", "mandatory")
                attribute("decodepth", figure(dimension, depth))
                attribute("duration", (next - second).toString())
            }
            return
        }
    }

    /**
     * The zone after a dive's local time, `+02:00`, or nothing where the dive gives no
     * `time_zone_offset`: a time with no zone is local, which is all such a dive says. `LOGIC-32`.
     */
    private fun zoneOf(dive: Item): String {
        val offset = (dive.single<Double>("time_zone_offset") as? Result.Usable)?.value
            ?: return ""
        val minutes = kotlin.math.round(offset / 60).toLong()
        val sign = if (minutes < 0) "-" else "+"
        val hours = (abs(minutes) / 60).toString().padStart(2, '0')
        val rest = (abs(minutes) % 60).toString().padStart(2, '0')
        return "$sign$hours:$rest"
    }

    /** Every trip under a `trip` of its own, its legs as the parts, deeper legs flattened. */
    private fun Built.tripsOut() {
        val trips = set.allOf(Types.DIVE_TRIP)
        val tops = trips.filter { topOf(it) === it }
        for (top in tops) {
            tag("trip") {
                attribute("id", fresh("trip"))
                say("name", text(top, "name"))
                for (leg in trips.filter { topOf(it) === top }) {
                    if (leg === top && trips.any { it !== top && topOf(it) === top } &&
                        divesOn(top).isEmpty()
                    ) {
                        continue
                    }
                    tag("trippart") { tripOut(leg) }
                }
            }
        }
    }

    private fun Built.tripOut(trip: ReferenceableItem) {
        attribute("id", nameOf(trip))
        say("name", text(trip, "name"))
        tag("dateoftrip") {
            dated("startdate", said(trip, "start_date"))
            dated("enddate", said(trip, "end_date"))
        }
        pointed(trip, "operator")?.let { operator ->
            tag("operator") {
                say("name", text(operator, "name"))
                link(nameOf(operator))
            }
        }
        tag("relateddives") { for (dive in divesOn(trip)) link(nameOf(dive)) }
        notes(text(trip, "remarks"))
    }

    /** The trip at the top of [trip]'s parents, which is [trip] where it names none. */
    private fun topOf(trip: ReferenceableItem): ReferenceableItem {
        var at = trip
        val seen = HashSet<ReferenceableItem>()
        while (seen.add(at)) at = pointed(at, "parent") ?: return at
        return at
    }

    /**
     * The dives of the logbook that were made, which is what a file carries.
     *
     * **A plan is not written.** A dive intended and not yet made, said in a file that goes to
     * another application, is a claim that it happened, and nothing in the file lets the reader
     * tell. UDDF has a `diveplan` of its own for the honest route one day. `LOGIC-36`, `uddf.md`.
     */
    private fun made(): List<ReferenceableItem> = set.allOf(Types.DIVE).filter { dive ->
        (dive.single<Boolean>("planned") as? Result.Usable)?.value != true
    }

    /** How many dives were left where they are, being intended rather than made. */
    val plans: Int get() = set.allOf(Types.DIVE).size - made().size

    /** The dives naming [trip], the ones made. */
    private fun divesOn(trip: ReferenceableItem): List<ReferenceableItem> =
        made().filter { dive -> pointed(dive, "dive_trip") === trip }

    /** The `mix` [gas] is defined as, defining it the first time. */
    private fun mixOf(gas: Gas): String {
        mixes.entries.firstOrNull { it.value == gas }?.let { return it.key }
        val id = fresh("mix_" + gas.toString())
        mixes[id] = gas
        return id
    }

    /** What the document calls [item], named the first time it is asked. */
    private fun nameOf(item: Item): String {
        val id = (item as? ReferenceableItem)?.let { set.idOf(it) } ?: return fresh("item")
        return names.getOrPut(id) { fresh(id) }
    }

    /**
     * A name nothing in the document has yet, made from [wanted].
     *
     * An XML id may not hold a `#` and may not begin with a digit, and a dive's id does both, so
     * what is not a letter, a digit, a point, a hyphen or an underscore becomes an underscore and
     * one beginning with a digit gains one in front.
     */
    private fun fresh(wanted: String): String {
        val kept = wanted.map { if (it.isLetterOrDigit() || it in ".-_") it else '_' }
            .joinToString("")
        val first = kept.firstOrNull()
        val cleaned = if (first != null && (first.isLetter() || first == '_')) kept else "_$kept"
        var name = cleaned
        var count = 1
        while (!taken.add(name)) name = cleaned + "_" + ++count
        return name
    }

    private fun Built.notes(said: String?) {
        if (said.isNullOrBlank()) return
        tag("notes") { for (line in said.lines()) say("para", line) }
    }

    private fun Built.rated(rating: String?) {
        if (rating == null) return
        tag("rating") { say("ratingvalue", rating) }
    }

    /** A date as UDDF wraps one, in a `datetime` inside the tag that names it. */
    private fun Built.dated(name: String, date: String?) {
        if (date == null) return
        tag(name) { say("datetime", date) }
    }

    /** The number [field] on [item] holds, in UDDF's unit and to the decimals that unit keeps. */
    private fun number(item: Item, field: String): String? {
        val value = (item.single<Double>(field) as? Result.Usable)?.value ?: return null
        return figure(dimensionOf(item, field), value)
    }

    private fun figure(dimension: Dimension?, value: Double): String {
        if (dimension == null) return plain(value, WITHOUT_A_UNIT)
        return plain(si.fromDefault(dimension, value), si.decimalsOf(dimension))
    }

    private fun dimensionOf(item: Item, field: String): Dimension? =
        (item.description[field] as? NumberDescription)?.dimension

    /** Whatever [field] on [item] holds, as text: a date, a time, a whole number. */
    private fun said(item: Item, field: String): String? =
        (item.read(field) as? Result.Usable)?.value?.toString()?.ifEmpty { null }

    private fun text(item: Item, field: String): String? =
        (item.single<String>(field) as? Result.Usable)?.value?.ifBlank { null }

    private fun texts(item: Item, field: String): List<String> =
        ((item.list<String>(field) as? Result.Usable)?.value.orEmpty())
            .mapNotNull { (it as? Element.Usable)?.value }

    /** What is worked out goes too, where nothing is stored: a dive's coldest water, from its recording. */
    private fun owned(item: Item, field: String): OwnedItem? = item.ownedOrWorked(field)

    private fun entries(item: Item, field: String): List<OwnedItem> =
        keyedEntries(item, field).map { it.second }

    private fun keyedEntries(item: Item, field: String): List<Pair<String, OwnedItem>> =
        ((item.keyed<OwnedItem>(field) as? Result.Usable)?.value.orEmpty())
            .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }

    /** The item [field] on [item] names, or absent where it names none this set holds. */
    private fun pointed(item: Item, field: String): ReferenceableItem? {
        val held = (item.single<Reference>(field) as? Result.Usable)?.value
        return (held as? Reference.Identified)?.let { set[it.id] }
    }

    private fun pointedAll(item: Item, field: String): List<ReferenceableItem> =
        ((item.list<Reference>(field) as? Result.Usable)?.value.orEmpty())
            .mapNotNull { ((it as? Element.Usable)?.value as? Reference.Identified)?.id }
            .mapNotNull { set[it] }

    private fun seriesOf(profile: Item, field: String): Series? =
        (profile.series<Double>(field) as? Result.Usable)?.value
}

/** Each series written onto a waypoint besides depth, and the waypoint child it becomes. */
private val SAMPLED = listOf(
    "temperature" to "temperature",
    "no_deco_time" to "nodecotime",
    "cns" to "cns",
    "otu" to "otu",
)

/** Below this a whole number is written as digits; a Long holds it exactly. */
private const val WHOLE = 1e15

/** How many decimals a number with no unit is written to. */
private const val WITHOUT_A_UNIT = 6

private fun Series.indices(): IntRange = 0..<size

/** What [series] measured at exactly [second], or absent where it measured nothing then. */
private fun valueAtSecond(series: Series, second: Int): Any? {
    for (at in series.indices()) {
        if (series.secondAt(at) == second) return (series.valueAt(at) as? Element.Usable)?.value
    }
    return null
}

/**
 * The depth at [second], measured or read off the line between the nearest two that were.
 *
 * Before the first and after the last the nearest measured depth stands. Absent only where the
 * series has no depth that reads.
 */
private fun depthAt(series: Series, second: Int): Double? {
    var before: Pair<Int, Double>? = null
    for (at in series.indices()) {
        val value = (series.valueAt(at) as? Element.Usable)?.value as? Double ?: continue
        val when_ = series.secondAt(at)
        if (when_ == second) return value
        if (when_ > second) {
            val (from, low) = before ?: return value
            return low + (value - low) * (second - from) / (when_ - from)
        }
        before = when_ to value
    }
    return before?.second
}

/** [value] rounded to [decimals], with no trailing zeros and no exponent where it is whole. */
private fun plain(value: Double, decimals: Int): String {
    var scale = 1.0
    repeat(decimals) { scale *= 10 }
    val rounded = round(value * scale) / scale
    if (rounded == round(rounded) && abs(rounded) < WHOLE) return rounded.toLong().toString()
    return rounded.toString()
}
