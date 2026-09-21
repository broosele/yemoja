package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.json.SettingsFile
import yemoja.logic.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What the user chooses, shown and read back. See ../../../../../../gui/doc.md — `GUI-42`.
 */
class EnteredTest {

    @Test
    fun `a gradient factor is typed as a percentage and held as a proportion`() {
        assertEquals(Entered.Value(0.3), chosenOf(Settings.DEFAULT_GF_LOW, "30"))
        assertEquals(Entered.Value(0.7), chosenOf(Settings.DEFAULT_GF_HIGH, "70 %"))
    }

    @Test
    fun `a proportion typed for a gradient factor is refused rather than read as a fraction of one`() {
        val said = assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_GF_LOW, "0.3")).reason
        assertTrue("percentage from 1 to 100" in said, said)
    }

    @Test
    fun `a rate is typed in its own unit and held as typed`() {
        assertEquals(Entered.Value(10.0), chosenOf(Settings.DEFAULT_ASCENT_RATE, "10"))
        assertEquals(Entered.Value(4.5), chosenOf(Settings.DEFAULT_LAST_STOP, "4.5"))
    }

    @Test
    fun `a rate outside what a setting can hold says the range and the unit`() {
        val said = assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_ASCENT_RATE, "90")).reason
        assertEquals("Ascent rate should be 1 to 30 m/min, but was 90", said)
    }

    @Test
    fun `an empty box takes the choice away`() {
        assertEquals(Entered.Value(null), chosenOf(Settings.DEFAULT_GF_LOW, "  "))
    }

    @Test
    fun `what is not a number says so`() {
        assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_DESCENT_RATE, "fast"))
    }
}

class ShownSettingTest {

    @Test
    fun `a value is shown as a person writes it`() {
        assertEquals("30", shownOf(Settings.DEFAULT_GF_LOW, 0.3))
        assertEquals("18", shownOf(Settings.DEFAULT_DESCENT_RATE, 18.0))
        assertEquals("4.5", shownOf(Settings.DEFAULT_LAST_STOP, 4.5))
        assertEquals("", shownOf(Settings.DEFAULT_GF_HIGH, null), "nothing chosen is an empty box")
    }

    @Test
    fun `where a value came from is said beside it`() {
        assertEquals("set on this device", answeredSaid(SettingsFile.LOCAL, Settings.DEFAULT_GF_LOW))
        assertEquals("set in this logbook", answeredSaid(SettingsFile.LOGBOOK, Settings.DEFAULT_GF_LOW))
        assertEquals("the default", answeredSaid(null, Settings.DEFAULT_ASCENT_RATE))
        assertEquals("not set", answeredSaid(null, Settings.DEFAULT_GF_LOW), "a factor has no default")
        assertEquals("not set", answeredSaid(null, Settings.AGENT_COMMAND), "and nor has the command")
        assertEquals("set on this device", answeredSaid(SettingsFile.LOCAL, Settings.AGENT_COMMAND))
    }

    @Test
    fun `plain numbers lose a trailing nought and keep a fraction`() {
        assertEquals("9", plain(9.0))
        assertEquals("9.5", plain(9.5))
        assertEquals("75", plain(0.75 * 100))
    }
}
