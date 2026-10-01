package yemoja.logic

import yemoja.data.Date
import java.time.LocalDate

/** The machine's own calendar date, which `java.time` reads in the machine's own zone. */
actual fun today(): Date {
    val now = LocalDate.now()
    return Date(now.year, now.monthValue, now.dayOfMonth)
}
