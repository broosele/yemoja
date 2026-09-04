package yemoja.logic

import yemoja.data.Date

/*
 * What day it is, which four fields are counted against and nothing else uses.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * The day this is running on, in whatever zone the machine is set to.
 *
 * **Local, and the zone is not worth deciding.** Recordings are GMT — `DATA-58` — and a moment in
 * GMT is already tomorrow in Auckland, so the two disagree for a third of every day. That gap does
 * not matter here: `days_left` and `expired` are hints, and no renewal turns on which side of
 * midnight it was judged from.
 *
 * Per platform because a calendar day needs a zone, and the standard library carries none. Its
 * clock answers with an instant, which is the same moment everywhere and so not a day anywhere.
 * `LOGIC-9`.
 */
expect fun today(): Date
