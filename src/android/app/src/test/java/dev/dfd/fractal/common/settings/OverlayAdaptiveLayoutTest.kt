package dev.dfd.fractal.common.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class OverlayAdaptiveLayoutTest {
    private data class Screen(val name: String, val w: Float, val h: Float, val insets: LayoutInsets = LayoutInsets.NONE)

    private val landscapeScreens = listOf(
        Screen("16:9 small phone", 640f, 360f),
        Screen("16:9 phone", 732f, 412f),
        Screen("19.5:9 phone", 851f, 393f),
        Screen("20:9 phone", 873f, 393f),
        Screen("20:9 phone, cutout", 873f, 393f, LayoutInsets(left = 32f)),
        Screen("21:9 phone", 960f, 411f),
        Screen("24:9 phone", 1024f, 384f),
        Screen("5:3 handheld", 800f, 480f),
        Screen("16:10 tablet", 1280f, 800f),
        Screen("4:3 tablet", 1024f, 768f),
        Screen("3:2 tablet", 1080f, 720f),
        Screen("1:1 RG Cube xhdpi", 360f, 360f),
        Screen("1:1 RG Cube hdpi", 480f, 480f),
    )

    private val portraitScreens = listOf(
        Screen("9:20 phone", 393f, 873f),
        Screen("9:21 phone", 411f, 960f),
        Screen("9:16 small phone", 360f, 640f),
        Screen("3:4 tablet", 768f, 1024f),
        Screen("10:16 tablet", 800f, 1280f),
    )

    private fun layout(screen: Screen, type: OverlayControllerType, sizeScale: Float = 1f) =
        computeAdaptiveLayout(
            OverlayLayoutSpec(
                widthDp = screen.w,
                heightDp = screen.h,
                controllerType = type,
                density = 2.75f,
                insets = screen.insets,
                sizeScale = sizeScale,
            )
        )

    private fun AdaptiveOverlayLayout.describe() =
        "${spec.widthDp.toInt()}x${spec.heightDp.toInt()} ${spec.controllerType}: unit=%.2f %s/%s/%s".format(
            Locale.ROOT, unit, arrangement, shoulders, center,
        )

    private fun assertSound(layout: AdaptiveOverlayLayout) {
        val name = layout.describe()
        // a smaller size setting is the user's choice
        val sizeFactor = minOf(1f, layout.spec.sizeScale)
        assertTrue("$name does not fit", layout.fits)
        val expected = overlayInputsFor(layout.spec.controllerType).toSet()
        assertEquals("$name places every input", expected, layout.boxes.keys)
        val margin = 4f
        val safe = layout.safeArea
        for ((config, box) in layout.boxes) {
            assertTrue("$name: $config $box leaves the safe area $safe", box.left >= safe.left + margin - 0.01f)
            assertTrue("$name: $config $box leaves the safe area $safe", box.top >= safe.top + margin - 0.01f)
            assertTrue("$name: $config $box leaves the safe area $safe", box.right <= safe.right - margin + 0.01f)
            assertTrue("$name: $config $box leaves the safe area $safe", box.bottom <= safe.bottom - margin + 0.01f)
        }
        val shapes = layout.shapes.entries.toList()
        for (i in shapes.indices) {
            for (j in i + 1 until shapes.size) {
                val distance = shapes[i].value.distanceTo(shapes[j].value)
                assertTrue(
                    "$name: ${shapes[i].key} and ${shapes[j].key} are $distance dp apart",
                    distance >= 4f,
                )
            }
        }
        // comfortable physical sizes, whatever the screen
        val face = layout.boxes.getValue(OverlayInputConfig.BUTTON_A)
        assertTrue("$name: face buttons ${face.width} dp", face.width >= 40f * sizeFactor)
        val stick = layout.boxes.getValue(OverlayInputConfig.JOYSTICK_RIGHT)
        assertTrue("$name: stick ${stick.width} dp", stick.width >= 96f * sizeFactor)
        val dpad = layout.boxes.getValue(OverlayInputConfig.DPAD)
        assertTrue("$name: d-pad ${dpad.width} dp", dpad.width >= 90f * sizeFactor)
    }

    @Test
    fun landscapeLayoutsFitWithoutOverlaps() {
        for (screen in landscapeScreens) {
            for (type in OverlayControllerType.entries) {
                val layout = layout(screen, type)
                println(
                    "${screen.name}: ${layout.describe()} stick=${layout.boxes.getValue(OverlayInputConfig.JOYSTICK_RIGHT).width.toInt()}dp" +
                        " A=${layout.boxes.getValue(OverlayInputConfig.BUTTON_A).width.toInt()}dp free=${layout.extraSlotCount}"
                )
                assertSound(layout)
                assertFalse(layout.isPortrait)
            }
        }
    }

    @Test
    fun portraitLayoutsFitBelowTheGame() {
        for (screen in portraitScreens) {
            for (type in OverlayControllerType.entries) {
                val layout = layout(screen, type)
                println(
                    "${screen.name}: ${layout.describe()} stick=${layout.boxes.getValue(OverlayInputConfig.JOYSTICK_RIGHT).width.toInt()}dp" +
                        " A=${layout.boxes.getValue(OverlayInputConfig.BUTTON_A).width.toInt()}dp free=${layout.extraSlotCount}"
                )
                assertSound(layout)
                assertTrue(layout.isPortrait)
                val gameBottom = screen.w * 9f / 16f
                for ((config, box) in layout.boxes) {
                    assertTrue("${screen.name}: $config covers the game ($box)", box.top >= gameBottom)
                }
            }
        }
    }

    @Test
    fun landscapeKeepsTheMiddleOfTheImageClear() {
        for (screen in landscapeScreens.filter { it.w / it.h >= 1.3f }) {
            for (type in OverlayControllerType.entries) {
                val layout = layout(screen, type)
                val middle = LayoutShape(LayoutBox(screen.w * 0.3f, screen.h * 0.28f, screen.w * 0.7f, screen.h * 0.6f), round = false)
                for ((config, shape) in layout.shapes) {
                    assertTrue("${screen.name}: $config covers the middle of the image", shape.distanceTo(middle) > 0f)
                }
            }
        }
    }

    @Test
    fun controlsSitOnTheirSide() {
        for (screen in landscapeScreens + portraitScreens) {
            val layout = layout(screen, OverlayControllerType.VPAD)
            val b = layout.boxes
            val midX = screen.w / 2f
            assertTrue(screen.name, b.getValue(OverlayInputConfig.JOYSTICK_LEFT).centerX < midX)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.DPAD).centerX < midX)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.BUTTON_ZL).centerX < midX)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.BUTTON_A).centerX > midX)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.JOYSTICK_RIGHT).centerX > midX)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.BUTTON_ZR).centerX > midX)
            // ABXY diamond: A right of Y, X above B
            assertTrue(b.getValue(OverlayInputConfig.BUTTON_A).centerX > b.getValue(OverlayInputConfig.BUTTON_Y).centerX)
            assertTrue(b.getValue(OverlayInputConfig.BUTTON_X).centerY < b.getValue(OverlayInputConfig.BUTTON_B).centerY)
            // shoulders above the sticks
            assertTrue(b.getValue(OverlayInputConfig.BUTTON_L).bottom < b.getValue(OverlayInputConfig.JOYSTICK_LEFT).top)
            assertTrue(b.getValue(OverlayInputConfig.BUTTON_R).bottom < b.getValue(OverlayInputConfig.BUTTON_X).top)
            // sticks and d-pad in the lower half of the control area
            val area = layout.controlArea
            assertTrue(screen.name, b.getValue(OverlayInputConfig.DPAD).centerY > area.centerY)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.JOYSTICK_RIGHT).centerY > area.centerY)
        }
    }

    @Test
    fun wiimoteNunchukStickIsOnTheLeft() {
        for (screen in landscapeScreens + portraitScreens) {
            val b = layout(screen, OverlayControllerType.WIIMOTE).boxes
            assertTrue(screen.name, b.getValue(OverlayInputConfig.JOYSTICK_RIGHT).centerX < screen.w / 2f)
            assertTrue(screen.name, b.getValue(OverlayInputConfig.BUTTON_Z).centerX < screen.w / 2f)
        }
    }

    @Test
    fun cutoutInsetsAreRespected() {
        val layout = layout(Screen("cutout", 873f, 393f, LayoutInsets(left = 40f, right = 24f)), OverlayControllerType.PRO)
        assertSound(layout)
        assertTrue(layout.boxes.values.all { it.left >= 40f && it.right <= 873f - 24f })
    }

    @Test
    fun leftShouldersGoBelowTheStatsBox() {
        // the FPS/CPU/RAM box of the emulator, three lines tall, in the top left corner
        val reserve = 50f
        for (screen in landscapeScreens) {
            for (type in listOf(OverlayControllerType.VPAD, OverlayControllerType.PRO, OverlayControllerType.CLASSIC)) {
                val layout = computeAdaptiveLayout(
                    OverlayLayoutSpec(
                        widthDp = screen.w,
                        heightDp = screen.h,
                        controllerType = type,
                        density = 2.75f,
                        insets = screen.insets,
                        reservedTopLeftDp = reserve,
                    )
                )
                assertSound(layout)
                for (config in listOf(OverlayInputConfig.BUTTON_ZL, OverlayInputConfig.BUTTON_L)) {
                    assertTrue("${layout.describe()}: $config under the stats box", layout.boxes.getValue(config).top >= reserve)
                }
            }
        }
    }

    @Test
    fun biggerSizeSettingStillFits() {
        for (screen in listOf(landscapeScreens[0], landscapeScreens[3], landscapeScreens[9], portraitScreens[0])) {
            val normal = layout(screen, OverlayControllerType.VPAD)
            val big = layout(screen, OverlayControllerType.VPAD, sizeScale = 1.4f)
            assertSound(big)
            assertTrue(
                "${screen.name}: bigger setting makes bigger controls",
                big.boxes.getValue(OverlayInputConfig.BUTTON_A).width >= normal.boxes.getValue(OverlayInputConfig.BUTTON_A).width,
            )
            val small = layout(screen, OverlayControllerType.VPAD, sizeScale = 0.7f)
            assertSound(small)
        }
    }

    @Test
    fun tabletsGetBiggerControlsThanPhonesButNotHuge() {
        val phone = layout(landscapeScreens[3], OverlayControllerType.VPAD)
        val tablet = layout(landscapeScreens[8], OverlayControllerType.VPAD)
        val phoneA = phone.boxes.getValue(OverlayInputConfig.BUTTON_A).width
        val tabletA = tablet.boxes.getValue(OverlayInputConfig.BUTTON_A).width
        assertTrue(tabletA > phoneA)
        assertTrue(tabletA < phoneA * 1.5f)
    }

    @Test
    fun extraSlotsAreFreeAndDistinct() {
        for (screen in landscapeScreens + portraitScreens) {
            for (type in OverlayControllerType.entries) {
                val layout = layout(screen, type)
                // square screens have little room left, the portrait band is compact; elsewhere six buttons fit
                val expected = when {
                    screen.w == screen.h -> 2
                    screen.h > screen.w -> 4
                    else -> 6
                }
                assertTrue("${screen.name} $type has ${layout.extraSlotCount} free slots", layout.extraSlotCount >= expected)
                val slots = (0 until expected).map { layout.extraSlot(it) }
                val all = layout.shapes.values + slots.map { LayoutShape(it, false) }
                for (slot in slots) {
                    assertTrue(slot.isInside(layout.safeArea))
                    val shape = LayoutShape(slot, false)
                    for (other in all) {
                        if (other.box == slot) continue
                        assertTrue("${screen.name} $type: slot $slot overlaps ${other.box}", shape.distanceTo(other) >= 4f)
                    }
                }
            }
        }
    }

    @Test
    fun normalizedRectsRoundTrip() {
        val screen = landscapeScreens[3]
        val layout = layout(screen, OverlayControllerType.VPAD, sizeScale = 1.2f)
        val box = layout.boxes.getValue(OverlayInputConfig.BUTTON_A)
        assertNotNull(layout.normalized(OverlayInputConfig.BUTTON_A))
        val rect = layout.normalized(box)
        val shortSide = screen.h
        assertEquals(box.centerX, rect.centerX * screen.w, 0.01f)
        assertEquals(box.centerY, rect.centerY * screen.h, 0.01f)
        // the overlay multiplies by the size setting again when drawing
        assertEquals(box.width, rect.width * shortSide * 1.2f, 0.01f)
    }

    @Test
    fun distanceBetweenShapes() {
        val a = LayoutShape(LayoutBox(0f, 0f, 10f, 10f), round = true)
        val b = LayoutShape(LayoutBox(20f, 0f, 30f, 10f), round = true)
        assertEquals(10f, a.distanceTo(b), 0.001f)
        val r = LayoutShape(LayoutBox(12f, 0f, 20f, 10f), round = false)
        assertEquals(2f, a.distanceTo(r), 0.001f)
        val overlap = LayoutShape(LayoutBox(5f, 5f, 15f, 15f), round = false)
        assertTrue(r.distanceTo(overlap) < 0f)
    }

    /** Set OVERLAY_LAYOUT_SVG_DIR to get a picture of every layout, to look at them. */
    @Test
    fun dumpSvgWhenAsked() {
        val dir = System.getenv("OVERLAY_LAYOUT_SVG_DIR") ?: return
        File(dir).mkdirs()
        for (screen in landscapeScreens + portraitScreens) {
            for (type in OverlayControllerType.entries) {
                val layout = layout(screen, type)
                val svg = StringBuilder()
                svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='${screen.w}' height='${screen.h}'>")
                svg.append("<rect width='100%' height='100%' fill='#202830'/>")
                val gameW = if (layout.isPortrait) screen.w else minOf(screen.w, screen.h * 16f / 9f)
                val gameH = gameW * 9f / 16f
                val gameX = (screen.w - gameW) / 2f
                val gameY = if (layout.isPortrait) 0f else (screen.h - gameH) / 2f
                svg.append("<rect x='$gameX' y='$gameY' width='$gameW' height='$gameH' fill='#3a5a40'/>")
                for (i in 0 until 6) {
                    val b = layout.extraSlot(i)
                    svg.append("<rect x='${b.left}' y='${b.top}' width='${b.width}' height='${b.height}' rx='8' fill='none' stroke='#ffcc00' stroke-dasharray='4'/>")
                }
                for ((config, shape) in layout.shapes) {
                    val b = shape.box
                    if (shape.round) {
                        svg.append("<circle cx='${b.centerX}' cy='${b.centerY}' r='${b.width / 2}' fill='#ffffff55' stroke='white'/>")
                    } else {
                        svg.append("<rect x='${b.left}' y='${b.top}' width='${b.width}' height='${b.height}' rx='10' fill='#ffffff55' stroke='white'/>")
                    }
                    val label = config.name.removePrefix("BUTTON_").removePrefix("JOYSTICK_")
                    svg.append("<text x='${b.centerX}' y='${b.centerY + 4}' font-size='11' text-anchor='middle' fill='black'>$label</text>")
                }
                svg.append("</svg>")
                val file = "${screen.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_$type.svg"
                File(dir, file).writeText(svg.toString())
            }
        }
    }
}
