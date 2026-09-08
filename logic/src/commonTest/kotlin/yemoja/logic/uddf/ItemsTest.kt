package yemoja.logic.uddf

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Every type a UDDF document holds, read into this model's items.
 *
 * See ../../../../../../doc.md — the field-by-field mapping is logic/uddf.md.
 */

/** A document holding [held], wrapped in the element UDDF puts it in. */
private fun document(held: String): ItemSet =
    Uddf.read("""<uddf xmlns="http://www.streit.cc/uddf/3.2/">$held</uddf>""")

private fun one(set: ItemSet, type: yemoja.data.ItemDescription): Item = set.allOf(type).single()

private fun said(item: Item, field: String): String? =
    (item.single<String>(field) as? Result.Usable)?.value

private fun named(item: Item, field: String): String? =
    when (val held = (item.single<Reference>(field) as? Result.Usable)?.value) {
        is Reference.Identified -> held.id
        is Reference.OneOff -> held.name
        null -> null
    }

class ReadWreckTest {

    private val wrecked = """
        <divesite><site id="s1"><name>Reef</name>
          <wreck id="w1">
            <name>Thistlegorm</name><shiptype>freighter</shiptype>
            <nationality>British</nationality>
            <built><shipyard>Sunderland</shipyard>
              <launchingdate><datetime>1940-04-09</datetime></launchingdate></built>
            <shipdimension><length>126.5</length><beam>17.7</beam></shipdimension>
            <sunk><datetime>1941-10-06T01:30:00</datetime></sunk>
          </wreck>
        </site></divesite>
    """

    @Test
    fun `a wreck inside a site is an item of its own`() {
        val wreck = one(document(wrecked), Types.WRECK)
        assertEquals("Thistlegorm", said(wreck, "name"))
        assertEquals("freighter", said(wreck, "ship_type"))
        assertEquals("Sunderland", said(wreck, "shipyard"))
    }

    @Test
    fun `the site points at it rather than holding it`() {
        // One wreck per site there; here a debris field is three items and one may be shared.
        val set = document(wrecked)
        val site = one(set, Types.DIVE_SITE)
        val held = site.list<Reference>("wrecks") as Result.Usable
        val first = (held.value.first() as Element.Usable).value as Reference.Identified
        assertEquals("thistlegorm", first.id)
        assertTrue(set[first.id] != null)
    }

    @Test
    fun `only the day a ship went down is kept`() {
        // The hour is rarely known and never matters underwater.
        val wreck = one(document(wrecked), Types.WRECK)
        assertEquals("1941-10-06", (wreck.read("sunk") as Result.Usable).value.toString())
    }

    @Test
    fun `a wreck's own name is not read as its site's`() {
        assertEquals("Reef", said(one(document(wrecked), Types.DIVE_SITE), "name"))
    }

    @Test
    fun `tonnage goes to the remarks with its number`() {
        // It is defined in kilograms and means a volume in the world, so a reader judges it.
        val set = document("""<divesite><site><wreck><name>A</name>
            <tonnage>4898</tonnage></wreck></site></divesite>""")
        assertTrue("4898" in said(one(set, Types.WRECK), "remarks").orEmpty())
    }
}

class ReadSiteTest {

    private val site = """
        <divesite><site id="s1">
          <name>Blue Hole</name><aliasname>Le Trou</aliasname>
          <geography><latitude>28.5721</latitude><longitude>34.5372</longitude>
            <altitude>0</altitude></geography>
          <sitedata><maximumdepth>102</maximumdepth><bottom>sand and coral</bottom>
            <environment>open water</environment>
            <rating><ratingvalue>9</ratingvalue></rating></sitedata>
          <notes><para>Deep.</para></notes>
        </site></divesite>
    """

    @Test
    fun `a site reads its name, its place and what it is like`() {
        val held = one(document(site), Types.DIVE_SITE)
        assertEquals("Blue Hole", said(held, "name"))
        val aliases = held.list<String>("alternative_names") as Result.Usable
        assertEquals("Le Trou", (aliases.value.single() as Element.Usable).value)
        assertEquals(28.5721, (held.single<Double>("latitude") as Result.Usable).value)
        assertEquals("sand and coral", said(held, "substrate"))
        assertEquals("Deep.", said(held, "remarks"))
    }

    @Test
    fun `a rating is read from the value inside it`() {
        val held = one(document(site), Types.DIVE_SITE).read("rating") as Result.Usable
        assertEquals(9, held.value)
    }

    @Test
    fun `the latest of several ratings is the one kept`() {
        // Ours holds the rating that stands; theirs may hold every one ever formed.
        val many = """<divesite><site><name>A</name>
            <rating><datetime>2020-01-01</datetime><ratingvalue>3</ratingvalue></rating>
            <rating><datetime>2024-01-01</datetime><ratingvalue>8</ratingvalue></rating>
        </site></divesite>"""
        val held = one(document(many), Types.DIVE_SITE).read("rating") as Result.Usable
        assertEquals(8, held.value)
    }
}

class ReadPersonTest {

    private val diver = """
        <diver>
          <owner id="p1">
            <personal><firstname>Anna</firstname><lastname>de Vries</lastname>
              <birthdate><datetime>1988-03-11</datetime></birthdate></personal>
            <address><street>Kade 4</street><city>Rotterdam</city></address>
            <contact><email>a@example.org</email><phone>0100</phone></contact>
            <medical><examination><datetime>2019-01-01</datetime></examination>
              <examination><datetime>2024-05-02</datetime></examination></medical>
            <education><certification><level>Open Water</level>
              <certificatenumber>12345</certificatenumber>
              <issuedate><datetime>2015-07-01</datetime></issuedate></certification></education>
          </owner>
          <buddy id="p2"><personal><firstname>Tom</firstname></personal></buddy>
        </diver>
    """

    @Test
    fun `an owner and a buddy are both people, which is one type here`() {
        val set = document(diver)
        assertEquals(2, set.allOf(Types.PERSON).size)
    }

    @Test
    fun `a person reads its name and how to reach it`() {
        val set = document(diver)
        val anna = set["anna_de_vries"]!!
        assertEquals("Anna", said(anna, "first_name"))
        assertEquals("a@example.org", said(anna, "email"))
    }

    @Test
    fun `an address in parts becomes one piece of prose`() {
        // Lossy on the way back out, and only there.
        assertEquals("Kade 4, Rotterdam", said(document(diver)["anna_de_vries"]!!, "address"))
    }

    @Test
    fun `the latest examination is the medical check, and the rest is lost`() {
        // `DATA-37`: this model records the state where theirs records a history.
        val medical = (document(diver)["anna_de_vries"]!!
            .single<OwnedItem>("medical") as Result.Usable).value
        val checked = (medical.read("last_medical_check") as Result.Usable).value
        assertEquals("2024-05-02", checked.toString())
    }

    @Test
    fun `a certification becomes a course, without the certification it points at`() {
        // A course points at a library item, which a document cannot name.
        val courses = (document(diver)["anna_de_vries"]!!
            .keyed<OwnedItem>("courses") as Result.Usable).value
        val course = (courses.values.first() as Element.Usable).value
        assertEquals("2015-07-01", (course.read("date") as Result.Usable).value.toString())
        assertEquals("12345", said(course, "number"))
        assertEquals(Result.Absent, course.read("certification"))
    }
}

class ReadGearTest {

    @Test
    fun `each element sets both axes from its own table`() {
        val set = document("""<diver><owner><equipment>
            <mask id="m1"><name>Mine</name></mask>
            <suit id="s1"><name>Drysuit</name></suit>
            <divecomputer id="c1"><name>Reef</name>
              <manufacturer><name>Suunto</name></manufacturer>
              <model>D5</model><serialnumber>99</serialnumber></divecomputer>
        </equipment></owner></diver>""")
        assertEquals(3, set.allOf(Types.GEAR).size)
        assertEquals("ABC", said(set["mine"]!!, "category"))
        assertEquals("mask", said(set["mine"]!!, "kind"))
        assertEquals("suit", said(set["drysuit"]!!, "category"))
        assertNull(said(set["drysuit"]!!, "kind"), "there is no way to say what sort of suit")
        assertEquals("Suunto", said(set["reef"]!!, "brand"))
        assertEquals("99", said(set["reef"]!!, "serial"))
    }

    @Test
    fun `a rebreather is not read at all`() {
        // `FEAT-21` rules them out.
        val set = document("""<diver><owner><equipment>
            <rebreather id="r1"><name>Inspiration</name></rebreather>
        </equipment></owner></diver>""")
        assertEquals(0, set.allOf(Types.GEAR).size)
    }
}

class ReadOperatorTest {

    @Test
    fun `a divebase is an operator`() {
        val set = document("""<divebase id="b1"><name>Northshore</name>
            <contact><email>b@example.org</email><homepage>example.org</homepage></contact>
            <rating><ratingvalue>7</ratingvalue></rating></divebase>""")
        val held = one(set, Types.OPERATOR)
        assertEquals("Northshore", said(held, "name"))
        assertEquals("b@example.org", said(held, "email"))
        assertEquals("example.org", said(held, "website"))
    }
}

class ReadTripTest {

    private val tripped = """
        <divetrip><trip><name>Provence</name>
          <trippart id="t1"><name>Calanques</name>
            <dateoftrip><startdate><datetime>2025-05-29</datetime></startdate>
              <enddate><datetime>2025-06-01</datetime></enddate></dateoftrip>
            <relateddives><link ref="d1"/></relateddives>
            <notes><para>Warm.</para></notes>
          </trippart></trip></divetrip>
        <profiledata><repetitiongroup><dive id="d1">
          <informationbeforedive><datetime>2025-05-30T10:00:00</datetime></informationbeforedive>
        </dive></repetitiongroup></profiledata>
    """

    @Test
    fun `a trippart is the trip, since it carries the fields`() {
        val trip = one(document(tripped), Types.DIVE_TRIP)
        assertEquals("Calanques", said(trip, "name"))
        assertEquals("2025-05-29", (trip.read("start_date") as Result.Usable).value.toString())
    }

    @Test
    fun `relateddives points the other way here, so the dive names the trip`() {
        val set = document(tripped)
        val dive = one(set, Types.DIVE)
        val details = (dive.single<OwnedItem>("details") as Result.Usable).value
        assertEquals("calanques", named(details, "dive_trip"))
    }
}

class ReadDiveLinksTest {

    private val linked = """
        <divesite><site id="s1"><name>Reef</name></site></divesite>
        <diver><owner id="p1"><equipment>
          <mask id="m1"><name>Mine</name></mask>
        </equipment></owner></diver>
        <profiledata><repetitiongroup>
          <dive id="d1"><informationbeforedive>
            <datetime>2025-05-30T10:00:00</datetime><link ref="s1"/>
          </informationbeforedive>
          <equipmentused><link ref="m1"/></equipmentused>
          <tankdata><link ref="m1"/><tankpressurebegin>20000000</tankpressurebegin>
            <tankvolume>0.012</tankvolume></tankdata>
          </dive>
          <dive id="d2"><informationbeforedive>
            <datetime>2025-05-30T12:00:00</datetime></informationbeforedive></dive>
        </repetitiongroup></profiledata>
    """

    @Test
    fun `a link under informationbeforedive is where the dive was`() {
        // The parent supplies the role; the ref says only which item.
        assertEquals("reef", named(document(linked).allOf(Types.DIVE).first(), "dive_site"))
    }

    @Test
    fun `a link under equipmentused is gear the dive wore`() {
        val dive = document(linked).allOf(Types.DIVE).first()
        val gear = (dive.single<OwnedItem>("gear") as Result.Usable).value
        val items = gear.list<Reference>("items") as Result.Usable
        assertEquals(1, items.value.size)
    }

    @Test
    fun `a tankdata becomes a gas source, in the units UDDF fixes`() {
        val dive = document(linked).allOf(Types.DIVE).first()
        val sources = (dive.keyed<OwnedItem>("gas_sources") as Result.Usable).value
        val gas = (sources.values.first() as Element.Usable).value
        val pressure = (gas.single<Double>("start_pressure") as Result.Usable).value
        assertTrue(pressure > 199.999 && pressure < 200.001, "$pressure bar from 200 in pascals")
        assertEquals(12.0, (gas.single<Double>("volume") as Result.Usable).value)
    }

    @Test
    fun `each dive of a group takes the one before it, and the first takes none`() {
        // This model does not group; the chain a group describes says the same. `DATA-60`.
        val set = document(linked)
        val dives = set.allOf(Types.DIVE).sortedBy { set.idOf(it) }
        assertEquals(Result.Absent, dives.first().read("previous_dive"))
        assertEquals(set.idOf(dives.first()), named(dives.last(), "previous_dive"))
    }
}
