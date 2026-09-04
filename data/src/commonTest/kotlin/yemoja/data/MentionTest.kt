package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Candidates in free text: what `JSON-23` calls a mention.
 *
 * See ../../../../../doc.md — the format's own document is data/json/doc.md.
 */

private fun ids(text: String): List<String> = mentionsIn(text).map { it.id }

class MentionTest {

    @Test
    fun `a mention is an at and the name after it`() {
        assertEquals(listOf("willy"), ids("We met @willy on the wall"))
    }

    @Test
    fun `punctuation after a name is punctuation`() {
        assertEquals(
            listOf("willy", "padi_wreck"),
            ids("We met @willy, who was on his @padi_wreck course."),
        )
    }

    @Test
    fun `a dot inside a name is kept, only a trailing one coming off`() {
        val block = "generic_0.5_kg_lead_weight"
        assertEquals(listOf(block), ids("Two of the belt were @$block."))
    }

    @Test
    fun `a dive is named whole, hyphens and index alike`() {
        assertEquals(listOf("2026-02-23#0"), ids("See @2026-02-23#0 for the shot line"))
    }

    @Test
    fun `capitals do not matter, an id being lowercase by rule`() {
        assertEquals(listOf("willy"), ids("@Willy came down after me"))
    }

    @Test
    fun `an at with nothing usable after it is no candidate`() {
        assertEquals(emptyList(), ids("max depth @ 22m"))
        assertEquals(emptyList(), ids("ended @"))
    }

    @Test
    fun `an address is a candidate like any other, and resolves to nothing`() {
        // Nothing says where a mention may begin, because nothing could: what separates an
        // address from a name is intent. Resolution is what makes it harmless.
        assertEquals(listOf("example.invalid"), ids("Mail him on tom@example.invalid"))
    }

    @Test
    fun `brackets and quotes do not disable one`() {
        assertEquals(listOf("willy", "anna"), ids("""(@willy) and "@anna" both came"""))
    }

    @Test
    fun `where it sits is where it sits, the at included`() {
        val text = "We met @willy, later"
        assertEquals(listOf(Mention(7..<13, "willy")), mentionsIn(text))
        assertEquals("@willy", text.substring(7, 13))
    }

    @Test
    fun `several on one line are all found, in order`() {
        assertEquals(listOf("a", "b", "c"), ids("@a then @b then @c"))
    }

    @Test
    fun `an at against an at finds the second`() {
        // A handle from a system that writes user@server has one name, not two.
        assertEquals(listOf("dive.social"), ids("@@dive.social".drop(1)))
    }

    @Test
    fun `text with no at holds nothing`() {
        assertEquals(emptyList(), ids("Flat calm all afternoon."))
    }
}
