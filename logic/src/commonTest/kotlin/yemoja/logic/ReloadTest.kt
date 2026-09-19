package yemoja.logic

import yemoja.data.Item
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Reading a logbook again after another hand has changed its files. `API-5`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private fun opened(vararg files: Pair<String, String>): Pair<MemoryFileStore, Universe> {
    val store = MemoryFileStore(mapOf(*files))
    val manifest = LogbookReader.manifest(store)
    val items = LogbookReader.read(store, Types.ALL, manifest)
    val user = manifest.user?.let { items[it.id] }
    return store to Universe(items, user, store)
}

private fun text(item: Item?, field: String): String? =
    (item?.single<String>(field) as? Result.Usable)?.value

class ReloadTest {

    @Test
    fun `a file edited behind the universe is read again, and the revision moves`() {
        val (store, universe) = opened(
            "dive/2026-06-01#0.json" to """{"rating": 6}""",
            "person.json" to """{"anna": {"first_name": "Anna"}}""",
        )
        store.writeText("dive/2026-06-01#0.json", """{"rating": 9}""")
        store.writeText("person.json", """{"anna": {"first_name": "Anne"}, "ben": {"first_name": "Ben"}}""")
        assertIs<Outcome.Done>(universe.reload())
        val rating = universe.logbook["2026-06-01#0"]?.single<Int>("rating") as? Result.Usable
        assertEquals(9, rating?.value)
        assertEquals("Anne", text(universe.logbook["anna"], "first_name"))
        assertEquals("Ben", text(universe.logbook["ben"], "first_name"), "an item added by hand")
        assertEquals(1, universe.revision, "so whatever showed the logbook asks again")
    }

    @Test
    fun `an item whose file went is gone, and one that will not read refuses the whole reload`() {
        val (store, universe) = opened(
            "dive/2026-06-01#0.json" to """{"rating": 6}""",
            "dive/2026-06-02#0.json" to """{"rating": 7}""",
        )
        store.delete("dive/2026-06-02#0.json")
        assertIs<Outcome.Done>(universe.reload())
        assertNull(universe.logbook["2026-06-02#0"])

        store.writeText("dive/2026-06-01#0.json", """{"rating": """)
        val refused = universe.reload()
        assertIs<Outcome.Refused>(refused)
        assertTrue("could not be read again" in refused.reason, refused.reason)
        val rating = universe.logbook["2026-06-01#0"]?.single<Int>("rating") as? Result.Usable
        assertEquals(6, rating?.value, "and what was held stays as it was")
        assertEquals(1, universe.revision, "a refusal moves nothing")
    }

    @Test
    fun `the user is the person the manifest names, resolved against what was read`() {
        val (store, universe) = opened(
            "yemoja.json" to """{"user": "@anna"}""",
            "person.json" to """{"anna": {"first_name": "Anna"}}""",
        )
        assertEquals("Anna", text(universe.user, "first_name"))
        store.writeText("person.json", """{"anna": {"first_name": "Anne"}}""")
        universe.reload()
        assertEquals("Anne", text(universe.user, "first_name"), "the item read now, not the old one")
        store.writeText("person.json", """{"ben": {"first_name": "Ben"}}""")
        universe.reload()
        assertNull(universe.user, "a manifest naming nobody the logbook holds names nobody")
    }
}
