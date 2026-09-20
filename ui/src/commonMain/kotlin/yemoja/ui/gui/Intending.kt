package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.Time
import yemoja.data.ValueFormatException
import yemoja.logic.Ascended
import yemoja.logic.Change
import yemoja.logic.Outcome
import yemoja.logic.Settings
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.completeAscent
import kotlin.math.ceil
import kotlin.math.roundToLong

/*
 * Starting a plan from what a reader types, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-41`.
 */

/**
 * Intention is what a plan is started from, exactly as it was typed.
 *
 * Text rather than numbers throughout, because a form holds what a reader typed until they ask for
 * the plan, and what does not read is said then rather than refused keystroke by keystroke.
 *
 * Immutable.
 */
internal data class Intention(
    val date: String = "",
    val time: String = "",
    /** The depth of the bottom, in metres. */
    val depth: String = "",
    /** How long from leaving the surface to leaving the bottom, in minutes. */
    val minutes: String = "",
    /** What is breathed, as a diver writes it: air, EAN32, TMX 21/35. */
    val gas: String = "air",
    val water: String = SALT,
    /** The low gradient factor, as a percentage. */
    val gradientLow: String = "",
    /** The high gradient factor, as a percentage. */
    val gradientHigh: String = "",
)

/** Intended is a plan worked out from an [Intention], or why there is none. */
internal sealed class Intended {

    /**
     * Made is a dive holding one planned profile, as a file writes one.
     *
     * Its depths reach the bottom and stay there; the way up is the model's to add, since only the
     * model knows what the bottom cost. `LOGIC-35`.
     */
    class Made(val fields: Map<String, Stored>) : Intended()

    /** Wrong is a typed value that will not do, and what is wrong with it. */
    data class Wrong(val reason: String) : Intended()
}

/**
 * The dive [intention] starts, or what in it will not do.
 *
 * **The gradient factors are asked for, not assumed.** They say how conservative the plan is, and a
 * number the application chose on the reader's behalf is a safety decision nobody made. Until the
 * logbook's own defaults are read, a plan is started only from factors somebody typed.
 *
 * **The bottom time counts the descent**, which is how a diver means it: forty minutes at thirty
 * metres is forty minutes from leaving the surface. The descent is taken at [descentRate], in metres
 * a minute, which the form says beside the deed.
 */
internal fun intendedOf(intention: Intention, descentRate: Double): Intended {
    val date = readOrWrong { Date.parse(intention.date) } ?: return wrong(intention.date, "date")
    val time = if (intention.time.isBlank()) {
        null
    } else {
        readOrWrong { Time.parse(clockOf(intention.time)) } ?: return wrong(intention.time, "time")
    }
    val depth = intention.depth.trim().toDoubleOrNull()
    if (depth == null || depth <= 0) {
        return Intended.Wrong("a depth is metres, more than nought, and ${said(intention.depth)} is not")
    }
    val minutes = intention.minutes.trim().toDoubleOrNull()
    if (minutes == null || minutes <= 0) {
        return Intended.Wrong(
            "a bottom time is minutes, more than nought, and ${said(intention.minutes)} is not",
        )
    }
    val descent = ceil(depth / descentRate * SECONDS_IN_MINUTE).toLong()
    val bottom = (minutes * SECONDS_IN_MINUTE).roundToLong()
    if (bottom <= descent) {
        return Intended.Wrong(
            "at ${plain(descentRate)} m a minute the bottom takes ${clockSaid(descent)} to reach, " +
                "which a bottom time of ${intention.minutes.trim()} minutes does not leave room for",
        )
    }
    val gas = try {
        Gas.parse(intention.gas)
    } catch (refused: ValueFormatException) {
        return Intended.Wrong(refused.message ?: "${said(intention.gas)} is not a gas")
    } catch (refused: IllegalArgumentException) {
        return Intended.Wrong(refused.message ?: "${said(intention.gas)} is not a gas")
    }
    if (intention.water !in WATERS) {
        return Intended.Wrong("the water is salt or fresh, and ${said(intention.water)} is neither")
    }
    val low = percentOf(intention.gradientLow)
        ?: return Intended.Wrong(gradientWrong("low", intention.gradientLow))
    val high = percentOf(intention.gradientHigh)
        ?: return Intended.Wrong(gradientWrong("high", intention.gradientHigh))
    if (low > high) {
        return Intended.Wrong(
            "the low gradient factor should not be above the high one, but $low is above $high",
        )
    }

    val profile = linkedMapOf<String, Stored>(
        "planned" to Stored.Leaf(true),
        "start_date" to Stored.Leaf(date.toString()),
    )
    if (time != null) profile["start_time"] = Stored.Leaf(time.toString())
    profile["water_type"] = Stored.Leaf(intention.water)
    profile["deco_model"] = Stored.Leaf(MODEL)
    profile["gradient_factor_low"] = Stored.Leaf(low / PERCENT)
    profile["gradient_factor_high"] = Stored.Leaf(high / PERCENT)
    profile["depth"] = Stored.Elements(
        listOf(sample(0, 0.0), sample(descent, depth), sample(bottom, depth)),
    )
    profile["gas_switches"] = Stored.Elements(
        listOf(Stored.Elements(listOf(Stored.Leaf(0L), Stored.Leaf("*$SOURCE")))),
    )
    profile["gas_sources"] = Stored.Members(
        mapOf(
            SOURCE to Stored.Members(
                mapOf(
                    "gas_type" to Stored.Leaf(gas.toString()),
                    "usage" to Stored.Leaf("bottom"),
                ),
            ),
        ),
    )
    val dive = linkedMapOf<String, Stored>("start_date" to Stored.Leaf(date.toString()))
    if (time != null) dive["start_time"] = Stored.Leaf(time.toString())
    dive["primary_profile"] = Stored.Leaf("*$PROFILE")
    dive["profiles"] = Stored.Members(mapOf(PROFILE to Stored.Members(profile)))
    return Intended.Made(dive)
}

/**
 * The form as it opens on [today]: the day filled in, and the gradient factors [low] and [high] the
 * user chose, as proportions.
 *
 * Only what was chosen is filled. Where nobody chose factors the boxes stay empty, the application
 * choosing no conservatism on anybody's behalf. `GUI-41`.
 */
internal fun intentionOf(today: Date, low: Double?, high: Double?): Intention = Intention(
    date = today.toString(),
    gradientLow = shownOf(Settings.DEFAULT_GF_LOW, low),
    gradientHigh = shownOf(Settings.DEFAULT_GF_HIGH, high),
)

/** One sample of a series, as a file writes it: the second, then the value. */
private fun sample(second: Long, value: Double): Stored =
    Stored.Elements(listOf(Stored.Leaf(second), Stored.Leaf(value)))

/** A clock time as it is written, where a reader typed hours and minutes and left the seconds. */
private fun clockOf(typed: String): String =
    if (typed.trim().count { it == ':' } == 1) "${typed.trim()}:00" else typed

/** A value read, or absent where it would not read. */
private inline fun <T> readOrWrong(read: () -> T): T? = try {
    read()
} catch (refused: ValueFormatException) {
    null
} catch (refused: IllegalArgumentException) {
    null
}

/** What to say of a date or a time that will not read. */
private fun wrong(typed: String, what: String): Intended.Wrong = Intended.Wrong(
    if (typed.isBlank()) {
        "a plan needs a $what"
    } else {
        "${said(typed)} is not a $what, which is written ${if (what == "date") "2026-10-03" else "14:30"}"
    },
)

/**
 * A percentage from 1 to 100, or absent where [typed] is not one.
 *
 * Below one is refused rather than read. A factor of 0.7% is no plan anybody makes, and `0.7` is
 * what a reader who knows the file's own proportions types for seventy.
 */
private fun percentOf(typed: String): Double? =
    typed.trim().removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it >= 1 && it <= PERCENT }

/** What to say of a gradient factor that will not do. */
private fun gradientWrong(which: String, typed: String): String = if (typed.isBlank()) {
    "a plan needs its $which gradient factor, which says how conservative it is"
} else {
    "the $which gradient factor is a percentage from 1 to 100, and ${said(typed)} is not"
}

/** A length of time as a clock reads it, minutes and seconds. */
private fun clockSaid(seconds: Long): String =
    "${seconds / SECONDS_IN_MINUTE}:${(seconds % SECONDS_IN_MINUTE).toString().padStart(2, '0')}"

/** Something typed, quoted, or *nothing* where nothing was. */
private fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

private const val SECONDS_IN_MINUTE = 60L

private const val PERCENT = 100.0

/** The one model built, which a plan is worked out with. `LOGIC-3`. */
private const val MODEL = "buhlmann"

/** What the one profile of a new plan is keyed as. */
private const val PROFILE = "a"

/** What its one gas source is keyed as. */
private const val SOURCE = "g1"

/** Salt water, which is where most diving is and what the form starts on. */
internal const val SALT = "salt"

/** The waters a plan can be in, which leaves out a gauge's standard as nothing anybody dives in. */
internal val WATERS = listOf(SALT, "fresh")

/**
 * Planning is the form a plan is started from, while it is open.
 *
 * Not immutable.
 */
internal class Planning {
    var open: Boolean by mutableStateOf(false)
    var typed: Intention by mutableStateOf(Intention())
    var wrong: String? by mutableStateOf(null)

    /**
     * Opens the form on [today], the day a plan is most often made for, and on the gradient
     * factors the user chose in [settings] where they chose any. A form already open is left as it
     * was typed. `GUI-41`.
     */
    fun openOn(today: Date, settings: Settings) {
        if (!open) {
            typed = intentionOf(
                today,
                settings.number(Settings.DEFAULT_GF_LOW),
                settings.number(Settings.DEFAULT_GF_HIGH),
            )
            wrong = null
        }
        open = true
    }
}

/**
 * The plan form as a card in the place a dive is shown, titled as a new item's form is.
 *
 * What it makes is chosen in the table, so the reader lands on the plan with its ascent already
 * added. `GUI-41`.
 */
@Composable
internal fun PlanCard(universe: Universe, planning: Planning, changer: Changer, onMade: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "New plan",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = HALF),
        )
        HorizontalDivider(modifier = Modifier.padding(bottom = GAP))
        Planner(universe, planning, changer, onMade)
    }
}

/**
 * The form a plan is started from: a day, a depth and a time at it, what is breathed, and how
 * conservative to be.
 *
 * On the Dives tab, in the place a dive is shown, since a plan is a dive that has not happened.
 * What is made is a dive holding one planned run to the bottom, given its way up by the model at
 * once, and then chosen in the table, where everything else about a plan is edited. `GUI-41`.
 */
@Composable
internal fun Planner(
    universe: Universe?,
    planning: Planning,
    changer: Changer,
    onMade: (String) -> Unit,
) {
    if (universe == null || !planning.open) return
    val typed = planning.typed
    val chosen = universe.settings
    val descent = chosen.number(Settings.DEFAULT_DESCENT_RATE) ?: FALLBACK_DESCENT_RATE
    val ascent = chosen.number(Settings.DEFAULT_ASCENT_RATE) ?: FALLBACK_ASCENT_RATE
    val last = chosen.number(Settings.DEFAULT_LAST_STOP) ?: FALLBACK_LAST_STOP
    val put = { changed: Intention ->
        planning.typed = changed
        planning.wrong = null
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Asked("Date", typed.date, WIDE) { put(typed.copy(date = it)) }
            Asked("Time", typed.time, NARROW, hint = "14:30") { put(typed.copy(time = it)) }
            Asked("Depth", typed.depth, NARROW, after = "m") { put(typed.copy(depth = it)) }
            Asked("Bottom time", typed.minutes, NARROW, after = "min") {
                put(typed.copy(minutes = it))
            }
        }
        Row(
            modifier = Modifier.padding(top = HALF),
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Asked("Gas", typed.gas, NARROW) { put(typed.copy(gas = it)) }
            Text(
                text = "Water",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            for (water in WATERS) {
                val chosen = typed.water == water
                TextButton(onClick = { put(typed.copy(water = water)) }) {
                    Text(
                        text = water.replaceFirstChar { it.uppercase() },
                        color = if (chosen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
            Asked("GF low", typed.gradientLow, NARROW, after = "%") {
                put(typed.copy(gradientLow = it))
            }
            Asked("GF high", typed.gradientHigh, NARROW, after = "%") {
                put(typed.copy(gradientHigh = it))
            }
        }
        Aside(
            "Descending at ${plain(descent)} m a minute. The way up is added at ${plain(ascent)} " +
                "m a minute, with the shallowest stop at ${plain(last)} m. Change them in Settings.",
        )
        planning.wrong?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = GAP),
            )
        }
        Row(
            modifier = Modifier.padding(top = HALF),
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Button(
                onClick = {
                    when (val made = madeFrom(universe, typed, changer, descent, ascent, last)) {
                        is Planned.Made -> {
                            planning.open = false
                            planning.typed = Intention()
                            onMade(made.id)
                        }

                        is Planned.Wrong -> planning.wrong = made.reason
                    }
                },
            ) { Text("Create plan") }
            TextButton(onClick = { planning.open = false }) { Text("Cancel") }
        }
    }
}

/** One question on the form: what it asks, and the box it is answered in. */
@Composable
private fun Asked(
    label: String,
    value: String,
    width: Dp,
    hint: String = "",
    after: String = "",
    onChange: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HALF),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Box(modifier = Modifier.width(width)) {
            Compact(value = value, onChange = onChange, hint = hint, after = after)
        }
    }
}

/** What came of asking for a plan: the dive made, or why none was. */
private sealed class Planned {
    class Made(val id: String) : Planned()
    class Wrong(val reason: String) : Planned()
}

/**
 * Make the dive [typed] intends, and give it its way up.
 *
 * Two changes rather than one, because the way up is worked out from the plan as the logbook holds
 * it, and it holds nothing until the first is made. A plan whose way up the model will not give is
 * still made: it is opened, and its own view says why. `GUI-40`.
 */
private fun madeFrom(
    universe: Universe,
    typed: Intention,
    changer: Changer,
    descentRate: Double,
    ascentRate: Double,
    lastStop: Double,
): Planned {
    val intended = intendedOf(typed, descentRate)
    if (intended is Intended.Wrong) return Planned.Wrong(intended.reason)
    val fields = (intended as Intended.Made).fields
    val id = when (val made = changer.change(listOf(Change.Add(Types.DIVE, fields)))) {
        is Outcome.Refused -> return Planned.Wrong(made.reason)
        is Outcome.Done -> made.added.firstOrNull() ?: return Planned.Wrong("nothing was made")
    }
    val profile = universe.logbook[id]?.let { plannedRunOf(it) } ?: return Planned.Made(id)
    val ascent = completeAscent(profile, ascentRate, lastStop)
    if (ascent is Ascended.Done) changer.change(ascentWrittenTo(profile, ascent))
    return Planned.Made(id)
}

/** The one run a plan started here holds. */
private fun plannedRunOf(dive: Item): Item? {
    val held = (dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value ?: return null
    return (held[PROFILE] as? Element.Usable)?.value
}

/** How fast a plan descends where there is no logbook to ask. */
internal val FALLBACK_DESCENT_RATE: Double = Settings.DEFAULT_DESCENT_RATE.default!!

/** How wide a box is for a date. */
private val WIDE = 120.dp

/** How wide a box is for a number or a short word. */
private val NARROW = 80.dp
