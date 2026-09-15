package yemoja.logic.uddf

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.json.DiskFileStore
import yemoja.data.json.LogbookReader
import yemoja.logic.Types
import yemoja.logic.primaryProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The populated fixture written out as UDDF and read back in, which is what an export is for.
 *
 * On the JVM because it reads the fixture from disk. See ../../../../../../doc.md — the mapping
 * is logic/uddf.md.
 */
class ExportedFixtureTest {

    private val logbook: ItemSet =
        LogbookReader.read(DiskFileStore("../fixtures/cousteau"), Types.ALL)
    private val exported: Exported = Uddf.write(logbook)
    private val back: ItemSet = Uddf.read(exported.text)

    private fun text(item: Item, field: String): String? =
        (item.single<String>(field) as? Result.Usable)?.value

    private fun said(item: Item, field: String): String? =
        (item.read(field) as? Result.Usable)?.value?.toString()

    private fun owned(item: Item, field: String): OwnedItem? =
        (item.single<OwnedItem>(field) as? Result.Usable)?.value

    /** The name of what [field] on [item] points at, in the set [item] belongs to. */
    private fun pointed(item: Item, field: String, set: ItemSet): String? {
        val held = (item.single<Reference>(field) as? Result.Usable)?.value
        return (held as? Reference.Identified)?.id?.let { set[it] }?.let { text(it, "name") }
    }

    private fun pointedAll(item: Item, field: String, set: ItemSet): Set<String> =
        ((item.list<Reference>(field) as? Result.Usable)?.value.orEmpty())
            .mapNotNull { ((it as? Element.Usable)?.value as? Reference.Identified)?.id }
            .mapNotNull { set[it] }.mapNotNull { text(it, "name") }.toSet()

    /** The dive in [back] made at the moment [dive] was. */
    private fun returned(dive: Item): Item = back.allOf(Types.DIVE).single {
        said(it, "start_date") == said(dive, "start_date") &&
            said(it, "start_time") == said(dive, "start_time")
    }

    @Test
    fun `every item the importer reads comes back, and the libraries stay behind`() {
        for (type in listOf(Types.DIVE, Types.GEAR, Types.DIVE_SITE, Types.WRECK, Types.PERSON)) {
            assertEquals(logbook.allOf(type).size, back.allOf(type).size, type.name)
        }
        assertEquals(logbook.allOf(Types.OPERATOR).size, back.allOf(Types.OPERATOR).size)
        // A region and a certification are what a library supplies, and UDDF has no item for them.
        assertEquals(0, back.allOf(Types.REGION).size)
        assertEquals(0, back.allOf(Types.CERTIFICATION).size)
    }

    @Test
    fun `a trip dives name comes back by that name, holding the same dives`() {
        // A trip whose dives all name its legs is a container only, and its legs are what go out.
        for (trip in logbook.allOf(Types.DIVE_TRIP)) {
            val dives = namingTrip(logbook, trip)
            if (dives.isEmpty()) continue
            val name = text(trip, "name")
            val again = back.allOf(Types.DIVE_TRIP).single { text(it, "name") == name }
            assertEquals(dives, namingTrip(back, again), name)
        }
    }

    /** When each dive in [set] naming [trip] on its details was made. */
    private fun namingTrip(set: ItemSet, trip: Item): Set<String> =
        set.allOf(Types.DIVE).filter { dive ->
            val named = owned(dive, "details")?.single<Reference>("dive_trip")
            val id = ((named as? Result.Usable)?.value as? Reference.Identified)?.id
            id != null && set[id] === trip
        }.map { said(it, "start_date") + " " + said(it, "start_time") }.toSet()

    @Test
    fun `a dive comes back saying what it said`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            val again = returned(dive)
            val where = said(dive, "start_date") + " " + said(dive, "start_time")
            for (field in listOf("dive_number", "rating")) {
                assertEquals(said(dive, field), said(again, field), "$field on $where")
            }
            assertEquals(near(dive, "max_depth"), near(again, "max_depth"), "max_depth on $where")
            assertEquals(pointed(dive, "dive_site", logbook), pointed(again, "dive_site", back))
            // A person's name is written in parts there, so what a name overrode stays behind.
            assertEquals(people(dive, logbook), people(again, back), "buddies on $where")
            val details = owned(dive, "details")
            val detailsAgain = owned(again, "details")
            assertEquals(
                details?.let { pointed(it, "operator", logbook) },
                detailsAgain?.let { pointed(it, "operator", back) },
                "operator on $where",
            )
            val gear = owned(dive, "gear")
            val gearAgain = owned(again, "gear")
            assertEquals(
                gear?.let { pointedAll(it, "items", logbook) }.orEmpty(),
                gearAgain?.let { pointedAll(it, "items", back) }.orEmpty(),
                "gear on $where",
            )
        }
    }

    @Test
    fun `a dive's zone goes out after its time and comes back as its offset`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            assertEquals(said(dive, "time_zone_offset"), said(returned(dive), "time_zone_offset"))
        }
        assertTrue("T08:50:00+02:00<" in exported.text, "the fixture's North Sea dive")
    }

    @Test
    fun `a closed word leaves in UDDF's and comes back as this model's`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            val again = returned(dive)
            val current = owned(dive, "environment")?.let { text(it, "current") }
            assertEquals(current, owned(again, "environment")?.let { text(it, "current") })
            // `warm` and `too warm` both leave as `hot`, so what comes back is the table's word.
            val warmth = owned(dive, "gear")?.let { text(it, "temperature_evaluation") }
            val expected = warmth?.let { WARMTHS.theirs(it) }?.let { WARMTHS.ours(it) }
            assertEquals(expected, owned(again, "gear")?.let { text(it, "temperature_evaluation") })
        }
    }

    @Test
    fun `the primary recording's depths come back at every second they were measured`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            val profile = (primaryProfile(dive) as? Result.Usable)?.value ?: continue
            val depth = (profile.series<Double>("depth") as Result.Usable).value
            val again = (primaryProfile(returned(dive)) as Result.Usable).value
            val depthAgain = (again.series<Double>("depth") as Result.Usable).value
            for (at in 0..<depth.size) {
                val measured = (depth.valueAt(at) as Element.Usable).value as Double
                val read = valueAt(depthAgain, depth.secondAt(at))
                val second = depth.secondAt(at)
                assertTrue(read != null && abs(read - measured) < 0.01, "at $second s")
            }
        }
    }

    @Test
    fun `a cylinder's pressures come back against the gas they were breathed from`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            val profile = (primaryProfile(dive) as? Result.Usable)?.value ?: continue
            val sources = keyed(dive, "gas_sources")
            val again = returned(dive)
            val profileAgain = (primaryProfile(again) as Result.Usable).value
            assertEquals(
                pressuresByGas(profile, sources),
                pressuresByGas(profileAgain, keyed(again, "gas_sources")),
            )
        }
    }

    @Test
    fun `a stop, an alarm and the times after the dive come back with the recording`() {
        var seen = 0
        for (dive in logbook.allOf(Types.DIVE)) {
            val profile = (primaryProfile(dive) as? Result.Usable)?.value ?: continue
            val again = (primaryProfile(returned(dive)) as Result.Usable).value
            // A nought before the first stop says nothing a series of steps did not already say.
            assertEquals(steps(profile, "decostop").dropWhile { it.second == "0.0" },
                steps(again, "decostop"))
            assertEquals(steps(profile, "alarms"), steps(again, "alarms"))
            for (field in listOf("no_flight_time", "desaturation_time")) {
                assertEquals(said(profile, field), said(again, field), field)
            }
            if (steps(profile, "decostop").isNotEmpty()) seen++
        }
        assertTrue(seen > 0, "the fixture should carry a dive with stops")
    }

    @Test
    fun `a gas source keeps its pressures, and a switch still names the gas it switched to`() {
        for (dive in logbook.allOf(Types.DIVE)) {
            val sources = keyed(dive, "gas_sources")
            if (sources.isEmpty()) continue
            val again = keyed(returned(dive), "gas_sources")
            assertEquals(sources.size, again.size)
            val pressures = { held: Map<String, OwnedItem> ->
                held.values.map { number(it, "start_pressure") to number(it, "end_pressure") }
            }
            assertEquals(pressures(sources), pressures(again))
            val profile = (primaryProfile(dive) as? Result.Usable)?.value ?: continue
            val switches = switchedTo(profile, sources)
            val profileAgain = (primaryProfile(returned(dive)) as Result.Usable).value
            assertEquals(switches, switchedTo(profileAgain, again))
        }
    }

    @Test
    fun `the user is the owner and holds the gear, and nobody else does`() {
        assertTrue("<owner id=\"jacques_cousteau\">" in exported.text)
        assertEquals(1, Regex("<equipment>").findAll(exported.text).count())
    }

    @Test
    fun `no name in the document holds what an XML id may not`() {
        // A dive's id holds a `#` and begins with a digit, and neither is allowed there.
        val named = Regex(""" (?:id|ref)="([^"]*)"""")
        val ids = named.findAll(exported.text).map { it.groupValues[1] }
        assertTrue(ids.none { '#' in it || it.first().isDigit() }, ids.joinToString())
    }

    @Test
    fun `each group opens once, saying how long since the dive before or that there was none`() {
        val groups = Regex("<repetitiongroup ").findAll(exported.text).count()
        val opened = Regex("<surfaceintervalbeforedive>").findAll(exported.text).count()
        assertEquals(groups, opened)
    }

    @Test
    fun `a dive on two computers is counted, only its primary recording going out`() {
        val several = logbook.allOf(Types.DIVE).count { keyed(it, "profiles").size > 1 }
        assertEquals(several, exported.leftOut)
        assertEquals(logbook.allOf(Types.DIVE).size, exported.dives)
    }

    private fun people(dive: Item, set: ItemSet): Set<String> =
        ((dive.list<Reference>("buddies") as? Result.Usable)?.value.orEmpty())
            .mapNotNull { ((it as? Element.Usable)?.value as? Reference.Identified)?.id }
            .mapNotNull { set[it] }
            .map { listOf(text(it, "first_name"), text(it, "last_name")).joinToString(" ") }
            .toSet()

    private fun near(item: Item, field: String): Long? =
        (item.single<Double>(field) as? Result.Usable)?.value
            ?.let { kotlin.math.round(it * 10).toLong() }

    private fun number(item: Item, field: String): Long? =
        (item.single<Double>(field) as? Result.Usable)?.value
            ?.let { kotlin.math.round(it).toLong() }

    private fun keyed(item: Item, field: String): Map<String, OwnedItem> =
        ((item.keyed<OwnedItem>(field) as? Result.Usable)?.value.orEmpty())
            .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }
            .toMap()

    /** Each switch as its second and the gas of the source it names. */
    private fun switchedTo(
        profile: Item,
        sources: Map<String, OwnedItem>,
    ): List<Pair<Int, String?>> {
        val switches = (profile.series<KeyReference>("gas_switches") as? Result.Usable)?.value
            ?: return emptyList()
        return (0..<switches.size).map { at ->
            val key = ((switches.valueAt(at) as? Element.Usable)?.value as? KeyReference)?.key
            switches.secondAt(at) to key?.let { sources[it] }?.let { said(it, "gas_type") }
        }
    }

    /** Each sample of [field] on [profile], its second and what it said. */
    private fun steps(profile: Item, field: String): List<Pair<Int, String>> {
        val series = (profile.read(field) as? Result.Usable)?.value as? Series ?: return emptyList()
        return (0..<series.size).map { at ->
            series.secondAt(at) to ((series.valueAt(at) as? Element.Usable)?.value).toString()
        }
    }

    /** Each cylinder's pressures, whole bar by second, under the gas it held. */
    private fun pressuresByGas(
        profile: Item,
        sources: Map<String, OwnedItem>,
    ): Map<String?, List<Pair<Int, Long>>> {
        val held = (profile.keyedSeries<Double>("pressures") as? Result.Usable)?.value.orEmpty()
        return held.mapNotNull { (key, entry) ->
            val series = (entry as? Element.Usable)?.value ?: return@mapNotNull null
            val points = (0..<series.size).map { at ->
                val bar = (series.valueAt(at) as Element.Usable).value as Double
                series.secondAt(at) to kotlin.math.round(bar).toLong()
            }
            sources[key]?.let { said(it, "gas_type") } to points
        }.toMap()
    }

    private fun valueAt(series: Series, second: Int): Double? {
        for (at in 0..<series.size) {
            if (series.secondAt(at) != second) continue
            return (series.valueAt(at) as? Element.Usable)?.value as? Double
        }
        return null
    }
}
