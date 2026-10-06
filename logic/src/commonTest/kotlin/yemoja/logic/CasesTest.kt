package yemoja.logic

import yemoja.data.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Plans written down as data, and what they come to written back. See
 * ../../../../../../ui/api/doc.md — `API-8`.
 */

private fun read(json: String): Read = casesOf(Json.parse(json))

private fun oneOf(json: String): Case = assertIs<Read.Cases>(read(json)).cases.single()

private const val FORTY = """
    [{"name": "forty", "runtime": [{"depth": 40}, {"depth": 40, "duration": "22:46"}],
      "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
      "gradient_factor_low": 1, "gradient_factor_high": 1}]
"""

class CasesTest {

    @Test
    fun `a case is its lines, its cylinders and whatever settings it names`() {
        val case = oneOf(FORTY)
        assertEquals("forty", case.name)
        assertEquals(2, case.planned.segments.size)
        assertEquals("40", case.planned.segments.first().depth)
        assertEquals("22:46", case.planned.segments.last().duration)
        assertEquals("air", case.planned.gases.single().gas)
        assertEquals("100", case.planned.gradientLow)
    }

    @Test
    fun `a number says what a string says, so a file need not remember which is quoted`() {
        val quoted = oneOf(
            """[{"runtime": [{"depth": "40", "duration": "600"}], "gradient_factor_low": "1", "gradient_factor_high": "1"}]""",
        )
        val plain =
            oneOf("""[{"runtime": [{"depth": 40, "duration": 600}], "gradient_factor_low": 1, "gradient_factor_high": 1}]""")
        assertEquals(quoted.planned.segments, plain.planned.segments)
        assertEquals(quoted.planned.gradientLow, plain.planned.gradientLow)
    }

    @Test
    fun `a case may stop to switch gas, and is told so as true or false`() {
        assertEquals(false, oneOf(FORTY).planned.switchStops)
        assertEquals(
            true,
            oneOf("""[{"runtime": [{"depth": 40, "duration": 600}], "gas_switch_stops": true}]""").planned.switchStops,
        )
        assertEquals(
            "case 1 gas_switch_stops should be true or false, but was yes",
            assertIs<Read.Wrong>(read("""[{"runtime": [{"depth": 40}], "gas_switch_stops": "yes"}]""")).reason,
        )
    }

    @Test
    fun `what a case leaves out the application answers`() {
        val case = oneOf(FORTY)
        assertEquals(
            shownOf(Settings.DEFAULT_ASCENT_RATE, Settings.DEFAULT_ASCENT_RATE.default),
            case.planned.ascentRate,
        )
        assertEquals(Settings.DEFAULT_WATER_TYPE.default, case.planned.water)
        assertEquals("1.4", case.planned.bottomOxygen)
    }

    @Test
    fun `a cylinder is named by its number on a line, as it is everywhere else`() {
        val case = oneOf(
            """[{"runtime": [{"depth": 21, "gas": 2, "duration": 300}],
                "gases": [{"gas": "air"}, {"gas": "EAN50", "role": "deco"}],
                "gradient_factor_low": 0.3, "gradient_factor_high": 0.7}]""",
        )
        assertEquals(1, case.planned.segments.single().gas, "the second cylinder, counted from nought")
        assertEquals(Role.DECO, case.planned.gases[1].role)
    }

    @Test
    fun `a gradient factor is a proportion, as a logbook writes it, and a percentage is refused`() {
        val case = oneOf("""[{"runtime": [{"depth": 20, "duration": 600}], "gradient_factor_low": 0.3}]""")
        assertEquals("30", case.planned.gradientLow, "the form's percentage")
        assertEquals(
            "case 1 gradient_factor_low should be 0 to 1, but was 30",
            assertIs<Read.Wrong>(read("""{"runtime": [{"depth": 20}], "gradient_factor_low": 30}""")).reason,
        )
    }

    @Test
    fun `a time is in seconds, as a logbook writes it, and minutes and seconds still read`() {
        val case = oneOf(
            """{"runtime": [{"depth": 40}, {"depth": 40, "duration": 1366}, {"depth": 40, "duration": "2:00"}],
                "safety_stop_duration": 300}""",
        )
        assertEquals("22:46", case.planned.segments[1].duration)
        assertEquals("2:00", case.planned.segments[2].duration)
        assertEquals("5", case.planned.safetyMinutes, "the form's minutes")
    }

    @Test
    fun `the water is written as water_type, as a logbook writes it`() {
        assertEquals("fresh", oneOf("""{"runtime": [{"depth": 20}], "water_type": "fresh"}""").planned.water)
    }

    @Test
    fun `the atmospheric pressure is written as atmospheric_pressure, and is one atmosphere if left out`() {
        assertEquals("0.85", oneOf("""{"runtime": [{"depth": 20}], "atmospheric_pressure": 0.85}""").planned.atmosphericPressure)
        assertEquals(SEA_LEVEL_SAID, oneOf("""{"runtime": [{"depth": 20}]}""").planned.atmosphericPressure)
    }

    @Test
    fun `a case's END max and whether oxygen is narcotic are written as end_max and oxygen_narcotic`() {
        val case = oneOf("""{"runtime": [{"depth": 20}], "end_max": 40, "oxygen_narcotic": false}""").planned
        assertEquals("40", case.narcoticDepth)
        assertEquals(false, case.oxygenNarcotic)
        val plain = oneOf("""{"runtime": [{"depth": 20}]}""").planned
        assertEquals("50", plain.narcoticDepth)
        assertEquals(true, plain.oxygenNarcotic)
        assertEquals(
            "case 1 oxygen_narcotic should be true or false, but was yes",
            assertIs<Read.Wrong>(read("""{"runtime": [{"depth": 20}], "oxygen_narcotic": "yes"}""")).reason,
        )
    }

    @Test
    fun `a rebreather case says its mode, setpoints, its cylinders' roles and the loop on a line`() {
        val case = oneOf(
            """{"runtime": [{"depth": 40}, {"depth": 40, "duration": 600, "gas": 3}, {"depth": 30, "gas": "loop"}],
                "dive_mode": "ccr", "setpoint_low": 0.6, "setpoint_high": 1.2, "setpoint_switch_depth": 10,
                "gases": [{"gas": "TMX18/45", "role": "diluent"}, {"gas": "O2", "role": "rich"}, {"gas": "EAN50", "role": "bailout"}]}""",
        ).planned
        assertEquals("ccr", case.diveMode)
        assertEquals(listOf("0.6", "1.2", "10"), listOf(case.setpointLow, case.setpointHigh, case.setpointSwitchDepth))
        assertEquals(listOf(Role.DILUENT, Role.RICH, Role.BAILOUT), case.gases.map { it.role })
        assertEquals(listOf(null, 2, LOOP), case.segments.map { it.gas })
        val plain = oneOf("""{"runtime": [{"depth": 40}], "dive_mode": "ccr"}""").planned
        assertEquals(listOf("0.7", "1.3", "6"), listOf(plain.setpointLow, plain.setpointHigh, plain.setpointSwitchDepth))
        assertEquals("oc", oneOf("""{"runtime": [{"depth": 40}]}""").planned.diveMode)
        assertEquals(true, plain.bailoutScenario)
        assertEquals(listOf("4", "10"), listOf(plain.co2HitFactor, plain.co2HitMinutes))
        val hit = oneOf("""{"runtime": [{"depth": 40}], "co2_hit_factor": 3, "co2_hit_time": 300}""").planned
        assertEquals(listOf("3", "5"), listOf(hit.co2HitFactor, hit.co2HitMinutes), "the time in seconds, as a file writes it")
        assertEquals(false, oneOf("""{"runtime": [{"depth": 40}], "bailout_reserve": false}""").planned.bailoutScenario)
        assertEquals(
            "case 1 line 1 gas should be a cylinder's number or loop, not first",
            assertIs<Read.Wrong>(read("""{"runtime": [{"depth": 40, "gas": "first"}]}""")).reason,
        )
    }

    @Test
    fun `a plan on its own is a file of one`() {
        val cases = assertIs<Read.Cases>(
            read("""{"runtime": [{"depth": 20, "duration": 600}], "gradient_factor_low": 0.3, "gradient_factor_high": 0.7}"""),
        ).cases
        assertEquals(1, cases.size)
        assertEquals("case 1", cases.single().name, "and it is called by its place")
    }

    @Test
    fun `a file that is not a list of plans says what one looks like`() {
        assertTrue("a plan or a list of them" in assertIs<Read.Wrong>(read("3")).reason)
        assertTrue("written as a list" in assertIs<Read.Wrong>(read("""[{"name": "x"}]""")).reason)
        assertTrue("runtime is empty" in assertIs<Read.Wrong>(read("""[{"runtime": []}]""")).reason)
        assertTrue(
            "not bottom, deco, bailout, diluent or rich" in
                    assertIs<Read.Wrong>(
                        read("""[{"runtime": [{"depth": 10}], "gases": [{"gas": "air", "role": "spare"}]}]"""),
                    ).reason,
        )
    }

    @Test
    fun `the gas reserve's own settings default like every other`() {
        val case = oneOf(FORTY)
        assertEquals(
            shownOf(Settings.DEFAULT_STRESS_FACTOR, Settings.DEFAULT_STRESS_FACTOR.default),
            case.planned.stressFactor,
        )
        assertEquals(
            shownOf(Settings.DEFAULT_PROBLEM_SOLVING_TIME, Settings.DEFAULT_PROBLEM_SOLVING_TIME.default),
            case.planned.problemMinutes,
        )
        assertEquals(true, case.planned.lostGasScenario)
        assertEquals(null, case.planned.lostGas, "the first deco cylinder, worked out rather than named")
        assertEquals(true, case.planned.sharedScenario)
    }

    @Test
    fun `lost_gas names a cylinder by number, and lost_gas_reserve switches the scenario off`() {
        val lines = """"runtime": [{"depth": 20, "duration": 600}], "gases": [{"gas": "air"}, {"gas": "EAN50"}]"""
        assertEquals(1, oneOf("""{$lines, "lost_gas": 2}""").planned.lostGas, "counted from nought")
        assertEquals(true, oneOf("""{$lines, "lost_gas": 2}""").planned.lostGasScenario)
        assertEquals(
            false,
            oneOf("""{$lines, "lost_gas_reserve": false}""").planned.lostGasScenario,
        )
        assertEquals(null, oneOf("""{$lines}""").planned.lostGas, "the first deco cylinder, left to find itself")
        assertEquals(
            "case 1 lost_gas should be one of the 2 cylinders, not 3",
            assertIs<Read.Wrong>(read("""{$lines, "lost_gas": 3}""")).reason,
        )
        assertTrue(
            "should be a cylinder's number" in
                    assertIs<Read.Wrong>(read("""{$lines, "lost_gas": "two"}""")).reason,
        )
        assertTrue(
            "lost_gas_reserve should be true or false" in
                    assertIs<Read.Wrong>(read("""{$lines, "lost_gas_reserve": "yes"}""")).reason,
        )
    }

    @Test
    fun `shared_gas_reserve is true or false, as a logbook writes a tick`() {
        assertEquals(
            false,
            oneOf("""{"runtime": [{"depth": 20}], "shared_gas_reserve": false}""").planned.sharedScenario,
        )
        assertEquals(
            "case 1 shared_gas_reserve should be true or false, but was yes",
            assertIs<Read.Wrong>(read("""{"runtime": [{"depth": 20}], "shared_gas_reserve": "yes"}""")).reason,
        )
    }

    @Test
    fun `a case may follow an earlier case, with the surface interval between them in seconds`() {
        val lines = """"runtime": [{"depth": 30, "duration": 1200}]"""
        val cases = assertIs<Read.Cases>(
            read(
                """[{"name": "first", $lines},
                    {"name": "second", "follows": "first", "surface_interval": 3600, $lines}]""",
            ),
        ).cases
        assertEquals(null, cases[0].follows)
        assertEquals(0, cases[1].follows?.earlier)
        assertEquals(3600.0, cases[1].follows?.intervalSeconds)
    }

    @Test
    fun `following reaches earlier cases only, and never without saying how long the interval was`() {
        val lines = """"runtime": [{"depth": 20, "duration": 600}]"""
        assertEquals(
            "first follows second, which is no earlier case in this file",
            assertIs<Read.Wrong>(
                read(
                    """[{"name": "first", "follows": "second", "surface_interval": 60, $lines},
                        {"name": "second", $lines}]""",
                ),
            ).reason,
        )
        assertEquals(
            "second follows first, but nothing says how long the surface interval was",
            assertIs<Read.Wrong>(
                read("""[{"name": "first", $lines}, {"name": "second", "follows": "first", $lines}]"""),
            ).reason,
        )
        assertEquals(
            "second surface_interval should be 0 seconds or more, but was -60",
            assertIs<Read.Wrong>(
                read(
                    """[{"name": "first", $lines},
                        {"name": "second", "follows": "first", "surface_interval": -60, $lines}]""",
                ),
            ).reason,
        )
        assertEquals(
            "case 1 has a surface_interval but no follows, so there is nothing it comes after",
            assertIs<Read.Wrong>(read("""[{"surface_interval": 60, $lines}]""")).reason,
        )
    }
}

class ReportedTest {

    private fun schedule(): Schedule =
        assertIs<Calculated.Done>(calculated(oneOf(FORTY).planned)).schedule

    @Test
    fun `a row carries a column for each heading, in order`() {
        val row = rowOf("forty", schedule())
        assertEquals(COLUMNS.size, row.size)
        assertEquals("forty", row.first())
        assertEquals("40", row[COLUMNS.indexOf("max_depth_m")])
        assertEquals("25", row[COLUMNS.indexOf("bottom_minutes")], "up to the last line described")
        assertTrue(row[COLUMNS.indexOf("stop_minutes")].toDouble() > 0)
        assertEquals("", row.last(), "nothing was refused")
    }

    @Test
    fun `the stops read as a depth and the minutes held there, deepest first`() {
        val said = rowOf("forty", schedule())[COLUMNS.indexOf("stops")]
        val depths = said.split(" ").map { it.substringBefore("@").toDouble() }
        assertEquals(depths.sortedDescending(), depths)
        assertTrue(said.endsWith("@${schedule().stops.last().seconds / 60}"), said)
    }

    @Test
    fun `a plan that would not calculate is a row with its reason and nothing else`() {
        val row = rowOf("broken", "Line 1 needs a depth")
        assertEquals(COLUMNS.size, row.size)
        assertEquals("broken", row.first())
        assertEquals("Line 1 needs a depth", row.last())
        assertTrue(row.drop(1).dropLast(1).all { it.isEmpty() })
    }

    @Test
    fun `a field holding a comma or a quote is quoted, so the table still parses`() {
        val table = tableOf(listOf(rowOf("one, two", """he said "no"""")))
        assertTrue("\"one, two\"" in table, table)
        assertTrue("\"\"no\"\"" in table, table)
        assertEquals(COLUMNS.joinToString(","), table.lines().first())
    }

    @Test
    fun `the whole answer holds every line, marked as described or added`() {
        val said = Json.write(saidOf("forty", schedule()))
        assertTrue("\"added\": false" in said, "the lines the caller wrote")
        assertTrue("\"added\": true" in said, "and the way up")
        assertTrue("\"direction\": \"stay\"" in said)
        assertTrue("\"warnings\"" in said)
        assertTrue("\"gas_litres\"" in said)
    }
}
