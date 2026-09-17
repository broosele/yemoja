package yemoja.data.json

import yemoja.data.Stored

/*
 * The two files a logbook's settings are kept in, read and written as they stand.
 *
 * See ../../../../../../json/doc.md — the table of files under the layout, and `DATA-9` in
 * ../../../../../../doc.md for the layers.
 */

/**
 * SettingsFile is one of the two files beside a logbook that settings are kept in.
 *
 * In the order they answer: this device's first, then the logbook's. What neither holds is the
 * application's own default, which is not a file's to know. `DATA-9`.
 */
enum class SettingsFile(val file: String) {
    /** This device's, which answers first and never travels. */
    LOCAL("settings.local.json"),

    /** The logbook's, which travels with it. */
    LOGBOOK("settings.json"),
}

/**
 * SettingsFiles reads and writes the settings files, and knows nothing of what a setting means.
 *
 * **No defaults, no meaning, no refusals.** A setting is handed back as its file wrote it, and
 * whether it makes sense is asked by whoever asked for it. A file that is not there, or will not
 * read as JSON, or is not a set of names, answers nothing: settings are not part of the data model,
 * and one a reader cannot make sense of is ignored rather than stopping a logbook opening.
 */
object SettingsFiles {

    /** Every setting [file] in [store] holds, under its name. */
    fun read(store: FileStore, file: SettingsFile): Map<String, Stored> {
        if (!store.isFile(file.file)) return emptyMap()
        val read = try {
            Json.parse(store.readText(file.file))
        } catch (refused: JsonFormatException) {
            return emptyMap()
        }
        return (read as? Stored.Members)?.members.orEmpty()
    }

    /**
     * Puts [value] under [name] in [file], or takes the name out where [value] is absent.
     *
     * **Every other setting in the file is kept as it was**, including those this version of the
     * application does not know: a newer one may have written them, and a setting nobody here
     * understands is not this writer's to lose. A file that would not read is written over, having
     * held nothing that could be kept.
     */
    fun write(store: FileStore, file: SettingsFile, name: String, value: Stored?) {
        val held = LinkedHashMap(read(store, file))
        if (value == null) held.remove(name) else held[name] = value
        store.writeText(file.file, Json.write(Stored.Members(held)) + "\n")
    }
}
