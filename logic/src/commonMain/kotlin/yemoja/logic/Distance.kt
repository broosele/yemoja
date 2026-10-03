package yemoja.logic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/*
 * How far one position is from another, which more than one thing here asks.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * How far apart two positions are, in metres.
 *
 * Flat rather than spherical: what this decides is which of a few places is nearest, and over the
 * few kilometres that can mean the earth's curve is worth centimetres.
 */
fun metresApart(
    latitude: Double,
    longitude: Double,
    otherLatitude: Double,
    otherLongitude: Double,
): Double {
    val north = (otherLatitude - latitude) * METRES_PER_DEGREE
    val east = (otherLongitude - longitude) * METRES_PER_DEGREE * cos(latitude * PI / 180.0)
    return sqrt(north * north + east * east)
}

/** A degree of latitude, in metres, which is near enough the same everywhere. */
private const val METRES_PER_DEGREE = 111_320.0
