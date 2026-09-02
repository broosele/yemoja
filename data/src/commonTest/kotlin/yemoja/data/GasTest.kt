package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GasTest {

    @Test
    fun `a mix is written as divers write it`() {
        assertEquals("AIR", Gas(21, 0).toString())
        assertEquals("O2", Gas(100, 0).toString())
        assertEquals("EAN32", Gas(32, 0).toString())
        assertEquals("TMX18/35", Gas(18, 35).toString())
    }

    @Test
    fun `a mix is read as divers write it`() {
        assertEquals(Gas(32, 0), Gas.parse("EAN32"))
        assertEquals(Gas(32, 0), Gas.parse("nx 32"))
        assertEquals(Gas(32, 0), Gas.parse("32%"))
        assertEquals(Gas(32, 0), Gas.parse("Nitrox32"))
        assertEquals(Gas(21, 0), Gas.parse("air"))
        assertEquals(Gas(100, 0), Gas.parse("O2"))
        assertEquals(Gas(18, 35), Gas.parse("TMX18/35"))
        assertEquals(Gas(18, 35), Gas.parse("18/35"))
        assertEquals(Gas(18, 35), Gas.parse("18/35/47"))
    }

    @Test
    fun `a bare number is not a mix`() {
        // It could be a cylinder size.
        assertFailsWith<ValueFormatException> { Gas.parse("32") }
    }

    @Test
    fun `a trimix must agree with itself`() {
        assertFailsWith<ValueFormatException> { Gas.parse("18/35/40") }
    }

    @Test
    fun `a mix that cannot exist is refused as a value, not as a fault`() {
        assertFailsWith<ValueFormatException> { Gas.parse("EAN200") }
        assertFailsWith<ValueFormatException> { Gas.parse("60/60") }
    }

    @Test
    fun `a mix that is not a mix at all is refused`() {
        assertFailsWith<ValueFormatException> { Gas.parse("helium") }
        assertFailsWith<ValueFormatException> { Gas.parse("") }
    }

    @Test
    fun `the constructor guards against a caller rather than a file`() {
        assertFailsWith<IllegalArgumentException> { Gas(101, 0) }
        assertFailsWith<IllegalArgumentException> { Gas(-1, 0) }
        assertFailsWith<IllegalArgumentException> { Gas(60, 60) }
    }

    @Test
    fun `nitrogen is whatever is left`() {
        assertEquals(79, Gas(21, 0).percentN2)
        assertEquals(47, Gas(18, 35).percentN2)
        assertEquals(0, Gas(100, 0).percentN2)
    }

    @Test
    fun `fractions round to the whole percentages the format writes`() {
        assertEquals(Gas(21, 0), Gas(0.21, 0.0))
        assertEquals(Gas(32, 0), Gas(0.315, 0.0))
        assertEquals(0.32, Gas(32, 0).fractionO2)
        assertEquals(0.35, Gas(18, 35).fractionHe)
    }

    @Test
    fun `writing a mix and reading it back gives the same mix`() {
        val mixes = listOf(Gas(21, 0), Gas(100, 0), Gas(32, 0), Gas(18, 35), Gas(10, 70))
        for (mix in mixes) {
            assertEquals(mix, Gas.parse(mix.toString()), "${mix.percentO2}/${mix.percentHe}")
        }
    }
}
