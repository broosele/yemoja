package yemoja.data.json

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

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
 * Not immutable: [release] is the one change, and it is final.
 */
class Lock private constructor(private val folder: Path) {

    private var held: Boolean = true

    /** Lets the logbook go. Doing it twice is allowed and does nothing the second time. */
    fun release() {
        if (!held) return
        held = false
        FileSystem.SYSTEM.deleteRecursively(folder, mustExist = false)
    }

    companion object {

        /** What sits after a logbook's own path to name its lock. */
        const val BESIDE: String = ".lock"

        /** The file inside the lock saying what took it. */
        private const val HOLDER: String = "holder"

        /**
         * Takes the lock for the logbook at [path], or absent where somebody already holds it.
         *
         * [note] is written inside, and is what [holderOf] gives back to whoever is refused.
         */
        fun take(path: String, note: String): Lock? {
            val folder = "$path$BESIDE".toPath()
            try {
                FileSystem.SYSTEM.createDirectory(folder, mustCreate = true)
            } catch (taken: IOException) {
                return null
            }
            FileSystem.SYSTEM.write(folder / HOLDER) { writeUtf8(note) }
            return Lock(folder)
        }

        /** What took the lock for the logbook at [path], or absent where nobody holds it. */
        fun holderOf(path: String): String? {
            val holder = "$path$BESIDE".toPath() / HOLDER
            if (FileSystem.SYSTEM.metadataOrNull(holder)?.isRegularFile != true) return null
            return try {
                FileSystem.SYSTEM.read(holder) { readUtf8() }
            } catch (unreadable: IOException) {
                ""
            }
        }
    }
}
