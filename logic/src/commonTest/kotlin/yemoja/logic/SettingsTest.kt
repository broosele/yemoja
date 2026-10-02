package yemoja.logic

import yemoja.data.json.MemoryFileStore
import yemoja.data.json.SettingsFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What the user chose, answered from the first layer that has an answer. See ../../../../../doc.md,
 * and `DATA-9` for the layers.
 */
class SettingsTest {

    private fun settings(vararg files: Pair<String, String>): Pair<Settings, MemoryFileStore> {
        val store = MemoryFileStore(mapOf(*files))
        return Settings(store) to store
    }

    @Test
    fun `nothing chosen answers with the default`() {
        val (chosen, _) = settings()
        assertEquals(9.0, chosen.number(Settings.DEFAULT_ASCENT_RATE))
        assertEquals(18.0, chosen.number(Settings.DEFAULT_DESCENT_RATE))
        assertEquals(3.0, chosen.number(Settings.DEFAULT_LAST_STOP))
        assertNull(chosen.answeredBy(Settings.DEFAULT_ASCENT_RATE), "no file answered")
    }

    @Test
    fun `the gradient factors have no default, the application choosing no conservatism`() {
        val (chosen, _) = settings()
        assertNull(chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_LOW))
        assertNull(chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_HIGH))
    }

    @Test
    fun `the logbook's file answers before the default`() {
        val (chosen, _) = settings("settings.json" to """{"default_gradient_factor_low": 0.2, "default_ascent_rate": 10}""")
        assertEquals(0.2, chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_LOW))
        assertEquals(10.0, chosen.number(Settings.DEFAULT_ASCENT_RATE), "a whole number reads as a number")
        assertEquals(SettingsFile.LOGBOOK, chosen.answeredBy(Settings.DEFAULT_GRADIENT_FACTOR_LOW))
    }

    @Test
    fun `this device's file answers before the logbook's`() {
        val (chosen, _) = settings(
            "settings.json" to """{"default_gradient_factor_low": 0.2}""",
            "settings.local.json" to """{"default_gradient_factor_low": 0.4}""",
        )
        assertEquals(0.4, chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_LOW))
        assertEquals(SettingsFile.LOCAL, chosen.answeredBy(Settings.DEFAULT_GRADIENT_FACTOR_LOW))
    }

    @Test
    fun `a value that will not do is ignored, and the next layer answers`() {
        val (chosen, _) = settings(
            "settings.local.json" to """{"default_gradient_factor_low": "thirty", "default_ascent_rate": 900}""",
            "settings.json" to """{"default_gradient_factor_low": 0.3}""",
        )
        assertEquals(0.3, chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_LOW), "text where a number belongs")
        assertEquals(9.0, chosen.number(Settings.DEFAULT_ASCENT_RATE), "a number outside the range")
    }

    @Test
    fun `a choice nobody has made yet goes to the logbook's file, and so to every device`() {
        val (chosen, store) = settings()
        assertIs<Outcome.Done>(chosen.choose(Settings.DEFAULT_GRADIENT_FACTOR_HIGH, 0.7))
        assertEquals(0.7, chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_HIGH), "read back at once")
        assertTrue(store.isFile("settings.json"))
        assertTrue(!store.isFile("settings.local.json"))
    }

    @Test
    fun `a choice already kept on this device stays on this device`() {
        val (chosen, store) = settings("settings.local.json" to """{"default_gradient_factor_high": 0.8}""")
        chosen.choose(Settings.DEFAULT_GRADIENT_FACTOR_HIGH, 0.75)
        assertEquals(0.75, chosen.number(Settings.DEFAULT_GRADIENT_FACTOR_HIGH))
        assertTrue("0.75" in store.readText("settings.local.json"))
        assertTrue(!store.isFile("settings.json"), "the logbook's file is not touched")
    }

    @Test
    fun `taking a choice away falls back to what answers next`() {
        val (chosen, _) = settings("settings.json" to """{"default_ascent_rate": 10}""")
        chosen.choose(Settings.DEFAULT_ASCENT_RATE, null)
        assertEquals(9.0, chosen.number(Settings.DEFAULT_ASCENT_RATE))
    }

    @Test
    fun `a value outside the range is refused rather than written`() {
        val (chosen, store) = settings()
        val refused = assertIs<Outcome.Refused>(chosen.choose(Settings.DEFAULT_GRADIENT_FACTOR_LOW, 30.0))
        assertTrue("GF low should be 0.0 to 1.0, but was 30.0" in refused.reason, refused.reason)
        assertTrue(!store.isFile("settings.json"))
    }

    @Test
    fun `the agent's command is read from this device alone`() {
        val (chosen, _) = settings("settings.local.json" to """{"desktop_agent_command": "codex-acp"}""")
        assertEquals("codex-acp", chosen.text(Settings.AGENT_COMMAND))
        assertEquals(SettingsFile.LOCAL, chosen.answeredBy(Settings.AGENT_COMMAND))
    }

    @Test
    fun `a command found in the logbook's file is ignored, that file travelling`() {
        val (chosen, _) = settings("settings.json" to """{"desktop_agent_command": "C:/Users/x/agent.exe"}""")
        assertNull(chosen.text(Settings.AGENT_COMMAND))
        assertNull(chosen.answeredBy(Settings.AGENT_COMMAND))
    }

    @Test
    fun `a command chosen for the first time is kept on this device, never in the logbook`() {
        val (chosen, store) = settings()
        assertIs<Outcome.Done>(chosen.choose(Settings.AGENT_COMMAND, "  npx some-agent  "))
        assertEquals("npx some-agent", chosen.text(Settings.AGENT_COMMAND), "as typed, without the spaces")
        assertTrue(store.isFile("settings.local.json"))
        assertTrue(!store.isFile("settings.json"), "nothing written where it would travel")
    }

    @Test
    fun `a blank command takes the choice away`() {
        val (chosen, _) = settings("settings.local.json" to """{"desktop_agent_command": "codex-acp"}""")
        chosen.choose(Settings.AGENT_COMMAND, "   ")
        assertNull(chosen.text(Settings.AGENT_COMMAND))
    }

    @Test
    fun `the numbers the form offers come first, the gradient factors first among them`() {
        assertEquals(
            listOf(
                "default_gradient_factor_low",
                "default_gradient_factor_high",
                "default_descent_rate",
                "default_ascent_rate",
                "default_last_stop",
                "default_po2_max_bottom",
                "default_po2_max_deco",
                "default_po2_min",
                "default_safety_stop_depth",
                "default_safety_stop_duration",
                "default_stress_factor",
                "default_problem_solving_time",
            ),
            Settings.OFFERED.map { it.name },
        )
        assertEquals(listOf("default_water_type"), Settings.OFFERED_CHOICES.map { it.name })
    }

    @Test
    fun `a new plan starts from the limits, the safety stop and the water a plan usually has`() {
        val (chosen, _) = settings()
        assertEquals(1.4, chosen.number(Settings.DEFAULT_PO2_MAX_BOTTOM))
        assertEquals(1.6, chosen.number(Settings.DEFAULT_PO2_MAX_DECO))
        assertEquals(0.18, chosen.number(Settings.DEFAULT_PO2_MIN))
        assertEquals(6.0, chosen.number(Settings.DEFAULT_SAFETY_STOP_DEPTH))
        assertEquals(180.0, chosen.number(Settings.DEFAULT_SAFETY_STOP_DURATION))
        assertEquals("salt", chosen.choice(Settings.DEFAULT_WATER_TYPE))
    }

    @Test
    fun `a new plan's reserve spends two minutes at depth before the way up`() {
        val (chosen, _) = settings()
        assertEquals(120.0, chosen.number(Settings.DEFAULT_PROBLEM_SOLVING_TIME))
    }

    @Test
    fun `a new plan's divers sharing gas breathe at twice their usual rate`() {
        val (chosen, _) = settings()
        assertEquals(2.0, chosen.number(Settings.DEFAULT_STRESS_FACTOR))
    }

    @Test
    fun `a safety stop of nought minutes is a choice, being no safety stop`() {
        val (chosen, _) = settings()
        assertIs<Outcome.Done>(chosen.choose(Settings.DEFAULT_SAFETY_STOP_DURATION, 0.0))
        assertEquals(0.0, chosen.number(Settings.DEFAULT_SAFETY_STOP_DURATION))
    }

    @Test
    fun `a word of the set is answered from the first layer holding one`() {
        val (chosen, _) = settings(
            "settings.local.json" to """{"default_water_type": "brackish"}""",
            "settings.json" to """{"default_water_type": "fresh"}""",
        )
        assertEquals("fresh", chosen.choice(Settings.DEFAULT_WATER_TYPE), "a word outside the set is passed over")
        assertEquals(SettingsFile.LOGBOOK, chosen.answeredBy(Settings.DEFAULT_WATER_TYPE))
    }

    @Test
    fun `a word outside the set is refused rather than written`() {
        val (chosen, store) = settings()
        val refused = assertIs<Outcome.Refused>(chosen.choose(Settings.DEFAULT_WATER_TYPE, "en13319"))
        assertTrue("Water should be one of salt, fresh, but was en13319" in refused.reason, refused.reason)
        assertTrue(!store.isFile("settings.json"))
        assertIs<Outcome.Done>(chosen.choose(Settings.DEFAULT_WATER_TYPE, "fresh"))
        assertEquals("fresh", chosen.choice(Settings.DEFAULT_WATER_TYPE))
        chosen.choose(Settings.DEFAULT_WATER_TYPE, null)
        assertEquals("salt", chosen.choice(Settings.DEFAULT_WATER_TYPE), "taken away, the default answers")
    }
}
