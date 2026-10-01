package yemoja.logic

import yemoja.data.Date
import kotlin.js.ExperimentalWasmJsInterop

/** A browser's own clock, read as year, month and day in its local zone. */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "() => { const d = new Date(); " +
            "return [d.getFullYear(), d.getMonth() + 1, d.getDate()]; }",
)
private external fun jsToday(): JsArray<JsNumber>

/** The day the browser's own clock reads, in whatever zone it is set to. */
@OptIn(ExperimentalWasmJsInterop::class)
actual fun today(): Date {
    val parts = jsToday()
    return Date(parts[0]!!.toInt(), parts[1]!!.toInt(), parts[2]!!.toInt())
}
