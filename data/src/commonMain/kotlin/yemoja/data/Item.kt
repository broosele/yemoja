package yemoja.data

/**
 * One item of one type: a dive, a person, a region.
 *
 * **A placeholder.** It holds nothing yet, and exists so the layer compiles. Three declarations
 * name it: [Role.Derived] and [Role.Overrideable], which carry a computation over one, and
 * [Referent.Resolved].
 *
 * What it will hold is settled and the shape is not: the description of its type, a map of field
 * name to value, and the items it belongs to. It holds no id, which lives in the file name or the
 * key it sits under. See *How a type is described* and *The set of items* in `doc.md`.
 *
 * Not immutable, unlike the values in this layer. An item is what a user edits.
 */
class Item
