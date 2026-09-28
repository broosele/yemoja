package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.Date
import yemoja.data.Moment
import yemoja.data.Time
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.logic.Change
import yemoja.logic.Import
import yemoja.logic.Meeting
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.divecomputer.DiveComputer
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.PI

/*
 * Reading a dive computer, worked out without a screen.
 *
 * See ../../../../../../gui/doc.md — `GUI-31`.
 */

/**
 * Stage is how far a download has got, which is the whole of what the screen shows of it.
 *
 * A download is the one thing the application does that takes minutes, so where it has got to
 * is a thing in its own right rather than a flag on a button. `GUI-31`.
 */
internal enum class Stage {
    /** Nothing is happening, and the button offers to start. */
    IDLE,

    /** Looking for what this machine can reach, which takes seconds. */
    LOOKING,

    /** More than one was found, and the reader says which. */
    CHOOSING,

    /** Being read, which takes minutes. */
    READING,

    /** Read, and what came of it is being shown. */
    DONE,
}

/** What to say while a download is at [stage], or absent where the screen says it another way. */
internal fun sayingOf(stage: Stage, computer: String?): String? = when (stage) {
    Stage.LOOKING -> "Looking for a dive computer…"
    Stage.CHOOSING -> "More than one dive computer is within reach. Which one should be read?"
    Stage.READING ->
        "Reading ${computer ?: "the dive computer"}. This can take several minutes, depending on " +
            "how many dives are on it."
    else -> null
}

/**
 * What to say of a download that finished: how many dives came across, or why none did.
 *
 * A refusal is the reason as the model gave it, since the model knows why and this does not.
 */
internal fun outcomeOf(outcome: Outcome, arrived: Int): String = when (outcome) {
    is Outcome.Refused -> outcome.reason
    is Outcome.Done -> when (arrived) {
        0 -> "The download finished, and there was no new dive on the computer."
        1 -> "The download finished, and 1 new dive is ready to review."
        else -> "The download finished, and $arrived new dives are ready to review."
    }
}

/**
 * What to say when a look found nothing, which is two different things.
 *
 * A computer that is switched off and a machine that cannot read one at all look identical from
 * here: no dive computer, either way. The remedies are nothing alike, so the words are not
 * either. `LOGIC-28`.
 */
internal fun emptyOf(readable: Boolean): String =
    if (readable) {
        "No dive computer was found. Switch yours on, put it into Bluetooth mode, and try again."
    } else {
        "This installation cannot read a dive computer. The libdivecomputer library is " +
            "missing, so no computer can be found whatever you do."
    }

/** How many dives a staged import holds, which is what a download brought. */
internal fun arrivedIn(import: Import?): Int =
    import?.staged?.logbook?.allOf(Types.DIVE)?.size ?: 0

/** What was taken in, and what stopped it where something did. */
internal class Taken(val many: Int, val refusal: String?)

/**
 * Arriving is one dive waiting to be taken in, and what is known about it.
 *
 * [onto] is the dive already held that this one appears to be, which is how two computers worn
 * on one dive are put back together: nobody is on two dives at once, so two recordings that
 * overlap in time are two recordings of one dive. [number] is what it would be called as a dive
 * of its own, and means nothing where it is merged onto one that is already numbered.
 */
internal class Arriving(
    val id: String,
    val said: String,
    val glued: Int,
    val onto: String?,
    val ontoSaid: String?,
    val number: Int,
    /** The site the device's own fix proposed, waiting to be answered. `LOGIC-18`. */
    val site: String?,
    /**
     * Whether something already in the logbook answers to this dive's id.
     *
     * Only another Yemoja logbook carries ids that mean the same on both sides, so this is
     * always false for a download. The arriving dive is laid over the held one member by
     * member, `RECON-6`, which is a thing to say before it happens. `GUI-33`.
     */
    val held: Boolean,
    /** Where the device said the dive was, written out, or absent where it said nothing. */
    val fix: String?,
    /** The sites already held that are nearest that fix, nearest first. */
    val nearby: List<Near>,
)

/** Near is a site already held, and how far it is from where a device said a dive was. */
internal class Near(val id: String, val name: String, val metres: Double)

/** [metres] as a reader would say it: to the metre up to a kilometre, then to a tenth of one. */
internal fun apartOf(metres: Double): String =
    if (metres < 1000.0) "${metres.toInt()} m" else "${(metres / 100.0).toInt() / 10.0} km"

/**
 * The sites in [into] nearest a fix, nearest first, at most [most] of them.
 *
 * **No radius.** One would be a number nobody can pick well: too small and the site is missed,
 * too large and the list is noise. A handful in order, each with its distance shown, lets the
 * reader judge what a threshold would have judged for them. `LOGIC-18`.
 */
internal fun nearestTo(into: ItemSet, latitude: Double, longitude: Double, most: Int = 3):
    List<Near> = into.allOf(Types.DIVE_SITE).mapNotNull { site ->
        val there = numberOf(site, "latitude") ?: return@mapNotNull null
        val across = numberOf(site, "longitude") ?: return@mapNotNull null
        val id = into.idOf(site as ReferenceableItem) ?: return@mapNotNull null
        Near(id, titleOf(site), metresApart(latitude, longitude, there, across))
    }.sortedBy { it.metres }.take(most)

/**
 * How far apart two positions are, in metres.
 *
 * Flat rather than spherical: what this decides is which site a dive was at, and over the few
 * kilometres that can mean the earth's curve is worth centimetres.
 */
internal fun metresApart(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double,
): Double {
    val north = (otherLatitude - latitude) * METRES_PER_DEGREE
    val east = (otherLongitude - longitude) * METRES_PER_DEGREE * cos(latitude * PI / 180.0)
    return sqrt(north * north + east * east)
}

/** A degree of latitude, in metres, which is near enough the same everywhere. */
private const val METRES_PER_DEGREE = 111_320.0

private fun numberOf(item: Item, field: String): Double? =
    (item.read(field) as? Result.Usable)?.value as? Double

/**
 * Every dive waiting to be taken in, each with what it appears to be and what it would be
 * called.
 *
 * Oldest first, and numbered in that order from [next] on. A logbook lists its dives newest
 * first, `DATA-89`, and that is the wrong way round here: a number counts up through a diver's
 * diving, so a list that hands them out has to run the same way.
 */
internal fun arrivingIn(import: Import, into: ItemSet, next: Int): List<Arriving> {
    val out = ArrayList<Arriving>()
    var number = next
    for (item in import.incoming.filter { it.description == Types.DIVE }.sortedBy(::whenOf)) {
        val id = import.staged.logbook.idOf(item) ?: continue
        val onto = if (import.asked(id)) import.proposal(id) else null
        val site = siteNamedBy(item)?.takeIf { import.staged.logbook[it] != null }
        // Where the dive was is a question only while the site it names has no name of its
        // own: that is a fix the device proposed and nobody has answered. A site arriving with
        // an imported dive is a site the dive already knows, and asking would be asking about
        // something the file settled. `LOGIC-18`, `GUI-33`.
        val at = site?.takeIf { unnamedIn(import, it) }?.let { import.staged.logbook[it] }
        val latitude = at?.let { numberOf(it, "latitude") }
        val longitude = at?.let { numberOf(it, "longitude") }
        val own = numberOf(item)
        out += Arriving(
            id = id,
            said = saidOf(item),
            glued = gluedIn(item),
            onto = onto,
            ontoSaid = onto?.let { into[it] }?.let { saidOf(it) },
            number = own ?: number,
            held = import.meeting(id) == Meeting.THE_SAME,
            site = site,
            fix = if (latitude == null || longitude == null) null else placeOf(latitude, longitude),
            nearby = if (latitude == null || longitude == null) {
                emptyList()
            } else {
                nearestTo(into, latitude, longitude)
            },
        )
        if (onto == null && own == null) number++
    }
    return out
}

/** Whether [id] is a site staged with no name, which is what a device's fix proposes. */
private fun unnamedIn(import: Import, id: String): Boolean {
    val site = import.staged.logbook[id] ?: return false
    return (site.single<String>("name") as? Result.Usable)?.value.isNullOrBlank()
}

/** The number a dive already carries, or absent where it carries none. */
private fun numberOf(dive: Item): Int? =
    ((dive.read("dive_number") as? Result.Usable)?.value as? Number)?.toInt()

/** When a dive was, as a number that sorts, or the end of time where it does not say. */
private fun whenOf(dive: Item): Long {
    val date = (dive.read("start_date") as? Result.Usable)?.value as? Date ?: return Long.MAX_VALUE
    val at = (dive.read("start_time") as? Result.Usable)?.value as? Time ?: Time(0, 0, 0)
    return Moment(date, at).epochSecond
}

/** A position written out, to four decimals, which is about ten metres. */
internal fun placeOf(latitude: Double, longitude: Double): String =
    "${rounded(latitude)}, ${rounded(longitude)}"

private fun rounded(degrees: Double): String {
    val whole = (degrees * 10_000).toLong()
    return "${whole / 10_000}.${(if (whole < 0) -whole else whole) % 10_000}"
}

/** Which site an arriving dive names, where it names one by id. */
private fun siteNamedBy(dive: Item): String? =
    ((dive.read("dive_site") as? Result.Usable)?.value as? Reference.Identified)?.id

/** A dive in a line: when it began, how long it ran, how deep it went, and what recorded it. */
internal fun saidOf(dive: Item): String {
    val began = (dive.read("start_time") as? Result.Usable)?.value?.toString()?.take(5)
    val lasted = ((dive.read("duration") as? Result.Usable)?.value as? Double)?.let { clockOf(it) }
    val deepest = (dive.read("max_depth") as? Result.Usable)?.value as? Double
    val deep = deepest?.let { displayOf(Types.DIVE.get("max_depth")!!, it) }
    val by = keyedOf(dive, "profiles").keys.map { prettyOf(it) }
    return (listOfNotNull(began, lasted, deep) + by).joinToString(" · ")
}

/** How many recordings were glued into this dive's longest profile, one where none were. */
internal fun gluedIn(dive: Item): Int =
    keyedOf(dive, "profiles").values.maxOfOrNull { tokensIn(it) } ?: 1

private fun tokensIn(profile: Item): Int =
    ((profile.list<String>("fingerprint") as? Result.Usable)?.value)?.size ?: 1

@Suppress("UNCHECKED_CAST")
private fun keyedOf(item: Item, field: String): Map<String, OwnedItem> {
    if (item.description[field] == null) return emptyMap()
    val read = (item.read(field) as? Result.Usable)?.value as? Map<String, Element<Any>>
    return read.orEmpty().mapNotNull { (key, element) ->
        ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
    }.toMap()
}

/**
 * The number the next dive taken in gets: one past the highest the logbook holds.
 *
 * A proposal like the rest of a downloaded dive. Nothing else knows what a diver counts, and
 * the one thing certain is that this dive follows the last. One, where nothing is numbered yet.
 */
internal fun nextNumberIn(set: ItemSet): Int {
    val highest = set.allOf(Types.DIVE).mapNotNull {
        ((it.read("dive_number") as? Result.Usable)?.value as? Number)?.toInt()
    }.maxOrNull()
    return (highest ?: 0) + 1
}

/**
 * Take [arriving] in, onto the dive it appears to be or as one of its own.
 *
 * A dive of its own is given its number and its primary recording on the way, written into the
 * staged dive so that what lands in the logbook carries them. The recording it arrived with is
 * the one it is worked from, there being only one; a second computer's comes later and does not
 * displace it. Merged onto a dive already held, it keeps that dive's number and its primary,
 * and is given neither.
 */
internal fun takeIn(import: Import, arriving: Arriving, onto: String?): Outcome {
    // A site the fix proposed goes first, so that the dive's reference lands on something —
    // but only where it was answered. An unanswered fix is a question nobody answered rather
    // than a site, and taking it in would put `unknown_dive_site` at a decimal position into
    // the logbook, which is exactly what `LOGIC-18` refuses.
    arriving.site?.let { site ->
        val staged = import.staged.logbook[site] ?: return@let
        val named = (staged.single<String>("name") as? Result.Usable)?.value
        if (named.isNullOrBlank()) {
            answered(import, arriving, null)
        } else {
            val done = import.insert(site)
            if (done is Outcome.Refused) return done
        }
    }
    if (onto == null) {
        import.staged.logbook[arriving.id]?.let { dive ->
            val writes = ArrayList<Change>()
            // Only what the dive does not already say. A computer numbers nothing, so a download
            // is always given one; another logbook's dive arrives with its own and keeps it, and
            // so does one that already names the recording it is worked from. `GUI-33`.
            if (numberOf(dive) == null) {
                writes += Change.Write(dive, "dive_number", Stored.Leaf(arriving.number))
            }
            if (dive.read("primary_profile") !is Result.Usable) {
                primaryFor(dive)?.let {
                    writes += Change.Write(dive, "primary_profile", Stored.Leaf("*$it"))
                }
            }
            if (writes.isNotEmpty()) {
                import.staged.change(Operation.IMPORT, *writes.toTypedArray())
            }
        }
    }
    return import.insert(arriving.id, onto)
}

/**
 * Take everything that arrived into the logbook, each as a dive of its own.
 *
 * **As each was proposed**, which is onto the dive it overlaps in time where there is one and
 * as a dive of its own where there is not. It is what pressing every row's own button would do,
 * offered once for a reader who has read the list and agrees with it.
 *
 * It stops at the first refusal and says so, leaving the rest staged: a reader who is told the
 * fourth would not go in can look at it, and what is left in the folder is what has not been
 * decided. `RECON-1`.
 */
internal fun takenIn(import: Import, into: ItemSet): Taken {
    var many = 0
    for (arriving in arrivingIn(import, into, nextNumberIn(into))) {
        when (val done = takeIn(import, arriving, arriving.onto)) {
            is Outcome.Refused -> return Taken(many, done.reason)
            is Outcome.Done -> many++
        }
    }
    return Taken(many, null)
}

/**
 * Answer where an arriving dive was made: at [site] already held, or nowhere.
 *
 * The site the fix proposed is dropped where the answer is somewhere else and no other dive
 * still waiting names it. A fix that nobody claims was a question, not an item.
 */
internal fun answered(import: Import, arriving: Arriving, site: String?): Outcome {
    val dive = import.staged.logbook[arriving.id]
        ?: return Outcome.Refused("that dive is no longer waiting")
    val done = import.staged.change(
        Operation.IMPORT,
        Change.Write(dive, "dive_site", site?.let { Stored.Leaf("@$it") }),
    )
    if (done is Outcome.Refused) return done
    val proposed = arriving.site
    if (proposed != null && proposed != site && !namedIn(import, proposed)) import.remove(proposed)
    return Outcome.Done()
}

/** Whether anything still waiting names the site called [id]. */
private fun namedIn(import: Import, id: String): Boolean =
    import.incoming.any { it.description == Types.DIVE && siteNamedBy(it) == id }

/**
 * Name the site a fix proposed, which is what makes it a site rather than a pair of numbers.
 *
 * A site has a name and a fix has none, so this is the answer *a new one* wants before it can
 * be taken in. `LOGIC-18`.
 */
internal fun namedAs(import: Import, arriving: Arriving, name: String): Outcome {
    val site = arriving.site?.let { import.staged.logbook[it] }
        ?: return Outcome.Refused("there is no site waiting to be named")
    return import.staged.change(
        Operation.IMPORT,
        Change.Write(site, "name", Stored.Leaf(name)),
    )
}

/**
 * The recording a dive is worked from, where it names none and has one to name.
 *
 * A downloaded dive has the one recording it arrived with, so which is primary is not a choice
 * anybody has to make. It is written rather than left to be worked out, since a dive read again
 * from a second computer would otherwise have nothing saying which of the two is meant.
 */
private fun primaryFor(dive: Item): String? {
    if (dive.read("primary_profile") is Result.Usable) return null
    return keyedOf(dive, "profiles").keys.firstOrNull()
}

/** What a computer is called in a list of them, which is the only thing one says of itself. */
internal fun namedOf(computer: DiveComputer): String = computer.name
