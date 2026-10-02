package yemoja.logic.divinglog

import yemoja.data.Gas
import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.data.sqlite.SqliteFile
import yemoja.data.sqlite.SqliteFormatException
import yemoja.logic.Types
import yemoja.logic.freeName
import yemoja.logic.unknownOf
import yemoja.logic.untilSurfaced
import kotlin.math.roundToLong

/*
 * A Diving Log database read into a set of items, to be reviewed as any import is.
 *
 * See ../../../../../../divinglog.md, which says what each table and column holds and where it
 * goes. `DLOG-1` to `DLOG-4`.
 */

/** DivingLogFormatException is thrown when a SQLite file is not a Diving Log database. */
class DivingLogFormatException(message: String) : RuntimeException(message)

/**
 * Read is a Diving Log database taken in: its items, and what the import should say about it.
 *
 * [said] names the version the file gave and counts what was left behind. `DLOG-2`, `DLOG-3`.
 *
 * Immutable.
 */
class Read(val items: ItemSet, val said: String)

/** DivingLog reads a Diving Log database. */
object DivingLog {

    /** The version of Diving Log's database this reader was worked out from. `DLOG-2`. */
    const val CHECKED = "4.2.0"

    /**
     * The items [bytes] holds, as a Diving Log database.
     *
     * Throws [DivingLogFormatException] where the file is SQLite but holds no dive table, and
     * [SqliteFormatException] where it is not SQLite this reader can read.
     */
    fun read(bytes: ByteArray): Read {
        val file = SqliteFile(bytes)
        val dives = file.rows("Logbook")
            ?: throw DivingLogFormatException("this database has no Logbook table, so it is not Diving Log's")
        val table = { name: String -> file.rows(name).orEmpty() }
        val set = ItemSet(Types.ALL)
        val reading = Reading(set)

        // A site whose own water is not given takes the water every dive there was logged in.
        val waters = dives.groupBy { it.number("PlaceID") }
            .mapValues { (_, at) -> at.mapNotNull { WATERS[it.number("Water")] }.distinct().singleOrNull() }
        for (row in table("Place")) {
            reading.put(Types.DIVE_SITE, "Place", row, siteOf(row, table, waters[row.number("ID")]))
        }
        for (row in table("Buddy")) reading.put(Types.PERSON, "Buddy", row, personOf(row))
        for (row in table("Shop")) reading.put(Types.OPERATOR, "Shop", row, operatorOf(row))
        for (row in table("Equipment")) reading.put(Types.GEAR, "Equipment", row, gearOf(row))
        for (row in table("Trip")) reading.put(Types.DIVE_TRIP, "Trip", row, tripOf(row, reading))
        // The user is a person like any other here; which person is the user is the logbook's to say.
        for (row in table("Personal")) {
            val person = personOf(row)
            val user = userOf(row, table("Brevets"))
            val remarks = paragraphs(
                (person["remarks"] as? Stored.Leaf)?.value as? String,
                (user["remarks"] as? Stored.Leaf)?.value as? String,
            )
            val fields = LinkedHashMap(person + user)
            fields.remove("remarks")
            fields.text("remarks", remarks)
            reading.put(Types.PERSON, "Personal", row, fields)
        }

        val tanks = table("Tank").groupBy { it.number("LogID") }
        val kinds = table("Divetype").associate { it.number("ID") to it.text("Typename") }
        val fish = table("Fish").associate { it.number("ID") to (it.text("CommonName") ?: it.text("ScientificName")) }
        val seen = table("FishRel").groupBy { it.number("LogID") }
        val own = table("Userdefined").groupBy { it.number("LogID") }
        val context = DiveContext(reading, tanks, kinds, fish, seen, own)
        var withProfile = 0
        for (row in dives.sortedWith(compareBy({ it.text("Divedate") }, { it.text("Entrytime") }))) {
            val fields = diveOf(row, context)
            if ("profiles" in fields) withProfile++
            reading.put(Types.DIVE, "Logbook", row, fields)
        }

        val version = table("DBInfo").firstOrNull()?.text("DBVersion")
        val left = listOf("Pictures" to "picture", "Signature" to "signature")
            .mapNotNull { (name, what) -> table(name).size.takeIf { it > 0 }?.let { counted(it, what) } }
        return Read(set, saidOf(version, dives.size, withProfile, left))
    }

    // --- Each table's rows.

    private fun siteOf(row: Row, table: (String) -> List<Row>, dived: String?): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        fields.text("name", row.text("Place"))
        fields.number("latitude", row.text("Lat")?.let { degreesOf(it) })
        fields.number("longitude", row.text("Lon")?.let { degreesOf(it) })
        fields.text("water_type", WATERS[row.number("Water")] ?: dived)
        fields.number("max_depth", row.real("MaxDepth")?.takeIf { it > 0 })
        fields.whole("rating", ratingOf(row))
        val country = table("Country").firstOrNull { it.number("ID") == row.number("CountryID") }?.text("Country")
        fields.text(
            "remarks",
            paragraphs(
                row.text("Comments"),
                lines(
                    "Country" to country,
                    "Water" to row.text("WaterName"),
                    "Altitude" to row.text("Altitude"),
                    "Difficulty" to row.text("Difficulty"),
                ),
            ),
        )
        return fields
    }

    private fun personOf(row: Row): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        fields.text("first_name", row.text("FirstName"))
        fields.text("last_name", row.text("LastName"))
        fields.text("birthday", row.text("Birthdate"))
        fields.text("email", row.text("Email"))
        fields.text("phone", row.text("Mobile") ?: row.text("Phone"))
        fields.text("address", addressOf(row))
        fields.text(
            "remarks",
            paragraphs(
                row.text("Comments"),
                lines(
                    "Phone" to row.text("Phone")?.takeIf { row.text("Mobile") != null },
                    "Website" to row.text("URL"),
                ),
            ),
        )
        return fields
    }

    /** What only the user's own row holds: their medical, and the certifications they hold. */
    private fun userOf(row: Row, brevets: List<Row>): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        val medical = LinkedHashMap<String, Stored>()
        medical.text("last_medical_check", row.text("LastMediCheck"))
        medical.text("blood_group", row.text("Bloodgroup"))
        if (medical.isNotEmpty()) fields["medical"] = Stored.Members(medical)
        val courses = LinkedHashMap<String, Stored>()
        for (brevet in brevets.sortedBy { it.number("SortOrd") ?: it.number("ID") }) {
            val course = LinkedHashMap<String, Stored>()
            course.text("date", brevet.text("CertDate"))
            course.text("number", brevet.text("Number"))
            val instructor = listOfNotNull(brevet.text("Instructor"), brevet.text("InstructorNo")?.let { "no. $it" })
            course.text(
                "remarks",
                listOfNotNull(brevet.text("Brevet"), brevet.text("Org"), instructor.joinToString(" ").ifEmpty { null }?.let { "instructor $it" })
                    .joinToString(", ").ifEmpty { null },
            )
            if (course.isEmpty()) continue
            courses[freeName("course") { it in courses }] = Stored.Members(course)
        }
        if (courses.isNotEmpty()) fields["courses"] = Stored.Members(courses)
        fields.text("remarks", lines("Emergency contact" to row.text("EmergContact")))
        return fields
    }

    private fun operatorOf(row: Row): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        fields.text("name", row.text("ShopName"))
        fields.text("address", addressOf(row))
        fields.text("phone", row.text("Phone") ?: row.text("Mobile"))
        fields.text("email", row.text("Email"))
        fields.text("website", row.text("URL"))
        fields.whole("rating", ratingOf(row))
        fields.text("remarks", paragraphs(row.text("Comments"), lines("Kind" to row.text("ShopType"))))
        return fields
    }

    private fun gearOf(row: Row): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        fields.text("name", row.text("Object"))
        fields.text("brand", row.text("Manufacturer"))
        fields.text("serial", row.text("Serial"))
        fields.text(
            "remarks",
            paragraphs(
                row.text("Comments"),
                lines(
                    "Bought" to row.text("DateP"),
                    "Shop" to row.text("Shop"),
                    "Price" to row.text("Price"),
                    "Warranty" to row.text("Warranty"),
                    "Last serviced" to row.text("DateR"),
                    "Next service" to row.text("DateRN"),
                ),
            ),
        )
        return fields
    }

    private fun tripOf(row: Row, reading: Reading): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        fields.text("name", row.text("TripName"))
        fields.text("start_date", row.text("StartDate"))
        fields.text("end_date", row.text("EndDate"))
        fields.text("operator", reading.pointer("Shop", row.number("ShopID")))
        fields.text("remarks", row.text("Comments"))
        return fields
    }

    // --- A dive.

    /** Everything a dive points into, read once for all of them. */
    private class DiveContext(
        val reading: Reading,
        val tanks: Map<Long?, List<Row>>,
        val kinds: Map<Long?, String?>,
        val fish: Map<Long?, String?>,
        val seen: Map<Long?, List<Row>>,
        val own: Map<Long?, List<Row>>,
    )

    private fun diveOf(row: Row, context: DiveContext): Map<String, Stored> {
        val reading = context.reading
        val id = row.number("ID")
        val fields = LinkedHashMap<String, Stored>()
        fields.whole("dive_number", row.number("Number"))
        fields.text("start_date", row.text("Divedate"))
        fields.text("start_time", row.text("Entrytime")?.let { if (it.count { c -> c == ':' } == 1) "$it:00" else it })
        fields.number("max_depth", row.real("Depth")?.takeIf { it > 0 })
        fields.number("average_depth", row.real("DepthAvg")?.takeIf { it > 0 })
        fields.whole("rating", ratingOf(row))
        fields.text("dive_site", reading.pointer("Place", row.number("PlaceID")))
        val buddies = idsOf(row.text("BuddyIDs")).mapNotNull { reading.pointer("Buddy", it) }
        if (buddies.isNotEmpty()) fields["buddies"] = Stored.Elements(buddies.map { Stored.Leaf(it) })
        val tags = idsOf(row.text("Divetype")).mapNotNull { context.kinds[it]?.lowercase() }.distinct()
        if (tags.isNotEmpty()) fields["tags"] = Stored.Elements(tags.map { Stored.Leaf(it) })
        if (row.number("Deco") == 1L) fields["deco"] = Stored.Leaf(true)
        fields.text("operator", reading.pointer("Shop", row.number("ShopID")))
        fields.text("dive_trip", reading.pointer("Trip", row.number("TripID")))

        val environment = LinkedHashMap<String, Stored>()
        environment.number("air_temperature", row.real("Airtemp"))
        environment.number("bottom_temperature", row.real("Watertemp"))
        if (environment.isNotEmpty()) fields["environment"] = Stored.Members(environment)
        row.real("Weight")?.takeIf { it > 0 }?.let { fields["gear"] = Stored.Members(mapOf("weight" to Stored.Leaf(it))) }

        val sources = sourcesOf(row, context.tanks[id].orEmpty())
        if (sources.isNotEmpty()) fields["gas_sources"] = Stored.Members(sources)
        val profile = profileOf(row, sources.keys.toList())
        if (profile != null) {
            fields["profiles"] = Stored.Members(mapOf(PROFILE to Stored.Members(profile)))
        } else {
            // With no samples, the logged time is the only duration there is.
            fields.number("duration", row.real("Divetime")?.takeIf { it > 0 }?.let { (it * 60).roundToLong().toDouble() })
        }

        val fishSeen = context.seen[id].orEmpty().mapNotNull { context.fish[it.number("FishID")] }
        val ownFields = context.own[id].orEmpty().firstOrNull()
        fields.text(
            "remarks",
            paragraphs(
                row.text("Comments"),
                lines(
                    "Visibility" to VISIBILITIES[row.number("Visibility")],
                    "Entry" to row.number("Entry")?.let { ENTRIES[it] },
                    "Weather" to row.text("Weather"),
                    "Current" to row.text("UWCurrent"),
                    "Surface" to row.text("Surface"),
                    "Boat" to row.text("Boat"),
                    "Divemaster" to row.text("Divemaster"),
                    "Suit" to row.text("Divesuit"),
                    "Altitude" to row.text("Altitude"),
                    "Pressure group" to listOfNotNull(row.text("PGStart"), row.text("PGEnd")).joinToString(" to ").ifEmpty { null },
                    "CNS" to row.text("CNS")?.let { "$it%" },
                    "Fish seen" to fishSeen.joinToString(", ").ifEmpty { null },
                ),
                ownFields?.let { own -> lines(*OWN_FIELDS.map { it to own.text(it) }.toTypedArray()) },
            ),
        )
        return fields
    }

    /**
     * The cylinders of a dive: the main one on the dive's own row, then the rest from `Tank`.
     *
     * A row of `Tank` holding neither a mix nor a size nor a pressure is a placeholder, and one the
     * same in every value as an earlier row of the dive is a copy Diving Log keeps in its slots;
     * neither is read. The profile names the main one as `0` and the first of the others as `1`.
     */
    private fun sourcesOf(row: Row, tanks: List<Row>): LinkedHashMap<String, Stored> {
        val sources = LinkedHashMap<String, Stored>()
        fun add(tank: Row) {
            val fields = LinkedHashMap<String, Stored>()
            fields.text("gas_type", gasOf(tank.real("O2"), tank.real("He")))
            fields.number("volume", tank.real("Tanksize")?.takeIf { it > 0 })
            fields.number("start_pressure", tank.real("PresS")?.takeIf { it > 0 })
            fields.number("end_pressure", tank.real("PresE")?.takeIf { it > 0 })
            if (fields.isEmpty()) return
            sources[freeName("gas") { it in sources }] = Stored.Members(fields)
        }
        add(row)
        val seen = HashSet<List<Double?>>()
        for (tank in tanks.sortedBy { it.number("SortOrd") ?: it.number("ID") }) {
            val values = TANK_VALUES.map { tank.real(it) }
            if (values.take(3).all { it == null } || !seen.add(values)) continue
            add(tank)
        }
        return sources
    }

    /**
     * The dive's recording, from its five columns of samples. `divinglog.md`, under *The profile*.
     *
     * What the computer recorded after the diver surfaced is cut, as a download cuts it.
     * `LOGIC-30`. A stop is read only while the no-decompression limit is below its ceiling, and
     * the CNS clock stops where its column saturates.
     */
    private fun profileOf(row: Row, sources: List<String>): Map<String, Stored>? {
        val interval = row.number("ProfileInt")?.takeIf { it > 0 } ?: return null
        val depths = samplesOf(row.text("Profile"), 12).map { it.digits(0, 5)?.let { cm -> cm / 100.0 } }
        if (depths.isEmpty()) return null
        val count = untilSurfaced(depths)
        val at = { sample: Int -> sample * interval }
        val fields = LinkedHashMap<String, Stored>()
        fields.text("dive_computer", row.text("Computer"))
        fields.series("depth", (0..<count).mapNotNull { n -> depths[n]?.let { at(n) to it } })

        val second = samplesOf(row.text("Profile2"), 11).take(count)
        if (second.isNotEmpty()) {
            // A computer with no thermometer writes nought throughout, which is no reading.
            val temperatures = second.mapIndexedNotNull { n, s -> s.digits(0, 3)?.let { at(n) to it / 10.0 } }
            if (temperatures.any { it.second != 0.0 }) fields.series("temperature", temperatures)
            val breathed = second.map { it.digits(7, 8)?.toInt() ?: 0 }
            val pressures = LinkedHashMap<String, MutableList<Pair<Long, Double>>>()
            for ((n, sample) in second.withIndex()) {
                val bar = sample.digits(3, 7)?.takeIf { it > 0 } ?: continue
                val key = sources.getOrNull(breathed[n]) ?: continue
                pressures.getOrPut(key) { ArrayList() } += at(n) to bar / 10.0
            }
            if (pressures.isNotEmpty()) {
                fields["pressures"] = Stored.Members(pressures.mapValues { (_, points) -> pointsOf(points) })
            }
            // The first is kept whatever it names, being what the dive began on. `LOGIC-31`.
            val switches = breathed.withIndex()
                .filter { (n, cylinder) -> n == 0 || cylinder != breathed[n - 1] }
                .mapNotNull { (n, cylinder) -> sources.getOrNull(cylinder)?.let { at(n) to "*$it" } }
            if (switches.isNotEmpty()) {
                fields["gas_switches"] = Stored.Elements(switches.map { (time, key) -> pair(time, Stored.Leaf(key)) })
            }
        }

        val fourth = samplesOf(row.text("Profile4"), 9).take(count)
        if (fourth.isNotEmpty()) {
            val limits = ArrayList<Pair<Long, Double>>()
            val stops = ArrayList<Pair<Long, Double>>()
            var stopping = false
            for ((n, sample) in fourth.withIndex()) {
                val limit = sample.digits(0, 3) ?: continue
                val stop = sample.digits(6, 9) ?: 0
                if (stop > 0 && limit < NDL_CEILING) {
                    stops.changed(at(n), stop.toDouble())
                    stopping = true
                } else {
                    // The limit and the stop are never written at the same moment, so the limit
                    // starts again where the stop clears even at the value it left off at.
                    if (stopping) {
                        stops.changed(at(n), 0.0)
                        limits += at(n) to limit * 60.0
                        stopping = false
                    } else {
                        limits.changed(at(n), limit * 60.0)
                    }
                }
            }
            fields.series("no_deco_time", limits)
            fields.series("decostop", stops)
        }

        val fifth = samplesOf(row.text("Profile5"), 19).take(count)
        if (fifth.isNotEmpty()) {
            val clock = ArrayList<Pair<Long, Double>>()
            for ((n, sample) in fifth.withIndex()) {
                val hundredths = sample.digits(13, 16) ?: continue
                if (hundredths >= CNS_CEILING) break
                clock.changed(at(n), hundredths / 100.0)
            }
            fields.series("cns", clock)
        }
        return fields
    }

    // --- Saying what was read.

    private fun saidOf(version: String?, dives: Int, withProfile: Int, left: List<String>): String {
        val which = when (version) {
            null -> "A Diving Log database that does not say its version"
            CHECKED -> "A Diving Log $version database"
            else -> "A Diving Log $version database, read as $CHECKED is, the version this reader was checked against"
        }
        val read = "$which: ${counted(dives, "dive")}, ${counted(withProfile, "profile")}."
        return if (left.isEmpty()) read else "$read Left out, being images: ${left.joinToString(", ")}."
    }

    private fun counted(count: Int, what: String): String = "$count $what${if (count == 1) "" else "s"}"

    /** The kind of key a dive's one recording is filed under, as a UDDF import files it. */
    private const val PROFILE = "profile"

    /** The no-decompression limit a computer reports when it is longer than it can say. */
    private const val NDL_CEILING = 99L

    /** Where the CNS column stops knowing, in hundredths of a percent. */
    private const val CNS_CEILING = 999L

    /** What tells two cylinders of a dive apart, the mix, the size and the pressures first. */
    private val TANK_VALUES: List<String> = listOf("O2", "Tanksize", "PresS", "He", "PresE")

    /** The water a dive or a site was in, by Diving Log's code. */
    private val WATERS: Map<Long?, String> = mapOf(1L to "salt", 2L to "fresh")

    /** How clear the water was, by Diving Log's code; nought is not given. */
    private val VISIBILITIES: Map<Long?, String> = mapOf(1L to "good", 2L to "medium", 3L to "poor")

    /** How the water was entered, by the codes whose meaning is known; the others are not read. */
    private val ENTRIES: Map<Long, String> = mapOf(0L to "shore", 2L to "boat")

    /** The columns of `Userdefined` that hold what a user typed, under the names Diving Log gives them. */
    private val OWN_FIELDS: List<String> = listOf("Solo") + (2..10).map { "Field$it" }
}

// --- The rows of a table, and the items they become.

/** A row of a table, as the SQLite reader gives it. */
private typealias Row = Map<String, Any?>

/** The text [column] holds, trimmed, or absent where it holds none. */
private fun Row.text(column: String): String? = when (val held = this[column]) {
    is String -> held.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n').trim().ifEmpty { null }
    is Long -> held.toString()
    is Double -> held.toString()
    else -> null
}

/** The whole number [column] holds, read from a number or from text that is one. */
private fun Row.number(column: String): Long? = when (val held = this[column]) {
    is Long -> held
    is Double -> held.toLong()
    is String -> held.trim().toLongOrNull()
    else -> null
}

/** The number [column] holds, read from a number or from text that is one. */
private fun Row.real(column: String): Double? = when (val held = this[column]) {
    is Long -> held.toDouble()
    is Double -> held
    is String -> held.trim().toDoubleOrNull()
    else -> null
}

/** A rating out of five stars as this model's out of ten, or absent where none was given. */
private fun ratingOf(row: Row): Long? = row.number("Rating")?.takeIf { it in 1L..5L }?.let { it * 2 }

/**
 * Reading is the set being filled, and which item each row of each table became.
 *
 * Not immutable.
 */
private class Reading(val set: ItemSet) {
    private val ours = HashMap<Pair<String, Long>, String>()

    /** Adds the item [fields] describe, made from [row] of [table], unless there is nothing to it. */
    fun put(description: ItemDescription, table: String, row: Row, fields: Map<String, Stored>) {
        if (fields.isEmpty()) return
        val item = ItemReader.read(description, Stored.Members(fields), set, Units.DEFAULT)
        val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
        val id = freeName(proposed) { set[it] != null }
        set.add(id, item)
        row.number("ID")?.let { ours[table to it] = id }
    }

    /** A reference to what row [id] of [table] became, or absent where it became nothing. */
    fun pointer(table: String, id: Long?): String? = id?.let { ours[table to it] }?.let { "@$it" }
}

private fun MutableMap<String, Stored>.text(field: String, value: String?) {
    if (value != null) this[field] = Stored.Leaf(value)
}

private fun MutableMap<String, Stored>.number(field: String, value: Double?) {
    if (value != null) this[field] = Stored.Leaf(value)
}

private fun MutableMap<String, Stored>.whole(field: String, value: Long?) {
    if (value != null) this[field] = Stored.Leaf(value)
}

/** Puts a series of [points] under [field] where it has any. */
private fun MutableMap<String, Stored>.series(field: String, points: List<Pair<Long, Double>>) {
    if (points.isNotEmpty()) this[field] = pointsOf(points)
}

private fun pointsOf(points: List<Pair<Long, Double>>): Stored.Elements =
    Stored.Elements(points.map { (time, value) -> pair(time, Stored.Leaf(value)) })

private fun pair(time: Long, value: Stored): Stored = Stored.Elements(listOf(Stored.Leaf(time), value))

/** Adds a point where the value differs from the last, which is all a stepped series needs. */
private fun MutableList<Pair<Long, Double>>.changed(time: Long, value: Double) {
    if (lastOrNull()?.second != value) this += time to value
}

/** [text] cut into samples of [width] characters, a short tail being no sample. */
private fun samplesOf(text: String?, width: Int): List<String> {
    if (text.isNullOrEmpty()) return emptyList()
    return (0..<text.length / width).map { text.substring(it * width, it * width + width) }
}

/** The number in characters [from] to [until] of a sample, or absent where they are not digits. */
private fun String.digits(from: Int, until: Int): Long? = substring(from, until).toLongOrNull()

/** The ids in a list written as text with commas between, as `3,17`. */
private fun idsOf(text: String?): List<Long> = text?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()

/** A mix as this model writes one, or absent where the oxygen is not given or will not make one. */
private fun gasOf(oxygen: Double?, helium: Double?): String? {
    val o2 = oxygen?.takeIf { it > 0 } ?: return null
    return try {
        Gas(o2.roundToLong().toInt(), (helium ?: 0.0).roundToLong().toInt()).toString()
    } catch (refused: IllegalArgumentException) {
        null
    }
}

/**
 * A position written in degrees, minutes and seconds, as `12°34'56.78"N`, in degrees.
 *
 * South and west are negative. A plain number is taken as degrees already.
 */
internal fun degreesOf(written: String): Double? {
    val text = written.trim()
    text.toDoubleOrNull()?.let { return it }
    val parts = Regex("""[\d.]+""").findAll(text).map { it.value.toDoubleOrNull() }.toList()
    if (parts.isEmpty() || parts.any { it == null }) return null
    val (degrees, minutes, seconds) = (parts.filterNotNull() + listOf(0.0, 0.0)).take(3)
    val value = degrees + minutes / 60 + seconds / 3600
    val negative = text.last().uppercaseChar() in "SW" || text.startsWith("-")
    return if (negative) -value else value
}

/** The address a row holds, its parts on one line with commas between. */
private fun addressOf(row: Row): String? =
    listOfNotNull(
        row.text("Street"),
        row.text("Address2"),
        listOfNotNull(row.text("Zip"), row.text("City")).joinToString(" ").ifEmpty { null },
        row.text("State"),
        row.text("Country"),
    ).joinToString(", ").ifEmpty { null }

/** Labelled lines, one for each value given, as `Boat: Blue Lady`. */
private fun lines(vararg labelled: Pair<String, String?>): String? =
    labelled.mapNotNull { (label, value) -> value?.let { "$label: $it" } }.joinToString("\n").ifEmpty { null }

/** The parts given, a blank line between each. */
private fun paragraphs(vararg parts: String?): String? = parts.filterNotNull().joinToString("\n\n").ifEmpty { null }
