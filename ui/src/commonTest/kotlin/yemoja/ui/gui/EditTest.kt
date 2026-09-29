package yemoja.ui.gui

import yemoja.data.ItemReader
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Change
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What an edit form hands the model. See ../../../../../../gui/doc.md — `GUI-29`.
 */
class EditTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "person.json" to """{"anna": {"first_name": "Anna"}}""",
                "dive/2026-06-21#0.json" to
                    """{"dive_site": "@blue", "buddies": ["@anna", "Jo"], "rating": 7,
                       "profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
            ),
        ),
        Types.ALL,
    )

    private val dive = set["2026-06-21#0"]!!

    private fun field(name: String) = Types.DIVE[name]!!

    @Test
    fun `each kind of field is edited its own way`() {
        assertEquals(Kind.NUMBER, kindOf(field("max_depth")))
        assertEquals(Kind.CLOCK, kindOf(field("duration")))
        assertEquals(Kind.RATING, kindOf(field("rating")))
        assertEquals(Kind.WHOLE, kindOf(field("dive_number")))
        assertEquals(Kind.DATE, kindOf(field("start_date")))
        assertEquals(Kind.YES_NO, kindOf(field("deco")))
        assertEquals(Kind.REFERENCE, kindOf(field("dive_site")))
        assertEquals(Kind.KEY, kindOf(field("primary_profile")))
        assertEquals(Kind.CHOICE, kindOf(Types.DIVE_SITE["water_type"]!!))
        assertEquals(Kind.SUGGESTED, kindOf(Types.DIVE_SITE["entry"]!!), "offered, not enforced")
        assertEquals(Kind.LONG_TEXT, kindOf(field("remarks")))
        assertEquals(Kind.NONE, kindOf(profile()["depth"]!!), "a series is read on the graph")
    }

    @Test
    fun `a key reference into another item is typed, not chosen from a list`() {
        // The entries are another dive's, so this form has no list to offer and would have asked
        // the profile for a collection profiles do not have. That threw, and opening the form on
        // any dive with a recording took the window with it.
        assertEquals(Kind.TEXT, kindOf(profile()["previous_profile"]!!))
        assertEquals(Kind.KEY, kindOf(field("primary_profile")), "its own are still chosen")
    }

    @Test
    fun `every list a form offers keys from is one the edited item has`() {
        // The general case of the crash above: the form reads the collection off the item it is
        // editing, so a key field chosen from a list must name a list that type declares. Walked
        // through everything a dive holds, owned items inside owned items included.
        val seen = HashSet<String>()
        fun walk(type: yemoja.data.ItemDescription) {
            if (!seen.add(type.name)) return
            val arranged = arrangedOf(type, editing = true)
            for (each in arranged.plain) {
                val key = each as? yemoja.data.KeyReferenceDescription ?: continue
                if (kindOf(key) != Kind.KEY) continue
                assertTrue(
                    type[key.collection] != null,
                    "${type.name}.${key.name} offers keys from ${key.collection}, which a" +
                        " ${type.name} does not have",
                )
            }
            for (inset in arranged.insets) walk(inset.description)
        }
        walk(Types.DIVE)
        assertTrue("profile" in seen && "gas_source" in seen, "the walk reached a plan's cylinders")
    }

    private fun profile() = (field("profiles") as yemoja.data.OwnedItemDescription).description

    @Test
    fun `what is worked out is not edited, and what is worked out unless told otherwise is`() {
        assertTrue(!editable(field("name")), "a dive's name is its id")
        assertTrue(editable(field("max_depth")) && overrideable(field("max_depth")))
        assertTrue(editable(field("rating")) && !overrideable(field("rating")))
    }

    @Test
    fun `a value is edited as the text a file holds it as, a time as a clock`() {
        assertEquals("@blue", textOf(field("dive_site"), yemoja.data.Reference.Identified("blue")))
        assertEquals("Jo", textOf(field("buddies"), yemoja.data.Reference.OneOff("Jo")))
        assertEquals("61:16", textOf(field("duration"), 3676.0))
        assertEquals("12.3", textOf(field("max_depth"), 12.345))
        assertEquals("", textOf(field("max_depth"), null))
    }

    @Test
    fun `a clock typed is seconds given, and a bare number is minutes`() {
        assertEquals("3676", givenOf(Kind.CLOCK, "61:16"))
        assertEquals("2700", givenOf(Kind.CLOCK, "45"))
        assertEquals("-60", givenOf(Kind.CLOCK, "-1:00"))
        // Not a clock, so it is left as typed for the model to refuse.
        assertEquals("1:75", givenOf(Kind.CLOCK, "1:75"))
        assertNull(givenOf(Kind.CLOCK, "  "), "nothing typed is the field cleared")
        assertEquals("12.3", givenOf(Kind.NUMBER, " 12.3 "))
    }

    @Test
    fun `a list's entries are edited as texts`() {
        val buddies = (dive.read("buddies") as yemoja.data.Result.Usable<*>).value
        assertEquals(listOf("@anna", "Jo"), entriesOf(field("buddies"), buddies))
    }

    @Test
    fun `a draft becomes one write per field changed, a list as its entries`() {
        val draft = Draft()
        assertTrue(draft.isEmpty)
        draft.put(dive, "max_depth", "12.3")
        draft.put(dive, "buddies", listOf("@anna"), listOf("@anna"))
        draft.put(dive, "rating", null, null)
        val writes = draft.writes().map { it as Change.Write }.associateBy { it.field }
        assertEquals(setOf("max_depth", "buddies", "rating"), writes.keys)
        assertEquals(Stored.Leaf("12.3"), writes.getValue("max_depth").given)
        assertTrue(writes.getValue("buddies").given is Stored.Elements)
        assertNull(writes.getValue("rating").given, "a clearing is nothing given")
    }

    @Test
    fun `the model's refusal is known before anything is saved`() {
        val draft = Draft()
        draft.put(dive, "max_depth", "deep")
        assertTrue(draft.refusalOf(dive, "max_depth")!!.isNotEmpty())
        draft.put(dive, "max_depth", "12.3")
        assertNull(draft.refusalOf(dive, "max_depth"))
        assertNull(draft.refusalOf(dive, "rating"), "not drafted, nothing to refuse")
    }

    @Test
    fun `a field of an owned item is drafted on that item, told apart by identity`() {
        val draft = Draft()
        val read = (dive.read("profiles") as yemoja.data.Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val profiles = read as Map<String, yemoja.data.Element<Any>>
        val entry = (profiles.getValue("p") as yemoja.data.Element.Usable).value
        val p = entry as yemoja.data.OwnedItem
        draft.put(p, "remarks", "cold")
        assertTrue(draft.changed(p, "remarks"))
        assertTrue(!draft.changed(dive, "remarks"))
        assertEquals(p, (draft.writes().single() as Change.Write).item)
    }

    @Test
    fun `a collection is changed whole, without the entry taken out or with one added`() {
        val two = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/2026-06-22#0.json" to
                        """{"gas_sources": {"tank_1": {"gas_type": "EAN32"}, "tank_2": {}}}""",
                ),
            ),
            Types.ALL,
        )["2026-06-22#0"]!!
        val without = withoutEntry(two, "gas_sources", "tank_2")
        assertEquals(listOf("tank_1"), without.members.keys.toList())
        val kept = without.members.getValue("tank_1") as Stored.Members
        assertEquals("EAN32", (kept.members.getValue("gas_type") as Stored.Leaf).value)
        val (key, with) = withEntry(two, "gas_sources")
        assertEquals("gas", key, "what a gas source holding nothing is called")
        assertEquals(listOf("tank_1", "tank_2", "gas"), with.members.keys.toList())
    }

    @Test
    fun `every draft on an entry is dropped when the entry is no longer the one held`() {
        val draft = Draft()
        val (_, tank) = keyedEntriesOf(dive, "profiles").single()
        draft.put(tank, "remarks", "cold")
        draft.put(dive, "rating", "8")
        draft.dropAll(tank)
        assertTrue(!draft.changed(tank, "remarks"))
        assertTrue(draft.changed(dive, "rating"))
    }
}

/*
 * Beginning a singular owned item that the logbook has none of.
 * See ../../../../../../gui/doc.md — `GUI-29`.
 */
class MakingFromAReferenceTest {

    @Test
    fun `a name nothing answers to offers to make one, named as the type is`() {
        assertEquals("New dive site: \"Blue Hole\"", makingSaid(Types.DIVE_SITE, "Blue Hole"))
        assertEquals("New person: \"Anna\"", makingSaid(Types.PERSON, " Anna "), "trimmed")
        assertEquals(
            "New dive site: type a name",
            makingSaid(Types.DIVE_SITE, null),
            "offered with nothing typed, saying what it needs",
        )
    }

    @Test
    fun `keeping a name and making an item are two lines, and only one field takes both`() {
        assertEquals("Keep \"John\" as a name only", keepingSaid("John"))
        assertEquals("Keep as a name: type one", keepingSaid(" "))
        val buddies = Types.DIVE["buddies"] as yemoja.data.ReferenceDescription
        assertEquals(true, buddies.oneOffAllowed, "a buddy may be a name and nothing else")
        val site = Types.DIVE["dive_site"] as yemoja.data.ReferenceDescription
        assertEquals(true, site.oneOffAllowed, "a quarry dived once is a name, as a buddy may be")
        val trip = Types.DIVE["dive_trip"] as yemoja.data.ReferenceDescription
        assertEquals(false, trip.oneOffAllowed, "a trip gathers dives, so it has to be an item")
    }
}

class BegunTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    @Test
    fun `an item is made from what was typed, so its id is minted from that`() {
        // An id is minted once, at creation, and nothing renames it afterwards. Making the item
        // before the form is filled in would call every one of them unknown_person. `GUI-35`.
        val held = logbook()
        val draft = Draft()
        val making = ItemReader.read(
            Types.PERSON,
            Stored.Members(emptyMap()),
            held.logbook,
            yemoja.data.Units.DEFAULT,
        )
        draft.put(making, "first_name", "Anna")
        draft.put(making, "last_name", "Devries")
        val done = assertIs<Outcome.Done>(
            held.change(Operation.EDIT, Change.Add(Types.PERSON, draft.fieldsOf(making))),
        )
        assertEquals(listOf("anna_devries"), done.added)
    }

    @Test
    fun `a block begun inside an item being made folds into the fields that make it`() {
        val held = logbook()
        val draft = Draft()
        val making = ItemReader.read(
            Types.GEAR,
            Stored.Members(emptyMap()),
            held.logbook,
            yemoja.data.Units.DEFAULT,
        )
        draft.put(making, "name", "Wing blue")
        val buoyancy = draft.begin(making, making.description["buoyancy"] as OwnedItemDescription)
        draft.put(buoyancy, "mass", "2.4")
        val fields = draft.fieldsOf(making)
        assertEquals(setOf("name", "buoyancy"), fields.keys, "there is no owner to write onto yet")
        val done = assertIs<Outcome.Done>(
            held.change(Operation.EDIT, Change.Add(Types.GEAR, fields)),
        )
        val made = held.logbook[done.added.single()]!!
        val block = (made.read("buoyancy") as Result.Usable).value as OwnedItem
        assertEquals(2.4, (block.single<Double>("mass") as Result.Usable).value)
    }

    @Test
    fun `a block the form began writes nothing while nothing is typed into it`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        draft.begin(suit, inset)
        assertEquals(emptyList(), draft.writes(), "an empty block is not a change")
    }

    @Test
    fun `a block the form began lands whole once a field is typed into it`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        val block = draft.begin(suit, inset)
        draft.put(block, "mass", "4.2")
        val write = draft.writes().single() as Change.Write
        assertEquals("buoyancy", write.field, "one write, of the block onto its owner")
        assertTrue(write.item === suit)
        val members = (write.given as Stored.Members).members
        assertEquals(listOf("mass"), members.keys.toList(), "holding what was typed and no more")
    }

    @Test
    fun `a cylinder is chosen from the cylinders, and a buddy from everybody`() {
        val held = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"twelve": {"name": "Twelve", "category": "cylinder"},
                        "reg": {"name": "Regulator", "category": "regulator"},
                        "stage": {"name": "Stage", "category": "Cylinder"},
                        "loose": {"name": "Loose"}}""",
                    "person.json" to """{"anna": {"first_name": "Anna"}}""",
                ),
            ),
            Types.ALL,
        )
        val source = (Types.DIVE["gas_sources"] as OwnedItemDescription).description
        val cylinder = source["cylinder"] as yemoja.data.ReferenceDescription
        assertEquals(
            listOf("stage", "twelve"),
            candidatesOf(held, source, cylinder).map { it.id }.sorted(),
            "the category read without regard to case",
        )
        val buddies = Types.DIVE["buddies"] as yemoja.data.ReferenceDescription
        assertEquals(listOf("anna"), candidatesOf(held, Types.DIVE, buddies).map { it.id })
    }

    @Test
    fun `an offset is typed and read as hours and minutes with a sign`() {
        // As minutes and seconds two hours would read 120:00. `LOGIC-32`.
        val zone = Types.DIVE["time_zone_offset"]!!
        assertEquals(Kind.OFFSET, kindOf(zone))
        assertEquals(Kind.CLOCK, kindOf(Types.DIVE["duration"]!!), "a length of time is a clock")
        assertEquals("+2:00", textOf(zone, 7200.0))
        assertEquals("-3:30", textOf(zone, -12600.0))
        assertEquals("+0:00", textOf(zone, 0.0))
        assertEquals("7200", givenOf(Kind.OFFSET, "+2:00"))
        assertEquals("-12600", givenOf(Kind.OFFSET, "-3:30"))
        assertEquals("19800", givenOf(Kind.OFFSET, "5:30"))
        assertEquals("-5400", givenOf(Kind.OFFSET, "-1.5"), "a bare number is hours")
        assertEquals("2:75", givenOf(Kind.OFFSET, "2:75"), "not an offset, and left for the model")
    }

    @Test
    fun `closing the form forgets what was typed, and the blocks it began`() {
        // A cancelled edit reopened with its values still typed in, and Save offered them.
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        val block = draft.begin(suit, inset)
        draft.put(block, "mass", "4.2")
        draft.put(suit, "name", "Wetsuit")
        draft.clear()
        assertTrue(draft.isEmpty)
        assertEquals(emptyList(), draft.writes())
        assertTrue(draft.begin(suit, inset) !== block, "a block begun afterwards is a new one")
    }

    @Test
    fun `beginning the same block twice gives the same one back`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        assertTrue(draft.begin(suit, inset) === draft.begin(suit, inset), "one block, not two")
    }

    @Test
    fun `writing an empty block gives an item one it did not have`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        assertEquals(Result.Absent, suit.read("buoyancy"), "nothing to fill in yet")
        val done = held.change(
            Operation.EDIT,
            Change.Write(suit, "buoyancy", Stored.Members(emptyMap())),
        )
        assertTrue(done is Outcome.Done, "an empty block is a shape, not a value")
        val begun = held.logbook["suit"]!!.read("buoyancy")
        assertTrue(begun is Result.Usable, "and now there is one")
        val buoyancy = (begun as Result.Usable).value as OwnedItem
        assertEquals(Result.Absent, buoyancy.read("mass"), "empty, with its fields to fill in")
        assertTrue(
            buoyancy.description.fields.any { it.name == "compressible_fraction" },
            "including the ones that were unreachable",
        )
    }

    @Test
    fun `a field written into the begun block lands`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        held.change(
            Operation.EDIT,
            Change.Write(held.logbook["suit"]!!, "buoyancy", Stored.Members(emptyMap())),
        )
        val buoyancy = (held.logbook["suit"]!!.read("buoyancy") as Result.Usable).value as OwnedItem
        val done = held.change(Operation.EDIT, Change.Write(buoyancy, "mass", Stored.Leaf(4.2)))
        assertTrue(done is Outcome.Done)
        val read = (held.logbook["suit"]!!.read("buoyancy") as Result.Usable).value as OwnedItem
        assertEquals(4.2, (read.single<Double>("mass") as Result.Usable).value)
    }
}
