package yemoja.ui.gui

/**
 * PlannerTips is what the dive planner says of each of its fields while the pointer rests on one.
 *
 * One sentence or two, in the words the manual uses, so a reader who has not opened the manual can
 * still fill the form in.
 */
internal object PlannerTips {
    // The runtime, a line each.
    const val RUNTIME = "Minutes from the start of the dive to the end of this line, rounded up."
    const val DIRECTION = "Whether this line goes down, stays level or goes up."
    const val DEPTH = "The depth this line ends at."
    const val DURATION = "How long this line takes. Give this or the rate, and the other is worked out."
    const val RATE = "How fast this line goes down or up. Give this or the duration, and the other is worked out."
    const val GAS = "The gas breathed on this line. In italics, it is the gas of the line above."
    const val ADD_LINE = "Add a line below."
    const val REMOVE_LINE = "Take out this line."
    const val WORKED = "The way up, worked out by the planner. Type lines of your own to shorten it."

    // The settings.
    const val GF_LOW = "Gradient factor for the deepest stop. Lower starts the stops deeper."
    const val GF_HIGH = "Gradient factor for surfacing. Lower makes the shallow stops longer."
    const val BOTTOM_OXYGEN = "The highest oxygen pressure for bottom and bailout gases. It sets their MOD."
    const val DECO_OXYGEN = "The highest oxygen pressure for deco gases. It sets how deep each can be switched to."
    const val DESCENT_RATE = "The speed of a line going down that has no duration or rate of its own."
    const val ASCENT_RATE = "The speed of the way up, and of a line going up that has no duration or rate of its own."
    const val SAFETY_DEPTH = "The depth of the safety stop."
    const val SAFETY_DURATION = "The shortest the safety stop may be. 0 means no safety stop."
    const val LAST_STOP = "The depth of the shallowest deco stop."
    const val WATER = "Salt or fresh water. Salt water weighs more, so a depth in it is a higher pressure."
    const val PANIC_FACTOR = "How much faster than usual two divers sharing gas breathe, for the buddy out of gas reserve."
    const val GAS_LOST = "The gas that is gone in the lost gas reserve."
    const val PROBLEM_SOLVING = "How long you stay at depth sorting out the trouble before the way up, in both reserve scenarios. 0 means none."

    // The gases, a column each.
    const val NUMBER = "The number that chooses this gas in the runtime."
    const val MIX = "The mix, such as AIR, EAN32 or TMX18/45."
    const val ROLE = "Bottom and deco gases are chosen by the planner for the way up. A bailout gas is breathed only on a line that names it."
    const val VOLUME = "The cylinder's size, as the water it holds, in litres."
    const val START = "The pressure the cylinder starts the dive at."
    const val SAC = "Your breathing rate on this gas at the surface, in litres a minute."
    const val MOD = "The deepest this gas may be breathed, at the oxygen limit for its role."
    const val USED = "The gas this plan takes from the cylinder, in litres at the surface."
    const val END = "The pressure left in the cylinder at the end of the dive."
    const val MINIMUM = "The pressure the cylinder must still hold at the reserve's worst moment. In red where the plan leaves less."
    const val ADD_GAS = "Add a gas below."
    const val REMOVE_GAS = "Take out this gas."

    // What the whole dive comes to.
    const val CNS = "Oxygen exposure of the brain and nerves, as a share of the limit."
    const val OTU = "Oxygen exposure of the lungs, in oxygen toxicity units."
    const val NO_FLY = "How long to wait after the dive before flying."
    const val DESATURATION = "How long until the model counts every tissue as clear of the dive."

    // The reserve.
    const val LOST_GAS = "Check that the other gases bring you up if the gas lost is gone at the worst moment."
    const val SHARED = "Check that your gas brings two divers up from the worst moment, sharing until a deco gas."
}
