package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun unitsOf(vararg declared: Pair<String, Any?>): Units = Units.of(mapOf(*declared))

/** Within a millimetre of a metre, which is finer than anything written by hand. */
private fun assertClose(expected: Double, actual: Double, what: String) {
    assertTrue(
        kotlin.math.abs(expected - actual) < 1e-9,
        "$what should be $expected, but was $actual",
    )
}

class DefaultUnitsTest {

    @Test
    fun `a file declaring nothing is written in the defaults`() {
        for (dimension in Dimension.entries) {
            assertEquals(7.5, Units.DEFAULT.toDefault(dimension, 7.5), dimension.name)
            assertEquals(7.5, Units.DEFAULT.fromDefault(dimension, 7.5), dimension.name)
            assertNull(Units.DEFAULT.refusal(dimension), dimension.name)
        }
    }

    @Test
    fun `declaring the default itself changes nothing`() {
        val units = unitsOf("length" to "m", "temperature" to "C")
        assertEquals(30.0, units.toDefault(Dimension.LENGTH, 30.0))
        assertEquals(30.0, units.toDefault(Dimension.TEMPERATURE, 30.0))
    }

    @Test
    fun `an empty block is the default itself`() {
        assertSame(Units.DEFAULT, Units.of(emptyMap()))
    }

    @Test
    fun `every dimension but one has a default name`() {
        assertEquals("m", Units.defaultName(Dimension.LENGTH))
        assertEquals("kg", Units.defaultName(Dimension.MASS))
        assertEquals("s", Units.defaultName(Dimension.TIME))
        assertEquals("C", Units.defaultName(Dimension.TEMPERATURE))
        assertEquals("l", Units.defaultName(Dimension.VOLUME))
        assertEquals("bar", Units.defaultName(Dimension.PRESSURE))
        assertEquals("deg", Units.defaultName(Dimension.ANGLE))
        assertEquals("kg/m3", Units.defaultName(Dimension.DENSITY))
        // A ratio takes no unit and needs none. DATA-8's table has no row for it.
        assertNull(Units.defaultName(Dimension.DIMENSIONLESS))
    }
}

class ConversionTest {

    @Test
    fun `a length in feet is read in metres`() {
        val units = unitsOf("length" to "ft")
        assertClose(30.48, units.toDefault(Dimension.LENGTH, 100.0), "100 ft")
        assertClose(100.0, units.fromDefault(Dimension.LENGTH, 30.48), "30.48 m")
    }

    @Test
    fun `a mass in pounds is read in kilograms`() {
        val units = unitsOf("mass" to "lb")
        assertClose(4.5359237, units.toDefault(Dimension.MASS, 10.0), "10 lb")
    }

    @Test
    fun `a time in minutes or hours is read in seconds`() {
        assertClose(90.0, unitsOf("time" to "min").toDefault(Dimension.TIME, 1.5), "1.5 min")
        assertClose(5400.0, unitsOf("time" to "h").toDefault(Dimension.TIME, 1.5), "1.5 h")
    }

    @Test
    fun `a volume in cubic metres is read in litres`() {
        assertClose(12.0, unitsOf("volume" to "m3").toDefault(Dimension.VOLUME, 0.012), "0.012 m3")
    }

    @Test
    fun `a pressure in psi or pascal is read in bar`() {
        val psi = unitsOf("pressure" to "psi")
        assertClose(13.789514586336722, psi.toDefault(Dimension.PRESSURE, 200.0), "200 psi")
        val pascal = unitsOf("pressure" to "Pa")
        assertClose(2.0, pascal.toDefault(Dimension.PRESSURE, 200000.0), "200 kPa")
    }
}

/** Temperature is the reason a unit is a factor and an offset. */
class AffineConversionTest {

    @Test
    fun `Fahrenheit is offset as well as scaled`() {
        val units = unitsOf("temperature" to "F")
        assertClose(0.0, units.toDefault(Dimension.TEMPERATURE, 32.0), "32 F")
        assertClose(100.0, units.toDefault(Dimension.TEMPERATURE, 212.0), "212 F")
        assertClose(-40.0, units.toDefault(Dimension.TEMPERATURE, -40.0), "-40 F")
    }

    @Test
    fun `kelvin is offset and not scaled`() {
        val units = unitsOf("temperature" to "K")
        assertClose(0.0, units.toDefault(Dimension.TEMPERATURE, 273.15), "273.15 K")
        assertClose(-273.15, units.toDefault(Dimension.TEMPERATURE, 0.0), "absolute zero")
    }

    @Test
    fun `a factor alone would read 32 F as 17 point 8`() {
        // The mistake the offset exists to prevent, stated as the number it would produce.
        val units = unitsOf("temperature" to "F")
        assertTrue(units.toDefault(Dimension.TEMPERATURE, 32.0) < 1.0, "32 F is freezing, not 17.8")
    }

    @Test
    fun `writing back undoes reading, offset and all`() {
        for (name in listOf("C", "F", "K")) {
            val units = unitsOf("temperature" to name)
            val written = 21.5
            val held = units.toDefault(Dimension.TEMPERATURE, written)
            assertClose(written, units.fromDefault(Dimension.TEMPERATURE, held), "$written $name")
        }
    }

    @Test
    fun `writing back undoes reading for every name in the set`() {
        val every = mapOf(
            Dimension.LENGTH to listOf("m", "ft"),
            Dimension.MASS to listOf("kg", "lb"),
            Dimension.TIME to listOf("s", "min", "h"),
            Dimension.TEMPERATURE to listOf("C", "F", "K"),
            Dimension.VOLUME to listOf("l", "m3"),
            Dimension.PRESSURE to listOf("bar", "psi", "Pa"),
            Dimension.ANGLE to listOf("deg"),
            Dimension.DENSITY to listOf("kg/m3"),
            Dimension.FLOW to listOf("l/min", "m3/s"),
        )
        for ((dimension, names) in every) {
            for (name in names) {
                val units = unitsOf(dimension.name.lowercase() to name)
                val held = units.toDefault(dimension, 12.25)
                assertClose(12.25, units.fromDefault(dimension, held), "12.25 $name")
                assertNull(units.refusal(dimension), name)
            }
        }
    }
}

/** `DATA-87`: an unrecognised name refuses its own dimension and nothing else. */
class UnknownUnitTest {

    @Test
    fun `a name nobody knows refuses its dimension, and says what it was`() {
        val units = unitsOf("length" to "fathom")
        assertEquals("fathom is not a unit of length", units.refusal(Dimension.LENGTH))
    }

    @Test
    fun `it leaves every other dimension alone`() {
        val units = unitsOf("length" to "fathom", "temperature" to "F")
        assertNull(units.refusal(Dimension.TEMPERATURE))
        assertNull(units.refusal(Dimension.PRESSURE))
        assertClose(0.0, units.toDefault(Dimension.TEMPERATURE, 32.0), "32 F")
        assertEquals(3.5, units.toDefault(Dimension.PRESSURE, 3.5))
    }

    @Test
    fun `case is part of the name`() {
        // C is Celsius and K is kelvin, and neither can be written the other way round.
        assertEquals(
            "c is not a unit of temperature",
            unitsOf("temperature" to "c").refusal(Dimension.TEMPERATURE),
        )
        assertEquals(
            "M is not a unit of length",
            unitsOf("length" to "M").refusal(Dimension.LENGTH),
        )
    }

    @Test
    fun `a key naming no dimension is passed over`() {
        // How this build meets a dimension a later one added, and a misspelling besides.
        val units = unitsOf("lenght" to "ft", "luminosity" to "cd")
        assertNull(units.refusal(Dimension.LENGTH))
        assertEquals(100.0, units.toDefault(Dimension.LENGTH, 100.0))
    }

    @Test
    fun `dimensionless is not a key, whatever a file says`() {
        assertNull(unitsOf("dimensionless" to "x").refusal(Dimension.DIMENSIONLESS))
    }

    @Test
    fun `a unit that is not text refuses, naming what was written`() {
        assertEquals("7 is not a unit of length", unitsOf("length" to 7).refusal(Dimension.LENGTH))
        assertEquals("null is not a unit of mass", unitsOf("mass" to null).refusal(Dimension.MASS))
    }
}

/** What a file's units do to a value on the way in and on the way out. */
class ReadingInUnitsTest {

    private val depth = NumberDescription("depth", Dimension.LENGTH, range = 0.0..332.35)
    private val feet = unitsOf("length" to "ft")

    @Test
    fun `a number is read in the unit its file declares`() {
        val read = depth.read(100.0, false, feet)
        assertClose(30.48, (read as Result.Usable).value as Double, "100 ft")
    }

    @Test
    fun `text is read in it too, since a file may write a number either way`() {
        val read = depth.read(" 100 ", false, feet)
        assertClose(30.48, (read as Result.Usable).value as Double, "100 ft as text")
    }

    @Test
    fun `writing back undoes reading, so a file keeps the units it was written in`() {
        assertEquals("100", depth.format(30.48, feet))
        assertEquals("30.48", depth.format(30.48, Units.DEFAULT))
    }

    @Test
    fun `the range is checked after conversion, so a bound means one thing`() {
        // 500 ft is 152.4 m and within the bound; the same number of metres is not.
        assertIs<Result.Usable<Any>>(depth.read(500.0, false, feet))
        assertIs<Result.Unusable>(depth.read(500.0, false, unitsOf("length" to "m")))
        // And past the bound in feet too: 1200 ft is 365.76 m.
        assertIs<Result.Unusable>(depth.read(1200.0, false, feet))
    }

    @Test
    fun `a range message names the unit it is stated in`() {
        val read = depth.read(1200.0, false, feet) as Result.Unusable
        assertEquals("depth should be within 0.0..332.35 m, but was 365.76 m", read.reason)
    }

    @Test
    fun `a dimensionless number takes no unit and its message names none`() {
        val cns = NumberDescription("cns", Dimension.DIMENSIONLESS, range = 0.0..200.0)
        val read = cns.read(250.0, false, feet) as Result.Unusable
        assertEquals("cns should be within 0.0..200.0, but was 250.0", read.reason)
    }

    @Test
    fun `a unit nobody knows makes the value unusable, keeping what was written`() {
        val read = depth.read(18.5, false, unitsOf("length" to "fathom")) as Result.Unusable
        assertEquals("depth cannot be read: fathom is not a unit of length", read.reason)
        assertEquals(Stored.Leaf(18.5), read.raw)
    }

    @Test
    fun `a whole number is a count, so no declaration reaches it`() {
        // DATA-68: a count has nothing to be converted into.
        val rating = WholeNumberDescription("rating", range = 1..10)
        assertEquals(7, (rating.read(7, false, feet) as Result.Usable).value)
    }

    @Test
    fun `no other kind is touched by a declaration`() {
        val all = unitsOf("length" to "ft", "time" to "h", "temperature" to "F")
        assertEquals("Blue Quarry", (TextDescription("n").read("Blue Quarry", false, all)
            as Result.Usable).value)
        assertEquals(
            Date(2026, 2, 23),
            (DateDescription("d").read("2026-02-23", false, all) as Result.Usable).value,
        )
        assertEquals(
            Time(1, 30, 0),
            (TimeDescription("t").read("01:30:00", false, all) as Result.Usable).value,
        )
    }
}

/** What a file gets written: twelve significant digits, and no trailing zeros. */
class WrittenFormTest {

    private val mass = NumberDescription("mass", Dimension.MASS)
    private val volume = NumberDescription("volume", Dimension.VOLUME)
    private val duration = NumberDescription("duration", Dimension.TIME)

    @Test
    fun `converting there and back leaves no tail`() {
        // 18.3 lb reads as 8.30013... kg and would write back as 18.300000000000004.
        val pounds = unitsOf("mass" to "lb")
        val held = (mass.read(18.3, false, pounds) as Result.Usable).value as Double
        assertEquals("18.3", mass.format(held, pounds))
    }

    @Test
    fun `no unit in the set writes past its own precision, or leaves a tail`() {
        val every = mapOf(
            Dimension.LENGTH to listOf("m", "ft"),
            Dimension.MASS to listOf("kg", "lb"),
            Dimension.TIME to listOf("s", "min", "h"),
            Dimension.TEMPERATURE to listOf("C", "F", "K"),
            Dimension.VOLUME to listOf("l", "m3"),
            Dimension.PRESSURE to listOf("bar", "psi", "Pa"),
        )
        for ((dimension, names) in every) {
            val description = NumberDescription("x", dimension)
            for (name in names) {
                val units = unitsOf(dimension.name.lowercase() to name)
                val decimals = units.decimalsOf(dimension)
                for (given in listOf(0.5, 1.0, 6.75, 12.25, 18.3, 99.9, 207.0, 1000.0)) {
                    val held = (description.read(given, false, units) as Result.Usable).value
                    val once = description.format(held as Double, units)
                    assertTrue(
                        once.substringAfter('.', "").length <= decimals,
                        "$given $name wrote $once, past the $decimals decimals the unit allows",
                    )
                    val again = (description.read(once, false, units) as Result.Usable).value
                    assertEquals(once, description.format(again as Double, units), "$given $name")
                }
            }
        }
    }

    @Test
    fun `a unit says how finely it is written, in steps of three decimals`() {
        val angle = NumberDescription("longitude", Dimension.ANGLE)
        // Six decimals of a degree is about a tenth of a metre. Three would be a hundred.
        assertEquals("51.123457", angle.format(51.1234567891, Units.DEFAULT))
        assertEquals("4.2731", angle.format(4.2731, Units.DEFAULT))
        val length = NumberDescription("depth", Dimension.LENGTH)
        assertEquals("22.106", length.format(22.1056451613, Units.DEFAULT))
        assertEquals("1.85", length.format(1.85, Units.DEFAULT), "a height is a length too")
        // The step below three is none, where the unit is already finer than anything measured.
        // A whole pascal is a hundred-thousandth of a bar, so nothing is lost by stopping there.
        val pascal = unitsOf("pressure" to "Pa")
        val pressure = NumberDescription("atmospheric_pressure", Dimension.PRESSURE)
        assertEquals("88000", pressure.format(0.880000123, pascal))
    }

    @Test
    fun `a file keeps its own unit, so its own figures come back whole`() {
        // 92 ft is 28.041600000000003 m, which in metres writes as 28.042. The file is in feet
        // and gets 92 back: what a user typed survives, in the unit they typed it in.
        val feet = unitsOf("length" to "ft")
        val depth = NumberDescription("max_depth", Dimension.LENGTH)
        val held = (depth.read(92.0, false, feet) as Result.Usable).value as Double
        assertEquals("92", depth.format(held, feet))
        assertEquals("28.042", depth.format(held, Units.DEFAULT))
    }

    @Test
    fun `a unit's precision is the finest thing written in it, not the finest one read`() {
        // The supplied gear holds displaced volumes of 0.04 l, and an atmospheric pressure is
        // 0.88 bar. A figure chosen for how a depth reads would round both of them away.
        assertEquals("0.04", volume.format(0.04, Units.DEFAULT))
        val pressure = NumberDescription("atmospheric_pressure", Dimension.PRESSURE)
        assertEquals("0.88", pressure.format(0.88, Units.DEFAULT))
    }

    @Test
    fun `a whole measurement is written whole`() {
        assertEquals("30", mass.format(30.0, Units.DEFAULT))
        assertEquals("0", mass.format(0.0, Units.DEFAULT))
        assertEquals("-4", mass.format(-4.0, Units.DEFAULT))
    }

    @Test
    fun `a small value keeps its digits, which decimal places would have cost it`() {
        // 0.0456 l is 0.0000456 m3, which is why the cubic metre carries eight decimals and
        // the litre three: each unit is written as finely as things are written in it.
        val cubic = unitsOf("volume" to "m3")
        val held = (volume.read(0.0000456, false, cubic) as Result.Usable).value as Double
        assertEquals("0.0000456", volume.format(held, cubic))
    }

    @Test
    fun `nothing is written with an exponent`() {
        // JSON allows it and this reader accepts it, and a diver opening the file would not.
        val pascal = unitsOf("pressure" to "Pa")
        val pressure = NumberDescription("pressure", Dimension.PRESSURE)
        val held = (pressure.read(200.0, false, Units.DEFAULT) as Result.Usable).value as Double
        assertEquals("20000000", pressure.format(held, pascal))
        // In cubic metres, which is the unit fine enough to hold a figure this small.
        val cubic = unitsOf("volume" to "m3")
        assertEquals("0.00001", volume.format(0.01, cubic))
        assertEquals("-0.00015", volume.format(-0.15, cubic))
    }

    @Test
    fun `a file that has been written once is written the same every time after`() {
        // 3661 s is 1.0169444... h, which no finite decimal writes exactly. What has to hold is
        // that saving an unchanged logbook produces no diff, so writing is settled after one pass.
        val hours = unitsOf("time" to "h")
        val once = duration.format(3661.0, hours)
        val read = (duration.read(once, false, hours) as Result.Usable).value as Double
        assertEquals(once, duration.format(read, hours))
    }
}
