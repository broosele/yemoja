package yemoja.logic

import yemoja.logic.divecomputer.DiveComputer
import yemoja.logic.divecomputer.Download
import yemoja.logic.divecomputer.Recording
import yemoja.logic.divecomputer.Session
import kotlin.concurrent.Volatile

/*
 * A read of a dive computer that holds no logbook, so it can run while one is edited.
 *
 * See ../../../../../../ui/gui/doc.md — `GUI-52`.
 */

/**
 * DeviceRead is one read of a dive computer, from the moment it starts until what it brought is
 * staged.
 *
 * It holds no logbook. What the device asks while it runs, where the last download stopped and
 * which access code to offer, is answered from what [Universe.readerOf] gathered before it began.
 * [run] can then take its minutes on another thread while the logbook is edited on its own, and
 * [Universe.arrive] stages what came, back on the logbook's thread. `GUI-52`.
 *
 * Not immutable: [run] fills it, and [cancelled] is set from outside while it does.
 */
class DeviceRead internal constructor(
    private val computer: DiveComputer,
    /** Where each recording the logbook holds stops, taken before the read began. */
    private val marks: List<Download.Mark>,
    /** Each gear item's serial, with the access code kept on it where there is one. */
    private val codes: List<Pair<String, String?>>,
    private val ask: (String) -> String?,
) {

    /** What the computer is called. */
    val name: String get() = computer.name

    /**
     * Whether the user has given the read up. Set from any thread.
     *
     * The device is told at its next block, and a read given up hands back nothing.
     */
    @Volatile
    var cancelled: Boolean = false

    /** Every recording the device handed over, once [run] has returned. */
    internal var recordings: List<Recording> = emptyList()
        private set

    /** The device's serial, once it has said it. */
    internal var serial: String? = null
        private set

    /** An access code the device handed over, waiting to be kept on its gear item. */
    internal var handed: ByteArray? = null
        private set

    /** Whether [handed] is on its gear item yet, which happens once however often it is staged. */
    internal var kept: Boolean = false

    /**
     * Read the device, telling [told] how far it has got.
     *
     * Takes minutes and blocks for them. Touches no logbook, so any thread may call it.
     */
    fun run(told: (done: Long, total: Long) -> Unit = { _, _ -> }) {
        val session = object : Session {
            override fun resume(serial: String?): String? {
                if (serial != null) this@DeviceRead.serial = serial
                return Download.after(marks, computer.name, serial)
            }

            override fun accessCode(name: String): ByteArray? = bytesOf(codeAdvertising(name))

            override fun pin(name: String): String? = ask("type the code $name is showing:")

            override fun keep(name: String, accessCode: ByteArray) {
                handed = accessCode
            }

            override fun progress(done: Long, total: Long): Unit = told(done, total)

            override val cancelled: Boolean get() = this@DeviceRead.cancelled
        }
        val read = computer.recordings(session).toList()
        recordings = if (cancelled) emptyList() else read
    }

    /**
     * The access code kept for the device advertising as [name], or absent where none is.
     *
     * Asked before the device has said its serial, so by the name: the gear item whose serial the
     * name, or the digits in it, spells. `LOGIC-24`.
     */
    private fun codeAdvertising(name: String): String? {
        val digits = name.filter { it.isDigit() }
        val gear = codes.firstOrNull { sameSerial(it.first, name) }
            ?: codes.firstOrNull { digits.isNotEmpty() && sameSerial(it.first, digits) }
        return gear?.second
    }
}
