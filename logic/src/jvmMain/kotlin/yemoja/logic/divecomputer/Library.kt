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

    fun dc_device_open(
        out: PointerByReference,
        context: Pointer?,
        descriptor: Pointer?,
        iostream: Pointer?,
    ): Int

    fun dc_device_set_fingerprint(device: Pointer?, data: ByteArray, size: Int): Int

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
    }
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
