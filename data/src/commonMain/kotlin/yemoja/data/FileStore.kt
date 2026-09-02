package yemoja.data

/**
 * FileStore is somewhere a logbook's files are, seen through the few operations reading one needs.
 *
 * Rooted: every path is relative to the folder the store stands for, written with `/` whatever the
 * platform uses. So nothing above this handles a separator, a drive letter or a home directory.
 *
 * **Four operations, because reading a logbook needs four.** `JSON-21` has to tell a `dives` folder
 * from a `dives.json` file and list whichever it finds, and the files are small enough to read
 * whole. Writing will add to this when there is a writer; guessing now at what it needs would be
 * inventing an interface against no implementation.
 *
 * It exists so that the layer holds no library. Common Kotlin has no files at all, so something
 * outside has to supply them, and one small interface keeps that to a single implementation which
 * can be swapped without anything above noticing. `DATA-86`.
 */
interface FileStore {

    /** Whether something is there and is a file. False where nothing is there at all. */
    fun isFile(path: String): Boolean

    /** Whether something is there and is a folder. False where nothing is there at all. */
    fun isFolder(path: String): Boolean

    /**
     * The names directly inside [path], in no particular order and without their folder.
     *
     * Empty where the folder is empty. Throws where [path] is not a folder, because asking for the
     * contents of a file is a mistake in the code rather than a fact about the logbook.
     */
    fun namesIn(path: String): List<String>

    /** The whole of [path] as text. Throws where it is not a file. */
    fun readText(path: String): String
}

/**
 * FileStoreMissing is thrown when a path is asked for something it cannot answer.
 *
 * A fault rather than a fact. Whether a logbook has a `dives` folder is asked with
 * [FileStore.isFolder] and answered without an exception. Reading a file that is not there means
 * the code asked without looking, and every platform would otherwise throw something different.
 */
class FileStoreMissing(message: String) : RuntimeException(message)

/**
 * MemoryFileStore is a store held in memory, for tests and for anything assembled without a disk.
 *
 * Built from the files alone: a folder exists where something is under it, so `dives/a.json` makes
 * `dives` a folder without its being declared. That is how a real one behaves and it keeps the
 * fixture to one map.
 */
class MemoryFileStore(files: Map<String, String>) : FileStore {

    // Copied. A Map is read-only, not immutable.
    private val files: Map<String, String> = files.toMap()

    override fun isFile(path: String): Boolean = path in files

    // The root is a folder whether or not anything is in it: it is what the store stands for.
    override fun isFolder(path: String): Boolean =
        path.isEmpty() || files.keys.any { it.startsWith("$path/") }

    override fun namesIn(path: String): List<String> {
        if (!isFolder(path)) throw FileStoreMissing("$path should be a folder, and is not")
        val within = if (path.isEmpty()) "" else "$path/"
        return files.keys
            .filter { it.startsWith(within) }
            .map { it.substring(within.length).substringBefore('/') }
            .distinct()
    }

    override fun readText(path: String): String =
        files[path] ?: throw FileStoreMissing("$path should be a file, and is not")
}
