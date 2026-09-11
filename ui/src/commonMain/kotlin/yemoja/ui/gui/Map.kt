package yemoja.ui.gui

/*
 * The map's geometry: coastlines, lakes, borders, rivers and cities at three scales, read from
 * the map library and chosen by how wide a frame is.
 *
 * The files are ../../../../../../../libraries/map, one shape per line as pairs of longitude
 * and latitude, and cities one per line with a name, a position, a rank and a population; the
 * layout is given in ../../../../../../../libraries/doc.md.
 *
 * See ../../../../../../gui/doc.md — `GUI-25`.
 */

/**
 * Outline is one coastline, lake, border or river: its rings, and the box round them.
 *
 * A coastline or a lake is a ring, and any ring after the first is a hole in it: the Caspian
 * in Eurasia, an island in a lake. A border or a river is one open ring. Each ring is longitude
 * and latitude interleaved, which keeps four hundred thousand points in a few arrays rather
 * than as many objects. No outline crosses the date line: the source splits them there, so a
 * box is always west to east without wrapping.
 */
internal class Outline(val rings: List<DoubleArray>) {
    val west: Double
    val east: Double
    val south: Double
    val north: Double

    init {
        var w = Double.MAX_VALUE
        var e = -Double.MAX_VALUE
        var s = Double.MAX_VALUE
        var n = -Double.MAX_VALUE
        for (ring in rings) {
            for (at in ring.indices step 2) {
                w = minOf(w, ring[at])
                e = maxOf(e, ring[at])
                s = minOf(s, ring[at + 1])
                n = maxOf(n, ring[at + 1])
            }
        }
        west = w
        east = e
        south = s
        north = n
    }

    /** How many points, over every ring. */
    val size: Int get() = rings.sumOf { it.size / 2 }
}

/** City is a populated place: where it is, and how important the source ranks it, 0 first. */
internal class City(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val rank: Int,
    val population: Int,
)

/** Layer is the map at one scale. */
internal class Layer(
    val land: List<Outline>,
    val lakes: List<Outline>,
    val borders: List<Outline>,
    val rivers: List<Outline>,
    val cities: List<City>,
)

/**
 * Atlas is the map at its three scales, and which of them a frame is drawn from.
 *
 * The coarse scale is the source's 1:110 million, the medium its 1:50 million and the fine its
 * 1:10 million. A frame is drawn from the coarsest scale that still looks like a map at that
 * width, which keeps the world to a few thousand points and a bay to its real coastline.
 */
internal class Atlas(val coarse: Layer, val medium: Layer, val fine: Layer) {

    fun layerFor(frame: Frame): Layer = when {
        frame.width > COARSE_ABOVE -> coarse
        frame.width > MEDIUM_ABOVE -> medium
        else -> fine
    }

    companion object {
        /** The scales, as the library names its folders, coarsest first. */
        val SCALES: List<String> = listOf("110m", "50m", "10m")

        /** The layers, as the library names its files. */
        val LAYERS: List<String> = listOf("land", "lakes", "borders", "rivers", "cities")

        /** Read from [text], which gives the file for a scale and a layer. */
        fun read(text: (scale: String, layer: String) -> String): Atlas {
            val layers = SCALES.map { scale ->
                Layer(
                    land = outlinesOf(text(scale, "land")),
                    lakes = outlinesOf(text(scale, "lakes")),
                    borders = outlinesOf(text(scale, "borders")),
                    rivers = outlinesOf(text(scale, "rivers")),
                    cities = citiesOf(text(scale, "cities")),
                )
            }
            return Atlas(layers[0], layers[1], layers[2])
        }
    }
}

/** Frames wider than this, in degrees, are drawn from the coarse scale. */
internal const val COARSE_ABOVE = 100.0

/** Frames wider than this and not coarse are drawn from the medium scale. */
internal const val MEDIUM_ABOVE = 20.0

/**
 * Outlines from a file: one per line, its rings separated by a slash, each ring longitude and
 * latitude alternating, separated by spaces.
 */
internal fun outlinesOf(text: String): List<Outline> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        Outline(
            line.split(RING).map { ring ->
                val numbers = ring.split(' ')
                DoubleArray(numbers.size) { numbers[it].toDouble() }
            },
        )
    }
    .toList()

/** What separates one ring of an outline from the next in the file. */
private const val RING = " / "

/** Cities from a file: one per line, tab-separated name, latitude, longitude, rank, population. */
internal fun citiesOf(text: String): List<City> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val cells = line.split('\t')
        City(cells[0], cells[1].toDouble(), cells[2].toDouble(), cells[3].toInt(), cells[4].toInt())
    }
    .toList()

/**
 * Whether any of a box from [west] to [east] and [south] to [north] lies in the frame.
 *
 * A box whose west edge lies further east of the frame than its east edge does straddles the
 * frame's west meridian, and so is in it.
 */
internal fun Frame.overlaps(west: Double, east: Double, south: Double, north: Double): Boolean {
    if (south > this.north || north < this.south) return false
    val from = eastOf(west)
    val to = eastOf(east)
    return from > to || from < width
}

/**
 * The rank a city must have to be named in a frame: the few dozen greatest on the world, and
 * every place the source knows in a bay.
 */
internal fun ranksNamedIn(frame: Frame): Int = when {
    frame.width > COARSE_ABOVE -> 0
    frame.width > MEDIUM_ABOVE -> 2
    frame.width > 4.0 -> 4
    frame.width > 1.0 -> 7
    else -> 10
}
