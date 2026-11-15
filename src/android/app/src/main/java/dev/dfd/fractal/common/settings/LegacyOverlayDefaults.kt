package dev.dfd.fractal.common.settings

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Default spots of the overlay before the adaptive layout (layout version 0). Only used for
 * profiles where the user moved inputs back then ([usesLegacyLayout]): the inputs they did not
 * move stay where they were, so a saved layout keeps looking the same until it is reset.
 */

/** Offsets from the screen edges in dp, as the old fixed layout had them. */
private data class LegacySpot(
    val alignBottom: Boolean = false,
    val alignEnd: Boolean = false,
    val paddingHorizontal: Int = 0,
    val paddingVertical: Int = 0,
    val width: Int,
    val height: Int = width,
)

private fun legacySpot(input: OverlayInputConfig): LegacySpot = when (input) {
    OverlayInputConfig.BUTTON_PLUS -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 272, paddingVertical = 24, width = 48)
    OverlayInputConfig.BUTTON_MINUS -> LegacySpot(alignBottom = true, paddingHorizontal = 272, paddingVertical = 24, width = 48)
    OverlayInputConfig.BUTTON_HOME -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 208, paddingVertical = 80, width = 48)
    OverlayInputConfig.BUTTON_L -> LegacySpot(paddingHorizontal = 48, paddingVertical = 8, width = 72, height = 36)
    OverlayInputConfig.BUTTON_ZL -> LegacySpot(paddingHorizontal = 48, paddingVertical = 64, width = 72, height = 36)
    OverlayInputConfig.BUTTON_R -> LegacySpot(alignEnd = true, paddingHorizontal = 48, paddingVertical = 8, width = 72, height = 36)
    OverlayInputConfig.BUTTON_ZR -> LegacySpot(alignEnd = true, paddingHorizontal = 48, paddingVertical = 64, width = 72, height = 36)
    OverlayInputConfig.BUTTON_C -> LegacySpot(alignEnd = true, paddingHorizontal = 60, paddingVertical = 8, width = 48)
    OverlayInputConfig.BUTTON_Z -> LegacySpot(alignEnd = true, paddingHorizontal = 48, paddingVertical = 64, width = 72, height = 36)
    OverlayInputConfig.BUTTON_A -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 32, paddingVertical = 56, width = 48)
    OverlayInputConfig.BUTTON_Y -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 128, paddingVertical = 56, width = 48)
    OverlayInputConfig.BUTTON_X -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 80, paddingVertical = 104, width = 48)
    OverlayInputConfig.BUTTON_B -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 80, paddingVertical = 8, width = 48)
    OverlayInputConfig.BUTTON_ONE -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 128, paddingVertical = 56, width = 48)
    OverlayInputConfig.BUTTON_TWO -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 80, paddingVertical = 104, width = 48)
    OverlayInputConfig.BUTTON_R_STICK_CLICK -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 208, paddingVertical = 80, width = 48)
    OverlayInputConfig.BUTTON_L_STICK_CLICK -> LegacySpot(alignBottom = true, paddingHorizontal = 208, paddingVertical = 80, width = 48)
    OverlayInputConfig.JOYSTICK_LEFT -> LegacySpot(alignBottom = true, paddingHorizontal = 144, paddingVertical = 120, width = 72)
    OverlayInputConfig.JOYSTICK_RIGHT -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 144, paddingVertical = 120, width = 72)
    OverlayInputConfig.DPAD -> LegacySpot(alignBottom = true, paddingHorizontal = 8, paddingVertical = 8, width = 144)
    OverlayInputConfig.BUTTON_BLOW_MIC -> LegacySpot(alignBottom = true, alignEnd = true, paddingHorizontal = 8, paddingVertical = 8, width = 40)
}

/** Old default spot of [input] on a [width] x [height] px surface with [density] px per dp. */
fun legacyDefaultRect(input: OverlayInputConfig, width: Int, height: Int, density: Float): NormalizedRect {
    fun Int.dpToPx() = (this * density).roundToInt()
    val spot = legacySpot(input)
    val inputWidth = spot.width.dpToPx()
    val inputHeight = spot.height.dpToPx()
    val horizontalPadding = spot.paddingHorizontal.dpToPx()
    val verticalPadding = spot.paddingVertical.dpToPx()
    val top = min(
        max(if (spot.alignBottom) height - inputHeight else 0, verticalPadding),
        height - verticalPadding - inputHeight,
    )
    val left = min(
        max(if (spot.alignEnd) width - inputWidth else 0, horizontalPadding),
        width - horizontalPadding - inputWidth,
    )
    return NormalizedRect.fromPixels(left, top, left + inputWidth, top + inputHeight, width, height)
}

/** Old default spot of the [index]th macro. */
fun legacyMacroRect(index: Int, width: Int, height: Int, density: Float): NormalizedRect {
    val shortSide = max(min(width, height), 1).toFloat()
    val column = index / 4
    val row = index % 4
    return NormalizedRect(
        centerX = (0.5f + (column - 0.5f) * 0.14f).coerceIn(0.1f, 0.9f),
        centerY = 0.3f + row * 0.13f,
        width = 72 * density / shortSide,
        height = 40 * density / shortSide,
    )
}

/** Old default spot of the [index]th shortcut. */
fun legacyShortcutRect(index: Int, width: Int, height: Int, density: Float): NormalizedRect {
    val shortSide = max(min(width, height), 1).toFloat()
    val column = index % 6
    val row = index / 6
    return NormalizedRect(
        centerX = (0.27f + column * 0.09f).coerceIn(0.05f, 0.95f),
        centerY = (0.07f + row * 0.1f).coerceIn(0.05f, 0.95f),
        width = 60 * density / shortSide,
        height = 34 * density / shortSide,
    )
}
