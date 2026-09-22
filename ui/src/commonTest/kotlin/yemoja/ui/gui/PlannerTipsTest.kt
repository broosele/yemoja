package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertTrue

class PlannerTipsTest {

    @Test
    fun `every reserve scenario says what it checks`() {
        for (scenario in Scenario.entries) {
            assertTrue(scenario.tip.startsWith("Check that"), "${scenario.label}: ${scenario.tip}")
        }
    }

    @Test
    fun `a tip is a sentence or two, ending on a full stop`() {
        val tips = listOf(
            PlannerTips.RUNTIME, PlannerTips.DIRECTION, PlannerTips.DEPTH, PlannerTips.DURATION, PlannerTips.RATE,
            PlannerTips.GAS, PlannerTips.WORKED, PlannerTips.GF_LOW, PlannerTips.GF_HIGH, PlannerTips.BOTTOM_OXYGEN,
            PlannerTips.DECO_OXYGEN, PlannerTips.DESCENT_RATE, PlannerTips.ASCENT_RATE, PlannerTips.SAFETY_DEPTH,
            PlannerTips.SAFETY_DURATION, PlannerTips.LAST_STOP, PlannerTips.WATER, PlannerTips.PANIC_FACTOR,
            PlannerTips.GAS_LOST, PlannerTips.PROBLEM_SOLVING, PlannerTips.NUMBER, PlannerTips.MIX, PlannerTips.ROLE,
            PlannerTips.VOLUME, PlannerTips.START, PlannerTips.SAC, PlannerTips.MOD, PlannerTips.USED, PlannerTips.END,
            PlannerTips.MINIMUM, PlannerTips.CNS, PlannerTips.OTU, PlannerTips.NO_FLY, PlannerTips.DESATURATION,
            PlannerTips.LOST_GAS, PlannerTips.SHARED,
        )
        for (tip in tips) {
            assertTrue(tip.endsWith("."), tip)
            assertTrue(tip.count { it == '.' } <= 2, "at most two sentences: $tip")
            assertTrue(tip.length <= 130, "short enough to read at a glance, but was ${tip.length}: $tip")
        }
    }
}
