@file:OptIn(ExperimentalUuidApi::class)

package yemoja.logic.divecomputer

import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import com.juul.kable.indicate
import com.juul.kable.notify
import com.juul.kable.write
import com.juul.kable.writeWithoutResponse
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * A dive computer over Bluetooth LE, which libdivecomputer leaves to the application.
 *
 * The library knows which models speak over it and which advertised names are theirs, and
 * nothing else: no scanning, no connecting, and no idea which of a device's characteristics carry
 * the bytes. Those three are done here, through Kable, and handed over as a custom stream.
 *
 * See ../../../../../../doc.md — `LOGIC-22` is why the characteristics are found, not known.
 */

/** How long a scan listens. A device advertises every few hundred milliseconds. */
internal val LISTENING: Duration = 4.seconds

/** How long a connection is given to come up and to report its services. */
private val CONNECTING: Duration = 15.seconds

/**
 * Every dive computer advertising itself now, matched by the name it advertises.
 *
 * Empty where the machine has no Bluetooth, or it is off, or nothing answered in [LISTENING]:
 * the same answer for all three, since there is no dive computer to read in any of them.
 */
internal fun advertisingUnder(
    library: Libdivecomputer,
    context: Pointer?,
    models: List<Pointer?>,
): List<DiveComputer> {
    val heard = LinkedHashMap<String, Advertisement>()
    try {
        runBlocking {
            withTimeoutOrNull(LISTENING) {
                Scanner().advertisements.collect { advertisement ->
                    if (advertisement.name == null) return@collect
                    heard.putIfAbsent(advertisement.identifier.toString(), advertisement)
                }
            }
        }
    } catch (unreachable: Exception) {
        return emptyList()
    }
    val found = ArrayList<DiveComputer>()
    for (advertisement in heard.values) {
        val name = advertisement.name ?: continue
        val accepted = models.filter { library.dc_descriptor_filter(it, Transport.BLE, name) != 0 }
        if (accepted.isEmpty()) continue
        val products = accepted.map { library.dc_descriptor_get_product(it).orEmpty() }
        val codes = accepted.map { library.dc_descriptor_get_model(it) }
        val at = preferred(name, products, codes)
        val vendor = library.dc_descriptor_get_vendor(accepted[at])
        val called = listOfNotNull(vendor, products[at]).joinToString(" ")
        val model = accepted[at]
        found += Attached(library, context, model, "$called over Bluetooth") { session ->
            Connection.open(advertisement)?.let { Custom(it, session).opened(library, context) }
        }
    }
    return found
}

/**
 * Which of the models that accepted [advertised] to call it, as an index into [products].
 *
 * A filter accepts a whole family — every Shearwater accepts every Shearwater name, every
 * Oceanic every Oceanic one — and the first member of a family is not the one in the room. So
 * the family's own way of naming itself decides, in two shapes and a fallback:
 *
 * By **name**, where the advertisement holds a product's, longest first: a Perdix 2 advertises as
 * *Perdix 2*, which holds *Perdix* too.
 *
 * By **code**, where it is two letters and then digits: those letters are the model number's own
 * two bytes, which is how the library tells one Oceanic from another and the only thing that
 * does. `GD312445` is model `0x4744`, and nothing else.
 *
 * Otherwise the first, which is a guess and is why the name a scan gives is not what a profile
 * records. `LOGIC-23`.
 */
internal fun preferred(advertised: String, products: List<String>, codes: List<Int>): Int {
    codes.indexOfFirst { spells(advertised, it) }.takeIf { it >= 0 }?.let { return it }
    var best = 0
    var length = 0
    for ((at, product) in products.withIndex()) {
        if (product.length > length && advertised.contains(product, ignoreCase = true)) {
            best = at
            length = product.length
        }
    }
    return best
}

/** Whether [advertised] is [code]'s two bytes as letters and then digits, and nothing else. */
private fun spells(advertised: String, code: Int): Boolean {
    val letters = charArrayOf(((code shr 8) and 0xff).toChar(), (code and 0xff).toChar())
    if (!letters.all { it.isLetter() }) return false
    val rest = advertised.drop(letters.size)
    return advertised.length > letters.size &&
        advertised.take(letters.size).equals(String(letters), ignoreCase = true) &&
        rest.all { it.isDigit() }
}

/** One characteristic a connected device offers, with the two abilities that matter here. */
internal data class Offered(
    val service: Uuid,
    val characteristic: Uuid,
    val writable: Boolean,
    val notifying: Boolean,
) {
    /**
     * Whether the service is one the Bluetooth SIG defined, as opposed to the vendor's own.
     *
     * Those are the sixteen-bit ones from `0x1800`, laid into the base UUID: generic access,
     * battery, device information and the rest. A vendor's own service is a full UUID, or a
     * sixteen-bit one the SIG assigned to that vendor, which sits well above the range.
     */
    val assigned: Boolean
        get() = ASSIGNED.matches(service.toString())

    companion object {
        /** A SIG-defined service, in the base UUID. */
        val ASSIGNED: Regex = Regex("000018[0-9a-f]{2}-0000-1000-8000-00805f9b34fb")
    }
}

/** The characteristic written to and the one listened to, within one service. */
internal data class Chosen(val service: Uuid, val writing: Uuid, val listening: Uuid)

/**
 * Which of what a device offers carries the bytes. `LOGIC-22`.
 *
 * The vendor's own service that has something writable and something that notifies. A single
 * characteristic doing both is preferred, then a service where two do it between them. Assigned
 * services — battery, device information, generic access — are never it, and are skipped.
 */
internal fun chosenAmong(offered: List<Offered>): Chosen? {
    val own = offered.filterNot { it.assigned }
    own.firstOrNull { it.writable && it.notifying }?.let {
        return Chosen(it.service, it.characteristic, it.characteristic)
    }
    for ((service, members) in own.groupBy { it.service }) {
        val writing = members.firstOrNull { it.writable } ?: continue
        val listening = members.firstOrNull { it.notifying } ?: continue
        return Chosen(service, writing.characteristic, listening.characteristic)
    }
    return null
}

/**
 * Wire is what a custom stream needs of a link, and no more.
 *
 * Kept apart from the connection so the stream can be exercised without one.
 */
internal interface Wire {

    /** What the device advertised itself as. */
    val name: String

    /**
     * One packet, written whole.
     *
     * **Never split.** A write is a packet to the device on the other end, and two writes are
     * two packets: a driver that sends twenty-one bytes and gets twenty and then one is
     * talking nonsense. Where the link cannot carry it, that is a failure to report rather
     * than something to work around.
     */
    fun send(bytes: ByteArray)

    /** The next notification, or absent when none arrived within [milliseconds]. */
    fun received(milliseconds: Long): ByteArray?

    /** A characteristic read directly, by its UUID, or absent where the device has none. */
    fun readOther(characteristic: Uuid): ByteArray?

    fun close()
}

/**
 * Connection is one device, connected and listened to.
 *
 * Notifications are queued as they arrive, so that a read the library asks for later still
 * sees bytes that came before it asked. The listening starts before the first write, and the
 * open waits for that, because a device answers at once and an answer before anyone is listening
 * is gone.
 */
internal class Connection private constructor(
    private val peripheral: Peripheral,
    private val chosen: Chosen,
    private val writeType: WriteType,
    override val name: String,
) : Wire {

    private val arrived = LinkedBlockingQueue<ByteArray>()

    private val writing: Characteristic =
        characteristicOf(chosen.service, chosen.writing)

    /** Start listening, and answer once the device has been told to notify. */
    private fun listening(): CompletableDeferred<Unit> {
        val ready = CompletableDeferred<Unit>()
        val heard = characteristicOf(chosen.service, chosen.listening)
        peripheral.scope.launch {
            try {
                peripheral.observe(heard) { ready.complete(Unit) }.collect { arrived.put(it) }
            } catch (ended: Exception) {
                ready.complete(Unit)
            }
        }
        return ready
    }

    override fun send(bytes: ByteArray) {
        runBlocking { peripheral.write(writing, bytes, writeType) }
    }

    override fun received(milliseconds: Long): ByteArray? =
        if (milliseconds < 0) arrived.take() else arrived.poll(milliseconds, TimeUnit.MILLISECONDS)

    override fun readOther(characteristic: Uuid): ByteArray? {
        val services = peripheral.services.value ?: return null
        val held = services.flatMap { it.characteristics }
            .firstOrNull { it.characteristicUuid == characteristic } ?: return null
        return try {
            runBlocking { peripheral.read(held) }
        } catch (failed: Exception) {
            null
        }
    }

    override fun close() {
        try {
            runBlocking { withTimeoutOrNull(CONNECTING) { peripheral.disconnect() } }
        } finally {
            peripheral.close()
        }
    }

    companion object {

        /**
         * Connect to what [advertisement] came from, or absent where it would not connect, said
         * nothing in time, or offers nothing this can drive.
         */
        fun open(advertisement: Advertisement): Connection? {
            val peripheral = Peripheral(advertisement)
            return try {
                runBlocking {
                    withTimeoutOrNull(CONNECTING) {
                        peripheral.connect()
                        val services = peripheral.services.first { it != null }.orEmpty()
                        val offered = services.flatMap { service ->
                            service.characteristics.map {
                                Offered(
                                    service = service.serviceUuid,
                                    characteristic = it.characteristicUuid,
                                    writable = it.properties.write ||
                                        it.properties.writeWithoutResponse,
                                    notifying = it.properties.notify || it.properties.indicate,
                                )
                            }
                        }
                        val chosen = chosenAmong(offered) ?: return@withTimeoutOrNull null
                        val quick = offered.first { it.characteristic == chosen.writing }
                        val writeType = if (services.flatMap { it.characteristics }
                                .first { it.characteristicUuid == quick.characteristic }
                                .properties.writeWithoutResponse
                        ) {
                            WriteType.WithoutResponse
                        } else {
                            WriteType.WithResponse
                        }
                        val connection = Connection(
                            peripheral,
                            chosen,
                            writeType,
                            advertisement.name.orEmpty(),
                        )
                        connection.listening().await()
                        connection
                    }
                }.also { if (it == null) peripheral.close() }
            } catch (failed: Exception) {
                peripheral.close()
                null
            }
        }
    }
}

/**
 * Custom is a libdivecomputer stream over a [Wire].
 *
 * The library reads with a timeout it sets, and a read answers one packet: what has arrived, up
 * to the room offered, and a timeout only when nothing came. Bytes past the room wait for the
 * next read. An ioctl answers the advertised name and a characteristic read, which are what the
 * drivers that speak Bluetooth ask, and the three requests of a device that guards itself: the
 * code it is showing, typed by the user, and an access code kept from last time and handed over
 * for next time. Those three go to the [session], which is the logbook's to answer. `LOGIC-24`.
 */
internal class Custom(private val wire: Wire, private val session: Session = Session.NONE) {

    /** Bytes received and not yet handed over. */
    private val pending = ArrayList<Byte>()

    private var timeout: Int = DEFAULT_TIMEOUT

    // Kept here for as long as the library holds their addresses.
    private val callbacks = CustomCallbacks()

    init {
        callbacks.set_timeout = Libdivecomputer.Numbered { _, value -> timeout = value; SUCCESS }
        callbacks.get_available = Libdivecomputer.Pointed { _, value ->
            value?.setLong(0, pending.size.toLong())
            SUCCESS
        }
        callbacks.poll = Libdivecomputer.Numbered { _, milliseconds ->
            if (gathered(1, milliseconds)) SUCCESS else Libdivecomputer.TIMEOUT
        }
        callbacks.read = Libdivecomputer.Transfer { _, data, size, actual ->
            read(data, size, actual)
        }
        callbacks.write = Libdivecomputer.Transfer { _, data, size, actual ->
            write(data, size, actual)
        }
        callbacks.ioctl = Libdivecomputer.Request { _, request, data, size ->
            ioctl(request, data, size)
        }
        callbacks.flush = Libdivecomputer.Plain { SUCCESS }
        callbacks.purge = Libdivecomputer.Numbered { _, direction ->
            if (direction and INPUT != 0) {
                pending.clear()
                while (wire.received(0) != null) Unit
            }
            SUCCESS
        }
        callbacks.sleep = Libdivecomputer.Numbered { _, milliseconds ->
            Thread.sleep(milliseconds.toLong())
            SUCCESS
        }
        callbacks.close = Libdivecomputer.Plain {
            wire.close()
            LIVE.remove(this)
            SUCCESS
        }
        callbacks.write()
    }

    /** The stream as the library holds it, or absent where the library would not open one. */
    fun opened(library: Libdivecomputer, context: Pointer?): Pointer? {
        val stream = PointerByReference()
        val status = library.dc_custom_open(stream, context, Transport.BLE, callbacks, null)
        if (status != SUCCESS) {
            wire.close()
            return null
        }
        LIVE += this
        return stream.value
    }

    /**
     * Wait until [wanted] bytes are pending, or [milliseconds] have passed; negative waits
     * without limit and zero not at all. Answers whether there are that many.
     */
    private fun gathered(wanted: Int, milliseconds: Int): Boolean {
        val until = System.currentTimeMillis() + milliseconds
        while (pending.size < wanted) {
            val left = when {
                milliseconds < 0 -> -1L
                milliseconds == 0 -> 0L
                else -> (until - System.currentTimeMillis()).coerceAtLeast(0)
            }
            if (milliseconds > 0 && left == 0L) break
            val more = wire.received(left) ?: if (milliseconds == 0) break else continue
            pending.addAll(more.asList())
        }
        return pending.size >= wanted
    }

    /**
     * One packet, or what is left of the last one: whatever has arrived, up to [size] bytes.
     *
     * Over Bluetooth LE the library reads packets, not counts. A driver offers a buffer larger
     * than any packet and takes what one read gives; a read that waited to fill the buffer
     * would wait past the answer and call it a timeout, which is what this did first.
     */
    internal fun read(data: Pointer?, size: Long, actual: Pointer?): Int {
        val room = size.toInt()
        val came = gathered(1, timeout)
        val given = minOf(room, pending.size)
        if (given > 0) {
            data?.write(0, pending.subList(0, given).toByteArray(), 0, given)
            pending.subList(0, given).clear()
        }
        actual?.setLong(0, given.toLong())
        return if (came) SUCCESS else Libdivecomputer.TIMEOUT
    }

    internal fun write(data: Pointer?, size: Long, actual: Pointer?): Int {
        val bytes = data?.getByteArray(0, size.toInt()) ?: ByteArray(0)
        try {
            if (bytes.isNotEmpty()) wire.send(bytes)
        } catch (failed: Exception) {
            actual?.setLong(0, 0)
            return Libdivecomputer.IO
        }
        actual?.setLong(0, bytes.size.toLong())
        return SUCCESS
    }

    internal fun ioctl(request: Int, data: Pointer?, size: Long): Int = when (request) {
        GET_NAME -> {
            val room = size.toInt()
            if (data == null || room <= 0) {
                Libdivecomputer.UNSUPPORTED
            } else {
                val bytes = wire.name.toByteArray()
                val kept = minOf(bytes.size, room - 1)
                data.write(0, bytes, 0, kept)
                data.setByte(kept.toLong(), 0)
                SUCCESS
            }
        }
        GET_PINCODE -> {
            val room = size.toInt()
            val typed = session.pin(wire.name)
            when {
                data == null || room <= 0 -> Libdivecomputer.UNSUPPORTED
                // Nobody typed one: the download is given up, not failed.
                typed == null -> Libdivecomputer.CANCELLED
                else -> {
                    val bytes = typed.toByteArray()
                    val kept = minOf(bytes.size, room - 1)
                    data.write(0, bytes, 0, kept)
                    data.setByte(kept.toLong(), 0)
                    SUCCESS
                }
            }
        }
        GET_ACCESSCODE -> {
            val kept = session.accessCode(wire.name)
            // None kept is unsupported, which is what makes the driver ask for the code instead.
            if (data == null || kept == null) {
                Libdivecomputer.UNSUPPORTED
            } else {
                data.write(0, kept, 0, minOf(kept.size, size.toInt()))
                SUCCESS
            }
        }
        SET_ACCESSCODE -> {
            if (data != null && size > 0) {
                session.keep(wire.name, data.getByteArray(0, size.toInt()))
            }
            SUCCESS
        }
        CHARACTERISTIC_READ -> {
            val room = size.toInt() - UUID_SIZE
            if (data == null || room < 0) {
                Libdivecomputer.UNSUPPORTED
            } else {
                val uuid = Uuid.fromByteArray(data.getByteArray(0, UUID_SIZE))
                val answer = wire.readOther(uuid)
                if (answer == null) {
                    Libdivecomputer.IO
                } else {
                    val kept = minOf(answer.size, room)
                    data.write(UUID_SIZE.toLong(), answer, 0, kept)
                    SUCCESS
                }
            }
        }
        else -> Libdivecomputer.UNSUPPORTED
    }

    companion object {

        private const val SUCCESS: Int = Libdivecomputer.SUCCESS

        /** What a read waits before the library has said, in milliseconds. */
        private const val DEFAULT_TIMEOUT: Int = 5000

        /** `DC_DIRECTION_INPUT`, the bit of a purge that means what was received. */
        private const val INPUT: Int = 1

        /** `DC_IOCTL_BLE_GET_NAME`: direction read, type `b`, number 0, variable size. */
        const val GET_NAME: Int = (1 shl 30) or ('b'.code shl 8) or 0

        /** `DC_IOCTL_BLE_GET_PINCODE`: the code the device is showing, ended by a zero byte. */
        const val GET_PINCODE: Int = (1 shl 30) or ('b'.code shl 8) or 1

        /** `DC_IOCTL_BLE_GET_ACCESSCODE`: the bytes kept from last time, out. */
        const val GET_ACCESSCODE: Int = (1 shl 30) or ('b'.code shl 8) or 2

        /** `DC_IOCTL_BLE_SET_ACCESSCODE`: the bytes to keep for next time, in. */
        const val SET_ACCESSCODE: Int = (2 shl 30) or ('b'.code shl 8) or 2

        /** `DC_IOCTL_BLE_CHARACTERISTIC_READ`: a UUID in, its value out after it. */
        const val CHARACTERISTIC_READ: Int = (1 shl 30) or ('b'.code shl 8) or 3

        /** `dc_ble_uuid_t`, sixteen bytes. */
        const val UUID_SIZE: Int = 16

        /** Every open stream, so the callbacks the library holds addresses of stay reachable. */
        private val LIVE = HashSet<Custom>()
    }
}
