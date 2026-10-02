package yemoja.ui.gui

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import yemoja.data.json.FileStore
import yemoja.data.json.FileStoreMissing

/*
 * A logbook in a folder the user granted the app, which Android reaches by content rather than
 * by path.
 *
 * See ../../../../../../gui/phone/android/doc.md — `AND-5`.
 */

/**
 * GrantedFileStore is a logbook in a folder the user picked, reached through the storage access
 * framework.
 *
 * Android gives an app no path to such a folder, only a tree it was granted, whose files are
 * found by asking for the children of each folder in turn. A folder's children are asked for once
 * and kept until something in it is written or deleted.
 *
 * **The libraries are the app's own**, read from inside it as on the desktop, and are never looked
 * for in the granted folder. Not immutable: it caches.
 */
internal class GrantedFileStore(
    private val resolver: ContentResolver,
    private val tree: Uri,
) : FileStore {

    /** Where the libraries come from, which is never the granted folder. */
    private val libraries = BundledLibraries

    /** A folder's children by name, as they were last asked for. */
    private val listed = HashMap<String, Map<String, Entry>>()

    /** One child of a folder: its document, and whether it is a folder itself. */
    private class Entry(val id: String, val folder: Boolean, val stamp: String? = null)

    private val root: String = DocumentsContract.getTreeDocumentId(tree)

    /** Each folder's path in the logbook by its document, for saying which folder would not list. */
    private val paths = HashMap<String, String>().apply { put(root, "the logbook's folder") }

    override fun isFile(path: String): Boolean =
        if (isLibrary(path)) libraries.isFile(path) else entryAt(path)?.folder == false

    override fun isFolder(path: String): Boolean = when {
        isLibrary(path) -> libraries.isFolder(path)
        path.isEmpty() -> true
        else -> entryAt(path)?.folder == true
    }

    override fun namesIn(path: String): List<String> {
        if (isLibrary(path)) return libraries.namesIn(path)
        val folder = folderId(path) ?: throw FileStoreMissing("$path should be a folder, and is not")
        return childrenOf(folder).keys.toList()
    }

    override fun readText(path: String): String {
        if (isLibrary(path)) return libraries.readText(path)
        val entry = entryAt(path)?.takeIf { !it.folder }
            ?: throw FileStoreMissing("$path should be a file, and is not")
        val stream = resolver.openInputStream(uriOf(entry.id))
            ?: throw FileStoreMissing("$path could not be opened")
        return stream.bufferedReader().use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray {
        if (isLibrary(path)) return libraries.readBytes(path)
        val entry = entryAt(path)?.takeIf { !it.folder }
            ?: throw FileStoreMissing("$path should be a file, and is not")
        val stream = resolver.openInputStream(uriOf(entry.id))
            ?: throw FileStoreMissing("$path could not be opened")
        return stream.use { it.readBytes() }
    }

    override fun writeText(path: String, text: String) {
        require(!isLibrary(path)) { "$path is a library, and libraries are not written" }
        val names = partsOf(path)
        var folder = root
        for (name in names.dropLast(1)) folder = folderIn(folder, name)
        val name = names.last()
        val id = childrenOf(folder)[name]?.id
            ?: DocumentsContract.createDocument(resolver, uriOf(folder), MIME, name)
                ?.let { DocumentsContract.getDocumentId(it) }
                ?: error("$path could not be made")
        listed.remove(folder)
        // Truncated as it is written, so a shorter text leaves nothing of the longer behind.
        val stream = resolver.openOutputStream(uriOf(id), "wt") ?: error("$path could not be written")
        stream.bufferedWriter().use { it.write(text) }
    }

    /** The folders are listed again when next asked, having perhaps changed by other hands. */
    override fun forget() {
        listed.clear()
    }

    /**
     * Every file in the folder by its path there, stamped with its date and size, from the
     * listings already asked for. A folder whose name begins with a dot is passed over. `JSON-28`.
     */
    fun stamps(): Map<String, String> {
        val found = HashMap<String, String>()
        fun walk(folder: String, prefix: String) {
            for ((name, entry) in childrenOf(folder)) {
                when {
                    entry.folder && !name.startsWith(".") -> walk(entry.id, "$prefix$name/")
                    !entry.folder -> entry.stamp?.let { found["$prefix$name"] = it }
                }
            }
        }
        walk(root, "")
        return found
    }

    override fun delete(path: String) {
        require(!isLibrary(path)) { "$path is a library, and libraries are not deleted" }
        val names = partsOf(path)
        val folder = folderId(names.dropLast(1).joinToString("/")) ?: return
        val entry = childrenOf(folder)[names.last()] ?: return
        DocumentsContract.deleteDocument(resolver, uriOf(entry.id))
        listed.remove(folder)
        listed.remove(entry.id)
    }

    private fun isLibrary(path: String): Boolean =
        path == FileStore.LIBRARIES || path.startsWith("${FileStore.LIBRARIES}/")

    private fun partsOf(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    /** The entry at [path], or absent where nothing is there. */
    private fun entryAt(path: String): Entry? {
        val names = partsOf(path)
        if (names.isEmpty()) return Entry(root, folder = true)
        var folder = root
        for (name in names.dropLast(1)) {
            val next = childrenOf(folder)[name]?.takeIf { it.folder } ?: return null
            folder = next.id
        }
        return childrenOf(folder)[names.last()]
    }

    private fun folderId(path: String): String? = entryAt(path)?.takeIf { it.folder }?.id

    /** The folder [name] in [parent], made where it is not there. */
    private fun folderIn(parent: String, name: String): String {
        childrenOf(parent)[name]?.let { if (it.folder) return it.id }
        val made = DocumentsContract.createDocument(resolver, uriOf(parent), Document.MIME_TYPE_DIR, name)
            ?: error("$name could not be made")
        listed.remove(parent)
        return DocumentsContract.getDocumentId(made)
    }

    private fun childrenOf(folder: String): Map<String, Entry> = listed.getOrPut(folder) { listedIn(folder) }

    /**
     * Every child of [folder], however the provider hands them over.
     *
     * **A cloud provider answers in parts.** Google Drive's gives the first files it has and marks
     * the answer as still loading, or gives one page of a longer list. Taking the first answer as
     * the whole folder read a logbook of 347 dives as one of 200. So while the answer is loading,
     * it is held open until the provider says the list has changed, and then asked for again; and
     * then page after page while it holds fewer than the provider counts. A folder that is still
     * loading after [PATIENCE] is refused, a part of a logbook being worse than none. `AND-5`.
     *
     * **The loading answer is held, not asked for again and again.** A provider fetches the list
     * for the answer it gave, and closing that answer may end the fetch, so asking every moment
     * could ask for ever. Each asking is at least [HOLD] after the one before.
     *
     * **A refusal says what was seen.** A phone's log is out of reach of most users, and the one
     * message they can send back is the refusal itself, so it carries how often the folder was
     * asked for, what the provider gave and counted, how often it said the list changed, and
     * whatever else it put in its answer.
     */
    private fun listedIn(folder: String): Map<String, Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        val path = paths[folder] ?: "a folder"
        val started = SystemClock.elapsedRealtime()
        val watched = Watched()
        while (true) {
            val asked = SystemClock.elapsedRealtime()
            val found = HashMap<String, Entry>()
            val first = pageOf(children, 0, found, path) { watched.changes += waitWhileLoading(it, started) }
            watched.askings += 1
            var total = first.total
            var offset = found.size
            // Further pages, where the provider counts more than it gave, loading or not, so that
            // what is said of the listing is what the provider has.
            while (total > found.size) {
                val page = pageOf(children, offset, found, path)
                if (page.rows == 0) break
                offset += page.rows
                total = maxOf(total, page.total)
            }
            watched.rows = found.size
            watched.total = total
            watched.extras = first.extras
            val elapsed = SystemClock.elapsedRealtime() - started
            if (!first.loading) {
                Log.i(TAG, "$path listed, ${found.size} entries in $elapsed ms; $watched")
                return found
            }
            if (elapsed >= PATIENCE) {
                Log.w(TAG, "$path still loading after $elapsed ms; $watched")
                throw FileStoreMissing(
                    "$path was still being listed after ${PATIENCE / 1000} s, so it was not read; $watched",
                )
            }
            val held = SystemClock.elapsedRealtime() - asked
            if (held < HOLD) Thread.sleep(HOLD - held)
        }
    }

    /**
     * Holds [loading], an answer still being filled, open until the provider says it has changed,
     * or [WAIT] has passed, or the folder's [PATIENCE] has run out. One where it said so, nought
     * where the time ran out.
     */
    private fun waitWhileLoading(loading: Cursor, started: Long): Int {
        val changed = CountDownLatch(1)
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) = changed.countDown()
        }
        loading.registerContentObserver(observer)
        try {
            val left = PATIENCE - (SystemClock.elapsedRealtime() - started)
            return if (changed.await(minOf(WAIT, maxOf(left, 0L)), TimeUnit.MILLISECONDS)) 1 else 0
        } finally {
            loading.unregisterContentObserver(observer)
        }
    }

    /** Watched is what listing one folder saw, said in a refusal so that it can be understood from the message alone. */
    private class Watched {
        var askings = 0
        var changes = 0
        var rows = 0
        var total = -1
        var extras: String? = null

        override fun toString(): String {
            val counted = if (total >= 0) "of $total counted" else "and no count"
            val said = extras?.let { ", and its answer carried $it" }.orEmpty()
            return "asked $askings times, given $rows entries $counted, told of $changes changes$said"
        }
    }

    /**
     * What one answer about a folder said: how many rows, whether more is coming, how many in all,
     * and everything else it carried, as text.
     */
    private class Page(val rows: Int, val loading: Boolean, val total: Int, val extras: String?)

    /**
     * The children of the folder at [path] from [offset] on, added to [into]. Where the answer is
     * still loading, [whileLoading] is given it before it is closed.
     */
    private fun pageOf(
        children: Uri,
        offset: Int,
        into: MutableMap<String, Entry>,
        path: String,
        whileLoading: (Cursor) -> Unit = {},
    ): Page {
        val asked = arrayOf(
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
        val paging = if (offset == 0) null else Bundle().apply { putInt(ContentResolver.QUERY_ARG_OFFSET, offset) }
        val rows = resolver.query(children, asked, paging, null) ?: return Page(0, false, -1, null)
        rows.use {
            var count = 0
            while (it.moveToNext()) {
                val folder = it.getString(2) == Document.MIME_TYPE_DIR
                // A provider that does not know a file's date or size gives no stamp, and that
                // file is always read from the folder. `JSON-28`.
                val modified = if (it.isNull(3)) 0L else it.getLong(3)
                val size = if (it.isNull(4)) -1L else it.getLong(4)
                val stamp = if (modified > 0 && size >= 0) "$modified/$size" else null
                val name = it.getString(0)
                val id = it.getString(1)
                into[name] = Entry(id, folder, stamp)
                if (folder) paths[id] = if (path.startsWith("the ")) name else "$path/$name"
                count += 1
            }
            val extras = it.extras
            val loading = extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true
            if (loading) whileLoading(it)
            return Page(
                rows = count,
                loading = loading,
                total = extras?.getInt(ContentResolver.EXTRA_TOTAL_COUNT, -1) ?: -1,
                // Read after the flags, which is what unpacks it so that it prints as its contents.
                extras = extras?.toString()?.take(EXTRAS_SAID),
            )
        }
    }

    private fun uriOf(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private companion object {
        /**
         * What a file is made as. Nothing a provider adds an extension for, which a name like
         * `dive.json` already carries and the lock's name must not be given.
         */
        const val MIME = "application/octet-stream"

        /** How long a folder may go on being listed before it is refused, in milliseconds. */
        const val PATIENCE = 120_000L

        /** The least time between one asking for a loading folder and the next, in milliseconds. */
        const val HOLD = 1_000L

        /** How much of an answer's extras a refusal repeats, which is enough to read a few flags. */
        const val EXTRAS_SAID = 400

        /**
         * How long a loading answer is held before the folder is asked for again, should the
         * provider never say it changed.
         */
        const val WAIT = 5_000L

        /** What the app's lines in the phone's log are marked with. */
        const val TAG = "Yemoja"
    }
}

/**
 * BundledLibraries is the libraries the app carries, read from inside it.
 *
 * A folder inside an Android app cannot be listed, only a file in it read, so what the libraries
 * hold is read from the index the build writes beside them. Nothing here is ever written.
 */
internal object BundledLibraries : FileStore {

    /** Every file the libraries hold, by its path from the top. */
    private val files: Set<String> by lazy {
        textOf("${FileStore.LIBRARIES}/$INDEX").lines().filter { it.isNotBlank() }.toSet()
    }

    override fun isFile(path: String): Boolean = path in files

    override fun isFolder(path: String): Boolean = files.any { it.startsWith("$path/") }

    override fun namesIn(path: String): List<String> {
        if (!isFolder(path)) throw FileStoreMissing("$path should be a folder, and is not")
        return files.filter { it.startsWith("$path/") }
            .map { it.removePrefix("$path/").substringBefore('/') }
            .distinct()
    }

    override fun readText(path: String): String {
        if (!isFile(path)) throw FileStoreMissing("$path should be a file, and is not")
        return textOf(path)
    }

    override fun writeText(path: String, text: String): Unit =
        error("$path is a library, and libraries are not written")

    override fun delete(path: String): Unit = error("$path is a library, and libraries are not deleted")

    private fun textOf(path: String): String {
        val stream = BundledLibraries::class.java.getResourceAsStream("/$path")
            ?: throw FileStoreMissing("$path is not in the app")
        return stream.bufferedReader().use { it.readText() }
    }

    /** The file the build writes, naming every other. */
    private const val INDEX = "index.txt"
}
