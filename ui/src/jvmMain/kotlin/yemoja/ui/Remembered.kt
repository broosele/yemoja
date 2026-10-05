package yemoja.ui

import java.io.File

/*
 * What the window remembers between runs, kept in this user's own application data.
 *
 * See ../../../../../gui/desktop/doc.md — `DESK-12`.
 */

/**
 * This user's application data: outside the installation, so an update replaces the application
 * and leaves this.
 *
 * Windows' `LOCALAPPDATA`, and elsewhere the data folder of the XDG convention Linux desktops
 * follow, `~/.local/share` unless `XDG_DATA_HOME` moves it. `LNX-2`.
 */
internal fun localData(): File {
    System.getenv("LOCALAPPDATA")?.let { return File(it, "Yemoja") }
    val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")
    return File(data, "yemoja")
}

/** The logbook folder the window last opened, or absent where none was or the folder is gone. */
internal fun lastLogbook(base: File = localData()): String? =
    File(base, LAST_LOGBOOK).takeIf { it.isFile }?.readText()?.trim()
        ?.takeIf { it.isNotEmpty() && File(it).isDirectory }

/**
 * Remembers [folder] as the logbook to open next time the window starts with none named.
 *
 * Failing to remember costs one opening by hand, so a folder that cannot be written is let be.
 */
internal fun rememberLogbook(folder: String, base: File = localData()) {
    runCatching {
        base.mkdirs()
        File(base, LAST_LOGBOOK).writeText(File(folder).absolutePath)
    }
}

/** The file the last logbook's folder is kept in, one line holding its path. */
private const val LAST_LOGBOOK = "last-logbook.txt"
