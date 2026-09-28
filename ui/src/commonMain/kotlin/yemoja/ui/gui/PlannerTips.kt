package yemoja.ui.gui

/**
 * PlannerTips is what the dive planner says of each of its fields while the pointer rests on one.
 *
 * Each opens with a phrase naming what the field is, as a label would, with no full stop. What
 * follows a semicolon is what the field does to the plan, where that is not plain from the name.
 *
 * Examples: `Depth at the end of this line`, `Minimum duration of the safety stop; 0 means none`.
 */
internal object PlannerTips {
    // The runtime, a line each.
    const val RUNTIME = "Runtime at the end of this line, in whole minutes rounded up"
    const val DIRECTION = "Direction of this line: down, level or up"
    const val DEPTH = "Depth at the end of this line"
    const val DURATION = "Duration of this line; give it or the rate, and the other is calculated"
    const val RATE = "Descent or ascent rate of this line; give it or the duration, and the other is calculated"
    const val GAS = "Gas source breathed on this line; in italics when it follows the line above"
    const val ADD_LINE = "Add a line below"
    const val REMOVE_LINE = "Take out this line"
    const val WORKED = "Ascent calculated by the planner; lines you type yourself shorten it"

    // The settings.
    const val MODEL = "Decompression model; the only one for now"
    const val GF_LOW = "Gradient factor at the deepest stop; lower means deeper first stops"
    const val GF_HIGH = "Gradient factor at the surface; lower means longer shallow stops"
    const val BOTTOM_OXYGEN = "Oxygen pressure limit for bottom and bailout gases; sets their MOD"
    const val DECO_OXYGEN = "Oxygen pressure limit for deco gases; sets the depth each is switched to"
    const val LEAST_OXYGEN = "Least oxygen pressure any gas may be breathed at; a hypoxic gas is warned of above its minimum depth"
    const val DESCENT_RATE = "Descent rate for lines given no duration or rate"
    const val ASCENT_RATE = "Ascent rate for the calculated ascent, and for lines given no duration or rate"
    const val SAFETY_DEPTH = "Depth of the safety stop"
    const val SAFETY_DURATION = "Minimum duration of the safety stop; 0 means none"
    const val LAST_STOP = "Depth of the shallowest deco stop"
    const val WATER = "Salt or fresh water; salt water is denser, so the same depth is a higher pressure"
    const val PANIC_FACTOR = "Breathing rate multiplier for two divers sharing gas, in the buddy out of gas reserve"
    const val GAS_LOST = "Gas source missing in the lost gas reserve; None leaves that reserve out"
    const val PROBLEM_SOLVING = "Time at depth solving the problem before the ascent, in both reserves; 0 means none"

    // The gases, a column each.
    const val NUMBER = "Number identifying the gas source"
    const val MIX = "Gas mix, such as AIR, EAN32 or TMX18/45"
    const val ROLE = "Role of the gas source; the planner switches to bottom and deco gases, and to bailout only where a line names it"
    const val VOLUME = "Water volume of the cylinder, in litres"
    const val START = "Cylinder pressure at the start of the dive"
    const val SAC = "Surface breathing rate on this gas, in litres a minute"
    const val MOD = "Maximum operating depth, at the oxygen limit for this gas's role"
    const val USED = "Gas used by the plan, in litres at the surface"
    const val END = "Cylinder pressure at the end of the dive"
    const val MINIMUM = "Pressure the cylinder must still hold at the reserve's worst moment; red where the plan leaves less"
    const val ADD_GAS = "Add a gas source below"
    const val REMOVE_GAS = "Take out this gas source"

    // What the whole dive comes to.
    const val CNS = "Oxygen exposure of the central nervous system, as a share of the limit"
    const val OTU = "Oxygen exposure of the lungs, in oxygen toxicity units"
    const val NO_FLY = "Waiting time after the dive before flying"
    const val DESATURATION = "Time until the model counts every tissue as clear of the dive"

    // The reserve.
    const val LOST_GAS = "Reserve for losing the gas source chosen as lost, at the worst moment"
    const val SHARED = "Reserve for sharing your gas with a buddy from the worst moment until a deco gas, each at the panic stress factor"
}
