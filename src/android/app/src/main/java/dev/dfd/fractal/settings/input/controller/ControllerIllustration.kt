package dev.dfd.fractal.settings.input.controller

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.nativeinterface.NativeInput.ClassicButton
import dev.dfd.fractal.nativeinterface.NativeInput.ProButton
import dev.dfd.fractal.nativeinterface.NativeInput.VPADButton
import dev.dfd.fractal.nativeinterface.NativeInput.WiimoteButton
import kotlin.math.abs
import kotlin.math.hypot

/** What the controller screen draws; GAMECUBE is a Classic Controller laid out as a GameCube pad. */
enum class ControllerStyle { GAMEPAD, PRO, CLASSIC, GAMECUBE, WIIMOTE }

/**
 * A part of the drawing, in units of the drawing width (the height is half the width).
 * [ids] are the native mapping ids it stands for; a tap on the part maps them in order.
 */
private sealed interface Part {
    val ids: List<Int>
    val label: String
    fun contains(x: Float, y: Float): Boolean
}

private data class RoundButton(
    val cx: Float, val cy: Float, val r: Float,
    override val label: String, override val ids: List<Int>,
) : Part {
    override fun contains(x: Float, y: Float) = hypot(x - cx, y - cy) <= r * 1.4f
}

private data class Stick(
    val cx: Float, val cy: Float, val r: Float,
    override val label: String, override val ids: List<Int>,
) : Part {
    override fun contains(x: Float, y: Float) = hypot(x - cx, y - cy) <= r * 1.2f
}

/** [ids] are up, down, left, right. */
private data class Dpad(
    val cx: Float, val cy: Float, val size: Float,
    override val ids: List<Int>,
) : Part {
    override val label = "D-pad"
    override fun contains(x: Float, y: Float) = abs(x - cx) <= size / 2 && abs(y - cy) <= size / 2
}

private data class Pill(
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    override val label: String, override val ids: List<Int>,
) : Part {
    override fun contains(x: Float, y: Float) = abs(x - cx) <= w / 2 + 0.01f && abs(y - cy) <= h / 2 + 0.01f
}

private data class BodyRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val radius: Float)
private data class BodyOval(val left: Float, val top: Float, val right: Float, val bottom: Float)

private class Drawing(val bodies: List<Any>, val parts: List<Part>, val screen: BodyRect? = null)

private fun gamePadDrawing() = Drawing(
    bodies = listOf(BodyRect(0.04f, 0.08f, 0.96f, 0.46f, 0.07f)),
    screen = BodyRect(0.29f, 0.12f, 0.71f, 0.40f, 0.01f),
    parts = listOf(
        Pill(0.14f, 0.035f, 0.13f, 0.03f, "ZL", listOf(VPADButton.ZL)),
        Pill(0.14f, 0.075f, 0.15f, 0.03f, "L", listOf(VPADButton.L)),
        Pill(0.86f, 0.035f, 0.13f, 0.03f, "ZR", listOf(VPADButton.ZR)),
        Pill(0.86f, 0.075f, 0.15f, 0.03f, "R", listOf(VPADButton.R)),
        Stick(0.16f, 0.17f, 0.045f, "L", listOf(VPADButton.STICKL_UP, VPADButton.STICKL_DOWN, VPADButton.STICKL_LEFT, VPADButton.STICKL_RIGHT, VPADButton.STICKL)),
        Dpad(0.16f, 0.31f, 0.09f, listOf(VPADButton.UP, VPADButton.DOWN, VPADButton.LEFT, VPADButton.RIGHT)),
        Stick(0.84f, 0.17f, 0.045f, "R", listOf(VPADButton.STICKR_UP, VPADButton.STICKR_DOWN, VPADButton.STICKR_LEFT, VPADButton.STICKR_RIGHT, VPADButton.STICKR)),
        RoundButton(0.84f, 0.265f, 0.019f, "X", listOf(VPADButton.X)),
        RoundButton(0.84f, 0.355f, 0.019f, "B", listOf(VPADButton.B)),
        RoundButton(0.795f, 0.31f, 0.019f, "Y", listOf(VPADButton.Y)),
        RoundButton(0.885f, 0.31f, 0.019f, "A", listOf(VPADButton.A)),
        RoundButton(0.80f, 0.42f, 0.013f, "−", listOf(VPADButton.MINUS)),
        RoundButton(0.88f, 0.42f, 0.013f, "+", listOf(VPADButton.PLUS)),
        RoundButton(0.16f, 0.42f, 0.013f, "⌂", listOf(VPADButton.HOME)),
        Pill(0.50f, 0.43f, 0.06f, 0.025f, "MIC", listOf(VPADButton.MIC)),
    ),
)

private fun proDrawing() = Drawing(
    bodies = listOf(
        BodyOval(0.07f, 0.12f, 0.37f, 0.49f),
        BodyOval(0.63f, 0.12f, 0.93f, 0.49f),
        BodyRect(0.13f, 0.08f, 0.87f, 0.34f, 0.08f),
    ),
    parts = listOf(
        Pill(0.20f, 0.035f, 0.13f, 0.03f, "ZL", listOf(ProButton.ZL)),
        Pill(0.20f, 0.075f, 0.15f, 0.03f, "L", listOf(ProButton.L)),
        Pill(0.80f, 0.035f, 0.13f, 0.03f, "ZR", listOf(ProButton.ZR)),
        Pill(0.80f, 0.075f, 0.15f, 0.03f, "R", listOf(ProButton.R)),
        Stick(0.24f, 0.18f, 0.05f, "L", listOf(ProButton.STICKL_UP, ProButton.STICKL_DOWN, ProButton.STICKL_LEFT, ProButton.STICKL_RIGHT, ProButton.STICKL)),
        Stick(0.76f, 0.18f, 0.05f, "R", listOf(ProButton.STICKR_UP, ProButton.STICKR_DOWN, ProButton.STICKR_LEFT, ProButton.STICKR_RIGHT, ProButton.STICKR)),
        Dpad(0.35f, 0.31f, 0.09f, listOf(ProButton.UP, ProButton.DOWN, ProButton.LEFT, ProButton.RIGHT)),
        RoundButton(0.65f, 0.265f, 0.019f, "X", listOf(ProButton.X)),
        RoundButton(0.65f, 0.355f, 0.019f, "B", listOf(ProButton.B)),
        RoundButton(0.605f, 0.31f, 0.019f, "Y", listOf(ProButton.Y)),
        RoundButton(0.695f, 0.31f, 0.019f, "A", listOf(ProButton.A)),
        RoundButton(0.44f, 0.18f, 0.013f, "−", listOf(ProButton.MINUS)),
        RoundButton(0.50f, 0.22f, 0.015f, "⌂", listOf(ProButton.HOME)),
        RoundButton(0.56f, 0.18f, 0.013f, "+", listOf(ProButton.PLUS)),
    ),
)

private fun classicDrawing() = Drawing(
    bodies = listOf(
        BodyOval(0.10f, 0.16f, 0.38f, 0.48f),
        BodyOval(0.62f, 0.16f, 0.90f, 0.48f),
        BodyRect(0.10f, 0.08f, 0.90f, 0.32f, 0.07f),
    ),
    parts = listOf(
        Pill(0.20f, 0.035f, 0.13f, 0.03f, "ZL", listOf(ClassicButton.ZL)),
        Pill(0.20f, 0.075f, 0.15f, 0.03f, "L", listOf(ClassicButton.L)),
        Pill(0.80f, 0.035f, 0.13f, 0.03f, "ZR", listOf(ClassicButton.ZR)),
        Pill(0.80f, 0.075f, 0.15f, 0.03f, "R", listOf(ClassicButton.R)),
        Dpad(0.22f, 0.19f, 0.09f, listOf(ClassicButton.UP, ClassicButton.DOWN, ClassicButton.LEFT, ClassicButton.RIGHT)),
        RoundButton(0.78f, 0.145f, 0.019f, "X", listOf(ClassicButton.X)),
        RoundButton(0.78f, 0.235f, 0.019f, "B", listOf(ClassicButton.B)),
        RoundButton(0.735f, 0.19f, 0.019f, "Y", listOf(ClassicButton.Y)),
        RoundButton(0.825f, 0.19f, 0.019f, "A", listOf(ClassicButton.A)),
        RoundButton(0.43f, 0.19f, 0.013f, "−", listOf(ClassicButton.MINUS)),
        RoundButton(0.50f, 0.19f, 0.015f, "⌂", listOf(ClassicButton.HOME)),
        RoundButton(0.57f, 0.19f, 0.013f, "+", listOf(ClassicButton.PLUS)),
        Stick(0.37f, 0.33f, 0.045f, "L", listOf(ClassicButton.STICKL_UP, ClassicButton.STICKL_DOWN, ClassicButton.STICKL_LEFT, ClassicButton.STICKL_RIGHT)),
        Stick(0.63f, 0.33f, 0.045f, "R", listOf(ClassicButton.STICKR_UP, ClassicButton.STICKR_DOWN, ClassicButton.STICKR_LEFT, ClassicButton.STICKR_RIGHT)),
    ),
)

/** A GameCube pad drawn over the Classic Controller buttons the GameCube layout uses. */
private fun gameCubeDrawing() = Drawing(
    bodies = listOf(
        BodyOval(0.11f, 0.14f, 0.39f, 0.49f),
        BodyOval(0.61f, 0.14f, 0.89f, 0.49f),
        BodyRect(0.15f, 0.09f, 0.85f, 0.33f, 0.09f),
    ),
    parts = listOf(
        Pill(0.21f, 0.06f, 0.16f, 0.035f, "L", listOf(ClassicButton.L)),
        Pill(0.79f, 0.06f, 0.16f, 0.035f, "R", listOf(ClassicButton.R)),
        Pill(0.78f, 0.105f, 0.09f, 0.022f, "Z", listOf(ClassicButton.ZR)),
        Stick(0.27f, 0.20f, 0.05f, "", listOf(ClassicButton.STICKL_UP, ClassicButton.STICKL_DOWN, ClassicButton.STICKL_LEFT, ClassicButton.STICKL_RIGHT)),
        Dpad(0.37f, 0.335f, 0.07f, listOf(ClassicButton.UP, ClassicButton.DOWN, ClassicButton.LEFT, ClassicButton.RIGHT)),
        Stick(0.63f, 0.335f, 0.038f, "C", listOf(ClassicButton.STICKR_UP, ClassicButton.STICKR_DOWN, ClassicButton.STICKR_LEFT, ClassicButton.STICKR_RIGHT)),
        RoundButton(0.74f, 0.21f, 0.038f, "A", listOf(ClassicButton.A)),
        RoundButton(0.675f, 0.265f, 0.02f, "B", listOf(ClassicButton.B)),
        RoundButton(0.815f, 0.175f, 0.022f, "X", listOf(ClassicButton.X)),
        RoundButton(0.72f, 0.125f, 0.022f, "Y", listOf(ClassicButton.Y)),
        RoundButton(0.50f, 0.21f, 0.016f, "▶", listOf(ClassicButton.PLUS)),
    ),
)

private fun wiimoteDrawing(nunchuk: Boolean, motionPlus: Boolean) = Drawing(
    bodies = buildList {
        add(BodyRect(0.29f, 0.02f, 0.41f, if (motionPlus) 0.44f else 0.48f, 0.035f))
        if (motionPlus) add(BodyRect(0.30f, 0.445f, 0.40f, 0.49f, 0.01f))
        if (nunchuk) add(BodyOval(0.56f, 0.10f, 0.72f, 0.47f))
    },
    parts = buildList {
        add(Dpad(0.35f, 0.09f, 0.07f, listOf(WiimoteButton.UP, WiimoteButton.DOWN, WiimoteButton.LEFT, WiimoteButton.RIGHT)))
        add(RoundButton(0.35f, 0.175f, 0.024f, "A", listOf(WiimoteButton.A)))
        add(Pill(0.23f, 0.175f, 0.05f, 0.04f, "B", listOf(WiimoteButton.B)))
        add(RoundButton(0.315f, 0.265f, 0.011f, "−", listOf(WiimoteButton.MINUS)))
        add(RoundButton(0.35f, 0.265f, 0.012f, "⌂", listOf(WiimoteButton.HOME)))
        add(RoundButton(0.385f, 0.265f, 0.011f, "+", listOf(WiimoteButton.PLUS)))
        add(RoundButton(0.35f, 0.35f, 0.016f, "1", listOf(WiimoteButton.ONE)))
        add(RoundButton(0.35f, 0.405f, 0.016f, "2", listOf(WiimoteButton.TWO)))
        if (nunchuk) {
            add(Stick(0.62f, 0.19f, 0.04f, "", listOf(WiimoteButton.NUNCHUCK_UP, WiimoteButton.NUNCHUCK_DOWN, WiimoteButton.NUNCHUCK_LEFT, WiimoteButton.NUNCHUCK_RIGHT)))
            add(RoundButton(0.775f, 0.13f, 0.018f, "C", listOf(WiimoteButton.NUNCHUCK_C)))
            add(Pill(0.775f, 0.20f, 0.05f, 0.04f, "Z", listOf(WiimoteButton.NUNCHUCK_Z)))
        }
    },
)

private fun drawingFor(style: ControllerStyle, nunchuk: Boolean, motionPlus: Boolean) = when (style) {
    ControllerStyle.GAMEPAD -> gamePadDrawing()
    ControllerStyle.PRO -> proDrawing()
    ControllerStyle.CLASSIC -> classicDrawing()
    ControllerStyle.GAMECUBE -> gameCubeDrawing()
    ControllerStyle.WIIMOTE -> wiimoteDrawing(nunchuk, motionPlus)
}

/**
 * Drawing of the emulated controller: mapped inputs in the accent color, unmapped ones faded,
 * [highlightedId] (the input being bound) filled. A tap on a part calls [onPartClick] with its ids.
 */
@Composable
fun ControllerIllustration(
    style: ControllerStyle,
    mappedIds: Set<Int>,
    highlightedId: Int?,
    modifier: Modifier = Modifier,
    nunchuk: Boolean = false,
    motionPlus: Boolean = false,
    onPartClick: ((ids: List<Int>, label: String) -> Unit)? = null,
) {
    val colors = FractalTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val drawing = remember(style, nunchuk, motionPlus) { drawingFor(style, nunchuk, motionPlus) }
    val palette = Palette(
        body = colors.surfaceHigh,
        bodyBorder = colors.border,
        mapped = colors.accent,
        unmapped = colors.textSecondary.copy(alpha = 0.45f),
        highlight = colors.accent,
        onHighlight = colors.onAccent,
        text = colors.text,
        screen = colors.background,
    )
    val tapModifier = if (onPartClick != null) {
        Modifier.pointerInput(drawing) {
            detectTapGestures { offset ->
                val unit = size.width.toFloat()
                val x = offset.x / unit
                val y = offset.y / unit
                drawing.parts.lastOrNull { it.contains(x, y) }?.let { onPartClick(it.ids, it.label) }
            }
        }
    } else Modifier

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .then(tapModifier)
    ) {
        val unit = size.width
        drawing.bodies.forEach { drawBody(it, unit, palette) }
        drawing.screen?.let { drawBody(it, unit, palette, fill = palette.screen) }
        drawing.parts.forEach { part ->
            val state = when {
                highlightedId != null && highlightedId in part.ids -> PartState.HIGHLIGHT
                part.ids.any { it in mappedIds } -> PartState.MAPPED
                else -> PartState.UNMAPPED
            }
            drawPart(part, unit, palette, state, textMeasurer)
        }
    }
}

private enum class PartState { MAPPED, UNMAPPED, HIGHLIGHT }

private data class Palette(
    val body: Color,
    val bodyBorder: Color,
    val mapped: Color,
    val unmapped: Color,
    val highlight: Color,
    val onHighlight: Color,
    val text: Color,
    val screen: Color,
)

private fun DrawScope.drawBody(body: Any, unit: Float, palette: Palette, fill: Color = palette.body) {
    when (body) {
        is BodyRect -> {
            val topLeft = Offset(body.left * unit, body.top * unit)
            val size = Size((body.right - body.left) * unit, (body.bottom - body.top) * unit)
            val corner = CornerRadius(body.radius * unit)
            drawRoundRect(fill, topLeft, size, corner)
            drawRoundRect(palette.bodyBorder, topLeft, size, corner, style = Stroke(unit * 0.003f))
        }

        is BodyOval -> {
            val topLeft = Offset(body.left * unit, body.top * unit)
            val size = Size((body.right - body.left) * unit, (body.bottom - body.top) * unit)
            drawOval(fill, topLeft, size)
            drawOval(palette.bodyBorder, topLeft, size, style = Stroke(unit * 0.003f))
        }
    }
}

private fun DrawScope.drawPart(part: Part, unit: Float, palette: Palette, state: PartState, textMeasurer: TextMeasurer) {
    val stroke = Stroke(unit * 0.004f)
    val lineColor = if (state == PartState.UNMAPPED) palette.unmapped else palette.mapped
    val fill = if (state == PartState.HIGHLIGHT) palette.highlight else palette.body
    val textColor = when (state) {
        PartState.HIGHLIGHT -> palette.onHighlight
        PartState.MAPPED -> palette.text
        PartState.UNMAPPED -> palette.unmapped
    }
    when (part) {
        is RoundButton -> {
            val center = Offset(part.cx * unit, part.cy * unit)
            drawCircle(fill, part.r * unit, center)
            drawCircle(lineColor, part.r * unit, center, style = stroke)
            drawLabel(part.label, center, part.r * unit * 1.1f, textColor, textMeasurer)
        }

        is Stick -> {
            val center = Offset(part.cx * unit, part.cy * unit)
            drawCircle(fill, part.r * unit, center)
            drawCircle(lineColor, part.r * unit, center, style = stroke)
            drawCircle(lineColor, part.r * unit * 0.55f, center, style = stroke)
            if (part.label.isNotEmpty()) drawLabel(part.label, center, part.r * unit * 0.6f, textColor, textMeasurer)
        }

        is Dpad -> {
            val half = part.size * unit / 2
            val arm = half / 3
            val center = Offset(part.cx * unit, part.cy * unit)
            val vertical = Size(arm * 2, half * 2)
            val horizontal = Size(half * 2, arm * 2)
            val corner = CornerRadius(arm * 0.4f)
            drawRoundRect(fill, Offset(center.x - arm, center.y - half), vertical, corner)
            drawRoundRect(fill, Offset(center.x - half, center.y - arm), horizontal, corner)
            drawRoundRect(lineColor, Offset(center.x - arm, center.y - half), vertical, corner, style = stroke)
            drawRoundRect(lineColor, Offset(center.x - half, center.y - arm), horizontal, corner, style = stroke)
        }

        is Pill -> {
            val topLeft = Offset((part.cx - part.w / 2) * unit, (part.cy - part.h / 2) * unit)
            val size = Size(part.w * unit, part.h * unit)
            val corner = CornerRadius(part.h * unit / 2)
            drawRoundRect(fill, topLeft, size, corner)
            drawRoundRect(lineColor, topLeft, size, corner, style = stroke)
            drawLabel(part.label, Offset(part.cx * unit, part.cy * unit), part.h * unit * 0.75f, textColor, textMeasurer)
        }
    }
}

private fun DrawScope.drawLabel(text: String, center: Offset, heightPx: Float, color: Color, textMeasurer: TextMeasurer) {
    if (text.isEmpty() || heightPx < 4f) return
    val layout = textMeasurer.measure(
        text,
        TextStyle(color = color, fontSize = (heightPx / density / fontScale).sp, fontWeight = FontWeight.Bold),
    )
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}
