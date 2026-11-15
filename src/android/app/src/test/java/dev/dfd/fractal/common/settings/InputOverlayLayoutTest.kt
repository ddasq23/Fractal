package dev.dfd.fractal.common.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputOverlayLayoutTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val rect = NormalizedRect(0.8f, 0.6f, 0.12f, 0.12f)
    private val other = NormalizedRect(0.2f, 0.3f, 0.2f, 0.2f)

    @Test
    fun `profiles saved before the adaptive layout keep their moved inputs`() {
        val saved = """
            {"alpha":90,"profiles":[{"id":"p1","controllerType":"VPAD",
            "rects":{"BUTTON_A":{"centerX":0.9,"centerY":0.5,"width":0.1,"height":0.1}}},
            {"id":"p2","controllerType":"PRO"}],
            "activeProfileIds":{"VPAD":"p1","PRO":"p2"}}
        """.trimIndent()
        val settings = json.decodeFromString<InputOverlaySettings>(saved)

        assertEquals(90, settings.alpha)
        val moved = settings.activeProfile(OverlayControllerType.VPAD)
        assertEquals(0, moved.layoutVersion)
        assertTrue(moved.usesLegacyLayout)
        assertEquals(0.9f, moved.rects[OverlayInputConfig.BUTTON_A]!!.centerX, 1e-6f)
        // nothing moved: the new layout applies without asking
        assertFalse(settings.activeProfile(OverlayControllerType.PRO).usesLegacyLayout)
        // never saved: a fresh default profile on the new layout
        assertEquals(OVERLAY_LAYOUT_VERSION, settings.activeProfile(OverlayControllerType.CLASSIC).layoutVersion)
    }

    @Test
    fun `settings survive a save and a load`() {
        val settings = InputOverlaySettings()
            .setInputRect(OverlayControllerType.VPAD, styleKey(OverlayInputConfig.DPAD), rect, portrait = false)
            .setInputRect(OverlayControllerType.VPAD, styleKey(OverlayInputConfig.DPAD), other, portrait = true)
        val decoded = json.decodeFromString<InputOverlaySettings>(json.encodeToString(settings))
        assertEquals(settings, decoded)
        assertFalse(decoded.activeProfile(OverlayControllerType.VPAD).usesLegacyLayout)
    }

    @Test
    fun `positions are kept per orientation and can be cleared`() {
        val key = styleKey(OverlayInputConfig.BUTTON_B)
        var profile = OverlayLayoutProfile(controllerType = OverlayControllerType.VPAD, layoutVersion = OVERLAY_LAYOUT_VERSION)
        profile = profile.withRect(key, rect, portrait = false).withRect(key, other, portrait = true)
        assertEquals(rect, profile.savedRect(key, portrait = false))
        assertEquals(other, profile.savedRect(key, portrait = true))

        profile = profile.withRect(key, null, portrait = false)
        assertNull(profile.savedRect(key, portrait = false))
        assertEquals(other, profile.savedRect(key, portrait = true))
    }

    @Test
    fun `macros and shortcuts keep their position on themselves`() {
        val macro = OverlayMacro(label = "A+B")
        val shortcut = OverlayShortcut(action = HotkeyAction.TOGGLE_MENU)
        var profile = OverlayLayoutProfile(controllerType = OverlayControllerType.PRO, macros = listOf(macro), shortcuts = listOf(shortcut))
        profile = profile.withRect(macro.id, rect, portrait = false).withRect(shortcut.id, other, portrait = false)
        assertEquals(rect, profile.macros.single().rect)
        assertEquals(other, profile.shortcuts.single().rect)
        assertEquals(rect, profile.savedRect(macro.id, portrait = false))
    }

    @Test
    fun `reset puts everything back on the adaptive layout and keeps the rest`() {
        val type = OverlayControllerType.VPAD
        val macro = OverlayMacro(label = "turbo A", rect = rect, behavior = ButtonBehavior(turbo = true))
        val behavior = ButtonBehavior(toggle = true)
        val settings = InputOverlaySettings(
            profiles = listOf(
                OverlayLayoutProfile(
                    id = "p",
                    controllerType = type,
                    rects = mapOf(OverlayInputConfig.BUTTON_A to rect),
                    portraitRects = mapOf("x" to other),
                    behaviors = mapOf(OverlayInputConfig.BUTTON_A to behavior),
                    macros = listOf(macro),
                    styles = mapOf(
                        macro.id to OverlayInputStyle(scale = 1.4f),
                        styleKey(OverlayInputConfig.BUTTON_A) to OverlayInputStyle(scale = 1.2f, opacity = 200),
                    ),
                ),
            ),
            activeProfileIds = mapOf(type to "p"),
        )
        assertTrue(settings.activeProfile(type).usesLegacyLayout)

        val reset = settings.resetActiveLayout(type).activeProfile(type)
        assertTrue(reset.rects.isEmpty())
        assertTrue(reset.portraitRects.isEmpty())
        assertNull(reset.macros.single().rect)
        assertTrue(reset.macros.single().behavior.turbo)
        assertEquals(behavior, reset.behaviors[OverlayInputConfig.BUTTON_A])
        // sizes go back to 1, opacity stays; styles left at the defaults are dropped
        assertNull(reset.styles[macro.id])
        assertEquals(OverlayInputStyle(opacity = 200), reset.styles[styleKey(OverlayInputConfig.BUTTON_A)])
        assertEquals(OVERLAY_LAYOUT_VERSION, reset.layoutVersion)
        assertFalse(reset.usesLegacyLayout)
    }

    @Test
    fun `duplicating a macro copies its look and places the copy`() {
        val type = OverlayControllerType.CLASSIC
        val macro = OverlayMacro(label = "combo")
        val settings = InputOverlaySettings()
            .upsertMacro(type, macro)
            .setInputStyle(type, macro.id, OverlayInputStyle(opacity = 120))

        val (copied, newId) = settings.duplicateExtra(type, macro.id, rect, portrait = false)
        assertNotNull(newId)
        assertNotEquals(macro.id, newId)
        val profile = copied.activeProfile(type)
        assertEquals(2, profile.macros.size)
        assertEquals(rect, profile.savedRect(newId!!, portrait = false))
        assertEquals(OverlayInputStyle(opacity = 120), profile.styles[newId])

        assertNull(settings.duplicateExtra(type, "missing", rect, portrait = false).second)
    }

    @Test
    fun `copied profiles get their own ids and portrait positions`() {
        val type = OverlayControllerType.VPAD
        val macro = OverlayMacro(label = "m")
        val base = InputOverlaySettings()
            .upsertMacro(type, macro)
            .setInputRect(type, macro.id, other, portrait = true)
        val copied = base.createProfile(type, "copy", base.activeProfile(type))
        val profile = copied.activeProfile(type)
        assertEquals("copy", profile.name)
        val newId = profile.macros.single().id
        assertNotEquals(macro.id, newId)
        assertEquals(other, profile.portraitRects[newId])
        assertEquals(2, copied.profilesFor(type).size)
    }

    @Test
    fun `hidden inputs are remembered and shown again`() {
        val hidden = InputOverlaySettings().setInputVisible(OverlayInputConfig.BUTTON_HOME, false)
        assertFalse(hidden.isInputVisible(OverlayInputConfig.BUTTON_HOME))
        assertTrue(hidden.isInputVisible(OverlayInputConfig.BUTTON_A))
        val shown = hidden.setInputVisible(OverlayInputConfig.BUTTON_HOME, true)
        assertTrue(shown.isInputVisible(OverlayInputConfig.BUTTON_HOME))
        assertTrue(shown.inputVisibilityMap.isEmpty())
    }
}
