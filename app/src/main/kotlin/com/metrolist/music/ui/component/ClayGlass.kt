/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tinted shadow ("clay3D" look) with a thin top highlight so the element reads as raised
 * instead of just glowing. Centralized here because the elevation/alpha values are a taste
 * call the user iterates on repeatedly (too strong -> too subtle -> back up) — tune once here
 * instead of hunting every call site.
 */
fun Modifier.clay3D(
    color: Color,
    elevation: Dp = 18.dp,
    shape: Shape = RoundedCornerShape(50),
    shadowAlpha: Float = 0.85f,
    highlightAlpha: Float = 0.35f,
): Modifier =
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = color.copy(alpha = shadowAlpha),
            spotColor = color.copy(alpha = shadowAlpha),
        ).border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = highlightAlpha), Color.Transparent)),
            shape = shape,
        )

/**
 * Translucent "frosted glass" surface: tinted background + soft shadow + vertical-gradient
 * edge highlight. Same recipe MiniPlayer already used by hand; reuse it instead of re-deriving
 * the alpha values per screen.
 */
fun Modifier.glass(
    backgroundColor: Color,
    accentColor: Color,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 18.dp,
    backgroundAlpha: Float = 0.55f,
    shadowAlpha: Float = 0.85f,
    borderTopAlpha: Float = 0.45f,
    borderBottomAlpha: Float = 0.1f,
    borderWidth: Dp = 1.5.dp,
    // Overrides the default white top->bottom gradient border, e.g. a screen that outlines
    // the glass panel in a solid accent color instead of a highlight (LoginScreen banner).
    borderBrush: Brush? = null,
): Modifier =
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = accentColor.copy(alpha = shadowAlpha),
            spotColor = accentColor.copy(alpha = shadowAlpha),
        ).clip(shape)
        .background(color = backgroundColor.copy(alpha = backgroundAlpha))
        .border(
            width = borderWidth,
            brush =
                borderBrush ?: Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = borderTopAlpha), Color.White.copy(alpha = borderBottomAlpha)),
                ),
            shape = shape,
        )
