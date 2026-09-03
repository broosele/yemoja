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
 * [root] is the logbook's folder and [libraryRoot] the folder the installation keeps its supplied
 * files in, each written the way the platform writes one. An installation without a library folder
 * gives none, and every library is then passed over. Paths given to this are relative and use `/`,
 * and are resolved a segment at a time so no separator is assumed.
 */
class DiskFileStore(
    root: String,
    libraryRoot: String? = null,
    override val libraries: Map<String, List<String>> = emptyMap(),
) : FileStore {

    private val root: Path = root.toPath()

    private val libraryRoot: Path? = libraryRoot?.toPath()

    private val files = FileSystem.SYSTEM

    override fun isFile(path: String): Boolean =
        files.metadataOrNull(at(path) ?: return false)?.isRegularFile == true

    override fun isFolder(path: String): Boolean =
        files.metadataOrNull(at(path) ?: return false)?.isDirectory == true

    override fun namesIn(path: String): List<String> {
        if (!isFolder(path)) throw FileStoreMissing("$path should be a folder, and is not")
        return files.list(at(path)!!).map { it.name }
    }

    override fun readText(path: String): String {
        if (!isFile(path)) throw FileStoreMissing("$path should be a file, and is not")
        return files.read(at(path)!!) { readUtf8() }
    }

    /** Where a path is on this machine, or absent where it names a library folder there is none. */
    private fun at(path: String): Path? {
        val within = FileStore.LIBRARIES
        if (path != within && !path.startsWith("$within/")) return under(root, path)
        return under(libraryRoot ?: return null, path.removePrefix(within))
    }

    private fun under(folder: Path, path: String): Path =
        path.split('/').filter { it.isNotEmpty() }.fold(folder) { under, name -> under / name }
}
