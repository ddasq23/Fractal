package dev.dfd.fractal.emulation.inputoverlay

import android.graphics.Color
import dev.dfd.fractal.common.settings.OverlayStyle

/**
 * Opaque colors of the on-screen controls; the opacity setting is applied on top by [SkinColors].
 */
data class OverlayPalette(
    /** Fill of an idle button. */
    val body: Int,
    /** Fill of a pressed button. */
    val bodyPressed: Int,
    /** Outline of an idle button. */
    val rim: Int,
    /** Letter or icon on an idle button. */
    val glyph: Int,
    /** Letter or icon on a pressed button. */
    val glyphPressed: Int,
    /** Well of the sticks and plate of the d-pad. */
    val base: Int,
    /** Ring around pressed inputs and the stick direction marker. */
    val glow: Int,
) {
    companion object {
        /** Translucent black and white, close to the look of older versions. */
        val CLASSIC = OverlayPalette(
            body = Color.rgb(0, 0, 0),
            bodyPressed = Color.rgb(255, 255, 255),
            rim = Color.rgb(255, 255, 255),
            glyph = Color.rgb(255, 255, 255),
            glyphPressed = Color.rgb(0, 0, 0),
            base = Color.rgb(90, 90, 90),
            glow = Color.rgb(255, 255, 255),
        )

        val DARK = OverlayPalette(
            body = Color.rgb(18, 18, 24),
            bodyPressed = Color.rgb(236, 236, 242),
            rim = Color.rgb(120, 120, 136),
            glyph = Color.rgb(240, 240, 246),
            glyphPressed = Color.rgb(16, 16, 20),
            base = Color.rgb(36, 36, 46),
            glow = Color.rgb(255, 255, 255),
        )

        val LIGHT = OverlayPalette(
            body = Color.rgb(248, 248, 252),
            bodyPressed = Color.rgb(28, 28, 34),
            rim = Color.rgb(70, 70, 82),
            glyph = Color.rgb(30, 30, 36),
            glyphPressed = Color.rgb(255, 255, 255),
            base = Color.rgb(212, 212, 222),
            glow = Color.rgb(20, 20, 24),
        )

        /** Dark controls outlined and lit in [color]. */
        fun custom(color: Int): OverlayPalette {
            val rgb = color or 0xFF000000.toInt()
            return OverlayPalette(
                body = Color.rgb(12, 12, 18),
                bodyPressed = rgb,
                rim = rgb,
                glyph = mix(rgb, Color.WHITE, 0.6f),
                glyphPressed = contrastOn(rgb),
                base = mix(rgb, Color.BLACK, 0.78f),
                glow = rgb,
            )
        }

        /** From the app theme: [accent] when pressed, the theme [background] and [text] otherwise. */
        fun fromTheme(accent: Int, onAccent: Int, background: Int, text: Int): OverlayPalette {
            val bg = background or 0xFF000000.toInt()
            val fg = text or 0xFF000000.toInt()
            val ac = accent or 0xFF000000.toInt()
            return OverlayPalette(
                body = bg,
                bodyPressed = ac,
                rim = mix(fg, ac, 0.45f),
                glyph = fg,
                glyphPressed = onAccent or 0xFF000000.toInt(),
                base = mix(bg, ac, 0.22f),
                glow = ac,
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

/** Palette the inputs read when they configure; set by the overlay before it (re)configures them. */
object Colors {
    var palette: OverlayPalette = OverlayPalette.CLASSIC
}

/**
 * Colors of one input at one opacity. Labels stay more opaque than the body so they remain
 * readable over a bright game, and pressed inputs light up well above the idle opacity.
 */
class SkinColors {
    var body = 0
        private set
    var bodyTop = 0
        private set
    var bodyPressed = 0
        private set
    var bodyPressedTop = 0
        private set
    var rim = 0
        private set
    var glyph = 0
        private set
    var glyphPressed = 0
        private set
    var base = 0
        private set
    var baseRim = 0
        private set
    var glow = 0
        private set
    var shadow = 0
        private set
    var sheen = 0
        private set

    fun update(alpha: Int, palette: OverlayPalette = Colors.palette) {
        val a = alpha.coerceIn(0, 255)
        val pressedAlpha = scaled(a, 1.5f, 80).coerceAtMost(245)
        body = withAlpha(palette.body, scaled(a, 0.8f))
        bodyTop = withAlpha(OverlayPalette.mix(palette.body, palette.rim, 0.16f), scaled(a, 0.8f))
        bodyPressed = withAlpha(palette.bodyPressed, pressedAlpha)
        bodyPressedTop = withAlpha(OverlayPalette.mix(palette.bodyPressed, Color.WHITE, 0.22f), pressedAlpha)
        rim = withAlpha(palette.rim, a)
        glyph = withAlpha(palette.glyph, scaled(a, 1.6f))
        glyphPressed = withAlpha(palette.glyphPressed, scaled(a, 1.6f, 120))
        base = withAlpha(palette.base, scaled(a, 0.6f))
        baseRim = withAlpha(palette.rim, scaled(a, 0.7f))
        glow = withAlpha(palette.glow, scaled(a, 0.9f, 50))
        shadow = withAlpha(Color.BLACK, scaled(a, 0.35f))
        sheen = withAlpha(Color.WHITE, scaled(a, 0.22f))
    }

    private fun scaled(alpha: Int, factor: Float, floor: Int = 0): Int =
        if (alpha == 0) 0 else (alpha * factor).toInt().coerceAtLeast(floor).coerceAtMost(255)

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
}
