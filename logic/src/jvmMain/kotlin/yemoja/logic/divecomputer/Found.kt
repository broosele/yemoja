package yemoja.logic.divecomputer

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import yemoja.data.Date
import yemoja.data.Time

/*
 * Finding a dive computer and reading it, which is the JVM's answer to the port.
 *
 * The one part written per target, and it holds no diving knowledge: it gets the bytes off the
 * device and hands over a `Recording`. `LOGIC-2`.
 *
 * See ../../../../../../doc.md — the mapping above this is logic/divecomputer.md.
 */

/**
 * FoundDevices is what this machine can reach through libdivecomputer.
 *
 * **What is attached is enumerated once and matched in memory.** An iterator may be given no
 * descriptor at all, in which case it lists every device on that transport; each is then held
 * against the models by `dc_descriptor_filter`, which compares a vendor and product id, or a
 * port's name, and touches no hardware.
 *
 * The obvious reading of the API is the other way round — ask each of the three hundred and fifty
 * odd models whether it is there — and that is what this did first. It took twenty-six seconds,
 * because each question is a full enumeration of the bus. Two enumerations and a few thousand
 * comparisons is the same answer in a fraction of a second.
 *
 * Empty where the library is not loaded, which is the same answer as *nothing can be read here*.
 */
class FoundDevices : Devices, AutoCloseable {

    // One context and one set of descriptors for as long as this lives, made on first use.
    // Every device found holds both, so they cannot go when a scan ends; freeing them per scan
    // leaked them instead, since nothing knew when the last device had been read. They go with
    // this, which a front end holds for as long as it holds the universe.
    private var context: Pointer? = null

    private var models: List<Pointer?> = emptyList()

    override fun found(): List<DiveComputer> {
        val library = Libdivecomputer.LOADED ?: return emptyList()
        val context = context ?: opened(library) ?: return emptyList()
        return usbhidUnder(library, context, models) +
            serialUnder(library, context, models) +
            advertisingUnder(library, context, models)
    }

    /** The context and every model the library knows, or absent where it would not open. */
    private fun opened(library: Libdivecomputer): Pointer? {
        val made = PointerByReference()
        if (library.dc_context_new(made) != Libdivecomputer.SUCCESS) return null
        val iterator = PointerByReference()
        val found = ArrayList<Pointer?>()
        if (library.dc_descriptor_iterator_new(iterator, made.value) == Libdivecomputer.SUCCESS) {
            val item = PointerByReference()
            while (library.dc_iterator_next(iterator.value, item) == Libdivecomputer.SUCCESS) {
                found += item.value
            }
            library.dc_iterator_free(iterator.value)
        }
        context = made.value
        models = found
        return made.value
    }

    /** Free what the library was holding. Devices found before this are not to be read after it. */
    override fun close() {
        val library = Libdivecomputer.LOADED ?: return
        for (model in models) library.dc_descriptor_free(model)
        models = emptyList()
        context?.let { library.dc_context_free(it) }
        context = null
    }

    /** What to call the model [descriptor] stands for. */
    private fun nameOf(library: Libdivecomputer, descriptor: Pointer?): String = listOfNotNull(
        library.dc_descriptor_get_vendor(descriptor),
        library.dc_descriptor_get_product(descriptor),
    ).joinToString(" ")

    private fun usbhidUnder(
        library: Libdivecomputer,
        context: Pointer?,
        models: List<Pointer?>,
    ): List<DiveComputer> {
        val iterator = PointerByReference()
        val opened = library.dc_usbhid_iterator_new(iterator, context, null)
        if (opened != Libdivecomputer.SUCCESS) return emptyList()
        val found = ArrayList<DiveComputer>()
        val item = PointerByReference()
        while (library.dc_iterator_next(iterator.value, item) == Libdivecomputer.SUCCESS) {
            val device = item.value
            val said = CUsbHid()
            said.vid = library.dc_usbhid_device_get_vid(device)
            said.pid = library.dc_usbhid_device_get_pid(device)
            said.write()
            val model = models.firstOrNull {
                library.dc_descriptor_filter(it, Transport.USBHID, said) != 0
            } ?: continue
            found += Attached(library, context, model, nameOf(library, model)) {
                val stream = PointerByReference()
                if (library.dc_usbhid_open(stream, context, device) == Libdivecomputer.SUCCESS) {
                    stream.value
                } else {
                    null
                }
            }
        }
        library.dc_iterator_free(iterator.value)
        return found
    }

    private fun serialUnder(
        library: Libdivecomputer,
        context: Pointer?,
        models: List<Pointer?>,
    ): List<DiveComputer> {
        val iterator = PointerByReference()
        val opened = library.dc_serial_iterator_new(iterator, context, null)
        if (opened != Libdivecomputer.SUCCESS) return emptyList()
        val found = ArrayList<DiveComputer>()
        val item = PointerByReference()
        while (library.dc_iterator_next(iterator.value, item) == Libdivecomputer.SUCCESS) {
            val port = library.dc_serial_device_get_name(item.value) ?: continue
            val model = models.firstOrNull {
                library.dc_descriptor_filter(it, Transport.SERIAL, port) != 0
            } ?: continue
            found += Attached(library, context, model, "${nameOf(library, model)} on $port") {
                val stream = PointerByReference()
                if (library.dc_serial_open(stream, context, port) == Libdivecomputer.SUCCESS) {
                    stream.value
                } else {
                    null
                }
            }
        }
        library.dc_iterator_free(iterator.value)
        return found
    }
}

/** `dc_transport_t`, which is a bit apiece. */
internal object Transport {
    const val SERIAL = 1
    const val USBHID = 4
    const val BLE = 32
}

/**
 * Attached is one device that is there now, waiting to be read.
 *
 * **Read eagerly.** `dc_device_foreach` walks the dives itself and calls back for each, so there
 * is no way to hand them over one at a time without a thread. The port asks for a sequence
 * because that is what lets an implementation be lazy; this one is not, and a download of a
 * full computer holds every dive in memory until it is staged.
 */
internal class Attached(
    private val library: Libdivecomputer,
    private val context: Pointer?,
    private val descriptor: Pointer?,
    override val name: String,
    private val open: () -> Pointer?,
) : DiveComputer {

    override fun recordings(resume: (String?) -> String?): Sequence<Recording> {
        val stream = open() ?: return emptySequence()
        val device = PointerByReference()
        val opened = library.dc_device_open(device, context, descriptor, stream)
        if (opened != Libdivecomputer.SUCCESS) {
            library.dc_iostream_close(stream)
            return emptySequence()
        }
        // Where the last download got to, so the device reports only what came after. `DATA-90`.
        // Told twice: now, knowing only the name, and again once the device has said its serial,
        // which is what says whose chain this is. The device says it before the first dive, and
        // a later telling replaces an earlier one. `LOGIC-23`.
        fun stopAt(serial: String?) {
            val known = bytesOf(resume(serial)) ?: return
            library.dc_device_set_fingerprint(device.value, known, known.size)
        }
        stopAt(null)
        var serial: String? = null
        val events = Libdivecomputer.EventCallback { _, event, data, _ ->
            if (event == Libdivecomputer.DEVINFO && data != null) {
                serial = serialOf(data)
                stopAt(serial)
            }
        }
        library.dc_device_set_events(device.value, Libdivecomputer.DEVINFO, events, null)
        val held = ArrayList<Recording>()
        val callback = Libdivecomputer.DiveCallback { data, size, fingerprint, fsize, _ ->
            val known = hexOf(fingerprint, fsize)
            recordingOf(library, device.value, data, size, name, serial, known)?.let { held += it }
            // Non-zero carries on; a download that stopped at the first dive would be a download
            // of one dive.
            1
        }
        library.dc_device_foreach(device.value, callback, null)
        library.dc_device_close(device.value)
        library.dc_iostream_close(stream)
        return held.asSequence()
    }
}

/**
 * The serial in a `dc_event_devinfo_t`, which is the third of three unsigned ints.
 *
 * As a decimal number, which is how the library has it; what the maker prints is matched
 * either way above the port. `LOGIC-23`.
 */
internal fun serialOf(devinfo: Pointer): String =
    (devinfo.getInt(SERIAL_OFFSET).toLong() and UNSIGNED).toString()

/** Past `model` and `firmware`, four bytes apiece. */
private const val SERIAL_OFFSET: Long = 8

/** The mask that reads a C `unsigned int` back from a Java int. */
private const val UNSIGNED: Long = 0xffffffffL

/** What a device handed out as [size] bytes, as hexadecimal. */
internal fun hexOf(held: Pointer?, size: Int): String? {
    if (held == null || size <= 0) return null
    return held.getByteArray(0, size).joinToString("") {
        val digits = (it.toInt() and 0xff).toString(16)
        if (digits.length == 1) "0$digits" else digits
    }
}

/** Hexadecimal back to the bytes a device takes, or absent where it is not hexadecimal. */
internal fun bytesOf(hex: String?): ByteArray? {
    if (hex == null || hex.length < 2 || hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (at in out.indices) {
        val byte = hex.substring(at * 2, at * 2 + 2).toIntOrNull(16) ?: return null
        out[at] = byte.toByte()
    }
    return out
}

/** One dive's bytes, parsed into what the port carries. */
private fun recordingOf(
    library: Libdivecomputer,
    device: Pointer?,
    data: Pointer?,
    size: Int,
    name: String,
    serial: String?,
    fingerprint: String?,
): Recording? {
    val parser = PointerByReference()
    if (library.dc_parser_new(parser, device, data, size.toLong()) != Libdivecomputer.SUCCESS) {
        return null
    }
    try {
        return readingOf(library, parser.value, name, serial, fingerprint)
    } finally {
        library.dc_parser_destroy(parser.value)
    }
}

/** What one parser says, as a recording. */
private fun readingOf(
    library: Libdivecomputer,
    parser: Pointer?,
    name: String,
    serial: String?,
    fingerprint: String?,
): Recording {
    val clock = CDateTime()
    val when_ = library.dc_parser_get_datetime(parser, clock) == Libdivecomputer.SUCCESS
    val salinity = fieldOf(library, parser, Field.SALINITY, CSalinity())
    val model = fieldOf(library, parser, Field.DECOMODEL, CDecoModel())
    val (gases, sourceOfMix) = sourcesOf(mixesOf(library, parser), tanksOf(library, parser))
    return Recording(
        computer = name,
        serial = serial,
        fingerprint = fingerprint,
        began = if (when_) Date(clock.year, clock.month, clock.day) else null,
        at = if (when_) Time(clock.hour, clock.minute, clock.second) else null,
        offset = clock.timezone.takeIf { when_ && it != CDateTime.NONE },
        duration = wholeOf(library, parser, Field.DIVETIME)?.toDouble(),
        maxDepth = realOf(library, parser, Field.MAXDEPTH),
        averageDepth = realOf(library, parser, Field.AVGDEPTH),
        water = salinity?.let {
            Recording.Water(if (it.type == 1) "salt" else "fresh", it.density)
        },
        atmospheric = realOf(library, parser, Field.ATMOSPHERIC),
        coldest = realOf(library, parser, Field.TEMPERATURE_MINIMUM),
        surface = realOf(library, parser, Field.TEMPERATURE_SURFACE),
        model = model?.let {
            Recording.DecoModel(
                name = DECO_MODELS.getOrNull(it.type),
                conservatism = it.conservatism,
                // Whole percentages there, a fraction here.
                gradientFactorLow = it.low.takeIf { low -> low > 0 }?.let { low -> low / 100.0 },
                gradientFactorHigh = it.high.takeIf { high -> high > 0 }?.let { h -> h / 100.0 },
            )
        },
        gases = gases,
        // A switch is reported as a mix and lands on a source; a mix no source carries is dropped,
        // there being nothing to point at. `LOGIC-12`.
        samples = samplesOf(library, parser).map { it.copy(gas = it.gas?.let(sourceOfMix::get)) },
    )
}

/** A field answered as a whole number, or absent where the device does not report it. */
private fun wholeOf(library: Libdivecomputer, parser: Pointer?, field: Int): Int? {
    val held = Memory(8)
    if (library.dc_parser_get_field(parser, field, 0, held) != Libdivecomputer.SUCCESS) return null
    return held.getInt(0)
}

/** A field answered as a real number. */
private fun realOf(library: Libdivecomputer, parser: Pointer?, field: Int): Double? {
    val held = Memory(8)
    if (library.dc_parser_get_field(parser, field, 0, held) != Libdivecomputer.SUCCESS) return null
    return held.getDouble(0)
}

/** A field answered as one of the library's own structs. */
private fun <T : com.sun.jna.Structure> fieldOf(
    library: Libdivecomputer,
    parser: Pointer?,
    field: Int,
    into: T,
    flags: Int = 0,
): T? {
    if (library.dc_parser_get_field(parser, field, flags, into) != Libdivecomputer.SUCCESS) {
        return null
    }
    into.read()
    return into
}

/** Every mix the dive was made on, in the order the device numbers them. */
private fun mixesOf(library: Libdivecomputer, parser: Pointer?): List<CGasMix?> =
    (0..<(wholeOf(library, parser, Field.GASMIX_COUNT) ?: 0)).map {
        fieldOf(library, parser, Field.GASMIX, CGasMix(), it)
    }

/** Every tank the dive carried, in the order the device numbers them. */
private fun tanksOf(library: Libdivecomputer, parser: Pointer?): List<CTank?> =
    (0..<(wholeOf(library, parser, Field.TANK_COUNT) ?: 0)).map {
        fieldOf(library, parser, Field.TANK, CTank(), it)
    }

/**
 * Two arrays as one collection, and which source each mix lands on.
 *
 * A tank and the mix it carried are one source here, tanks first and in their own order — which
 * is what lets a tank pressure keep its index. A mix no tank carried is still a source, added
 * after them. The map says which source a *mix* index lands on: the first tank carrying it, or
 * the source added for it. A switch is reported as a mix, and where two tanks share one, only
 * the first can be pointed at. `LOGIC-12`.
 */
internal fun sourcesOf(
    mixes: List<CGasMix?>,
    tanks: List<CTank?>,
): Pair<List<Recording.GasSource>, Map<Int, Int>> {
    val sources = ArrayList<Recording.GasSource>()
    val landing = HashMap<Int, Int>()
    for (tank in tanks) {
        val mixAt = tank?.gasmix?.takeIf { it != CTank.UNKNOWN }
        val mix = mixAt?.let { mixes.getOrNull(it) }
        if (mixAt != null && mixAt !in landing) landing[mixAt] = sources.size
        val sidemount = tank?.usage == SIDEMOUNT || mix?.usage == SIDEMOUNT
        sources += Recording.GasSource(
            gas = mix?.let { gasOf(it) },
            volume = tank?.volume?.takeIf { tank.type != CTank.NO_VOLUME },
            startPressure = tank?.beginpressure?.takeIf { it > 0 },
            endPressure = tank?.endpressure?.takeIf { it > 0 },
            // `sidemount` and nothing else: the rest are rebreather words. `LOGIC-12`.
            configuration = if (sidemount) "sidemount" else null,
        )
    }
    for ((at, mix) in mixes.withIndex()) {
        if (mix == null || at in landing) continue
        landing[at] = sources.size
        sources += Recording.GasSource(gas = gasOf(mix))
    }
    return sources to landing
}

/** A mix as divers write one, which is what the model holds. */
private fun gasOf(mix: CGasMix): String {
    val oxygen = (mix.oxygen * 100).toInt()
    val helium = (mix.helium * 100).toInt()
    if (helium > 0) return "Tx$oxygen/$helium"
    if (oxygen in 20..22) return "air"
    return "EAN$oxygen"
}

/** Every reading through the dive, in the order the device recorded them. */
private fun samplesOf(library: Libdivecomputer, parser: Pointer?): List<Recording.Sample> {
    val walked = Walked()
    library.dc_parser_samples_foreach(parser, walked, null)
    return walked.done()
}

/**
 * Walked gathers what a sample walk reports.
 *
 * **A time opens a sample and everything after it belongs to that time**, which is how the
 * library's callback works: `DC_SAMPLE_TIME` marks a new instant and each reading that follows
 * describes it.
 *
 * Visible to the module rather than to this file alone, so that the offsets it reads and the
 * constants it maps can be driven by a test. They are the part of this file most easily wrong and
 * the part a device would not obviously prove.
 */
internal class Walked : Libdivecomputer.SampleCallback {

    private val held = ArrayList<Recording.Sample>()
    private var at = 0
    private var depth: Double? = null
    private var temperature: Double? = null
    private var pressures = LinkedHashMap<Int, Double>()
    private var gas: Int? = null
    private var decostop: Double? = null
    private var noDecoTime: Double? = null
    private var cns: Double? = null
    private var alarms = ArrayList<String>()
    private var opened = false

    override fun invoke(type: Int, value: Pointer, userdata: Pointer?) {
        if (type == SampleType.TIME) {
            close()
            // Milliseconds there, seconds here, which is what a file holds. `DATA-58`.
            at = Sampled.time(value) / 1000
            opened = true
            return
        }
        when (type) {
            SampleType.DEPTH -> depth = Sampled.depth(value)
            SampleType.TEMPERATURE -> temperature = Sampled.temperature(value)
            SampleType.CNS -> cns = Sampled.cns(value) * 100
            SampleType.GASMIX -> gas = Sampled.gasmix(value)
            SampleType.PRESSURE ->
                pressures[Sampled.pressureTank(value)] = Sampled.pressureValue(value)

            SampleType.DECO -> when (Sampled.decoType(value)) {
                DECO_NDL -> noDecoTime = Sampled.decoTime(value).toDouble()
                DECO_DECOSTOP -> decostop = Sampled.decoDepth(value)
                // A safety stop is not a required one, and a deep stop is not either. `LOGIC-13`.
                else -> Unit
            }

            SampleType.EVENT -> {
                // Beginnings only: two identical words in the series could not say which was
                // which. `LOGIC-16`.
                val beginning = Sampled.eventFlags(value) and EVENT_END == 0
                if (beginning) ALARMS[Sampled.eventType(value)]?.let { alarms += it }
            }

            else -> Unit
        }
    }

    /** Everything the walk gathered, the last sample closed. */
    fun done(): List<Recording.Sample> {
        close()
        return held
    }

    private fun close() {
        if (!opened) return
        held += Recording.Sample(
            at = at,
            depth = depth,
            temperature = temperature,
            pressures = LinkedHashMap(pressures),
            gas = gas,
            decostop = decostop,
            noDecoTime = noDecoTime,
            cns = cns,
            alarms = ArrayList(alarms),
        )
        depth = null
        temperature = null
        pressures = LinkedHashMap()
        gas = null
        decostop = null
        noDecoTime = null
        cns = null
        alarms = ArrayList()
    }
}

/** `dc_field_type_t`, in the header's order. */
private object Field {
    const val DIVETIME = 0
    const val MAXDEPTH = 1
    const val AVGDEPTH = 2
    const val GASMIX_COUNT = 3
    const val GASMIX = 4
    const val SALINITY = 5
    const val ATMOSPHERIC = 6
    const val TEMPERATURE_SURFACE = 7
    const val TEMPERATURE_MINIMUM = 8
    const val TANK_COUNT = 10
    const val TANK = 11
    const val DECOMODEL = 13
    const val LOCATION = 14
}

/** `dc_sample_type_t`, in the header's order. */
private object SampleType {
    const val TIME = 0
    const val DEPTH = 1
    const val PRESSURE = 2
    const val TEMPERATURE = 3
    const val EVENT = 4
    const val CNS = 11
    const val DECO = 12
    const val GASMIX = 13
}

/** `dc_decomodel_type_t`, in the header's order. */
private val DECO_MODELS = listOf(null, "buhlmann", "vpm", "rgbm", "dciem")

/** `DC_USAGE_SIDEMOUNT`, the one usage this model has a word for. */
private const val SIDEMOUNT = 3

/**
 * Two of `dc_deco_type_t`'s four, in the header's order.
 *
 * **A safety stop is `1` and a required stop is `2`**, which is worth writing down: taking the
 * first for the second is exactly the mistake `LOGIC-13` refuses, and it would make `deco` derive
 * true for every recreational dive that held three minutes at five metres. A deep stop is `3` and
 * is dropped with it.
 */
private const val DECO_NDL = 0

private const val DECO_DECOSTOP = 2

/** `SAMPLE_FLAGS_END`, `1 << 1`, which marks an event stopping rather than starting. */
private const val EVENT_END = 2

/**
 * Five of the twenty-six events, by their `dc_sample_event_t` value.
 *
 * `LOGIC-16` refuses to stretch a near-miss into one of the nine words this model has, so what is
 * not here is dropped rather than approximated. `breath`, `deco`, `error` and `skincooling` are
 * the four of ours that nothing maps onto — `SAMPLE_EVENT_DECOSTOP` is not our `deco`, which is
 * what the `DC_SAMPLE_DECO` reading carries.
 */
private val ALARMS = mapOf(
    2 to "rbt",
    3 to "ascent",
    6 to "link",
    9 to "surface",
    22 to "microbubbles",
)
