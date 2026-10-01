package yemoja.logic.divecomputer

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/*
 * libdivecomputer, as much of it as this side of the port calls.
 *
 * Declarations only, taken from the published headers, which is the interface a program links
 * against and what the LGPL contemplates. Nothing of the implementation behind them was read.
 *
 * See ../../../../../../doc.md — `LOGIC-2` is why this is below the port and not above it.
 */

/**
 * Libdivecomputer is the C library's own vocabulary, and the only place it is named.
 *
 * Spelt as the library spells itself, which also keeps it clear of [DiveComputer], the port's own
 * word for a device. Two names differing only in case cannot both be a class on Windows.
 *
 * **Linked as a shared library.** The licence position in README turns on it: dynamically linked,
 * the rest of the application stays its own, and a user must be able to replace it. So this is a
 * `.dll`, a `.so` or a `.dylib` loaded at run time and never compiled in.
 *
 * A `dc_status_t` comes back as an int, of which `0` is success; a `dc_iterator_next` answers `1`
 * when there is no more. The names are the library's, not this project's, because a declaration
 * that renamed them would be a second thing to check against the header.
 */
internal interface Libdivecomputer : Library {

    fun dc_context_new(context: PointerByReference): Int

    fun dc_context_free(context: Pointer?): Int

    fun dc_descriptor_iterator_new(iterator: PointerByReference, context: Pointer?): Int

    fun dc_iterator_next(iterator: Pointer?, item: PointerByReference): Int

    fun dc_iterator_free(iterator: Pointer?): Int

    fun dc_descriptor_get_vendor(descriptor: Pointer?): String?

    fun dc_descriptor_get_product(descriptor: Pointer?): String?

    fun dc_descriptor_get_transports(descriptor: Pointer?): Int

    fun dc_descriptor_get_model(descriptor: Pointer?): Int

    fun dc_descriptor_free(descriptor: Pointer?)

    fun dc_usbhid_iterator_new(
        iterator: PointerByReference,
        context: Pointer?,
        descriptor: Pointer?,
    ): Int

    fun dc_usbhid_open(iostream: PointerByReference, context: Pointer?, device: Pointer?): Int

    fun dc_usbhid_device_get_vid(device: Pointer?): Short

    fun dc_usbhid_device_get_pid(device: Pointer?): Short

    fun dc_descriptor_filter(descriptor: Pointer?, transport: Int, userdata: Structure?): Int

    fun dc_descriptor_filter(descriptor: Pointer?, transport: Int, userdata: String?): Int

    fun dc_serial_iterator_new(
        iterator: PointerByReference,
        context: Pointer?,
        descriptor: Pointer?,
    ): Int

    fun dc_serial_open(iostream: PointerByReference, context: Pointer?, name: String): Int

    fun dc_serial_device_get_name(device: Pointer?): String?

    fun dc_iostream_close(iostream: Pointer?): Int

    fun dc_custom_open(
        iostream: PointerByReference,
        context: Pointer?,
        transport: Int,
        callbacks: CustomCallbacks,
        userdata: Pointer?,
    ): Int

    fun dc_device_open(
        out: PointerByReference,
        context: Pointer?,
        descriptor: Pointer?,
        iostream: Pointer?,
    ): Int

    fun dc_device_set_fingerprint(device: Pointer?, data: ByteArray, size: Int): Int

    fun dc_device_set_events(
        device: Pointer?,
        events: Int,
        callback: EventCallback,
        userdata: Pointer?,
    ): Int

    fun dc_device_set_cancel(device: Pointer?, callback: CancelCallback, userdata: Pointer?): Int

    fun dc_device_foreach(device: Pointer?, callback: DiveCallback, userdata: Pointer?): Int

    fun dc_device_close(device: Pointer?): Int

    fun dc_parser_new(
        parser: PointerByReference,
        device: Pointer?,
        data: Pointer?,
        size: Long,
    ): Int

    fun dc_parser_get_datetime(parser: Pointer?, datetime: CDateTime): Int

    fun dc_parser_get_field(parser: Pointer?, type: Int, flags: Int, value: Pointer?): Int

    fun dc_parser_get_field(parser: Pointer?, type: Int, flags: Int, value: Structure?): Int

    fun dc_parser_samples_foreach(
        parser: Pointer?,
        callback: SampleCallback,
        userdata: Pointer?,
    ): Int

    fun dc_parser_destroy(parser: Pointer?): Int

    /** `dc_dive_callback_t`: one dive's bytes, and a non-zero answer to carry on. */
    fun interface DiveCallback : Callback {
        fun invoke(
            data: Pointer?,
            size: Int,
            fingerprint: Pointer?,
            fsize: Int,
            userdata: Pointer?,
        ): Int
    }

    /** `dc_sample_callback_t`: one reading, whose shape the type says. */
    fun interface SampleCallback : Callback {
        fun invoke(type: Int, value: Pointer, userdata: Pointer?)
    }

    /** `dc_cancel_callback_t`: asked between blocks, and non-zero gives the download up. */
    fun interface CancelCallback : Callback {
        fun invoke(userdata: Pointer?): Int
    }

    /** `dc_event_callback_t`: something the device said about itself, shaped as the event says. */
    fun interface EventCallback : Callback {
        fun invoke(device: Pointer?, event: Int, data: Pointer?, userdata: Pointer?)
    }

    /** A stream callback taking one number: a timeout, a count of milliseconds, a direction. */
    fun interface Numbered : Callback {
        fun invoke(userdata: Pointer?, value: Int): Int
    }

    /** A stream callback taking nothing: flush, close. */
    fun interface Plain : Callback {
        fun invoke(userdata: Pointer?): Int
    }

    /** A stream callback answering through a pointer: how much is waiting. */
    fun interface Pointed : Callback {
        fun invoke(userdata: Pointer?, value: Pointer?): Int
    }

    /** A read or a write: [size] bytes at [data], and how many were moved written to [actual]. */
    fun interface Transfer : Callback {
        fun invoke(userdata: Pointer?, data: Pointer?, size: Long, actual: Pointer?): Int
    }

    /** An ioctl: a request number, and [size] bytes at [data] that it reads or fills. */
    fun interface Request : Callback {
        fun invoke(userdata: Pointer?, request: Int, data: Pointer?, size: Long): Int
    }

    companion object {

        /**
         * What the library might be called, in the order they are tried.
         *
         * A platform decorates the name and they do not agree: a Unix maps `divecomputer` to
         * `libdivecomputer.so`, while Windows wants the file's own name and the build there
         * carries the soname in it. So each is tried rather than guessed at.
         */
        val NAMES: List<String> = listOf("libdivecomputer-0", "divecomputer-0", "divecomputer")

        /**
         * The library, or absent where it could not be loaded.
         *
         * **Absent is an answer, not a fault.** A machine without it is one where no dive computer
         * can be read, which is a thing to say to the user rather than an exception to throw at
         * them. Where it lives is not settled: JNA looks along `jna.library.path` and then the
         * platform's own search, which is enough to develop against and is not an answer for five
         * targets.
         */
        val LOADED: Libdivecomputer? by lazy {
            NAMES.firstNotNullOfOrNull {
                try {
                    Native.load(it, Libdivecomputer::class.java)
                } catch (missing: UnsatisfiedLinkError) {
                    null
                }
            }
        }

        /** What `dc_status_t` calls success. */
        const val SUCCESS: Int = 0

        /** What `dc_iterator_next` answers when there is nothing more. */
        const val DONE: Int = 1

        /** `DC_EVENT_PROGRESS`: how far the transfer has got, as two unsigned ints. */
        const val PROGRESS: Int = 1 shl 1

        /** `DC_EVENT_DEVINFO`: the model, firmware and serial, said once before the dives. */
        const val DEVINFO: Int = 1 shl 2

        /** `DC_STATUS_UNSUPPORTED`: a request this stream has no answer to. */
        const val UNSUPPORTED: Int = -1

        /** `DC_STATUS_IO`: the link failed. */
        const val IO: Int = -6

        /** `DC_STATUS_TIMEOUT`: the bytes asked for did not all arrive in time. */
        const val TIMEOUT: Int = -7

        /** `DC_STATUS_CANCELLED`: given up, by the user rather than by a fault. */
        const val CANCELLED: Int = -10
    }
}

/**
 * CustomCallbacks is `dc_custom_cbs_t`, a stream the application provides.
 *
 * The library owns serial and USB itself; anything else, Bluetooth LE among them, is opened by
 * the application and handed over as these fifteen functions, of which a stream fills the ones it
 * can and leaves the rest null. The order is the header's.
 *
 * **Held for as long as the stream is open.** A function pointer the library keeps is a Java
 * object JNA keeps only while something else does. Whoever opens a stream on these keeps them.
 */
@Structure.FieldOrder(
    "set_timeout", "set_break", "set_dtr", "set_rts", "get_lines", "get_available", "configure",
    "poll", "read", "write", "ioctl", "flush", "purge", "sleep", "close",
)
internal class CustomCallbacks : Structure() {
    @JvmField var set_timeout: Libdivecomputer.Numbered? = null
    @JvmField var set_break: Libdivecomputer.Numbered? = null
    @JvmField var set_dtr: Libdivecomputer.Numbered? = null
    @JvmField var set_rts: Libdivecomputer.Numbered? = null
    @JvmField var get_lines: Libdivecomputer.Pointed? = null
    @JvmField var get_available: Libdivecomputer.Pointed? = null

    /** Baud rate and the rest, which a serial line has and a Bluetooth link does not. */
    @JvmField var configure: Callback? = null
    @JvmField var poll: Libdivecomputer.Numbered? = null
    @JvmField var read: Libdivecomputer.Transfer? = null
    @JvmField var write: Libdivecomputer.Transfer? = null
    @JvmField var ioctl: Libdivecomputer.Request? = null
    @JvmField var flush: Libdivecomputer.Plain? = null
    @JvmField var purge: Libdivecomputer.Numbered? = null
    @JvmField var sleep: Libdivecomputer.Numbered? = null
    @JvmField var close: Libdivecomputer.Plain? = null
}

/** One model the library knows how to read, which is what a descriptor is. */
internal data class Supported(
    val vendor: String,
    val product: String,
    /** The transports it can be reached over, as the bit set `dc_transport_t` defines. */
    val transports: Int,
) {
    override fun toString(): String = "$vendor $product"
}

/**
 * Every model this build of the library can read.
 *
 * Empty where the library could not be loaded, which is the same answer as *no dive computer can
 * be read here* and is what a front end says to the user.
 */
internal fun supported(): List<Supported> {
    val library = Libdivecomputer.LOADED ?: return emptyList()
    val context = PointerByReference()
    if (library.dc_context_new(context) != Libdivecomputer.SUCCESS) return emptyList()
    try {
        val iterator = PointerByReference()
        val opened = library.dc_descriptor_iterator_new(iterator, context.value)
        if (opened != Libdivecomputer.SUCCESS) return emptyList()
        try {
            val found = ArrayList<Supported>()
            val item = PointerByReference()
            while (library.dc_iterator_next(iterator.value, item) == Libdivecomputer.SUCCESS) {
                val descriptor = item.value
                found += Supported(
                    library.dc_descriptor_get_vendor(descriptor).orEmpty(),
                    library.dc_descriptor_get_product(descriptor).orEmpty(),
                    library.dc_descriptor_get_transports(descriptor),
                )
                library.dc_descriptor_free(descriptor)
            }
            return found
        } finally {
            library.dc_iterator_free(iterator.value)
        }
    } finally {
        library.dc_context_free(context.value)
    }
}
