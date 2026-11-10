package dev.dfd.fractal.emulation.inputoverlay

import android.graphics.Color
import dev.dfd.fractal.common.settings.OverlayStyle

/**
 * Opaque colors of the overlay inputs; the overlay opacity is applied on top.
 * [activeAlphaBoost] makes pressed inputs stand out more than idle ones.
 */
data class OverlayPalette(
    val activeFill: Int,
    val activeStroke: Int,
    val inactiveFill: Int,
    val inactiveStroke: Int,
    val backgroundFill: Int,
    val backgroundStroke: Int,
    val activeAlphaBoost: Float = 1f,
) {
    companion object {
        val CLASSIC = OverlayPalette(
            activeFill = Color.rgb(255, 255, 255),
            activeStroke = Color.rgb(0, 0, 0),
            inactiveFill = Color.rgb(0, 0, 0),
            inactiveStroke = Color.rgb(255, 255, 255),
            backgroundFill = Color.rgb(128, 128, 128),
            backgroundStroke = Color.rgb(200, 200, 200),
        )

        val DARK = OverlayPalette(
            activeFill = Color.rgb(235, 235, 240),
            activeStroke = Color.rgb(16, 16, 20),
            inactiveFill = Color.rgb(12, 12, 16),
            inactiveStroke = Color.rgb(170, 170, 182),
            backgroundFill = Color.rgb(40, 40, 48),
            backgroundStroke = Color.rgb(120, 120, 132),
            activeAlphaBoost = 1.6f,
        )

        val LIGHT = OverlayPalette(
            activeFill = Color.rgb(26, 26, 30),
            activeStroke = Color.rgb(255, 255, 255),
            inactiveFill = Color.rgb(250, 250, 252),
            inactiveStroke = Color.rgb(30, 30, 34),
            backgroundFill = Color.rgb(215, 215, 222),
            backgroundStroke = Color.rgb(70, 70, 78),
            activeAlphaBoost = 1.6f,
        )

        /** Dark inputs outlined and lit in [color]. */
        fun custom(color: Int): OverlayPalette {
            val rgb = color or 0xFF000000.toInt()
            return OverlayPalette(
                activeFill = rgb,
                activeStroke = contrastOn(rgb),
                inactiveFill = Color.rgb(10, 10, 14),
                inactiveStroke = rgb,
                backgroundFill = mix(rgb, Color.BLACK, 0.6f),
                backgroundStroke = rgb,
                activeAlphaBoost = 1.6f,
            )
        }

        /** From the app theme: [accent] when pressed, the theme [background] and [text] otherwise. */
        fun fromTheme(accent: Int, onAccent: Int, background: Int, text: Int): OverlayPalette {
            val bg = background or 0xFF000000.toInt()
            val fg = text or 0xFF000000.toInt()
            val ac = accent or 0xFF000000.toInt()
            return OverlayPalette(
                activeFill = ac,
                activeStroke = onAccent or 0xFF000000.toInt(),
                inactiveFill = bg,
                inactiveStroke = fg,
                backgroundFill = mix(bg, ac, 0.3f),
                backgroundStroke = mix(fg, ac, 0.5f),
                activeAlphaBoost = 1.6f,
            )
        }

        /** Palette of a style that does not depend on the app theme; null for [OverlayStyle.THEME]. */
        fun forStyle(style: OverlayStyle, customColor: Int): OverlayPalette? = when (style) {
            OverlayStyle.THEME -> null
            OverlayStyle.CLASSIC -> CLASSIC
            OverlayStyle.DARK -> DARK
            OverlayStyle.LIGHT -> LIGHT
            OverlayStyle.CUSTOM -> custom(customColor)
        }

        fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )

        private fun contrastOn(color: Int): Int {
            val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return if (luminance > 0.6) Color.rgb(12, 12, 16) else Color.WHITE
        }
    }
}

/** Colors the inputs read when they configure; set by the overlay before it (re)configures them. */
object Colors {
    var palette: OverlayPalette = OverlayPalette.CLASSIC

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    private fun boosted(alpha: Int) = (alpha * palette.activeAlphaBoost).toInt()

    fun activeFill(alpha: Int) = withAlpha(palette.activeFill, boosted(alpha))
    fun activeStroke(alpha: Int) = withAlpha(palette.activeStroke, boosted(alpha))

    fun inactiveFill(alpha: Int) = withAlpha(palette.inactiveFill, alpha)
    fun inactiveStroke(alpha: Int) = withAlpha(palette.inactiveStroke, alpha)

    fun backgroundFill(alpha: Int) = withAlpha(palette.backgroundFill, alpha)
    fun backgroundStroke(alpha: Int) = withAlpha(palette.backgroundStroke, alpha)
}
