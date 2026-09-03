package yemoja.data.json

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The same questions asked of both stores, so the one held in memory cannot drift from the one on
 * disk. A store standing in for a logbook is only useful while it answers the same way.
 */
abstract class FileStoreTest {

    /** A store over these files, the libraries among them under `libraries`. */
    abstract fun storeOf(files: Map<String, String>): FileStore

    private val logbook = mapOf(
        "yemoja.json" to """{"version": 1}""",
        "dives/2026-02-23#0.json" to """{"rating": 7}""",
        "dives/2026-02-24#0.json" to """{"rating": 8}""",
        "persons.json" to """{"anna_devries": {}}""",
    )

    @Test
    fun `a file is a file and not a folder`() {
        val store = storeOf(logbook)
        assertTrue(store.isFile("yemoja.json"))
        assertFalse(store.isFolder("yemoja.json"))
    }

    @Test
    fun `a folder is a folder and not a file`() {
        val store = storeOf(logbook)
        assertTrue(store.isFolder("dives"))
        assertFalse(store.isFile("dives"))
    }

    @Test
    fun `what is not there is neither`() {
        // Asking is how a logbook is found, so it answers rather than throwing. JSON-21.
        val store = storeOf(logbook)
        assertFalse(store.isFile("wrecks.json"))
        assertFalse(store.isFolder("wrecks"))
    }

    @Test
    fun `a folder lists what is directly inside it`() {
        val store = storeOf(logbook)
        assertEquals(
            listOf("2026-02-23#0.json", "2026-02-24#0.json"),
            store.namesIn("dives").sorted(),
        )
    }

    @Test
    fun `the root lists both kinds of thing`() {
        val store = storeOf(logbook)
        assertEquals(listOf("dives", "persons.json", "yemoja.json"), store.namesIn("").sorted())
    }

    @Test
    fun `a file reads whole`() {
        val store = storeOf(logbook)
        assertEquals("""{"rating": 7}""", store.readText("dives/2026-02-23#0.json"))
    }

    @Test
    fun `asking a file for its contents, or nothing for anything, is a fault`() {
        val store = storeOf(logbook)
        assertFailsWith<FileStoreMissing> { store.namesIn("yemoja.json") }
        assertFailsWith<FileStoreMissing> { store.namesIn("wrecks") }
        assertFailsWith<FileStoreMissing> { store.readText("dives") }
        assertFailsWith<FileStoreMissing> { store.readText("wrecks.json") }
    }

    @Test
    fun `text survives being written and read back`() {
        val store = storeOf(mapOf("a.json" to "Zeelandbrug — é 🐟\nsecond line"))
        assertEquals("Zeelandbrug — é 🐟\nsecond line", store.readText("a.json"))
    }

    @Test
    fun `a library is reached by the same operations, under its own folder`() {
        val store = storeOf(mapOf("libraries/region/world.json" to """{"europe": {}}"""))
        assertTrue(store.isFile("libraries/region/world.json"))
        assertFalse(store.isFile("libraries/region/atlantis.json"))
        assertEquals("""{"europe": {}}""", store.readText("libraries/region/world.json"))
    }
}

/** Which files hold a type, and in what order they are read. [FileStore.getPaths]. */
abstract class GetPathsTest : FileStoreTest() {

    @Test
    fun `a folder gives every file in it, in a settled order`() {
        val store = storeOf(mapOf("dive/b.json" to "{}", "dive/a.json" to "{}"))
        // Sorted, so that two machines hold the same logbook the same way.
        assertEquals(listOf("dive/a.json", "dive/b.json"), store.getPaths("dive"))
    }

    @Test
    fun `a type kept in one file gives that file`() {
        val store = storeOf(mapOf("region.json" to "{}"))
        assertEquals(listOf("region.json"), store.getPaths("region"))
    }

    @Test
    fun `anything not ending in json is passed over`() {
        // A logbook is a folder someone else may also keep things in.
        val store = storeOf(mapOf("dive/a.json" to "{}", "dive/notes.txt" to "hello"))
        assertEquals(listOf("dive/a.json"), store.getPaths("dive"))
    }

    @Test
    fun `the declared libraries follow the logbook, in the order they were declared`() {
        val store = storeOf(
            mapOf(
                "region.json" to "{}",
                "libraries/region/world.json" to "{}",
                "libraries/region/europe.json" to "{}",
            ),
        )
        // The order shadowing is settled in: the first to define an id wins.
        assertEquals(
            listOf("region.json", "libraries/region/europe.json", "libraries/region/world.json"),
            store.getPaths("region", listOf("region/europe", "region/world")),
        )
    }

    @Test
    fun `a library the logbook does not declare is not read, though it is there`() {
        val store = storeOf(
            mapOf("libraries/region/world.json" to "{}", "libraries/region/europe.json" to "{}"),
        )
        assertEquals(
            listOf("libraries/region/world.json"),
            store.getPaths("region", listOf("region/world")),
        )
    }

    @Test
    fun `a declared library this installation has not got is passed over`() {
        val store = storeOf(
            mapOf("libraries/region/world.json" to "{}"),
        )
        assertEquals(
            listOf("libraries/region/world.json"),
            store.getPaths("region", listOf("region/atlantis", "region/world")),
        )
    }

    @Test
    fun `a type nothing holds gives no files at all`() {
        assertEquals(emptyList(), storeOf(mapOf("dive.json" to "{}")).getPaths("wreck"))
    }

    @Test
    fun `a type stored as a folder and as a file at once is refused`() {
        val store = storeOf(mapOf("region.json" to "{}", "region/europe.json" to "{}"))
        val refused = assertFailsWith<FileStoreAmbiguous> { store.getPaths("region") }
        assertEquals(
            "region is stored as a folder and as region.json, " +
                "and only one of them can be the region",
            refused.message,
        )
    }
}

class MemoryFileStoreTest : GetPathsTest() {

    override fun storeOf(files: Map<String, String>): FileStore = MemoryFileStore(files)

    @Test
    fun `a folder exists because something is under it`() {
        // Nothing declares a folder: one is there when a path goes through it.
        val store = MemoryFileStore(mapOf("dives/deep/a.json" to "{}"))
        assertTrue(store.isFolder("dives"))
        assertTrue(store.isFolder("dives/deep"))
        assertEquals(listOf("deep"), store.namesIn("dives"))
    }

    @Test
    fun `a store keeps its own copy of what it was given`() {
        val files = mutableMapOf("a.json" to "{}")
        val store = MemoryFileStore(files)
        files["b.json"] = "{}"
        assertFalse(store.isFile("b.json"))
    }
}
