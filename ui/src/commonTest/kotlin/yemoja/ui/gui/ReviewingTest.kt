package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Applied
import yemoja.logic.Changed
import yemoja.logic.Refused
import yemoja.logic.Staged
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Reviewing what an agent staged, apart from the drawing of it.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
class StagedSaidTest {

    private fun edit(fields: Int): Staged = Staged(
        "2026-06-01#0",
        "dive",
        Staged.Kind.EDIT,
        (1..fields).map { Changed("field$it", "1", "2") },
    )

    @Test
    fun `what a staging comes to counts each kind of change apart`() {
        val staged = listOf(
            edit(2),
            Staged("new#0", "dive_site", Staged.Kind.ADD, listOf(Changed("name", null, "Blue"))),
        )
        assertEquals(
            "An agent has staged 1 item to add and 1 item to change, 3 fields in all.",
            stagedSaidOf(staged),
        )
    }

    @Test
    fun `one kind alone reads as one clause`() {
        assertEquals(
            "An agent has staged 1 item to change, 2 fields in all.",
            stagedSaidOf(listOf(edit(2))),
        )
    }

    @Test
    fun `three kinds read with commas and an and`() {
        val staged = listOf(
            Staged("new#0", "dive", Staged.Kind.ADD, listOf(Changed("rating", null, "8"))),
            edit(1),
            Staged("2026-01-01#0", "dive", Staged.Kind.DELETE, listOf(Changed("rating", "4", null))),
        )
        val said = stagedSaidOf(staged)
        assertTrue("1 item to add, 1 item to change and 1 item to delete" in said, said)
    }

    @Test
    fun `nothing staged says so, which is what an empty review would say`() {
        assertEquals("The agent has proposed no changes.", stagedSaidOf(emptyList()))
    }
}

class ProposalSaidTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
                "dive/2026-06-01#0.json" to """{"dive_site": "@blue", "max_depth": 18}""",
            ),
        ),
        Types.ALL,
    )

    @Test
    fun `an item already held is called what the logbook calls it`() {
        val staged = Staged("blue", "dive_site", Staged.Kind.EDIT, emptyList())
        assertEquals("To change Dive site Blue Hole", proposalSaidOf(staged, set))
    }

    @Test
    fun `an item being added is called by its type, having no name until it lands`() {
        val staged = Staged("new#0", "dive_site", Staged.Kind.ADD, emptyList())
        assertEquals("To add a new dive site", proposalSaidOf(staged, set))
        assertTrue("new#0" !in proposalSaidOf(staged, set), "an id is never shown")
    }

    @Test
    fun `an item that has gone since is not named after something that is not there`() {
        val staged = Staged("2020-01-01#0", "dive", Staged.Kind.DELETE, emptyList())
        assertEquals("To delete a dive no longer in the logbook", proposalSaidOf(staged, set))
    }
}

class ChangeSaidTest {

    @Test
    fun `an edit says what it holds and what it would hold`() {
        val field = Changed("max_depth", "18 m", "21 m")
        assertEquals("18 m → 21 m", changeSaidOf(field, Staged.Kind.EDIT))
    }

    @Test
    fun `a field that held nothing says so as a word`() {
        assertEquals("nothing → 21 m", changeSaidOf(Changed("max_depth", null, "21 m"), Staged.Kind.EDIT))
        assertEquals("18 m → nothing", changeSaidOf(Changed("max_depth", "18 m", null), Staged.Kind.EDIT))
    }

    @Test
    fun `an item being added or deleted says one side only`() {
        val adding = Changed("name", null, "Blue Hole")
        assertEquals("Blue Hole", changeSaidOf(adding, Staged.Kind.ADD))
        val going = Changed("name", "Blue Hole", null)
        assertEquals("Blue Hole", changeSaidOf(going, Staged.Kind.DELETE))
    }

    @Test
    fun `a field path reads as where in the item it sits`() {
        assertEquals("Max depth", fieldSaidOf("max_depth"))
        assertEquals("Environment · Visibility", fieldSaidOf("environment.visibility"))
        assertEquals("Gas sources · G1 · Usage", fieldSaidOf("gas_sources.g1.usage"))
    }
}

class MovedSaidTest {

    @Test
    fun `a field the logbook still holds as it was says nothing`() {
        assertNull(movedSaidOf(Changed("rating", "6", "8", now = "6")))
    }

    @Test
    fun `a field that moved since it was staged says what it is now`() {
        assertEquals("it is 3 now", movedSaidOf(Changed("rating", "6", "8", now = "3")))
    }

    @Test
    fun `a field emptied since it was staged says so as a word`() {
        assertEquals("it is nothing now", movedSaidOf(Changed("rating", "6", "8", now = null)))
    }
}

class AppliedSaidTest {

    @Test
    fun `what landed is counted`() {
        assertEquals("Applied 2 items, 5 fields.", appliedSaidOf(Applied(2, 5, emptyList())))
        assertEquals("Applied 1 item, 1 field.", appliedSaidOf(Applied(1, 1, emptyList())))
    }

    @Test
    fun `what was left alone is named, in the reason the model gave`() {
        val refused = Refused("2026-06-01#0", "rating", "it was 6 when this was staged, and is 3 now")
        val said = appliedSaidOf(Applied(1, 2, listOf(refused)))
        assertTrue(said.startsWith("Applied 1 item, 2 fields."), said)
        assertTrue("1 change left alone" in said, said)
        assertTrue("Rating: it was 6 when this was staged, and is 3 now." in said, said)
    }

    @Test
    fun `a refusal about a whole item names no field`() {
        val refused = Refused("2026-06-01#0", "", "it has been deleted since")
        val said = appliedSaidOf(Applied(0, 0, listOf(refused)))
        assertTrue(said.startsWith("Nothing was applied."), said)
        assertTrue("it has been deleted since." in said, said)
    }
}

class StagedLineTest {

    @Test
    fun `the panel says how much is waiting to be looked at`() {
        assertEquals("1 item staged, waiting to be looked at.", stagedLineOf(1))
        assertEquals("4 items staged, waiting to be looked at.", stagedLineOf(4))
    }
}

/*
 * What a review says when the staging moved under the reader. `RECON-8`, `GUI-38`.
 */
class MovedListTest {

    @Test
    fun `a list that changed since it was drawn says so, and says nothing was applied`() {
        val said = appliedSaidOf(
            Applied(0, 0, listOf(Refused("", "", "what is staged has changed since it was shown"))),
        )
        assertTrue(said.startsWith("Nothing was applied."), said)
        assertTrue("changed since it was shown" in said, said)
    }
}
