package yemoja.ui.gui

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
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
    private class Entry(val id: String, val folder: Boolean)

    private val root: String = DocumentsContract.getTreeDocumentId(tree)

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
     * the whole folder read a logbook of 347 dives as one of 200. So the list is asked for again
     * while it is loading, and page after page while it holds fewer than the provider counts.
     * A folder that is still loading after [PATIENCE] is refused, a part of a logbook being worse
     * than none. `AND-5`.
     */
    private fun listedIn(folder: String): Map<String, Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
        var waited = 0L
        while (true) {
            val found = HashMap<String, Entry>()
            val first = pageOf(children, 0, found)
            if (!first.loading) {
                var total = first.total
                var offset = found.size
                // Further pages, where the provider counts more than it gave.
                while (total > found.size) {
                    val page = pageOf(children, offset, found)
                    if (page.rows == 0) break
                    offset += page.rows
                    total = maxOf(total, page.total)
                }
                return found
            }
            if (waited >= PATIENCE) {
                throw FileStoreMissing("the folder was still being listed after ${PATIENCE / 1000} s, so it was not read")
            }
            Thread.sleep(STEP)
            waited += STEP
        }
    }

    /** What one answer about a folder said: how many rows, whether more is coming, how many in all. */
    private class Page(val rows: Int, val loading: Boolean, val total: Int)

    /** The children from [offset] on, added to [into]. */
    private fun pageOf(children: Uri, offset: Int, into: MutableMap<String, Entry>): Page {
        val asked = arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE)
        val paging = if (offset == 0) null else Bundle().apply { putInt(ContentResolver.QUERY_ARG_OFFSET, offset) }
        val rows = resolver.query(children, asked, paging, null) ?: return Page(0, false, -1)
        rows.use {
            var count = 0
            while (it.moveToNext()) {
                val folder = it.getString(2) == Document.MIME_TYPE_DIR
                into[it.getString(0)] = Entry(it.getString(1), folder)
                count += 1
            }
            val extras = it.extras
            return Page(
                rows = count,
                loading = extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true,
                total = extras?.getInt(ContentResolver.EXTRA_TOTAL_COUNT, -1) ?: -1,
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

        /** How long between one asking and the next while a folder is listed. */
        const val STEP = 250L
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
