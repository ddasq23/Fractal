package dev.dfd.fractal.common.settings

import kotlinx.serialization.Serializable

/** Color scheme of the on-screen controls. */
@Serializable
enum class OverlayStyle {
    /** Derived from the app theme: accent when pressed, theme background and text otherwise. */
    THEME,

    /** Translucent black and white, the look of older versions. */
    CLASSIC,
    DARK,
    LIGHT,

    /** Dark buttons outlined and lit in [InputOverlaySettings.customColor]. */
    CUSTOM,
}

const val DEFAULT_OVERLAY_CUSTOM_COLOR: Int = 0xFF00C8FF.toInt()

/** Colors offered for [OverlayStyle.CUSTOM]. */
val OverlayCustomColors: List<Int> = listOf(
    0xFFFF2D95.toInt(),
    0xFF6A3CFF.toInt(),
    0xFF00C8FF.toInt(),
    0xFF00D68F.toInt(),
    0xFFFFC400.toInt(),
    0xFFFF6B35.toInt(),
    0xFFFF3B3B.toInt(),
    0xFFFFFFFF.toInt(),
)

/** Shape of an overlay button. */
@Serializable
enum class OverlayButtonShape {
    ROUND,
    SQUARE,
}

const val INPUT_SCALE_MIN = 0.5f
const val INPUT_SCALE_MAX = 2.5f

/**
 * Look of one input on top of the overlay wide settings.
 * [opacity]: 0..255, null follows [InputOverlaySettings.alpha].
 * [scale]: size multiplier on top of the input's own size and the overlay scale.
 * [shape]: null keeps the input's usual shape; sticks and the d-pad ignore it.
 */
@Serializable
data class OverlayInputStyle(
    val opacity: Int? = null,
    val scale: Float = 1f,
    val shape: OverlayButtonShape? = null,
) {
    val isDefault: Boolean get() = opacity == null && scale == 1f && shape == null
}

/** An on-screen button that runs a [HotkeyAction] instead of pressing controller buttons. */
@Serializable
data class OverlayShortcut(
    val id: String = java.util.UUID.randomUUID().toString(),
    val action: HotkeyAction,
    /** Blank shows the action's short name. */
    val label: String = "",
    /** Null until the user moves it; it is then placed at a default spot. */
    val rect: NormalizedRect? = null,
)

/** Key of a built in input in [OverlayLayoutProfile.styles]; macros and shortcuts use their id. */
fun styleKey(config: OverlayInputConfig): String = config.name
