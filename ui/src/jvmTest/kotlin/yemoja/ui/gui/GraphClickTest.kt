package yemoja.ui.gui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/*
 * A click on a graph, and the box it opens. See ../../../../../../gui/doc.md — `GUI-4`.
 *
 * Drawn off-screen and clicked with a pointer, inside the selection every screen sits in.
 */

@OptIn(ExperimentalComposeUiApi::class)
class GraphClickTest {

    private val depth = listOf(Line("Depth", listOf(Point(0.0, 0.0), Point(2.0, 20.0), Point(10.0, 20.0), Point(12.0, 0.0))))
    private val overlays = listOf(Overlay("CNS", "%", Line("CNS", listOf(Point(0.0, 0.0), Point(12.0, 12.0)))))

    @Test
    fun `a click on the plot opens a box beside it, and a click on the box or a right click closes it`() {
        val scene = ImageComposeScene(600, 300, Density(1f)) {
            MaterialTheme { Selectable { Graphed(depth, overlays, emptyList(), planned = true, chosenFor = null) } }
        }
        try {
            var ms = 0L
            fun frame(): Image { ms += 20; return scene.render(ms * 1_000_000) }
            fun click(at: Offset, right: Boolean = false) {
                scene.sendPointerEvent(PointerEventType.Move, at, timeMillis = ms)
                frame()
                val pressed = if (right) PointerButtons(isSecondaryPressed = true) else PointerButtons(isPrimaryPressed = true)
                scene.sendPointerEvent(PointerEventType.Press, at, timeMillis = ms, buttons = pressed)
                frame()
                scene.sendPointerEvent(PointerEventType.Release, at, timeMillis = ms, buttons = PointerButtons())
                repeat(10) { frame() }
            }
            // A point a little right of the click, where the box opens.
            fun beside(): Int = Bitmap.makeFromImage(frame()).getColor(340, 150)

            val before = beside()
            click(Offset(300f, 150f))
            val open = beside()
            assertNotEquals(before, open, "the box covers the plot beside the click")
            click(Offset(340f, 150f))
            assertEquals(before, beside(), "and is gone once clicked")
            click(Offset(300f, 150f))
            assertNotEquals(before, beside())
            click(Offset(100f, 80f), right = true)
            assertEquals(before, beside(), "a right click on the plot closes it too")
        } finally {
            scene.close()
        }
    }
}
