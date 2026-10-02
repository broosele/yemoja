package yemoja.data.json

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * DiskFileStore is a [FileStore] over the machine's own files, and over what the application
 * ships with itself.
 *
 * The one place a file library is named. Everything else works through [FileStore], so which
 * library that is can change without anything above noticing. `DATA-86`.
 *
 * [root] is the logbook's folder, written the way the platform writes one. Paths given to this
 * are relative and use `/`, and are resolved a segment at a time so no separator is assumed.
 *
 * **Libraries come from the application's own resources**, not from a folder anybody has to find.
 * There is no reliable way to ask a JVM where it was installed, and on a phone the question has
 * no answer at all, so the shipped set travels inside the application and is read from there.
 * `LIB-6`. [libraryRoot] names a folder to read them from instead, which is what a test does and
 * what user-supplied libraries would want.
 */
class DiskFileStore(root: String, libraryRoot: String? = null) : FileStore {

    private val root: Path = root.toPath()

    private val libraryRoot: Path? = libraryRoot?.toPath()

    override fun isFile(path: String): Boolean =
        located(path).let { it.files.metadataOrNull(it.at)?.isRegularFile == true }

    override fun isFolder(path: String): Boolean =
        located(path).let { it.files.metadataOrNull(it.at)?.isDirectory == true }

    override fun namesIn(path: String): List<String> {
        if (!isFolder(path)) throw FileStoreMissing("$path should be a folder, and is not")
        return located(path).let { it.files.list(it.at) }.map { it.name }
    }

    override fun readText(path: String): String {
        if (!isFile(path)) throw FileStoreMissing("$path should be a file, and is not")
        return located(path).let { it.files.read(it.at) { readUtf8() } }
    }

    override fun readBytes(path: String): ByteArray {
        if (!isFile(path)) throw FileStoreMissing("$path should be a file, and is not")
        return located(path).let { it.files.read(it.at) { readByteArray() } }
    }

    override fun writeText(path: String, text: String) {
        refuseLibrary(path)
        val where = located(path)
        where.at.parent?.let { where.files.createDirectories(it) }
        where.files.write(where.at) { writeUtf8(text) }
    }

    override fun delete(path: String) {
        refuseLibrary(path)
        located(path).let { it.files.delete(it.at, mustExist = false) }
    }

    /**
     * Where [path] is: which set of files holds it, and where in that set.
     *
     * A library path with no folder named for it is one of the application's own resources, which
     * are addressed from the root of what is on the class path. Everything else is under [root].
     */
    private fun located(path: String): Located {
        val within = FileStore.LIBRARIES
        if (path != within && !path.startsWith("$within/")) {
            return Located(FileSystem.SYSTEM, under(root, path))
        }
        val folder = libraryRoot ?: return Located(FileSystem.RESOURCES, under(TOP, path))
        return Located(FileSystem.SYSTEM, under(folder, path.removePrefix(within)))
    }

    private fun under(folder: Path, path: String): Path =
        path.split('/').filter { it.isNotEmpty() }.fold(folder) { under, name -> under / name }

    /** A path resolved: the files that hold it, and where in them. */
    private class Located(val files: FileSystem, val at: Path)
}

/** The root of what is on the class path, which is where a resource is addressed from. */
private val TOP: Path = "/".toPath()
