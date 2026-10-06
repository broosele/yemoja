package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Where the System tab says the logbook was opened from. `GUI-30`.
 */
class LogbookAtTest {

    private val platform = Platform(
        manual = emptyList(),
        atlas = { error("the map is not read here") },
        open = {},
        today = { Date(2026, 10, 6) },
    )

    private fun universe(path: String?): Universe {
        val store = MemoryFileStore(emptyMap())
        return Universe(LogbookReader.read(store, Types.ALL), null, store, path)
    }

    @Test
    fun `a desktop says the folder the logbook was opened from`() {
        assertEquals("D:/logbooks/mine", platform.logbookAt(universe("D:/logbooks/mine")))
    }

    @Test
    fun `a logbook opened from no folder says nothing`() {
        assertNull(platform.logbookAt(universe(null)))
    }
}

/** What System's buttons say of themselves. `GUI-30`. */
class DeedTipTest {

    @Test
    fun `every deed explains itself`() {
        for (deed in Deed.entries) assertTrue(deed.tip.isNotBlank(), "${deed.label} says nothing")
    }

    @Test
    fun `the deeds that choose a logbook say it is a folder`() {
        for (deed in listOf(Deed.OPEN, Deed.NEW)) assertTrue("folder" in deed.tip, deed.tip)
    }
}

/** The planner's defaults in the settings, by the planner's own sections. `GUI-42`. */
class SettingsSectionsTest {

    @Test
    fun `every default the settings offer is in exactly one section`() {
        val placed = (PLANNER_FIRST + PLANNER_SECOND).flatMap { it.second }
        val offered = yemoja.logic.Settings.OFFERED + yemoja.logic.Settings.OFFERED_CHOICES +
            yemoja.logic.Settings.OFFERED_FLAGS
        assertEquals(offered.map { it.name }.sorted(), placed.map { it.name }.sorted())
    }

    @Test
    fun `the sections are the planner's, in its two columns`() {
        assertEquals(listOf("General", "Gas"), PLANNER_FIRST.map { it.first })
        assertEquals(listOf("Algorithm", "Stops", "Contingency"), PLANNER_SECOND.map { it.first })
    }
}
