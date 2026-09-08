package yemoja.logic.divecomputer

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.DIVE
import yemoja.logic.Types
import yemoja.logic.freeName
import yemoja.logic.unknownOf

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
     */
    fun read(recordings: Sequence<Recording>): ItemSet {
        val set = ItemSet(Types.ALL)
        for (recording in recordings) {
            val item = ItemReader.read(DIVE, Stored.Members(diveOf(recording)), set, Units.DEFAULT)
            val proposed = item.description.proposedId?.invoke(item) ?: unknownOf(item)
            set.add(freeName(proposed) { set[it] != null }, item)
        }
        return set
    }

    /**
     * The fingerprint to resume a download from, or absent where there is none to resume from.
     *
     * The newest recording in [logbook] that [computer] made and that carries one. Newest by
     * when the dive was, which is the order a device counts in too, so handing it back says
     * *stop when you reach this one*. `DATA-90`.
     *
     * Matched on the slug of what recorded it, because a profile names its computer either way:
     * a gear item by its id, which is already one, and a borrowed one by a plain name, which is
     * not. Slugging both is what makes *Reef Computer* and `reef_computer` the same answer.
     */
    fun after(logbook: ItemSet, computer: String): String? {
        var newest: Pair<String, String>? = null
        for (dive in logbook.allOf(DIVE)) {
            val profiles = dive.keyed<OwnedItem>("profiles") as? Result.Usable ?: continue
            for (element in profiles.value.values) {
                val profile = (element as? Element.Usable)?.value ?: continue
                if (nameOf(profile) != yemoja.logic.slug(computer)) continue
                val held = (profile.single<String>("fingerprint") as? Result.Usable)?.value
                    ?: continue
                val began = whenOf(dive) ?: continue
                if (newest == null || began > newest.first) newest = began to held
            }
        }
        return newest?.second
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

    /** What one recording says, as a dive's fields. */
    private fun diveOf(held: Recording): Map<String, Stored> {
        val fields = LinkedHashMap<String, Stored>()
        held.began?.let { fields["start_date"] = Stored.Leaf(it) }
        held.at?.let { fields["start_time"] = Stored.Leaf(it) }
        // Written as overrides: a computer usually reports a better figure than its own recording,
        // which is sampled only every few seconds. `LOGIC-19`.
        held.duration?.let { fields["duration"] = Stored.Leaf(it) }
        held.maxDepth?.let { fields["max_depth"] = Stored.Leaf(it) }
        held.averageDepth?.let { fields["average_depth"] = Stored.Leaf(it) }
        environmentOf(held)?.let { fields["environment"] = it }
        gasesOf(held)?.let { fields["gas_sources"] = it }
        profileOf(held)?.let { fields["profiles"] = it }
        return fields
    }

    /** The conditions, of which a computer knows temperature and pressure and nothing else. */
    private fun environmentOf(held: Recording): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        held.coldest?.let { fields["bottom_temperature"] = Stored.Leaf(it) }
        held.surface?.let { fields["surface_temperature"] = Stored.Leaf(it) }
        held.atmospheric?.let { fields["atmospheric_pressure"] = Stored.Leaf(it) }
        return fields.ifEmpty { null }?.let { Stored.Members(it) }
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
    private fun profileOf(held: Recording): Stored.Members? {
        val fields = LinkedHashMap<String, Stored>()
        held.computer?.let { fields["dive_computer"] = Stored.Leaf(it) }
        held.fingerprint?.let { fields["fingerprint"] = Stored.Leaf(it) }
        held.began?.let { fields["start_date"] = Stored.Leaf(it) }
        held.at?.let { fields["start_time"] = Stored.Leaf(it) }
        held.offset?.let { fields["gmt_offset"] = Stored.Leaf(it) }
        held.duration?.let { fields["duration"] = Stored.Leaf(it) }
        // A download hands the conversion over, which is what a file cannot: a depth is a
        // pressure divided by an assumed density, and UDDF discards the density. `DATA-59`.
        held.water?.type?.let { fields["water_type"] = Stored.Leaf(it) }
        held.water?.density?.let { fields["density"] = Stored.Leaf(it) }
        held.model?.name?.let { fields["deco_model"] = Stored.Leaf(it) }
        held.model?.conservatism?.let { fields["conservatism"] = Stored.Leaf(it) }
        held.model?.gradientFactorLow?.let { fields["gradient_factor_low"] = Stored.Leaf(it) }
        held.model?.gradientFactorHigh?.let { fields["gradient_factor_high"] = Stored.Leaf(it) }
        fields.putAll(seriesOf(held))
        if (fields.isEmpty()) return null
        val key = held.computer?.let { yemoja.logic.slug(it) }?.ifEmpty { null } ?: "profile"
        return Stored.Members(mapOf(key to Stored.Members(fields)))
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
     */
    private fun gasSwitchesOf(held: Recording): Stored.Elements? {
        val points = held.samples.mapNotNull { sample ->
            sample.gas?.let { sample.at to "*" + keyAt(it) }
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

    /** What a gas source at [at] is called, which is what a pressure and a switch name. */
    private fun keyAt(at: Int): String = if (at == 0) "gas" else "gas#$at"

    /** Each series read from one value of a sample, and how to get that value out of one. */
    private val READINGS: List<Pair<String, (Recording.Sample) -> Double?>> = listOf(
        "depth" to { it.depth },
        "temperature" to { it.temperature },
        "decostop" to { it.decostop },
        "no_deco_time" to { it.noDecoTime },
        "cns" to { it.cns },
    )
}
