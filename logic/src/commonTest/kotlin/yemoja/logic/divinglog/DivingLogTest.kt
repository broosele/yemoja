package yemoja.logic.divinglog

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.sqlite.SqliteFormatException
import yemoja.logic.Types
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A Diving Log database read into items. See ../../../../../../divinglog.md — `DLOG-1` to `DLOG-4`.
 *
 * The database is invented, and tool/divinglogfixture.py says what each of its rows is there for.
 */

@OptIn(ExperimentalEncodingApi::class)
private val READ: Read by lazy { DivingLog.read(Base64.decode(DATABASE)) }

private fun dive(number: Int): ReferenceableItem =
    READ.items.allOf(Types.DIVE).single { (usable(it, "dive_number") as? Number)?.toInt() == number }

private fun usable(item: Item, field: String): Any? = (item.read(field) as? Result.Usable)?.value

@Suppress("UNCHECKED_CAST")
private fun profileOf(dive: Item): OwnedItem =
    ((usable(dive, "profiles") as Map<String, Element<Any>>).values.single() as Element.Usable).value as OwnedItem

/** A series as its times and its values, numbers as plain doubles. */
private fun pointsOf(item: Item, field: String): List<Pair<Int, Any?>> {
    val series = usable(item, field) as? Series ?: return emptyList()
    return (0..<series.size).map { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value
        series.secondAt(at) to ((value as? Number)?.toDouble() ?: (value as? KeyReference)?.key ?: value)
    }
}

private fun remarksOf(item: Item): String = usable(item, "remarks") as? String ?: ""

class DivingLogTest {

    @Test
    fun `every kind of row becomes the item it is, and the dives come out in order`() {
        assertEquals(3, READ.items.allOf(Types.DIVE).size)
        assertEquals(2, READ.items.allOf(Types.DIVE_SITE).size)
        assertEquals(2, READ.items.allOf(Types.PERSON).size, "the buddy and the user")
        assertEquals(1, READ.items.allOf(Types.OPERATOR).size)
        assertEquals(1, READ.items.allOf(Types.DIVE_TRIP).size)
        assertEquals(1, READ.items.allOf(Types.GEAR).size)
    }

    @Test
    fun `a dive keeps its number, its start, its depth, its rating out of ten and what it points at`() {
        val first = dive(1)
        assertEquals("2030-05-02", usable(first, "start_date").toString())
        assertEquals("21:05", usable(first, "start_time").toString().take(5))
        assertEquals(12.0, usable(first, "max_depth"))
        assertEquals(8L, (usable(first, "rating") as Number).toLong(), "four stars are eight out of ten")
        val site = (usable(first, "dive_site") as Reference.Identified).id
        assertEquals("Nowhere Quarry", usable(READ.items[site]!!, "name"))
        assertTrue(usable(first, "buddies").toString().contains("ada"), usable(first, "buddies").toString())
        val tags = (usable(first, "tags") as List<*>).map { (it as Element.Usable<*>).value }
        assertEquals(listOf("night", "mountain lake"), tags)
    }

    @Test
    fun `what was typed and has no field goes to the remarks, labelled`() {
        val remarks = remarksOf(dive(1))
        assertTrue(remarks.startsWith("First line.\nSecond line."), "the user's own words first, line ends made plain")
        assertTrue("Visibility: good" in remarks, remarks)
        assertTrue("Entry: shore" in remarks, remarks)
        assertTrue("Weather: clear" in remarks, remarks)
        val second = remarksOf(dive(2))
        assertTrue("Boat: Blue Pretender" in second && "Divemaster: Dee Master" in second, second)
        assertTrue("Visibility: poor" in second && "Entry: boat" in second, second)
        val third = remarksOf(dive(3))
        assertTrue("Fish seen: Pretend wrasse" in third, third)
        assertTrue("Solo: yes" in third, third)
        assertTrue("Visibility" !in third, "nought is no visibility given")
        assertTrue("Entry" !in third, "an entry code of unknown meaning is not guessed at")
    }

    @Test
    fun `a dive with no samples takes its logged time as its duration`() {
        assertEquals(2520.0, (usable(dive(3), "duration") as Number).toDouble())
        assertNull(usable(dive(3), "profiles"))
    }

    @Test
    fun `a profile is read from its columns, and what came after surfacing is cut`() {
        val profile = profileOf(dive(2))
        val depth = pointsOf(profile, "depth")
        assertEquals(listOf(0, 60, 120, 180, 240, 300, 360, 420), depth.map { it.first }, "the surfacing kept, the floating after it not")
        assertEquals(30.0, depth[1].second)
        assertEquals("Pretend Computer", usable(profile, "dive_computer").toString().removePrefix("OneOff(name=").removeSuffix(")"))
        assertEquals(17.5, pointsOf(profileOf(dive(1)), "temperature")[1].second, "tenths of a degree")
    }

    @Test
    fun `a stop is read while the limit is below its ceiling, and the limit pauses for it`() {
        val profile = profileOf(dive(2))
        assertEquals(listOf(120 to 6.0, 360 to 0.0), pointsOf(profile, "decostop"), "cleared where the limit returns to 99")
        val limits = pointsOf(profile, "no_deco_time")
        assertEquals(listOf(0 to 99 * 60.0, 60 to 20 * 60.0, 360 to 99 * 60.0), limits, "nothing written during the stop")
    }

    @Test
    fun `the CNS clock is in percent, and stops where its column saturates`() {
        val clock = pointsOf(profileOf(dive(2)), "cns")
        assertEquals(listOf(0 to 1.0, 60 to 3.0, 120 to 6.0, 180 to 9.0), clock)
        assertEquals(listOf(0 to 0.0, 60 to 1.0, 120 to 2.0), pointsOf(profileOf(dive(1)), "cns"), "a step at each change")
    }

    @Test
    fun `the main cylinder and the first other become the profile's two, and copies in the slots are not read`() {
        @Suppress("UNCHECKED_CAST")
        val sources = (usable(dive(2), "gas_sources") as Map<String, Element<Any>>)
        assertEquals(listOf("gas", "gas#1"), sources.keys.toList(), "three slots of EAN50, two of them copies")
        val deco = (sources.getValue("gas#1") as Element.Usable).value as OwnedItem
        assertEquals("EAN50", usable(deco, "gas_type").toString())
        val profile = profileOf(dive(2))
        assertEquals(listOf(0 to "gas", 180 to "gas#1"), pointsOf(profile, "gas_switches"))
        val pressures = (usable(profile, "pressures") as Map<*, *>)
            .mapValues { (_, held) -> (held as Element.Usable<*>).value as Series }
        assertEquals(setOf("gas", "gas#1"), pressures.keys, "each cylinder's pressure under its own key")
        assertEquals(210.0, ((pressures.getValue("gas").valueAt(0) as Element.Usable).value as Number).toDouble(), "tenths of a bar")
    }

    @Test
    fun `a site takes its position, its rating, and the water its dives were in where it gives none`() {
        val quarry = READ.items.allOf(Types.DIVE_SITE).single { usable(it, "name") == "Nowhere Quarry" }
        assertEquals(12.5, usable(quarry, "latitude"))
        assertEquals(-3.25, usable(quarry, "longitude"), "west is negative")
        assertEquals("fresh", usable(quarry, "water_type"))
        assertEquals(8L, (usable(quarry, "rating") as Number).toLong())
        assertTrue("Country: Atlantis" in remarksOf(quarry), remarksOf(quarry))
        val reef = READ.items.allOf(Types.DIVE_SITE).single { usable(it, "name") == "Fictional Reef" }
        assertEquals("salt", usable(reef, "water_type"), "its own water, given")
    }

    @Test
    fun `the user keeps their medical and their certifications`() {
        val user = READ.items.allOf(Types.PERSON).single { usable(it, "first_name") == "Una" }
        val medical = usable(user, "medical") as OwnedItem
        assertEquals("2029-12-01", usable(medical, "last_medical_check").toString())
        assertEquals("0+", usable(medical, "blood_group"))
        @Suppress("UNCHECKED_CAST")
        val course = ((usable(user, "courses") as Map<String, Element<Any>>).values.single() as Element.Usable).value as OwnedItem
        assertEquals("OW-1", usable(course, "number"))
        assertTrue("Open Water Diver, Some Agency, instructor Ivan Instructor no. 42" == usable(course, "remarks"), usable(course, "remarks").toString())
        assertTrue("Emergency contact: Someone at home" in remarksOf(user), remarksOf(user))
    }

    @Test
    fun `the import says which version it read and what it left`() {
        assertEquals(
            "A Diving Log 4.2.0 database: 3 dives, 2 profiles. Left out, being images: 1 picture.",
            READ.said,
        )
    }

    @Test
    fun `a database that is not Diving Log's is refused, and so is a file that is not SQLite`() {
        @OptIn(ExperimentalEncodingApi::class)
        val other = Base64.decode(NOT_DIVING_LOG)
        assertTrue("no Logbook table" in assertFailsWith<DivingLogFormatException> { DivingLog.read(other) }.message!!)
        assertFailsWith<SqliteFormatException> { DivingLog.read("not a database".encodeToByteArray()) }
    }

    @Test
    fun `a position in degrees, minutes and seconds reads as degrees, south and west below nought`() {
        assertEquals(12.5, degreesOf("12°30'00.00\"N"))
        assertEquals(-33.875, degreesOf("33°52'30\"S"))
        assertEquals(4.5, degreesOf("4.5"))
        assertNull(degreesOf("nowhere"))
    }

    @Test
    fun `every value read is one its field accepts`() {
        val refused = ArrayList<String>()
        fun walk(item: Item, where: String) {
            for ((name, result) in item.fields) {
                if (result is Result.Unusable) refused += "$where.$name: ${result.reason}"
                val value = (result as? Result.Usable)?.value
                if (value is OwnedItem) walk(value, "$where.$name")
                if (value is Map<*, *>) for (entry in value.values) (entry as? Element.Usable<*>)?.let { (it.value as? OwnedItem)?.let { owned -> walk(owned, "$where.$name") } }
            }
        }
        for (type in Types.ALL) for (item in READ.items.allOf(type)) walk(item, type.name)
        assertEquals(emptyList(), refused)
    }
}
