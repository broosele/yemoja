package yemoja.logic.divecomputer

import com.sun.jna.Pointer
import com.sun.jna.Structure

/*
 * The shapes libdivecomputer answers a field with, declared from the published headers.
 *
 * Each is the header's own struct with the header's own field order, because a declaration that
 * rearranged them would read the wrong bytes and say nothing about it.
 *
 * See ../../../../../../doc.md.
 */

/** `dc_datetime_t`, which is the device's clock as it read. */
@Structure.FieldOrder("year", "month", "day", "hour", "minute", "second", "timezone")
internal class CDateTime : Structure() {
    @JvmField var year: Int = 0
    @JvmField var month: Int = 0
    @JvmField var day: Int = 0
    @JvmField var hour: Int = 0
    @JvmField var minute: Int = 0
    @JvmField var second: Int = 0

    /** Seconds east of UTC, or [NONE] where the device reported no zone. `LOGIC-11`. */
    @JvmField var timezone: Int = 0

    companion object {
        /** `DC_TIMEZONE_NONE`, which is what a device that does not know its zone answers. */
        const val NONE: Int = Int.MIN_VALUE
    }
}

/** `dc_gasmix_t`, the fractions of one mix. */
@Structure.FieldOrder("helium", "oxygen", "nitrogen", "usage")
internal class CGasMix : Structure() {
    @JvmField var helium: Double = 0.0
    @JvmField var oxygen: Double = 0.0
    @JvmField var nitrogen: Double = 0.0
    @JvmField var usage: Int = 0
}

/** `dc_tank_t`, one cylinder's volume and pressures. Volume in litres, pressure in bar. */
@Structure.FieldOrder(
    "gasmix", "type", "volume", "workpressure", "beginpressure", "endpressure", "usage",
)
internal class CTank : Structure() {
    /** Which mix it carried, or [UNKNOWN]. */
    @JvmField var gasmix: Int = 0

    /** `DC_TANKVOLUME_NONE` means there is no volume to write. */
    @JvmField var type: Int = 0
    @JvmField var volume: Double = 0.0
    @JvmField var workpressure: Double = 0.0
    @JvmField var beginpressure: Double = 0.0
    @JvmField var endpressure: Double = 0.0
    @JvmField var usage: Int = 0

    companion object {
        const val UNKNOWN: Int = -1
        const val NO_VOLUME: Int = 0
    }
}

/** `dc_salinity_t`, which is what a computer turned pressure into depth with. `DATA-59`. */
@Structure.FieldOrder("type", "density")
internal class CSalinity : Structure() {
    /** `DC_WATER_FRESH` is 0 and `DC_WATER_SALT` is 1. */
    @JvmField var type: Int = 0
    @JvmField var density: Double = 0.0
}

/** `dc_decomodel_t`, with its one union member written out as the two it holds. */
@Structure.FieldOrder("type", "conservatism", "high", "low")
internal class CDecoModel : Structure() {
    @JvmField var type: Int = 0
    @JvmField var conservatism: Int = 0

    /** `params.gf.high`, as whole percentages. */
    @JvmField var high: Int = 0

    /** `params.gf.low`. */
    @JvmField var low: Int = 0
}

/** `dc_usbhid_desc_t`, which is what a bare USB device says about itself. */
@Structure.FieldOrder("vid", "pid")
internal class CUsbHid : Structure() {
    @JvmField var vid: Short = 0
    @JvmField var pid: Short = 0
}

/** `dc_location_t`, which is a proposal at review rather than a field. `LOGIC-18`. */
@Structure.FieldOrder("latitude", "longitude", "altitude")
internal class CLocation : Structure() {
    @JvmField var latitude: Double = 0.0
    @JvmField var longitude: Double = 0.0
    @JvmField var altitude: Double = 0.0
}

/**
 * What a sample carries, read off the union by hand.
 *
 * **The union is not declared.** `dc_sample_value_t` overlays a dozen shapes, and a JNA union has
 * to be told which one before it will read; the type is already in hand at the callback, so the
 * bytes are read at the offset that type puts them at. That is fewer moving parts than a union
 * that must be set up correctly on every call, and the offsets are the header's own.
 */
internal object Sampled {

    fun time(value: Pointer): Int = value.getInt(0)

    fun depth(value: Pointer): Double = value.getDouble(0)

    fun temperature(value: Pointer): Double = value.getDouble(0)

    fun cns(value: Pointer): Double = value.getDouble(0)

    fun gasmix(value: Pointer): Int = value.getInt(0)

    /** `{ unsigned int tank; double value; }`, the double aligned to eight. */
    fun pressureTank(value: Pointer): Int = value.getInt(0)

    fun pressureValue(value: Pointer): Double = value.getDouble(8)

    /** `{ unsigned int type; unsigned int time; double depth; unsigned int tts; }`. */
    fun decoType(value: Pointer): Int = value.getInt(0)

    fun decoTime(value: Pointer): Int = value.getInt(4)

    fun decoDepth(value: Pointer): Double = value.getDouble(8)

    /** `{ unsigned int type; unsigned int time; unsigned int flags; unsigned int value; }`. */
    fun eventType(value: Pointer): Int = value.getInt(0)

    fun eventFlags(value: Pointer): Int = value.getInt(8)
}
