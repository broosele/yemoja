package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemWriter
import yemoja.data.KeyReference
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.Time
import yemoja.data.Units

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
 * The write naming a dive's one profile as its primary, where [write] gives the dive a second and
 * nothing names a primary yet, or null where it does not.
 *
 * A dive with one profile needs no name, and one with several and none named is worked from none
 * of them, so the moment a dive gains its second profile is the moment its first must be named:
 * the recording it was already worked from, whatever came after. [written] is every dive and field
 * the same change writes, so a change that names a primary itself is left to do so. `DATA-120`.
 */
internal fun primaryKeptBy(write: Change.Write, made: Result<Any>, written: Set<Pair<Item, String>>): Change.Write? {
    if (write.field != PROFILES || write.item.description != Types.DIVE) return null
    if (write.item to PRIMARY in written) return null
    if (write.item.single<KeyReference>(PRIMARY) !is Result.Absent) return null
    val had = (write.item.keyed<OwnedItem>(PROFILES) as? Result.Usable)?.value?.keys ?: return null
    val only = had.singleOrNull() ?: return null
    val given = ((made as? Result.Usable)?.value as? Map<*, *>)?.keys ?: return null
    if (given.size < 2 || only !in given) return null
    return Change.Write(write.item, PRIMARY, Stored.Leaf("*$only"))
}

/**
 * The cylinders of [dive]'s primary profile, which are the dive's until the user writes its own.
 *
 * A recording keeps what its computer said about the gas and a plan what it assumes, so the dive
 * reads whichever run it is worked from. Absent where that run keeps none, which is a dive logged
 * before recordings kept their own: those dives hold theirs as written. `RECON-7`.
 */
internal fun divesSources(dive: Item): Result<Any> {
    val profile = (primaryProfile(dive) as? Result.Usable)?.value ?: return Result.Absent
    val own = (profile.read(SOURCES) as? Result.Usable)?.value as? Map<*, *>
    return if (own.isNullOrEmpty()) Result.Absent else Result.Usable(own, Result.Origin.DERIVED)
}

/**
 * [write] as it should land, which is on the dive where it reaches the primary recording's own
 * cylinders, and [write] itself anywhere else.
 *
 * **A recording holds what its computer said**, so an edit to its cylinders is the user's and
 * belongs on the dive. The primary recording's cylinders are what the dive shows, and the edit
 * becomes the dive's own set: every cylinder copied with the edit laid on it. From then on the
 * dive's cylinders are the user's. A plan's are its own to change. `RECON-7`.
 */
internal fun overriding(write: Change.Write, plans: List<Item>): Change.Write {
    val (profile, key) = recordingSourceOf(write, plans) ?: return write
    val dive = (profile as? OwnedItem)?.parent ?: return write
    if ((primaryProfile(dive) as? Result.Usable)?.value !== profile) return write
    if (key == null) return Change.Write(dive, SOURCES, write.given)
    val written = LinkedHashMap(
        (ItemWriter.write(profile, Units.DEFAULT).members[SOURCES] as? Stored.Members)?.members.orEmpty(),
    )
    val entry = LinkedHashMap((written[key] as? Stored.Members)?.members.orEmpty())
    when (val given = write.given) {
        null -> entry.remove(write.field)
        is Stored -> entry[write.field] = given
        else -> entry[write.field] = Stored.Leaf(given)
    }
    written[key] = Stored.Members(entry)
    return Change.Write(dive, SOURCES, Stored.Members(written))
}

/**
 * Why [write] cannot land, where it edits the cylinders of a recording the dive is not worked
 * from; null otherwise.
 *
 * Those cylinders are not the dive's, so there is no dive's set for the edit to become. `RECON-7`.
 */
internal fun refusedCylinders(write: Change.Write, plans: List<Item>): String? {
    val (profile, _) = recordingSourceOf(write, plans) ?: return null
    val dive = (profile as? OwnedItem)?.parent ?: return null
    if ((primaryProfile(dive) as? Result.Usable)?.value === profile) return null
    return "a recording's cylinders are what its computer said, and this one is not the dive's primary"
}

/**
 * The recording and the key of its cylinder [write] reaches, the key absent where the write is to
 * the whole set; or null where it reaches no recording's cylinders.
 *
 * [plans] are the profiles the same change marks as planned: a plan is built field by field, and
 * its cylinders may be written before it says it is one.
 */
private fun recordingSourceOf(write: Change.Write, plans: List<Item>): Pair<Item, String?>? {
    val item = write.item
    if (item.description == PROFILE && write.field == SOURCES && isRecording(item, plans)) return item to null
    val profile = (item as? OwnedItem)?.parent ?: return null
    if (profile.description != PROFILE || !isRecording(profile, plans)) return null
    val entries = (profile.keyed<OwnedItem>(SOURCES) as? Result.Usable)?.value ?: return null
    val key = entries.entries.firstOrNull { (it.value as? Element.Usable)?.value === item }?.key ?: return null
    return profile to key
}

private fun isRecording(profile: Item, plans: List<Item>): Boolean =
    plans.none { it === profile } && (profile.single<Boolean>("planned") as? Result.Usable)?.value != true

private const val PROFILES = "profiles"
private const val SOURCES = "gas_sources"
private const val PRIMARY = "primary_profile"

/**
 * When a recording began, in the local time of the dive.
 *
 * `recorded_time_offset` is how far ahead of local time the computer's clock read, so it comes
 * off rather than going on: a computer still on a clock two hours ahead writes 7200, and a
 * recording saying 09:00 began at 07:00. The correction moves the date as well as the time where
 * it has to, so two minutes past midnight with two hours coming off is late the previous evening.
 * `LOGIC-32`.
 */
internal fun began(profile: Item): Moment? {
    val date = (profile.single<Date>("start_date") as? Result.Usable)?.value ?: return null
    val time = (profile.single<Time>("start_time") as? Result.Usable)?.value ?: Time(0, 0, 0)
    val offset = (profile.single<Double>("recorded_time_offset") as? Result.Usable)?.value ?: 0.0
    return Moment(date, time).plusSeconds(-offset.toLong())
}

/**
 * [moment], in the local time of [dive], on the one clock every dive shares.
 *
 * Local time less the dive's `time_zone_offset`, which is how far local time was ahead of GMT.
 * A dive saying nothing is taken to be on GMT, so two dives that both say nothing compare as
 * their local times do. `LOGIC-32`.
 */
internal fun absoluteOf(dive: Item, moment: Moment): Moment {
    val offset = (dive.single<Double>("time_zone_offset") as? Result.Usable)?.value ?: 0.0
    return moment.plusSeconds(-offset.toLong())
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
