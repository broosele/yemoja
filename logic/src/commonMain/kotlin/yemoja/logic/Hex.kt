package yemoja.logic

/*
 * Bytes as hexadecimal text and back, which is how a fingerprint and an access code are written.
 *
 * See doc.md — `DATA-90`, `LOGIC-24`.
 */

/** [bytes] as hexadecimal, two lower-case digits apiece. */
internal fun hexOf(bytes: ByteArray): String = bytes.joinToString("") {
    val digits = (it.toInt() and 0xff).toString(16)
    if (digits.length == 1) "0$digits" else digits
}

/** The bytes [hex] spells, or absent where it is not hexadecimal or holds no whole byte. */
internal fun bytesOf(hex: String?): ByteArray? {
    if (hex == null || hex.length < 2 || hex.length % 2 != 0) return null
    val out = ByteArray(hex.length / 2)
    for (at in out.indices) {
        val byte = hex.substring(at * 2, at * 2 + 2).toIntOrNull(16) ?: return null
        out[at] = byte.toByte()
    }
    return out
}
