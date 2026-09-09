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
        val model = models.firstOrNull {
            library.dc_descriptor_filter(it, Transport.BLE, name) != 0
        } ?: continue
        val vendor = library.dc_descriptor_get_vendor(model)
        val product = library.dc_descriptor_get_product(model)
        val called = listOfNotNull(vendor, product).joinToString(" ")
        found += Attached(library, context, model, "$called over Bluetooth") {
            Connection.open(advertisement)?.let { Custom(it).opened(library, context) }
        }
    }
    return found
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

    /** The most bytes one [send] may carry. */
    val chunk: Int

    /** One write of at most [chunk] bytes. */
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
    override val chunk: Int,
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

        /** What a write may carry when the device does not say. */
        private const val SMALLEST_CHUNK: Int = 20

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
                        val chunk = try {
                            peripheral.maximumWriteValueLengthForType(writeType)
                        } catch (unknown: Exception) {
                            SMALLEST_CHUNK
                        }
                        val connection = Connection(
                            peripheral,
                            chosen,
                            writeType,
                            advertisement.name.orEmpty(),
                            chunk.coerceAtLeast(1),
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
 * The library reads with a timeout it sets, and a read either fills what was asked for or says
 * it timed out, with what did arrive; bytes past what was asked for wait for the next read. An
 * ioctl answers the advertised name and a characteristic read, which are what the drivers that
 * speak Bluetooth ask; a PIN or access code is unsupported until a device is met that wants one.
 */
internal class Custom(private val wire: Wire) {

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

    internal fun read(data: Pointer?, size: Long, actual: Pointer?): Int {
        val wanted = size.toInt()
        val full = gathered(wanted, timeout)
        val given = minOf(wanted, pending.size)
        if (given > 0) {
            data?.write(0, pending.subList(0, given).toByteArray(), 0, given)
            pending.subList(0, given).clear()
        }
        actual?.setLong(0, given.toLong())
        return if (full) SUCCESS else Libdivecomputer.TIMEOUT
    }

    internal fun write(data: Pointer?, size: Long, actual: Pointer?): Int {
        val bytes = data?.getByteArray(0, size.toInt()) ?: ByteArray(0)
        var sent = 0
        try {
            while (sent < bytes.size) {
                val end = minOf(bytes.size, sent + wire.chunk)
                wire.send(bytes.copyOfRange(sent, end))
                sent = end
            }
        } catch (failed: Exception) {
            actual?.setLong(0, sent.toLong())
            return Libdivecomputer.IO
        }
        actual?.setLong(0, sent.toLong())
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

        /** `DC_IOCTL_BLE_CHARACTERISTIC_READ`: a UUID in, its value out after it. */
        const val CHARACTERISTIC_READ: Int = (1 shl 30) or ('b'.code shl 8) or 3

        /** `dc_ble_uuid_t`, sixteen bytes. */
        const val UUID_SIZE: Int = 16

        /** Every open stream, so the callbacks the library holds addresses of stay reachable. */
        private val LIVE = HashSet<Custom>()
    }
}
