package yemoja.logic.uddf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * The closed vocabularies both formats have, crossing between them.
 *
 * See ../../../../../../doc.md — the comparison is logic/uddf.md.
 */
class WordsTest {

    @Test
    fun `every current crosses and comes back the same, the scale being UDDF's own`() {
        for (step in listOf("none", "very mild", "mild", "moderate", "hard", "very hard")) {
            assertEquals(step, CURRENTS.theirs(step)?.let { CURRENTS.ours(it) }, step)
        }
    }

    @Test
    fun `a joined pair leaves as one word and comes back as its first half`() {
        assertEquals("ocean-sea", ENVIRONMENTS.theirs("sea"))
        assertEquals("ocean", ENVIRONMENTS.ours("ocean-sea"))
        assertEquals("hyperbaric-chamber", ENVIRONMENTS.theirs("hyperbaric chamber"))
    }

    @Test
    fun `a word either side has no counterpart for crosses as nothing`() {
        assertNull(WARMTHS.theirs("lukewarm"), "free text has no place in a closed list")
        assertNull(ENVIRONMENTS.ours("unknown"), "which is what absent already says")
    }

    @Test
    fun `how warm is matched without regard to case or the spaces around it`() {
        assertEquals("comfortable", WARMTHS.theirs("Good"))
        assertEquals("very cold", WARMTHS.ours(" very-cold "))
    }
}
