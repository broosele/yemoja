package yemoja.logic

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import yemoja.data.Result

/*
 * Opening a logbook, which is the one thing the Universe does so far.
 *
 * Here rather than in commonTest because opening names a folder, and a folder is a platform.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** What a folder has to be before it is a logbook. */
class OpeningTest {

    @Test
    fun `a folder that is not there is a mistake, not an empty logbook`() {
        // A missing folder answers every question with no, so without the check a mistyped
        // path opens as a logbook holding nothing and the user is told nothing.
        val missing = Files.createTempDirectory("yemoja-").resolve("nowhere")
        val refused = assertFailsWith<IllegalArgumentException> {
            Universe.open(missing.toString())
        }
        assertEquals(true, refused.message?.endsWith("should be a folder, and is not"),
            refused.message)
    }

    @Test
    fun `a file where a folder should be is refused the same way`() {
        val file = Files.createTempDirectory("yemoja-").resolve("logbook.json")
        file.writeText("{}")
        assertFailsWith<IllegalArgumentException> { Universe.open(file.toString()) }
    }

    @Test
    fun `an empty folder is a logbook, and an empty one`() {
        // Nothing is required of a logbook but that the folder be there. `JSON-21`.
        val universe = Universe.open(Files.createTempDirectory("yemoja-").toString())
        assertEquals(0, universe.logbook.size)
        assertNull(universe.user)
    }
}

/** Who a logbook belongs to. `JSON-22`. */
class OwnerTest {

    private fun opened(manifest: String, person: String? = null): Universe {
        val folder = Files.createTempDirectory("yemoja-")
        folder.resolve("yemoja.json").writeText(manifest)
        if (person != null) folder.resolve("person.json").writeText(person)
        return Universe.open(folder.toString())
    }

    @Test
    fun `the owner is the person the manifest names`() {
        val universe = opened(
            """{"user": "@anna_devries"}""",
            """{"anna_devries": {"first_name": "Anna", "last_name": "de Vries"}}""",
        )
        val user = assertNotNull(universe.user)
        assertEquals("Anna de Vries", (user.single<String>("name") as Result.Usable).value)
    }

    @Test
    fun `a logbook naming no owner has none`() {
        assertNull(opened("""{"libraries": {}}""").user)
    }

    @Test
    fun `a logbook with no manifest has none`() {
        val folder = Files.createTempDirectory("yemoja-")
        assertNull(Universe.open(folder.toString()).user)
    }

    @Test
    fun `an owner nobody wrote is absent rather than a refusal`() {
        // A dangling reference like any other. The person may yet be added.
        val universe = opened("""{"user": "@nobody"}""", """{"anna_devries": {}}""")
        assertNull(universe.user)
        assertEquals(1, universe.logbook.size)
    }

    @Test
    fun `an owner naming something that is not a person is not the owner`() {
        val folder = Files.createTempDirectory("yemoja-")
        folder.resolve("yemoja.json").writeText("""{"user": "@north_sea"}""")
        folder.resolve("region.json").writeText("""{"north_sea": {"name": "North Sea"}}""")
        assertNull(Universe.open(folder.toString()).user)
    }
}
