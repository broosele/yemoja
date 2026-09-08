package yemoja.logic.uddf

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

/*
 * A document read into a tree, which is all the XML this layer wants to know about.
 *
 * See ../../../../../../doc.md — the mapping this feeds is logic/uddf.md.
 */

/**
 * Tag is one element of a document: what it is called, what it carries and what it says.
 *
 * Not called an element, which is [yemoja.data.Element]'s word here for one value of several.
 *
 * **Names are local ones.** A prefix and the namespace behind it are dropped, because the only
 * documents read here are UDDF ones and a UDDF file puts everything in one namespace. A document
 * that mixed two would have its names run together, which is a reason to look at this again
 * rather than something the mapping can meet today.
 *
 * Immutable.
 */
internal class Tag(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<Tag>,
    /** The text directly inside it, with what its children say left out. */
    val said: String,
) {

    /** Every child called [name], in the order the document holds them. */
    fun all(name: String): List<Tag> = children.filter { it.name == name }

    /** The first child called [name], or absent where it has none. */
    fun one(name: String): Tag? = children.firstOrNull { it.name == name }

    /** What the first child called [name] says, or absent where it says nothing. */
    fun text(name: String): String? = one(name)?.said?.trim()?.ifEmpty { null }

    /**
     * The first tag called [name] anywhere inside this one, or absent where there is none.
     *
     * **[apart] is not gone into.** A dive's fields are spread over `informationbeforedive` and
     * `informationafterdive`, and which of the two holds a given one is a detail of the format
     * this mapping would rather not repeat. Searching for the name finds it either way; the
     * samples are excluded because a waypoint carries names a dive also uses.
     */
    fun find(name: String, apart: Set<String> = emptySet()): Tag? {
        for (child in children) {
            if (child.name == name) return child
            if (child.name in apart) continue
            child.find(name, apart)?.let { return it }
        }
        return null
    }

    /** Everything said inside this tag and everything inside that, run together. */
    fun everything(): String =
        (said + children.joinToString("") { it.everything() }).trim()
}

/** [text] as a tree. Throws [nl.adaptivity.xmlutil.XmlException] where it is not a document. */
internal fun tagsIn(text: String): Tag {
    val reader = xmlStreaming.newGenericReader(text)
    while (reader.hasNext()) {
        if (reader.next() == EventType.START_ELEMENT) return tagAt(reader)
    }
    throw IllegalArgumentException("a document should hold at least one tag, and held none")
}

/** The tag the reader has just started, and everything inside it. */
private fun tagAt(reader: XmlReader): Tag {
    val name = reader.localName
    val attributes = LinkedHashMap<String, String>()
    for (at in 0..<reader.attributeCount) {
        attributes[reader.getAttributeLocalName(at)] = reader.getAttributeValue(at)
    }
    val children = ArrayList<Tag>()
    val said = StringBuilder()
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> children += tagAt(reader)
            EventType.END_ELEMENT -> return Tag(name, attributes, children, said.toString())
            // An entity reference is an event of its own and carries what it stands for, so a
            // `&amp;` in a diver's notes is a character rather than a gap.
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> said.append(reader.text)
            else -> Unit
        }
    }
    return Tag(name, attributes, children, said.toString())
}
