package yemoja.logic

import yemoja.data.ItemDescription
import yemoja.data.ItemReader
import yemoja.data.ItemSet
import yemoja.data.OwnedItemDescription
import yemoja.data.Stored
import yemoja.data.Units
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * What a keyed collection calls a new entry. `JSON-18`.
 *
 * See ../../../../../doc.md.
 */

/** The type of what sits under [field]'s keys, reached the way anything else reaches it. */
private fun within(owner: ItemDescription, field: String): ItemDescription =
    (owner[field] as OwnedItemDescription).description

/** The key [within] would propose for an entry holding [fields]. */
private fun proposed(within: ItemDescription, vararg fields: Pair<String, Any>): String {
    val set = ItemSet(Types.ALL)
    val held = Stored.Members(fields.associate { (name, value) -> name to Stored.Leaf(value) })
    val entry = ItemReader.read(within, held, set, Units.DEFAULT)
    return within.proposedId!!(entry)
}

class ProposedKeyTest {

    private val profile = within(Types.DIVE, "profiles")

    private val gasSource = within(Types.DIVE, "gas_sources")

    private val course = within(Types.PERSON, "courses")

    private val maintenance = within(Types.GEAR, "maintenances")

    @Test
    fun `a profile is keyed by the computer that recorded it`() {
        assertEquals("perdix", proposed(profile, "dive_computer" to "@perdix"))
    }

    @Test
    fun `a computer named rather than pointed at still names the profile`() {
        // A one-off reference is a name somebody wrote, and it is as good a key as an id.
        assertEquals("borrowed_puck", proposed(profile, "dive_computer" to "Borrowed Puck"))
    }

    @Test
    fun `a profile from no computer at all is called profile`() {
        assertEquals("profile", proposed(profile))
    }

    @Test
    fun `a gas source is keyed by what it was for`() {
        assertEquals("bottom", proposed(gasSource, "usage" to "bottom", "gas_type" to "EAN32"))
    }

    @Test
    fun `and by the gas in it where nobody said what it was for`() {
        assertEquals("ean32", proposed(gasSource, "gas_type" to "EAN32"))
    }

    @Test
    fun `a gas source saying neither is called gas`() {
        assertEquals("gas", proposed(gasSource))
    }

    @Test
    fun `a course is keyed by the certification it was for`() {
        assertEquals(
            "open_water",
            proposed(course, "certification" to "@open_water", "date" to "2022-06-01"),
        )
    }

    @Test
    fun `and by its date where the certification was never entered`() {
        assertEquals("2022-06-01", proposed(course, "date" to "2022-06-01"))
    }

    @Test
    fun `a maintenance is keyed by what was done and when`() {
        // Both, because the same thing happens again and again: a date alone would not say what
        // was done, and a type alone would count the times it was.
        assertEquals(
            "annual_service_2026-03-04",
            proposed(maintenance, "type" to "annual service", "date" to "2026-03-04"),
        )
    }

    @Test
    fun `a maintenance saying only one of them takes that one`() {
        assertEquals("2026-03-04", proposed(maintenance, "date" to "2026-03-04"))
        assertEquals("annual_service", proposed(maintenance, "type" to "annual service"))
    }

    @Test
    fun `a maintenance saying neither is called maintenance`() {
        assertEquals("maintenance", proposed(maintenance))
    }
}

/** The rule a proposal goes through before it is used, for keys and for ids alike. */
class FreeNameTest {

    @Test
    fun `a free name is taken as it stands`() {
        assertEquals("course", freeName("course") { it in setOf("dive") })
    }

    @Test
    fun `a taken one goes to index one, zero being what a bare name means`() {
        assertEquals("course#1", freeName("course") { it == "course" })
    }

    @Test
    fun `a proposal carrying an index goes to one past it`() {
        // A dive proposes `#0` because two dives a day are ordinary. `DATA-84`.
        assertEquals("2026-03-04#1", freeName("2026-03-04#0") { it == "2026-03-04#0" })
    }

    @Test
    fun `it keeps going until something is free`() {
        val taken = setOf("course", "course#1", "course#2")
        assertEquals("course#3", freeName("course") { it in taken })
    }

    @Test
    fun `a name given up comes back`() {
        // The lowest free one, not the next unused. `JSON-18` binds reuse to there being a
        // journal to protect, and there is none yet.
        assertEquals("course#1", freeName("course") { it in setOf("course", "course#2") })
    }
}
