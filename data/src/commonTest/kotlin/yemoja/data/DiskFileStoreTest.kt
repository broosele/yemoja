package yemoja.data

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random

/**
 * The same questions, asked of the real thing.
 *
 * This one touches the disk, which the tests of `data/json` are allowed to do because the real
 * format is what they are testing. `TEST-4`. It writes only under a folder of its own making.
 */
class DiskFileStoreTest : FileStoreTest() {

    override fun storeOf(files: Map<String, String>): FileStore {
        val system = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "yemoja-${Random.nextLong()}"
        for ((path, text) in files) {
            val at = path.split('/').fold(root) { under, name -> under / name }
            at.parent?.let { system.createDirectories(it) }
            system.write(at) { writeUtf8(text) }
        }
        return DiskFileStore(root.toString())
    }
}
