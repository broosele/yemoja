package yemoja.ui.api

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * The planner as a command. See ../../../../../../ui/api/doc.md — `API-8`.
 */
class PlannerTest {

    private val cases = """
        [
          {"name": "forty", "lines": [{"depth": 40}, {"depth": 40, "duration": "22:46"}],
           "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
           "gradient_factor_low": 1, "gradient_factor_high": 1},
          {"name": "broken", "lines": [{"depth": 40, "duration": "soon"}],
           "gradient_factor_low": 1, "gradient_factor_high": 1}
        ]
    """.trimIndent()

    /** Runs the command over a file holding [json], and gives what it exited with and wrote. */
    private fun run(json: String, vararg arguments: String): Pair<Int, String> {
        val file = Files.createTempFile("cases", ".json")
        Files.writeString(file, json)
        val caught = ByteArrayOutputStream()
        val was = System.out
        System.setOut(PrintStream(caught, true, Charsets.UTF_8))
        val code = try {
            planned(file.toString(), arguments.toList())
        } finally {
            System.setOut(was)
            Files.deleteIfExists(file)
        }
        return code to caught.toString(Charsets.UTF_8)
    }

    @Test
    fun `a file of plans comes back as a table, one row a plan`() {
        val (code, said) = run(cases)
        assertEquals(0, code)
        val lines = said.trim().lines()
        assertEquals(COLUMNS.joinToString(","), lines.first())
        assertEquals(3, lines.size, "the headings and a row each")
        assertTrue(lines[1].startsWith("forty,40,25,"))
    }

    @Test
    fun `one plan that will not calculate does not cost the others their answer`() {
        val (code, said) = run(cases)
        assertEquals(0, code, "a case that will not read is reported rather than fatal")
        assertTrue(said.lines()[2].startsWith("broken,,"))
        assertTrue("not \"\"soon\"\"" in said, said)
    }

    @Test
    fun `asked for the whole answer, it gives every line of every dive`() {
        val (code, said) = run(cases, "--json")
        assertEquals(0, code)
        assertTrue("\"added\": true" in said, "the way up the model added")
        assertTrue("\"refused\":" in said, "and why the second one has none")
    }

    @Test
    fun `a file that is not there, or is not JSON, says so and stops`() {
        assertEquals(2, planned("D:/nowhere/cases.json", emptyList()))
        assertEquals(2, run("{oh no").first)
    }
}
