package yemoja.ui.api

/*
 * An agent behind a launcher: this process starts the real one and waits, which is the shape of
 * `npx …` and of a `.cmd` on Windows. Stopping the launcher must stop what it started.
 *
 * It writes the child's process id where the test can read it, since a test cannot otherwise tell
 * one java from another.
 *
 * See ../../../../../../gui/doc.md — `GUI-38`.
 */
object LaunchingAgent {

    @JvmStatic
    fun main(arguments: Array<String>) {
        val running = ProcessHandle.current().info().command().orElse("java")
        // The streams are handed straight through, which is what makes this a launcher rather
        // than another party to the conversation: the window speaks to the agent, not to this.
        val started = ProcessBuilder(
            listOf(running, "-cp", System.getProperty("java.class.path"), FAKE),
        ).inheritIO().start()
        java.io.File(arguments[0]).writeText(started.pid().toString())
        started.waitFor()
    }
}

/** The agent this one launches, which is the one that answers without a model. */
private const val FAKE = "yemoja.ui.api.FakeAgent"
