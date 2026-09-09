package yemoja.logic

/*
 * Whether two spellings of a serial are one serial.
 *
 * See doc.md — `LOGIC-23`.
 */

/**
 * Whether [a] and [b] name one serial.
 *
 * Case, spaces and punctuation do not count: `FL-2200-4471` and `fl 2200 4471` are one. A device
 * reports its serial as a number and its maker prints it as they please, so a spelling that is
 * all digits is also read as a decimal number and one holding a hexadecimal letter as a
 * hexadecimal one, and the two are compared as numbers: `001124` and `1124` are one, and so are
 * `1787924901` and `6A9191A5`. A hexadecimal spelling that happens to hold only digits is the
 * one case this misses.
 */
internal fun sameSerial(a: String, b: String): Boolean {
    val left = bare(a)
    val right = bare(b)
    if (left.isEmpty() || right.isEmpty()) return false
    if (left == right) return true
    val leftDecimal = decimalOf(left)
    val rightDecimal = decimalOf(right)
    if (leftDecimal != null && leftDecimal == rightDecimal) return true
    return (leftDecimal != null && leftDecimal == hexadecimalOf(right)) ||
        (rightDecimal != null && rightDecimal == hexadecimalOf(left))
}

/** Letters and digits only, in one case. */
private fun bare(spelt: String): String = spelt.lowercase().filter { it.isLetterOrDigit() }

private fun decimalOf(bare: String): Long? =
    if (bare.all { it.isDigit() }) bare.toLongOrNull() else null

private fun hexadecimalOf(bare: String): Long? =
    if (bare.any { it in 'a'..'f' } && bare.all { it.isDigit() || it in 'a'..'f' }) {
        bare.toLongOrNull(16)
    } else {
        null
    }
