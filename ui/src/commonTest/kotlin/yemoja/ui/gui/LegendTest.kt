package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * What the planner's graph names under it. `GUI-43`.
 */
class LegendTest {

    private fun line(label: String, main: Boolean = true, dotted: Boolean = false, empty: Boolean = false) =
        Line(label, if (empty) emptyList() else listOf(Point(0.0, 0.0), Point(1.0, 10.0)), main = main, dotted = dotted)

    @Test
    fun `each line drawn is named, in the order drawn, with how it is drawn`() {
        val depth = listOf(line("Depth"), line("Ceiling", main = false), line("Lost way up", main = false, dotted = true))
        val overlay = Overlay("pO₂", "bar", line("pO₂"))
        assertEquals(
            listOf(
                Drawn.DEPTH to "Depth",
                Drawn.THIN to "Ceiling",
                Drawn.WAY to "Lost way up, as the reserve is costed",
                Drawn.RIGHT to "pO₂, on the right axis",
            ),
            legendOf(depth, overlay),
        )
    }

    @Test
    fun `a line not drawn is not named`() {
        assertEquals(listOf(Drawn.DEPTH to "Depth"), legendOf(listOf(line("Depth"), line("Ceiling", main = false, empty = true)), null))
    }
}
