package yemoja.logic

import yemoja.data.Stored
import yemoja.data.json.Json

/*
 * What a browser calls: a file of plans in as JSON text, the answers out as JSON text. `LOGIC-43`,
 * website/doc.md in the yemoja_website repository.
 *
 * The shape is `manual/planning-from-a-file.md`'s own: what `yemoja plan --json` answers with, so
 * a file saved from the command line opens on the website and a file saved there reads back with
 * the command. No logbook is opened and none is needed, the planner being arithmetic over what
 * the text says.
 */

/**
 * [json] calculated, as JSON text: an array of answers, one a case in file order, each the whole
 * answer `saidOf` writes or a `refused` naming why there is none.
 *
 * A file that will not read at all answers as an array of one refusal, so a caller reads one
 * shape however the asking went.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun calculatePlan(json: String): String {
    val stored = try {
        Json.parse(json)
    } catch (error: Exception) {
        return refusal("not JSON: ${error.message}")
    }
    val cases = when (val read = casesOf(stored)) {
        is Read.Wrong -> return refusal(read.reason)
        is Read.Cases -> read.cases
    }
    if (cases.isEmpty()) return refusal("no plan was given")
    val answers = calculatedAll(cases)
    return Json.write(
        Stored.Elements(
            cases.zip(answers).map { (case, answer) ->
                when (answer) {
                    is Calculated.Refused -> saidOf(case.name, answer.reason)
                    is Calculated.Done -> saidOf(case.name, answer.schedule)
                }
            },
        ),
    )
}

private fun refusal(reason: String): String = Json.write(Stored.Elements(listOf(saidOf("plan", reason))))
