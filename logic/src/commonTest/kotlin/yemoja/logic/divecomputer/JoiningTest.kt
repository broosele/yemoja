package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Time
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Putting back together a dive a computer cut in two.
 * See ../../../../../../../doc.md — `LOGIC-25` in logic/doc.md.
 */
class JoiningTest {

    private fun stretch(
        minute: Int,
        token: String,
        lasted: Double,
        deepest: Double = 20.0,
        mean: Double = 10.0,
        cold: Double = 8.0,
        computer: String = "Reef",
        gases: List<Recording.GasSource> = emptyList(),
        samples: List<Recording.Sample> = emptyList(),
    ) = Recording(
        computer = computer,
        fingerprints = listOf(token),
        began = Date(2026, 6, 21),
        at = Time(10, minute, 0),
        duration = lasted,
        maxDepth = deepest,
        averageDepth = mean,
        coldest = cold,
        gases = gases,
        samples = samples,
    )

    private fun samples(vararg at: Int) = at.map { Recording.Sample(at = it, depth = 5.0) }

    @Test
    fun `two stretches a short surface apart are one recording`() {
        val first = stretch(0, "a1", 1200.0, deepest = 30.0, mean = 12.0, cold = 8.0)
        val next = stretch(23, "b2", 600.0, deepest = 18.0, mean = 6.0, cold = 9.0)
        val one = joined(sequenceOf(first, next)).toList().single()
        assertEquals(listOf("a1", "b2"), one.fingerprints, "one token per stretch, in order")
        assertEquals(1980.0, one.duration, "the first start to the second end, surface and all")
        assertEquals(30.0, one.maxDepth)
        assertEquals(8.0, one.coldest)
        assertEquals(10.0, one.averageDepth, "weighted by how long each stretch lasted")
        assertEquals(Time(10, 0, 0), one.at, "the dive began when the first stretch did")
    }

    @Test
    fun `a longer surface is two dives, and so is another device`() {
        val first = stretch(0, "a1", 1200.0)
        assertEquals(2, joined(sequenceOf(first, stretch(31, "b2", 600.0))).toList().size)
        val other = stretch(23, "b2", 600.0, computer = "Other")
        assertEquals(2, joined(sequenceOf(first, other)).toList().size)
    }

    @Test
    fun `a dive cut into three comes back as one`() {
        val all = joined(
            sequenceOf(stretch(0, "a", 600.0), stretch(15, "b", 600.0), stretch(30, "c", 600.0)),
        ).toList()
        assertEquals(listOf("a", "b", "c"), all.single().fingerprints)
    }

    @Test
    fun `the order a device reports in does not matter`() {
        val first = stretch(0, "a1", 1200.0)
        val next = stretch(23, "b2", 600.0)
        val one = joined(sequenceOf(next, first)).toList().single()
        assertEquals(listOf("a1", "b2"), one.fingerprints, "kept in the order they were recorded")
        assertEquals(Time(10, 0, 0), one.at)
    }

    @Test
    fun `the second stretch's samples are laid after the first's`() {
        val first = stretch(0, "a1", 1200.0, samples = samples(0, 600, 1200))
        val next = stretch(23, "b2", 600.0, samples = samples(0, 600))
        val one = joined(sequenceOf(first, next)).toList().single()
        assertEquals(listOf(0, 600, 1200, 1380, 1980), one.samples.map { it.at })
        val forwards = one.samples.zipWithNext().all { (a, b) -> b.at > a.at }
        assertTrue(forwards, "a series runs forwards")
    }

    @Test
    fun `one cylinder read twice is one cylinder, from its first reading to its last`() {
        // The same list of cylinders both times, each stretch reading its own pressures off it.
        val before = Recording.GasSource(gas = "air", startPressure = 200.0, endPressure = 192.0)
        val after = Recording.GasSource(gas = "air", startPressure = 192.0, endPressure = 85.0)
        val first = stretch(0, "a1", 1200.0, gases = listOf(before), samples = samples(0, 1200))
        val next = stretch(23, "b2", 600.0, gases = listOf(after), samples = samples(0, 600))
        val one = joined(sequenceOf(first, next)).toList().single().gases.single()
        assertEquals("air", one.gas)
        assertEquals(200.0, one.startPressure, "what it began the dive at")
        assertEquals(85.0, one.endPressure, "and what it came up with")
    }

    @Test
    fun `two cylinders of one gas stay two, and what named one still names it`() {
        val twin = listOf(Recording.GasSource(gas = "air"), Recording.GasSource(gas = "air"))
        val first = stretch(0, "a1", 1200.0, gases = twin, samples = samples(0, 1200))
        val next = stretch(
            23,
            "b2",
            600.0,
            gases = twin,
            samples = listOf(Recording.Sample(at = 0, gas = 1, pressures = mapOf(1 to 150.0))),
        )
        val one = joined(sequenceOf(first, next)).toList().single()
        assertEquals(2, one.gases.size, "matching by gas would have made them one")
        val moved = one.samples.last()
        assertEquals(1, moved.gas, "the second cylinder is still the second")
        assertEquals(mapOf(1 to 150.0), moved.pressures)
    }

    @Test
    fun `a gas only the second stretch has is added after the first's`() {
        val air = Recording.GasSource(gas = "air")
        val rich = Recording.GasSource(gas = "EAN32")
        val first = stretch(0, "a1", 1200.0, gases = listOf(air), samples = samples(0, 1200))
        val next = stretch(23, "b2", 600.0, gases = listOf(air, rich), samples = samples(0, 600))
        val one = joined(sequenceOf(first, next)).toList().single()
        assertEquals(listOf("air", "EAN32"), one.gases.map { it.gas })
    }

    @Test
    fun `a recording that does not say when it began is left where it is`() {
        val first = stretch(0, "a1", 1200.0)
        val nameless = Recording(computer = "Reef", fingerprints = listOf("b2"))
        assertEquals(2, joined(sequenceOf(first, nameless)).toList().size)
    }

    @Test
    fun `a download of a dive cut in two makes one dive carrying both tokens`() {
        val set = Download.read(
            sequenceOf(stretch(0, "a1", 1200.0), stretch(23, "b2", 600.0)),
        )
        val dives = set.allOf(Types.DIVE)
        assertEquals(1, dives.size, "one dive, not two")
        assertEquals(listOf("a1", "b2"), tokensOf(dives.single()))
    }

    private fun tokensOf(dive: Item): List<String> {
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val profile = (profiles.values.first() as Element.Usable).value
        val held = (profile.list<String>("fingerprint") as Result.Usable).value
        return held.mapNotNull { (it as? Element.Usable)?.value }
    }
}
