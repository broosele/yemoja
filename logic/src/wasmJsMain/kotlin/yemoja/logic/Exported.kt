package yemoja.logic

import yemoja.data.json.Json

/*
 * What a browser calls: one plan in as JSON text, one answer out as JSON text. `LOGIC-37`,
 * website/doc.md in the yemoja_website repository.
 *
 * The shape is `manual/planning-from-a-file.md`'s own: what `yemoja plan --json` answers with for
 * one case, so a file saved from the command line opens on the website and a file saved there
 * reads back with the command. No logbook is opened and none is needed, the planner being
 * arithmetic over what the text says.
 */

/**
 * [json] calculated, as JSON text: the whole answer `saidOf` writes, or a `refused` naming why
 * there is none.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun calculatePlan(json: String): String {
    val stored = try {
        Json.parse(json)
    } catch (error: Exception) {
        return Json.write(saidOf("plan", "not JSON: ${error.message}"))
    }
    val case = when (val read = casesOf(stored)) {
        is Read.Wrong -> return Json.write(saidOf("plan", read.reason))
        is Read.Cases -> read.cases.firstOrNull()
            ?: return Json.write(saidOf("plan", "no plan was given"))
    }
    return when (val answer = calculated(case.planned)) {
        is Calculated.Refused -> Json.write(saidOf(case.name, answer.reason))
        is Calculated.Done -> Json.write(saidOf(case.name, answer.schedule))
    }
}
