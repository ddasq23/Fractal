// SPDX-License-Identifier: GPL-3.0-only
//
// Based on WinlatorHUD from Winlator-Ludashi by StevenMXZ (https://github.com/StevenMXZ/Winlator-Ludashi),
// licensed under the GPL-3.0. Ported to Kotlin and adapted to Fractal: the frame rate comes from the emulator's
// performance monitor, the look follows the app theme ([HudStyle]), the place is kept by the screen around it and the
// size scales the text instead of the view. Winlator's renderer, container and frame generation parts are left out.

package dev.dfd.fractal.emulation.hud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.res.ResourcesCompat
import dev.dfd.fractal.R
import dev.dfd.fractal.common.settings.HudItem
import dev.dfd.fractal.nativeinterface.NativeEmulation
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The performance HUD: CPU and GPU load, temperatures, RAM, power, battery and the game's frame rate, drawn on a
 * canvas over the game.
 *
 * Cheap on purpose, since it shares the device with the emulator: the statistics are read on one background thread
 * once a second, and only the shown ones; the view is invalidated only when a shown value changed and laid out again
 * only when its size changes; [onDraw] allocates nothing. The thread runs only while the HUD is attached, its window
 * is visible and [setRunning] allows it (the host stops it while the activity is paused).
 *
 * The view doesn't move itself: dragging reports the finger's travel through [dragListener] and the host moves it.
 */
class PerformanceHudView(context: Context) : View(context) {
    interface DragListener {
        fun onDragStart()

        /** Travel of the finger since it went down, in pixels. */
        fun onDrag(dx: Float, dy: Float)

        fun onDragEnd()
    }

    var dragListener: DragListener? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // settings
    private var items = 0
    private var scale = 1f
    private var opacity = 1f
    private var vertical = false
    private var style: HudStyle? = null
    private var bare = false

    // sizes in pixels, from the scale
    private var padH = 0f
    private var padV = 0f
    private var edgePad = 0f
    private var startInset = 0f
    private var labelGap = 0f
    private var innerSpace = 0f
    private var separatorGap = 0f
    private var lineGap = 0f
    private var rowHeight = 0f
    private var baselineOffset = 0f
    private var cornerRadius = 0f
    private var borderWidth = 0f
    private var barWidth = 0f
    private var contentWidth = 0
    private var contentHeight = 0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val valuePaint = Paint(TEXT_FLAGS)
    private val chargePaint = Paint(TEXT_FLAGS)
    private val labelPaints = Array(HudGroup.entries.size) { Paint(TEXT_FLAGS) }
    private val textPaints: Array<Paint> = arrayOf(valuePaint, chargePaint, *labelPaints)
    private val fontMetrics = Paint.FontMetrics()

    private val fillRect = RectF()
    private val borderRect = RectF()
    private val barRect = RectF()
    private val fillPath = Path()
    private val borderPath = Path()
    private val barPath = Path()

    private class Slot {
        var shown = false
        var text = ""
        var width = 0f

        /** Room kept for the widest usual value, so the HUD doesn't change size with every reading. */
        var reserve = 0f
        lateinit var paint: Paint

        val present get() = shown && (reserve > 0f || text.isNotEmpty())
        val advance get() = max(reserve, width)
    }

    private class Group(val label: String) {
        var shown = false
        var labelWidth = 0f
        var width = 0f
        val slots = Array(3) { Slot() }
    }

    private val groups = Array(HudGroup.entries.size) { Group(HudGroup.entries[it].label) }

    /** The latest readings by [HudItem] ordinal, on the UI thread. */
    private val values = IntArray(HudItem.entries.size) { NOT_READ }
    private var gpuName = ""

    // handed from the stats thread to the UI thread
    private val lock = Any()
    private val pending = IntArray(HudItem.entries.size) { NOT_READ }
    private var pendingGpuName = ""
    private val incoming = IntArray(HudItem.entries.size)

    // stats thread only
    private val samplerLock = Any()
    private val sampled = IntArray(HudItem.entries.size) { NOT_READ }
    private val posted = IntArray(HudItem.entries.size) { NOT_READ }
    private var postedGpuName = ""
    private var sensors: HudSensors? = null

    @Volatile
    private var sampleMask = 0
    private var running = false
    private var statsThread: HandlerThread? = null
    private var statsLoop: StatsLoop? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val applyRunnable = Runnable { applyPending() }

    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        val valueFont = loadTypeface(R.font.inter)
        valuePaint.setTypeface(valueFont)
        valuePaint.fontVariationSettings = "'wght' 600"
        // tabular figures: digits all as wide, so values don't wobble
        valuePaint.fontFeatureSettings = "tnum"
        chargePaint.setTypeface(valueFont)
        chargePaint.fontVariationSettings = "'wght' 700"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        configureSlots()
    }

    private fun loadTypeface(font: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, font) }.getOrNull() ?: Typeface.DEFAULT_BOLD

    private fun configureSlots() {
        for (group in groups) for (slot in group.slots) slot.paint = valuePaint
        slotOf(HudItem.GPU_NAME).paint = labelPaints[HudGroup.GPU_NAME.ordinal]
        slotOf(HudItem.CHARGING).paint = chargePaint
    }

    // ---- settings ----

    /** Applies the HUD settings; cheap when nothing changed. */
    fun applySettings(items: Int, sizePercent: Int, opacityPercent: Int, vertical: Boolean) {
        val newScale = sizePercent / 100f
        val newOpacity = opacityPercent / 100f
        var relayout = false
        if (items != this.items) {
            this.items = items
            sampleMask = items
            relayout = true
            // values for items that just appeared: read them now rather than in a second
            statsLoop?.sampleNow()
        }
        if (newScale != scale) {
            scale = newScale
            relayout = true
        }
        if (vertical != this.vertical) {
            this.vertical = vertical
            relayout = true
        }
        if (newOpacity != opacity) {
            opacity = newOpacity
            applyColors()
            invalidate()
        }
        if (relayout) {
            updateMetrics()
            updateLayout()
            updateBackground()
        }
        updateThread()
    }

    fun applyStyle(style: HudStyle) {
        if (style == this.style) return
        this.style = style
        val labelFont = loadTypeface(if (style.labelFont == HudFont.GROTESK) R.font.space_grotesk else R.font.inter)
        for (paint in labelPaints) {
            paint.setTypeface(labelFont)
            paint.fontVariationSettings = "'wght' 700"
        }
        applyColors()
        updateMetrics()
        updateLayout()
        updateBackground()
        invalidate()
    }

    /** Lets the statistics thread run (when the HUD is also attached and visible). */
    fun setRunning(running: Boolean) {
        if (running == this.running) return
        this.running = running
        updateThread()
    }

    /** Colors for the style and opacity; a faded background switches the text to colors that read over the game. */
    private fun applyColors() {
        val style = style ?: return
        bare = opacity < BARE_OPACITY
        valuePaint.color = if (bare) style.valueBare else style.value
        chargePaint.color = if (bare) style.chargeBare else style.charge
        val labels = if (bare) style.labelsBare else style.labels
        for (i in labelPaints.indices) labelPaints[i].color = labels[i]
        val shadowRadius = if (bare) 1.6f * density else 0f
        for (paint in textPaints) {
            if (bare) paint.setShadowLayer(shadowRadius, 0f, 0.8f * density, SHADOW_COLOR) else paint.clearShadowLayer()
        }
        separatorPaint.color = style.separator
        // the separators stay visible without a background, like the text
        separatorPaint.alpha = (HudStyle.alpha(style.separator) * max(opacity, if (bare) 0.6f else 0f)).roundToInt()
        highlightPaint.color = style.highlight
        highlightPaint.alpha = (HudStyle.alpha(style.highlight) * opacity).roundToInt()
        barPaint.color = style.accentBar
        barPaint.alpha = (HudStyle.alpha(style.accentBar) * opacity).roundToInt()
        if (style.fill.size == 1) {
            fillPaint.shader = null
            fillPaint.color = style.fill[0]
            fillPaint.alpha = (HudStyle.alpha(style.fill[0]) * opacity).roundToInt()
        } else {
            // the gradient carries its own alpha; the paint's alpha fades it
            fillPaint.color = OPAQUE_BLACK
            fillPaint.alpha = (255 * opacity).roundToInt()
        }
        if (style.border.size == 1) {
            borderPaint.shader = null
            borderPaint.color = style.border[0]
            borderPaint.alpha = (HudStyle.alpha(style.border[0]) * opacity).roundToInt()
        } else if (style.border.size > 1) {
            borderPaint.color = OPAQUE_BLACK
            borderPaint.alpha = (255 * opacity).roundToInt()
        }
    }

    private fun updateMetrics() {
        val style = style
        val textSize = BASE_TEXT_DP * density * scale
        valuePaint.textSize = textSize
        chargePaint.textSize = textSize * 0.92f
        for (paint in labelPaints) paint.textSize = textSize * 0.92f
        valuePaint.getFontMetrics(fontMetrics)
        rowHeight = fontMetrics.descent - fontMetrics.ascent
        baselineOffset = -fontMetrics.ascent
        padV = 4f * density * scale
        padH = 8f * density * scale
        labelGap = 4f * density * scale
        innerSpace = 5f * density * scale
        separatorGap = 13f * density * scale
        lineGap = 2f * density * scale
        borderWidth = if (style == null || style.border.isEmpty()) 0f else max(1f, style.borderWidthDp * density * scale)
        barWidth = if (style == null || style.accentBar == 0) 0f else 3f * density * scale
        separatorPaint.strokeWidth = max(1f, 1f * density * scale)
        borderPaint.strokeWidth = borderWidth
        highlightPaint.strokeWidth = max(1f, density * scale * 0.8f)

        for (group in groups) {
            group.labelWidth = if (group.label.isEmpty()) 0f else labelPaints[0].measureText(group.label)
        }
        for (item in HudItem.entries) {
            val slot = slotOf(item)
            slot.reserve = RESERVE[item.ordinal]?.let { slot.paint.measureText(it) } ?: 0f
            slot.width = if (slot.text.isEmpty()) 0f else slot.paint.measureText(slot.text)
        }
    }

    private fun slotOf(item: HudItem): Slot = groups[GROUP_OF[item.ordinal].ordinal].slots[SLOT_OF[item.ordinal]]

    /** Which groups and slots show, their widths and the HUD's size; asks for a new layout when the size changed. */
    private fun updateLayout() {
        for (group in groups) {
            group.shown = false
            for (slot in group.slots) slot.shown = false
        }
        for (item in HudItem.entries) {
            if (items and item.bit == 0) continue
            if (item == HudItem.GPU_NAME && gpuName.isEmpty()) continue
            slotOf(item).shown = true
            groups[GROUP_OF[item.ordinal].ordinal].shown = true
        }
        var shownGroups = 0
        var rowWidth = 0f
        var widest = 0f
        for (group in groups) {
            if (!group.shown) continue
            var width = if (group.label.isEmpty()) 0f else group.labelWidth + labelGap
            var first = true
            for (slot in group.slots) {
                if (!slot.present) continue
                if (!first) width += innerSpace
                width += slot.advance
                first = false
            }
            group.width = width
            rowWidth += width
            widest = max(widest, width)
            shownGroups++
        }
        val style = style
        startInset = barWidth
        if (shownGroups == 0) {
            contentWidth = 0
            contentHeight = 0
        } else if (vertical) {
            edgePad = padH
            contentHeight = ceil(padV * 2 + shownGroups * rowHeight + (shownGroups - 1) * lineGap).toInt()
            contentWidth = ceil(startInset + edgePad * 2 + widest).toInt()
        } else {
            val height = padV * 2 + rowHeight
            // the round ends of a pill need more room than square corners
            edgePad = if (style?.shape == HudShape.PILL) max(padH, height * 0.42f) else padH
            contentHeight = ceil(height).toInt()
            contentWidth = ceil(startInset + edgePad * 2 + rowWidth + (shownGroups - 1) * separatorGap).toInt()
        }
        if (contentWidth != measuredWidth || contentHeight != measuredHeight) requestLayout()
        invalidate()
    }

    // ---- statistics ----

    private inner class StatsLoop(val handler: Handler) : Runnable {
        @Volatile
        var alive = true

        override fun run() {
            if (!alive) return
            sampleOnce()
            if (alive) handler.postDelayed(this, STATS_INTERVAL_MS)
        }

        fun sampleNow() {
            handler.removeCallbacks(this)
            handler.post(this)
        }
    }

    private fun updateThread() {
        val wanted = running && items != 0 && isAttachedToWindow && windowVisibility == VISIBLE
        if (wanted) startThread() else stopThread()
    }

    private fun startThread() {
        if (statsThread != null) return
        val thread = HandlerThread("PerformanceHud", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
        val loop = StatsLoop(Handler(thread.looper))
        statsThread = thread
        statsLoop = loop
        loop.handler.post(loop)
    }

    private fun stopThread() {
        statsLoop?.let {
            it.alive = false
            it.handler.removeCallbacks(it)
            // the GPU timestamps cost a little: off while nobody looks (runs before the quit below)
            sensors?.let { s -> it.handler.post { s.setEmulatorGpuLoad(false) } }
        }
        statsThread?.quitSafely()
        statsThread = null
        statsLoop = null
        mainHandler.removeCallbacks(applyRunnable)
    }

    /** On the stats thread: reads the shown statistics and hands them to the UI thread when one changed. */
    private fun sampleOnce() {
        synchronized(samplerLock) { sampleLocked() }
    }

    private fun sampleLocked() {
        val mask = sampleMask
        val sensors = sensors ?: HudSensors(context).also { sensors = it }
        if (mask and HudItem.FPS.bit != 0) {
            val fps = runCatching { NativeEmulation.getFps() }.getOrDefault(-1.0)
            sampled[HudItem.FPS.ordinal] = if (fps >= 0) fps.roundToInt() else -1
        }
        runCatching { sensors.sample(mask, sampled) }
        val name = if (mask and HudItem.GPU_NAME.bit != 0) sensors.gpuName else postedGpuName
        if (sampled.contentEquals(posted) && name == postedGpuName) return
        sampled.copyInto(posted)
        postedGpuName = name
        synchronized(lock) {
            sampled.copyInto(pending)
            pendingGpuName = name
        }
        mainHandler.removeCallbacks(applyRunnable)
        mainHandler.post(applyRunnable)
    }

    /** On the UI thread: new readings to text, measured only when they changed. */
    private fun applyPending() {
        val name: String
        synchronized(lock) {
            pending.copyInto(incoming)
            name = pendingGpuName
        }
        var changed = false
        for (item in HudItem.entries) {
            val value = incoming[item.ordinal]
            if (value == values[item.ordinal]) continue
            values[item.ordinal] = value
            val slot = slotOf(item)
            slot.text = format(item, value)
            slot.width = if (slot.text.isEmpty()) 0f else slot.paint.measureText(slot.text)
            changed = true
        }
        if (name != gpuName) {
            gpuName = name
            val slot = slotOf(HudItem.GPU_NAME)
            slot.text = name
            slot.width = if (name.isEmpty()) 0f else slot.paint.measureText(name)
            changed = true
        }
        if (changed) updateLayout()
    }

    private fun format(item: HudItem, value: Int): String = when {
        item == HudItem.CHARGING -> if (value == 1) "CHG" else ""
        value == NOT_READ -> "--"
        value < 0 -> "N/A"
        else -> when (item) {
            HudItem.FPS -> value.toString()
            HudItem.CPU_TEMP, HudItem.BATTERY_TEMP -> "$value°C"
            HudItem.POWER -> String.format(Locale.US, "%.1fW", value / 1000f)
            else -> "$value%"
        }
    }

    // ---- view ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(contentWidth, widthMeasureSpec),
            resolveSize(contentHeight, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBackground()
    }

    /** Shapes and gradients of the background, for the current size. */
    private fun updateBackground() {
        val style = style ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        cornerRadius = when (style.shape) {
            HudShape.PILL -> if (vertical) min(style.cornerDp * density * scale, h / 2) else h / 2
            else -> min(style.cornerDp * density * scale, min(w, h) / 2)
        }
        fillRect.set(0f, 0f, w, h)
        val inset = borderWidth / 2
        borderRect.set(inset, inset, w - inset, h - inset)
        barRect.set(0f, 0f, barWidth, h)
        if (style.shape == HudShape.CHAMFERED) {
            chamfer(fillPath, fillRect, cornerRadius)
            chamfer(borderPath, borderRect, max(0f, cornerRadius - inset / 2))
            // the bar follows the cut corner
            barPath.reset()
            barPath.moveTo(0f, cornerRadius)
            barPath.lineTo(barWidth, max(0f, cornerRadius - barWidth))
            barPath.lineTo(barWidth, h)
            barPath.lineTo(0f, h)
            barPath.close()
        }
        fillPaint.shader = if (style.fill.size > 1) {
            LinearGradient(0f, 0f, 0f, h, style.fill.toIntArray(), null, Shader.TileMode.CLAMP)
        } else null
        borderPaint.shader = if (style.border.size > 1) {
            LinearGradient(0f, 0f, w, 0f, style.border.toIntArray(), null, Shader.TileMode.CLAMP)
        } else null
    }

    /** Rectangle with the top start and bottom end corners cut by [cut]. */
    private fun chamfer(path: Path, r: RectF, cut: Float) {
        path.reset()
        path.moveTo(r.left + cut, r.top)
        path.lineTo(r.right, r.top)
        path.lineTo(r.right, r.bottom - cut)
        path.lineTo(r.right - cut, r.bottom)
        path.lineTo(r.left, r.bottom)
        path.lineTo(r.left, r.top + cut)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        val style = style ?: return
        if (contentWidth == 0 || width == 0) return
        drawBackground(canvas, style)
        if (vertical) drawColumn(canvas) else drawRow(canvas)
    }

    private fun drawBackground(canvas: Canvas, style: HudStyle) {
        if (opacity <= 0f) return
        val chamfered = style.shape == HudShape.CHAMFERED
        if (chamfered) canvas.drawPath(fillPath, fillPaint)
        else canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint)
        if (barWidth > 0f) {
            if (chamfered) canvas.drawPath(barPath, barPaint) else canvas.drawRect(barRect, barPaint)
        }
        if (style.highlight != 0) {
            // along the straight part of the top edge only
            val y = borderWidth + highlightPaint.strokeWidth / 2
            canvas.drawLine(cornerRadius, y, width - cornerRadius, y, highlightPaint)
        }
        if (borderWidth > 0f) {
            if (chamfered) canvas.drawPath(borderPath, borderPaint)
            else canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    private fun drawRow(canvas: Canvas) {
        var x = startInset + edgePad
        val baseline = padV + baselineOffset
        val separatorTop = padV + rowHeight * 0.2f
        val separatorBottom = padV + rowHeight * 0.8f
        var first = true
        for (i in groups.indices) {
            val group = groups[i]
            if (!group.shown) continue
            if (!first) {
                val cx = x + separatorGap / 2
                canvas.drawLine(cx, separatorTop, cx, separatorBottom, separatorPaint)
                x += separatorGap
            }
            first = false
            drawGroup(canvas, i, x, baseline)
            x += group.width
        }
    }

    private fun drawColumn(canvas: Canvas) {
        val x = startInset + edgePad
        var top = padV
        for (i in groups.indices) {
            if (!groups[i].shown) continue
            drawGroup(canvas, i, x, top + baselineOffset)
            top += rowHeight + lineGap
        }
    }

    private fun drawGroup(canvas: Canvas, index: Int, startX: Float, baseline: Float) {
        val group = groups[index]
        var x = startX
        if (group.label.isNotEmpty()) {
            canvas.drawText(group.label, x, baseline, labelPaints[index])
            x += group.labelWidth + labelGap
        }
        var first = true
        for (slot in group.slots) {
            if (!slot.present) continue
            if (!first) x += innerSpace
            canvas.drawText(slot.text, x, baseline, slot.paint)
            x += slot.advance
            first = false
        }
    }

    // ---- touch ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragListener?.onDragStart()
                }
                if (dragging) dragListener?.onDrag(dx, dy)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) dragListener?.onDragEnd()
                dragging = false
            }
        }
        // touches on the HUD are its own: they must not reach the controls or the game below
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateThread()
    }

    override fun onDetachedFromWindow() {
        stopThread()
        if (dragging) {
            dragging = false
            dragListener?.onDragEnd()
        }
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateThread()
    }

    private companion object {
        const val NOT_READ = Int.MIN_VALUE
        const val STATS_INTERVAL_MS = 1000L
        const val BASE_TEXT_DP = 12f

        /** Below this background opacity the text switches to colors and a shadow that read over the game. */
        const val BARE_OPACITY = 0.45f
        const val SHADOW_COLOR = 0xC0000000.toInt()
        const val OPAQUE_BLACK = 0xFF000000.toInt()
        const val TEXT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG

        val GROUP_OF: Array<HudGroup> = Array(HudItem.entries.size) { i ->
            when (HudItem.entries[i]) {
                HudItem.FPS -> HudGroup.FPS
                HudItem.GPU_NAME -> HudGroup.GPU_NAME
                HudItem.GPU_USAGE -> HudGroup.GPU
                HudItem.CPU_USAGE, HudItem.CPU_TEMP -> HudGroup.CPU
                HudItem.RAM -> HudGroup.RAM
                HudItem.POWER -> HudGroup.POWER
                HudItem.BATTERY_LEVEL, HudItem.BATTERY_TEMP, HudItem.CHARGING -> HudGroup.BATTERY
            }
        }

        val SLOT_OF: IntArray = IntArray(HudItem.entries.size) { i ->
            when (HudItem.entries[i]) {
                HudItem.CPU_TEMP, HudItem.BATTERY_TEMP -> 1
                HudItem.CHARGING -> 2
                else -> 0
            }
        }

        /** The widest usual value of each item; none for the GPU name and the charging mark, which rarely change. */
        val RESERVE: Array<String?> = Array(HudItem.entries.size) { i ->
            when (HudItem.entries[i]) {
                HudItem.FPS -> "000"
                HudItem.GPU_USAGE, HudItem.CPU_USAGE, HudItem.RAM, HudItem.BATTERY_LEVEL -> "100%"
                HudItem.CPU_TEMP -> "100°C"
                HudItem.BATTERY_TEMP -> "00°C"
                HudItem.POWER -> "00.0W"
                HudItem.GPU_NAME, HudItem.CHARGING -> null
            }
        }
    }
}
