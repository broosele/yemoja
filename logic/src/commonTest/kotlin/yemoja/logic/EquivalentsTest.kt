package yemoja.logic

import yemoja.data.Gas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The depths of air a mix is equivalent to. See ../../../../../doc.md — `LOGIC-41`.
 *
 * The rule of thumb is ten metres of sea water to a bar and a bar at the surface. The model's own
 * figures differ from it by a few centimetres, which the tolerances allow for.
 */

private val EAN32 = Gas.parse("EAN32")
private val EAN36 = Gas.parse("EAN36")
private val TRIMIX = Gas.parse("TMX18/45")

class EquivalentsTest {

    @Test
    fun `air is equivalent to itself, at any depth and by either convention`() {
        for (metres in listOf(0.0, 10.0, 30.0, 60.0)) {
            assertEquals(metres, equivalentAirDepth(metres, Gas.AIR), 1e-9)
            assertEquals(metres, equivalentNarcoticDepth(metres, Gas.AIR), 1e-9)
            assertEquals(metres, equivalentNarcoticDepth(metres, Gas.AIR, oxygenNarcotic = false), 1e-9)
        }
    }

    @Test
    fun `nitrox is breathed as shallower air, by the published rule`() {
        // EAN36 at 30 m: 0.64 of 4 bar is 2.56 bar of nitrogen, which air holds at 3.24 bar: 22.4 m.
        assertEquals(22.4, equivalentAirDepth(30.0, EAN36), 0.1)
        // EAN32 at 30 m: 24.4 m.
        assertEquals(24.4, equivalentAirDepth(30.0, EAN32), 0.1)
    }

    @Test
    fun `air at the equivalent depth holds the nitrogen the mix holds`() {
        val depth = equivalentAirDepth(33.0, EAN32)

        assertEquals(
            EAN32.fractionN2 * ambientAt(33.0, NOMINAL_DENSITY, SEA_LEVEL),
            Gas.AIR.fractionN2 * ambientAt(depth, NOMINAL_DENSITY, SEA_LEVEL),
            1e-9,
        )
    }

    @Test
    fun `a rich mix near the surface is equivalent to the surface, never above it`() {
        assertEquals(0.0, equivalentAirDepth(0.0, EAN32), "less nitrogen than air at the surface")
    }

    @Test
    fun `nitrox is as narcotic as air when oxygen counts, and less when it does not`() {
        assertEquals(30.0, equivalentNarcoticDepth(30.0, EAN32), 1e-9)
        assertEquals(equivalentAirDepth(30.0, EAN32), equivalentNarcoticDepth(30.0, EAN32, oxygenNarcotic = false), 1e-9)
    }

    @Test
    fun `helium makes a mix less narcotic, and counting oxygen makes it more cautious`() {
        // Trimix 18/45 at 60 m: 0.55 of 7 bar is 3.85 bar of narcotic gas, which air is at 2.85 bar
        // of depth: 28.5 m. Without the oxygen, 0.37 of 7 bar is 2.59 bar of nitrogen: 22.8 m.
        val counted = equivalentNarcoticDepth(60.0, TRIMIX)
        val nitrogenOnly = equivalentNarcoticDepth(60.0, TRIMIX, oxygenNarcotic = false)

        assertEquals(28.5, counted, 0.15)
        assertEquals(22.8, nitrogenOnly, 0.15)
        assertTrue(counted > nitrogenOnly)
    }

    @Test
    fun `in fresh water the depths are fresh water's too`() {
        val depth = equivalentAirDepth(30.0, EAN32, density = 1000.0)

        assertEquals(
            EAN32.fractionN2 * ambientAt(30.0, 1000.0, SEA_LEVEL),
            Gas.AIR.fractionN2 * ambientAt(depth, 1000.0, SEA_LEVEL),
            1e-9,
        )
    }

    @Test
    fun `a mix breathable at the surface has no minimum depth`() {
        assertEquals(0.0, minimumOperatingDepth(Gas.AIR))
        assertEquals(0.0, minimumOperatingDepth(EAN32))
    }

    @Test
    fun `a hypoxic mix is breathed from where its oxygen reaches the minimum`() {
        // Trimix 10/70: 0.18 bar of oxygen at 1.8 bar, which is about 8 m.
        val shallowest = minimumOperatingDepth(Gas.parse("TMX10/70"))!!

        assertEquals(7.9, shallowest, 0.1)
        assertEquals(LEAST_OXYGEN, 0.10 * ambientAt(shallowest, NOMINAL_DENSITY, SEA_LEVEL), 1e-9)
    }

    @Test
    fun `a stricter minimum takes a hypoxic mix deeper`() {
        val mix = Gas.parse("TMX12/60")

        assertTrue(minimumOperatingDepth(mix, least = 0.18)!! > minimumOperatingDepth(mix, least = 0.16)!!)
    }

    @Test
    fun `a mix with no oxygen has no minimum depth either`() {
        assertEquals(null, minimumOperatingDepth(Gas(0, 100)))
    }
}
