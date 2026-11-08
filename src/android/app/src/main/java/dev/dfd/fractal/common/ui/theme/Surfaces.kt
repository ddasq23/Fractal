/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize

/** The recorded background that glass surfaces blur and redraw behind themselves. */
@Stable
class Backdrop internal constructor(val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val supportsBackdropBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Draws [background] full screen, records it so glass surfaces in [content] can blur it,
 * and draws [content] on top.
 */
@Composable
fun BackdropHost(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            content = background,
        )
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            content()
        }
    }
}

/**
 * Paints a panel/card/bar background following the current theme's [SurfaceStyle]:
 * opaque fill, or glass with a blurred backdrop, border and top highlight.
 */
fun Modifier.fractalSurface(
    shape: Shape,
    style: SurfaceStyle,
    fillOverride: Color? = null,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val glass = style.kind == SurfaceKind.GLASS || style.kind == SurfaceKind.DARK_GLASS
    val blurLayer = if (glass && backdrop != null && supportsBackdropBlur) rememberGraphicsLayer() else null
    var position by remember { mutableStateOf(Offset.Zero) }
    val fill = fillOverride ?: style.fill

    this
        .then(if (blurLayer != null) Modifier.onGloballyPositioned { position = it.positionInRoot() } else Modifier)
        .drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            if (blurLayer != null && backdrop != null) {
                val radius = style.backdropBlur.toPx()
                blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                blurLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                    val offset = backdrop.origin - position
                    translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
                }
                val clip = Path().apply { addOutline(outline) }
                clipPath(clip) { drawLayer(blurLayer) }
            }
            drawOutline(outline, fill)
            if (style.topHighlight.alpha > 0f) {
                drawOutline(
                    outline,
                    Brush.verticalGradient(
                        0f to style.topHighlight.copy(alpha = style.topHighlight.alpha * 0.45f),
                        0.18f to Color.Transparent,
                        endY = size.height,
                    ),
                )
            }
            if (style.borderWidth.value > 0f && style.borderColor.alpha > 0f) {
                drawOutline(outline, style.borderColor, style = Stroke(style.borderWidth.toPx()))
            }
        }
}

/** [fractalSurface] with the current theme's surface style and panel shape. */
@Composable
fun Modifier.themedPanel(shape: Shape = FractalTheme.shapes.panel): Modifier =
    fractalSurface(shape, FractalTheme.surface)
