package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Ascended
import yemoja.logic.Evaluated
import yemoja.logic.Types
import yemoja.logic.completeAscent
import yemoja.logic.evaluate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Starting a plan from what a reader typed. See ../../../../../../gui/doc.md — `GUI-41`.
 */
/** The descent rate a form reads where nobody chose one. */
private const val RATE = 18.0

class IntendedTest {

    private val typed = Intention(
        date = "2026-10-03",
        time = "14:30",
        depth = "30",
        minutes = "25",
        gas = "EAN32",
        gradientLow = "30",
        gradientHigh = "70",
    )

    /** The dive [intention] makes, read back as a logbook would read its file. */
    private fun read(intention: Intention): Item {
        val made = assertIs<Intended.Made>(intendedOf(intention, RATE))
        val text = Json.write(Stored.Members(made.fields))
        val set = LogbookReader.read(MemoryFileStore(mapOf("dive/d#0.json" to text)), Types.ALL)
        return set["d#0"]!!
    }

    private fun profileOf(dive: Item): Item {
        val held = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        return (held.getValue("a") as Element.Usable).value
    }

    @Test
    fun `what was typed becomes a dive holding one planned run to the bottom`() {
        val dive = read(typed)
        val plan = profileOf(dive)
        assertEquals(true, (plan.single<Boolean>("planned") as Result.Usable).value)
        assertEquals(true, (dive.single<Boolean>("planned") as Result.Usable).value, "so the dive is a plan")
        assertEquals(0.3, (plan.single<Double>("gradient_factor_low") as Result.Usable).value)
        assertEquals(0.7, (plan.single<Double>("gradient_factor_high") as Result.Usable).value)
        assertEquals("salt", (plan.single<String>("water_type") as Result.Usable).value)
    }

    @Test
    fun `the bottom is reached at the descent rate and held until the bottom time`() {
        val made = assertIs<Intended.Made>(intendedOf(typed, RATE))
        val flat = Json.write(Stored.Members(made.fields)).replace(Regex("\\s"), "")
        // Thirty metres at eighteen a minute is a hundred seconds; twenty-five minutes is 1500.
        assertTrue("[0,0.0]" in flat, flat)
        assertTrue("[100,30.0]" in flat, flat)
        assertTrue("[1500,30.0]" in flat, flat)
    }

    @Test
    fun `the model can answer for what was made, and give it a way up`() {
        val plan = profileOf(read(typed))
        assertIs<Evaluated.Done>(evaluate(plan), "a plan started here is one the model can answer for")
        val ascent = assertIs<Ascended.Done>(completeAscent(plan, 9.0, 3.0))
        assertTrue(ascent.depth.isNotEmpty(), "and the way up is asked for from the bottom")
        assertEquals(0.0, ascent.depth.last().second, "ending at the surface")
    }

    @Test
    fun `a time typed without seconds is taken as the minute`() {
        val made = assertIs<Intended.Made>(intendedOf(typed, RATE))
        assertTrue("14:30:00" in Json.write(Stored.Members(made.fields)))
    }

    @Test
    fun `a plan with no time of day is still a plan`() {
        assertIs<Intended.Made>(intendedOf(typed.copy(time = ""), RATE))
    }

    @Test
    fun `the gradient factors are asked for rather than assumed`() {
        val said = assertIs<Intended.Wrong>(intendedOf(typed.copy(gradientLow = ""), RATE)).reason
        assertTrue("low gradient factor" in said && "conservative" in said, said)
    }

    @Test
    fun `a gradient factor is a percentage, and the low one is not above the high one`() {
        // A proportion typed where a percentage was asked for is refused rather than read as 0.7%.
        val proportion = assertIs<Intended.Wrong>(intendedOf(typed.copy(gradientHigh = "0.7"), RATE)).reason
        assertTrue("from 1 to 100" in proportion, proportion)
        val above = assertIs<Intended.Wrong>(intendedOf(typed.copy(gradientLow = "80"), RATE)).reason
        assertTrue("above" in above, above)
        assertIs<Intended.Wrong>(intendedOf(typed.copy(gradientHigh = "120"), RATE))
        assertIs<Intended.Made>(intendedOf(typed.copy(gradientLow = "30%", gradientHigh = "70%"), RATE))
    }

    @Test
    fun `what will not read says so in the reader's terms`() {
        val date = assertIs<Intended.Wrong>(intendedOf(typed.copy(date = "03/10/2026"), RATE)).reason
        assertTrue("is not a date" in date && "2026-10-03" in date, date)
        val depth = assertIs<Intended.Wrong>(intendedOf(typed.copy(depth = "deep"), RATE)).reason
        assertTrue("depth is metres" in depth, depth)
        assertIs<Intended.Wrong>(intendedOf(typed.copy(gas = "nitrox for beginners"), RATE))
        assertIs<Intended.Wrong>(intendedOf(typed.copy(water = "en13319"), RATE))
    }

    @Test
    fun `a bottom time too short to reach the bottom in is refused with the arithmetic`() {
        val said = assertIs<Intended.Wrong>(intendedOf(typed.copy(depth = "60", minutes = "3"), RATE)).reason
        assertTrue("3:20 to reach" in said, said)
    }

    @Test
    fun `nothing typed asks for a date first`() {
        assertEquals("a plan needs a date", assertIs<Intended.Wrong>(intendedOf(Intention(), RATE)).reason)
    }
}
