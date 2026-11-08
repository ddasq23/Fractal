/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.WordmarkStyle
import dev.dfd.fractal.common.ui.theme.fractalSurface

/** Gamepad A confirms like D-pad center/Enter do. */
fun Modifier.gamepadConfirm(onConfirm: () -> Unit): Modifier = onKeyEvent {
    if (it.key == Key.ButtonA && it.type == KeyEventType.KeyUp) {
        onConfirm()
        true
    } else false
}

/** White ring drawn around focused items; only visible while navigating with keys/gamepad. */
@Composable
fun Modifier.focusRing(focused: Boolean, shape: Shape, width: Dp = 3.dp, gap: Dp = 3.dp): Modifier {
    if (!focused) return this
    return this
        .border(width, FractalTheme.colors.focusRing, shape)
        .padding(gap)
}

@Composable
fun FractalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = FractalTheme.colors
    val shapes = FractalTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "button scale")
    val content = if (primary) colors.onAccent else colors.text
    val accentBrush = colors.accentBrush
    Box(
        modifier
            .scale(scale)
            .focusRing(focused, shapes.button)
            .clip(shapes.button)
            .then(
                when {
                    primary && accentBrush != null -> Modifier.background(accentBrush)
                    primary -> Modifier.background(colors.accent)
                    else -> Modifier.fractalSurface(shapes.button, FractalTheme.surface, colors.surfaceHigh)
                }
            )
            .gamepadConfirm { if (enabled) onClick() }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalContentColor provides content.copy(alpha = if (enabled) 1f else 0.4f)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
fun FractalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val colors = FractalTheme.colors
    val shapes = FractalTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .focusRing(focused, shapes.button, gap = 2.dp)
            .clip(shapes.button)
            .background(if (selected) colors.selected else Color.Transparent)
            .gamepadConfirm(onClick)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) colors.accent else colors.text,
            modifier = Modifier.size(22.dp),
        )
        if (selected) {
            // accent bar under the selected item
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 16.dp, height = 2.dp)
                    .background(colors.accent)
            )
        }
    }
}

@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, highlight: Boolean = false) {
    val colors = FractalTheme.colors
    Row(
        modifier
            .fractalSurface(
                FractalTheme.shapes.chip,
                FractalTheme.surface,
                if (highlight) colors.accent.copy(alpha = 0.22f) else colors.surfaceHigh,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null)
            Icon(icon, null, tint = if (highlight) colors.accent else colors.textSecondary, modifier = Modifier.size(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Three level Sierpinski triangle in a solid color, the app's mark. */
@Composable
fun SierpinskiMark(modifier: Modifier = Modifier, color: Color = FractalTheme.colors.accent) {
    Canvas(modifier) {
        val h = size.minDimension * 0.866f
        val side = size.minDimension
        val top = Offset(size.width / 2f, (size.height - h) / 2f)
        val path = Path()
        fun tri(a: Offset, b: Offset, c: Offset, depth: Int) {
            if (depth == 0) {
                path.moveTo(a.x, a.y); path.lineTo(b.x, b.y); path.lineTo(c.x, c.y); path.close()
                return
            }
            val ab = (a + b) / 2f
            val bc = (b + c) / 2f
            val ca = (c + a) / 2f
            tri(a, ab, ca, depth - 1)
            tri(ab, b, bc, depth - 1)
            tri(ca, bc, c, depth - 1)
        }
        tri(top, Offset(top.x + side / 2f, top.y + h), Offset(top.x - side / 2f, top.y + h), 3)
        drawPath(path, color)
    }
}

@Composable
fun FractalWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SierpinskiMark(Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text("FRACTAL", style = WordmarkStyle, color = FractalTheme.colors.text)
    }
}
