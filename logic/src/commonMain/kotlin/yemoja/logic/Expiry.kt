package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.Result

/*
 * What has run out, which is all that is asked of a day outside the logbook.
 *
 * An insurance and a maintenance ask one question in two places, so the counting is here rather
 * than with either of them. What day it is comes from Today.kt, which is per platform.
 *
 * **Not in Today.kt**, which is where it would read best. That file declares `today()` as an
 * expect, its actual is a Today.kt of its own, and an expect alone generates no file class.
 * Putting ordinary functions beside it makes one, and the two files then claim the same
 * `TodayKt` name.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * How many days are left before [until] on [item], counted from the day the logbook was opened.
 *
 * Negative once the day has passed, which is what makes `days_left` and `expired` two readings
 * of one fact rather than two calculations.
 */
private fun daysLeft(item: Item, until: String): Long? {
    val ends = (item.single<Date>(until) as? Result.Usable)?.value ?: return null
    return today().daysUntil(ends)
}

/** [item]'s remaining days, or absent where it owes nothing by any date. */
internal fun remaining(item: Item, until: String): Result<Any> {
    val left = daysLeft(item, until) ?: return Result.Absent
    return Result.Usable(left.toInt(), Result.Origin.DERIVED)
}

/** Whether [until] on [item] has passed, which is the same fact read as a yes or a no. */
internal fun passed(item: Item, until: String): Result<Any> {
    val left = remaining(item, until)
    if (left !is Result.Usable) return left
    return Result.Usable((left.value as Int) < 0, Result.Origin.DERIVED)
}
