package yemoja.logic

import yemoja.data.Gas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What a cylinder holds once a gas is added, and which gases make a mix. See
 * ../../../../../doc.md — `LOGIC-45`.
 *
 * The figures compared against are the reference's own at 20 °C: the NIST isotherm of each pure
 * gas, and the GERG-2008 mixing rules for a mix.
 */

private val HELIUM = Gas(0, 100)
private val EAN32 = Gas(32, 0)
private val TRIMIX = Gas(18, 45)

/** Litre bar a mole at 20 °C, which is what an ideal gas reads for a mole a litre. */
private const val IDEAL = 0.08314462618 * 293.15

/** How far the pressure of [contents] is from what an ideal gas would read: a real gas's Z. */
private fun compressibility(contents: Contents): Double = contents.pressure / (contents.amount * IDEAL)

/** The steps of [recipe] added one after another to [start], as a blender would. */
private fun filled(start: Contents, recipe: Blend.Recipe): Contents =
    recipe.added.fold(start) { held, added -> toppedUp(held, added.gas, added.bar) }

class ContentsTest {

    @Test
    fun `a pure gas is its own published curve`() {
        val published = mapOf(
            Gas.OXYGEN to listOf(0.9465, 0.9391, 0.9802),
            Gas(0, 0) to listOf(1.0008, 1.0518, 1.1401),
            HELIUM to listOf(1.0481, 1.0952, 1.1413),
        )
        for ((gas, expected) in published) {
            for ((bar, z) in listOf(100.0, 200.0, 300.0).zip(expected)) {
                assertEquals(z, compressibility(contentsOf(gas, bar)), 0.001, "$gas at $bar bar")
            }
        }
    }

    @Test
    fun `a mix is the reference's, cross terms and all`() {
        val reference = mapOf(
            Gas.AIR to listOf(0.9890, 1.0281, 1.1069),
            EAN32 to listOf(0.9830, 1.0158, 1.0896),
            TRIMIX to listOf(1.0501, 1.1124, 1.1844),
            Gas(50, 50) to listOf(1.0464, 1.1035, 1.1694),
            Gas(10, 70) to listOf(1.0589, 1.1215, 1.1868),
        )
        for ((gas, expected) in reference) {
            for ((bar, z) in listOf(100.0, 200.0, 300.0).zip(expected)) {
                assertEquals(z, compressibility(contentsOf(gas, bar)), 0.003, "$gas at $bar bar")
            }
        }
    }

    @Test
    fun `contents read back the pressure and the mix they were made from`() {
        val held = contentsOf(TRIMIX, 200.0)
        assertEquals(200.0, held.pressure, 1e-6)
        assertEquals(0.18, held.fractionO2, 1e-9)
        assertEquals(0.45, held.fractionHe, 1e-9)
        assertEquals(0.37, held.fractionN2, 1e-9)
        assertEquals(TRIMIX, held.gas)
    }

    @Test
    fun `a gas at a low pressure is an ideal one`() {
        assertEquals(1.0, compressibility(contentsOf(Gas.AIR, 1.0)), 0.001)
        assertEquals(1.0, compressibility(contentsOf(HELIUM, 1.0)), 0.001)
    }

    @Test
    fun `an empty cylinder holds nothing and has no mix`() {
        val empty = contentsOf(Gas.AIR, 0.0)
        assertEquals(0.0, empty.amount, 1e-9)
        assertEquals(0.0, Contents.EMPTY.pressure)
        assertEquals(0.0, Contents.EMPTY.fractionO2)
    }

    @Test
    fun `more gas always reads a higher pressure, as far as a cylinder is worked out`() {
        for (gas in listOf(Gas.OXYGEN, Gas.AIR, Gas(0, 0), HELIUM, TRIMIX, Gas(50, 50))) {
            var before = contentsOf(gas, 0.0)
            for (bar in 10..MOST_PRESSURE.toInt() step 10) {
                val held = contentsOf(gas, bar.toDouble())
                assertTrue(held.amount > before.amount, "$gas at $bar bar")
                before = held
            }
        }
    }

    @Test
    fun `litres are counted at one bar, as a diver counts them`() {
        // An ideal gas would be 12 L times 200 bar; air at 200 bar is three per cent short of it.
        assertEquals(2400.0 / 1.0281, contentsOf(Gas.AIR, 200.0).litresIn(12.0), 5.0)
    }

    @Test
    fun `a pressure past what the fit reaches is refused`() {
        assertFailsWith<IllegalArgumentException> { contentsOf(Gas.AIR, MOST_PRESSURE + 1) }
        assertFailsWith<IllegalArgumentException> { contentsOf(Gas.AIR, -1.0) }
    }
}

class ToppedUpTest {

    @Test
    fun `a half-used nitrox topped up with air is richer than the pressures alone say`() {
        // By pressures alone, 100 bar of 32 % and 100 bar of 21 % is 26.5 %. The first hundred bar
        // holds more gas than the second, so the reference gives 26.7 %.
        val held = toppedUp(contentsOf(EAN32, 100.0), Gas.AIR, 200.0)
        assertEquals(0.2672, held.fractionO2, 0.0005)
        assertEquals(0.0, held.fractionHe)
        assertEquals(200.0, held.pressure, 1e-6)
        assertEquals(Gas(27, 0), held.gas)
    }

    @Test
    fun `a trimix topped up with air keeps more helium than the pressures alone say`() {
        val held = toppedUp(contentsOf(TRIMIX, 80.0), Gas.AIR, 200.0)
        assertEquals(0.1976, held.fractionO2, 0.0005)
        assertEquals(0.1862, held.fractionHe, 0.001)
    }

    @Test
    fun `a top-up with the same mix leaves the mix`() {
        val held = toppedUp(contentsOf(EAN32, 60.0), EAN32, 200.0)
        assertEquals(0.32, held.fractionO2, 1e-9)
        assertEquals(contentsOf(EAN32, 200.0).amount, held.amount, 1e-6)
    }

    @Test
    fun `a top-up to the pressure already there adds nothing`() {
        val start = contentsOf(EAN32, 100.0)
        assertEquals(start.amount, toppedUp(start, Gas.AIR, 100.0).amount, 1e-6)
    }

    @Test
    fun `a top-up to less than is there is refused`() {
        assertFailsWith<IllegalArgumentException> { toppedUp(contentsOf(EAN32, 100.0), Gas.AIR, 90.0) }
    }
}

class BlendedTest {

    private val usual = listOf(Gas.OXYGEN, HELIUM, Gas.AIR)

    private fun recipe(start: Contents, wanted: Contents, from: List<Gas> = usual): Blend.Recipe =
        assertIs<Blend.Recipe>(blended(start, wanted, from))

    @Test
    fun `nitrox in an empty cylinder is oxygen and then air`() {
        val recipe = recipe(Contents.EMPTY, contentsOf(EAN32, 200.0))
        assertNull(recipe.drained)
        assertEquals(listOf(Gas.OXYGEN, Gas.AIR), recipe.added.map { it.gas })
        // An ideal gas would say 27.8 bar of oxygen.
        assertEquals(26.93, recipe.added[0].bar, 0.1)
        assertEquals(200.0, recipe.added[1].bar, 1e-6)
    }

    @Test
    fun `trimix in an empty cylinder is filled to the reference's pressures`() {
        val recipe = recipe(Contents.EMPTY, contentsOf(TRIMIX, 200.0))
        assertEquals(usual, recipe.added.map { it.gas })
        // An ideal gas would say 16.3 bar of oxygen and helium to 106.3 bar.
        assertEquals(14.53, recipe.added[0].bar, 0.1)
        assertEquals(101.17, recipe.added[1].bar, 0.4)
        assertEquals(200.0, recipe.added[2].bar, 1e-6)
    }

    @Test
    fun `the order listed is the order filled, and changes the pressures between`() {
        val recipe = recipe(Contents.EMPTY, contentsOf(TRIMIX, 200.0), listOf(HELIUM, Gas.OXYGEN, Gas.AIR))
        assertEquals(listOf(HELIUM, Gas.OXYGEN, Gas.AIR), recipe.added.map { it.gas })
        assertEquals(84.19, recipe.added[0].bar, 0.3)
        assertEquals(101.17, recipe.added[1].bar, 0.4)
    }

    @Test
    fun `a recipe filled by its pressures leaves the mix that was wanted`() {
        val start = contentsOf(EAN32, 50.0)
        val wanted = contentsOf(Gas(21, 35), 220.0)
        val held = filled(start, recipe(start, wanted))
        assertEquals(0.21, held.fractionO2, 1e-6)
        assertEquals(0.35, held.fractionHe, 1e-6)
        assertEquals(220.0, held.pressure, 1e-6)
    }

    @Test
    fun `what the cylinder already holds is used`() {
        val start = contentsOf(EAN32, 100.0)
        val recipe = recipe(start, contentsOf(EAN32, 200.0))
        assertNull(recipe.drained)
        val oxygen = recipe.added.first { it.gas == Gas.OXYGEN }
        val fromEmpty = recipe(Contents.EMPTY, contentsOf(EAN32, 200.0)).added.first { it.gas == Gas.OXYGEN }
        assertTrue(oxygen.contents.amount < fromEmpty.contents.amount, "half the oxygen is already there")
    }

    @Test
    fun `a gas the mix needs none of is left out`() {
        val recipe = recipe(Contents.EMPTY, contentsOf(Gas.AIR, 200.0))
        assertEquals(listOf(Gas.AIR), recipe.added.map { it.gas })
    }

    @Test
    fun `a cylinder already holding the mix is added nothing`() {
        val held = contentsOf(EAN32, 200.0)
        val recipe = recipe(held, held)
        assertNull(recipe.drained)
        assertTrue(recipe.added.isEmpty())
    }

    @Test
    fun `too rich a start is drained as little as will do`() {
        // Half a cylinder of 50 % cannot be thinned to 32 % with air in the room left.
        val start = contentsOf(Gas(50, 0), 150.0)
        val wanted = contentsOf(EAN32, 200.0)
        val recipe = recipe(start, wanted, listOf(Gas.OXYGEN, Gas.AIR))
        val drained = assertIs<Double>(recipe.drained)
        assertTrue(drained > 0 && drained < 150.0, "drained to $drained bar")
        assertEquals(listOf(Gas.AIR), recipe.added.map { it.gas }, "the most kept is where no oxygen is added")
        val held = filled(contentsOf(Gas(50, 0), drained), recipe)
        assertEquals(0.32, held.fractionO2, 1e-6)
        assertEquals(200.0, held.pressure, 1e-6)
    }

    @Test
    fun `more than is wanted of the mix itself is let down to it`() {
        val recipe = recipe(contentsOf(Gas.AIR, 230.0), contentsOf(Gas.AIR, 200.0))
        assertEquals(200.0, assertIs<Double>(recipe.drained), 1e-6)
        assertTrue(recipe.added.isEmpty())
    }

    @Test
    fun `helium the mix has no place for is emptied out`() {
        val recipe = recipe(contentsOf(TRIMIX, 60.0), contentsOf(EAN32, 200.0))
        assertEquals(0.0, assertIs<Double>(recipe.drained), 1e-6)
        assertEquals(listOf(Gas.OXYGEN, Gas.AIR), recipe.added.map { it.gas })
    }

    @Test
    fun `a mix the gases cannot make is impossible`() {
        assertIs<Blend.Impossible>(blended(Contents.EMPTY, contentsOf(TRIMIX, 200.0), listOf(Gas.OXYGEN, Gas.AIR)))
        assertIs<Blend.Impossible>(blended(Contents.EMPTY, contentsOf(Gas(10, 0), 200.0), listOf(Gas.OXYGEN, Gas.AIR)))
        assertIs<Blend.Impossible>(blended(Contents.EMPTY, contentsOf(EAN32, 200.0), emptyList()))
    }

    @Test
    fun `with more gases than are needed the fewest are used, the earlier listed first`() {
        val wanted = contentsOf(EAN32, 200.0)
        val banked = recipe(Contents.EMPTY, wanted, listOf(Gas.OXYGEN, Gas.AIR, EAN32))
        assertEquals(listOf(EAN32), banked.added.map { it.gas })
        val listedTwice = recipe(Contents.EMPTY, wanted, listOf(Gas.OXYGEN, Gas.AIR, Gas.AIR))
        assertEquals(listOf(Gas.OXYGEN, Gas.AIR), listedTwice.added.map { it.gas })
    }

    @Test
    fun `a helium mix is made from a banked nitrox too`() {
        val wanted = contentsOf(Gas(18, 35), 200.0)
        val recipe = recipe(Contents.EMPTY, wanted, listOf(HELIUM, EAN32, Gas.AIR))
        val held = filled(Contents.EMPTY, recipe)
        assertEquals(0.18, held.fractionO2, 1e-6)
        assertEquals(0.35, held.fractionHe, 1e-6)
    }
}
