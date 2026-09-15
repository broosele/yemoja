package yemoja.ui.gui

import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * What the application is divided into. See ../../../../../../gui/doc.md.
 */
class TabsTest {

    @Test
    fun `every type the model holds belongs to exactly one tab`() {
        // The check that matters: a type in no tab is unreachable and nothing else would say
        // so. Wrecks were exactly that until somebody noticed by eye.
        val placed = TABS.flatMap { it.types }
        assertEquals(Types.ALL.size, placed.size, "placed: ${placed.map { it.name }}")
        assertEquals(Types.ALL.toSet(), placed.toSet())
    }

    @Test
    fun `every tab has a screen behind it, none being a placeholder any more`() {
        assertEquals(TABS.size, TABS.map { it.shape }.distinct().size, "one screen apiece")
        assertTrue(TABS.all { it.name.isNotBlank() })
    }

    @Test
    fun `a tab about what a logbook holds is not offered without one`() {
        val without = TABS.filter { !it.needsLogbook }
        assertEquals(listOf("Home", "Manuals"), without.map { it.name })
        assertEquals(
            listOf("Dives", "Gear", "Community", "Locations"),
            TABS.filter { it.needsLogbook }.map { it.name },
        )
        assertEquals("Home", without.first().name, "a window with no logbook still opens on Home")
    }

    @Test
    fun `the subjects are the ones the interface document settled`() {
        assertEquals(
            // Statistics is Home's, not a tab: the figures shown without asking and all of
            // them are the same subject at two depths. What the application does to a logbook
            // as a whole is Home's too, which is why there is no System. `GUI-30`.
            listOf("Home", "Dives", "Gear", "Community", "Locations", "Manuals"),
            TABS.map { it.name },
        )
        assertEquals(
            listOf("dive", "dive_trip"),
            TABS.first { it.name == "Dives" }.types.map { it.name },
        )
        assertEquals(
            listOf("region", "dive_site", "wreck"),
            TABS.first { it.name == "Locations" }.types.map { it.name },
        )
    }
}
