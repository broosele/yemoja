package yemoja.logic

import yemoja.data.MultilineTextDescription

/*
 * What more than one type is described with.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * Every item type has one, for whatever has no field of its own.
 *
 * A new one each time it is read, because a description is immutable and shared but the field
 * list of each type is its own.
 */
internal val REMARKS: MultilineTextDescription get() = MultilineTextDescription("remarks")

/** Closed: what a site is in, and what a computer was set to. */
internal val WATER_TYPES = setOf("salt", "fresh", "en13319")

internal val LONGITUDE = -180.0..180.0

internal val LATITUDE = -90.0..90.0
