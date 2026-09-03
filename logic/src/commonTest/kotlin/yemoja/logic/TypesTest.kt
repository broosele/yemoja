package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.TextDescription
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A logbook of these files, read with every type the application knows. */
private fun logbook(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun text(item: Item, name: String): String? =
    (item.single<String>(name) as? Result.Usable)?.value

private fun number(item: Item, name: String): Double? =
    (item.single<Double>(name) as? Result.Usable)?.value

class EveryTypeTest {

    @Test
    fun `a type is named as its files are`() {
        assertEquals(listOf("person", "region", "gear"), Types.ALL.map { it.name })
    }

    @Test
    fun `every type has a name, which its id is worked out from`() {
        for (type in Types.ALL) {
            val name = assertNotNull(type["name"], "${type.name} should have a name")
            assertEquals(Cardinality.SINGLE, name.cardinality, type.name)
            assertEquals(String::class, name.valueType, type.name)
        }
    }

    @Test
    fun `every type has remarks, and only there is a line break allowed`() {
        for (type in Types.ALL) {
            assertIs<MultilineTextDescription>(type["remarks"], type.name)
        }
    }

    @Test
    fun `only single values and lists of them are described yet`() {
        // Keyed collections and owned items are still to come.
        val shown = setOf(Cardinality.SINGLE, Cardinality.LIST)
        for (type in Types.ALL) {
            for (field in type.fields) {
                assertTrue(field.cardinality in shown, "${type.name}.${field.name}")
            }
        }
    }

    @Test
    fun `a region names the regions it sits inside, and there may be several`() {
        val parents = assertNotNull(Types.REGION["parents"])
        assertEquals(Cardinality.LIST, parents.cardinality)
        assertIs<ReferenceDescription>(parents)
        assertEquals("region", parents.targetType)
    }

    @Test
    fun `an emergency contact may be a plain name, having no item of their own`() {
        val contacts = assertNotNull(Types.PERSON["emergency_contacts"])
        assertEquals(Cardinality.LIST, contacts.cardinality)
        assertIs<ReferenceDescription>(contacts)
        assertTrue(contacts.oneOffAllowed)
    }
}

class PersonTest {

    private fun person(fields: String) = logbook("person.json" to """{"anna": $fields}""")["anna"]!!

    @Test
    fun `a name is assembled from the parts`() {
        val read = person("""{"first_name": "Anna", "last_name": "de Vries"}""")
            .single<String>("name")
        assertEquals("Anna de Vries", (read as Result.Usable).value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `middle names sit between them`() {
        val fields = """{"first_name": "Anna", "middle_names": "Maria Louise",
            "last_name": "de Vries"}"""
        assertEquals("Anna Maria Louise de Vries", text(person(fields), "name"))
    }

    @Test
    fun `a part that is missing leaves no gap`() {
        assertEquals("Anna", text(person("""{"first_name": "Anna"}"""), "name"))
        assertEquals("de Vries", text(person("""{"last_name": "de Vries"}"""), "name"))
    }

    @Test
    fun `a written name corrects the assembly`() {
        // Names do not all follow one pattern, so the user's is the one that counts.
        val fields = """{"first_name": "Anna", "last_name": "de Vries", "name": "Vries, A de"}"""
        val read = person(fields).single<String>("name") as Result.Usable
        assertEquals("Vries, A de", read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }

    @Test
    fun `a person with no parts at all has no name`() {
        // Nothing is required, including a name, and an empty person is not a run of spaces.
        assertEquals(Result.Absent, person("{}").single<String>("name"))
    }

    @Test
    fun `the rest of a person reads as it is written`() {
        val fields = """{"birthday": "1990-04-03", "email": "anna@example.invalid",
            "phone": "+31 6 0000 0000", "address": "1 Nowhere Street"}"""
        val anna = person(fields)
        assertEquals("anna@example.invalid", text(anna, "email"))
        assertIs<Result.Usable<*>>(anna.single<Date>("birthday"))
    }
}

class RegionTest {

    private fun region(fields: String) =
        logbook("region.json" to """{"north_sea": $fields}""")["north_sea"]!!

    @Test
    fun `the four edges are angles`() {
        for (edge in listOf("west", "east", "south", "north")) {
            val field = Types.REGION[edge]
            assertIs<NumberDescription>(field, edge)
            assertEquals(Dimension.ANGLE, field.dimension, edge)
        }
    }

    @Test
    fun `a box is read as written`() {
        val fields = """{"name": "North Sea", "category": "sea",
            "west": -4.5, "east": 12.0, "south": 51.0, "north": 61.0}"""
        val sea = region(fields)
        assertEquals(-4.5, number(sea, "west"))
        assertEquals(61.0, number(sea, "north"))
        assertEquals("sea", text(sea, "category"))
    }

    @Test
    fun `east may be west of west, because the date line is unremarkable`() {
        // The Pacific runs from 120 to -70, which is where it starts and where it ends.
        val pacific = region("""{"west": 120.0, "east": -70.0}""")
        assertEquals(120.0, number(pacific, "west"))
        assertEquals(-70.0, number(pacific, "east"))
    }

    @Test
    fun `a latitude past the pole is unusable, and says so in degrees`() {
        val refused = region("""{"north": 91.0}""").single<Double>("north")
        assertIs<Result.Unusable>(refused)
        assertEquals("north should be within -90.0..90.0 deg, but was 91.0 deg", refused.reason)
    }

    @Test
    fun `a longitude past the meridian is unusable`() {
        assertIs<Result.Unusable>(region("""{"west": -181.0}""").single<Double>("west"))
    }

    @Test
    fun `the usual categories are suggested and nothing else is refused`() {
        val suggested = assertNotNull(Types.REGION["category"] as? TextDescription)
        assertTrue("ocean" in suggested.suggestedSet!!)
        // Anything a user likes, so the set suggests rather than closes.
        assertEquals("lake", text(region("""{"category": "lake"}"""), "category"))
    }
}

class GearTest {

    private fun gear(fields: String, units: String = "") =
        logbook("gear.json" to """{$units"twelve_litre": $fields}""")["twelve_litre"]!!

    @Test
    fun `what a cylinder holds is a volume`() {
        val capacity = Types.GEAR["capacity"]
        assertIs<NumberDescription>(capacity)
        assertEquals(Dimension.VOLUME, capacity.dimension)
    }

    @Test
    fun `a capacity follows the units of its file`() {
        // A twelve-litre written in cubic metres is 0.012, and the model holds litres.
        val cylinder = gear("""{"capacity": 0.012}""", """"units": {"volume": "m3"}, """)
        assertEquals(12.0, number(cylinder, "capacity"))
    }

    @Test
    fun `a piece of gear reads as it is written`() {
        val fields = """{"name": "Faber 12", "brand": "Faber", "model": "HP100",
            "serial": "0000-0000", "category": "cylinder", "kind": "steel",
            "description": "A twelve-litre steel cylinder", "capacity": 12.0}"""
        val cylinder = gear(fields)
        assertEquals("Faber", text(cylinder, "brand"))
        assertEquals("cylinder", text(cylinder, "category"))
        assertEquals("steel", text(cylinder, "kind"))
        assertEquals(12.0, number(cylinder, "capacity"))
    }

    @Test
    fun `generic says whether an item is a kind of thing rather than one you own`() {
        val supplied = gear("""{"generic": true}""")
        assertEquals(true, (supplied.single<Boolean>("generic") as Result.Usable).value)
        // Leave it out and it is not answered here: absent is false, and that is the reader's.
        assertEquals(Result.Absent, gear("{}").single<Boolean>("generic"))
    }
}

class ReadingAWholeLogbookTest {

    @Test
    fun `three types read together, and each item knows its own`() {
        val set = logbook(
            "person.json" to """{"anna": {"first_name": "Anna", "last_name": "de Vries"}}""",
            "region.json" to """{"north_sea": {"name": "North Sea", "category": "sea"}}""",
            "gear/faber_12.json" to """{"name": "Faber 12", "capacity": 12.0}""",
        )
        assertEquals(3, set.size)
        assertEquals("Anna de Vries", text(set["anna"]!!, "name"))
        assertEquals("North Sea", text(set["north_sea"]!!, "name"))
        assertEquals(12.0, number(set["faber_12"]!!, "capacity"))
    }

    @Test
    fun `a supplied library is read alongside the logbook`() {
        // The shape every file in libraries/ has: a units block, then items under their ids.
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    LogbookReader.MANIFEST to
                        """{"libraries": {"gear": ["generic_gear"]}}""",
                    "libraries/generic_gear.json" to """{
                        "units": {"mass": "kg", "volume": "l"},
                        "generic_2_kg_lead_weight": {
                            "name": "2 kg lead weight", "category": "weights",
                            "kind": "lead weight", "generic": true
                        }
                    }""",
                ),
            ),
            Types.ALL,
        )
        assertEquals(1, set.size)
        val weight = set["generic_2_kg_lead_weight"]!!
        assertEquals("2 kg lead weight", text(weight, "name"))
        assertEquals(Types.GEAR, weight.description)
    }

    @Test
    fun `a field no type describes is kept apart rather than lost`() {
        val set = logbook("region.json" to """{"north_sea": {"name": "North Sea", "sea_id": 7}}""")
        assertEquals(setOf("sea_id"), set["north_sea"]!!.unrecognisedFields.keys)
    }
}
