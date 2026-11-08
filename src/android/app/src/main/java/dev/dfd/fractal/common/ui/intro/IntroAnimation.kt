/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.intro

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.dfd.fractal.common.ui.theme.FractalPalette
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.FractalThemeId
import dev.dfd.fractal.common.ui.theme.WordmarkStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Intro on app launch stays under 2 s; the one at the end of the welcome setup is a bit longer. */
private const val LAUNCH_MS = 1900
private const val SETUP_MS = 3000

/** True when the user turned animations off in the Android settings (animator duration scale 0). */
fun introAnimationsDisabled(): Boolean = !ValueAnimator.areAnimatorsEnabled()

/**
 * Polygons assembling into the Sierpinski mark and the FRACTAL wordmark, drawn in the current theme.
 * Skips on touch, on any key (consumed by [introKeyFilter]) or back.
 */
@Composable
fun IntroAnimation(long: Boolean, onFinished: () -> Unit) {
    val finishedCallback by rememberUpdatedState(onFinished)
    var done by remember { mutableStateOf(false) }
    val finish = {
        if (!done) {
            done = true
            finishedCallback()
        }
    }
    if (introAnimationsDisabled()) {
        LaunchedEffect(Unit) { finish() }
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(if (long) SETUP_MS else LAUNCH_MS, easing = LinearEasing))
        finish()
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    BackHandler { finish() }

    val theme = FractalTheme.tokens.id
    val scheme = MaterialTheme.colorScheme
    val spread = if (long) 1.6f else 1f

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - segment(progress.value, 0.88f, 1f) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    finish()
                }
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        val side = min(maxWidth.value, maxHeight.value) * 0.34f
        Canvas(Modifier.fillMaxSize()) {
            drawBackground(theme, scheme, progress.value)
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val markModifier = Modifier.size(side.dp, (side * H).dp)
            Canvas(
                if (theme == FractalThemeId.BLUR) {
                    markModifier.blur(
                        radius = (40f * (1f - FastOutSlowInEasing.transform(segment(progress.value, 0f, 0.7f)))).dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                } else markModifier
            ) {
                val t = progress.value
                val zoom = if (long) 1f + 0.12f * FastOutSlowInEasing.transform(segment(t, 0.8f, 1f)) else 1f
                drawMark(theme, scheme, t, spread, size.width * zoom, Offset(size.width * (1f - zoom) / 2f, size.height * (1f - zoom) / 2f))
            }
            Wordmark(theme, scheme, progress.value)
        }
    }
}

/**
 * While the intro plays, every key is swallowed (down and up), so the button that skips it doesn't
 * also act on the screen underneath; the skip happens on key up.
 */
fun introKeyFilter(playing: () -> Boolean, skip: () -> Unit): (androidx.compose.ui.input.key.KeyEvent) -> Boolean =
    { event ->
        if (!playing()) false
        else {
            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP) skip()
            true
        }
    }

// region geometry

private const val H = 0.8660254f

/** One of the 27 triangles of the 3-level mark, in a unit box (width 1, height [H]). */
private class Facet(val a: Offset, val b: Offset, val c: Offset, val index: Int) {
    val centroid = Offset((a.x + b.x + c.x) / 3f, (a.y + b.y + c.y) / 3f)
}

private val Facets: List<Facet> = buildList {
    fun tri(a: Offset, b: Offset, c: Offset, depth: Int) {
        if (depth == 0) {
            add(Facet(a, b, c, size))
            return
        }
        val ab = (a + b) / 2f
        val bc = (b + c) / 2f
        val ca = (c + a) / 2f
        tri(a, ab, ca, depth - 1)
        tri(ab, b, bc, depth - 1)
        tri(ca, bc, c, depth - 1)
    }
    tri(Offset(0.5f, 0f), Offset(1f, H), Offset(0f, H), 3)
}

/** Deterministic start state of each facet. */
private class Scatter(val angle: Float, val distance: Float, val rotation: Float, val scale: Float, val delay: Float)

private val Scatters: List<Scatter> = Facets.map { f ->
    val r = Random(f.index * 7919 + 17)
    Scatter(
        angle = r.nextFloat() * 2f * PI.toFloat(),
        distance = 0.8f + r.nextFloat() * 1.2f,
        rotation = r.nextFloat() * 360f - 180f,
        scale = 0.3f + r.nextFloat() * 1.4f,
        delay = (f.index / 27f) * 0.7f + r.nextFloat() * 0.3f,
    )
}

private fun segment(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)

private fun stepped(p: Float, steps: Int) = if (p >= 1f) 1f else floor(p * steps) / steps

private val Overshoot = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Progress of facet [i] inside the assembly window (0 .. 0.62 of the timeline). */
private fun facetProgress(t: Float, i: Int): Float {
    val start = Scatters[i].delay * 0.38f
    return segment(t, start, start + 0.24f)
}

/** Path of a facet moved from its scattered state towards its place; [e] = eased progress. */
private fun facetPath(
    f: Facet,
    side: Float,
    origin: Offset,
    e: Float,
    from: Offset,
    rotationDeg: Float,
    scale: Float,
): Path {
    val home = origin + f.centroid * side
    val center = from + (home - from) * e
    val rot = rotationDeg * (1f - e) * (PI.toFloat() / 180f)
    val s = scale + (1f - scale) * e
    val c = cos(rot)
    val sn = sin(rot)
    fun place(v: Offset): Offset {
        val d = (v - f.centroid) * side * s
        return center + Offset(d.x * c - d.y * sn, d.x * sn + d.y * c)
    }
    return Path().apply {
        val a = place(f.a)
        val b = place(f.b)
        val cc = place(f.c)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); close()
    }
}

private fun scatteredFrom(f: Facet, side: Float, origin: Offset, spread: Float): Offset {
    val sc = Scatters[f.index]
    val home = origin + f.centroid * side
    return home + Offset(cos(sc.angle), sin(sc.angle)) * (sc.distance * side * spread)
}

// endregion

// region mark per theme

private fun DrawScope.drawMark(theme: FractalThemeId, scheme: ColorScheme, t: Float, spread: Float, side: Float, origin: Offset) {
    when (theme) {
        FractalThemeId.SOLID -> Facets.forEach { f ->
            // dry cuts: facets jump in along the 45° chamfer diagonal, no easing, no rotation
            val p = stepped(facetProgress(t, f.index), 4)
            if (p <= 0f) return@forEach
            val dir = if (f.index % 2 == 0) Offset(-1f, -1f) else Offset(1f, 1f)
            val home = origin + f.centroid * side
            drawPath(facetPath(f, side, origin, p, home + dir * (0.5f * side * spread), 0f, 1f), FractalPalette.Magenta)
        }

        FractalThemeId.LIQUID_GLASS -> {
            val shift = t * 2.4f * side
            val glass = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.30f),
                    FractalPalette.Magenta.copy(alpha = 0.26f),
                    FractalPalette.Cyan.copy(alpha = 0.22f),
                    Color.White.copy(alpha = 0.10f),
                ),
                start = Offset(origin.x - side + shift, origin.y),
                end = Offset(origin.x + shift, origin.y + side * H),
            )
            val settle = segment(t, 0.6f, 0.82f)
            Facets.forEach { f ->
                val sc = Scatters[f.index]
                val e = FastOutSlowInEasing.transform(facetProgress(t, f.index))
                if (e <= 0f) return@forEach
                val path = facetPath(f, side, origin, e, scatteredFrom(f, side, origin, spread), sc.rotation, sc.scale)
                drawPath(path, glass, alpha = e)
                if (settle > 0f) drawPath(path, FractalPalette.Magenta, alpha = settle * 0.9f)
                drawPath(path, Color.White.copy(alpha = 0.55f * e), style = Stroke(width = 1.2.dp.toPx()))
            }
        }

        FractalThemeId.BLUR -> Facets.forEach { f ->
            // shapes start big and soft (the canvas is blurred) and come into focus in place
            val p = FastOutSlowInEasing.transform(facetProgress(t, f.index) * 0.6f + segment(t, 0f, 0.62f) * 0.4f)
            val home = origin + f.centroid * side
            val from = home + Offset(cos(Scatters[f.index].angle), sin(Scatters[f.index].angle)) * (0.25f * side * spread)
            drawPath(facetPath(f, side, origin, p, from, 0f, 2.2f), FractalPalette.Magenta, alpha = p)
        }

        FractalThemeId.MATERIAL_YOU -> {
            val tones = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
            val unify = segment(t, 0.62f, 0.8f)
            val round = Stroke(width = side * 0.025f, join = StrokeJoin.Round)
            Facets.forEach { f ->
                // tonal shapes bloom in place with a springy overshoot
                val p = facetProgress(t, f.index)
                if (p <= 0f) return@forEach
                val e = Overshoot.transform(p)
                val home = origin + f.centroid * side
                val color = lerp(tones[f.index % tones.size], scheme.primary, unify)
                // progress 0 keeps the facet at "from" (its home) with the given rotation and scale
                val path = facetPath(f, side, origin, 0f, home, -25f * (1f - e), e.coerceAtLeast(0f))
                drawPath(path, color, style = Fill)
                drawPath(path, color, style = round)
            }
        }

        FractalThemeId.SYNTHWAVE -> {
            val neon = Brush.linearGradient(
                listOf(FractalPalette.Magenta, FractalPalette.Purple, FractalPalette.Cyan),
                start = origin,
                end = origin + Offset(side, side * H),
            )
            Facets.forEach { f ->
                // neon outlines dropping in from above
                val e = FastOutSlowInEasing.transform(facetProgress(t, f.index))
                if (e <= 0f) return@forEach
                val home = origin + f.centroid * side
                val path = facetPath(f, side, origin, e, home - Offset(0f, side * 1.4f * spread), 0f, 1f)
                drawPath(path, neon, alpha = 0.12f * e, style = Stroke(width = 9.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, neon, alpha = 0.30f * e, style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, neon, alpha = e, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                drawPath(path, neon, alpha = 0.18f * e)
            }
        }
    }
}

// endregion

// region background per theme

private fun DrawScope.drawBackground(theme: FractalThemeId, scheme: ColorScheme, t: Float) {
    when (theme) {
        FractalThemeId.SOLID -> {
            drawRect(FractalPalette.Ink)
            // two chamfered slabs cut across the screen in a few hard steps
            val p = stepped(segment(t, 0f, 0.4f), 5)
            val band = size.height * 0.5f
            listOf(0f to FractalPalette.Surface, 0.35f to FractalPalette.SurfaceHigh).forEach { (lag, color) ->
                val x = -size.width + (size.width * 2.2f) * (p - lag).coerceIn(0f, 1f)
                val y = size.height * (0.15f + lag)
                val slab = Path().apply {
                    moveTo(x, y); lineTo(x + size.width * 0.8f, y)
                    lineTo(x + size.width * 0.8f - band * 0.5f, y + band); lineTo(x - band * 0.5f, y + band); close()
                }
                drawPath(slab, color, alpha = 0.6f)
            }
        }

        FractalThemeId.LIQUID_GLASS -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF0B0A12), Color(0xFF1A1430), Color(0xFF0E1A2A))))
            // big faint facets slowly turning behind the glass
            for (i in 0 until 5) {
                val r = Random(i * 31 + 5)
                val c = Offset(size.width * r.nextFloat(), size.height * r.nextFloat())
                val s = size.minDimension * (0.4f + r.nextFloat() * 0.5f)
                val a = (r.nextFloat() + t * 0.15f * (if (i % 2 == 0) 1 else -1)) * 2f * PI.toFloat()
                val path = Path().apply {
                    for (k in 0 until 3) {
                        val ang = a + k * 2f * PI.toFloat() / 3f
                        val pt = c + Offset(cos(ang), sin(ang)) * s
                        if (k == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                    }
                    close()
                }
                drawPath(path, Color.White, alpha = 0.035f)
                drawPath(path, Color.White, alpha = 0.10f, style = Stroke(width = 1.dp.toPx()))
            }
        }

        FractalThemeId.BLUR -> {
            drawRect(FractalPalette.Ink)
            drawRect(
                Brush.radialGradient(
                    listOf(FractalPalette.Magenta.copy(alpha = 0.22f * segment(t, 0f, 0.6f)), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.8f,
                )
            )
        }

        FractalThemeId.MATERIAL_YOU -> {
            drawRect(scheme.background)
            val grow = FastOutSlowInEasing.transform(segment(t, 0f, 0.7f))
            listOf(
                Triple(Offset(0.2f, 0.25f), scheme.primaryContainer, 0.55f),
                Triple(Offset(0.85f, 0.7f), scheme.secondaryContainer, 0.45f),
                Triple(Offset(0.6f, 0.1f), scheme.tertiaryContainer, 0.35f),
            ).forEach { (pos, color, r) ->
                drawCircle(color, radius = size.minDimension * r * grow, center = Offset(size.width * pos.x, size.height * pos.y), alpha = 0.45f)
            }
        }

        FractalThemeId.SYNTHWAVE -> {
            drawRect(Brush.verticalGradient(listOf(FractalPalette.Night, Color(0xFF1B0B3A), FractalPalette.Night)))
            val horizon = size.height * 0.76f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, FractalPalette.Magenta.copy(alpha = 0.35f), Color.Transparent),
                    startY = horizon - 40.dp.toPx(),
                    endY = horizon + 40.dp.toPx(),
                )
            )
            val grid = FractalPalette.Magenta
            val vanishing = Offset(size.width / 2f, horizon)
            for (i in -14..14) {
                val bottom = Offset(size.width / 2f + i * size.width / 7f, size.height)
                drawLine(grid, vanishing, bottom, strokeWidth = 3.dp.toPx(), alpha = 0.10f)
                drawLine(grid, vanishing, bottom, strokeWidth = 1.dp.toPx(), alpha = 0.45f)
            }
            val phase = (t * 2.5f) % 1f
            for (k in 0 until 12) {
                val z = (k + phase) / 12f
                val y = horizon + (size.height - horizon) * z * z
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx(), alpha = 0.12f * z)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), alpha = 0.6f * z)
            }
        }
    }
}

// endregion

@Composable
private fun Wordmark(theme: FractalThemeId, scheme: ColorScheme, t: Float) {
    val reveal = segment(t, 0.5f, 0.8f)
    val base = WordmarkStyle.copy(fontSize = 30.sp)
    when (theme) {
        FractalThemeId.SOLID -> Text(
            "FRACTAL",
            style = base,
            color = FractalPalette.Text,
            modifier = Modifier.drawWithContent {
                // chamfered wipe in hard steps
                val x = size.width * 1.3f * stepped(reveal, 6)
                val cut = Path().apply {
                    moveTo(0f, 0f); lineTo(x, 0f); lineTo(x - size.height * 0.6f, size.height); lineTo(0f, size.height); close()
                }
                clipPath(cut) { this@drawWithContent.drawContent() }
            },
        )

        FractalThemeId.LIQUID_GLASS -> Text(
            "FRACTAL",
            style = base.copy(letterSpacing = (0.9f - 0.55f * FastOutSlowInEasing.transform(reveal)).em),
            color = Color.White,
            modifier = Modifier.graphicsLayer { alpha = reveal },
        )

        FractalThemeId.BLUR -> Text(
            "FRACTAL",
            style = base,
            color = FractalPalette.Text,
            modifier = Modifier
                .graphicsLayer { alpha = reveal }
                .blur((18f * (1f - reveal)).dp, BlurredEdgeTreatment.Unbounded),
        )

        FractalThemeId.MATERIAL_YOU -> Text(
            "FRACTAL",
            style = base,
            color = scheme.onBackground,
            modifier = Modifier.graphicsLayer {
                alpha = reveal
                translationY = (1f - Overshoot.transform(reveal)) * 24.dp.toPx()
            },
        )

        FractalThemeId.SYNTHWAVE -> Text(
            "FRACTAL",
            style = base.merge(
                TextStyle(
                    brush = Brush.horizontalGradient(listOf(FractalPalette.Magenta, FractalPalette.Purple, FractalPalette.Cyan)),
                    shadow = Shadow(FractalPalette.Magenta, blurRadius = 28f * reveal),
                )
            ),
            modifier = Modifier.graphicsLayer { alpha = reveal },
        )
    }
}
