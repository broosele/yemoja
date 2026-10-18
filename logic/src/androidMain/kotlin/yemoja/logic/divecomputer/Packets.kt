package yemoja.logic.divecomputer

import com.juul.kable.AndroidPeripheral
import com.juul.kable.Peripheral

/*
 * Packet size over Bluetooth LE on Android, which an application has to ask for.
 *
 * See ../../../../../../divecomputer.md.
 */

/** Asks for the largest packets Android allows, and says what the device agreed to. */
internal actual suspend fun largerPackets(peripheral: Peripheral) {
    val android = peripheral as? AndroidPeripheral ?: return
    try {
        val agreed = android.requestMtu(LARGEST)
        System.err.println("bluetooth: packets of $agreed bytes agreed")
    } catch (refused: Exception) {
        // A device that will not change keeps the smallest, which is what it had.
        System.err.println("bluetooth: larger packets refused: $refused")
    }
}

/** The largest packet an Android application may ask for, in bytes. */
private const val LARGEST = 517
