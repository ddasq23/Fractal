package dev.dfd.fractal.emulation.inputoverlay.inputs

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import androidx.annotation.ColorInt
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.emulation.inputoverlay.Colors

abstract class Input protected constructor(
    rect: Rect,
    alpha: Int,
) {
    /** Bounds on the overlay, in pixels. Change it with [setBounds]. */
    val rect: Rect = Rect(rect)

    protected var alpha: Int = alpha
        private set

    /** The built in input this is, or null for a macro. */
    var config: OverlayInputConfig? = null

    /** Id of the macro this input runs, or null for a built in input. */
    var macroId: String? = null

    /** Id of the shortcut this input is, or null. */
    var shortcutId: String? = null

    /** Resolution independent layout [rect] comes from, before the global scale. */
    var layout: NormalizedRect = NormalizedRect(0.5f, 0.5f, 0.1f, 0.1f)

    /** Size multiplier of this input on top of [layout] and the global scale. */
    var styleScale: Float = 1f

    /** Opacity set for this input alone, or null to follow the overlay opacity. */
    var alphaOverride: Int? = null

    /** Picks up a new [Colors.palette]. */
    fun refreshColors() = configure()

    private var boundingRectangleColor = 0
    private var drawBoundingRectangle = false
    private val boundingRectanglePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    protected var activeFillColor = 0
    protected var activeStrokeColor = 0
    protected var inactiveFillColor = 0
    protected var inactiveStrokeColor = 0
    protected val paint = Paint().apply {
        strokeWidth = 3f
        isAntiAlias = true
    }

    /** Round inputs keep their aspect ratio when resized. */
    open val keepsAspectRatio: Boolean = true

    /**
     * Touch handling for inputs that follow their own pointer (sticks, d-pad).
     * Buttons are driven by the overlay through [Button.pointerDown]/[Button.pointerUp].
     */
    open fun onTouch(event: MotionEvent): Boolean = false

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        rect.set(left, top, right, bottom)
        configure()
    }

    fun setAlpha(alpha: Int) {
        if (this.alpha == alpha) return
        this.alpha = alpha
        configure()
    }

    /** Releases everything the input holds. */
    abstract fun resetInput()

    protected abstract fun configure()

    protected fun configureColors(alpha: Int) {
        activeFillColor = Colors.activeFill(alpha)
        activeStrokeColor = Colors.activeStroke(alpha)
        inactiveFillColor = Colors.inactiveFill(alpha)
        inactiveStrokeColor = Colors.inactiveStroke(alpha)
    }

    protected abstract fun drawInput(canvas: Canvas)

    /** Drawn over the input, e.g. behavior indicators. */
    protected open fun drawDecorations(canvas: Canvas) {}

    fun draw(canvas: Canvas) {
        drawInput(canvas)
        drawDecorations(canvas)
        if (drawBoundingRectangle) {
            boundingRectanglePaint.color = boundingRectangleColor
            canvas.drawRect(rect, boundingRectanglePaint)
        }
    }

    fun enableDrawingBoundingRect(@ColorInt color: Int) {
        drawBoundingRectangle = true
        boundingRectangleColor = color
    }

    fun disableDrawingBoundingRect() {
        drawBoundingRectangle = false
    }

    abstract fun isInside(x: Float, y: Float): Boolean
}
