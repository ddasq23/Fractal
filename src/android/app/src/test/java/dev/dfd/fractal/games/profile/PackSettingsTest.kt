package dev.dfd.fractal.games.profile

import dev.dfd.fractal.graphicpacks.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Detection against the shapes of the community packs (cemu_graphic_packs v990). */
class PackSettingsTest {
    private var nextId = 1L

    private fun pack(path: String, active: Boolean = false, name: String = path.substringAfterLast('/'), vararg presets: Preset) =
        GamePack(nextId++, path, name, "", "", active, presets.toList())

    private fun preset(category: String?, vararg choices: String, active: String = choices.first()) =
        Preset(0, category, active, choices.toList())

    @Test
    fun resolutionCategoryOfGraphicsPack() {
        val graphics = pack(
            "The Legend of Zelda: Breath of the Wild/Graphics", active = true, name = "Graphics",
            presets = arrayOf(
                preset("Aspect Ratio", "16:9 (Default)", "21:9"),
                preset("Resolution", "640x360", "1280x720 (HD, Default)", "1920x1080 (Full HD)", active = "640x360"),
                preset("Shadows", "Low (50%)", "Medium (100%, Default)"),
            ),
        )
        val setting = listOf(graphics).resolutionSetting()!!
        assertEquals(listOf("640x360", "1280x720 (HD, Default)", "1920x1080 (Full HD)"), setting.options.map { it.label })
        assertEquals("640x360", setting.selected?.choice)
        assertEquals("Resolution", setting.selected?.category)
    }

    @Test
    fun tvResolutionWinsOverGamepad() {
        val graphics = pack(
            "Mario Kart 8/Graphics", name = "Graphic Options",
            presets = arrayOf(
                preset("Gamepad Resolution", "640x360", "854x480 (Default)"),
                preset("TV Resolution", "640x360", "1280x720 (HD, Default)"),
                preset("Shadows", "Low (50%)", "Medium (100%, Default)"),
            ),
        )
        val setting = listOf(graphics).resolutionSetting()!!
        assertEquals("TV Resolution", setting.options.first().category)
        assertNull(setting.selected)
    }

    @Test
    fun resolutionPackWithoutCategory() {
        val shadow = pack("Pikmin 3/Graphics/Shadow Resolution", presets = arrayOf(preset(null, "Medium (100%, Default)", "Low (50%)")))
        val res = pack("Pikmin 3/Graphics/Resolution", active = true, presets = arrayOf(preset(null, "1280x720 (Default)", "640x360", "1920x1080", active = "1920x1080")))
        val setting = listOf(shadow, res).resolutionSetting()!!
        assertEquals(setOf(res.id), setting.packIds)
        assertEquals("1920x1080", setting.selected?.label)
    }

    @Test
    fun separatorsAreLeftOut() {
        val res = pack(
            "Virtual Console/NES/Graphics", name = "Graphics Settings",
            presets = arrayOf(preset("Resolution", "1920x1080 (Default)", "~~~Original NES Resolutions~~~", "256x240 (Original NES)")),
        )
        assertEquals(2, listOf(res).resolutionSetting()!!.options.size)
    }

    @Test
    fun twoResolutionPacksMerge() {
        val perf = pack("Twilight Princess HD/Graphics/Performance Resolution", name = "Resolution", presets = arrayOf(preset(null, "1280x720", "640x360")))
        val main = pack("Twilight Princess HD/Graphics/Resolution", name = "Resolution", presets = arrayOf(preset(null, "1920x1080 (Native)", "2560x1440")))
        val setting = listOf(perf, main).resolutionSetting()!!
        assertEquals(main.id, setting.mainPack.id)
        assertEquals(listOf("1920x1080 (Native)", "2560x1440", "1280x720  ·  Performance Resolution", "640x360  ·  Performance Resolution"), setting.options.map { it.label })
    }

    @Test
    fun onePackPerResolution() {
        val a = pack("Old Game/1280x720", active = true)
        val b = pack("Old Game/1920x1080")
        val setting = listOf(a, b).resolutionSetting()!!
        assertEquals(listOf("1280x720", "1920x1080"), setting.options.map { it.label })
        assertEquals(a.id, setting.selected?.packId)
        assertNull(setting.selected?.choice)
    }

    @Test
    fun noResolution() {
        val p = pack("ZombiU/Mods/60FPS", presets = arrayOf())
        val s = pack("Game/Graphics", presets = arrayOf(preset("Additional Downsampling Resolutions", "Disabled", "Enabled")))
        assertNull(listOf(p, s).resolutionSetting())
    }

    @Test
    fun fpsLimitCategory() {
        val fps = pack(
            "The Legend of Zelda: Breath of the Wild/Mods/FPS++", active = true, name = "FPS++",
            presets = arrayOf(
                preset("Mode", "Normal Settings", "Advanced Settings"),
                preset("FPS Limit", "60FPS Limit (Default)", "30FPS Limit", active = "30FPS Limit"),
                preset("Cutscene FPS Limit", "Limit all cutscenes to 60FPS", "Limit all cutscenes to 30FPS"),
            ),
        )
        val setting = listOf(fps).fpsSetting()!!
        assertEquals("FPS Limit", setting.options.first().category)
        assertEquals("30FPS Limit", setting.selected?.choice)
    }

    @Test
    fun wholeFpsPack() {
        val fps = pack("ZombiU/Mods/60FPS", name = "60FPS")
        val partial = pack("Splatoon/Mods/60FPS In Plaza")
        val workaround = pack("The Legend of Zelda: The Wind Waker HD/Workarounds/FPS Slowdown")
        val setting = listOf(fps, partial, workaround).fpsSetting()!!
        assertEquals(listOf(fps.id), setting.options.map { it.packId })
        assertNull(setting.options.single().choice)
        assertNull(setting.selected)
    }

    @Test
    fun uncappedFramerateWithoutCategory() {
        val fps = pack(
            "Darksiders Warmastered Edition/Mods/Uncapped Framerate",
            presets = arrayOf(preset(null, "60FPS (ideal for 240/120/60Hz displays)", "30FPS (ideal for 240/120/60Hz displays)")),
        )
        assertNotNull(listOf(fps).fpsSetting())
    }
}
