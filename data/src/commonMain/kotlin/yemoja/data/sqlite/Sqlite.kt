package yemoja.data.sqlite

/*
 * A SQLite database read table by table, from SQLite's own published file format.
 *
 * See ../../../../../../doc.md — `DATA-130`. The format is https://www.sqlite.org/fileformat.html,
 * which is in the public domain.
 */

/**
 * SqliteFormatException is thrown when a file is not a SQLite database this reader can read.
 *
 * A database whose pages contradict each other is one, and so is a table kept in a form this
 * reader does not follow: a table `WITHOUT ROWID`, or text in UTF-16.
 */
class SqliteFormatException(message: String) : RuntimeException(message)

/**
 * SqliteFile is a SQLite database held whole, read one table at a time.
 *
 * **Read as it lies on disk.** A journal or a write-ahead log beside the file is not looked at,
 * so a database a program left mid-transaction is read as it stood before the transaction.
 * Nothing is written, no index is used, and no query is understood: a table is its rows, in the
 * order of their ids.
 *
 * Immutable.
 */
class SqliteFile(private val bytes: ByteArray) {

    private val pageSize: Int
    private val usable: Int

    /** Each table by name, with its columns in order and the page its tree starts at. */
    private val schema: Map<String, Table>

    private class Table(val columns: List<String>, val root: Int, val alias: Int?)

    init {
        if (!isSqlite(bytes)) throw SqliteFormatException("this is not a SQLite database")
        if (bytes.size < HEADER_SIZE) throw SqliteFormatException("the file ends inside its header")
        val written = unsigned16(16)
        // A page of 65536 bytes does not fit two bytes, and is written as 1.
        pageSize = if (written == 1) MAX_PAGE else written
        if (pageSize < MIN_PAGE || pageSize and (pageSize - 1) != 0) {
            throw SqliteFormatException("the page size should be a power of two from $MIN_PAGE, but was $pageSize")
        }
        usable = pageSize - (bytes[20].toInt() and 0xFF)
        val encoding = unsigned32(56)
        if (encoding != 0L && encoding != UTF8) {
            throw SqliteFormatException("text should be UTF-8, but the file says encoding $encoding")
        }
        val tables = LinkedHashMap<String, Table>()
        for (row in rowsOf(1)) {
            val values = row.second
            if (values.getOrNull(0) != "table") continue
            val name = values.getOrNull(1) as? String ?: continue
            val sql = values.getOrNull(4) as? String ?: continue
            val root = (values.getOrNull(3) as? Long)?.toInt() ?: continue
            // An internal table, the sequence counters, is read like any other and wanted by nobody.
            if (name.startsWith("sqlite_")) continue
            if (WITHOUT_ROWID.containsMatchIn(sql.substringAfterLast(')'))) {
                tables[name] = Table(emptyList(), 0, null)
                continue
            }
            val (columns, alias) = columnsOf(sql)
            tables[name] = Table(columns, root, alias)
        }
        schema = tables
    }

    /** Every table the database holds, in the order its schema lists them. */
    val tables: List<String> get() = schema.keys.toList()

    /** The columns of [table] in the order they were declared, or null where there is no such table. */
    fun columns(table: String): List<String>? = schema[table]?.columns

    /**
     * Every row of [table], each a map from its column to its value, or null where there is no
     * such table.
     *
     * A value is a `Long`, a `Double`, a `String`, a `ByteArray` or null, as SQLite stored it,
     * whatever the column was declared to hold: SQLite lets a column declared `REAL` keep text.
     * A column added after a row was written has no value in that row, which is read as null.
     */
    fun rows(table: String): List<Map<String, Any?>>? {
        val held = schema[table] ?: return null
        if (held.root == 0) throw SqliteFormatException("$table is a table WITHOUT ROWID, which this reader does not read")
        return rowsOf(held.root).map { (id, values) ->
            held.columns.withIndex().associate { (at, column) ->
                // The column that is the row's id is stored as nothing, the id standing in for it.
                column to if (at == held.alias) id else values.getOrNull(at)
            }
        }
    }

    // --- The tree a table is kept in.

    /** Every row of the table whose tree starts at [root], as its id and its values, in id order. */
    private fun rowsOf(root: Int): List<Pair<Long, List<Any?>>> {
        val found = ArrayList<Pair<Long, List<Any?>>>()
        val visited = HashSet<Int>()
        fun walk(page: Int) {
            if (page < 1 || page.toLong() * pageSize > bytes.size) {
                throw SqliteFormatException("page $page should lie in the file, which holds ${bytes.size / pageSize}")
            }
            if (!visited.add(page)) throw SqliteFormatException("page $page is reached twice, so the tree loops")
            val start = (page - 1) * pageSize
            // Page 1 begins with the file's header, and its own header follows that.
            val header = if (page == 1) start + HEADER_SIZE else start
            val count = unsigned16(header + 3)
            when (val kind = bytes[header].toInt() and 0xFF) {
                LEAF_TABLE -> for (cell in 0..<count) {
                    found += leafCell(start + unsigned16(header + LEAF_HEADER + cell * 2))
                }

                INTERIOR_TABLE -> {
                    for (cell in 0..<count) {
                        walk(unsigned32(start + unsigned16(header + INTERIOR_HEADER + cell * 2)).toInt())
                    }
                    walk(unsigned32(header + 8).toInt())
                }

                else -> throw SqliteFormatException("page $page should hold a table, but is of kind $kind")
            }
        }
        walk(root)
        return found
    }

    /** The row in the leaf cell at [at]: its id, and its values, gathered from any overflow pages. */
    private fun leafCell(at: Int): Pair<Long, List<Any?>> {
        val (size, afterSize) = varint(at)
        val (id, afterId) = varint(afterSize)
        val payload = size.toInt()
        // How much of the payload sits in the cell, the rest running on through overflow pages.
        val most = usable - 35
        val local = if (payload <= most) {
            payload
        } else {
            val least = (usable - 12) * 32 / 255 - 23
            val fits = least + (payload - least) % (usable - 4)
            if (fits <= most) fits else least
        }
        val record = ByteArray(payload)
        bytes.copyInto(record, 0, afterId, afterId + local)
        var filled = local
        var next = if (local < payload) unsigned32(afterId + local).toInt() else 0
        while (filled < payload) {
            if (next < 1) throw SqliteFormatException("a row runs out of overflow pages before its end")
            if (next.toLong() * pageSize > bytes.size) {
                throw SqliteFormatException("overflow page $next should lie in the file, which holds ${bytes.size / pageSize}")
            }
            val page = (next - 1) * pageSize
            val take = minOf(usable - 4, payload - filled)
            bytes.copyInto(record, filled, page + 4, page + 4 + take)
            filled += take
            next = unsigned32(page).toInt()
        }
        return id to valuesOf(record)
    }

    // --- A row's values.

    /** The values a record holds, each as its serial type says. */
    private fun valuesOf(record: ByteArray): List<Any?> {
        val (headerSize, afterHeaderSize) = varint(record, 0)
        val types = ArrayList<Long>()
        var at = afterHeaderSize
        while (at < headerSize) {
            val (type, after) = varint(record, at)
            types += type
            at = after
        }
        var body = headerSize.toInt()
        return types.map { type ->
            val length = lengthOf(type)
            val value: Any? = when {
                type == 0L -> null
                type in 1L..6L -> signed(record, body, length)
                type == 7L -> Double.fromBits(signed(record, body, 8))
                type == 8L -> 0L
                type == 9L -> 1L
                type >= 12 && type % 2 == 0L -> record.copyOfRange(body, body + length)
                type >= 13 -> record.decodeToString(body, body + length)
                else -> throw SqliteFormatException("a value has the reserved serial type $type")
            }
            body += length
            value
        }
    }

    /** How many bytes a value of serial [type] takes. */
    private fun lengthOf(type: Long): Int = when (type) {
        in 0L..4L -> type.toInt()
        5L -> 6
        6L, 7L -> 8
        8L, 9L, 10L, 11L -> 0
        else -> ((type - if (type % 2 == 0L) 12 else 13) / 2).toInt()
    }

    // --- Numbers as SQLite writes them: big-endian, and a variable length for most.

    private fun unsigned16(at: Int): Int = (bytes[at].toInt() and 0xFF shl 8) or (bytes[at + 1].toInt() and 0xFF)

    private fun unsigned32(at: Int): Long =
        (0..3).fold(0L) { held, step -> held shl 8 or (bytes[at + step].toLong() and 0xFF) }

    private fun varint(at: Int): Pair<Long, Int> = varint(bytes, at)

    private companion object {
        const val HEADER_SIZE = 100
        const val MIN_PAGE = 512
        const val MAX_PAGE = 65536
        const val UTF8 = 1L
        const val LEAF_TABLE = 0x0D
        const val INTERIOR_TABLE = 0x05
        const val LEAF_HEADER = 8
        const val INTERIOR_HEADER = 12

        val WITHOUT_ROWID = Regex("""\bWITHOUT\s+ROWID\b""", RegexOption.IGNORE_CASE)
    }
}

/** Whether [bytes] begin as every SQLite database does. */
fun isSqlite(bytes: ByteArray): Boolean =
    bytes.size >= SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

/** `SQLite format 3` and a nought, which is how every database begins. */
private val SIGNATURE: ByteArray = "SQLite format 3\u0000".encodeToByteArray()

/**
 * A variable-length integer at [at] in [bytes], and where the next thing begins.
 *
 * One to nine bytes, seven bits each but the ninth, which gives all eight.
 */
private fun varint(bytes: ByteArray, at: Int): Pair<Long, Int> {
    var value = 0L
    for (step in 0..<8) {
        val byte = bytes[at + step].toInt() and 0xFF
        value = value shl 7 or (byte and 0x7F).toLong()
        if (byte and 0x80 == 0) return value to at + step + 1
    }
    return (value shl 8 or (bytes[at + 8].toLong() and 0xFF)) to at + 9
}

/** A signed big-endian integer of [length] bytes at [at] in [bytes]. */
private fun signed(bytes: ByteArray, at: Int, length: Int): Long {
    var value = bytes[at].toLong() // The first byte carries the sign.
    for (step in 1..<length) value = value shl 8 or (bytes[at + step].toLong() and 0xFF)
    return value
}

/**
 * The columns a `CREATE TABLE` statement declares, and which of them, by place, is the row's id.
 *
 * Only the names are taken. A constraint written as an entry of its own, `PRIMARY KEY (a, b)`, is
 * not a column. A column declared `INTEGER PRIMARY KEY` is the row's id under another name, and
 * SQLite stores nothing for it in the row.
 */
private fun columnsOf(sql: String): Pair<List<String>, Int?> {
    val open = sql.indexOf('(')
    val close = sql.lastIndexOf(')')
    if (open < 0 || close < open) throw SqliteFormatException("a table should list its columns: $sql")
    val columns = ArrayList<String>()
    var alias: Int? = null
    for (entry in splitTopLevel(sql.substring(open + 1, close))) {
        val trimmed = entry.trim()
        if (trimmed.isEmpty() || CONSTRAINT.containsMatchIn(trimmed)) continue
        val (name, rest) = nameOf(trimmed)
        if (INTEGER_KEY.containsMatchIn(rest)) alias = columns.size
        columns += name
    }
    return columns to alias
}

/** [text] split on its commas, leaving those inside brackets or quotes alone. */
private fun splitTopLevel(text: String): List<String> {
    val parts = ArrayList<String>()
    var depth = 0
    var quote: Char? = null
    var start = 0
    for ((at, character) in text.withIndex()) {
        when {
            quote != null -> if (character == quote) quote = null
            character in "\"'`[" -> quote = if (character == '[') ']' else character
            character == '(' -> depth++
            character == ')' -> depth--
            character == ',' && depth == 0 -> {
                parts += text.substring(start, at)
                start = at + 1
            }
        }
    }
    parts += text.substring(start)
    return parts
}

/** The column name an entry begins with, unquoted, and what follows it. */
private fun nameOf(entry: String): Pair<String, String> {
    val close = when (entry.first()) {
        '"' -> '"'
        '`' -> '`'
        '[' -> ']'
        '\'' -> '\''
        else -> null
    }
    if (close == null) {
        val end = entry.indexOfFirst { it.isWhitespace() }.let { if (it < 0) entry.length else it }
        return entry.substring(0, end) to entry.substring(end)
    }
    val end = entry.indexOf(close, 1)
    if (end < 0) throw SqliteFormatException("a column's name is not closed: $entry")
    return entry.substring(1, end) to entry.substring(end + 1)
}

/** An entry of a table's list that is a constraint on the table rather than a column. */
private val CONSTRAINT = Regex("""^(CONSTRAINT|PRIMARY\s+KEY|UNIQUE|CHECK|FOREIGN\s+KEY)\b""", RegexOption.IGNORE_CASE)

/** A column declared as the row's id: `INTEGER PRIMARY KEY`, its type spelt exactly so. */
private val INTEGER_KEY = Regex("""^\s*INTEGER\s+PRIMARY\s+KEY\b""", RegexOption.IGNORE_CASE)
