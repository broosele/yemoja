package yemoja.data.json

/*
 * A logbook read through a copy of its files kept where reading is cheap.
 *
 * See ../../../../../../json/doc.md — `JSON-28`.
 */

/**
 * CachedFileStore is a logbook whose unchanged files are read from a local copy.
 *
 * **The logbook stays the only truth.** A file is taken from the copy only where [stamps] says it
 * has the very stamp it had when it was copied: its date and its size, as one folder listing
 * reports them for every file at once. A file with no stamp, one not copied, or one whose stamp
 * differs is read from the logbook and copied afresh. The copy is one file in [copies], written by
 * [keep] after a logbook has been read; losing it costs one slower opening.
 *
 * Reading a folder's listing is cheap where opening each file is not: a cloud drive's files on a
 * desktop, and on a phone every file a separate request to another app.
 *
 * **What this store writes is read back from memory** for as long as it lives, since the stamp a
 * write leaves is not known until the folder is listed again, and a writer reads the file it is
 * about to replace. `JSON-26`. Everything but reading a file goes straight to [logbook].
 *
 * Not immutable: it learns as it reads.
 */
class CachedFileStore(
    private val logbook: FileStore,
    /** Every file's stamp, by its path in the logbook, or absent for one that cannot say. */
    private val stamps: () -> Map<String, String>,
    private val copies: FileStore,
) : FileStore {

    /** The stamps as last listed, taken at the first reading and again after [forget]. */
    private var listed: Map<String, String>? = null

    /** What the copy holds, by path: the stamp it was copied at and the text. */
    private val held: MutableMap<String, Copy> by lazy { readCopies() }

    /** What was written here since this store was made, which is newer than any copy. */
    private val written = HashMap<String, String>()

    /** Whether [held] has changed since it was last kept. */
    private var changed = false

    private class Copy(val stamp: String, val text: String)

    override fun isFile(path: String): Boolean = logbook.isFile(path)

    override fun isFolder(path: String): Boolean = logbook.isFolder(path)

    override fun namesIn(path: String): List<String> = logbook.namesIn(path)

    override fun readText(path: String): String {
        written[path]?.let { return it }
        if (isLibrary(path)) return logbook.readText(path)
        val stamp = (listed ?: stamps().also { listed = it })[path]
        val copy = held[path]
        if (stamp != null && copy != null && copy.stamp == stamp) return copy.text
        val text = logbook.readText(path)
        if (stamp != null) {
            held[path] = Copy(stamp, text)
            changed = true
        } else if (held.remove(path) != null) {
            changed = true
        }
        return text
    }

    /**
     * The files a reading will have to ask the logbook for, in the order of their paths: those
     * the listing stamps that the copy does not hold as they now are.
     *
     * For a store that can fetch several files at once, to do so before the reading asks for each
     * in turn. `JSON-28`.
     */
    fun wanting(): List<String> {
        val stamped = listed ?: stamps().also { listed = it }
        return stamped.filter { (path, stamp) -> path !in written && held[path]?.stamp != stamp }.keys.sorted()
    }

    // A file read as bytes is not a logbook's, so it is neither copied nor taken from the copy.
    override fun readBytes(path: String): ByteArray = logbook.readBytes(path)

    override fun writeText(path: String, text: String) {
        logbook.writeText(path, text)
        written[path] = text
        if (held.remove(path) != null) changed = true
    }

    override fun delete(path: String) {
        logbook.delete(path)
        written.remove(path)
        if (held.remove(path) != null) changed = true
    }

    /** The stamps are listed again at the next reading, and the logbook told the same. */
    override fun forget() {
        listed = null
        written.clear()
        logbook.forget()
    }

    /** Writes the copy, where it has changed. A copy that will not write is only a slower opening. */
    fun keep() {
        if (!changed) return
        val out = StringBuilder(HEADER).append('\n')
        for ((path, copy) in held) {
            out.append(path).append('\n').append(copy.stamp).append('\n')
                .append(copy.text.length).append('\n').append(copy.text).append('\n')
        }
        try {
            copies.writeText(FILE, out.toString())
            changed = false
        } catch (failed: Exception) {
            // Nothing is lost but the time a copy would have saved.
        }
    }

    /** The copy as last kept, or nothing where there is none or it will not read. */
    private fun readCopies(): MutableMap<String, Copy> {
        val found = HashMap<String, Copy>()
        val text = try {
            if (!copies.isFile(FILE)) return found
            copies.readText(FILE)
        } catch (failed: Exception) {
            return found
        }
        if (!text.startsWith(HEADER + "\n")) return found
        var at = HEADER.length + 1
        fun line(): String? {
            val end = text.indexOf('\n', at)
            if (end < 0) return null
            return text.substring(at, end).also { at = end + 1 }
        }
        while (at < text.length) {
            val path = line() ?: break
            val stamp = line() ?: break
            val length = line()?.toIntOrNull() ?: break
            if (at + length > text.length) break
            found[path] = Copy(stamp, text.substring(at, at + length))
            at += length + 1
        }
        return found
    }

    private fun isLibrary(path: String): Boolean =
        path == FileStore.LIBRARIES || path.startsWith("${FileStore.LIBRARIES}/")

    private companion object {
        /** The copy's file, one per logbook in a folder of its own. */
        const val FILE = "logbook.copy"

        /** What the copy's file begins with, and the form it is written in. */
        const val HEADER = "yemoja copy 1"
    }
}
