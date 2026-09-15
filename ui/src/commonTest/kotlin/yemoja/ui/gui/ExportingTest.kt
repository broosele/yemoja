package yemoja.ui.gui

import yemoja.logic.uddf.Exported
import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Writing a logbook out. See ../../../../../../gui/doc.md — `GUI-37`.
 */
class ExportSaidTest {

    @Test
    fun `an export says how many dives went and where`() {
        assertEquals("Wrote 20 dives to log.uddf.", exportSaid(Exported("", 20, 0), "log.uddf"))
        assertEquals("Wrote 1 dive to log.uddf.", exportSaid(Exported("", 1, 0), "log.uddf"))
    }

    @Test
    fun `a dive on two computers is said aloud, since the file does not show it`() {
        assertEquals(
            "Wrote 20 dives to log.uddf. 1 dive recorded on more than one computer was written " +
                "with its primary recording only, UDDF holding one to a dive.",
            exportSaid(Exported("", 20, 1), "log.uddf"),
        )
        assertEquals(
            "Wrote 20 dives to log.uddf. 3 dives recorded on more than one computer were " +
                "written with their primary recording only, UDDF holding one to a dive.",
            exportSaid(Exported("", 20, 3), "log.uddf"),
        )
    }
}

class UddfNamedTest {

    @Test
    fun `a name typed bare is given the extension`() {
        assertEquals("D:/out/log.uddf", uddfNamed("D:/out/log"))
        assertEquals("C:\\out\\log.uddf", uddfNamed("C:\\out\\log"))
    }

    @Test
    fun `a name that says what it is is left as typed`() {
        assertEquals("D:/out/log.xml", uddfNamed("D:/out/log.xml"))
        assertEquals("D:/my.logs/log.uddf", uddfNamed("D:/my.logs/log"), "a point in the folder")
    }
}
