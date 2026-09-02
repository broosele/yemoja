package yemoja.data

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

    /** A store holding these files, at these relative paths. */
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
}

class MemoryFileStoreTest : FileStoreTest() {

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
