package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What the Calculations tab works out. See ../../../../../../gui/doc.md — `GUI-43`.
 *
 * Fresh water and a round bar of atmosphere, as the logic layer's own tests use, so ten metres is
 * two bar and the arithmetic is checked by hand.
 */
private fun ambient(metres: Double): Double = 1.0 + metres / 10.0

private fun depthOf(bar: Double): Double = (bar - 1.0) * 10.0

private fun typed(vararg pairs: Pair<Figure, String>): Map<Figure, String> = mapOf(*pairs)

class SacSolvedTest {

    /** Twenty minutes at ten metres on a 12-litre cylinder from 200 to 120 bar: 960 litres at 2 bar. */
    private val dive = typed(
        Figure.DEPTH to "10", Figure.DURATION to "20", Figure.SIZE to "12",
        Figure.START to "200", Figure.END to "120", Figure.SAC to "24",
    )

    private fun value(unknown: Figure, of: Map<Figure, String> = dive): Double =
        assertIs<Answer.Value>(sacSolved(of, unknown, ::ambient, ::depthOf)).value

    @Test
    fun `each of the six is the one equation turned round`() {
        assertEquals(24.0, value(Figure.SAC), 1e-9)
        assertEquals(10.0, value(Figure.DEPTH), 1e-9)
        assertEquals(20.0, value(Figure.DURATION), 1e-9)
        assertEquals(12.0, value(Figure.SIZE), 1e-9)
        assertEquals(200.0, value(Figure.START), 1e-9)
        assertEquals(120.0, value(Figure.END), 1e-9)
    }

    @Test
    fun `the box being worked out is not read, whatever it says`() {
        assertEquals(24.0, value(Figure.SAC, dive + (Figure.SAC to "nonsense")), 1e-9)
    }

    @Test
    fun `an empty box means the form waits rather than complains`() {
        assertEquals(Answer.Waiting, sacSolved(dive - Figure.SIZE, Figure.SAC, ::ambient, ::depthOf))
    }

    @Test
    fun `what is not a number is said by its name`() {
        val said = assertIs<Answer.Wrong>(sacSolved(dive + (Figure.SIZE to "twelve"), Figure.SAC, ::ambient, ::depthOf))
        assertEquals("Cylinder size should be a number, but was \"twelve\"", said.reason)
    }

    @Test
    fun `a rate of nought or a duration of nought is refused where it would divide`() {
        assertIs<Answer.Wrong>(sacSolved(dive + (Figure.DURATION to "0"), Figure.SAC, ::ambient, ::depthOf))
        assertIs<Answer.Wrong>(sacSolved(dive + (Figure.SAC to "0"), Figure.DURATION, ::ambient, ::depthOf))
        assertIs<Answer.Wrong>(sacSolved(dive + (Figure.SIZE to "0"), Figure.END, ::ambient, ::depthOf))
    }

    @Test
    fun `an end pressure may come out below nought, which is the honest answer`() {
        // 24 L/min for 200 minutes at 2 bar is 9600 litres, which a 12-litre cylinder at 200 bar has not got.
        val over = dive + (Figure.DURATION to "200")
        assertTrue(value(Figure.END, over) < 0)
    }

    @Test
    fun `a depth less gas than the surface would take is refused rather than negative`() {
        val thin = dive + (Figure.START to "121")
        val said = assertIs<Answer.Wrong>(sacSolved(thin, Figure.DEPTH, ::ambient, ::depthOf))
        assertTrue("surface" in said.reason, said.reason)
    }
}

class NdlAskedTest {

    private fun asked(depth: String, gas: String = "air", high: String = "85"): Answer =
        ndlAsked(depth, gas, high, 18.0)

    @Test
    fun `a depth typed gives minutes, the descent counted`() {
        val answer = assertIs<Answer.Value>(asked("30"))
        assertTrue(answer.value > 30.0 / 18.0, "more than the descent alone: ${answer.value}")
        assertTrue(answer.value < 60.0, "and well under an hour at thirty metres: ${answer.value}")
    }

    @Test
    fun `an empty box waits`() {
        assertEquals(Answer.Waiting, asked(""))
        assertEquals(Answer.Waiting, asked("30", high = ""))
    }

    @Test
    fun `a factor is a percentage, and a gas is a gas`() {
        assertTrue("percentage" in assertIs<Answer.Wrong>(asked("30", high = "0.85")).reason)
        assertIs<Answer.Wrong>(asked("30", gas = "helium and hope"))
        assertIs<Answer.Wrong>(asked("deep"))
    }

    @Test
    fun `a depth with no limit says so in words`() {
        assertTrue("no limit" in assertIs<Answer.Wrong>(asked("3")).reason)
    }
}

class AnswerSaidTest {

    @Test
    fun `a value is said to a tenth with its unit, and the rest as it is`() {
        assertEquals("24 L/min", answerSaid(Answer.Value(24.0), "L/min"))
        assertEquals("17.3 min", answerSaid(Answer.Value(17.26), "min"))
        assertNull(answerSaid(Answer.Waiting, "m"))
        assertEquals("why", answerSaid(Answer.Wrong("why"), "m"))
    }
}
