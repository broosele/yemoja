package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Where what a field says leads: a reference to its item, and nothing else anywhere.
 * See ../../../../../../gui/doc.md — `GUI-28`.
 */
class PartsTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "person.json" to
                    """{"anna": {"first_name": "Anna"}, "bram": {"first_name": "Bram"}}""",
                "dive_site.json" to """{"blue_hole": {"name": "Blue Hole"}}""",
                "dive/2026-06-21#0.json" to
                    """{"dive_site": "@blue_hole", "buddies": ["@anna", "@bram", "@gone"],
                       "max_depth": 30.0, "rating": 7}""",
            ),
        ),
        Types.ALL,
    )

    private val dive = set["2026-06-21#0"]!!

    private fun shown(field: String): Shown = shownOf(dive.description[field]!!, dive)!!

    @Test
    fun `a reference leads to the item it names`() {
        val part = shown("dive_site").parts.single()
        assertEquals("Blue Hole", part.text)
        assertEquals("blue_hole", part.leadsTo)
    }

    @Test
    fun `a list leads entry by entry, and the commas between lead nowhere`() {
        val parts = shown("buddies").parts
        assertEquals(listOf("Anna", ", ", "Bram", ", ", "@gone"), parts.map { it.text })
        assertEquals(listOf("anna", null, "bram", null, null), parts.map { it.leadsTo })
    }

    @Test
    fun `a region's children are left to the tree, a trip's dives to their statistics`() {
        val names = fieldsShownOf(Types.REGION).map { it.name }
        assertEquals(Types.REGION.fields.size - 1, names.size)
        assertEquals(false, "children" in names)
        assertEquals(false, "dives" in fieldsShownOf(Types.DIVE_TRIP).map { it.name })
        val kept = Types.DIVE.housekeeping.size
        assertEquals(Types.DIVE.fields.size - kept, fieldsShownOf(Types.DIVE).size)
    }

    @Test
    fun `a value that is not a reference leads nowhere`() {
        assertNull(shown("max_depth").parts.single().leadsTo)
    }

    @Test
    fun `a rating is a rating, and nothing else is`() {
        assertEquals(7, shown("rating").rating)
        assertEquals("7", shown("rating").text, "read straight through, it is still the number")
        assertNull(shown("max_depth").rating)
    }

    @Test
    fun `a rating out of ten is five stars, two points a star, an odd one ending in a half`() {
        val (f, h, e) = Triple(Star.FULL, Star.HALF, Star.EMPTY)
        assertEquals(listOf(f, f, f, h, e), starsOf(7))
        assertEquals(listOf(f, f, f, f, f), starsOf(10))
        assertEquals(listOf(h, e, e, e, e), starsOf(1))
        assertEquals(listOf(f, f, f, f, f), starsOf(12), "above the range is all of them")
        assertEquals(listOf(e, e, e, e, e), starsOf(0), "below it is none")
    }
}

class DisplayTest {

    private val depth = Types.DIVE["max_depth"]!!
    private val duration = Types.DIVE["duration"]!!

    @Test
    fun `a time reads as minutes and seconds, however long`() {
        assertEquals("61:16", clockOf(3676.0))
        assertEquals("121:05", clockOf(7265.0))
        assertEquals("0:05", clockOf(5.0))
        assertEquals("-60:00", clockOf(-3600.0))
        assertEquals("61:16", displayOf(duration, 3676.0))
    }

    @Test
    fun `a depth reads to one decimal, no trailing zero, and its unit after it`() {
        assertEquals("12.3 m", displayOf(depth, 12.345))
        assertEquals("12 m", displayOf(depth, 12.0))
        assertEquals("12.4 m", displayOf(depth, 12.35))
        assertEquals("12.3", numberOf(depth, 12.345), "the number alone, for a range")
    }

    @Test
    fun `an angle keeps the file's own precision, a coordinate being nothing to round`() {
        val latitude = Types.DIVE_SITE["latitude"]!!
        assertEquals("51.643556 °", displayOf(latitude, 51.643556))
    }

    @Test
    fun `a time carries no unit, being a clock, and a whole number none either`() {
        assertEquals("", unitOf(duration))
        assertEquals("", unitOf(Types.DIVE["dive_number"]!!))
        assertEquals("m", unitOf(depth))
    }
}

class KeyTest {

    @Test
    fun `a key reads with its underscores as spaces and its first letter up`() {
        assertEquals("Tank 1", prettyOf("tank_1"))
        assertEquals("Perdix 2", prettyOf("perdix_2"))
        assertEquals("P2", prettyOf("p2"))
    }

    @Test
    fun `a key reference reads as the key, read`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/2026-06-21#0.json" to
                        """{"primary_profile": "*perdix_2", "profiles": {"perdix_2": {}}}""",
                ),
            ),
            Types.ALL,
        )
        val dive = set["2026-06-21#0"]!!
        assertEquals("Perdix 2", shownOf(dive.description["primary_profile"]!!, dive)!!.text)
    }
}

class HousekeepingTest {

    private val profile = (Types.DIVE["profiles"] as yemoja.data.OwnedItemDescription).description

    @Test
    fun `what is kept for the machinery, or solely feeds other fields, is not shown`() {
        val names = fieldsShownOf(profile).map { it.name }
        for (hidden in listOf("fingerprint", "serial", "gmt_offset", "tolerances", "start_date")) {
            assertEquals(false, hidden in names, hidden)
        }
        assertEquals(true, "duration" in names)
        assertEquals(false, "primary_profile" in fieldsShownOf(Types.DIVE).map { it.name })
        assertEquals(false, "access_code" in fieldsShownOf(Types.GEAR).map { it.name })
        assertEquals(true, "dive_number" in fieldsShownOf(Types.DIVE).map { it.name })
    }

    @Test
    fun `but it is shown when editing`() {
        val names = fieldsShownOf(profile, editing = true).map { it.name }
        assertEquals(true, "fingerprint" in names)
        assertEquals(true, "start_date" in names)
        val dive = fieldsShownOf(Types.DIVE, editing = true).map { it.name }
        assertEquals(true, "primary_profile" in dive)
    }

    @Test
    fun `the entry an item points at by key is the one to mark`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/2026-06-21#0.json" to """{"primary_profile": "*b",
                        "profiles": {"a": {}, "b": {}, "c": {}}}""",
                    "dive/2026-06-22#0.json" to """{"profiles": {"a": {}}}""",
                ),
            ),
            Types.ALL,
        )
        assertEquals(1, pointedEntryOf(set["2026-06-21#0"]!!, "profiles"))
        assertNull(pointedEntryOf(set["2026-06-22#0"]!!, "profiles"), "nothing points")
        assertNull(pointedEntryOf(set["2026-06-21#0"]!!, "gas_sources"), "no pointer to it")
    }
}

class ArrangedTest {

    @Test
    fun `an item view flows the plain fields and sets each owned item in an inset, in order`() {
        val dive = arrangedOf(Types.DIVE)
        assertEquals(
            listOf("details", "environment", "gear", "profiles", "gas_sources"),
            dive.insets.map { it.name },
        )
        assertEquals(false, dive.plain.any { it.name in dive.insets.map { i -> i.name } })
        val kept = Types.DIVE.housekeeping.size
        assertEquals(Types.DIVE.fields.size - kept, dive.plain.size + dive.insets.size)
    }

    @Test
    fun `a series is not laid out, the graph being where it is read`() {
        val profile = (Types.DIVE["profiles"] as yemoja.data.OwnedItemDescription).description
        val names = arrangedOf(profile).plain.map { it.name }
        assertEquals(false, "depth" in names)
        assertEquals(false, "pressures" in names)
        assertEquals(true, "duration" in names)
    }

    @Test
    fun `a tab is called by the entry's name, else by what it points at, else by its key`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"perdix": {"name": "Perdix 2"}}""",
                    "dive/2026-06-21#0.json" to """{"profiles": {
                        "p1": {"dive_computer": "@perdix", "depth": [[0, 0]]},
                        "p2": {"depth": [[0, 0]]}
                    }}""",
                ),
            ),
            Types.ALL,
        )
        val dive = set["2026-06-21#0"]!!
        val read = (dive.read("profiles") as Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val profiles = read as Map<String, Element<Any>>
        val p1 = (profiles.getValue("p1") as Element.Usable).value as OwnedItem
        val p2 = (profiles.getValue("p2") as Element.Usable).value as OwnedItem
        assertEquals("Perdix 2", entryLabelOf("p1", p1), "the computer that made it")
        assertEquals("P2", entryLabelOf("p2", p2), "nothing on it says a thing, so the key, read")
    }
}
