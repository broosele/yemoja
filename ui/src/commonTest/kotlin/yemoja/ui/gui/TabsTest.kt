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
    fun `a tab with nothing behind it says what it will hold`() {
        for (tab in TABS.filter { it.shape == Shape.NONE }) {
            assertTrue(!tab.owed.isNullOrBlank(), "${tab.name} says nothing about being empty")
        }
    }

    @Test
    fun `a tab with something behind it has nothing to excuse`() {
        for (tab in TABS.filter { it.shape != Shape.NONE }) assertEquals(null, tab.owed)
    }

    @Test
    fun `the subjects are the ones the interface document settled`() {
        assertEquals(
            // Statistics is Home's, not a tab: the figures shown without asking and all of
            // them are the same subject at two depths.
            listOf("Home", "Dive", "Gear", "Community", "Location", "System", "Manuals"),
            TABS.map { it.name },
        )
        assertEquals(
            listOf("dive", "dive_trip"),
            TABS.first { it.name == "Dive" }.types.map { it.name },
        )
        assertEquals(
            listOf("region", "dive_site", "wreck"),
            TABS.first { it.name == "Location" }.types.map { it.name },
        )
    }
}
