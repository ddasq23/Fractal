/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.emulation.hud

import dev.dfd.fractal.common.ui.theme.FractalThemeId

/** The HUD's groups, in the order they are drawn: a label and up to three values each. */
enum class HudGroup(val label: String) {
    FPS("FPS"),
    GPU_NAME(""),
    GPU("GPU"),
    CPU("CPU"),
    RAM("RAM"),
    EMU("EMU"),
    DRAWS("DRAW"),
    POWER("PWR"),
    BATTERY("BAT"),
}

enum class HudShape {
    /** Rounded rectangle with [HudStyle.cornerDp] corners. */
    ROUNDED,

    /** Fully rounded ends while it is a single row; rounded corners when it is a column. */
    PILL,

    /** The top start and bottom end corners cut, like the Solid and Synthwave cards. */
    CHAMFERED,
}

enum class HudFont { GROTESK, INTER }

/** The theme colors the HUD is made from, as ARGB. */
data class HudThemeColors(
    val id: FractalThemeId,
    val isDark: Boolean,
    val background: Int,
    val surface: Int,
    val accent: Int,
    val text: Int,
    val textSecondary: Int,
    val border: Int,
)

/**
 * How the HUD is painted: the app theme's colors, shape and kind of surface, with a hue per statistic that stays
 * readable over any game picture. Colors are ARGB. [labels] and [value] are for the HUD's own background; with the
 * background faded out the HUD uses [labelsBare] and [valueBare], light colors that read over the game with a shadow.
 */
data class HudStyle(
    /** One color, or several for a gradient from top to bottom. */
    val fill: List<Int>,
    /** One color, or several for a gradient from start to end. Empty for none. */
    val border: List<Int>,
    val borderWidthDp: Float,
    /** Light line along the top inner edge (glass), 0 for none. */
    val highlight: Int,
    /** Bar along the start edge (Solid's selection bars), 0 for none. */
    val accentBar: Int,
    val shape: HudShape,
    val cornerDp: Float,
    val labelFont: HudFont,
    val labels: List<Int>,
    val labelsBare: List<Int>,
    val value: Int,
    val valueBare: Int,
    val charge: Int,
    val chargeBare: Int,
    val separator: Int,
) {
    companion object {
        /** Winlator-Ludashi's colors per statistic, the base every theme tints. */
        private val BaseHues = mapOf(
            HudGroup.FPS to 0xFF76FF03.toInt(),
            HudGroup.GPU_NAME to 0xFFA9D6FF.toInt(),
            HudGroup.GPU to 0xFFE040FB.toInt(),
            HudGroup.CPU to 0xFF00E5FF.toInt(),
            HudGroup.RAM to 0xFFB0FFB0.toInt(),
            HudGroup.EMU to 0xFFFFD740.toInt(),
            HudGroup.DRAWS to 0xFF9FA8DA.toInt(),
            HudGroup.POWER to 0xFFFF8000.toInt(),
            HudGroup.BATTERY to 0xFFEF5350.toInt(),
        )
        private const val BASE_CHARGE = 0xFF40C440.toInt()

        /** Neon set of the Synthwave theme. */
        private val SynthwaveHues = mapOf(
            HudGroup.FPS to 0xFF00F0FF.toInt(),
            HudGroup.GPU_NAME to 0xFFC3B9E6.toInt(),
            HudGroup.GPU to 0xFFFF2D95.toInt(),
            HudGroup.CPU to 0xFF6AE3FF.toInt(),
            HudGroup.RAM to 0xFFB388FF.toInt(),
            HudGroup.EMU to 0xFFFFE66D.toInt(),
            HudGroup.DRAWS to 0xFF7DF9C2.toInt(),
            HudGroup.POWER to 0xFFFFB000.toInt(),
            HudGroup.BATTERY to 0xFFFF6E9C.toInt(),
        )

        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()

        fun from(theme: HudThemeColors): HudStyle = when (theme.id) {
            FractalThemeId.SOLID -> solid(theme)
            FractalThemeId.LIQUID_GLASS -> liquidGlass(theme)
            FractalThemeId.BLUR -> blur(theme)
            FractalThemeId.MATERIAL_YOU -> materialYou(theme)
            FractalThemeId.SYNTHWAVE -> synthwave(theme)
        }

        /** Opaque chamfered card with the theme's border and the accent bar on the start edge. */
        private fun solid(t: HudThemeColors): HudStyle {
            val fill = withAlpha(t.surface, 0xE6)
            return build(
                t, fill = listOf(fill), border = listOf(opaque(t.border)), borderWidthDp = 1f,
                highlight = 0, accentBar = opaque(t.accent), shape = HudShape.CHAMFERED, cornerDp = 6f,
                labelFont = HudFont.GROTESK, hues = BaseHues, tint = t.accent, tintAmount = 0.18f,
                value = t.text, separator = withAlpha(t.textSecondary, 0x66),
            )
        }

        /** Smoked glass pill with a light rim and the top highlight. */
        private fun liquidGlass(t: HudThemeColors): HudStyle {
            // the theme's white glass would wash out over a bright game: a darker tint keeps the values readable
            val fill = withAlpha(mix(opaque(t.background), WHITE, 0.10f), 0xA6)
            return build(
                t, fill = listOf(fill), border = listOf(withAlpha(WHITE, 0x47)), borderWidthDp = 1f,
                highlight = withAlpha(WHITE, 0x66), accentBar = 0, shape = HudShape.PILL, cornerDp = 16f,
                labelFont = HudFont.INTER, hues = BaseHues.mapValues { mix(it.value, WHITE, 0.22f) },
                tint = t.accent, tintAmount = 0.10f, value = WHITE, separator = withAlpha(WHITE, 0x4D),
            )
        }

        /** Dark frosted panel; a SurfaceView can't be blurred behind, so the fill is denser instead. */
        private fun blur(t: HudThemeColors): HudStyle = build(
            t, fill = listOf(withAlpha(BLACK, 0xB3)), border = listOf(withAlpha(WHITE, 0x29)), borderWidthDp = 1f,
            highlight = 0, accentBar = 0, shape = HudShape.ROUNDED, cornerDp = 12f,
            labelFont = HudFont.INTER, hues = BaseHues, tint = t.accent, tintAmount = 0.08f,
            value = WHITE, separator = withAlpha(WHITE, 0x40),
        )

        /** Tonal surface of the dynamic color scheme, the statistics' hues pulled towards its primary color. */
        private fun materialYou(t: HudThemeColors): HudStyle = build(
            t, fill = listOf(withAlpha(t.surface, 0xEB)), border = emptyList(), borderWidthDp = 0f,
            highlight = 0, accentBar = 0, shape = HudShape.ROUNDED, cornerDp = 12f,
            labelFont = HudFont.INTER, hues = BaseHues, tint = t.accent, tintAmount = 0.45f,
            value = t.text, separator = withAlpha(t.textSecondary, 0x59),
        )

        /** Night gradient card with the magenta to cyan rim and neon labels. */
        private fun synthwave(t: HudThemeColors): HudStyle = build(
            t,
            fill = listOf(0xE015102E.toInt(), 0xE02A0B48.toInt()),
            border = listOf(0xFFFF2D95.toInt(), 0xFF6A3CFF.toInt(), 0xFF00C8FF.toInt()),
            borderWidthDp = 1.5f, highlight = 0, accentBar = 0, shape = HudShape.CHAMFERED, cornerDp = 6f,
            labelFont = HudFont.GROTESK, hues = SynthwaveHues, tint = t.accent, tintAmount = 0f,
            value = t.text, separator = 0x996A5AA0.toInt(),
        )

        private fun build(
            t: HudThemeColors,
            fill: List<Int>,
            border: List<Int>,
            borderWidthDp: Float,
            highlight: Int,
            accentBar: Int,
            shape: HudShape,
            cornerDp: Float,
            labelFont: HudFont,
            hues: Map<HudGroup, Int>,
            tint: Int,
            tintAmount: Float,
            value: Int,
            separator: Int,
        ): HudStyle {
            // what the labels sit on: the fill over a dark game picture
            val backdrop = over(fill.first(), BLACK)
            val tinted = HudGroup.entries.map { mix(hues.getValue(it), opaque(tint), tintAmount) }
            val charge = mix(BASE_CHARGE, opaque(tint), tintAmount * 0.5f)
            return HudStyle(
                fill = fill,
                border = border,
                borderWidthDp = borderWidthDp,
                highlight = highlight,
                accentBar = accentBar,
                shape = shape,
                cornerDp = cornerDp,
                labelFont = labelFont,
                labels = tinted.map { readableOn(it, backdrop) },
                labelsBare = tinted.map { readableOn(it, BLACK) },
                value = readableOn(opaque(value), backdrop, minContrast = 7.0),
                valueBare = WHITE,
                charge = readableOn(charge, backdrop),
                chargeBare = readableOn(charge, BLACK),
                separator = separator,
            )
        }

        fun alpha(c: Int): Int = c ushr 24
        private fun red(c: Int) = (c shr 16) and 0xFF
        private fun green(c: Int) = (c shr 8) and 0xFF
        private fun blue(c: Int) = c and 0xFF
        private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

        fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
        fun opaque(c: Int): Int = c or BLACK

        /** [a] moved towards [b] by [t], keeping [a]'s alpha. */
        fun mix(a: Int, b: Int, t: Float): Int = argb(
            alpha(a),
            (red(a) + (red(b) - red(a)) * t).toInt(),
            (green(a) + (green(b) - green(a)) * t).toInt(),
            (blue(a) + (blue(b) - blue(a)) * t).toInt(),
        )

        /** [top] painted over the opaque [bottom]. */
        fun over(top: Int, bottom: Int): Int = mix(opaque(bottom), opaque(top), alpha(top) / 255f)

        /** WCAG relative luminance. */
        fun luminance(c: Int): Double {
            fun channel(v: Int): Double {
                val s = v / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(red(c)) + 0.7152 * channel(green(c)) + 0.0722 * channel(blue(c))
        }

        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        /** [color], lightened on a dark [background] or darkened on a light one until it reads well. */
        fun readableOn(color: Int, background: Int, minContrast: Double = 4.5): Int {
            val bg = opaque(background)
            val target = if (luminance(bg) < 0.4) WHITE else BLACK
            var c = opaque(color)
            var steps = 0
            while (contrast(c, bg) < minContrast && steps < 12) {
                c = mix(c, target, 0.15f)
                steps++
            }
            return c
        }
    }
}
