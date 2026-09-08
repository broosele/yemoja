package yemoja.logic.divecomputer

import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * Whether the library loads and answers, which is the whole of what can be checked without a
 * dive computer to plug in.
 *
 * See ../../../../../../doc.md.
 */
class LibraryTest {

    @Test
    fun `it says which models it can read, or says none where it is not there`() {
        val models = supported()
        if (Libdivecomputer.LOADED == null) {
            assertTrue(models.isEmpty(), "no library here, so nothing can be read")
            return
        }
        assertTrue(models.size > 100, "it knows hundreds of models, and said ${models.size}")
        assertTrue(models.any { it.vendor == "Suunto" }, "Suunto is not among them")
        assertTrue(models.all { it.product.isNotEmpty() }, "every model has a product name")
    }
}
