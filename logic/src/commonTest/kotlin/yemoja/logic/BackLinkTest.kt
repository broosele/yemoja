package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * The far side of a reference, worked out on the item pointed at: a site's dives, a person's,
 * an operator's, a wreck's sites, a region's, a piece of gear's dives.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */
class BackLinkTest {

    private val set: ItemSet = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "region.json" to """{"egypt": {"name": "Egypt"}}""",
                "wreck.json" to """{"thistlegorm": {"name": "Thistlegorm"}}""",
                "dive_site.json" to """{
                    "blue_hole": {"name": "Blue Hole", "regions": ["@egypt"]},
                    "shaab_ali": {"name": "Shaab Ali", "regions": ["@egypt"],
                                  "wrecks": ["@thistlegorm"]}
                }""",
                "person.json" to
                    """{"anna": {"first_name": "Anna"}, "bram": {"first_name": "Bram"}}""",
                "operator.json" to """{"rse": {"name": "Red Sea Explorers"}}""",
                "dive_trip.json" to
                    """{"egypt_2026": {"name": "Egypt 2026", "operator": "@rse"}}""",
                "gear.json" to """{"wing": {"name": "Wing"}, "twelve": {"name": "Twelve"}}""",
                "dive/2026-06-01#0.json" to """{
                    "dive_site": "@blue_hole", "buddies": ["@anna"],
                    "operator": "@rse", "gear": {"items": ["@wing", "@twelve"]}
                }""",
                "dive/2026-06-02#0.json" to """{
                    "dive_site": "@blue_hole", "buddies": ["@anna", "@bram"],
                    "gear": {"items": ["@wing"]}
                }""",
                "dive/2026-06-03#0.json" to """{"dive_site": "@shaab_ali"}""",
            ),
        ),
        Types.ALL,
    )

    private fun named(item: Item, field: String): List<String> {
        val where = "$field on ${item.description.name}"
        val read = assertIs<Result.Usable<*>>(item.read(field), where)
        assertEquals(Result.Origin.DERIVED, read.origin)
        return (read.value as List<*>).map { (it as Element.Usable<*>).value }
            .map { (it as Reference.Identified).id }
    }

    @Test
    fun `a site holds the dives made there`() {
        val first = setOf("2026-06-01#0", "2026-06-02#0")
        assertEquals(first, named(set["blue_hole"]!!, "dives").toSet())
        assertEquals(listOf("2026-06-03#0"), named(set["shaab_ali"]!!, "dives"))
    }

    @Test
    fun `a person holds the dives they were a buddy on`() {
        val both = setOf("2026-06-01#0", "2026-06-02#0")
        assertEquals(both, named(set["anna"]!!, "dives").toSet())
        assertEquals(listOf("2026-06-02#0"), named(set["bram"]!!, "dives"))
    }

    @Test
    fun `an operator holds the dives and the trips naming it`() {
        assertEquals(listOf("2026-06-01#0"), named(set["rse"]!!, "dives"))
        assertEquals(listOf("egypt_2026"), named(set["rse"]!!, "dive_trips"))
    }

    @Test
    fun `a wreck holds the sites it lies at`() {
        assertEquals(listOf("shaab_ali"), named(set["thistlegorm"]!!, "dive_sites"))
    }

    @Test
    fun `a region holds the sites naming it, and not the ones deeper inside`() {
        assertEquals(setOf("blue_hole", "shaab_ali"), named(set["egypt"]!!, "dive_sites").toSet())
    }

    @Test
    fun `a piece of gear holds the dives it was taken on`() {
        val both = setOf("2026-06-01#0", "2026-06-02#0")
        assertEquals(both, named(set["wing"]!!, "dives").toSet())
        assertEquals(listOf("2026-06-01#0"), named(set["twelve"]!!, "dives"))
    }

    @Test
    fun `a dive computer holds the dives it recorded, whether or not the dive lists it as gear`() {
        val held = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"perdix": {"name": "Perdix", "serial": "A1B2"},
                        "petrel": {"name": "Petrel"}}""",
                    "dive/2026-06-01#0.json" to """{"profiles": {"perdix": {"serial": "A1B2"}}}""",
                    "dive/2026-06-02#0.json" to """{"gear": {"items": ["@perdix"]},
                        "profiles": {"perdix": {"serial": "A1B2"},
                                     "petrel": {"dive_computer": "@petrel"}}}""",
                    "dive/2026-06-03#0.json" to """{"gear": {"items": ["@petrel"]}}""",
                ),
            ),
            Types.ALL,
        )
        assertEquals(
            listOf("2026-06-01#0", "2026-06-02#0"),
            named(held["perdix"]!!, "dives"),
            "worked out from the serial, and listed once where the gear names it too",
        )
        assertEquals(listOf("2026-06-02#0", "2026-06-03#0"), named(held["petrel"]!!, "dives"))
    }

    @Test
    fun `the user holds every dive, being on each of them whoever else was`() {
        val held = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "yemoja.json" to """{"user": "@bram"}""",
                    "person.json" to """{"anna": {"first_name": "Anna"},
                        "bram": {"first_name": "Bram"}}""",
                    "dive/2026-06-01#0.json" to """{"buddies": ["@anna"]}""",
                    "dive/2026-06-02#0.json" to "{}",
                ),
            ),
            Types.ALL,
        )
        assertEquals(listOf("2026-06-01#0", "2026-06-02#0"), named(held["bram"]!!, "dives"))
        assertEquals(listOf("2026-06-01#0"), named(held["anna"]!!, "dives"), "a buddy is not")
    }

    @Test
    fun `nothing pointing back is an empty list, having been asked`() {
        assertEquals(emptyList(), named(set["bram"]!!, "dives").filter { it == "none" })
        val trips = assertIs<Result.Usable<*>>(set["egypt_2026"]!!.read("dives"))
        assertEquals(emptyList<Any>(), trips.value as List<*>)
    }
}
