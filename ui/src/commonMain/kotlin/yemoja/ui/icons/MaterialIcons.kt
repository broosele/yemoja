/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Changed for Yemoja: the eleven icons it uses from material-icons-extended 1.7.3, written out
 * from that library's own drawing data so that the library, 37 MB, need not be shipped. This file
 * and the LICENSE beside it are not covered by the repository's own licence. `DESK-11`.
 */

package yemoja.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** `Filled.ArrowRight`. */
val Icons.Filled.ArrowRight: ImageVector get() = arrowRight

private val arrowRight: ImageVector by lazy {
    materialIcon(name = "Filled.ArrowRight") {
        materialPath {
            moveTo(10f, 17f)
            lineToRelative(5f, -5f)
            lineToRelative(-5f, -5f)
            verticalLineToRelative(10f)
            close()
        }
    }
}

/** `Filled.AutoAwesome`. */
val Icons.Filled.AutoAwesome: ImageVector get() = autoAwesome

private val autoAwesome: ImageVector by lazy {
    materialIcon(name = "Filled.AutoAwesome") {
        materialPath {
            moveTo(19f, 9f)
            lineToRelative(1.25f, -2.75f)
            lineTo(23f, 5f)
            lineToRelative(-2.75f, -1.25f)
            lineTo(19f, 1f)
            lineToRelative(-1.25f, 2.75f)
            lineTo(15f, 5f)
            lineToRelative(2.75f, 1.25f)
            lineTo(19f, 9f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(9f, 4f)
            lineTo(6.5f, 9.5f)
            lineTo(1f, 12f)
            lineToRelative(5.5f, 2.5f)
            lineTo(9f, 20f)
            lineToRelative(2.5f, -5.5f)
            lineTo(17f, 12f)
            lineToRelative(-5.5f, -2.5f)
            close()
            moveTo(19f, 15f)
            lineToRelative(-1.25f, 2.75f)
            lineTo(15f, 19f)
            lineToRelative(2.75f, 1.25f)
            lineTo(19f, 23f)
            lineToRelative(1.25f, -2.75f)
            lineTo(23f, 19f)
            lineToRelative(-2.75f, -1.25f)
            lineTo(19f, 15f)
            close()
        }
    }
}

/** `Filled.Calculate`. */
val Icons.Filled.Calculate: ImageVector get() = calculate

private val calculate: ImageVector by lazy {
    materialIcon(name = "Filled.Calculate") {
        materialPath {
            moveTo(19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(13.03f, 7.06f)
            lineTo(14.09f, 6f)
            lineToRelative(1.41f, 1.41f)
            lineTo(16.91f, 6f)
            lineToRelative(1.06f, 1.06f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(1.41f, 1.41f)
            lineToRelative(-1.06f, 1.06f)
            lineTo(15.5f, 9.54f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(-1.06f, -1.06f)
            lineToRelative(1.41f, -1.41f)
            lineTo(13.03f, 7.06f)
            close()
            moveTo(6.25f, 7.72f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(1.5f)
            horizontalLineToRelative(-5f)
            verticalLineTo(7.72f)
            close()
            moveTo(11.5f, 16f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2f)
            horizontalLineTo(6f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(16f)
            close()
            moveTo(18f, 17.25f)
            horizontalLineToRelative(-5f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(5f)
            verticalLineTo(17.25f)
            close()
            moveTo(18f, 14.75f)
            horizontalLineToRelative(-5f)
            verticalLineToRelative(-1.5f)
            horizontalLineToRelative(5f)
            verticalLineTo(14.75f)
            close()
        }
    }
}

/** `Filled.DownloadDone`. */
val Icons.Filled.DownloadDone: ImageVector get() = downloadDone

private val downloadDone: ImageVector by lazy {
    materialIcon(name = "Filled.DownloadDone") {
        materialPath {
            moveTo(20.13f, 5.41f)
            lineToRelative(-1.41f, -1.41f)
            lineToRelative(-9.19f, 9.19f)
            lineToRelative(-4.25f, -4.24f)
            lineToRelative(-1.41f, 1.41f)
            lineToRelative(5.66f, 5.66f)
            close()
        }
        materialPath {
            moveTo(5f, 18f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-14f)
            close()
        }
    }
}

/** `Filled.Groups`. */
val Icons.Filled.Groups: ImageVector get() = groups

private val groups: ImageVector by lazy {
    materialIcon(name = "Filled.Groups") {
        materialPath {
            moveTo(12f, 12.75f)
            curveToRelative(1.63f, 0f, 3.07f, 0.39f, 4.24f, 0.9f)
            curveToRelative(1.08f, 0.48f, 1.76f, 1.56f, 1.76f, 2.73f)
            lineTo(18f, 18f)
            horizontalLineTo(6f)
            lineToRelative(0f, -1.61f)
            curveToRelative(0f, -1.18f, 0.68f, -2.26f, 1.76f, -2.73f)
            curveTo(8.93f, 13.14f, 10.37f, 12.75f, 12f, 12.75f)
            close()
            moveTo(4f, 13f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            curveTo(2f, 12.1f, 2.9f, 13f, 4f, 13f)
            close()
            moveTo(5.13f, 14.1f)
            curveTo(4.76f, 14.04f, 4.39f, 14f, 4f, 14f)
            curveToRelative(-0.99f, 0f, -1.93f, 0.21f, -2.78f, 0.58f)
            curveTo(0.48f, 14.9f, 0f, 15.62f, 0f, 16.43f)
            verticalLineTo(18f)
            lineToRelative(4.5f, 0f)
            verticalLineToRelative(-1.61f)
            curveTo(4.5f, 15.56f, 4.73f, 14.78f, 5.13f, 14.1f)
            close()
            moveTo(20f, 13f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            reflectiveCurveToRelative(-2f, 0.9f, -2f, 2f)
            curveTo(18f, 12.1f, 18.9f, 13f, 20f, 13f)
            close()
            moveTo(24f, 16.43f)
            curveToRelative(0f, -0.81f, -0.48f, -1.53f, -1.22f, -1.85f)
            curveTo(21.93f, 14.21f, 20.99f, 14f, 20f, 14f)
            curveToRelative(-0.39f, 0f, -0.76f, 0.04f, -1.13f, 0.1f)
            curveToRelative(0.4f, 0.68f, 0.63f, 1.46f, 0.63f, 2.29f)
            verticalLineTo(18f)
            lineToRelative(4.5f, 0f)
            verticalLineTo(16.43f)
            close()
            moveTo(12f, 6f)
            curveToRelative(1.66f, 0f, 3f, 1.34f, 3f, 3f)
            curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
            reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
            curveTo(9f, 7.34f, 10.34f, 6f, 12f, 6f)
            close()
        }
    }
}

/** `Filled.Map`. */
val Icons.Filled.Map: ImageVector get() = map

private val map: ImageVector by lazy {
    materialIcon(name = "Filled.Map") {
        materialPath {
            moveTo(20.5f, 3f)
            lineToRelative(-0.16f, 0.03f)
            lineTo(15f, 5.1f)
            lineTo(9f, 3f)
            lineTo(3.36f, 4.9f)
            curveToRelative(-0.21f, 0.07f, -0.36f, 0.25f, -0.36f, 0.48f)
            verticalLineTo(20.5f)
            curveToRelative(0f, 0.28f, 0.22f, 0.5f, 0.5f, 0.5f)
            lineToRelative(0.16f, -0.03f)
            lineTo(9f, 18.9f)
            lineToRelative(6f, 2.1f)
            lineToRelative(5.64f, -1.9f)
            curveToRelative(0.21f, -0.07f, 0.36f, -0.25f, 0.36f, -0.48f)
            verticalLineTo(3.5f)
            curveToRelative(0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
            close()
            moveTo(15f, 19f)
            lineToRelative(-6f, -2.11f)
            verticalLineTo(5f)
            lineToRelative(6f, 2.11f)
            verticalLineTo(19f)
            close()
        }
    }
}

/** `Filled.MenuBook`. */
val Icons.Filled.MenuBook: ImageVector get() = menuBook

private val menuBook: ImageVector by lazy {
    materialIcon(name = "Filled.MenuBook") {
        materialPath {
            moveTo(21f, 5f)
            curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
            curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
            curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
            reflectiveCurveTo(2.45f, 4.9f, 1f, 6f)
            verticalLineToRelative(14.65f)
            curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
            curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
            curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
            curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
            curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
            curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
            curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
            curveToRelative(0.25f, 0f, 0.5f, -0.25f, 0.5f, -0.5f)
            verticalLineTo(6f)
            curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21f, 5f)
            close()
            moveTo(21f, 18.5f)
            curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
            curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
            verticalLineTo(8f)
            curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
            curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
            verticalLineTo(18.5f)
            close()
        }
        materialPath {
            moveTo(17.5f, 10.5f)
            curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
            verticalLineTo(9.24f)
            curveTo(19.21f, 9.09f, 18.36f, 9f, 17.5f, 9f)
            curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
            verticalLineToRelative(1.66f)
            curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
            close()
        }
        materialPath {
            moveTo(13f, 12.49f)
            verticalLineToRelative(1.66f)
            curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
            curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
            verticalLineTo(11.9f)
            curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
            curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13f, 12.49f)
            close()
        }
        materialPath {
            moveTo(17.5f, 14.33f)
            curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
            verticalLineToRelative(1.66f)
            curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
            curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
            verticalLineToRelative(-1.52f)
            curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
            close()
        }
    }
}

/** `Filled.PropaneTank`. */
val Icons.Filled.PropaneTank: ImageVector get() = propaneTank

private val propaneTank: ImageVector by lazy {
    materialIcon(name = "Filled.PropaneTank") {
        materialPath {
            moveTo(4f, 15f)
            verticalLineToRelative(3f)
            curveToRelative(0f, 2.21f, 1.79f, 4f, 4f, 4f)
            horizontalLineToRelative(8f)
            curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
            verticalLineToRelative(-3f)
            horizontalLineTo(4f)
            close()
        }
        materialPath {
            moveTo(20f, 13f)
            verticalLineToRelative(-3f)
            curveToRelative(0f, -1.86f, -1.28f, -3.41f, -3f, -3.86f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(9f)
            curveTo(7.9f, 2f, 7f, 2.9f, 7f, 4f)
            verticalLineToRelative(2.14f)
            curveToRelative(-1.72f, 0.45f, -3f, 2f, -3f, 3.86f)
            verticalLineToRelative(3f)
            horizontalLineTo(20f)
            close()
            moveTo(9f, 4f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-2f)
            curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
            reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
            horizontalLineTo(9f)
            verticalLineTo(4f)
            close()
        }
    }
}

/** `Filled.ScubaDiving`. */
val Icons.Filled.ScubaDiving: ImageVector get() = scubaDiving

private val scubaDiving: ImageVector by lazy {
    materialIcon(name = "Filled.ScubaDiving") {
        materialPath {
            moveTo(1f, 13f)
            curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
            reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
            reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
            reflectiveCurveTo(1f, 14.1f, 1f, 13f)
            close()
            moveTo(8.89f, 10.11f)
            lineToRelative(4.53f, -1.21f)
            lineTo(12.64f, 6f)
            lineTo(8.11f, 7.21f)
            curveToRelative(-0.8f, 0.21f, -1.28f, 1.04f, -1.06f, 1.84f)
            lineToRelative(0f, 0f)
            curveTo(7.27f, 9.85f, 8.09f, 10.33f, 8.89f, 10.11f)
            close()
            moveTo(20.5f, 5.9f)
            lineTo(23f, 3f)
            lineToRelative(-1f, -1f)
            lineToRelative(-3f, 3f)
            lineToRelative(-2f, 4f)
            lineToRelative(-9.48f, 2.87f)
            curveToRelative(-0.82f, 0.2f, -1.39f, 0.89f, -1.5f, 1.68f)
            lineTo(5.24f, 18f)
            lineTo(2.4f, 21.8f)
            lineTo(4f, 23f)
            lineToRelative(3f, -4f)
            lineToRelative(1.14f, -3.14f)
            lineTo(14f, 14f)
            lineToRelative(5f, -3.5f)
            lineTo(20.5f, 5.9f)
            close()
        }
    }
}

/** `Filled.StarHalf`. */
val Icons.Filled.StarHalf: ImageVector get() = starHalf

private val starHalf: ImageVector by lazy {
    materialIcon(name = "Filled.StarHalf") {
        materialPath {
            moveTo(22f, 9.24f)
            lineToRelative(-7.19f, -0.62f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineToRelative(5.46f, 4.73f)
            lineTo(5.82f, 21f)
            lineTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineToRelative(-1.63f, -7.03f)
            lineTo(22f, 9.24f)
            close()
            moveTo(12f, 15.4f)
            verticalLineTo(6.1f)
            lineToRelative(1.71f, 4.04f)
            lineToRelative(4.38f, 0.38f)
            lineToRelative(-3.32f, 2.88f)
            lineToRelative(1f, 4.28f)
            lineTo(12f, 15.4f)
            close()
        }
    }
}

/** `Filled.StarOutline`. */
val Icons.Filled.StarOutline: ImageVector get() = starOutline

private val starOutline: ImageVector by lazy {
    materialIcon(name = "Filled.StarOutline") {
        materialPath {
            moveTo(22f, 9.24f)
            lineToRelative(-7.19f, -0.62f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineToRelative(5.46f, 4.73f)
            lineTo(5.82f, 21f)
            lineTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineToRelative(-1.63f, -7.03f)
            lineTo(22f, 9.24f)
            close()
            moveTo(12f, 15.4f)
            lineToRelative(-3.76f, 2.27f)
            lineToRelative(1f, -4.28f)
            lineToRelative(-3.32f, -2.88f)
            lineToRelative(4.38f, -0.38f)
            lineTo(12f, 6.1f)
            lineToRelative(1.71f, 4.04f)
            lineToRelative(4.38f, 0.38f)
            lineToRelative(-3.32f, 2.88f)
            lineToRelative(1f, 4.28f)
            lineTo(12f, 15.4f)
            close()
        }
    }
}
