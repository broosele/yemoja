package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * What a recording's depths were made with.
 *
 * A computer measures pressure and divides by an assumed density, so the figure the maker chose
 * is baked into every depth it wrote. `DATA-59`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private fun profile(water: String, computer: String = "", gear: String = "{}"): Item {
    val names = if (computer.isEmpty()) "" else ""","dive_computer": "$computer""""
    val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "gear.json" to gear,
                "dive/d#0.json" to """{"profiles": {"p1": {$water$names,
                    "depth": [[0, 0], [60, 12.0]]}}}""",
            ),
        ),
        Types.ALL,
    )
    val held = assertIs<Result.Usable<*>>(set["d#0"]!!.read("profiles")).value
    @Suppress("UNCHECKED_CAST")
    val entries = held as Map<String, Element<Any>>
    return (entries["p1"] as Element.Usable).value as Item
}

private fun density(item: Item): Any? = (item.read("density") as? Result.Usable)?.value

class DensityTest {

    private val measured = """{"reef_computer": {"name": "Reef computer", "brand": "Maker",
        "category": "instruments", "salt_density": 1025.0}}"""

    @Test
    fun `fresh water is a thousand, whatever the computer is`() {
        val fresh = profile(""""water_type": "fresh"""", "@reef_computer", measured)
        assertEquals(1000.0, density(fresh))
    }

    @Test
    fun `en13319 is exactly 1020, being a standard rather than a measurement`() {
        assertEquals(
            1020.0,
            density(profile(""""water_type": "en13319"""", "@reef_computer", measured)),
        )
    }

    @Test
    fun `salt is what the computer was set to`() {
        val read = assertIs<Result.Usable<*>>(
            profile(""""water_type": "salt"""", "@reef_computer", measured).read("density"),
        )
        assertEquals(1025.0, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a computer with no figure on it falls back to the usual one`() {
        val plain = """{"reef_computer": {"name": "Reef computer", "category": "instruments"}}"""
        assertEquals(1030.0, density(profile(""""water_type": "salt"""", "@reef_computer", plain)))
    }

    @Test
    fun `no computer named falls back too, which a borrowed one has`() {
        assertEquals(1030.0, density(profile(""""water_type": "salt"""")))
    }

    @Test
    fun `a computer that is only a name falls back, there being no item to ask`() {
        assertEquals(1030.0, density(profile(""""water_type": "salt"""", "someone's Perdix")))
    }

    @Test
    fun `a computer this logbook does not hold falls back`() {
        assertEquals(1030.0, density(profile(""""water_type": "salt"""", "@gone", measured)))
    }

    @Test
    fun `a recording that does not say what it was set to has no density`() {
        // Every profile imported from UDDF: it keeps the converted depth and drops the
        // conversion, so nothing can be worked back until a user says. `DATA-59`.
        assertEquals(Result.Absent, profile(""""start_date": "2026-06-21"""").read("density"))
    }

    @Test
    fun `a written density wins over the computer`() {
        val item = profile(
            """"water_type": "salt", "density": 1035.0""",
            "@reef_computer",
            measured,
        )
        val read = assertIs<Result.Usable<*>>(item.read("density"))
        assertEquals(1035.0, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}
