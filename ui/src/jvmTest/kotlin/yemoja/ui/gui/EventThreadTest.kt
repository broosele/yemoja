package yemoja.ui.gui

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * The thread an agent's tool calls are carried onto, which is the one the screens edit from.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
class EventThreadTest {

    @Test
    fun `a call carried onto the event thread runs on it`() {
        // Not Dispatchers.Main: nothing on this classpath provides one, and the first real agent
        // to call a tool met "Module with the Main dispatcher is missing" instead of an answer.
        val ran = runBlocking {
            withContext(eventThread()) { SwingUtilities.isEventDispatchThread() }
        }
        assertTrue(ran, "should run on Swing's event thread, where the Universe is edited")
    }
}
