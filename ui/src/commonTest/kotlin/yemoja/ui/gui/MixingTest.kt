package yemoja.ui.gui

import yemoja.logic.Contents
import yemoja.logic.contentsOf
import yemoja.data.Gas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What the Gas mix form answers. See ../../../../../../gui/doc.md — `GUI-56`.
 *
 * The figures are the logic layer's, which tests them against the reference, `LOGIC-45`. What is
 * checked here is what the form reads from its boxes and how it says the answer.
 */

private fun rows(answer: MixAnswer): List<MixRow> = assertIs<MixAnswer.Rows>(answer).rows

private fun reason(answer: MixAnswer): String = assertIs<MixAnswer.Wrong>(answer).reason

class ToppedUpAskedTest {

    @Test
    fun `a half-used nitrox topped up with air says its mix to a tenth`() {
        val answer = rows(toppedUpAsked("EAN32", "100", "", "air", "200"))
        assertEquals(listOf(MixRow("Mix", "EAN27 (26.7 % O₂)")), answer)
    }

    @Test
    fun `a cylinder size adds the litres that went in`() {
        val answer = rows(toppedUpAsked("EAN32", "100", "12", "air", "200"))
        assertEquals("Added", answer[1].label)
        val litres = answer[1].text.removeSuffix(" L of AIR").toInt()
        // The reference gives 1128 L. By pressures alone it would be 1200.
        assertTrue(litres in 1120..1136, answer[1].text)
    }

    @Test
    fun `helium is said where there is some, and named as divers write it`() {
        val answer = rows(toppedUpAsked("TMX18/45", "80", "", "air", "200"))
        assertTrue(Regex("""TMX20/19 \(19\.\d % O₂, 18\.\d % He\)""").matches(answer.single().text), answer.single().text)
        assertEquals("Added", rows(toppedUpAsked("air", "50", "10", "he", "100"))[1].label)
        assertTrue(rows(toppedUpAsked("air", "50", "10", "Helium", "100"))[1].text.endsWith("L of He"))
    }

    @Test
    fun `an empty cylinder needs no start gas`() {
        assertEquals("AIR (21 % O₂)", rows(toppedUpAsked("", "0", "", "air", "200")).single().text)
    }

    @Test
    fun `the form waits while a box it needs is empty`() {
        assertIs<MixAnswer.Waiting>(toppedUpAsked("EAN32", "", "", "air", "200"))
        assertIs<MixAnswer.Waiting>(toppedUpAsked("", "100", "", "air", "200"))
        assertIs<MixAnswer.Waiting>(toppedUpAsked("EAN32", "100", "", "", "200"))
        assertIs<MixAnswer.Waiting>(toppedUpAsked("EAN32", "100", "", "air", ""))
    }

    @Test
    fun `a box that cannot be read says what it should hold`() {
        assertEquals(
            "End pressure should be at least the start pressure, 100 bar, not 90",
            reason(toppedUpAsked("EAN32", "100", "", "air", "90")),
        )
        assertEquals(
            "Start pressure should be 0 to 340 bar, not 400",
            reason(toppedUpAsked("EAN32", "400", "", "air", "200")),
        )
        assertEquals(
            "Start pressure should be a number, not \"full\"",
            reason(toppedUpAsked("EAN32", "full", "", "air", "200")),
        )
        assertEquals(
            "Cylinder size should be more than 0 L, not \"0\"",
            reason(toppedUpAsked("EAN32", "100", "0", "air", "200")),
        )
        assertEquals(gasWrong("fizz"), reason(toppedUpAsked("fizz", "100", "", "air", "200")))
        assertEquals("End pressure should be more than 0 bar", reason(toppedUpAsked("", "0", "", "air", "0")))
    }

    @Test
    fun `a comma is a decimal point, as a phone's number keys may give one`() {
        assertEquals(
            rows(toppedUpAsked("EAN32", "100.5", "", "air", "200")),
            rows(toppedUpAsked("EAN32", "100,5", "", "air", "200")),
        )
    }
}

class BlendAskedTest {

    @Test
    fun `a blend is one line a gas, each with the pressure to fill to`() {
        val answer = rows(blendAsked("", "0", "", "TMX18/45", "200", "O2, He, air"))
        assertEquals(listOf("Add O2", "Add He", "Add AIR"), answer.map { it.label })
        assertEquals("to 200 bar", answer.last().text)
        // By pressures alone the helium would end at 106.3 bar; the reference says 101.2.
        val helium = answer[1].text.removePrefix("to ").removeSuffix(" bar").toDouble()
        assertTrue(helium in 100.7..101.7, answer[1].text)
    }

    @Test
    fun `the order typed is the order filled`() {
        val answer = rows(blendAsked("", "0", "", "TMX18/45", "200", "He; O2; air"))
        assertEquals(listOf("Add He", "Add O2", "Add AIR"), answer.map { it.label })
    }

    @Test
    fun `a cylinder size adds the litres of each gas`() {
        val answer = rows(blendAsked("", "0", "12", "EAN32", "200", "O2, air"))
        assertTrue(Regex("""to 2\d(\.\d)? bar \(\d+ L\)""").matches(answer[0].text), answer[0].text)
        assertTrue(Regex("""to 200 bar \(\d+ L\)""").matches(answer[1].text), answer[1].text)
    }

    @Test
    fun `a cylinder that holds too much of something is drained first`() {
        val answer = rows(blendAsked("EAN50", "150", "", "EAN32", "200", "O2, air"))
        assertEquals(listOf("Drain", "Add AIR"), answer.map { it.label })
        assertTrue(answer[0].text.startsWith("to ") && answer[0].text.endsWith(" bar"), answer[0].text)
        val emptied = rows(blendAsked("TMX18/45", "60", "", "EAN32", "200", "O2, air"))
        assertEquals(MixRow("Drain", "until empty"), emptied.first())
    }

    @Test
    fun `a cylinder already holding the mix is added nothing`() {
        val answer = rows(blendAsked("EAN32", "200", "", "EAN32", "200", "O2, air"))
        assertEquals(listOf(MixRow("Add", "nothing, the cylinder holds this already")), answer)
        val over = rows(blendAsked("air", "230", "", "air", "200", "air"))
        assertEquals(listOf(MixRow("Drain", "to 200 bar"), MixRow("Add", "nothing")), over)
    }

    @Test
    fun `a mix the gases cannot make says so, naming them`() {
        assertEquals(
            "O2 and AIR cannot make TMX18/45, in an empty cylinder either",
            reason(blendAsked("", "0", "", "TMX18/45", "200", "O2, air")),
        )
    }

    @Test
    fun `the form waits while a box it needs is empty, and refuses a gas it cannot read`() {
        assertIs<MixAnswer.Waiting>(blendAsked("", "0", "", "EAN32", "200", ""))
        assertIs<MixAnswer.Waiting>(blendAsked("", "0", "", "", "200", "O2, air"))
        assertEquals(gasWrong("fizz"), reason(blendAsked("", "0", "", "EAN32", "200", "O2, fizz")))
    }
}

class MixSaidTest {

    @Test
    fun `a mix is its name and its fractions, helium only where there is some`() {
        assertEquals("EAN32 (32 % O₂)", mixSaid(contentsOf(Gas(32, 0), 200.0)))
        assertEquals("TMX18/45 (18 % O₂, 45 % He)", mixSaid(contentsOf(Gas(18, 45), 200.0)))
        assertEquals("He (0 % O₂, 100 % He)", mixSaid(contentsOf(Gas(0, 100), 200.0)))
        assertEquals(0.0, Contents.EMPTY.amount)
    }
}
