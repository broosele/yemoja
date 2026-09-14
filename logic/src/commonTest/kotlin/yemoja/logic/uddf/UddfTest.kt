package yemoja.logic.uddf

import yemoja.data.Element
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * A UDDF document read into this model's items.
 *
 * See ../../../../../../doc.md — the mapping is logic/uddf.md.
 */

/** A document holding one dive, whose `informationafterdive` is [after]. */
private fun dived(before: String = "", after: String = "", samples: String = ""): String = """
<uddf xmlns="http://www.streit.cc/uddf/3.2/" version="3.2.0">
  <profiledata>
    <repetitiongroup id="g1">
      <dive id="d1">
        <informationbeforedive>
          <datetime>2024-06-15T10:05:00</datetime>
          $before
        </informationbeforedive>
        <samples>$samples</samples>
        <informationafterdive>
          $after
        </informationafterdive>
      </dive>
    </repetitiongroup>
  </profiledata>
</uddf>
"""

private fun oneDive(text: String) = Uddf.read(text).allOf(Types.DIVE).single()

private fun number(text: String, field: String): Double? =
    (oneDive(text).single<Double>(field) as? Result.Usable)?.value

class ReadDiveTest {

    @Test
    fun `a dive is named for the day it was made on, not for the document's own id`() {
        // A UDDF id is a name for one document's own use and says nothing outside it.
        val set = Uddf.read(dived())
        assertEquals(listOf("2024-06-15#0"), set.allOf(Types.DIVE).map { set.idOf(it) })
    }

    @Test
    fun `a datetime is split into a date and a time`() {
        val dive = oneDive(dived())
        assertEquals("2024-06-15", (dive.read("start_date") as Result.Usable).value.toString())
        assertEquals("10:05:00", (dive.read("start_time") as Result.Usable).value.toString())
    }

    @Test
    fun `a zone after the time is left off`() {
        // gmt_offset lives on the profile and is not read yet, so this drops rather than lies.
        val dive = oneDive(dived().replace("10:05:00<", "10:05:00+02:00<"))
        assertEquals("10:05:00", (dive.read("start_time") as Result.Usable).value.toString())
    }

    @Test
    fun `depths come across as they are, both being metres`() {
        val deepest = number(dived(after = "<greatestdepth>28.4</greatestdepth>"), "max_depth")
        assertEquals(28.4, deepest)
    }

    @Test
    fun `a temperature comes across from kelvin`() {
        // The one conversion that is an offset rather than a factor, and the unit set has it.
        val dive = oneDive(dived(after = "<lowesttemperature>285.15</lowesttemperature>"))
        val environment = (dive.single<OwnedItem>("environment") as Result.Usable).value
        val coldest = environment.single<Double>("bottom_temperature") as Result.Usable
        assertEquals(12.0, coldest.value)
    }

    @Test
    fun `a rating is read from its value, and from the element itself where it says one`() {
        val nested = "<rating><ratingvalue>7</ratingvalue></rating>"
        assertEquals(7, (oneDive(dived(after = nested)).read("rating") as Result.Usable).value)
        val plain = "<rating>7</rating>"
        assertEquals(7, (oneDive(dived(after = plain)).read("rating") as Result.Usable).value)
    }

    @Test
    fun `notes become remarks, paragraph by paragraph`() {
        val notes = "<notes><para>Cold.</para><para>And grey.</para></notes>"
        val dive = oneDive(dived(after = notes))
        val details = (dive.single<OwnedItem>("details") as Result.Usable).value
        assertEquals("Cold.\nAnd grey.", (details.single<String>("remarks") as Result.Usable).value)
    }

    @Test
    fun `how warm the diver was is kept with the gear`() {
        val dive = oneDive(dived(after = "<thermalcomfort>comfortable</thermalcomfort>"))
        val gear = (dive.single<OwnedItem>("gear") as Result.Usable).value
        val said = gear.single<String>("temperature_evaluation") as Result.Usable
        assertEquals("comfortable", said.value)
    }

    @Test
    fun `a field is found whichever half of the dive holds it`() {
        // Which of the two a value sits in is a detail of the format the mapping does not repeat.
        val deepest = number(dived(before = "<greatestdepth>28.4</greatestdepth>"), "max_depth")
        assertEquals(28.4, deepest)
    }

    @Test
    fun `an owned item nothing was said about is not made`() {
        assertEquals(Result.Absent, oneDive(dived()).read("environment"))
    }
}

class ReadProfileTest {

    private val samples = """
        <waypoint><divetime>0</divetime><depth>0.0</depth></waypoint>
        <waypoint><divetime>90</divetime><depth>8.6</depth>
          <temperature>285.15</temperature></waypoint>
        <waypoint><divetime>180</divetime><depth>14.9</depth></waypoint>
    """

    private fun profile(): yemoja.data.Item {
        val dive = oneDive(dived(samples = samples))
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        return (profiles.values.first() as Element.Usable).value
    }

    private fun series(name: String): Series =
        (profile().series<Double>(name) as Result.Usable).value

    @Test
    fun `waypoints become one series per quantity, each with its own times`() {
        // UDDF keeps one list with everything on it; this model keeps a series apiece.
        assertEquals(3, series("depth").size)
        assertEquals(1, series("temperature").size, "only the waypoint that carried one")
    }

    @Test
    fun `a series carries the seconds a waypoint gives and the value it measured`() {
        val depth = series("depth")
        assertEquals(90, depth.secondAt(1))
        assertEquals(8.6, (depth.valueAt(1) as Element.Usable).value)
    }

    @Test
    fun `the recording is under the key its own type proposes for one naming no computer`() {
        val dive = oneDive(dived(samples = samples))
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        assertEquals(listOf("profile"), profiles.keys.toList())
    }

    @Test
    fun `the waypoints after the surfacing are dropped`() {
        // A file is as free as a device to keep recording once the diving stopped. `LOGIC-30`.
        val floating = """
            <waypoint><divetime>0</divetime><depth>0.0</depth></waypoint>
            <waypoint><divetime>90</divetime><depth>14.9</depth></waypoint>
            <waypoint><divetime>180</divetime><depth>0.4</depth></waypoint>
            <waypoint><divetime>240</divetime><depth>0.0</depth></waypoint>
            <waypoint><divetime>300</divetime><depth>0.0</depth></waypoint>
        """
        val dive = oneDive(dived(samples = floating))
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val depth = ((profiles.values.first() as Element.Usable).value
            .series<Double>("depth") as Result.Usable).value
        assertEquals(3, depth.size, "the surfacing is the last of it")
        assertEquals(180, depth.secondAt(2))
    }

    @Test
    fun `a switchmix names the gas source the cylinder it links became`() {
        // The first is what the dive began on, which is where that fact lives. `LOGIC-31`.
        val document = """
<uddf xmlns="http://www.streit.cc/uddf/3.2/" version="3.2.0">
  <gasdefinitions>
    <mix id="air"><o2>0.21</o2></mix>
    <mix id="ean50"><o2>0.50</o2></mix>
  </gasdefinitions>
  <profiledata>
    <repetitiongroup id="g1">
      <dive id="d1">
        <informationbeforedive><datetime>2024-06-15T10:05:00</datetime></informationbeforedive>
        <samples>
          <waypoint><divetime>0</divetime><depth>0.0</depth>
            <switchmix ref="air"/></waypoint>
          <waypoint><divetime>60</divetime><depth>18.0</depth>
            <switchmix ref="air"/></waypoint>
          <waypoint><divetime>1200</divetime><depth>6.0</depth>
            <switchmix ref="ean50"/></waypoint>
          <waypoint><divetime>1800</divetime><depth>0.4</depth></waypoint>
        </samples>
        <tankdata>
          <link ref="air"/><tankpressurebegin>20000000</tankpressurebegin>
        </tankdata>
        <tankdata>
          <link ref="ean50"/><tankpressurebegin>20000000</tankpressurebegin>
        </tankdata>
      </dive>
    </repetitiongroup>
  </profiledata>
</uddf>
"""
        val dive = Uddf.read(document).allOf(Types.DIVE).single()
        assertEquals(listOf("gas", "gas#1"), (dive.keyed<OwnedItem>("gas_sources") as Result.Usable)
            .value.keys.toList())
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val profile = (profiles.values.first() as Element.Usable).value
        val switches = (profile.series<KeyReference>("gas_switches") as Result.Usable).value
        assertEquals(2, switches.size, "the one at a minute repeats the air and is dropped")
        assertEquals(listOf(0, 1200), (0..<switches.size).map { switches.secondAt(it) })
        assertEquals("gas#1", ((switches.valueAt(1) as Element.Usable).value as KeyReference).key)
    }

    @Test
    fun `a dive with no samples has no profile`() {
        assertEquals(Result.Absent, oneDive(dived()).read("profiles"))
    }
}

class ReadTagsTest {

    @Test
    fun `a document with no tags is refused rather than read as empty`() {
        val refused = runCatching { Uddf.read("   ") }.exceptionOrNull()
        assertTrue(refused != null, "reading nothing should say so")
    }

    @Test
    fun `text is read through a namespace prefix`() {
        val prefixed = """
            <u:uddf xmlns:u="http://www.streit.cc/uddf/3.2/">
              <u:profiledata><u:repetitiongroup><u:dive>
                <u:informationbeforedive><u:datetime>2024-06-15T10:05:00</u:datetime>
                </u:informationbeforedive>
              </u:dive></u:repetitiongroup></u:profiledata>
            </u:uddf>
        """
        assertEquals(1, Uddf.read(prefixed).allOf(Types.DIVE).size)
    }

    @Test
    fun `an entity and a CDATA section both come through`() {
        val notes = "<notes><para>cold &amp; grey<![CDATA[, really]]></para></notes>"
        val dive = oneDive(dived(after = notes))
        val details = (dive.single<OwnedItem>("details") as Result.Usable).value
        val remarks = details.single<String>("remarks") as Result.Usable
        assertEquals("cold & grey, really", remarks.value)
    }
}
