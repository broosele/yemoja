package yemoja.logic

import yemoja.data.Stored
import yemoja.data.json.FileStore
import yemoja.data.json.SettingsFile
import yemoja.data.json.SettingsFiles

/*
 * What the user chose, asked of one object by every front end.
 *
 * See ../../../../../doc.md — the Universe; `UI-2` in ../../../../../../ui/doc.md for why it lives
 * here, and `DATA-9` in ../../../../../../data/doc.md for the layers.
 */

/**
 * Setting is one thing a user may choose, what it may hold, and what holds where they chose nothing.
 *
 * **Its granularity is fixed here, with its name.** A setting meant for one kind of device carries
 * the prefix in its name, and a reader asks for that whole name: there is no falling back from one
 * prefix to another. `DATA-53`.
 *
 * Immutable.
 */
class Setting internal constructor(
    /** As written in a settings file. */
    val name: String,
    /** What a front end calls it. */
    val label: String,
    /** The unit it is written in, which is the setting's own: a settings file declares no units. */
    val unit: String,
    /** What holds where nobody chose, or absent where the application does not choose for them. */
    val default: Double?,
    /** What a written value must lie in to be read at all. */
    val range: ClosedFloatingPointRange<Double>,
)

/**
 * Settings are what a logbook's user chose, asked for by name and answered from the first layer
 * that has an answer.
 *
 * **Three layers, first that answers winning**: this device's file, the logbook's, then the
 * setting's own default. `DATA-9`. A value that will not read, or lies outside the setting's range,
 * is ignored as if it were not there, and the next layer answers. That is what `manual/settings.md`
 * promises: a setting Yemoja cannot make sense of is ignored, and the default shown.
 *
 * **Not a change, and not in the journal.** Settings are not part of the data model, so writing one
 * goes straight to its file and moves nothing a `Change` would. The files themselves are listed in
 * data/json/doc.md.
 *
 * **Defaults reach the making of something, never the reading of it.** A plan's gradient factors
 * are copied onto the plan when it is started, and an ascent rate is an argument when the ascent is
 * written; nothing that works out what a dive or a plan comes to reads a setting, so changing one
 * moves nothing already made. `LOGIC-35`, `LOGIC-37`.
 *
 * Not immutable: a setting written is read back at once.
 */
class Settings internal constructor(private val store: FileStore) {

    private val held = HashMap<SettingsFile, Map<String, Stored>>()

    /** What [setting] holds, from the first layer that answers with something it can read. */
    fun number(setting: Setting): Double? {
        for (file in SettingsFile.entries) {
            readable(setting, file)?.let { return it }
        }
        return setting.default
    }

    /** The file [setting] is answered from, or absent where its default answers. */
    fun answeredBy(setting: Setting): SettingsFile? =
        SettingsFile.entries.firstOrNull { readable(setting, it) != null }

    /**
     * Chooses [value] for [setting], or takes the choice away where [value] is absent.
     *
     * **Written where it is already kept, and otherwise to the logbook's file**, so a choice made on
     * one device reaches every other. Only a setting already kept on this device alone is written
     * there, which is where somebody put it on purpose. A value outside the setting's range is
     * refused rather than written, being one the next read would ignore.
     */
    fun choose(setting: Setting, value: Double?): Outcome {
        if (value != null && value !in setting.range) {
            return Outcome.Refused(
                "${setting.label} should be ${setting.range.start} to " +
                    "${setting.range.endInclusive}, but was $value",
            )
        }
        val file = if (keeps(setting, SettingsFile.LOCAL)) SettingsFile.LOCAL else SettingsFile.LOGBOOK
        SettingsFiles.write(store, file, setting.name, value?.let { Stored.Leaf(it) })
        held.remove(file)
        return Outcome.Done()
    }

    /** Whether [file] names [setting] at all, readable or not. */
    private fun keeps(setting: Setting, file: SettingsFile): Boolean = setting.name in of(file)

    /** What [file] says [setting] is, where it says something the setting can hold. */
    private fun readable(setting: Setting, file: SettingsFile): Double? {
        val value = (of(file)[setting.name] as? Stored.Leaf)?.value
        val number = when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            else -> return null
        }
        return number.takeIf { it in setting.range }
    }

    /** Every setting [file] holds, read once and again after it is written. */
    private fun of(file: SettingsFile): Map<String, Stored> =
        held.getOrPut(file) { SettingsFiles.read(store, file) }

    companion object {

        /** The low gradient factor a new plan starts with, from 0 to 1. None is assumed. */
        val DEFAULT_GF_LOW = Setting("default_gf_low", "GF low", "", null, 0.0..1.0)

        /** The high gradient factor a new plan starts with, from 0 to 1. None is assumed. */
        val DEFAULT_GF_HIGH = Setting("default_gf_high", "GF high", "", null, 0.0..1.0)

        /** How fast a new plan descends, in metres a minute. */
        val DEFAULT_DESCENT_RATE =
            Setting("default_descent_rate", "Descent rate", "m/min", 18.0, 1.0..60.0)

        /** How fast an ascent is written to rise, in metres a minute. */
        val DEFAULT_ASCENT_RATE =
            Setting("default_ascent_rate", "Ascent rate", "m/min", 9.0, 1.0..30.0)

        /** How deep an ascent takes its shallowest stop, in metres. */
        val DEFAULT_LAST_STOP = Setting("default_last_stop", "Last stop", "m", 3.0, 0.0..12.0)

        /** Every setting there is, in the order a screen offers them. */
        val ALL: List<Setting> = listOf(
            DEFAULT_GF_LOW,
            DEFAULT_GF_HIGH,
            DEFAULT_DESCENT_RATE,
            DEFAULT_ASCENT_RATE,
            DEFAULT_LAST_STOP,
        )
    }
}
