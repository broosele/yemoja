package yemoja.logic

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * How often each worked-out field of a logbook gets a value. See ../../../../../../testing.md.
 */
class CoverageTest {

    private val covered = coverageOf(
        LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/sampled#0.json" to """{"buddies": ["anna"],
                        "gas_sources": {"g1": {"volume": 12, "sac": 15}},
                        "profiles": {"p1": {"temperature": [[0, 24], [600, 10], [1800, 16]]}}}""",
                    "dive/written#0.json" to """{"environment": {"bottom_temperature": 18}}""",
                    "dive/bare#0.json" to """{"dive_number": 3}""",
                ),
            ),
            Types.ALL,
        ),
    ).associateBy { it.path }

    @Test
    fun `each field is counted as worked out, written, or absent`() {
        val coldest = covered.getValue("dive.environment.bottom_temperature")
        assertEquals(1, coldest.worked, "the one with a recording")
        assertEquals(1, coldest.written)
        assertEquals(1, coldest.absent, "the bare dive is counted, though it has no conditions")
        assertEquals(3, coldest.asked)
    }

    @Test
    fun `an entry of a keyed collection is counted under a star`() {
        val sac = covered.getValue("dive.gas_sources.*.sac")
        assertEquals(1, sac.written)
        assertEquals(1, sac.asked, "one source in the whole logbook")
    }

    @Test
    fun `a field nothing works out is not listed`() {
        assertNull(covered["dive.dive_number"])
        assertEquals(3, covered.getValue("dive.buddy_count").asked, "and one that is, is")
    }
}
