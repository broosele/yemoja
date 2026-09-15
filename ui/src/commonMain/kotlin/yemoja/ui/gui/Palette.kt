package yemoja.ui.gui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The colours, which are the one part of the look that is ours.
 *
 * Material's own scheme is purple, and a logbook of the sea is not. These are the same roles
 * Material fills, filled from a marine hue instead: navy where the default is violet, and a
 * cool grey where it is a warm one. Everything the roles imply stays the platform's, so what
 * is chosen is tinted and what is worked out is greyed exactly as before, in other colours.
 *
 * See ../../../../../../gui/doc.md — `GUI-3`.
 */

/**
 * Caution, for a warning that is not yet a failure.
 *
 * Material's scheme has no role for it: there is an error and there is everything else, and a
 * thing falling due next week is neither. So these are the project's own, a pair to match the
 * container roles beside them, and they are read the same way — the container behind, the on
 * colour in front. `GUI-34`.
 *
 * Amber rather than a paler red, because the two warnings sit next to each other and have to be
 * told apart at a glance rather than by reading them.
 */
internal val CAUTION_LIGHT: Color = Color(0xFFFCEFC7)
internal val ON_CAUTION_LIGHT: Color = Color(0xFF4A3A00)
internal val CAUTION_DARK: Color = Color(0xFF4A3A00)
internal val ON_CAUTION_DARK: Color = Color(0xFFFCEFC7)

/** Marine, for a light window. */
internal val MARINE_LIGHT: ColorScheme = lightColorScheme(
    primary = Color(0xFF2A5A87),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0B1E33),
    secondary = Color(0xFF506A82),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F0),
    onSecondaryContainer = Color(0xFF0E1D2B),
    tertiary = Color(0xFF2E6B6A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEAE8),
    onTertiaryContainer = Color(0xFF002020),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF191C1F),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF191C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF424A53),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F8),
    surfaceContainer = Color(0xFFECF0F4),
    surfaceContainerHigh = Color(0xFFE6EBF0),
    surfaceContainerHighest = Color(0xFFE0E6EB),
    outline = Color(0xFF737B85),
    outlineVariant = Color(0xFFC2C8D0),
    surfaceTint = Color(0xFF2A5A87),
)

/** Marine, for a dark window. */
internal val MARINE_DARK: ColorScheme = darkColorScheme(
    primary = Color(0xFFA5C8F0),
    onPrimary = Color(0xFF0D2C4A),
    primaryContainer = Color(0xFF1E4267),
    onPrimaryContainer = Color(0xFFD3E4F7),
    secondary = Color(0xFFB8C9DB),
    onSecondary = Color(0xFF23323F),
    secondaryContainer = Color(0xFF394956),
    onSecondaryContainer = Color(0xFFD4E4F5),
    tertiary = Color(0xFFA2D1CF),
    onTertiary = Color(0xFF003736),
    tertiaryContainer = Color(0xFF1E4F4E),
    onTertiaryContainer = Color(0xFFBEE9E7),
    background = Color(0xFF101417),
    onBackground = Color(0xFFE0E3E7),
    surface = Color(0xFF101417),
    onSurface = Color(0xFFE0E3E7),
    surfaceVariant = Color(0xFF414A54),
    onSurfaceVariant = Color(0xFFC1C8D1),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262B2F),
    surfaceContainerHighest = Color(0xFF31363A),
    outline = Color(0xFF959DA6),
    outlineVariant = Color(0xFF414A54),
    surfaceTint = Color(0xFFA5C8F0),
)
