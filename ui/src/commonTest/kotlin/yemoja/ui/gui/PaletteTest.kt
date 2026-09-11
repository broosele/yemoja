package yemoja.ui.gui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/*
 * That the marine colours stay readable. A hand-filled scheme can pair a dark on a dark and
 * nothing would say so until somebody squints. See ../../../../../../gui/doc.md — `GUI-3`.
 */
class PaletteTest {

    @Test
    fun `every colour with an on-colour reads at body-text contrast, in the light`() {
        readable(MARINE_LIGHT)
    }

    @Test
    fun `every colour with an on-colour reads at body-text contrast, in the dark`() {
        readable(MARINE_DARK)
    }

    @Test
    fun `text and the greyed worked-out values read on every surface, in the light`() {
        onSurfaces(MARINE_LIGHT)
    }

    @Test
    fun `text and the greyed worked-out values read on every surface, in the dark`() {
        onSurfaces(MARINE_DARK)
    }

    private fun readable(scheme: ColorScheme) {
        val pairs = listOf(
            "primary" to (scheme.primary to scheme.onPrimary),
            "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
            "secondary" to (scheme.secondary to scheme.onSecondary),
            "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
            "tertiary" to (scheme.tertiary to scheme.onTertiary),
            "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
            "background" to (scheme.background to scheme.onBackground),
            "surface" to (scheme.surface to scheme.onSurface),
            "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
        )
        for ((role, colours) in pairs) atLeast(BODY, role, colours.first, colours.second)
    }

    private fun onSurfaces(scheme: ColorScheme) {
        val surfaces = listOf(
            "surface" to scheme.surface,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "secondaryContainer" to scheme.secondaryContainer,
        )
        for ((role, surface) in surfaces) {
            atLeast(BODY, "onSurface on $role", surface, scheme.onSurface)
            // The outline is what a worked-out value is greyed in, and it still has to be read.
            atLeast(LARGE, "outline on $role", surface, scheme.outline)
        }
    }

    private fun atLeast(ratio: Double, role: String, back: Color, front: Color) {
        val got = contrast(back, front)
        val was = "%.2f".format(got)
        assertTrue(got >= ratio, "$role should contrast at least $ratio, but was $was")
    }

    /** WCAG 2 contrast ratio: (lighter + 0.05) / (darker + 0.05) on relative luminance. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private companion object {
        /** WCAG AA for body text. */
        const val BODY = 4.5

        /** WCAG AA for large text, which is what a greyed value is held to rather than body. */
        const val LARGE = 3.0
    }
}
