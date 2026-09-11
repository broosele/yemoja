package yemoja.ui.gui

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * The map library as the build bundles it. Runs on the JVM only, because it reads the jar's
 * resources. See ../../../../../../gui/doc.md — `GUI-25`.
 */
class MapBundledTest {

    @Test
    fun `every scale and layer is bundled and reads`() {
        val atlas = Atlas.read { scale, layer ->
            val path = "/libraries/map/$scale/$layer.txt"
            val stream = MapBundledTest::class.java.getResourceAsStream(path)
            assertNotNull(stream, "$path should be on the classpath")
            stream.bufferedReader().use { it.readText() }
        }
        for (layer in listOf(atlas.coarse, atlas.medium, atlas.fine)) {
            assertTrue(layer.land.isNotEmpty() && layer.cities.isNotEmpty())
        }
        // Finer is more: the point of three scales.
        assertTrue(atlas.coarse.land.sumOf { it.size } < atlas.medium.land.sumOf { it.size })
        assertTrue(atlas.medium.land.sumOf { it.size } < atlas.fine.land.sumOf { it.size })
    }
}
