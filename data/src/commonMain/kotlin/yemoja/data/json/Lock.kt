package yemoja.data.json

import kotlin.random.Random

/*
 * A lock on a logbook, held by whoever has it open for editing.
 *
 * See ../../../../../../json/doc.md — `JSON-27`.
 */

/**
 * Lock is a logbook held open for editing, and the way to let it go.
 *
 * **A file inside the logbook**, [FILE], reached through the logbook's own store. A phone and a
 * desktop sharing one synced folder then see each other's lock, and a folder a phone was granted
 * holds its lock where the grant reaches. The reader asks for files by type and never reads it.
 *
 * **Held until released or removed by hand, with one exception.** Nothing here can tell a window
 * that crashed from one still running, so a lock left behind stays, and the refusal says where it
 * is. The exception is a holder that runs once on its device: Android runs one copy of an app and
 * ends it without warning, so a lock bearing that device's name is one it left, and is taken over.
 *
 * **Released only while it is still this one.** The file carries a token, and [release] deletes it
 * only where the token is still its own: a reader who removed a stale lock while this window was
 * alive, and a second window that then took the logbook, would otherwise lose that second
 * window's lock when the first one closed.
 *
 * Not immutable: [release] is the one change, and it is final.
 */
class Lock private constructor(private val store: FileStore, private val token: String) {

    private var held: Boolean = true

    /** Lets the logbook go. Doing it twice is allowed and does nothing the second time. */
    fun release() {
        if (!held) return
        held = false
        if (writtenIn(store)?.token == token) store.delete(FILE)
    }

    /** What a lock file says: what took it, on which device, and the token that says whose it is. */
    private class Written(val note: String, val device: String, val token: String)

    companion object {

        /** The lock's file, inside the logbook it locks. */
        const val FILE: String = ".yemoja.lock"

        /** What parts one line of the file from the next. */
        private const val PARTING: String = "\n"

        /**
         * Takes the lock on the logbook in [store], or absent where something else holds it.
         *
         * [note] is what [holderOf] gives back to whoever is refused. [device] names the device
         * taking it, and where [takesOver] it is matched against a lock already there: a holder
         * that runs once on its device takes over the lock that device left. A desktop window
         * does not, since two of them may be open on one machine. `JSON-27`.
         *
         * **Not atomic.** Making a folder was the test and the setting in one step, and writing a
         * file is not. The file is read back once it is written, and the lock is taken only where
         * it still names this holder, which leaves the race to the moment between looking and
         * writing. Any failure to write is thrown, since telling a reader another window has the
         * logbook would send them looking for one that does not exist.
         */
        fun take(store: FileStore, note: String, device: String = "", takesOver: Boolean = false): Lock? {
            writtenIn(store)?.let { there ->
                if (!(takesOver && device.isNotEmpty() && there.device == device)) return null
            }
            val token = Random.nextLong().toString(RADIX)
            store.writeText(FILE, listOf(note, device, token).joinToString(PARTING))
            if (writtenIn(store)?.token != token) return null
            return Lock(store, token)
        }

        /** What took the lock on the logbook in [store], or absent where nothing holds it. */
        fun holderOf(store: FileStore): String? = writtenIn(store)?.note

        private fun writtenIn(store: FileStore): Written? {
            if (!store.isFile(FILE)) return null
            val lines = try {
                store.readText(FILE).split(PARTING)
            } catch (gone: FileStoreMissing) {
                return null
            }
            return Written(lines[0], lines.getOrElse(1) { "" }, lines.getOrElse(2) { "" })
        }

        /** How the token is spelt, which is only ever compared with itself. */
        private const val RADIX = 36
    }
}
