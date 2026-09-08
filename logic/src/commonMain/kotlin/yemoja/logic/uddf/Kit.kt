package yemoja.logic.uddf

/*
 * The twenty-one elements UDDF has for equipment, against the two axes this model has.
 *
 * See ../../../../../../doc.md — the table is in logic/uddf.md, under *Equipment*.
 */

/**
 * What each of UDDF's equipment elements is, as a category and a kind.
 *
 * **Theirs is one closed axis and ours is two open ones.** A thing *is* a `mask` there, with no
 * way to say what sort of suit a `suit` is; here `category` and `kind` both accept anything. So
 * each element sets both from this table, and where nothing sensible fits a category it is left
 * out — absent means unknown, which is exactly what it is.
 *
 * `rebreather` is not here, being dropped by `FEAT-21`, and neither is `equipmentconfiguration`,
 * which describes how a set is put together rather than being a piece of it.
 */
internal val KIT: Map<String, Pair<String?, String?>> = mapOf(
    "mask" to ("ABC" to "mask"),
    "fins" to ("ABC" to "fins"),
    "boots" to ("suit" to "boots"),
    "gloves" to ("suit" to "gloves"),
    "suit" to ("suit" to null),
    "buoyancycontroldevice" to ("BCD" to null),
    "regulator" to ("regulator" to null),
    "tank" to ("cylinder" to null),
    "lead" to ("weights" to "lead weight"),
    "divecomputer" to ("instruments" to "dive computer"),
    "light" to ("lighting" to "light"),
    "camera" to ("photography" to "camera"),
    "videocamera" to ("photography" to "video camera"),
    "knife" to ("accessory" to "knife"),
    "compass" to ("instruments" to "compass"),
    "watch" to ("instruments" to "watch"),
    "compressor" to (null to "compressor"),
    "scooter" to (null to "scooter"),
    "variouspieces" to (null to null),
)

/** The names of those elements, which is what a document is searched for. */
internal val GEAR: Set<String> = KIT.keys
