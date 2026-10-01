package yemoja.logic

import yemoja.data.json.Lock
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * One window at a time on a logbook. See ../../../../../../data/json/doc.md — `JSON-27`.
 */
class LockTest {

    private fun logbook(): Path = Files.createTempDirectory("yemoja-").resolve("mine").also {
        Files.createDirectory(it)
        it.resolve("yemoja.json").writeText("""{"libraries": {}}""")
    }

    private fun lockOf(folder: Path): Path = folder.resolve(Lock.FILE)

    @Test
    fun `opening takes the lock, and a second opening is refused and told where it is`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        assertTrue(lockOf(folder).exists(), "the lock sits inside the logbook")
        val refused = assertFailsWith<IllegalStateException> { Universe.open(folder.toString()) }
        assertTrue("open for editing elsewhere" in refused.message.orEmpty(), refused.message)
        assertTrue("a Yemoja window" in refused.message.orEmpty(), "and says what has it")
        assertTrue(Lock.FILE in refused.message.orEmpty(), "and where the lock is, to remove it")
        first.close()
    }

    @Test
    fun `closing lets it go, and the next opening succeeds`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        first.close()
        assertTrue(!lockOf(folder).exists(), "the file is gone")
        val second = Universe.open(folder.toString())
        second.close()
        second.close()
        assertTrue(!lockOf(folder).exists(), "closing twice is allowed")
    }

    @Test
    fun `a logbook that will not read leaves no lock behind`() {
        val folder = logbook()
        folder.resolve("yemoja.json").writeText("""{"user": 1}""")
        assertFailsWith<RuntimeException> { Universe.open(folder.toString()) }
        assertTrue(!lockOf(folder).exists(), "nothing was opened, so nothing is held")
        val opened = Universe.open(folder.toString().also { folder.resolve("yemoja.json").writeText("{}") })
        opened.close()
    }

    @Test
    fun `making a logbook takes its lock the same way`() {
        val where = Files.createTempDirectory("yemoja-").resolve("new")
        val made = Universe.create(where.toString())
        assertTrue(lockOf(where).exists())
        assertFailsWith<IllegalStateException> { Universe.open(where.toString()) }
        made.close()
    }

    @Test
    fun `a path with a trailing separator is the same logbook, with the same lock in it`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        assertFailsWith<IllegalStateException> { Universe.open(folder.toString() + java.io.File.separator) }
        first.close()
    }

    @Test
    fun `the reader never reads the lock as part of the logbook`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        val read = LogbookReader.read(yemoja.data.json.DiskFileStore(folder.toString()), Types.ALL)
        assertEquals(emptyList(), read.allOf(Types.DIVE), "an empty logbook, lock and all")
        first.close()
    }

    @Test
    fun `closing does not take away a lock that is no longer this window's`() {
        // A reader deleted a lock while its window was alive, and a second window took the logbook.
        val folder = logbook()
        val first = Universe.open(folder.toString())
        lockOf(folder).toFile().delete()
        val second = Universe.open(folder.toString())
        first.close()
        assertTrue(lockOf(folder).exists(), "the second window's lock survives the first closing")
        assertFailsWith<IllegalStateException> { Universe.open(folder.toString()) }
        second.close()
        assertTrue(!lockOf(folder).exists())
    }

    @Test
    fun `the refusal says what holds the lock and nothing of its token`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        assertEquals("a Yemoja window", Lock.holderOf(yemoja.data.json.DiskFileStore(folder.toString())))
        first.close()
    }

    @Test
    fun `a logbook nowhere on disk holds no lock, and closing it does nothing`() {
        val store = MemoryFileStore(emptyMap())
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
        universe.close()
        assertEquals(0, universe.revision, "nothing about it moved")
    }

    @Test
    fun `a holder that runs once on its device takes over the lock that device left`() {
        val store = MemoryFileStore(mapOf("yemoja.json" to "{}"))
        val phone = Holder("Yemoja on a phone", device = "phone-1", takesOver = true)
        Lock.take(store, phone.note, phone.device, phone.takesOver)!!
        // The app was ended without warning, and starts again.
        val again = Lock.take(store, phone.note, phone.device, phone.takesOver)
        assertTrue(again != null, "its own lock is taken over")
        again!!.release()
        assertTrue(!store.isFile(Lock.FILE))
    }

    @Test
    fun `another device's lock is refused, and a desktop never takes one over`() {
        val store = MemoryFileStore(mapOf("yemoja.json" to "{}"))
        Lock.take(store, "Yemoja on a phone", device = "phone-1", takesOver = true)!!
        assertEquals(null, Lock.take(store, "Yemoja on a tablet", device = "tablet-2", takesOver = true))
        val desktop = MemoryFileStore(mapOf("yemoja.json" to "{}"))
        Lock.take(desktop, Holder.WINDOW.note)!!
        assertEquals(null, Lock.take(desktop, Holder.WINDOW.note), "two windows on one machine")
    }
}
