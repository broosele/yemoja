package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Time
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What a download makes of what a device hands over.
 *
 * See ../../../../../../doc.md — the mapping is logic/divecomputer.md.
 */

/** A recording of one dive, with whatever [held] varies on top of a date and a time. */
private fun recorded(held: (Recording) -> Recording = { it }): Item {
    val base = Recording(began = Date(2026, 6, 21), at = Time(10, 5, 0))
    return Download.read(sequenceOf(held(base))).allOf(Types.DIVE).single()
}

private fun profile(dive: Item): Item {
    val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
    return (profiles.values.first() as Element.Usable).value
}

private fun keyed(dive: Item, field: String): List<String> {
    val held = (dive.keyed<OwnedItem>(field) as Result.Usable).value
    return held.keys.toList()
}

private fun series(item: Item, field: String): Series =
    (item.series<Double>(field) as Result.Usable).value

class DownloadedDiveTest {

    @Test
    fun `a dive is named for the day it was made on`() {
        val one = Recording(began = Date(2026, 6, 21), at = Time(10, 5, 0))
        val set = Download.read(sequenceOf(one))
        assertEquals(listOf("2026-06-21#0"), set.allOf(Types.DIVE).map { set.idOf(it) })
    }

    @Test
    fun `the figures a computer reports are written over its own recording`() {
        // A computer usually reports a better one than its profile, sampled every few seconds.
        val dive = recorded { it.copy(duration = 3600.0, maxDepth = 28.4) }
        val depth = dive.single<Double>("max_depth") as Result.Usable
        assertEquals(28.4, depth.value)
        assertEquals(Result.Origin.OVERRIDDEN, depth.origin)
    }

    @Test
    fun `a downloaded dive is a skeleton, and nothing else is created`() {
        // No gear, no person, no operator, no trip, no region. `LOGIC-20`.
        val one = Recording(began = Date(2026, 6, 21), at = Time(10, 5, 0))
        val set = Download.read(sequenceOf(one))
        assertEquals(1, Types.ALL.sumOf { set.allOf(it).size })
        val dive = set.allOf(Types.DIVE).single()
        assertEquals(Result.Absent, dive.read("dive_site"))
        assertEquals(Result.Absent, dive.read("buddies"))
        assertEquals(Result.Absent, dive.read("rating"))
    }

    @Test
    fun `the conditions are what a computer knows and no more`() {
        val dive = recorded {
            it.copy(coldest = 12.0, surface = 19.5, atmospheric = 1.013)
        }
        val where = (dive.single<OwnedItem>("environment") as Result.Usable).value
        assertEquals(12.0, (where.single<Double>("bottom_temperature") as Result.Usable).value)
        assertEquals(19.5, (where.single<Double>("surface_temperature") as Result.Usable).value)
        assertEquals(Result.Absent, where.read("current"), "a computer does not know it")
    }
}

class DownloadedProfileTest {

    @Test
    fun `the recording is keyed by the computer that made it`() {
        val dive = recorded { it.copy(computer = "Reef Computer") }
        assertEquals(listOf("reef_computer"), keyed(dive, "profiles"))
    }

    @Test
    fun `one that names no computer is keyed by its type's fallback`() {
        assertEquals(listOf("profile"), keyed(recorded { it.copy(maxDepth = 1.0) }
            .let { recorded { it.copy(samples = listOf(Recording.Sample(0, depth = 0.0))) } },
            "profiles"))
    }

    @Test
    fun `the density a computer was set to comes across, which a file cannot give`() {
        // `DATA-59`: a depth is a pressure divided by an assumed density, and UDDF discards it.
        val dive = recorded {
            it.copy(water = Recording.Water("salt", 1030.0))
        }
        val held = profile(dive)
        assertEquals("salt", (held.single<String>("water_type") as Result.Usable).value)
        assertEquals(1030.0, (held.single<Double>("density") as Result.Usable).value)
    }

    @Test
    fun `what the computer worked its stops out with is kept`() {
        // The one thing a download offers that LOGIC-10 should not drop. `LOGIC-17`.
        val dive = recorded {
            it.copy(model = Recording.DecoModel("buhlmann", 2, 0.3, 0.8))
        }
        val held = profile(dive)
        assertEquals("buhlmann", (held.single<String>("deco_model") as Result.Usable).value)
        assertEquals(0.3, (held.single<Double>("gradient_factor_low") as Result.Usable).value)
    }

    @Test
    fun `a sample contributes to each series it answers for and to no other`() {
        val dive = recorded {
            it.copy(
                samples = listOf(
                    Recording.Sample(0, depth = 0.0),
                    Recording.Sample(60, depth = 12.0, temperature = 14.0),
                    Recording.Sample(120, depth = 0.0),
                ),
            )
        }
        assertEquals(3, series(profile(dive), "depth").size)
        assertEquals(1, series(profile(dive), "temperature").size)
    }
}

class ThinnedTest {

    /** A dive of [many] samples descending in a straight line and coming back up. */
    private fun straight(many: Int): Item = recorded {
        it.copy(
            samples = (0..<many).map {
                Recording.Sample(it * 2, depth = if (it < many / 2) it * 0.5 else (many - it) * 0.5)
            },
        )
    }

    @Test
    fun `points lying on a line the others describe are dropped`() {
        // Eighteen hundred depths in an hour, and a profile drawn from those is no better than
        // one drawn from a couple of hundred. `LOGIC-15`.
        val kept = series(profile(straight(100)), "depth").size
        assertTrue(kept < 10, "a straight descent and ascent needs three points, and kept $kept")
    }

    @Test
    fun `the first and the last are always kept`() {
        val depth = series(profile(straight(100)), "depth")
        assertEquals(0, depth.secondAt(0))
        assertEquals(198, depth.secondAt(depth.size - 1))
    }

    @Test
    fun `what the thinning cost is written beside the series`() {
        // A missing figure claims nothing: it does not mean the series was left alone, it means
        // nobody recorded what was done. `LOGIC-15`.
        val held = (profile(straight(100)).single<OwnedItem>("tolerances") as Result.Usable).value
        assertEquals(0.1, (held.single<Double>("depth") as Result.Usable).value)
    }

    @Test
    fun `a series nothing was dropped from claims nothing`() {
        val dive = recorded {
            it.copy(samples = listOf(Recording.Sample(0, depth = 0.0)))
        }
        assertEquals(Result.Absent, profile(dive).read("tolerances"))
    }

    @Test
    fun `a point far off the line survives`() {
        val dive = recorded {
            it.copy(
                samples = listOf(
                    Recording.Sample(0, depth = 0.0),
                    Recording.Sample(60, depth = 20.0),
                    Recording.Sample(120, depth = 0.0),
                ),
            )
        }
        assertEquals(3, series(profile(dive), "depth").size)
    }
}

class DownloadedGasTest {

    private val gassed = recorded {
        it.copy(
            gases = listOf(
                Recording.GasSource(
                    "EAN32", volume = 12.0, startPressure = 200.0, endPressure = 50.0,
                ),
                Recording.GasSource("air", volume = 11.0),
            ),
            samples = listOf(
                Recording.Sample(0, pressures = mapOf(0 to 200.0), gas = 0),
                Recording.Sample(600, pressures = mapOf(0 to 120.0)),
                Recording.Sample(1200, pressures = mapOf(0 to 50.0), gas = 1),
            ),
        )
    }

    @Test
    fun `a tank and the mix it carried are one gas source`() {
        // Two arrays there, one collection here: a gas and its cylinder are one thing. `LOGIC-12`.
        assertEquals(listOf("tank_1", "tank_2"), keyed(gassed, "gas_sources"))
    }

    @Test
    fun `a tank is not a cylinder, so the volume is written and nothing is named`() {
        val sources = (gassed.keyed<OwnedItem>("gas_sources") as Result.Usable).value
        val first = (sources.values.first() as Element.Usable).value
        assertEquals(12.0, (first.single<Double>("volume") as Result.Usable).value)
        assertEquals(Result.Absent, first.read("cylinder"), "a tank has no identity of its own")
    }

    @Test
    fun `pressures are keyed by the gas source rather than by a tank`() {
        val held = (profile(gassed).keyedSeries<Double>("pressures") as Result.Usable).value
        assertEquals(listOf("tank_1"), held.keys.toList())
    }

    @Test
    fun `a gas switch names the source that was switched to`() {
        val switches = (profile(gassed).series<KeyReference>("gas_switches") as Result.Usable).value
        assertEquals(2, switches.size)
        val second = (switches.valueAt(1) as Element.Usable).value as KeyReference
        assertEquals("tank_2", second.key)
    }

    @Test
    fun `usage is not read, no computer recording what a cylinder was for`() {
        val sources = (gassed.keyed<OwnedItem>("gas_sources") as Result.Usable).value
        val first = (sources.values.first() as Element.Usable).value
        assertEquals(Result.Absent, first.read("usage"))
    }
}

class DownloadedAlarmTest {

    @Test
    fun `an alarm becomes one word at the instant it began`() {
        val dive = recorded {
            it.copy(
                samples = listOf(
                    Recording.Sample(0, depth = 0.0),
                    Recording.Sample(300, alarms = listOf("ascent")),
                ),
            )
        }
        val alarms = (profile(dive).series<String>("alarms") as Result.Usable).value
        assertEquals(1, alarms.size)
        assertEquals(300, alarms.secondAt(0))
        assertEquals("ascent", (alarms.valueAt(0) as Element.Usable).value)
    }

    @Test
    fun `a recording with no alarms holds none`() {
        assertNull((profile(recorded {
            it.copy(samples = listOf(Recording.Sample(0, depth = 0.0)))
        }).read("alarms") as? Result.Usable)?.value)
    }
}

/** What the device knows a recording by, which is how a later download says where to stop. */
class FingerprintTest {

    private fun of(vararg dived: Recording): yemoja.data.ItemSet = Download.read(dived.asSequence())

    private fun one(computer: String, day: Int, held: String?) = Recording(
        computer = computer,
        fingerprints = listOfNotNull(held),
        began = Date(2026, 6, day),
        at = Time(10, 5, 0),
    )

    @Test
    fun `it lands on the profile, beside what recorded it`() {
        val dive = recorded { it.copy(computer = "Reef", fingerprints = listOf("a1b2")) }
        val held = (profile(dive).list<String>("fingerprint") as Result.Usable).value
        assertEquals(listOf("a1b2"), held.map { (it as Element.Usable).value })
    }

    @Test
    fun `a recording that carries none says none`() {
        assertEquals(Result.Absent, profile(recorded { it.copy(computer = "Reef") })
            .read("fingerprint"))
    }

    @Test
    fun `the one to resume from is the newest that computer made`() {
        val set = of(one("Reef", 20, "aa"), one("Reef", 22, "bb"), one("Reef", 21, "cc"))
        assertEquals("bb", Download.after(set, "reef"))
    }

    @Test
    fun `another computer's is not the one to resume from`() {
        // Two computers worn on one trip are two chains, and each says where its own got to.
        val set = of(one("Reef", 22, "bb"), one("Other", 23, "zz"))
        assertEquals("bb", Download.after(set, "reef"))
    }

    @Test
    fun `a dive carrying none is passed over`() {
        val set = of(one("Reef", 20, "aa"), one("Reef", 22, null))
        assertEquals("aa", Download.after(set, "reef"))
    }

    @Test
    fun `nothing to resume from is nothing`() {
        assertNull(Download.after(of(one("Reef", 20, null)), "reef"))
        assertNull(Download.after(of(one("Reef", 20, "aa")), "nobody"))
    }
}

/**
 * Where a device said a dive was, carried into the review as a site to be answered.
 * See ../../../../../../../doc.md — `LOGIC-18`.
 */
class PositionTest {

    private fun at(latitude: Double, longitude: Double, minute: Int) = Recording(
        computer = "Reef",
        began = Date(2026, 6, 21),
        at = Time(10, minute, 0),
        duration = 600.0,
        position = Recording.Position(latitude, longitude),
    )

    private fun read(vararg dived: Recording) = Download.read(dived.asSequence())

    private fun siteOf(set: yemoja.data.ItemSet, dive: Item): Item? {
        val named = (dive.single<Reference>("dive_site") as? Result.Usable)?.value
        return (named as? Reference.Identified)?.let { set[it.id] }
    }

    private fun number(item: Item, field: String): Double? =
        (item.single<Double>(field) as? Result.Usable)?.value

    @Test
    fun `a dive that reported where it was names a site holding that position`() {
        val set = read(at(51.6435, 3.7069, 0))
        val dive = set.allOf(Types.DIVE).single()
        val site = assertNotNull(siteOf(set, dive), "the dive should name one")
        assertEquals(51.6435, number(site, "latitude"))
        assertEquals(3.7069, number(site, "longitude"))
        assertNull(
            (site.single<String>("name") as? Result.Usable)?.value,
            "a fix is not a site, and naming it is the reader's answer",
        )
    }

    @Test
    fun `two dives at one place propose one site, and two places propose two`() {
        val together = read(at(51.6435, 3.7069, 0), at(51.6445, 3.7069, 30))
        assertEquals(1, together.allOf(Types.DIVE_SITE).size, "a hundred metres apart is one place")
        val apart = read(at(51.6435, 3.7069, 0), at(51.7435, 3.7069, 30))
        assertEquals(2, apart.allOf(Types.DIVE_SITE).size, "eleven kilometres apart is two")
    }

    @Test
    fun `a dive that reported nothing names nothing, and makes no site`() {
        val set = read(Recording(computer = "Reef", began = Date(2026, 6, 21), at = Time(10, 0, 0)))
        assertEquals(0, set.allOf(Types.DIVE_SITE).size)
        assertEquals(Result.Absent, set.allOf(Types.DIVE).single().read("dive_site"))
    }

    @Test
    fun `a height above the sea becomes the site's elevation`() {
        val high = Recording(
            computer = "Reef",
            began = Date(2026, 6, 21),
            at = Time(10, 0, 0),
            position = Recording.Position(46.5, 6.6, 372.0),
        )
        val site = siteOf(read(high), read(high).allOf(Types.DIVE).single())
        assertEquals(372.0, number(assertNotNull(site), "elevation"))
    }
}

/**
 * Which dive of a day is which, when a device counts backwards.
 * See ../../../../../../../doc.md — `LOGIC-20`.
 */
class OrderTest {

    private fun at(hour: Int) = Recording(
        computer = "Reef",
        began = Date(2026, 9, 13),
        at = Time(hour, 0, 0),
        duration = 2400.0,
    )

    @Test
    fun `the index on an id counts up through the day, whatever order the device reports in`() {
        // A Shearwater hands over its newest dive first, which is the wrong way round for this.
        val set = Download.read(sequenceOf(at(14), at(10), at(12)))
        val order = set.allOf(Types.DIVE).mapNotNull { set.idOf(it) }.sorted()
        assertEquals(listOf("2026-09-13#0", "2026-09-13#1", "2026-09-13#2"), order)
        assertEquals(Time(10, 0, 0), began(set, "2026-09-13#0"), "the first of the day is #0")
        assertEquals(Time(12, 0, 0), began(set, "2026-09-13#1"))
        assertEquals(Time(14, 0, 0), began(set, "2026-09-13#2"), "and the last is the highest")
    }

    @Test
    fun `a recording that does not say when it was comes last and is still named`() {
        val set = Download.read(sequenceOf(at(14), Recording(computer = "Reef")))
        assertEquals(2, set.allOf(Types.DIVE).size)
        assertEquals(Time(14, 0, 0), began(set, "2026-09-13#0"))
    }

    private fun began(set: yemoja.data.ItemSet, id: String): Time? =
        (set[id]?.single<Time>("start_time") as? Result.Usable)?.value
}

/**
 * Which of a computer's gas slots are written down.
 * See ../../../../../../../doc.md — `LOGIC-12`.
 */
class UsedGasTest {

    private fun slots(vararg gas: String) = gas.map { Recording.GasSource(gas = it) }

    private fun dived(
        gases: List<Recording.GasSource>,
        samples: List<Recording.Sample> = emptyList(),
    ) = Recording(
        computer = "Reef",
        began = Date(2026, 6, 21),
        at = Time(10, 0, 0),
        duration = 1200.0,
        gases = gases,
        samples = samples,
    )

    @Test
    fun `a slot nothing read and nobody switched to is not written down`() {
        val held = usedIn(
            dived(
                slots("air", "air", "EAN19"),
                listOf(Recording.Sample(at = 0, gas = 2, pressures = mapOf(2 to 190.0))),
            ),
        )
        assertEquals(listOf("EAN19"), held.gases.map { it.gas }, "the two switched off go")
        assertEquals(0, held.samples.single().gas, "and what named the third names the first")
        assertEquals(mapOf(0 to 190.0), held.samples.single().pressures)
    }

    @Test
    fun `a slot with a pressure stays, though nobody switched to it`() {
        val held = usedIn(
            dived(
                slots("air", "EAN32"),
                listOf(Recording.Sample(at = 0, gas = 0, pressures = mapOf(1 to 190.0))),
            ),
        )
        assertEquals(listOf("air", "EAN32"), held.gases.map { it.gas }, "both were used, one way")
    }

    @Test
    fun `the only slot there is stays, there being nothing to choose between`() {
        val held = usedIn(dived(slots("EAN32")))
        assertEquals(listOf("EAN32"), held.gases.map { it.gas })
    }

    @Test
    fun `a computer that reports no switch at all keeps the gas it began on`() {
        // An i330R says nothing about gas: no switch, no pressure, just the slots it holds.
        val held = usedIn(dived(slots("air", "EAN32"), samples = listOf(Recording.Sample(at = 0))))
        assertEquals(listOf("air"), held.gases.map { it.gas }, "the first is the one it began on")
    }

    @Test
    fun `a switch at the first sample says which it began on, and the rest still go`() {
        val held = usedIn(
            dived(slots("air", "EAN32", "EAN50"), listOf(Recording.Sample(at = 0, gas = 1))),
        )
        assertEquals(listOf("EAN32"), held.gases.map { it.gas }, "not the first, the one named")
        assertEquals(0, held.samples.single().gas)
    }

    @Test
    fun `a download writes what is left, keyed from one`() {
        val set = Download.read(
            sequenceOf(
                dived(
                    slots("air", "air", "EAN19"),
                    listOf(Recording.Sample(at = 0, gas = 2, pressures = mapOf(2 to 190.0))),
                ),
            ),
        )
        val dive = set.allOf(Types.DIVE).single()
        val sources = (dive.keyed<yemoja.data.OwnedItem>("gas_sources") as Result.Usable).value
        assertEquals(listOf("tank_1"), sources.keys.toList(), "the one that was used, keyed first")
    }
}
