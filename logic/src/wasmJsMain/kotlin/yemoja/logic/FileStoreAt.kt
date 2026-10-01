package yemoja.logic

import yemoja.data.json.FileStore

/** Never called. A browser has no disk, and the planner never opens a [Universe] from a path. */
internal actual fun fileStoreAt(path: String): FileStore =
    throw UnsupportedOperationException("a browser has no disk to open $path from")
