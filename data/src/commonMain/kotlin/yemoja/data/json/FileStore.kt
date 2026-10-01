package yemoja.data.json

/**
 * FileStoreMissing is thrown when a path is asked for something it cannot answer.
 *
 * A fault rather than a fact. Whether a logbook has a `dive` folder is asked with
 * [FileStore.isFolder] and answered without an exception. Reading a file that is not there means
 * the code asked without looking, and every platform would otherwise throw something different.
 */
class FileStoreMissing(message: String) : RuntimeException(message)

/** FileStoreAmbiguous is thrown when a type is stored as a folder and as a file at once. */
class FileStoreAmbiguous(message: String) : RuntimeException(message)

/**
 * Refuses [path] where it names a library, which nothing writes.
 *
 * A library belongs to the installation and differs on every device the logbook is opened on, so
 * writing one would put an installation's data inside somebody's logbook. A fault in the code
 * rather than a fact about the logbook, so it is refused the way a bad id is.
 */
internal fun refuseLibrary(path: String) {
    val within = FileStore.LIBRARIES
    require(path != within && !path.startsWith("$within/")) {
        "$path is a library, and a library is not the logbook's to write"
    }
}

/**
 * FileStore is where a logbook's files and the installation's libraries are, seen through the few
 * operations reading them needs.
 *
 * Built from two folders: the logbook, and the library directory the application keeps its supplied
 * files in. They are apart because a logbook contains itself and may be copied, while libraries
 * belong to the installation and differ on every device the logbook is opened on.
 *
 * **One namespace over the two.** Every path is relative and written with `/` whatever the platform
 * uses, so nothing above this handles a separator, a drive letter or a home directory. A path under
 * [LIBRARIES] resolves against the library directory and any other against the logbook, which is
 * how a path from [getPaths] can be handed straight back to [readText]. It cannot collide with a
 * logbook file: a type is stored under its own name, and no type is called `libraries`.
 *
 * The files are small enough to read whole, and small enough to write whole: a file is replaced
 * rather than amended, since the smallest thing a logbook holds is an item and an item is a file
 * or a member of one.
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

    /**
     * Puts [text] at [path], making whatever folders it needs and replacing whatever was there.
     *
     * **No temporary file and no rename.** A change interrupted part way through can leave one
     * file written and the next not, which is a decision deferred rather than an oversight: see
     * `JSON-25`. The one thing refused here is a library, which belongs to the installation and
     * not to the logbook.
     */
    fun writeText(path: String, text: String)

    /** Removes the file at [path]. Does nothing where there is none, since the end is the same. */
    fun delete(path: String)

    /**
     * Every file holding items of [type], in the order they are to be read.
     *
     * The logbook's own come first — each file in its `type` folder, or the single `type.json`,
     * whichever is there — and then the [libraries] named, in the order they are named. That is
     * the order shadowing is settled in: the first to define an id wins, so a logbook item is met
     * before a supplied one, and an earlier library before a later. See `data/libraries.md`.
     *
     * The names are given rather than found here, because they are declared in `yemoja.json` and
     * reading that is the reader's job. A library sitting in the directory that the logbook does
     * not name is left alone, and a named one the installation has not got is passed over.
     *
     * A type the logbook holds neither way contributes nothing from it, which is how a logbook
     * with no operators in it reads.
     *
     * Throws [FileStoreAmbiguous] where the logbook holds the type both ways at once.
     *
     * For example, `getPaths("dive")` gives every file in the `dive` folder, and
     * `getPaths("region", listOf("region/world"))` gives `region.json` followed by
     * `libraries/region/world.json`.
     */
    fun getPaths(type: String, libraries: List<String> = emptyList()): List<String> {
        val file = "$type.json"
        val asFolder = isFolder(type)
        val asFile = isFile(file)
        if (asFolder && asFile) {
            throw FileStoreAmbiguous(
                "$type is stored as a folder and as $file, and only one of them can be the $type",
            )
        }
        val paths = ArrayList<String>()
        if (asFolder) {
            // Sorted, because what a folder lists first is the platform's business and two machines
            // reading the same logbook should hold it the same way. Anything not ending in `.json`
            // is passed over: a logbook is a folder someone else may also keep things in.
            namesIn(type).filter { it.endsWith(".json") }.sorted().mapTo(paths) { "$type/$it" }
        } else if (asFile) {
            paths.add(file)
        }
        for (name in libraries) {
            val at = "$LIBRARIES/$name.json"
            if (isFile(at)) paths.add(at)
        }
        return paths
    }

    companion object {

        /** The folder a path sits under to mean the library directory rather than the logbook. */
        const val LIBRARIES: String = "libraries"
    }
}

/**
 * MemoryFileStore is a store held in memory, for tests and for anything assembled without a disk.
 *
 * Built from the files alone: a folder exists where something is under it, so `dive/a.json` makes
 * `dive` a folder without its being declared. That is how a real one behaves, and it keeps the
 * fixture to one map with the libraries in it under [FileStore.LIBRARIES].
 */
class MemoryFileStore(files: Map<String, String>) : FileStore {

    // Copied, and its own from then on: what it was built from is not changed by writing here.
    private val files: MutableMap<String, String> = LinkedHashMap(files)

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

    // A folder is wherever something is under it, so writing a file makes its folders as it goes
    // and there is nothing else to do.
    override fun writeText(path: String, text: String) {
        refuseLibrary(path)
        files[path] = text
    }

    override fun delete(path: String) {
        refuseLibrary(path)
        files.remove(path)
    }
}
