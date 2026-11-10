package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import dev.dfd.fractal.emulation.inputoverlay.fillRoundRectangleWithStroke
import dev.dfd.fractal.emulation.inputoverlay.inputs.innerdrawing.ButtonInnerDrawing

class RectangleButton(
    private val innerDrawing: ButtonInnerDrawing,
    alpha: Int,
    rect: Rect,
) : Button(rect, alpha) {
    private val bounds = RectF()

    override val keepsAspectRatio: Boolean = false

    init {
        configure()
    }

    override fun configure() {
        bounds.set(rect)
        configureColors(alpha)
        innerDrawing.configure(rect, alpha)
    }

    override fun drawInput(canvas: Canvas) {
        val fillColor: Int
        val strokeColor: Int
        if (state) {
            fillColor = activeFillColor
            strokeColor = activeStrokeColor
        } else {
            fillColor = inactiveFillColor
            strokeColor = inactiveStrokeColor
        }
        canvas.fillRoundRectangleWithStroke(bounds, RECTANGLE_RADIUS, paint, fillColor, strokeColor)
        innerDrawing.draw(canvas, state)
    }

    override fun isInside(x: Float, y: Float): Boolean =
        x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom

    companion object {
        const val RECTANGLE_RADIUS = 5f
    }
}
