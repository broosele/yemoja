package yemoja.data.json

import yemoja.data.Stored
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The two settings files, read and written as they stand. See ../../../../../../json/doc.md.
 */
class SettingsFilesTest {

    @Test
    fun `a file that is there is read name by name`() {
        val store = MemoryFileStore(mapOf("settings.json" to """{"default_gradient_factor_low": 0.3, "theme": "dark"}"""))
        val read = SettingsFiles.read(store, SettingsFile.LOGBOOK)
        assertEquals(Stored.Leaf(0.3), read["default_gradient_factor_low"])
        assertEquals(Stored.Leaf("dark"), read["theme"], "a setting is handed back whatever it means")
    }

    @Test
    fun `a file that is not there holds nothing`() {
        assertEquals(emptyMap(), SettingsFiles.read(MemoryFileStore(emptyMap()), SettingsFile.LOCAL))
    }

    @Test
    fun `a file that will not read is ignored rather than stopping anything`() {
        val broken = MemoryFileStore(mapOf("settings.json" to "{ this is not json"))
        assertEquals(emptyMap(), SettingsFiles.read(broken, SettingsFile.LOGBOOK))
        val list = MemoryFileStore(mapOf("settings.json" to "[1, 2]"))
        assertEquals(emptyMap(), SettingsFiles.read(list, SettingsFile.LOGBOOK), "nor one that names nothing")
    }

    @Test
    fun `the two files are the two the layout names`() {
        assertEquals("settings.local.json", SettingsFile.LOCAL.file)
        assertEquals("settings.json", SettingsFile.LOGBOOK.file)
        assertEquals(listOf(SettingsFile.LOCAL, SettingsFile.LOGBOOK), SettingsFile.entries, "in the order they answer")
    }

    @Test
    fun `writing one setting keeps every other, including those nobody here knows`() {
        val store = MemoryFileStore(mapOf("settings.json" to """{"from_a_newer_version": [1, 2], "default_gradient_factor_low": 0.2}"""))
        SettingsFiles.write(store, SettingsFile.LOGBOOK, "default_gradient_factor_low", Stored.Leaf(0.3))
        val read = SettingsFiles.read(store, SettingsFile.LOGBOOK)
        assertEquals(Stored.Leaf(0.3), read["default_gradient_factor_low"])
        assertTrue("from_a_newer_version" in read, "a setting this version does not know is not lost")
    }

    @Test
    fun `writing nothing takes the setting out`() {
        val store = MemoryFileStore(mapOf("settings.local.json" to """{"default_gradient_factor_low": 0.2}"""))
        SettingsFiles.write(store, SettingsFile.LOCAL, "default_gradient_factor_low", null)
        assertEquals(emptyMap(), SettingsFiles.read(store, SettingsFile.LOCAL))
    }

    @Test
    fun `a file written where there was none is made`() {
        val store = MemoryFileStore(emptyMap())
        SettingsFiles.write(store, SettingsFile.LOGBOOK, "default_ascent_rate", Stored.Leaf(9.0))
        assertTrue(store.isFile("settings.json"))
        assertTrue(store.readText("settings.json").endsWith("\n"), "a file ends its last line")
    }
}
