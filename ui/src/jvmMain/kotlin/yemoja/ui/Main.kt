package yemoja.ui

import yemoja.ui.gui.gui
import yemoja.ui.tui.tui
import kotlin.system.exitProcess

/*
 * Where the application starts, and the only place that reads a command line.
 *
 * One command per front end, because ui/doc.md has several standing side by side and a user
 * should not have to know which executable each of them became.
 *
 * See ../../../../../doc.md.
 */

/** What each command is called, and what it does with what follows it. */
internal val COMMANDS: Map<String, Command> = mapOf(
    "tui" to Command(listOf("logbook folder"), "show a logbook in the terminal") {
        tui(it.single())
    },
    "gui" to Command(listOf("logbook folder"), "show a logbook in a window", least = 0) {
        gui(it.firstOrNull())
    },
)

/**
 * Command is one thing the application can be asked to do.
 *
 * [arguments] names what follows the command, one entry each. It is how many are wanted as well
 * as what they are called, so the count and the usage line cannot disagree.
 */
internal class Command(
    val arguments: List<String>,
    val summary: String,
    /** How few will do. Those past it are optional, and written in brackets. */
    val least: Int = arguments.size,
    val run: (List<String>) -> Int,
) {

    /** What to write after the command's name. */
    val written: String get() = arguments
        .mapIndexed { at, name -> if (at < least) "<$name>" else "[<$name>]" }
        .joinToString(" ")
}

/** Run the command named first, and exit with whatever it answers. */
fun main(args: Array<String>) {
    val command = COMMANDS[args.firstOrNull()]
    if (command == null) {
        // Naming no command at all is asking what there is, so it is not an error worth a
        // different message from naming one that does not exist.
        System.err.println(usage())
        exitProcess(2)
    }
    val rest = args.drop(1)
    if (rest.size !in command.least..command.arguments.size) {
        System.err.println("yemoja ${args[0]} ${command.written}")
        exitProcess(2)
    }
    exitProcess(command.run(rest))
}

/** Every command there is, one to a line, as a user should be shown them. */
internal fun usage(): String {
    val width = COMMANDS.keys.maxOf { it.length }
    val lines = COMMANDS.map { (name, command) ->
        "  yemoja ${name.padEnd(width)} ${command.written}    ${command.summary}"
    }
    return (listOf("usage:") + lines).joinToString("\n")
}
