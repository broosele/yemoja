package yemoja.data

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * DiskFileStore is a [FileStore] over the machine's own files.
 *
 * The one place a file library is named. Everything else works through [FileStore], so which
 * library that is can change without anything above noticing. `DATA-86`.
 *
 * [root] is the logbook's folder, written the way the platform writes one. Paths given to it are
 * relative and use `/`, and are resolved a segment at a time so no separator is assumed.
 */
class DiskFileStore(root: String) : FileStore {

    private val root: Path = root.toPath()

    private val files = FileSystem.SYSTEM

    override fun isFile(path: String): Boolean =
        files.metadataOrNull(at(path))?.isRegularFile == true

    override fun isFolder(path: String): Boolean =
        files.metadataOrNull(at(path))?.isDirectory == true

    override fun namesIn(path: String): List<String> {
        if (!isFolder(path)) throw FileStoreMissing("$path should be a folder, and is not")
        return files.list(at(path)).map { it.name }
    }

    override fun readText(path: String): String {
        if (!isFile(path)) throw FileStoreMissing("$path should be a file, and is not")
        return files.read(at(path)) { readUtf8() }
    }

    private fun at(path: String): Path =
        path.split('/').filter { it.isNotEmpty() }.fold(root) { under, name -> under / name }
}
