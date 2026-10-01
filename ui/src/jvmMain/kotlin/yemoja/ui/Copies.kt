package yemoja.ui

import yemoja.data.json.CachedFileStore
import yemoja.data.json.DiskFileStore
import yemoja.data.json.FileStore
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/*
 * A logbook folder read through a copy of its unchanged files, on a desktop.
 *
 * See ../../../../../../data/json/doc.md — `JSON-28`.
 */

/**
 * The logbook in [folder], read through a copy kept in this user's own application data.
 *
 * One copy per logbook, named after the folder's full path, so two logbooks never share one.
 */
internal fun copiedFrom(folder: String): FileStore =
    CachedFileStore(DiskFileStore(folder), { stampsUnder(folder) }, DiskFileStore(copiesOf(folder)))

/**
 * Every file under [folder] by its path there, stamped with its date and size.
 *
 * Read from the folders' own listings, which carry both for every file at once: hundreds of files
 * on a cloud drive in milliseconds, where asking each file for itself took as long as reading it.
 * A folder whose name begins with a dot is passed over, a logbook kept under version control
 * holding thousands of files the logbook never reads.
 */
private fun stampsUnder(folder: String): Map<String, String> {
    val root = Paths.get(folder)
    val found = HashMap<String, String>()
    try {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && dir.fileName.toString().startsWith(".")) FileVisitResult.SKIP_SUBTREE
                else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val path = root.relativize(file).joinToString("/")
                found[path] = "${attrs.lastModifiedTime().toMillis()}/${attrs.size()}"
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, failed: java.io.IOException): FileVisitResult =
                FileVisitResult.CONTINUE
        })
    } catch (failed: java.io.IOException) {
        // No stamps is no copy: every file is read from the logbook, which is only slower.
        return emptyMap()
    }
    return found
}

/** Where the copy of the logbook in [folder] is kept: this user's application data. */
private fun copiesOf(folder: String): String {
    val base = System.getenv("LOCALAPPDATA")?.let { File(it, "Yemoja") }
        ?: File(System.getProperty("user.home"), ".yemoja")
    val digest = MessageDigest.getInstance("SHA-256").digest(File(folder).canonicalPath.toByteArray())
    val name = digest.take(NAME_BYTES).joinToString("") { "%02x".format(it) }
    return File(File(base, "copies"), name).path
}

/** How many bytes of the folder's digest name its copy: enough that no two logbooks meet. */
private const val NAME_BYTES = 12
