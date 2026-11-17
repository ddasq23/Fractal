package dev.dfd.fractal.common.settings

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PerformanceHudSettingsTest {
    @Test
    fun `defaults show everything but the battery level, at the top center`() {
        val settings = PerformanceHudSettings()
        assertFalse(settings.enabled)
        for (item in HudItem.entries) assertEquals(item.name, item != HudItem.BATTERY_LEVEL, settings.shows(item))
        assertEquals(HudAnchor.TOP_CENTER, settings.anchor)
    }

    @Test
    fun `item bits are distinct`() {
        assertEquals(HudItem.entries.size, HudItem.entries.map { it.bit }.toSet().size)
        assertTrue(HudItem.entries.all { Integer.bitCount(it.bit) == 1 })
    }

    @Test
    fun `rapid independent toggles keep the latest state`() {
        // the case of Winlator-Ludashi's HudPreferencesTest
        val settings = PerformanceHudSettings()
            .withItem(HudItem.FPS, false)
            .withItem(HudItem.RAM, false)
            .withItem(HudItem.FPS, true)
        assertTrue(settings.shows(HudItem.FPS))
        assertFalse(settings.shows(HudItem.RAM))
        assertTrue(settings.shows(HudItem.CPU_USAGE))
    }

    @Test
    fun `a dragged place has no anchor until one is picked`() {
        val dragged = PerformanceHudSettings().copy(x = 0.31f, y = 0.72f)
        assertNull(dragged.anchor)
        val picked = dragged.at(HudAnchor.BOTTOM_RIGHT)
        assertEquals(HudAnchor.BOTTOM_RIGHT, picked.anchor)
        assertEquals(1f, picked.x)
        assertEquals(1f, picked.y)
    }

    @Test
    fun `out of range values are brought back`() {
        val clean = PerformanceHudSettings(
            items = -1,
            sizePercent = 900,
            opacityPercent = -20,
            x = Float.NaN,
            y = 3f,
        ).sanitized()
        assertEquals(HudItem.ALL_MASK, clean.items)
        assertEquals(PerformanceHudSettings.SIZE_MAX, clean.sizePercent)
        assertEquals(PerformanceHudSettings.OPACITY_MIN, clean.opacityPercent)
        assertEquals(PerformanceHudSettings.DEFAULT_ANCHOR.x, clean.x)
        assertEquals(1f, clean.y)
        val fine = PerformanceHudSettings()
        assertTrue(fine.sanitized() === fine)
    }

    @Test
    fun `settings saved before the HUD existed read as the defaults, and the HUD settings survive a save`() = runBlocking {
        val old = AppSettingsSerializer.readFrom(ByteArrayInputStream("""{"guiSettings":{"theme":"SYNTHWAVE"}}""".toByteArray()))
        assertEquals(PerformanceHudSettings(), old.performanceHud)

        val hud = PerformanceHudSettings(enabled = true, items = HudItem.FPS.bit, sizePercent = 140, vertical = true)
            .copy(x = 0.25f, y = 0.5f)
        val out = ByteArrayOutputStream()
        AppSettingsSerializer.writeTo(old.copy(performanceHud = hud), out)
        val read = AppSettingsSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
        assertEquals(hud, read.performanceHud)
    }
}
