package yemoja.ui.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNull

/*
 * A tooltip over words that can be selected. See ../../../../../../gui/doc.md — `GUI-36`.
 *
 * Drawn off-screen and pressed with a pointer, since the fault is in how a press is handled.
 */

@OptIn(ExperimentalComposeUiApi::class)
class ExplainedSelectionTest {

    @Test
    fun `pressing words while their tooltip shows does not throw`() {
        val thrown = AtomicReference<Throwable?>(null)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // A press is handled in a coroutine, and what it throws reaches this handler, not the test.
        Thread.setDefaultUncaughtExceptionHandler { _, error -> thrown.compareAndSet(null, error) }
        val scene = ImageComposeScene(400, 200, Density(1f)) {
            MaterialTheme {
                Box(Modifier.fillMaxSize().padding(40.dp)) {
                    Selectable { Explained("What this is") { Text("Pressed words") } }
                }
            }
        }
        try {
            var ms = 0L
            fun frame() { ms += 20; scene.render(ms * 1_000_000) }
            val on = Offset(50f, 48f)
            scene.sendPointerEvent(PointerEventType.Move, on, timeMillis = ms)
            repeat(40) { frame() }
            scene.sendPointerEvent(PointerEventType.Press, on, timeMillis = ms, buttons = PointerButtons(isPrimaryPressed = true))
            repeat(5) { frame() }
            scene.sendPointerEvent(PointerEventType.Release, on, timeMillis = ms, buttons = PointerButtons(isPrimaryPressed = false))
            repeat(5) { frame() }
        } catch (error: Throwable) {
            thrown.compareAndSet(null, error)
        } finally {
            scene.close()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertNull(thrown.get(), "a press should select or do nothing, but threw ${thrown.get()}")
    }
}
