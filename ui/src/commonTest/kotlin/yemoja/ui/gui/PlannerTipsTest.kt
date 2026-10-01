package yemoja.ui.gui

import yemoja.logic.Scenario
import kotlin.test.Test
import kotlin.test.assertTrue

class PlannerTipsTest {

    @Test
    fun `every reserve scenario is named as a reserve`() {
        for (scenario in Scenario.entries) {
            assertTrue(tipOf(scenario).startsWith("Reserve for "), "${scenario.label}: ${tipOf(scenario)}")
        }
    }

    @Test
    fun `a tip opens as a label would, with no full stop`() {
        val tips = listOf(
            PlannerTips.RUNTIME,
            PlannerTips.DIRECTION,
            PlannerTips.DEPTH,
            PlannerTips.DURATION,
            PlannerTips.RATE,
            PlannerTips.GAS,
            PlannerTips.WORKED,
            PlannerTips.MODEL,
            PlannerTips.GF_LOW,
            PlannerTips.GF_HIGH,
            PlannerTips.BOTTOM_OXYGEN,
            PlannerTips.DECO_OXYGEN,
            PlannerTips.LEAST_OXYGEN,
            PlannerTips.DIVE_START,
            PlannerTips.AFTER,
            PlannerTips.DESCENT_RATE,
            PlannerTips.ASCENT_RATE,
            PlannerTips.SAFETY_DEPTH,
            PlannerTips.SAFETY_DURATION,
            PlannerTips.LAST_STOP,
            PlannerTips.WATER,
            PlannerTips.PANIC_FACTOR,
            PlannerTips.GAS_LOST,
            PlannerTips.PROBLEM_SOLVING,
            PlannerTips.NUMBER,
            PlannerTips.MIX,
            PlannerTips.ROLE,
            PlannerTips.VOLUME,
            PlannerTips.START,
            PlannerTips.SAC,
            PlannerTips.MOD,
            PlannerTips.USED,
            PlannerTips.END,
            PlannerTips.MINIMUM,
            PlannerTips.CNS,
            PlannerTips.OTU,
            PlannerTips.NO_FLY,
            PlannerTips.DESATURATION,
            PlannerTips.LOST_GAS,
            PlannerTips.SHARED,
        )
        for (tip in tips) {
            assertTrue(tip.first().isUpperCase() && !tip.endsWith("."), tip)
            assertTrue('.' !in tip, "one phrase, not sentences: $tip")
            assertTrue(tip.length <= 130, "short enough to read at a glance, but was ${tip.length}: $tip")
        }
    }
}
