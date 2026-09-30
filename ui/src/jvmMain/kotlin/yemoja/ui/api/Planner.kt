package yemoja.ui.api

import yemoja.data.Stored
import yemoja.data.json.Json
import java.io.File

/*
 * The planner as a command: a file of plans in, a table of what they come to out.
 *
 * See ../../../../../../ui/api/doc.md — `API-8`. No logbook is opened and none is needed: this is
 * `calculated` and nothing else, which is why it can be pointed at a hundred cases at once.
 */

/**
 * Calculates every plan in the file at [path] and writes what they come to.
 *
 * A table by default, one row a plan, because the question this answers is usually *how does this
 * compare*. `--json` gives every line of every dive instead, for a caller with something to diff.
 *
 * A plan that will not calculate takes a row of its own with the reason in it, rather than
 * stopping the run: one bad case in fifty should not cost the other forty-nine.
 */
internal fun planned(path: String, arguments: List<String>): Int {
    val asJson = "--json" in arguments
    val file = File(path)
    if (!file.isFile) {
        System.err.println("$path is not a file")
        return 2
    }
    val stored = try {
        Json.parse(file.readText())
    } catch (error: Exception) {
        System.err.println("$path is not JSON: ${error.message}")
        return 2
    }
    val cases = when (val read = casesOf(stored)) {
        is Read.Wrong -> {
            System.err.println(read.reason)
            return 2
        }
        is Read.Cases -> read.cases
    }
    val answers = cases.map { it to calculated(it.planned) }
    println(if (asJson) jsonOf(answers) else tableOf(answers.map { (case, answer) -> rowOf(case, answer) }))
    // A case that would not calculate is reported, not fatal: what it says is in the output.
    return 0
}

private fun rowOf(case: Case, answer: Calculated): List<String> = when (answer) {
    is Calculated.Done -> rowOf(case.name, answer.schedule)
    is Calculated.Refused -> rowOf(case.name, answer.reason)
}

private fun jsonOf(answers: List<Pair<Case, Calculated>>): String = Json.write(
    Stored.Elements(
        answers.map { (case, answer) ->
            when (answer) {
                is Calculated.Done -> saidOf(case.name, answer.schedule)
                is Calculated.Refused -> saidOf(case.name, answer.reason)
            }
        },
    ),
)
