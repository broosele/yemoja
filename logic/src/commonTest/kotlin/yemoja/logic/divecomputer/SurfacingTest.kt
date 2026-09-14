package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Time
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Where a recording stops being a dive.
 * See ../../../../../../../doc.md — `LOGIC-30` in logic/doc.md.
 */
class SurfacingTest {

    /** A recording of one dive, a sample every ten seconds, at the depths given. */
    private fun dived(vararg depth: Double?) = Recording(
        computer = "Reef",
        began = Date(2026, 6, 21),
        at = Time(10, 0, 0),
        samples = depth.mapIndexed { at, held -> Recording.Sample(at = at * 10, depth = held) },
    )

    private fun depths(held: Recording): List<Double?> = held.samples.map { it.depth }

    @Test
    fun `what a computer recorded after the surfacing is cut off`() {
        val held = ended(dived(0.0, 12.0, 4.0, 0.4, 0.0, 0.0, 0.0))
        assertEquals(listOf(0.0, 12.0, 4.0, 0.4), depths(held), "the surfacing itself stays")
    }

    @Test
    fun `a dive that surfaced in the middle of it keeps that`() {
        val held = ended(dived(0.0, 12.0, 0.2, 0.3, 9.0, 3.0, 0.0, 0.0))
        assertEquals(listOf(0.0, 12.0, 0.2, 0.3, 9.0, 3.0, 0.0), depths(held))
    }

    @Test
    fun `a recording that never went below the surface is left whole`() {
        val held = dived(0.0, 0.5, 0.8, 0.0)
        assertEquals(depths(held), depths(ended(held)), "nothing here says where a dive ended")
    }

    @Test
    fun `a depth nobody recorded is not evidence of floating`() {
        val held = ended(dived(0.0, 12.0, 0.5, null, 0.0))
        assertEquals(listOf(0.0, 12.0, 0.5, null, 0.0), depths(held))
    }

    @Test
    fun `whole samples go, so an alarm sounded on the boat goes with them`() {
        val held = ended(
            Recording(
                computer = "Reef",
                samples = listOf(
                    Recording.Sample(at = 0, depth = 12.0, pressures = mapOf(0 to 190.0)),
                    Recording.Sample(at = 10, depth = 0.0, pressures = mapOf(0 to 80.0)),
                    Recording.Sample(
                        at = 20,
                        depth = 0.0,
                        pressures = mapOf(0 to 79.0),
                        alarms = listOf("surface"),
                    ),
                ),
            ),
        )
        assertEquals(listOf(0, 10), held.samples.map { it.at })
        assertTrue(held.samples.none { it.alarms.isNotEmpty() }, "the tail took its alarm with it")
    }

    @Test
    fun `a downloaded profile ends at the surface`() {
        val set = Download.read(sequenceOf(dived(0.0, 12.0, 11.0, 0.6, 0.0, 0.0)))
        val dive = set.allOf(Types.DIVE).single()
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val profile = (profiles.values.first() as Element.Usable).value
        val depth: Series = (profile.series<Double>("depth") as Result.Usable).value
        assertEquals(30, depth.secondAt(depth.size - 1), "the last sample is the surfacing")
    }

    /** A stretch beginning [minute] minutes in, diving for [dived] samples and floating after. */
    private fun stretch(minute: Int, token: String, dived: Int, floating: Int) = Recording(
        computer = "Reef",
        fingerprints = listOf(token),
        began = Date(2026, 6, 21),
        at = Time(10, minute, 0),
        duration = (dived * 60).toDouble(),
        samples = (0..<dived).map { Recording.Sample(at = it * 60, depth = 12.0) } +
            (0..floating).map { Recording.Sample(at = (dived + it) * 60, depth = 0.0) },
    )

    @Test
    fun `what separates two stretches is the surface spent, not the wait a computer was set to`() {
        // Ten minutes down, ten floating while the computer made up its mind, and back in at
        // twenty-two minutes. Measured from the end of the recording that is two minutes apart
        // and one dive; measured from the surfacing it is twelve, and two dives. `LOGIC-25`.
        val first = stretch(0, "a1", dived = 10, floating = 10)
        val next = stretch(22, "b2", dived = 10, floating = 10)
        val set = Download.read(sequenceOf(first, next))
        assertEquals(2, set.allOf(Types.DIVE).size)
    }

    @Test
    fun `a stretch that really did resume soon is still one dive`() {
        val first = stretch(0, "a1", dived = 10, floating = 10)
        val next = stretch(15, "b2", dived = 10, floating = 10)
        val set = Download.read(sequenceOf(first, next))
        assertEquals(1, set.allOf(Types.DIVE).size, "five minutes on the surface")
    }
}
