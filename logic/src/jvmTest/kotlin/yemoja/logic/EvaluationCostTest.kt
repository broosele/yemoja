package yemoja.logic

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.measureTime

/*
 * What an evaluation costs on a recording the size of a real one, measured rather than assumed.
 *
 * A screen works one out inside composition, so a dive that takes a second to evaluate is a dive
 * that takes a second to open. This is a measurement printed for a reader, not a limit asserted:
 * a time is a property of the machine it ran on, and a suite that fails on a slow one teaches
 * people to ignore it.
 */
class EvaluationCostTest {

    @Test
    fun `evaluating a long recording is measured and said aloud`() {
        for (metres in listOf(30.0, 10.0)) {
            for (samples in listOf(200, 1000, 4000)) {
                val profile = profileOf(samples, metres)
                // Asserted, not assumed: a refusal returns in microseconds and reads as speed.
                assertIs<Evaluated.Done>(evaluate(profile), "$samples samples at $metres m")
                val took = measureTime { evaluate(profile) }
                println("$samples samples at $metres m: $took")
            }
        }
    }

    /**
     * A dive of [samples] points at [metres], a second apart, which is as often as a computer
     * samples and makes the longest recording anybody has the longest dive anybody made.
     *
     * The shallow one is the dear case: a run owing no stop asks for a no-decompression limit at
     * every sample, and the search for one walks forward a minute at a time.
     */
    private fun profileOf(samples: Int, metres: Double): OwnedItem {
        val depths = (0..<samples).joinToString(", ") { at ->
            "[$at, ${if (at == 0 || at == samples - 1) 0.0 else metres}]"
        }
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/d#0.json" to """{"environment": {"atmospheric_pressure": 1.0},
                        "gas_sources": {"g1": {"gas_type": "AIR"}},
                        "profiles": {"p1": {"water_type": "fresh", "gradient_factor_low": 0.3,
                            "gradient_factor_high": 0.75, "depth": [$depths]}}}""",
                ),
            ),
            Types.ALL,
        )
        val dive = set["d#0"]!!
        val held = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        return (held.getValue("p1") as Element.Usable).value
    }
}
