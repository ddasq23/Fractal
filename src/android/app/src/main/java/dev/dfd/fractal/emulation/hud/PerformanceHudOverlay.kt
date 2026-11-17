/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.emulation.hud

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dfd.fractal.common.settings.PerformanceHudSettings
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.FractalTokens

/** Room kept between the HUD and the screen edges. */
private val EdgeMargin = 4.dp

/**
 * The performance HUD over the game, in the app theme's look.
 *
 * The HUD is a canvas [PerformanceHudView] sized to its content, so it takes only the touches that land on it and the
 * on-screen controls below keep the rest of the screen. Dragging moves it through a graphics layer translation:
 * no recomposition and no new layout while the finger moves, only the layer's offset; the place is saved once, when
 * the finger lifts, through [onMoved] (fractions of the free room, see [PerformanceHudSettings.x]).
 */
@Composable
fun PerformanceHudOverlay(
    settings: PerformanceHudSettings,
    onMoved: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FractalTheme.tokens
    val style = remember(tokens) { HudStyle.from(tokens.toHudThemeColors()) }
    // the statistics stop while the game is in the background or behind another activity
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val running = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val margin = with(LocalDensity.current) { EdgeMargin.toPx() }
    val currentOnMoved by rememberUpdatedState(onMoved)
    val placement = remember { HudPlacement() }
    placement.margin = margin
    placement.target = Offset(settings.x, settings.y)

    // a place coming from the settings (a preset, a reset, the saved drag) replaces the one held after a drag
    LaunchedEffect(settings.x, settings.y) { placement.dropped = null }

    Box(modifier.fillMaxSize().onSizeChanged { placement.area = it }) {
        AndroidView(
            factory = { context ->
                PerformanceHudView(context).apply {
                    dragListener = object : PerformanceHudView.DragListener {
                        override fun onDragStart() = placement.startDrag()
                        override fun onDrag(dx: Float, dy: Float) = placement.drag(dx, dy)
                        override fun onDragEnd() {
                            val (x, y) = placement.endDrag() ?: return
                            currentOnMoved(x, y)
                        }
                    }
                }
            },
            update = { view ->
                view.applyStyle(style)
                view.applySettings(settings.items, settings.sizePercent, settings.opacityPercent, settings.vertical)
                view.setRunning(running)
            },
            onRelease = { view -> view.setRunning(false) },
            modifier = Modifier
                .onSizeChanged { placement.hud = it }
                .graphicsLayer {
                    // read here, the place only updates this layer: dragging doesn't recompose or lay out anything
                    translationX = placement.x()
                    translationY = placement.y()
                },
        )
    }
}

/** Where the HUD goes on the screen: the settings' place, or where the finger is taking it. */
@Stable
private class HudPlacement {
    var area by mutableStateOf(IntSize.Zero)
    var hud by mutableStateOf(IntSize.Zero)
    var margin = 0f

    /** The settings' place, as fractions. */
    var target by mutableStateOf(Offset.Zero)

    /** Where the HUD was dropped, as fractions, until the settings have it. */
    var dropped by mutableStateOf<Offset?>(null)

    private var dragging by mutableStateOf(false)
    private var dragX by mutableFloatStateOf(0f)
    private var dragY by mutableFloatStateOf(0f)
    private var startX = 0f
    private var startY = 0f

    private val freeX get() = (area.width - hud.width - 2 * margin).coerceAtLeast(0f)
    private val freeY get() = (area.height - hud.height - 2 * margin).coerceAtLeast(0f)

    fun x(): Float = if (dragging) dragX else margin + (dropped ?: target).x * freeX
    fun y(): Float = if (dragging) dragY else margin + (dropped ?: target).y * freeY

    fun startDrag() {
        startX = x()
        startY = y()
        dragX = startX
        dragY = startY
        dragging = true
    }

    fun drag(dx: Float, dy: Float) {
        if (!dragging) return
        dragX = (startX + dx).coerceIn(margin, margin + freeX)
        dragY = (startY + dy).coerceIn(margin, margin + freeY)
    }

    /** The place the HUD was dropped at, as fractions; null when it wasn't being dragged. */
    fun endDrag(): Pair<Float, Float>? {
        if (!dragging) return null
        val fx = if (freeX > 0f) ((dragX - margin) / freeX).coerceIn(0f, 1f) else target.x
        val fy = if (freeY > 0f) ((dragY - margin) / freeY).coerceIn(0f, 1f) else target.y
        dropped = Offset(fx, fy)
        dragging = false
        return fx to fy
    }
}

private fun FractalTokens.toHudThemeColors() = HudThemeColors(
    id = id,
    isDark = isDark,
    background = colors.background.toArgb(),
    surface = colors.surface.toArgb(),
    accent = colors.accent.toArgb(),
    text = colors.text.toArgb(),
    textSecondary = colors.textSecondary.toArgb(),
    border = colors.border.toArgb(),
)
