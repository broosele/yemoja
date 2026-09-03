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
        return Logbook.open(folder.toString())
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
