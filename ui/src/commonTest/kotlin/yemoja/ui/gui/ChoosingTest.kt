package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.data.json.SettingsFile
import yemoja.logic.Outcome
import yemoja.logic.Settings
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.shownOf
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
        assertEquals(Entered.Value(0.3), chosenOf(Settings.DEFAULT_GRADIENT_FACTOR_LOW, "30"))
        assertEquals(Entered.Value(0.7), chosenOf(Settings.DEFAULT_GRADIENT_FACTOR_HIGH, "70 %"))
    }

    @Test
    fun `a proportion typed for a gradient factor is refused rather than read as a fraction of one`() {
        val said = assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_GRADIENT_FACTOR_LOW, "0.3")).reason
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
        assertEquals(Entered.Value(null), chosenOf(Settings.DEFAULT_GRADIENT_FACTOR_LOW, "  "))
    }

    @Test
    fun `what is not a number says so`() {
        assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_DESCENT_RATE, "fast"))
    }
}

class ShownSettingTest {

    /** Settings over an empty logbook, so every one of them is the application's own answer. */
    private fun settings(): Settings {
        val store = MemoryFileStore(emptyMap())
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null).settings
    }

    @Test
    fun `a value is shown as a person writes it`() {
        assertEquals("30", shownOf(Settings.DEFAULT_GRADIENT_FACTOR_LOW, 0.3))
        assertEquals("18", shownOf(Settings.DEFAULT_DESCENT_RATE, 18.0))
        assertEquals("4.5", shownOf(Settings.DEFAULT_LAST_STOP, 4.5))
        assertEquals("", shownOf(Settings.DEFAULT_GRADIENT_FACTOR_HIGH, null), "nothing chosen is an empty box")
    }

    @Test
    fun `a time is held in seconds and typed in minutes`() {
        assertEquals("3", shownOf(Settings.DEFAULT_SAFETY_STOP_DURATION, 180.0))
        assertEquals("1.5", shownOf(Settings.DEFAULT_PROBLEM_SOLVING_TIME, 90.0))
        assertEquals(Entered.Value(300.0), chosenOf(Settings.DEFAULT_SAFETY_STOP_DURATION, "5"))
        assertEquals(
            "Safety stop duration should be 0 to 15 min, but was 20",
            assertIs<Entered.Wrong>(chosenOf(Settings.DEFAULT_SAFETY_STOP_DURATION, "20")).reason,
        )
    }

    @Test
    fun `where a value came from is said beside it`() {
        assertEquals("set on this device", answeredSaid(SettingsFile.LOCAL, Settings.DEFAULT_GRADIENT_FACTOR_LOW))
        assertEquals("set in this logbook", answeredSaid(SettingsFile.LOGBOOK, Settings.DEFAULT_GRADIENT_FACTOR_LOW))
        assertEquals("the default", answeredSaid(null, Settings.DEFAULT_ASCENT_RATE))
        assertEquals("not set", answeredSaid(null, Settings.DEFAULT_GRADIENT_FACTOR_LOW), "a factor has no default")
        assertEquals("not set", answeredSaid(null, Settings.AGENT_COMMAND), "and nor has the command")
        assertEquals("set on this device", answeredSaid(SettingsFile.LOCAL, Settings.AGENT_COMMAND))
    }

    @Test
    fun `a default is left out of the boxes, so it shows through them`() {
        val settings = settings()
        val choosing = Choosing()
        choosing.fill(settings)
        assertEquals(
            "",
            choosing.typed[Settings.DEFAULT_ASCENT_RATE.name],
            "nobody chose it, so the box is empty and the default reads through it",
        )
        assertEquals(
            "",
            filledOf(settings, Settings.DEFAULT_ASCENT_RATE),
            "and a form that opens again fills it the same way",
        )
        assertEquals(
            false,
            Settings.DEFAULT_WATER_TYPE.name in choosing.typed,
            "a word nobody picked is not in the form either",
        )
    }

    @Test
    fun `a value somebody chose is in its box, and reads as written`() {
        val settings = settings()
        assertTrue(settings.choose(Settings.DEFAULT_ASCENT_RATE, 7.0) is Outcome.Done)
        val choosing = Choosing()
        choosing.fill(settings)
        assertEquals("7", choosing.typed[Settings.DEFAULT_ASCENT_RATE.name])
        assertEquals("7", filledOf(settings, Settings.DEFAULT_ASCENT_RATE))
        assertEquals(
            SettingsFile.LOGBOOK,
            settings.answeredBy(Settings.DEFAULT_ASCENT_RATE),
            "which is what the note beside the box reports",
        )
    }

    @Test
    fun `plain numbers lose a trailing nought and keep a fraction`() {
        assertEquals("9", plain(9.0))
        assertEquals("9.5", plain(9.5))
        assertEquals("75", plain(0.75 * 100))
    }
}
