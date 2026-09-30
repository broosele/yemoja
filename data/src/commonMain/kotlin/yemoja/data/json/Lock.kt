package yemoja.data.json

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import kotlin.random.Random

/*
 * A lock on a logbook, held by whoever has it open for editing.
 *
 * See ../../../../../../json/doc.md — `JSON-27`. A folder beside the logbook, because making a
 * folder is the one thing every file system does atomically: two windows racing for it get one
 * winner and one refusal, with no moment in between where both believe they hold it.
 */

/**
 * Lock is a logbook held open for editing, and the way to let it go.
 *
 * **Held until released, or until the folder is removed by hand.** Nothing here can tell a
 * window that crashed from one that is still running, so a lock left behind is left behind, and
 * the refusal says where it is so a reader can remove it. A file-system lock that dies with its
 * process would be better and is not portable.
 *
 * **Released only while it is still this one.** The holder file carries a token, and [release]
 * deletes the folder only where the token is still its own: a reader who removed a stale lock
 * while this window was alive, and a second window that then took the logbook, would otherwise
 * lose that second window's lock when the first one closed.
 *
 * Not immutable: [release] is the one change, and it is final.
 */
class Lock private constructor(private val folder: Path, private val token: String) {

    private var held: Boolean = true

    /** Lets the logbook go. Doing it twice is allowed and does nothing the second time. */
    fun release() {
        if (!held) return
        held = false
        val written = try {
            FileSystem.SYSTEM.read(folder / HOLDER) { readUtf8() }
        } catch (gone: IOException) {
            return
        }
        if (tokenIn(written) == token) FileSystem.SYSTEM.deleteRecursively(folder, mustExist = false)
    }

    companion object {

        /** What sits after a logbook's own name to name its lock. */
        const val BESIDE: String = ".lock"

        /** The file inside the lock saying what took it. */
        private const val HOLDER: String = "holder"

        /** What parts the note a reader reads from the token that says whose the lock is. */
        private const val PARTING: String = "\n"

        /**
         * Where the lock for the logbook at [path] sits: beside it, named after it.
         *
         * Worked out from the path's own name rather than by adding to its text, so `D:\log` and
         * `D:\log\` are one logbook with one lock, and the lock never lands inside the folder a
         * sync carries. `JSON-27`.
         */
        fun folderOf(path: String): String = placeOf(path).toString()

        private fun placeOf(path: String): Path {
            val logbook = path.toPath(normalize = true)
            val parent = logbook.parent ?: return "${logbook}$BESIDE".toPath()
            return parent / (logbook.name + BESIDE)
        }

        /**
         * Takes the lock for the logbook at [path], or absent where somebody already holds it.
         *
         * [note] is written inside, and is what [holderOf] gives back to whoever is refused.
         * **Absent only where the lock is already there.** Any other failure to make it — a folder
         * that may not be written, a disk that is full — is thrown, since telling a reader that
         * another window has the logbook would send them looking for one that does not exist.
         */
        fun take(path: String, note: String): Lock? {
            val folder = placeOf(path)
            try {
                FileSystem.SYSTEM.createDirectory(folder, mustCreate = true)
            } catch (failed: IOException) {
                if (FileSystem.SYSTEM.metadataOrNull(folder) != null) return null
                throw failed
            }
            val token = Random.nextLong().toString(RADIX)
            try {
                FileSystem.SYSTEM.write(folder / HOLDER) { writeUtf8(note + PARTING + token) }
            } catch (failed: IOException) {
                // A lock with nobody's name in it would hold the logbook shut for good.
                FileSystem.SYSTEM.deleteRecursively(folder, mustExist = false)
                throw failed
            }
            return Lock(folder, token)
        }

        /** What took the lock for the logbook at [path], or absent where nobody holds it. */
        fun holderOf(path: String): String? {
            val holder = placeOf(path) / HOLDER
            if (FileSystem.SYSTEM.metadataOrNull(holder)?.isRegularFile != true) return null
            return try {
                FileSystem.SYSTEM.read(holder) { readUtf8() }.substringBefore(PARTING)
            } catch (unreadable: IOException) {
                ""
            }
        }

        private fun tokenIn(written: String): String = written.substringAfter(PARTING, "")

        /** How the token is spelt, which is only ever compared with itself. */
        private const val RADIX = 36
    }
}
