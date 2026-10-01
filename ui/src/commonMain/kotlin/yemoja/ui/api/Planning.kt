package yemoja.ui.api

import yemoja.data.Stored
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Planned
import yemoja.logic.Shaped
import yemoja.logic.Worked
import yemoja.logic.planKeyOf
import yemoja.logic.shapedOf
import yemoja.logic.workedOf
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.ui.gui.diveFieldsOf
import yemoja.ui.gui.newDiveOf
import yemoja.ui.gui.onDiveOf
import yemoja.ui.gui.planFieldsOf

/*
 * The dive planner as functions: one that answers, and one that writes.
 *
 * See ../../../../../../ui/api/doc.md — `API-2` and `API-7`. Both take the description `API-6`
 * settles, and both run the window's own calculation rather than one of their own. The answering
 * half — `Calculated`, `Schedule` and `calculated` — lives in the logic layer, `LOGIC-37`, being
 * pure arithmetic over a description; what is here is the half that writes.
 */

/**
 * Prepared is a plan ready to be written: the key it sits under and the fields it carries, or why it
 * is not ready.
 *
 * The one place those fields are built, so a plan the window saves, one this writes and one an
 * agent stages are the same plan. `API-9`.
 */
internal sealed class Prepared {

    class Plan(
        val key: String,
        /** The profile's own fields, which sit under [key] in the dive's `profiles`. */
        val fields: Map<String, Stored>,
        /** What the plan says about the dive itself: its start, and the dive it follows. */
        val dive: Map<String, Stored>,
    ) : Prepared()

    class Refused(val reason: String) : Prepared()
}

/**
 * [planned] as the fields a plan is written as, calculated first.
 *
 * [universe] is absent where there is no logbook, which a plan following an earlier run needs and
 * nothing else does.
 */
internal fun preparedOf(universe: Universe?, planned: Planned, name: String): Prepared {
    val ready = when (val shaped = shapedOf(planned, universe)) {
        is Shaped.Ready -> shaped
        is Shaped.Wrong -> return Prepared.Refused(shaped.reason)
        is Shaped.Waiting -> return Prepared.Refused("the plan has no lines")
    }
    val done = when (val worked = workedOf(ready)) {
        is Worked.Done -> worked
        is Worked.Refused -> return Prepared.Refused(worked.reason)
    }
    return Prepared.Plan(
        key = planKeyOf(name),
        fields = planFieldsOf(planned, ready.conditions, done.whole),
        dive = diveFieldsOf(planned, universe),
    )
}

/**
 * Saved is what writing a plan into a logbook came to: the dive it sits on, or why it does not.
 */
internal sealed class Saved {

    /** The dive's id, and the key the plan sits under within it. */
    class Done(val dive: String, val key: String) : Saved()

    class Refused(val reason: String) : Saved()
}

/**
 * Writes [planned] into [universe] as a plan, on the dive called [dive] or on a new one.
 *
 * **This is the half that changes data**, and the only one: everything else about a plan is
 * `calculated`. It is the same write the window's *Save as new dive* and *Attach to existing dive*
 * make, so a plan written here is a plan the window can open. `API-7`.
 *
 * [name] is what the plan is called within the dive, *Plan A* by default, and a plan that will not
 * calculate is refused rather than written: a schedule nobody can read is not worth keeping.
 */
internal fun saved(
    universe: Universe,
    planned: Planned,
    dive: String? = null,
    name: String = FIRST_PLAN,
): Saved {
    val made = when (val read = preparedOf(universe, planned, name)) {
        is Prepared.Refused -> return Saved.Refused(read.reason)
        is Prepared.Plan -> read
    }
    val held = dive?.let {
        universe.logbook[it] ?: return Saved.Refused("$it is not in this logbook")
    }
    if (held != null && held.description != Types.DIVE) {
        return Saved.Refused("$dive is a ${held.description.name} rather than a dive")
    }
    val key = made.key
    val changes = if (held == null) {
        newDiveOf(key, made.fields, made.dive)
    } else {
        onDiveOf(held, key, made.fields, made.dive)
    }
    return when (val outcome = universe.change(Operation.EDIT, *changes.toTypedArray())) {
        is Outcome.Refused -> Saved.Refused(outcome.reason)
        is Outcome.Done -> Saved.Done(
            dive = dive ?: outcome.added.firstOrNull() ?: return Saved.Refused("nothing was added"),
            key = key,
        )
    }
}

/** What a plan is called where nobody names it: the first letter free, `GUI-44`. */
internal const val FIRST_PLAN = "Plan A"
