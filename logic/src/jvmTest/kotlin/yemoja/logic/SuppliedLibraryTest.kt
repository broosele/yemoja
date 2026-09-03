package yemoja.logic

import yemoja.data.ItemSet
import yemoja.data.Result
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The libraries the application ships with, read from inside it.
 *
 * These are the real supplied files rather than invented ones, which is the point: what is
 * checked is that a released build can find its own data, and only the real thing can show that.
 */
class SuppliedLibraryTest {

    /** A logbook folder holding nothing but a manifest declaring [libraries]. */
    private fun logbookDeclaring(libraries: String): ItemSet {
        val folder: Path = Files.createTempDirectory("yemoja-")
        folder.resolve("yemoja.json").writeText("""{"libraries": $libraries}""")
        return Logbook.open(folder.toString()).items
    }

    private fun name(set: ItemSet, id: String): String? =
        (set[id]?.single<String>("name") as? Result.Usable)?.value

    @Test
    fun `a declared library is found inside the application`() {
        val set = logbookDeclaring("""{"region": ["region/world"]}""")
        assertEquals("World", name(set, "world"))
        assertEquals("Europe", name(set, "europe"))
    }

    @Test
    fun `every supplied region library loads`() {
        val every = """["region/world", "region/africa", "region/asia", "region/europe",
            "region/north_america", "region/oceania", "region/south_america"]"""
        val set = logbookDeclaring("""{"region": $every}""")
        assertTrue(set.size > 100, "the supplied regions should be many, and were ${set.size}")
        assertNotNull(set["north_sea"], "a sea from one of the later files should be there")
    }

    @Test
    fun `a library of another type loads under that type`() {
        val set = logbookDeclaring("""{"gear": ["generic_gear"]}""")
        val weight = assertNotNull(set["generic_2_kg_lead_weight"])
        assertEquals(Types.GEAR, weight.description)
        assertEquals("2 kg lead weight", name(set, "generic_2_kg_lead_weight"))
    }

    @Test
    fun `a library file declares its own units, and they are used`() {
        // generic_gear.json is written in kilograms and litres, and says so.
        val set = logbookDeclaring("""{"gear": ["generic_gear"]}""")
        val cylinder = assertNotNull(set["generic_steel_12_l_cylinder"])
        assertEquals(12.0, (cylinder.single<Double>("capacity") as Result.Usable).value)
    }

    @Test
    fun `a library the logbook does not declare is not read`() {
        assertEquals(0, logbookDeclaring("{}").size)
    }

    @Test
    fun `a name no supplied library answers to is passed over`() {
        val set = logbookDeclaring("""{"region": ["region/atlantis", "region/world"]}""")
        assertNull(set["atlantis"])
        assertEquals("World", name(set, "world"))
    }
}

/** Who a logbook belongs to. `JSON-22`. */
class OwnerTest {

    private fun logbook(manifest: String, person: String? = null): Logbook {
        val folder = Files.createTempDirectory("yemoja-")
        folder.resolve("yemoja.json").writeText(manifest)
        if (person != null) folder.resolve("person.json").writeText(person)
        return Logbook.open(folder.toString())
    }

    @Test
    fun `the owner is the person the manifest names`() {
        val logbook = logbook(
            """{"user": "@anna_devries"}""",
            """{"anna_devries": {"first_name": "Anna", "last_name": "de Vries"}}""",
        )
        val user = assertNotNull(logbook.user)
        assertEquals("Anna de Vries", (user.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `a logbook naming no owner has none`() {
        assertNull(logbook("""{"libraries": {}}""").user)
    }

    @Test
    fun `a logbook with no manifest has none`() {
        val folder = Files.createTempDirectory("yemoja-")
        assertNull(Logbook.open(folder.toString()).user)
    }

    @Test
    fun `an owner nobody wrote is absent rather than a refusal`() {
        // A dangling reference like any other. The person may yet be added.
        val logbook = logbook("""{"user": "@nobody"}""", """{"anna_devries": {}}""")
        assertNull(logbook.user)
        assertEquals(1, logbook.items.size)
    }

    @Test
    fun `an owner naming something that is not a person is not the owner`() {
        val folder = Files.createTempDirectory("yemoja-")
        folder.resolve("yemoja.json").writeText("""{"user": "@north_sea"}""")
        folder.resolve("region.json").writeText("""{"north_sea": {"name": "North Sea"}}""")
        assertNull(Logbook.open(folder.toString()).user)
    }
}
