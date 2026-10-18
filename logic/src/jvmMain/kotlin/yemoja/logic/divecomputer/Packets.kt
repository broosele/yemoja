package yemoja.logic.divecomputer

import com.juul.kable.Peripheral

/*
 * Packet size over Bluetooth LE on the desktop, which the platform settles on connecting.
 *
 * See ../../../../../../divecomputer.md.
 */

/** Nothing to ask: Windows and Linux agree larger packets with the device themselves. */
@Suppress("UNUSED_PARAMETER")
internal actual suspend fun largerPackets(peripheral: Peripheral) {}
