package yemoja.data.json

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * A logbook read through a local copy of its unchanged files. See ../../../../../../json/doc.md —
 * `JSON-28`.
 */

/** A store over [files] that counts what was read from it. */
private class Counting(files: Map<String, String>) : FileStore {
    val inner = MemoryFileStore(files)
    val reads = ArrayList<String>()
    var forgotten = 0
    override fun isFile(path: String): Boolean = inner.isFile(path)
    override fun isFolder(path: String): Boolean = inner.isFolder(path)
    override fun namesIn(path: String): List<String> = inner.namesIn(path)
    override fun readText(path: String): String = inner.readText(path).also { reads += path }
    override fun writeText(path: String, text: String) = inner.writeText(path, text)
    override fun delete(path: String) = inner.delete(path)
    override fun forget() {
        forgotten += 1
    }
}

class CachedFileStoreTest {

    private val logbook = Counting(mapOf("gear.json" to """{"a": {}}""", "person.json" to "{}"))
    private val copies = MemoryFileStore(emptyMap())
    private var stamps = mapOf("gear.json" to "1/9", "person.json" to "1/2")

    private fun store() = CachedFileStore(logbook, { stamps }, copies)

    @Test
    fun `a file whose stamp is unchanged is read from the copy, not the logbook`() {
        store().apply {
            readText("gear.json")
            readText("person.json")
            keep()
        }
        logbook.reads.clear()
        val again = store()
        assertEquals("""{"a": {}}""", again.readText("gear.json"))
        assertEquals("{}", again.readText("person.json"))
        assertEquals(emptyList(), logbook.reads, "nothing was asked of the logbook")
    }

    @Test
    fun `a file whose stamp changed is read from the logbook, and copied again`() {
        store().apply { readText("gear.json"); keep() }
        logbook.inner.writeText("gear.json", """{"b": {}}""")
        stamps = stamps + ("gear.json" to "2/9")
        logbook.reads.clear()
        val again = store()
        assertEquals("""{"b": {}}""", again.readText("gear.json"))
        assertEquals(listOf("gear.json"), logbook.reads)
        again.keep()
        logbook.reads.clear()
        assertEquals("""{"b": {}}""", store().readText("gear.json"))
        assertEquals(emptyList(), logbook.reads, "the fresh copy serves the next time")
    }

    @Test
    fun `a file with no stamp is always read from the logbook`() {
        stamps = emptyMap()
        store().apply { readText("gear.json"); keep() }
        logbook.reads.clear()
        store().readText("gear.json")
        assertEquals(listOf("gear.json"), logbook.reads)
    }

    @Test
    fun `what is written here is read back as written, before any listing knows of it`() {
        val store = store()
        store.readText("gear.json")
        store.writeText("gear.json", """{"c": {}}""")
        assertEquals("""{"c": {}}""", store.readText("gear.json"), "not the copy of what was there")
        assertEquals("""{"c": {}}""", logbook.inner.readText("gear.json"), "and the logbook holds it")
    }

    @Test
    fun `forgetting lists the stamps again and tells the logbook`() {
        var listings = 0
        val store = CachedFileStore(logbook, { listings += 1; stamps }, copies)
        store.readText("gear.json")
        store.readText("person.json")
        assertEquals(1, listings, "one listing serves a whole reading")
        store.forget()
        store.readText("gear.json")
        assertEquals(2, listings)
        assertEquals(1, logbook.forgotten)
    }

    @Test
    fun `a copy that will not read is no copy, and costs only a slower reading`() {
        copies.writeText("logbook.copy", "yemoja copy 1\ngear.json\n1/9\n999\nshort")
        assertEquals("""{"a": {}}""", store().readText("gear.json"))
        assertEquals(listOf("gear.json"), logbook.reads)
    }

    @Test
    fun `what a reading will ask the logbook for is what the copy lacks or holds stale`() {
        assertEquals(listOf("gear.json", "person.json"), store().wanting(), "nothing copied yet, in path order")
        store().apply { readText("gear.json"); readText("person.json"); keep() }
        assertEquals(emptyList(), store().wanting(), "all of it copied as it now is")
        stamps = stamps + ("gear.json" to "2/9")
        assertEquals(listOf("gear.json"), store().wanting(), "one changed since")
    }

    @Test
    fun `a file with no stamp is not wanted ahead, and neither is one written here`() {
        stamps = mapOf("person.json" to "1/2")
        val store = store()
        assertEquals(listOf("person.json"), store.wanting(), "gear.json says no stamp, so nothing knows it")
        store.writeText("person.json", "{}")
        assertEquals(emptyList(), store.wanting(), "what was written here is read back from memory")
    }
}
