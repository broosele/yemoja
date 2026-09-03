package yemoja.data

import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The same questions, asked of the real thing.
 *
 * This one touches the disk, which the tests of `data/json` are allowed to do because the real
 * format is what they are testing. `TEST-4`. It writes only under a folder of its own making, and
 * splits the files into a logbook folder and a library folder as an installation has them.
 */
class DiskFileStoreTest : GetPathsTest() {

    override fun storeOf(
        files: Map<String, String>,
        libraries: Map<String, List<String>>,
    ): FileStore {
        val at = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "yemoja-${Random.nextLong()}"
        val within = "${FileStore.LIBRARIES}/"
        write(at / "logbook", files.filterKeys { !it.startsWith(within) })
        write(
            at / "libraries",
            files.filterKeys { it.startsWith(within) }.mapKeys { it.key.removePrefix(within) },
        )
        return DiskFileStore(
            (at / "logbook").toString(),
            (at / "libraries").toString(),
            libraries,
        )
    }

    private fun write(root: Path, files: Map<String, String>) {
        val system = FileSystem.SYSTEM
        system.createDirectories(root)
        for ((path, text) in files) {
            val file = path.split('/').fold(root) { under, name -> under / name }
            file.parent?.let { system.createDirectories(it) }
            system.write(file) { writeUtf8(text) }
        }
    }

    @Test
    fun `an installation with no library folder passes over every library`() {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "yemoja-${Random.nextLong()}"
        write(root, mapOf("region.json" to "{}"))
        val store = DiskFileStore(root.toString(), null, mapOf("region" to listOf("region/world")))
        assertFalse(store.isFile("libraries/region/world.json"))
        assertEquals(listOf("region.json"), store.getPaths("region"))
    }
}
