package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Time
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
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
