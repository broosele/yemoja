package yemoja.logic

import yemoja.data.json.DiskFileStore
import yemoja.data.json.FileStore

/** The real disk, through [DiskFileStore], which a JVM and Android share. */
internal actual fun fileStoreAt(path: String): FileStore = DiskFileStore(path)
