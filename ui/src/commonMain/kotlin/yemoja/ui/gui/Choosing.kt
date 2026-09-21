package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yemoja.data.json.SettingsFile
import yemoja.logic.ChoiceSetting
import yemoja.logic.Outcome
import yemoja.logic.NumberSetting
import yemoja.logic.Setting
import yemoja.logic.Settings
import yemoja.logic.Universe
import kotlin.math.roundToLong

/*
 * What the user chooses, shown and changed.
 *
 * See ../../../../../../gui/doc.md — `GUI-42`.
 */

/**
 * Choosing is the settings form while it is open: what is typed in each box, by setting name.
 *
 * Not immutable.
 */
internal class Choosing {
    var open: Boolean by mutableStateOf(false)
    val typed = mutableStateMapOf<String, String>()
    var said: String? by mutableStateOf(null)
}

/**
 * The settings, each with what it holds and where that came from, and a deed to save what changed.
 *
 * In the home screen's System box, opened by its own deed, as a download is: a short settings
 * list is a form rather than a place. Each says whether this device, this logbook or the
 * application answered it, since a choice kept on this device is one a reader may otherwise look
 * for in vain on another. `GUI-42`.
 */
@Composable
internal fun Chooser(universe: Universe?, choosing: Choosing, onChanged: () -> Unit = {}) {
    if (universe == null || !choosing.open) return
    val settings = universe.settings
    Column(modifier = Modifier.fillMaxWidth().padding(top = HALF)) {
        for (setting in Settings.OFFERED) {
            SettingRow(setting, BOX, unitOf(setting), answeredSaid(settings.answeredBy(setting), setting), choosing)
        }
        for (setting in Settings.OFFERED_CHOICES) {
            ChoiceRow(setting, answeredSaid(settings.answeredBy(setting), setting), choosing)
        }
        val agent = Settings.AGENT_COMMAND
        SettingRow(agent, COMMAND, "", answeredSaid(settings.answeredBy(agent), agent), choosing)
        Aside(AGENT_SETUP)
        choosing.said?.let { Aside(it) }
        Row(
            modifier = Modifier.padding(top = HALF),
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Button(
                onClick = {
                    choosing.said = saved(settings, choosing)
                    onChanged()
                },
            ) { Text("Save") }
            TextButton(onClick = { choosing.open = false }) { Text("Close") }
        }
    }
}

/** One setting: what it is called, the box it is typed in, and where its value came from. */
@Composable
private fun SettingRow(setting: Setting, wide: Dp, after: String, answered: String, choosing: Choosing) {
    Row(
        modifier = Modifier.padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        Box(modifier = Modifier.width(wide)) {
            Compact(
                value = choosing.typed[setting.name].orEmpty(),
                onChange = {
                    choosing.typed[setting.name] = it
                    choosing.said = null
                },
                after = after,
            )
        }
        Text(
            text = answered,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** One setting of a fixed set: what it is called, a menu of its words, and where it came from. */
@Composable
private fun ChoiceRow(setting: ChoiceSetting, answered: String, choosing: Choosing) {
    Row(
        modifier = Modifier.padding(vertical = HALF),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = setting.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        Box(modifier = Modifier.width(BOX)) {
            val held = choosing.typed[setting.name] ?: setting.default
            Pick(
                chosen = wordSaid(held),
                options = setting.choices.map { wordSaid(it) },
            ) { index ->
                choosing.typed[setting.name] = setting.choices[index]
                choosing.said = null
            }
        }
        Text(
            text = answered,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** A setting's word as a menu shows it: `salt` as *Salt*. */
internal fun wordSaid(word: String): String = word.replaceFirstChar { it.uppercase() }

/** Fills the form with what each setting holds now, as it is shown. */
internal fun Choosing.fill(settings: Settings) {
    typed.clear()
    for (setting in Settings.OFFERED) typed[setting.name] = shownOf(setting, settings.number(setting))
    for (setting in Settings.OFFERED_CHOICES) typed[setting.name] = settings.choice(setting)
    typed[Settings.AGENT_COMMAND.name] = settings.text(Settings.AGENT_COMMAND).orEmpty()
    said = null
}

/**
 * How the agent's command is set up, said under its box.
 *
 * The command is the adapter that speaks the protocol, not the agent's own program: what an
 * agent's maker publishes for editors. Said here because a reader who types the program itself
 * gets a panel that says *Starting* for ever, and the box is the place they are looking when
 * they type it.
 */
internal const val AGENT_SETUP: String =
    "The command that starts your agent, kept on this computer only. It is the agent's adapter " +
        "for editors, not the agent itself: for Claude Code it is npx @zed-industries/claude-code-acp, " +
        "and for Codex npx @zed-industries/codex-acp. Those need Node.js installed; the full path " +
        "to node.exe and to the adapter's index.js works in their place. Whatever the agent's " +
        "instructions give for using it from Zed or another editor is what to type here."

/**
 * Chooses every setting whose box says something other than what it holds, and says what came of it.
 *
 * All or nothing: every box is read before anything is written, so one that will not do leaves the
 * rest as they were rather than half the form saved.
 */
private fun saved(settings: Settings, choosing: Choosing): String {
    val chosen = LinkedHashMap<NumberSetting, Double?>()
    for (setting in Settings.OFFERED) {
        val typed = choosing.typed[setting.name].orEmpty()
        if (typed.trim() == shownOf(setting, settings.number(setting))) continue
        when (val read = chosenOf(setting, typed)) {
            is Entered.Wrong -> return read.reason
            is Entered.Value -> chosen[setting] = read.value
        }
    }
    val picked = LinkedHashMap<ChoiceSetting, String>()
    for (setting in Settings.OFFERED_CHOICES) {
        val typed = choosing.typed[setting.name] ?: continue
        if (typed != settings.choice(setting)) picked[setting] = typed
    }
    val command = choosing.typed[Settings.AGENT_COMMAND.name].orEmpty().trim()
    val commandMoved = command != settings.text(Settings.AGENT_COMMAND).orEmpty()
    if (chosen.isEmpty() && picked.isEmpty() && !commandMoved) return "Nothing was changed."
    for ((setting, value) in chosen) {
        val outcome = settings.choose(setting, value)
        if (outcome is Outcome.Refused) return outcome.reason
    }
    for ((setting, value) in picked) {
        val outcome = settings.choose(setting, value)
        if (outcome is Outcome.Refused) return outcome.reason
    }
    if (commandMoved) settings.choose(Settings.AGENT_COMMAND, command)
    choosing.fill(settings)
    return "Saved."
}

/** Entered is what was typed for a setting, read as what it would hold, or why it will not do. */
internal sealed class Entered {

    /** A value to choose, or absent to take the choice away. */
    data class Value(val value: Double?) : Entered()

    data class Wrong(val reason: String) : Entered()
}

/**
 * What [typed] chooses for [setting].
 *
 * **An empty box takes the choice away**, so what answers is the next layer: the logbook's, or the
 * application's default. The gradient factors are typed as percentages, as the plan form takes
 * them, and a value below one is refused rather than read as a fraction of a percent. `GUI-41`.
 */
internal fun chosenOf(setting: NumberSetting, typed: String): Entered {
    val text = typed.trim().removeSuffix("%").trim()
    if (text.isEmpty()) return Entered.Value(null)
    val number = text.toDoubleOrNull()
        ?: return Entered.Wrong("${setting.label} should be a number, but was \"${typed.trim()}\"")
    if (isPercentage(setting)) {
        if (number < 1 || number > PERCENT) {
            return Entered.Wrong("${setting.label} is a percentage from 1 to 100, but was $text")
        }
        return Entered.Value(number / PERCENT)
    }
    if (number !in setting.range) {
        return Entered.Wrong(
            "${setting.label} should be ${plain(setting.range.start)} to " +
                "${plain(setting.range.endInclusive)} ${setting.unit}, but was $text",
        )
    }
    return Entered.Value(number)
}

/** What a setting holds, as its box shows it: a percentage for a gradient factor, blank for none. */
internal fun shownOf(setting: NumberSetting, value: Double?): String = when {
    value == null -> ""
    isPercentage(setting) -> plain(value * PERCENT)
    else -> plain(value)
}

/** Where a setting's value came from, as the form says it beside the box. */
internal fun answeredSaid(file: SettingsFile?, setting: Setting): String = when (file) {
    SettingsFile.LOCAL -> "set on this device"
    SettingsFile.LOGBOOK -> "set in this logbook"
    null -> if ((setting as? NumberSetting)?.default == null) "not set" else "the default"
}

/** The unit written after a setting's box. */
private fun unitOf(setting: NumberSetting): String = if (isPercentage(setting)) "%" else setting.unit

/** Whether [setting] is held as a proportion and typed as a percentage, which the factors are. */
private fun isPercentage(setting: NumberSetting): Boolean =
    setting == Settings.DEFAULT_GF_LOW || setting == Settings.DEFAULT_GF_HIGH

/** A number as a person writes it: `18`, not `18.0`, and `9.5` where there is a fraction. */
internal fun plain(value: Double): String {
    val rounded = (value * 1000).roundToLong() / 1000.0
    return if (rounded == rounded.roundToLong().toDouble()) {
        rounded.roundToLong().toString()
    } else {
        rounded.toString()
    }
}

private const val PERCENT = 100.0

/** How wide a setting's box is. */
private val BOX = 110.dp

/** How wide the command's box is, a command being a line rather than a number. */
private val COMMAND = 420.dp
