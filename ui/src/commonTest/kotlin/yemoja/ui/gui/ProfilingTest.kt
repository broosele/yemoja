package yemoja.ui.gui

import yemoja.logic.Ascended
import yemoja.logic.Evaluated
import yemoja.logic.completeAscent
import yemoja.logic.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * A dive planned in the Calculations tab. See ../../../../../../gui/doc.md — `GUI-43`.
 */
private const val DESCENT = 18.0

private const val ASCENT = 9.0

/** A form with [levels] typed into it, on air at 20/80. */
private fun shaping(vararg levels: Pair<String, String>): Shaping {
    val shaping = Shaping()
    shaping.levels.clear()
    for ((minutes, depth) in levels) shaping.levels.add(Level(minutes, depth))
    shaping.gradientLow = "20"
    shaping.gradientHigh = "80"
    return shaping
}

/** The same, with a second cylinder of [gas] and the last level breathed on it. */
private fun switching(vararg levels: Pair<String, String>, gas: String = "EAN50"): Shaping {
    val shaping = shaping(*levels)
    shaping.gases.add(Breathed(gas = gas))
    shaping.levels[shaping.levels.lastIndex] = shaping.levels.last().copy(gas = 1)
    return shaping
}

private fun shaped(shaping: Shaping): Shaped = shapedOf(shaping, DESCENT, ASCENT)

class ShapedOfTest {

    @Test
    fun `a level is reached at the rate and held for the rest of its minutes`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("20" to "30"))).run
        // Thirty metres at eighteen a minute is a hundred seconds; twenty minutes is 1200.
        assertEquals(listOf(0 to 0.0, 100 to 30.0, 1200 to 30.0), run.depth)
        assertEquals(listOf(0 to "g1"), run.switches)
        assertEquals(0.2, run.gradientFactorLow)
        assertEquals(0.8, run.gradientFactorHigh)
    }

    @Test
    fun `a second level rises at the ascent rate and holds there`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("20" to "30", "10" to "21"))).run
        // Nine metres up at nine a minute is a minute, then nine minutes at twenty-one.
        assertEquals(listOf(0 to 0.0, 100 to 30.0, 1200 to 30.0, 1260 to 21.0, 1800 to 21.0), run.depth)
    }

    @Test
    fun `a level at the depth before it takes no travelling`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("20" to "30", "10" to "30"))).run
        assertEquals(listOf(0 to 0.0, 100 to 30.0, 1200 to 30.0, 1800 to 30.0), run.depth)
    }

    @Test
    fun `a level with less time than the travel takes is refused with the arithmetic`() {
        val said = assertIs<Shaped.Wrong>(shaped(shaping("1" to "30"))).reason
        assertTrue("1:40 to reach" in said, said)
    }

    @Test
    fun `an empty form waits, and an empty row among others is passed over`() {
        assertEquals(Shaped.Waiting, shaped(shaping("" to "")))
        val run = assertIs<Shaped.Ready>(shaped(shaping("20" to "30", "" to ""))).run
        assertEquals(1200, run.depth.last().first)
    }

    @Test
    fun `what will not read says which level it was`() {
        assertTrue("level 2" in assertIs<Shaped.Wrong>(shaped(shaping("20" to "30", "ten" to "21"))).reason)
        assertTrue("level 1" in assertIs<Shaped.Wrong>(shaped(shaping("20" to "deep"))).reason)
    }

    @Test
    fun `the gradient factors are asked for, as a plan on a dive asks for them`() {
        val without = shaping("20" to "30")
        without.gradientHigh = ""
        assertTrue("conservative" in assertIs<Shaped.Wrong>(shaped(without)).reason)
    }

    @Test
    fun `what is breathed is read as a gas, and the cylinder is taken where it is given`() {
        val nitrox = shaping("20" to "30")
        nitrox.gases[0] = Breathed(gas = "EAN32", sac = "18", size = "12", fill = "200")
        val run = assertIs<Shaped.Ready>(shaped(nitrox)).run
        assertEquals(32, run.sources.getValue("g1").gas.percentO2)
        assertEquals(18.0, run.sources.getValue("g1").sac)
        assertEquals(12.0, run.sources.getValue("g1").volume)
        assertEquals(200.0, run.sources.getValue("g1").fill)
        nitrox.gases[0] = Breathed(gas = "nonsense")
        assertTrue("Gas 1" in assertIs<Shaped.Wrong>(shaped(nitrox)).reason)
    }
}

class SwitchesOfTest {

    @Test
    fun `one cylinder is switched to at the start and never again`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("20" to "30", "10" to "20"))).run
        assertEquals(listOf(0 to "g1"), run.switches)
    }

    @Test
    fun `a level on another cylinder is switched to where that level begins`() {
        val run = assertIs<Shaped.Ready>(shaped(switching("20" to "30", "10" to "21"))).run
        // The first level ends at twenty minutes, which is where the second begins to be reached.
        assertEquals(listOf(0 to "g1", 1200 to "g2"), run.switches)
        assertEquals(2, run.sources.size)
        assertEquals(50, run.sources.getValue("g2").gas.percentO2)
    }

    @Test
    fun `the first switch is at nought whichever cylinder the first level names`() {
        val shaping = shaping("20" to "30")
        shaping.gases.add(Breathed(gas = "EAN32"))
        shaping.levels[0] = shaping.levels[0].copy(gas = 1)
        val run = assertIs<Shaped.Ready>(shaped(shaping)).run
        assertEquals(listOf(0 to "g2"), run.switches, "a run must say what it goes in on")
    }

    @Test
    fun `a level naming a cylinder that is no longer listed falls back rather than refusing`() {
        val shaping = switching("20" to "30", "10" to "21")
        shaping.gases.removeAt(1)
        val run = assertIs<Shaped.Ready>(shaped(shaping)).run
        assertEquals(listOf(0 to "g1"), run.switches)
    }

    @Test
    fun `a deco gas nobody switched to is switched to on the way up, by the model`() {
        // Listed and left: the bottom stays on air, and the ascent takes the richer mix where it
        // may. That is what a reader means by carrying a deco gas.
        val listed = shaping("25" to "40")
        listed.gases.add(Breathed(gas = "EAN50"))
        val run = assertIs<Shaped.Ready>(shaped(listed)).run
        assertEquals(listOf(0 to "g1"), run.switches, "nothing of theirs switches")
        val ascended = assertIs<Ascended.Done>(completeAscent(run, ASCENT, 3.0))
        assertEquals(listOf("g2"), ascended.switches.map { it.second }, "the model switches itself")
        assertIs<Evaluated.Done>(evaluate(withAscent(run, ascended)))
    }
}

class StopsOfTest {

    @Test
    fun `a dive that owes stops is given them, and the runtime counts the way up`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("25" to "40"))).run
        val ascended = assertIs<Ascended.Done>(completeAscent(run, ASCENT, 3.0))
        val stops = stopsOf(ascended)
        assertTrue(stops.isNotEmpty(), "twenty-five minutes at forty metres owes stops")
        assertTrue(stops.map { it.metres } == stops.map { it.metres }.sortedDescending(), "$stops")
        assertEquals(3.0, stops.last().metres, "the shallowest is where it was asked to be")
        val whole = withAscent(run, ascended)
        assertTrue(runtimeOf(whole) > runtimeOf(run), "the way up takes time")
        assertIs<Evaluated.Done>(evaluate(whole), "and the whole run is one the model answers for")
    }

    @Test
    fun `a dive within its limit owes no stop`() {
        val run = assertIs<Shaped.Ready>(shaped(shaping("12" to "18"))).run
        val ascended = assertIs<Ascended.Done>(completeAscent(run, ASCENT, 3.0))
        assertEquals(emptyList(), stopsOf(ascended))
    }

    @Test
    fun `the minutes a stop is held for are gathered into one stop`() {
        // What the model writes while it holds: a point a minute at each depth.
        val ascent = Ascended.Done(
            depth = listOf(0 to 9.0, 60 to 9.0, 120 to 9.0, 140 to 6.0, 200 to 6.0, 220 to 0.0),
            switches = emptyList(),
        )
        assertEquals(listOf(Stop(9.0, 120), Stop(6.0, 60)), stopsOf(ascent))
    }

    @Test
    fun `a stop reads as a depth and a time`() {
        assertEquals("6 m for 3 min", stopSaid(Stop(6.0, 180)))
        assertEquals("3 m for 1.5 min", stopSaid(Stop(3.0, 90)))
    }
}
