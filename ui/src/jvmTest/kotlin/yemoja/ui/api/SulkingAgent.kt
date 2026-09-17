package yemoja.ui.api

/*
 * An agent that starts, complains where a terminal would show it, and stops without speaking the
 * protocol. What an agent that is installed but not logged in does.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
object SulkingAgent {

    @JvmStatic
    fun main(arguments: Array<String>) {
        System.err.println("Error: not logged in.")
        System.err.println("Run claude /login in the terminal.")
        System.err.flush()
    }
}
