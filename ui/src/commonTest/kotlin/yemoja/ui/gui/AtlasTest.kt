package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/*
 * The map's geometry as it is read and chosen. See ../../../../../../gui/doc.md — `GUI-25`.
 */
class AtlasTest {

    private val frame = Frame(west = 30.0, east = 36.0, south = 22.0, north = 30.0)

    @Test
    fun `a shape is one line of longitude and latitude pairs, with its box worked out`() {
        val shapes = outlinesOf("4.0 51.0 5.0 52.0 4.5 53.0\n\n-1.5 50.0 -1.0 50.5\n")
        assertEquals(2, shapes.size)
        assertEquals(3, shapes[0].size)
        assertEquals(4.0, shapes[0].west)
        assertEquals(5.0, shapes[0].east)
        assertEquals(51.0, shapes[0].south)
        assertEquals(53.0, shapes[0].north)
    }

    @Test
    fun `a ring after the first is a hole, and the box is round all of them`() {
        val outside = "0.0 0.0 10.0 0.0 10.0 10.0 0.0 10.0"
        val hole = "4.0 4.0 6.0 4.0 6.0 6.0 4.0 6.0"
        val shape = outlinesOf("$outside / $hole\n").single()
        assertEquals(2, shape.rings.size)
        assertEquals(8, shape.size)
        assertEquals(10.0, shape.east)
    }

    @Test
    fun `a city is a tab-separated line`() {
        val text = "Tokyo\t35.687\t139.749\t0\t35676000\nZierikzee\t51.650\t3.917\t8\t9000\n"
        val cities = citiesOf(text)
        assertEquals("Zierikzee", cities[1].name)
        assertEquals(8, cities[1].rank)
        assertEquals(139.749, cities[0].longitude)
    }

    @Test
    fun `the scale follows the width of the frame`() {
        val one = Layer(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val two = Layer(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val three = Layer(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val atlas = Atlas(one, two, three)
        assertSame(one, atlas.layerFor(Frame(-180.0, 180.0, -90.0, 90.0)), "the world")
        assertSame(two, atlas.layerFor(Frame(-10.0, 40.0, 35.0, 70.0)), "a continent")
        assertSame(three, atlas.layerFor(frame), "a country")
    }

    @Test
    fun `a box overlaps the frame where any of it lies inside`() {
        assertTrue(frame.overlaps(32.0, 33.0, 24.0, 25.0), "inside")
        assertTrue(frame.overlaps(35.0, 40.0, 24.0, 25.0), "across the east edge")
        assertTrue(frame.overlaps(25.0, 31.0, 24.0, 25.0), "across the west edge")
        assertTrue(frame.overlaps(20.0, 40.0, 0.0, 60.0), "holding the whole frame")
        assertFalse(frame.overlaps(40.0, 41.0, 24.0, 25.0), "east of it")
        assertFalse(frame.overlaps(32.0, 33.0, 40.0, 41.0), "north of it")
    }

    @Test
    fun `a frame across the date line sees a box beyond it`() {
        val pacific = Frame(west = 120.0, east = -70.0, south = -60.0, north = 60.0)
        assertTrue(pacific.overlaps(-160.0, -150.0, 10.0, 25.0), "Hawaii")
        assertFalse(pacific.overlaps(0.0, 10.0, 40.0, 50.0), "Europe")
    }

    @Test
    fun `how many cities are named narrows with the frame`() {
        assertEquals(0, ranksNamedIn(Frame(-180.0, 180.0, -90.0, 90.0)))
        assertEquals(10, ranksNamedIn(Frame(3.5, 4.5, 51.3, 51.8)))
        assertTrue(ranksNamedIn(frame) in 2..9)
    }
}
