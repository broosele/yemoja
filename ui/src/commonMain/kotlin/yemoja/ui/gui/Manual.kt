package yemoja.ui.gui

/*
 * The manual as the Manuals tab reads it: chapters, the sections in them, and the text of each
 * broken into what it is made of.
 *
 * The markdown understood here is the subset ../../../../../../../manual/doc.md commits the
 * chapters to, and no more: headings, paragraphs, the two kinds of list, fenced code, narrow
 * tables, a rule, and inside a line bold, italics, code and a link. Anything else is read as
 * prose, which is what a chapter that broke the rule would look like rather than a crash.
 *
 * See ../../../../../../gui/doc.md — `GUI-15`.
 */

/**
 * Every chapter, by file, in reading order.
 *
 * App info opens the manual, and after it the order is the manual's own, as its conventions list
 * them. A chapter not named here is not shown, which a test on the folder catches.
 */
internal val CHAPTERS: List<String> = listOf(
    "app-info.md",
    "data-format.md",
    "data-fields.md",
    "decompression.md",
    "settings.md",
    "uddf.md",
)

/** Chapter is one file of the manual, read into its blocks. */
internal class Chapter(
    /** The file it came from, which is what a link between chapters names. */
    val file: String,
    val blocks: List<Block>,
) {
    /** The first heading, which every chapter opens with; the file's name failing that. */
    val title: String = (blocks.firstOrNull() as? Block.Heading)?.text?.let(::plainOf) ?: file

    /** The second-level headings, which are what the tree offers under a chapter. */
    val sections: List<Section> = blocks.mapIndexedNotNull { at, block ->
        if (block is Block.Heading && block.level == 2) {
            Section(plainOf(block.text), slugOf(plainOf(block.text)), at)
        } else {
            null
        }
    }
}

/** Section is one heading a chapter can be opened at. */
internal class Section(val title: String, val anchor: String, val block: Int)

/** Block is one piece of a chapter, read from top to bottom. */
internal sealed class Block {
    class Heading(val level: Int, val text: List<Span>) : Block()
    class Paragraph(val text: List<Span>) : Block()
    class Bullets(val items: List<List<Span>>) : Block()
    class Numbered(val items: List<List<Span>>) : Block()
    class Code(val text: String) : Block()
    class Table(val header: List<List<Span>>, val rows: List<List<List<Span>>>) : Block()
    object Rule : Block()
}

/** Span is one run of a line, styled one way. */
internal sealed class Span {
    abstract val text: String

    class Plain(override val text: String) : Span()
    class Strong(override val text: String) : Span()
    class Emphasis(override val text: String) : Span()
    class Code(override val text: String) : Span()
    class Link(override val text: String, val target: String) : Span()
}

/** The text of a line with its styling dropped. */
internal fun plainOf(spans: List<Span>): String = spans.joinToString("") { it.text }

/**
 * A heading as a link names it: lower case, punctuation dropped, spaces as hyphens.
 *
 * The same rule the hosting sites use, so a link written for one of them lands here too.
 */
internal fun slugOf(heading: String): String =
    heading.lowercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.replace(' ', '-')

/** [text] read as the chapter in [file]. */
internal fun chapterOf(file: String, text: String): Chapter {
    val blocks = ArrayList<Block>()
    val lines = text.lines()
    var at = 0
    while (at < lines.size) {
        val line = lines[at]
        when {
            line.isBlank() -> at += 1
            line.startsWith("```") -> {
                val code = ArrayList<String>()
                at += 1
                while (at < lines.size && !lines[at].startsWith("```")) code += lines[at++]
                at += 1
                blocks += Block.Code(code.joinToString("\n"))
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                blocks += Block.Heading(level, spansOf(line.drop(level).trim()))
                at += 1
            }
            line.trim() == "---" -> {
                blocks += Block.Rule
                at += 1
            }
            line.startsWith("|") -> {
                val rows = ArrayList<List<List<Span>>>()
                while (at < lines.size && lines[at].startsWith("|")) {
                    val cells = lines[at].trim().removePrefix("|").removeSuffix("|").split("|")
                    // The row of dashes under the header says which row is the header and
                    // holds nothing itself.
                    if (cells.none { it.trim().any { c -> c != '-' && c != ':' } }) {
                        at += 1
                        continue
                    }
                    rows += cells.map { spansOf(it.trim()) }
                    at += 1
                }
                blocks += Block.Table(rows.first(), rows.drop(1))
            }
            isBullet(line) -> {
                val items = ArrayList<List<Span>>()
                while (at < lines.size && isBullet(lines[at])) {
                    val item = StringBuilder(lines[at].trim().drop(2))
                    at += 1
                    // A line indented under an item continues it.
                    while (continues(lines, at)) item.append(' ').append(lines[at++].trim())
                    items += spansOf(item.toString())
                }
                blocks += Block.Bullets(items)
            }
            isNumbered(line) -> {
                val items = ArrayList<List<Span>>()
                while (at < lines.size && isNumbered(lines[at])) {
                    val item = StringBuilder(lines[at].substringAfter(". ").trim())
                    at += 1
                    while (continues(lines, at)) item.append(' ').append(lines[at++].trim())
                    items += spansOf(item.toString())
                }
                blocks += Block.Numbered(items)
            }
            else -> {
                val paragraph = ArrayList<String>()
                while (at < lines.size && lines[at].isNotBlank() && !opensBlock(lines[at])) {
                    paragraph += lines[at++].trim()
                }
                blocks += Block.Paragraph(spansOf(paragraph.joinToString(" ")))
            }
        }
    }
    return Chapter(file, blocks)
}

/** Whether the line at [at] is indented under a list item, and so continues it. */
private fun continues(lines: List<String>, at: Int): Boolean =
    at < lines.size && lines[at].startsWith("  ") && lines[at].isNotBlank()

private fun isBullet(line: String): Boolean = line.startsWith("- ") || line.startsWith("* ")

private fun isNumbered(line: String): Boolean =
    line.isNotEmpty() && line[0].isDigit() && line.substringBefore(". ", "").all { it.isDigit() } &&
        line.contains(". ")

private fun opensBlock(line: String): Boolean =
    line.startsWith("#") || line.startsWith("```") || line.startsWith("|") || isBullet(line) ||
        isNumbered(line) || line.trim() == "---"

/**
 * A line as its runs.
 *
 * Four marks are known, `**strong**`, `*emphasis*`, `` `code` `` and `[text](target)`, and a
 * mark that never closes is read as the character it is.
 */
internal fun spansOf(line: String): List<Span> {
    val spans = ArrayList<Span>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) spans += Span.Plain(plain.toString())
        plain.clear()
    }
    var at = 0
    while (at < line.length) {
        val closed = when {
            line.startsWith("**", at) -> spanned(line, at, "**", "**") { Span.Strong(it) }
            line.startsWith("*", at) -> spanned(line, at, "*", "*") { Span.Emphasis(it) }
            line.startsWith("`", at) -> spanned(line, at, "`", "`") { Span.Code(it) }
            line.startsWith("[", at) -> linked(line, at)
            else -> null
        }
        if (closed == null) {
            plain.append(line[at])
            at += 1
        } else {
            flush()
            spans += closed.first
            at = closed.second
        }
    }
    flush()
    return spans
}

/** The span a mark opens at [at], and where the text resumes; absent where it never closes. */
private fun spanned(
    line: String,
    at: Int,
    opens: String,
    closes: String,
    make: (String) -> Span,
): Pair<Span, Int>? {
    val from = at + opens.length
    val to = line.indexOf(closes, from)
    if (to <= from) return null
    return make(line.substring(from, to)) to to + closes.length
}

private fun linked(line: String, at: Int): Pair<Span, Int>? {
    val close = line.indexOf("](", at)
    if (close < 0) return null
    val end = line.indexOf(')', close)
    if (end < 0) return null
    return Span.Link(line.substring(at + 1, close), line.substring(close + 2, end)) to end + 1
}
