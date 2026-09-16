package yemoja.ui.api

/*
 * What a test that waits on something outside itself uses instead of hanging the suite.
 *
 * See ../../../../../../api/doc.md.
 */

/**
 * Frees a test that is stuck after [seconds], and kills this process only if that does not work.
 *
 * A blocking read cannot be cancelled, so a test waiting on a socket, a pipe or another process
 * hangs the run rather than failing it — and a run stopped by hand leaves Gradle's record of it
 * half written, which breaks the next run of the whole module.
 *
 * **[release] is tried first**, and should close whatever the test is waiting on: a read of a
 * closed socket ends, the test fails with its own message, and every other test in the module
 * still runs. Halting is the last resort, because the whole module shares one process and taking
 * it down takes five hundred tests nobody was testing with it. Either way, what every thread was
 * doing is printed first.
 *
 * Interrupt what it answers when the test is done, or it will free a passing test.
 */
internal fun watchdog(what: String, seconds: Long = 60, release: () -> Unit = {}): Thread {
    val watching = Thread {
        try {
            Thread.sleep(seconds * 1000)
        } catch (woken: InterruptedException) {
            return@Thread
        }
        System.err.println("=== $what did not finish in $seconds seconds ===")
        for ((thread, stack) in Thread.getAllStackTraces()) {
            System.err.println("--- ${thread.name} (${thread.state})")
            for (at in stack.take(STACK)) System.err.println("      at $at")
        }
        System.err.flush()
        runCatching(release)
        try {
            Thread.sleep(LETTING_GO)
        } catch (woken: InterruptedException) {
            return@Thread
        }
        System.err.println("=== $what is still stuck, so this process is going down ===")
        System.err.flush()
        Runtime.getRuntime().halt(9)
    }
    watching.isDaemon = true
    watching.start()
    return watching
}

/** How long letting go of what a test waits on is given to work, in milliseconds. */
private const val LETTING_GO = 10_000L

/** How much of each thread's stack is printed. Enough to name what it is waiting on. */
private const val STACK = 12
