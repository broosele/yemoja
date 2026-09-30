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

    private fun lockOf(folder: Path): Path = folder.resolveSibling(folder.fileName.toString() + Lock.BESIDE)

    @Test
    fun `opening takes the lock, and a second opening is refused and told where it is`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        assertTrue(lockOf(folder).exists(), "the lock sits beside the logbook")
        val refused = assertFailsWith<IllegalStateException> { Universe.open(folder.toString()) }
        assertTrue("open for editing elsewhere" in refused.message.orEmpty(), refused.message)
        assertTrue("a Yemoja window" in refused.message.orEmpty(), "and says what has it")
        assertTrue(Lock.BESIDE in refused.message.orEmpty(), "and where the lock is, to remove it")
        first.close()
    }

    @Test
    fun `closing lets it go, and the next opening succeeds`() {
        val folder = logbook()
        val first = Universe.open(folder.toString())
        first.close()
        assertTrue(!lockOf(folder).exists(), "the folder is gone")
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
    fun `a logbook nowhere on disk holds no lock, and closing it does nothing`() {
        val store = MemoryFileStore(emptyMap())
        val universe = Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
        universe.close()
        assertEquals(0, universe.revision, "nothing about it moved")
    }
}
