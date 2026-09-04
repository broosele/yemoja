package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.Time

/*
 * What a dive's times and depths are worked out from: the recording a computer made.
 *
 * Every one of these walks the same way in — a dive to its primary profile, a profile to its
 * samples — so the walk lives here once and the derivations in Dive.kt say what they take
 * from it. It is here rather than there because a dive and the profiles it owns both use it.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * A derived value that could not be worked out, which `DATA-50` makes a fault not an absence.
 *
 * The raw form is an empty leaf. `Result.Unusable` carries what the source held so that a bad
 * value can be shown in place, and nothing was written here: the fault is in what this was
 * worked out from rather than in anything a file says.
 */
internal fun unusable(reason: String): Result.Unusable =
    Result.Unusable(Stored.Leaf(null), reason)

/**
 * Whichever recording a dive is worked from, or why there is none to work from.
 *
 * **A dive with one profile needs no `primary_profile`**, there being nothing to choose between,
 * and the ordinary dive is that one. With several and none named, nothing here guesses: the
 * manual is explicit that everything taken from a recording is then reported as something that
 * cannot be worked out rather than taken from whichever came first.
 */
internal fun primaryProfile(dive: Item): Result<Item> {
    val profiles = dive.keyed<OwnedItem>("profiles")
    if (profiles !is Result.Usable) return Result.Absent
    val held = profiles.value.mapNotNull { (key, entry) ->
        (entry as? Element.Usable)?.let { key to it.value }
    }
    if (held.isEmpty()) return Result.Absent
    val named = dive.single<KeyReference>("primary_profile")
    if (named !is Result.Usable) {
        val only = held.singleOrNull()
            ?: return unusable(
                "a dive with ${held.size} profiles says which is primary, and this one does not",
            )
        return Result.Usable(only.second, Result.Origin.DERIVED)
    }
    val key = named.value.key
    val chosen = held.firstOrNull { it.first == key }
        ?: return unusable("this dive has no profile called $key")
    return Result.Usable(chosen.second, Result.Origin.DERIVED)
}

/**
 * When a recording began, in GMT.
 *
 * `gmt_offset` is how far ahead of GMT the recording reads, so it comes off rather than going
 * on: a computer on Egyptian time writes 7200 and a recording saying 09:00 began at 07:00. The
 * correction moves the date as well as the time where it has to, so two minutes past midnight
 * with two hours coming off is late the previous evening.
 */
internal fun began(profile: Item): Moment? {
    val date = (profile.single<Date>("start_date") as? Result.Usable)?.value ?: return null
    val time = (profile.single<Time>("start_time") as? Result.Usable)?.value ?: Time(0, 0, 0)
    val offset = (profile.single<Double>("gmt_offset") as? Result.Usable)?.value ?: 0.0
    return Moment(date, time).plusSeconds(-offset.toLong())
}

/**
 * How long a recording ran, in seconds, or absent where it took no samples.
 *
 * The last sample of any series it holds. A time in a series is seconds from the start of the
 * recording whatever else the file declares — `DATA-58` — so the largest of them is how long
 * the computer was writing. Every series is asked rather than `depth` alone, because the recording
 * ended when the last thing it wrote was written.
 */
internal fun ranFor(profile: Item): Int? =
    seriesOf(profile).mapNotNull { it.lastSecond() }.maxOrNull()

/** Every plain series a profile holds, the keyed ones aside. */
private fun seriesOf(profile: Item): List<Series> =
    profile.description.fields
        .filter { it.cardinality == Cardinality.SERIES }
        .mapNotNull { (profile.read(it.name) as? Result.Usable)?.value as? Series }

/** When the last sample was taken, or absent where the series holds none. */
internal fun Series.lastSecond(): Int? = if (size == 0) null else secondAt(size - 1)

/** Every value a series holds that could be read, the unreadable ones passed over. */
internal fun Series.usable(): List<Any> =
    (0..<size).mapNotNull { (valueAt(it) as? Element.Usable)?.value }

/** [moment] as a date and a time, which is how the two fields are read off one answer. */
internal fun dateOf(moment: Moment): Result<Any> = Result.Usable(moment.date, Result.Origin.DERIVED)

internal fun timeOf(moment: Moment): Result<Any> = Result.Usable(moment.time, Result.Origin.DERIVED)

/**
 * What [take] makes of the dive's primary profile, or why it could not be reached.
 *
 * Absent and unusable both travel: a dive with no recording has nothing to say, and one whose
 * recording cannot be chosen says why rather than looking empty.
 */
internal fun fromProfile(dive: Item, take: (Item) -> Result<Any>): Result<Any> =
    when (val profile = primaryProfile(dive)) {
        is Result.Usable -> take(profile.value)
        is Result.Unusable -> Result.Unusable(profile.raw, profile.reason)
        Result.Absent -> Result.Absent
    }
