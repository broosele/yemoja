package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Time

/*
 * What a dive computer hands over, and the door it hands it through.
 *
 * See ../../../../../../doc.md — the field-by-field mapping is logic/divecomputer.md, and
 * `LOGIC-2` is where this boundary comes from.
 */

/**
 * Recording is one dive as a device reports it, before any of it is a dive.
 *
 * **The port's whole vocabulary.** Reaching a device needs a native library and platform
 * Bluetooth, which the logic layer should not have, so what crosses the boundary is this: a set
 * of readings about the dive as a whole and a walk of what was recorded through it. That is what
 * a dive computer *is*, rather than what one library calls it, so the mapping above it is written
 * once and the half below it is only *get the bytes off the device* — bar two translations that
 * must be made in the library's own words, which `LOGIC-2` names. `LOGIC-2`.
 *
 * **Values are in the model's default units** — metres, bar, degrees Celsius, litres, seconds —
 * and converting into them is the implementation's, so that no arithmetic is written three times.
 *
 * **A field nobody reported is absent, and absent is not zero.** A computer that does not measure
 * gas says nothing about pressure rather than saying none.
 *
 * Immutable, and a data class so that a reading can be varied without writing the rest out again.
 */
data class Recording(
    /** The device by name, which is what a `profile` is filed under. */
    val computer: String? = null,
    /**
     * The device's serial, or absent where the device did not say.
     *
     * Written to the profile, which names its computer through it: the gear item carrying the
     * same serial. As the library gives it, a decimal number; what a maker prints is matched
     * either way. `LOGIC-23`.
     */
    val serial: String? = null,
    /**
     * What the device knows this recording by, as hexadecimal.
     *
     * Handed back to the device before a later download, which then reports only what came
     * after it. `DATA-90` keeps it on the profile, being a fact about the recording rather than
     * a bookmark of the application's.
     */
    val fingerprint: String? = null,
    val began: Date? = null,
    val at: Time? = null,
    /** Seconds east of UTC, or absent where the device reports no zone. `LOGIC-11`. */
    val offset: Int? = null,
    /** Seconds. Written as an override: a computer's own figure beats its recording. `LOGIC-19`. */
    val duration: Double? = null,
    val maxDepth: Double? = null,
    val averageDepth: Double? = null,
    val water: Water? = null,
    val atmospheric: Double? = null,
    /** The coldest water, which is a dive's bottom temperature here. */
    val coldest: Double? = null,
    /** The water at the surface, which is not the air and does not pretend to be. `LOGIC-19`. */
    val surface: Double? = null,
    val model: DecoModel? = null,
    val gases: List<GasSource> = emptyList(),
    val samples: List<Sample> = emptyList(),
) {

    /** The salinity a computer was set to: a type, and the density it turned pressure by. */
    data class Water(val type: String? = null, val density: Double? = null)

    /** What the computer worked its stops out with, which is metadata about data this keeps. */
    data class DecoModel(
        val name: String? = null,
        val conservatism: Int? = null,
        val gradientFactorLow: Double? = null,
        val gradientFactorHigh: Double? = null,
    )

    /**
     * One gas the dive was made on, and the cylinder it came out of.
     *
     * Two arrays there — mixes and tanks, the second indexing into the first — and one collection
     * here, because a gas and the cylinder it came from are one thing. A mix breathed without a
     * tank is still a source. `LOGIC-12`.
     */
    data class GasSource(
        /** As divers write one: `air`, `EAN32`, `Tx18/45`. */
        val gas: String? = null,
        val volume: Double? = null,
        val startPressure: Double? = null,
        val endPressure: Double? = null,
        /** `sidemount` and nothing else, that being the only usage this model has a word for. */
        val configuration: String? = null,
    )

    /**
     * What was read at one instant, which is a point of every series it has a value for.
     *
     * Theirs is one list carrying whatever was measured; ours is a series per quantity with its
     * own times. So a sample contributes to the series it answers for and to no others.
     */
    data class Sample(
        /** Seconds from the start of the recording, which is what a file holds. `DATA-58`. */
        val at: Int,
        val depth: Double? = null,
        val temperature: Double? = null,
        /** By which gas source, as an index into [Recording.gases]. */
        val pressures: Map<Int, Double> = emptyMap(),
        /** The gas switched to, as an index into [Recording.gases]. */
        val gas: Int? = null,
        /** The depth of a stop that must be made, which is not a safety stop. `LOGIC-13`. */
        val decostop: Double? = null,
        val noDecoTime: Double? = null,
        val cns: Double? = null,
        /** Five of the twenty-six a device reports, and beginnings only. `LOGIC-16`. */
        val alarms: List<String> = emptyList(),
    )
}

/**
 * DiveComputer is one device, as much of one as this layer knows about.
 *
 * A download takes minutes, so what is read comes back as a sequence: an implementation *may*
 * hand dives over one at a time, and a caller *may* stop walking. Neither is promised. The JVM's
 * reads everything before it answers, and nothing cancels yet — `TUI-8`.
 *
 * **A fix is not carried, deliberately.** `LOGIC-18` decided what one is for and no review
 * question exists to land it in, so the port does not ask for it: a field every implementation
 * must fill and nothing reads is a lie waiting to be believed.
 */
interface DiveComputer {

    /** What to call it, which is what a downloaded profile is filed under. */
    val name: String

    /**
     * Every dive it holds, oldest first, read as the sequence is walked.
     *
     * [session] answers what the download asks while it runs: where to stop, and the codes a
     * device that guards itself wants. Asked rather than told because each answer needs
     * something the device says only once opened. `DATA-90`, `LOGIC-23`, `LOGIC-24`.
     */
    fun recordings(session: Session = Session.NONE): Sequence<Recording>
}

/**
 * Session is what a download asks of the logbook while it runs.
 *
 * Where to stop needs the device's serial, which it says only once opened, and a code needs to
 * be typed while the device is showing it. So a download is handed something to ask rather than
 * the answers. Every answer has a default that means *nothing*, and a download given nothing
 * transfers everything and types no code.
 */
interface Session {

    /**
     * The fingerprint of the last dive already held, or absent where none is.
     *
     * [serial] is the device's, once it has said it, or absent where it never does. The device is
     * told where to stop and reports only what came after, so a second download transfers
     * nothing it has given before. `DATA-90`. The serial is what says whose chain this is.
     * `LOGIC-23`.
     */
    fun resume(serial: String?): String? = null

    /** The access code kept for the device advertising as [name], or absent where none is. */
    fun accessCode(name: String): ByteArray? = null

    /** The code the device called [name] is showing, as the user types it, or absent. */
    fun pin(name: String): String? = null

    /** An access code the device called [name] handed over, to be kept for next time. */
    fun keep(name: String, accessCode: ByteArray) {}

    companion object {
        /** A session that answers nothing. */
        val NONE: Session = object : Session {}
    }
}

/**
 * Devices is what can be reached, which only a platform can answer.
 *
 * The one thing implemented per target. Everything above it is written once.
 */
interface Devices {

    /** Every dive computer this machine can reach now. */
    fun found(): List<DiveComputer>
}
