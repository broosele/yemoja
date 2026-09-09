package yemoja.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Whether two spellings of a serial are one serial. `LOGIC-23`.
 *
 * See doc.md.
 */
class SerialTest {

    @Test
    fun `case, spaces and punctuation do not count`() {
        assertTrue(sameSerial("FL-2200-4471", "fl 2200 4471"))
        assertTrue(sameSerial("6A9191A5", "6a91.91a5"))
    }

    @Test
    fun `what the device said in decimal matches what the maker prints in hexadecimal`() {
        assertTrue(sameSerial("1787924901", "6A9191A5"))
        assertTrue(sameSerial("6a9191a5", "1787924901"), "either way round")
    }

    @Test
    fun `leading zeros do not count`() {
        assertTrue(sameSerial("001124", "1124"))
    }

    @Test
    fun `two decimal spellings are not read as each other's hexadecimal`() {
        // 10 in hexadecimal is 16, and two serials that differ are two serials.
        assertFalse(sameSerial("10", "16"))
    }

    @Test
    fun `different is different, and nothing matches nothing`() {
        assertFalse(sameSerial("FL-2200-4471", "FL-2200-4472"))
        assertFalse(sameSerial("", "FL-2200-4471"))
        assertFalse(sameSerial("--", ""))
    }
}
