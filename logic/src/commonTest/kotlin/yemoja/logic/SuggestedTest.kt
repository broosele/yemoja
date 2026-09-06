package yemoja.logic

import yemoja.data.FieldDescription
import yemoja.data.ItemDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * What a field suggests, which is what it ships with joined with what the logbook already uses.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** A Universe over these files. */
private fun over(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store)
}

private fun categoryOf(type: ItemDescription): FieldDescription = type["category"]!!

/** The maintenance an item of gear owns, whose type is the field most of this exercises. */
private val MAINTENANCE = (Types.GEAR["maintenances"] as OwnedItemDescription).description

class SuggestedTest {

    @Test
    fun `a field that suggests nothing suggests nothing`() {
        assertEquals(emptyList(), over("region.json" to "{}").suggested(Types.REGION["name"]!!))
    }

    @Test
    fun `the presets keep the order they were declared in`() {
        // `none, light, moderate, strong` is a scale; sorted it says something else.
        assertEquals(
            listOf("world", "continent", "ocean", "sea", "country", "area"),
            over("region.json" to "{}").suggested(categoryOf(Types.REGION)),
        )
    }

    @Test
    fun `a word in use follows them`() {
        val said = over("region.json" to """{"a": {"category": "archipelago"}}""")
            .suggested(categoryOf(Types.REGION))
        assertEquals("archipelago", said.last())
        assertEquals(7, said.size, "and the presets are all still there: $said")
    }

    @Test
    fun `words in use are sorted among themselves`() {
        val said = over(
            "region.json" to """{"a": {"category": "reef"}, "b": {"category": "archipelago"}}""",
        ).suggested(categoryOf(Types.REGION))
        assertEquals(listOf("archipelago", "reef"), said.takeLast(2))
    }

    @Test
    fun `a word that is already a preset is not offered twice`() {
        val said = over("region.json" to """{"a": {"category": "sea"}}""")
            .suggested(categoryOf(Types.REGION))
        assertEquals(1, said.count { it == "sea" }, said.toString())
    }

    @Test
    fun `a word used inside an owned item counts`() {
        // Most fields that suggest anything sit on one: a profile's model, a maintenance's type.
        val universe = over("gear.json" to """{"a": {"maintenances": {"k": {"type": "polish"}}}}""")
        assertTrue("polish" in universe.suggested(MAINTENANCE["type"]!!))
    }

    @Test
    fun `two fields sharing a vocabulary do not share what is used`() {
        // They ship with the same words and are still two fields. Pooling them would mean
        // matching sets by their contents, which is a coincidence rather than a statement.
        val universe = over("gear.json" to """{"a": {"maintenances": {"k": {"type": "polish"}}}}""")
        assertTrue("polish" !in universe.suggested(MAINTENANCE["follow_up_type"]!!))
    }

    @Test
    fun `what a change adds is offered from then on`() {
        val universe = over("region.json" to """{"a": {}}""")
        val region = universe.logbook.allOf(Types.REGION).first()
        universe.change(Operation.EDIT, Change.Write(region, "category", "archipelago"))
        assertTrue("archipelago" in universe.suggested(categoryOf(Types.REGION)))
    }
}
