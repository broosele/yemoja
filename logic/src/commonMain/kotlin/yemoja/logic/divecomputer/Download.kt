package yemoja.logic.divecomputer

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.DIVE
import yemoja.logic.Types
import yemoja.logic.sameSerial
import yemoja.logic.freeName
import yemoja.logic.unknownOf
import yemoja.logic.untilSurfaced
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * What a download makes of what a device hands over.
 *
 * Every row is logic/divecomputer.md's, and cites the question that settled it rather than
 * arguing it again. See ../../../../../../doc.md.
 */

/**
 * Download turns recordings into this model's items.
 *
 * **A downloaded dive is a skeleton, deliberately.** It has its times, its depths, its gas and
 * what the computer thought; it has no site until asked, no buddies, no rating, no trip, no tags,
 * and nothing about the conditions but temperature and pressure. A computer does not know those
 * things, and the review is where a user adds them. Nothing else is created at all — no gear, no
 * person, no operator, no trip, no region. `LOGIC-20`.
 *
 * **What is dropped is reported.** A device offers more than this model keeps, and inventing a
 * field for each in order to lose nothing would be letting the devices decide what a dive is. So
 * it is dropped and the download says what it dropped. `LOGIC-10`.
 */
object Download {

    /**
     * The dives [recordings] hold, as an item set of their own.
     *
     * Ids are this model's own, minted from what each dive says. A device's own numbering means
     * nothing outside the device, and a downloaded set is matched by what it recorded rather than
     * by what it was called.
     *
     * [into] is the logbook these are going to, where there is one. A recording whose serial
     * finds a gear item there is filed under that item's id, which is what a profile's key is
     * for: it says which computer, and it is the same key each time that computer is read.
     * `LOGIC-23`.
     */
    fun read(recordings: Sequence<Recording>, into: ItemSet? = null): ItemSet {
        val set = ItemSet(Types.ALL)
        val places = ArrayList<Placed>()
        // Each stretch loses what it recorded while floating before any two are compared, so
        // that what separates them is the surface a diver spent rather than the wait a computer
        // was set to. `LOGIC-30`.
        //
        // The stretches of a dive a computer cut up are one recording before they are a dive,
        // so everything below this sees a whole dive. `LOGIC-25`.
        //
        // Oldest first, whatever order the device counts in. Two dives on one day are told
        // apart by the index on their id, and an index that runs backwards through the day is
        // one a reader has to know not to trust.
        for (held in joined(recordings.map { ended(it) }).sortedWith(BY_WHEN)) {
            val recording = joinedIn(usedIn(held))
            val named = recording.serial?.let { into?.let { logbook -> computerIn(logbook, it) } }
            // Where the device said it was, as a site to be asked about. `LOGIC-18`.
            val where = recording.position?.let { placedIn(places, it, set) }
            val fields = diveOf(recording, named, where)
            val item = ItemReader.read(DIVE, Stored.Members(fields), set, Units.DEFAULT)
            val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
            set.add(freeName(proposed) { set[it] != null }, item)
        }
        return set
    }

    /** A fix put with the site it belongs to, so that two dives at one place propose one site. */
    private class Placed(val at: Recording.Position, val id: String)

    /**
     * The id of the proposed site [fix] belongs to, made where it belongs to none yet.
     *
     * **Two fixes within [TOGETHER] are one place.** A week's diving arrives with no site to
     * offer and something has to say whether Tuesday's dive and Friday's are one new site or
     * two; a download of ten dives on one reef should propose one site rather than ten.
     * `LOGIC-18`.
     *
     * The site carries the position and nothing else, not even a name. A fix is not a site: a
     * site has a name, a water type and its regions, and none of that is in a pair of
     * coordinates. Naming it is the reader's answer at review, and until then it is a question
     * wearing the shape of an item. `LOGIC-20`.
     */
    private fun placedIn(places: MutableList<Placed>, fix: Recording.Position, set: ItemSet):
        String {
        places.firstOrNull { metresBetween(it.at, fix) <= TOGETHER }?.let { return it.id }
        val fields = LinkedHashMap<String, Stored>()
        fields["latitude"] = Stored.Leaf(fix.latitude)
        fields["longitude"] = Stored.Leaf(fix.longitude)
        fix.altitude?.let { fields["elevation"] = Stored.Leaf(it) }
        val site = ItemReader.read(Types.DIVE_SITE, Stored.Members(fields), set, Units.DEFAULT)
        val id = freeName(unknownOf(site)) { set[it] != null }
        set.add(id, site)
        places += Placed(fix, id)
        return id
    }

    /**
     * How far apart two fixes are, in metres, close enough for a question about hundreds of them.
     *
     * Flat rather than spherical: over a kilometre the earth's curve is worth centimetres, and
     * what this decides is whether two dives were at one place.
     */
    private fun metresBetween(one: Recording.Position, other: Recording.Position): Double {
        val north = (other.latitude - one.latitude) * METRES_PER_DEGREE
        val shrunk = cos(one.latitude * PI / 180.0)
        val east = (other.longitude - one.longitude) * METRES_PER_DEGREE * shrunk
        return sqrt(north * north + east * east)
    }

    /** The id of the gear item in [logbook] carrying [serial], or absent where none does. */
    private fun computerIn(logbook: ItemSet, serial: String): String? =
        logbook.allOf(Types.GEAR).firstOrNull { gear ->
            val held = (gear.single<String>("serial") as? Result.Usable)?.value
            held != null && sameSerial(held, serial)
        }?.let { logbook.idOf(it) }

    /**
     * The fingerprint to resume a download from, or absent where there is none to resume from.
     *
     * The newest recording in [logbook] that [computer] made and that carries one. Newest by
     * when the dive was, which is the order a device counts in too, so handing it back says
     * *stop when you reach this one*. `DATA-90`.
     *
     * Which recordings are this computer's is said by [serial], where the device gave one: a
     * profile carrying it, or one whose computer is the gear item that does. `LOGIC-23`. Where
     * the device gave none, by [computer]'s slug against the key the profile is filed under,
     * which is the device's name as a download writes it, or against what the profile names,
     * because a profile kept by hand names its computer either way: a gear item by its id,
     * which is already a slug, and a borrowed one by a plain name, which is not. Slugging all
     * of them is what makes *Reef Computer* and `reef_computer` the same answer.
     */
    fun after(logbook: ItemSet, computer: String, serial: String? = null): String? =
        after(marksIn(logbook), computer, serial)

    /** The same answer from [marks] taken earlier, which needs no logbook to hand. `GUI-52`. */
    internal fun after(marks: List<Mark>, computer: String, serial: String? = null): String? =
        marks.filter { it.madeBy(computer, serial) }.maxByOrNull { it.began }?.token

    /**
     * Mark is one recording a logbook holds, cut down to what [after] asks of it.
     *
     * Taken on the logbook's own thread before a download begins, so the download can ask where
     * to stop from another thread while the logbook goes on being edited. `GUI-52`. Immutable.
     */
    internal class Mark(
        val began: String,
        val token: String,
        /** The key the profile is filed under. */
        val key: String,
        /** The computer the profile names, as a slug. */
        val named: String?,
        /** The serial the profile carries itself. */
        val serial: String?,
        /** The serial of the gear item the profile names. */
        val computerSerial: String?,
    ) {
        /** Whether this was recorded by [computer], with [serial]. */
        fun madeBy(computer: String, serial: String?): Boolean {
            if (serial == null) {
                val slug = yemoja.logic.slug(computer)
                return key == slug || named == slug
            }
            val own = this.serial
            if (own != null && sameSerial(own, serial)) return true
            return computerSerial != null && sameSerial(computerSerial, serial)
        }
    }

    /** Every recording in [logbook] that carries a token and says when it was, as a [Mark]. */
    internal fun marksIn(logbook: ItemSet): List<Mark> {
        val marks = ArrayList<Mark>()
        for (dive in logbook.allOf(DIVE)) {
            val profiles = dive.keyed<OwnedItem>("profiles") as? Result.Usable ?: continue
            val began = whenOf(dive) ?: continue
            for ((key, element) in profiles.value) {
                val profile = (element as? Element.Usable)?.value ?: continue
                val held = lastTokenOf(profile) ?: continue
                val names = (profile.single<Reference>("dive_computer") as? Result.Usable)?.value
                val id = (names as? Reference.Identified)?.id
                marks += Mark(
                    began = began,
                    token = held,
                    key = key,
                    named = nameOf(profile),
                    serial = (profile.single<String>("serial") as? Result.Usable)?.value,
                    computerSerial = id?.let {
                        (logbook[it]?.single<String>("serial") as? Result.Usable)?.value
                    },
                )
            }
        }
        return marks
    }

    /** Every token a recording in [logbook] carries, which says what has been taken in already. */
    internal fun tokensIn(logbook: ItemSet): Set<String> {
        val tokens = HashSet<String>()
        for (dive in logbook.allOf(DIVE)) {
            val profiles = dive.keyed<OwnedItem>("profiles") as? Result.Usable ?: continue
            for (element in profiles.value.values) {
                val profile = (element as? Element.Usable)?.value ?: continue
                val held = profile.list<String>("fingerprint") as? Result.Usable ?: continue
                held.value.mapNotNullTo(tokens) { (it as? Element.Usable)?.value }
            }
        }
        return tokens
    }

    /**
     * The last token [profile] carries, which is the one a device reaches last.
     *
     * A recording holds one per stretch it was cut into, in the order they were recorded, so
     * the last is the one that says *stop when you reach this*. `LOGIC-25`.
     */
    private fun lastTokenOf(profile: Item): String? {
        val held = (profile.list<String>("fingerprint") as? Result.Usable)?.value ?: return null
        return held.mapNotNull { (it as? Element.Usable)?.value }.lastOrNull()
    }

    /** What a profile says recorded it, as a slug, by id or by the plain name a one-off has. */
    private fun nameOf(profile: Item): String? =
        when (val held = (profile.single<Reference>("dive_computer") as? Result.Usable)?.value) {
            is Reference.Identified -> held.id
            is Reference.OneOff -> yemoja.logic.slug(held.name)
            null -> null
        }

    /** When a dive began, as text that sorts, or absent where it does not say. */
    private fun whenOf(dive: Item): String? {
        val date = (dive.read("start_date") as? Result.Usable)?.value ?: return null
        val at = (dive.read("start_time") as? Result.Usable)?.value ?: ""
        return "$date $at"
    }

    /**
     * Recordings oldest first, and whatever says nothing about when it was after them.
     *
     * A device reports in an order of its own — a Shearwater counts from its newest — and what
     * an id's index should follow is the day, not the device.
     */
    private val BY_WHEN: Comparator<Recording> = compareBy(nullsLast()) { held ->
        held.began?.let { date -> held.at?.let { Moment(date, it).epochSecond } }
    }

    /** How near two fixes must be to be one place, in metres. `LOGIC-18`. */
    private const val TOGETHER = 500.0

    /** A degree of latitude, in metres, which is near enough the same everywhere. */
    private const val METRES_PER_DEGREE = 111_320.0

    /** What one recording says, as a dive's fields; [where] is the site it proposes, if any. */
    private fun diveOf(
        held: Recording,
        named: String?,
        where: String? = null,
    ): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        // The start is not written: it follows from the recording, and writing it would fix it
        // where a later `recorded_time_offset` could not reach. The zone is the device's where it
        // reports one, which is local time against GMT if its clock was set. `LOGIC-32`.
        held.offset?.let { fields["time_zone_offset"] = Stored.Leaf(it) }
        where?.let { fields["dive_site"] = Stored.Leaf("@$it") }
        gasesOf(held)?.let { fields["gas_sources"] = it }
        profileOf(held, named)?.let { fields["profiles"] = it }
        return fields
    }

    /**
     * The gas the dive was made on, keyed as this model keys one.
     *
     * A tank is not a cylinder: it has no identity, so nothing points at a gear item and the
     * volume is written directly instead. `LOGIC-12`.
     */
    private fun gasesOf(held: Recording): Stored.Members? {
        if (held.gases.isEmpty()) return null
        val sources = LinkedHashMap<String, Stored>()
        for ((at, gas) in held.gases.withIndex()) {
            val fields = LinkedHashMap<String, Stored>()
            gas.gas?.let { fields["gas_type"] = Stored.Leaf(it) }
            gas.volume?.let { fields["volume"] = Stored.Leaf(it) }
            gas.startPressure?.let { fields["start_pressure"] = Stored.Leaf(it) }
            gas.endPressure?.let { fields["end_pressure"] = Stored.Leaf(it) }
            gas.configuration?.let { fields["configuration"] = Stored.Leaf(it) }
            sources[keyAt(at)] = Stored.Members(fields)
        }
        return Stored.Members(sources)
    }

    /**
     * The recording itself, as the one profile a download makes.
     *
     * The computer names it, which is what a profile's key is for: a dive has more than one
     * profile precisely when more than one computer was worn. `JSON-18`.
     */
    private fun profileOf(held: Recording, named: String?): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        // Which computer is not written: the serial is, and the computer is worked out from it.
        // `LOGIC-23`.
        held.serial?.let { fields["serial"] = Stored.Leaf(it) }
        if (held.fingerprints.isNotEmpty()) {
            fields["fingerprint"] = Stored.Elements(held.fingerprints.map { Stored.Leaf(it) })
        }
        held.began?.let { fields["start_date"] = Stored.Leaf(it) }
        held.at?.let { fields["start_time"] = Stored.Leaf(it) }
        held.duration?.let { fields["duration"] = Stored.Leaf(it) }
        // A download hands the conversion over, which is what a file cannot: a depth is a
        // pressure divided by an assumed density, and UDDF discards the density. `DATA-59`.
        held.water?.type?.let { fields["water_type"] = Stored.Leaf(it) }
        held.water?.density?.let { fields["density"] = Stored.Leaf(it) }
        // What this computer measured is this computer's, so a second one beside it overwrites
        // nothing and the dive reads whichever is primary. `DATA-124`.
        held.atmospheric?.let { fields["atmospheric_pressure"] = Stored.Leaf(it) }
        // Written as overrides: a computer usually reports a better figure than its own samples,
        // which are taken only every few seconds. `LOGIC-19`.
        held.maxDepth?.let { fields["max_depth"] = Stored.Leaf(it) }
        held.averageDepth?.let { fields["average_depth"] = Stored.Leaf(it) }
        held.coldest?.let { fields["bottom_temperature"] = Stored.Leaf(it) }
        held.surface?.let { fields["surface_temperature"] = Stored.Leaf(it) }
        held.model?.name?.let { fields["deco_model"] = Stored.Leaf(it) }
        held.model?.conservatism?.let { fields["conservatism"] = Stored.Leaf(it) }
        held.model?.gradientFactorLow?.let { fields["gradient_factor_low"] = Stored.Leaf(it) }
        held.model?.gradientFactorHigh?.let { fields["gradient_factor_high"] = Stored.Leaf(it) }
        fields.putAll(seriesOf(held))
        if (fields.isEmpty()) return null
        // The gear item the serial named, which is the same key each time that computer is read.
        // Where the logbook keeps no item for it, the device's own name, which at least says
        // which one it was. `LOGIC-23`.
        val called = named ?: held.computer?.let { yemoja.logic.slug(it) }?.ifEmpty { null }
        return Stored.Members(mapOf((called ?: "profile") to Stored.Members(fields)))
    }

    /**
     * Every series the samples answer for, thinned, with what the thinning cost written beside.
     *
     * **The figure is written because a missing one claims nothing.** An absent tolerance does not
     * mean the series was left alone, it means nobody recorded what was done. `LOGIC-15`.
     */
    private fun seriesOf(held: Recording): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        val kept = LinkedHashMap<String, Stored>()
        for ((name, of) in READINGS) {
            val points = held.samples.mapNotNull { sample -> of(sample)?.let { sample.at to it } }
            if (points.isEmpty()) continue
            val tolerance = TOLERANCES[name]
            val out = if (tolerance == null) points else thinned(points, tolerance)
            fields[name] = pointsOf(out)
            if (tolerance != null && out.size < points.size) {
                kept[name] = Stored.Leaf(tolerance)
            }
        }
        pressuresOf(held)?.let { fields["pressures"] = it }
        alarmsOf(held)?.let { fields["alarms"] = it }
        gasSwitchesOf(held)?.let { fields["gas_switches"] = it }
        if (kept.isNotEmpty()) fields["tolerances"] = Stored.Members(kept)
        return fields
    }

    /** One series, keyed by the gas source it was read from rather than by a tank. `LOGIC-12`. */
    private fun pressuresOf(held: Recording): Stored.Members? {
        val by = LinkedHashMap<Int, MutableList<Pair<Int, Double>>>()
        for (sample in held.samples) {
            for ((which, bar) in sample.pressures) {
                by.getOrPut(which) { ArrayList() }.add(sample.at to bar)
            }
        }
        if (by.isEmpty()) return null
        val out = LinkedHashMap<String, Stored>()
        for ((which, points) in by) {
            out[keyAt(which)] = pointsOf(thinned(points, TOLERANCES.getValue("pressures")))
        }
        return Stored.Members(out)
    }

    /**
     * Which gas was switched to, named by the first source carrying that mix. `LOGIC-12`.
     *
     * Written as a key reference, which begins with `*` where a reference to an item begins with
     * `@`: it points into a collection on the item rather than at an item. `JSON-19`.
     *
     * **The first is kept whatever it says, and a repeat of it is not.** A Shearwater reports
     * the gas on the first sample of every dive, which is what says the dive began on that one:
     * a collection has no inherent order and nothing may lean on the one its entries sit in, so
     * the gas list cannot say it and this is where it lives. What is dropped is a later reading
     * naming the gas already being breathed, which is a repeat rather than a change and is what
     * a dive cut in two brings along at the second stretch's start. `LOGIC-31`.
     */
    private fun gasSwitchesOf(held: Recording): Stored.Elements? {
        val points = ArrayList<Pair<Int, String>>()
        var breathing: Int? = null
        for (sample in held.samples) {
            val gas = sample.gas ?: continue
            if (gas == breathing) continue
            breathing = gas
            points += sample.at to "*" + keyAt(gas)
        }
        if (points.isEmpty()) return null
        return Stored.Elements(
            points.map { Stored.Elements(listOf(Stored.Leaf(it.first), Stored.Leaf(it.second))) },
        )
    }

    /** The alarms that began, one word apiece and no severity. `LOGIC-16`. */
    private fun alarmsOf(held: Recording): Stored.Elements? {
        val points = held.samples.flatMap { sample -> sample.alarms.map { sample.at to it } }
        if (points.isEmpty()) return null
        return Stored.Elements(
            points.map { Stored.Elements(listOf(Stored.Leaf(it.first), Stored.Leaf(it.second))) },
        )
    }

    /** A run of times and values, as a series is written. */
    private fun pointsOf(held: List<Pair<Int, Double>>): Stored.Elements = Stored.Elements(
        held.map { Stored.Elements(listOf(Stored.Leaf(it.first), Stored.Leaf(it.second))) },
    )

    /**
     * What a gas source at [at] is called, which is what a pressure and a switch name.
     *
     * Tanks numbered from one, since a computer knows them by position and nothing else; a
     * source a user adds is keyed by what it was for instead, which a download cannot know.
     */
    private fun keyAt(at: Int): String = "tank_${at + 1}"

    /** Each series read from one value of a sample, and how to get that value out of one. */
    private val READINGS: List<Pair<String, (Recording.Sample) -> Double?>> = listOf(
        "depth" to { it.depth },
        "temperature" to { it.temperature },
        "decostop" to { it.decostop },
        "no_deco_time" to { it.noDecoTime },
        "cns" to { it.cns },
    )
}

/**
 * [held] with the gas sources nothing used taken out, and every index that named one moved.
 *
 * A computer reports every gas slot it has, including the ones switched off: three of them
 * where one cylinder was dived. Nothing in the library says which are switched on — a gas
 * carries its mix and a usage that is about rebreathers, and a tank its volume and pressures,
 * and a slot that is off looks exactly like a stage carried and not breathed. So what is kept
 * is what was used: a slot a pressure was read from, or one the diver switched to.
 *
 * **A dive begins on a gas, and that gas was used.** Where a computer reports a switch at the
 * first sample — a Perdix does — that switch says which, and it is counted like any other.
 * Where a computer reports no switch at all — an i330R does not — nothing says which, and the
 * first slot is taken to be the one it began on, a gas list beginning with the gas dived.
 *
 * **Where there is only one, it is kept whatever it says**, there being nothing to choose
 * between. `LOGIC-12`, `LOGIC-29`.
 */
internal fun usedIn(held: Recording): Recording {
    if (held.gases.size <= 1) return held
    val used = LinkedHashSet<Int>()
    // The gas it began on, where nothing switched and so nothing said which.
    if (held.samples.none { it.gas != null }) used += 0
    for (sample in held.samples) {
        sample.gas?.let { used += it }
        used += sample.pressures.keys
    }
    if (used.size == held.gases.size) return held
    val kept = held.gases.indices.filter { it in used }
    val moved = kept.withIndex().associate { (at, was) -> was to at }
    return held.copy(
        gases = kept.map { held.gases[it] },
        samples = held.samples.map { sample ->
            sample.copy(
                gas = sample.gas?.let { moved[it] },
                pressures = sample.pressures.mapNotNull { (at, read) ->
                    moved[at]?.let { it to read }
                }.toMap(),
            )
        },
    )
}

/**
 * [held] with a tank and the mix it was breathed on made one source, where they arrived apart.
 *
 * A Perdix reports the transmitter's tank and the gas list as different slots: the pressures come
 * from the tank, which carries no mix, and the mix from a slot that carries no pressures. A dive
 * on one cylinder then reads as two sources, one of which says nothing about how much gas there
 * was and the other nothing about what it held. `LOGIC-31`.
 *
 * **Only where the mix was the gas the dive began on.** The switch that names it is at the start,
 * where a computer says what it is breathing rather than that anything changed, and it is the
 * only one. A second switch later in the dive is a second cylinder, and two sources are then what
 * happened.
 */
internal fun joinedIn(held: Recording): Recording {
    if (held.gases.size != 2) return held
    val pressured = held.gases.indices.filter { at ->
        held.gases[at].startPressure != null || held.gases[at].endPressure != null ||
            held.samples.any { at in it.pressures }
    }
    val mixed = held.gases.indices.filter { held.gases[it].gas != null }
    val tank = pressured.singleOrNull() ?: return held
    val gas = mixed.filter { it != tank }.singleOrNull() ?: return held
    // The tank slot may carry a mix of its own, and a computer that fills it in writes the same
    // one: air against air says nothing about two cylinders. A different mix does, and stays two.
    val theirs = held.gases[tank].gas
    if (theirs != null && !theirs.equals(held.gases[gas].gas, ignoreCase = true)) return held
    if (held.samples.any { gas in it.pressures }) return held
    val switches = held.samples.filter { it.gas != null }
    val began = switches.singleOrNull() ?: return held
    if (began.gas != gas || began.at > AT_THE_START) return held
    val one = held.gases[gas].copy(
        volume = held.gases[gas].volume ?: held.gases[tank].volume,
        startPressure = held.gases[tank].startPressure,
        endPressure = held.gases[tank].endPressure,
    )
    return held.copy(
        gases = listOf(one),
        samples = held.samples.map { sample ->
            sample.copy(
                gas = sample.gas?.let { 0 },
                pressures = sample.pressures.mapNotNull { (at, read) ->
                    if (at == tank) 0 to read else null
                }.toMap(),
            )
        },
    )
}

/**
 * How late a switch may be and still be the gas a dive began on, in seconds.
 *
 * A computer writes what it is breathing within a sample or two of the water closing over it; a
 * cylinder changed for is minutes down at least.
 */
private const val AT_THE_START = 60

/**
 * [held] with what it recorded after the dive ended cut off.
 *
 * A computer keeps recording for a while after a diver surfaces, so a profile arrives with a
 * flat tail at the surface on the end of it. It is cut here, where the recording is still a
 * recording, so that nothing above has to know it was ever there. `LOGIC-30`.
 *
 * Whole samples go, which takes every series with them: a pressure read on the boat and an
 * alarm that sounded after the dive are as much the tail as the depth is. What the device said
 * about the dive as a whole is untouched, its own figures being the dive already.
 */
internal fun ended(held: Recording): Recording {
    val kept = untilSurfaced(held.samples.map { it.depth })
    if (kept == held.samples.size) return held
    return held.copy(samples = held.samples.take(kept))
}
