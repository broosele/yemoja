package yemoja.ui.gui

import yemoja.data.Item
import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

class WordTest {

    @Test
    fun `a word from a vocabulary reads as a word, and free text as it was typed`() {
        val configuration = (Types.DIVE["gas_sources"] as yemoja.data.OwnedItemDescription)
            .description["configuration"]!!
        assertEquals("Back mounted", displayOf(configuration, "back_mounted"))
        assertEquals("Side mounted", displayOf(configuration, "side_mounted"))
        assertEquals("Sidemount", displayOf(configuration, "sidemount"), "the model's own word")
        val usage = (Types.DIVE["gas_sources"] as yemoja.data.OwnedItemDescription)
            .description["usage"]!!
        assertEquals("Deco", displayOf(usage, "deco"))
        val gas = (Types.DIVE["gas_sources"] as yemoja.data.OwnedItemDescription)
            .description["gas_type"]!!
        assertEquals("EAN32", displayOf(gas, "EAN32"), "a mix is drawn from no set")
        assertEquals("cold and dark", displayOf(Types.DIVE["remarks"]!!, "cold and dark"))
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
        for (hidden in listOf(
            "fingerprint", "serial", "tolerances", "start_date", "recorded_time_offset",
        )) {
            assertEquals(false, hidden in names, hidden)
        }
        assertEquals(true, "duration" in names)
        assertEquals(false, "primary_profile" in fieldsShownOf(Types.DIVE).map { it.name })
        assertEquals(false, "time_zone_offset" in fieldsShownOf(Types.DIVE).map { it.name })
        val form = fieldsShownOf(Types.DIVE, editing = true).map { it.name }
        assertEquals(true, "time_zone_offset" in form, "still set in the form")
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
    fun `the entry an item points at by key comes first and is the one marked`() {
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
        val dive = set["2026-06-21#0"]!!
        assertEquals(
            listOf("b", "a", "c"),
            shownEntriesOf(dive, "profiles").map { it.first },
            "the one pointed at first, the rest as they are held",
        )
        assertEquals(0, pointedEntryOf(dive, "profiles"), "which is where the mark goes")
        assertEquals(
            listOf("a"),
            shownEntriesOf(set["2026-06-22#0"]!!, "profiles").map { it.first },
        )
        assertNull(pointedEntryOf(set["2026-06-22#0"]!!, "profiles"), "nothing points")
        assertNull(pointedEntryOf(dive, "gas_sources"), "no pointer to it")
        assertEquals(
            listOf("a", "b", "c"),
            keyedEntriesOf(dive, "profiles").map { it.first },
            "and what is stored is not reordered",
        )
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
    fun `the dives of gear, a person, a site and an operator sit at the foot, apart`() {
        for (type in listOf(Types.GEAR, Types.PERSON, Types.DIVE_SITE, Types.OPERATOR)) {
            val arranged = arrangedOf(type)
            assertEquals(listOf("dives"), arranged.foot.map { it.name }, type.name)
            assertEquals(false, arranged.plain.any { it.name == "dives" }, type.name)
        }
        assertEquals(
            true,
            arrangedOf(Types.OPERATOR).plain.any { it.name == "dive_trips" },
            "a handful of trips stays in the columns",
        )
    }

    @Test
    fun `the form has no foot, and lays out what the view put there among the rest`() {
        val arranged = arrangedOf(Types.GEAR, editing = true)
        assertEquals(emptyList(), arranged.foot)
        assertEquals(true, arranged.plain.any { it.name == "dives" })
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

    @Test
    fun `an entry naming something with a short form is labelled by the short form`() {
        // A row of tabs reading Advanced Open Water Diver has ends nobody can see. `GUI-16`.
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "certification.json" to """{
                        "ow": {"name": "Open Water Diver", "abbreviation": "OW"},
                        "nitrox": {"name": "Enriched Air Diver", "abbreviation": "nitrox"},
                        "cave": {"name": "Cave Diver"}
                    }""",
                    "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries",
                        "courses": {
                            "k1": {"certification": "@ow", "date": "2020-06-07"},
                            "k2": {"certification": "@cave", "date": "2021-06-07"},
                            "k3": {"certification": "@nitrox", "date": "2022-06-07"}
                        }}}""",
                ),
            ),
            Types.ALL,
        )
        val anna = set["anna"]!!
        val read = (anna.read("courses") as Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val courses = read as Map<String, Element<Any>>
        val first = (courses.getValue("k1") as Element.Usable).value as OwnedItem
        val second = (courses.getValue("k2") as Element.Usable).value as OwnedItem
        val third = (courses.getValue("k3") as Element.Usable).value as OwnedItem
        assertEquals("OW", entryLabelOf("k1", first), "raising the first letter leaves OW alone")
        assertEquals("Cave Diver", entryLabelOf("k2", second), "and the name where there is none")
        assertEquals("Nitrox", entryLabelOf("k3", third), "a label begins with a capital")
    }
}

/*
 * Which fields a form puts forward, and which it folds away.
 * See ../../../../../../gui/doc.md — `GUI-29`.
 */
class ForwardTest {

    private fun gear(json: String): Item =
        LogbookReader.read(MemoryFileStore(mapOf("gear.json" to json)), Types.ALL)
            .allOf(Types.GEAR).single()

    private fun folded(item: Item): List<String> =
        arrangedOf(item.description, editing = true).plain
            .filterNot { forwardOf(it, item) }.map { it.name }

    @Test
    fun `a computer's fields are put forward on a computer`() {
        val held = gear("""{"p": {"name": "Perdix 2", "category": "instruments"}}""")
        assertEquals(listOf("capacity"), folded(held), "and a cylinder's is not")
    }

    @Test
    fun `a computer's fields are folded away on a suit`() {
        val held = gear("""{"s": {"name": "Drysuit", "category": "suit"}}""")
        assertEquals(
            listOf("serial", "access_code", "capacity", "salt_density"),
            folded(held),
            "an access code on a drysuit is how a form reads as though nobody thought",
        )
    }

    @Test
    fun `an item saying nothing about what it is keeps them folded`() {
        // A reader who has not said what it is has not said the field applies either.
        val held = gear("""{"x": {"name": "Something"}}""")
        assertTrue("salt_density" in folded(held))
    }

    @Test
    fun `nothing is folded on a type that has no fields of one kind only`() {
        val set = LogbookReader.read(
            MemoryFileStore(mapOf("person.json" to """{"a": {"first_name": "Anna"}}""")),
            Types.ALL,
        )
        val anna = set.allOf(Types.PERSON).single()
        assertEquals(emptyList(), arrangedOf(anna.description, editing = true).plain
            .filterNot { forwardOf(it, anna) }.map { it.name })
    }
}
