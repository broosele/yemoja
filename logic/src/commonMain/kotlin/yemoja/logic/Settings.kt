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
 * Setting is one thing a user may choose, under the name a settings file writes it.
 *
 * **Its granularity is fixed here, with its name.** A setting meant for one kind of device carries
 * the prefix in its name, and a reader asks for that whole name: there is no falling back from one
 * prefix to another. `DATA-53`.
 *
 * **So is its layer, where it has only one.** A setting [onlyHere] is read from this device's file
 * alone and written there alone: a copy found in the logbook's file is ignored, since that file
 * travels and what the setting holds is true of one machine. `DATA-9`.
 *
 * Immutable.
 */
sealed class Setting(
    /** As written in a settings file. */
    val name: String,
    /** What a front end calls it. */
    val label: String,
    /** Whether it belongs to this device alone, and never to the logbook's file. */
    val onlyHere: Boolean,
)

/**
 * NumberSetting is a setting holding a number, in a unit of its own.
 *
 * Immutable.
 */
class NumberSetting internal constructor(
    name: String,
    label: String,
    /** The unit it is written in, which is the setting's own: a settings file declares no units. */
    val unit: String,
    /** What holds where nobody chose, or absent where the application does not choose for them. */
    val default: Double?,
    /** What a written value must lie in to be read at all. */
    val range: ClosedFloatingPointRange<Double>,
) : Setting(name, label, onlyHere = false)

/**
 * TextSetting is a setting holding a line of text, with no default.
 *
 * Immutable.
 */
class TextSetting internal constructor(name: String, label: String, onlyHere: Boolean) :
    Setting(name, label, onlyHere)

/**
 * ChoiceSetting is a setting holding one of a fixed set of words, and a default among them.
 *
 * The words are what a settings file holds, so they follow the vocabulary of the data field the
 * setting feeds rather than how a front end shows them.
 *
 * Immutable.
 */
class ChoiceSetting internal constructor(
    name: String,
    label: String,
    /** Every word the setting may hold, in the order a front end offers them. */
    val choices: List<String>,
    /** What holds where nobody chose, which is always one of [choices]. */
    val default: String,
) : Setting(name, label, onlyHere = false) {

    init {
        require(default in choices) { "the default should be one of $choices, but was $default" }
    }
}

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
    fun number(setting: NumberSetting): Double? {
        for (file in layersOf(setting)) {
            readable(setting, file)?.let { return it }
        }
        return setting.default
    }

    /** What [setting] holds, from the first layer that answers with text, or absent where none does. */
    fun text(setting: TextSetting): String? {
        for (file in layersOf(setting)) {
            readable(setting, file)?.let { return it }
        }
        return null
    }

    /** What [setting] holds, from the first layer that answers with one of its words. */
    fun choice(setting: ChoiceSetting): String {
        for (file in layersOf(setting)) {
            readable(setting, file)?.let { return it }
        }
        return setting.default
    }

    /** The file [setting] is answered from, or absent where its default answers. */
    fun answeredBy(setting: Setting): SettingsFile? = layersOf(setting).firstOrNull { file ->
        when (setting) {
            is NumberSetting -> readable(setting, file) != null
            is TextSetting -> readable(setting, file) != null
            is ChoiceSetting -> readable(setting, file) != null
        }
    }

    /**
     * Chooses [value] for [setting], or takes the choice away where [value] is absent.
     *
     * **Written where it is already kept, and otherwise to the logbook's file**, so a choice made on
     * one device reaches every other. Only a setting already kept on this device alone is written
     * there, which is where somebody put it on purpose, and one [Setting.onlyHere] is written there
     * always. A value outside the setting's range is refused rather than written, being one the next
     * read would ignore.
     */
    fun choose(setting: NumberSetting, value: Double?): Outcome {
        if (value != null && value !in setting.range) {
            return Outcome.Refused(
                "${setting.label} should be ${setting.range.start} to " +
                    "${setting.range.endInclusive}, but was $value",
            )
        }
        write(setting, value?.let { Stored.Leaf(it) })
        return Outcome.Done()
    }

    /** Chooses [value] for [setting], or takes the choice away where it is absent or blank. */
    fun choose(setting: TextSetting, value: String?): Outcome {
        write(setting, value?.trim()?.ifEmpty { null }?.let { Stored.Leaf(it) })
        return Outcome.Done()
    }

    /** Chooses [value] for [setting], or takes the choice away where it is absent. */
    fun choose(setting: ChoiceSetting, value: String?): Outcome {
        if (value != null && value !in setting.choices) {
            return Outcome.Refused(
                "${setting.label} should be one of ${setting.choices.joinToString(", ")}, " +
                    "but was $value",
            )
        }
        write(setting, value?.let { Stored.Leaf(it) })
        return Outcome.Done()
    }

    /** Writes [value] for [setting] to the one file it belongs in. */
    private fun write(setting: Setting, value: Stored?) {
        val file = when {
            setting.onlyHere -> SettingsFile.LOCAL
            setting.name in of(SettingsFile.LOCAL) -> SettingsFile.LOCAL
            else -> SettingsFile.LOGBOOK
        }
        SettingsFiles.write(store, file, setting.name, value)
        held.remove(file)
    }

    /** The files [setting] may be answered from, in the order they answer. */
    private fun layersOf(setting: Setting): List<SettingsFile> =
        if (setting.onlyHere) listOf(SettingsFile.LOCAL) else SettingsFile.entries

    /** What [file] says [setting] is, where it says something the setting can hold. */
    private fun readable(setting: NumberSetting, file: SettingsFile): Double? {
        val value = (of(file)[setting.name] as? Stored.Leaf)?.value
        val number = when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            else -> return null
        }
        return number.takeIf { it in setting.range }
    }

    /** What [file] says [setting] is, where it says a line of text that is not blank. */
    private fun readable(setting: TextSetting, file: SettingsFile): String? =
        ((of(file)[setting.name] as? Stored.Leaf)?.value as? String)?.takeIf { it.isNotBlank() }

    /** What [file] says [setting] is, where it says one of the setting's words. */
    private fun readable(setting: ChoiceSetting, file: SettingsFile): String? =
        ((of(file)[setting.name] as? Stored.Leaf)?.value as? String)?.takeIf { it in setting.choices }

    /** Every setting [file] holds, read once and again after it is written. */
    private fun of(file: SettingsFile): Map<String, Stored> =
        held.getOrPut(file) { SettingsFiles.read(store, file) }

    companion object {

        /** The low gradient factor a new plan starts with, from 0 to 1. None is assumed. */
        val DEFAULT_GF_LOW = NumberSetting("default_gf_low", "GF low", "", null, 0.0..1.0)

        /** The high gradient factor a new plan starts with, from 0 to 1. None is assumed. */
        val DEFAULT_GF_HIGH = NumberSetting("default_gf_high", "GF high", "", null, 0.0..1.0)

        /** How fast a new plan descends, in metres a minute. */
        val DEFAULT_DESCENT_RATE =
            NumberSetting("default_descent_rate", "Descent rate", "m/min", 18.0, 1.0..60.0)

        /** How fast an ascent is written to rise, in metres a minute. */
        val DEFAULT_ASCENT_RATE =
            NumberSetting("default_ascent_rate", "Ascent rate", "m/min", 9.0, 1.0..30.0)

        /** How deep an ascent takes its shallowest stop, in metres. */
        val DEFAULT_LAST_STOP = NumberSetting("default_last_stop", "Last stop", "m", 3.0, 0.0..12.0)

        /** The most oxygen a new plan breathes a bottom gas at, in bar. */
        val DEFAULT_BOTTOM_PO2 =
            NumberSetting("default_bottom_po2", "pO₂ max bottom", "bar", 1.4, 0.5..2.0)

        /** The most oxygen a new plan breathes a deco gas at, in bar. */
        val DEFAULT_DECO_PO2 =
            NumberSetting("default_deco_po2", "pO₂ max deco", "bar", 1.6, 0.5..2.0)

        /** The least oxygen a new plan breathes any gas at, in bar. `LOGIC-37`. */
        val DEFAULT_MIN_PO2 =
            NumberSetting("default_min_po2", "pO₂ min", "bar", LEAST_OXYGEN, 0.1..0.5)

        /** How deep a new plan's safety stop is, in metres. */
        val DEFAULT_SAFETY_STOP_DEPTH =
            NumberSetting("default_safety_stop_depth", "Safety stop depth", "m", 6.0, 1.0..12.0)

        /** How long a new plan's safety stop lasts, in minutes. Nought is no safety stop. */
        val DEFAULT_SAFETY_STOP_DURATION =
            NumberSetting("default_safety_stop_duration", "Safety stop duration", "min", 3.0, 0.0..15.0)

        /**
         * How many times their usual rate each of two divers sharing gas breathes at, in a new
         * plan's reserve. Stress alone: the second diver is counted apart. `LOGIC-40`.
         */
        val DEFAULT_PANIC_FACTOR =
            NumberSetting("default_panic_factor", "Panic stress factor", "× SAC", 2.0, 1.0..10.0)

        /**
         * How long a new plan's reserve spends at the depth trouble starts before the way up
         * begins, in minutes, for finding the problem and a buddy. Nought is none. `LOGIC-40`.
         */
        val DEFAULT_PROBLEM_SOLVING_TIME =
            NumberSetting("default_problem_solving_time", "Problem solving time", "min", 2.0, 0.0..10.0)

        /** The water a new plan is dived in, in the words `water_type` uses. */
        val DEFAULT_WATER_TYPE =
            ChoiceSetting("default_water_type", "Water", listOf("salt", "fresh"), "salt")

        /**
         * The command that starts the user's agent, as it was last started.
         *
         * A desktop's, since only a desktop hosts an agent, and this device's alone: a command
         * names a program where one machine keeps it, often in a folder under a user's own name,
         * and the logbook's file carries whatever is in it to every machine. `GUI-38`.
         */
        val AGENT_COMMAND = TextSetting("desktop_agent_command", "Agent command", onlyHere = true)

        /**
         * Every setting the settings form offers, in its order.
         *
         * The agent's command is not among them. It is chosen where it is used, in the agent panel,
         * which remembers the one last started. `GUI-42`.
         */
        val OFFERED: List<NumberSetting> = listOf(
            DEFAULT_GF_LOW,
            DEFAULT_GF_HIGH,
            DEFAULT_DESCENT_RATE,
            DEFAULT_ASCENT_RATE,
            DEFAULT_LAST_STOP,
            DEFAULT_BOTTOM_PO2,
            DEFAULT_DECO_PO2,
            DEFAULT_MIN_PO2,
            DEFAULT_SAFETY_STOP_DEPTH,
            DEFAULT_SAFETY_STOP_DURATION,
            DEFAULT_PANIC_FACTOR,
            DEFAULT_PROBLEM_SOLVING_TIME,
        )

        /** Every setting holding one of a set of words that the settings form offers, after the numbers. */
        val OFFERED_CHOICES: List<ChoiceSetting> = listOf(DEFAULT_WATER_TYPE)
    }
}
