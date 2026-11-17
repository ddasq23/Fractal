package dev.dfd.fractal.emulation.hud

import dev.dfd.fractal.common.ui.theme.FractalThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudStyleTest {
    private val black = 0xFF000000.toInt()

    /** The themes' token colors, as Tokens.kt has them (Material You with a dark and a light scheme). */
    private val themes = listOf(
        HudThemeColors(FractalThemeId.SOLID, true, 0xFF100F16.toInt(), 0xFF17151F.toInt(), 0xFFFF2D95.toInt(), 0xFFF2F0F7.toInt(), 0xFFB9B5C8.toInt(), 0xFF2C2838.toInt()),
        HudThemeColors(FractalThemeId.LIQUID_GLASS, true, 0xFF14121C.toInt(), 0x1CFFFFFF, 0xFFFF2D95.toInt(), 0xFFFFFFFF.toInt(), 0xB8FFFFFF.toInt(), 0x3DFFFFFF),
        HudThemeColors(FractalThemeId.BLUR, true, 0xFF09080D.toInt(), 0x61000000, 0xFFFF2D95.toInt(), 0xFFFFFFFF.toInt(), 0xBFFFFFFF.toInt(), 0x24FFFFFF),
        HudThemeColors(FractalThemeId.SYNTHWAVE, true, 0xFF0B0820.toInt(), 0xFF15102E.toInt(), 0xFFFF2D95.toInt(), 0xFFF2F0F7.toInt(), 0xFFC3B9E6.toInt(), 0xFF3A2C70.toInt()),
        HudThemeColors(FractalThemeId.MATERIAL_YOU, true, 0xFF111318.toInt(), 0xFF1D2024.toInt(), 0xFFA8C8FF.toInt(), 0xFFE2E2E9.toInt(), 0xFFC4C6D0.toInt(), 0xFF44474F.toInt()),
        HudThemeColors(FractalThemeId.MATERIAL_YOU, false, 0xFFF9F9FF.toInt(), 0xFFEDEDF4.toInt(), 0xFF3F5F90.toInt(), 0xFF191C20.toInt(), 0xFF44474F.toInt(), 0xFFC4C6D0.toInt()),
    )

    @Test
    fun `every theme's text reads on its own background and over a dark game`() {
        for (theme in themes) {
            val style = HudStyle.from(theme)
            val backdrop = HudStyle.over(style.fill.first(), black)
            assertEquals(HudGroup.entries.size, style.labels.size)
            for (label in style.labels) {
                assertTrue("${theme.id} label", HudStyle.contrast(label, backdrop) >= 4.5)
            }
            for (label in style.labelsBare) {
                assertTrue("${theme.id} bare label", HudStyle.contrast(label, black) >= 4.5)
            }
            assertTrue("${theme.id} value", HudStyle.contrast(style.value, backdrop) >= 7.0)
            assertTrue("${theme.id} charge", HudStyle.contrast(style.charge, backdrop) >= 4.5)
        }
    }

    @Test
    fun `each theme has its own look`() {
        val styles = themes.associate { it.id to HudStyle.from(it) }
        assertEquals(HudShape.CHAMFERED, styles.getValue(FractalThemeId.SOLID).shape)
        assertNotEquals(0, styles.getValue(FractalThemeId.SOLID).accentBar)
        assertEquals(HudShape.PILL, styles.getValue(FractalThemeId.LIQUID_GLASS).shape)
        assertNotEquals(0, styles.getValue(FractalThemeId.LIQUID_GLASS).highlight)
        assertEquals(HudShape.ROUNDED, styles.getValue(FractalThemeId.BLUR).shape)
        assertTrue(styles.getValue(FractalThemeId.SYNTHWAVE).fill.size > 1)
        assertTrue(styles.getValue(FractalThemeId.SYNTHWAVE).border.size > 1)
    }

    @Test
    fun `material you labels follow the scheme's primary and darken on a light scheme`() {
        val light = HudStyle.from(themes.last())
        val dark = HudStyle.from(themes[4])
        val fps = HudGroup.FPS.ordinal
        assertTrue(HudStyle.luminance(light.labels[fps]) < HudStyle.luminance(dark.labels[fps]))
        // with the background faded out the light scheme's HUD still reads over the game
        assertTrue(HudStyle.luminance(light.valueBare) > 0.9)
    }
}
