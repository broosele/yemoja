package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/*
 * An owned item holding a keyed collection of its own, and where a key reference into one is
 * rooted. Invented types again: this layer knows nothing about diving. `TEST-4`.
 */

private val BOOK = ItemDescription("book", listOf(TextDescription("title")))

/**
 * A shelf keeps books of its own, and says which it is known for.
 *
 * `known_for` is the case the rooting rule exists for: a shelf with books names one of those, and
 * a shelf with none names one of the library's.
 */
private val SHELF = ItemDescription(
    "shelf",
    listOf(
        TextDescription("name"),
        OwnedItemDescription("books", BOOK, cardinality = Cardinality.KEYED),
        KeyReferenceDescription("known_for", collection = "books"),
    ),
)

private val LIBRARY = ItemDescription(
    "library",
    listOf(
        TextDescription("name"),
        OwnedItemDescription("shelves", SHELF, cardinality = Cardinality.KEYED),
        OwnedItemDescription("books", BOOK, cardinality = Cardinality.KEYED),
    ),
)

private fun library(stored: Stored.Members): ReferenceableItem =
    ItemReader.read(LIBRARY, stored, ItemSet(listOf(LIBRARY)), Units.DEFAULT)

private fun members(vararg held: Pair<String, Stored>): Stored.Members =
    Stored.Members(linkedMapOf(*held))

private fun entryOf(item: Item, collection: String, key: String): OwnedItem {
    val held = (item.keyed<OwnedItem>(collection) as Result.Usable).value
    return assertIs<Element.Usable<OwnedItem>>(held.getValue(key)).value
}

/** A library whose one shelf holds one book, and which holds one of its own besides. */
private val STOCKED = members(
    "name" to Stored.Leaf("Municipal"),
    "shelves" to members(
        "s1" to members(
            "name" to Stored.Leaf("Travel"),
            "books" to members("b1" to members("title" to Stored.Leaf("Sea Room"))),
            "known_for" to Stored.Leaf("*b1"),
        ),
    ),
    "books" to members("b9" to members("title" to Stored.Leaf("The Periodic Table"))),
)

class NestingTest {

    @Test
    fun `a keyed collection inside a keyed owned item reads, and knows its owner`() {
        val shelf = entryOf(library(STOCKED), "shelves", "s1")
        val book = entryOf(shelf, "books", "b1")

        assertEquals("Sea Room", (book.single<String>("title") as Result.Usable).value)
        assertSame(shelf, book.parent)
    }

    @Test
    fun `it goes back to what it was read from`() {
        assertEquals(STOCKED, ItemWriter.write(library(STOCKED), Units.DEFAULT))
    }

    @Test
    fun `a key reference is rooted at the collection its own item holds`() {
        val shelf = entryOf(library(STOCKED), "shelves", "s1")

        assertSame(shelf, shelf.rootOf("books"))
    }

    @Test
    fun `a key reference is rooted at the owner where the item holds none`() {
        val bare = members(
            "shelves" to members("s1" to members("name" to Stored.Leaf("Travel"))),
            "books" to members("b9" to members("title" to Stored.Leaf("The Periodic Table"))),
        )
        val library = library(bare)
        val shelf = entryOf(library, "shelves", "s1")

        assertSame(library, shelf.rootOf("books"))
    }

    @Test
    fun `where neither holds any it is still the nearest that could`() {
        val empty = members("shelves" to members("s1" to members("name" to Stored.Leaf("Travel"))))
        val shelf = entryOf(library(empty), "shelves", "s1")

        assertSame(shelf, shelf.rootOf("books"))
    }

    @Test
    fun `a collection nobody declares is rooted nowhere`() {
        val shelf = entryOf(library(STOCKED), "shelves", "s1")

        assertNull(shelf.rootOf("pamphlets"))
    }
}
