package yemoja.ui.api

/*
 * A program that starts and waits for its input without ever speaking the protocol, which is what
 * a terminal program does when it is mistaken for an agent: `claude` on its own reads a line and
 * answers a person, not a window.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
object SilentAgent {

    @JvmStatic
    fun main(arguments: Array<String>) {
        // Read everything and answer nothing, until the window gives up and closes the input.
        val reader = System.`in`.bufferedReader()
        while (reader.readLine() != null) Unit
    }
}
