package dev.dfd.fractal.common.input

import android.view.KeyEvent
import dev.dfd.fractal.common.settings.ButtonBehavior
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.MacroStep
import dev.dfd.fractal.common.settings.OverlayMacroInput
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.EmulatedControllerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalMacroTest {
    private val a = OverlayMacroInput.A
    private val b = OverlayMacroInput.B

    private fun combo(vararg buttons: OverlayMacroInput, behavior: ButtonBehavior = ButtonBehavior()) =
        PhysicalMacro(trigger = setOf(KeyEvent.KEYCODE_BUTTON_L1), buttons = buttons.toList(), behavior = behavior)

    private fun sequence(vararg steps: MacroStep, repeat: Boolean = false) = PhysicalMacro(
        trigger = setOf(KeyEvent.KEYCODE_BUTTON_L1),
        kind = MacroKind.SEQUENCE,
        steps = steps.toList(),
        behavior = ButtonBehavior(turbo = repeat),
    )

    @Test
    fun comboHoldsItsButtonsWhileTheTriggerIsHeld() {
        val playback = MacroPlayback(combo(a, b))
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(0))
        playback.onTrigger(true, 0)
        assertEquals(listOf(a, b), playback.buttonsAt(500))
        assertFalse(playback.needsTicks)
        playback.onTrigger(false, 600)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(600))
    }

    @Test
    fun turboPressesAndReleasesAtItsRate() {
        // 10 presses a second: 50 ms down, 50 ms up
        val playback = MacroPlayback(combo(a, behavior = ButtonBehavior(turbo = true, turboHz = 10)))
        playback.onTrigger(true, 1000)
        assertTrue(playback.needsTicks)
        assertEquals(listOf(a), playback.buttonsAt(1000))
        assertEquals(listOf(a), playback.buttonsAt(1049))
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(1050))
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(1099))
        assertEquals(listOf(a), playback.buttonsAt(1100))
        playback.onTrigger(false, 1120)
        assertFalse(playback.needsTicks)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(1120))
    }

    @Test
    fun toggleLatchesUntilTheNextPress() {
        val playback = MacroPlayback(combo(a, behavior = ButtonBehavior(toggle = true)))
        playback.onTrigger(true, 0)
        playback.onTrigger(false, 50)
        assertEquals(listOf(a), playback.buttonsAt(1000))
        playback.onTrigger(true, 1100)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(1100))
        playback.onTrigger(false, 1150)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(2000))
    }

    @Test
    fun sequencePlaysOnceToTheEndEvenIfReleased() {
        val playback = MacroPlayback(sequence(MacroStep(listOf(a), 100), MacroStep(emptyList(), 50), MacroStep(listOf(b), 100)))
        playback.onTrigger(true, 0)
        playback.onTrigger(false, 10)
        assertEquals(listOf(a), playback.buttonsAt(99))
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(120))
        assertEquals(listOf(b), playback.buttonsAt(150))
        assertTrue(playback.needsTicks)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(250))
        assertFalse(playback.needsTicks)
    }

    @Test
    fun repeatingSequenceLoopsWhileHeldAndStopsOnRelease() {
        val playback = MacroPlayback(sequence(MacroStep(listOf(a), 100), MacroStep(listOf(b), 100), repeat = true))
        playback.onTrigger(true, 0)
        assertEquals(listOf(b), playback.buttonsAt(150))
        assertEquals(listOf(a), playback.buttonsAt(250))
        playback.onTrigger(false, 260)
        assertFalse(playback.needsTicks)
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(270))
    }

    @Test
    fun stepDurationsAreClamped() {
        val playback = MacroPlayback(sequence(MacroStep(listOf(a), 0), MacroStep(listOf(b), 100)))
        playback.onTrigger(true, 0)
        assertEquals(listOf(a), playback.buttonsAt(MacroStep.MIN_STEP_MS - 1L))
        assertEquals(listOf(b), playback.buttonsAt(MacroStep.MIN_STEP_MS.toLong()))
    }

    @Test
    fun releaseLetsGoOfEverything() {
        val playback = MacroPlayback(combo(a, behavior = ButtonBehavior(toggle = true)))
        playback.onTrigger(true, 0)
        playback.release()
        assertEquals(emptyList<OverlayMacroInput>(), playback.buttonsAt(10))
        // the next press starts it again
        playback.onTrigger(true, 20)
        assertEquals(listOf(a), playback.buttonsAt(30))
    }

    @Test
    fun triggerHeldOnOneControllerOrTheChosenOne() {
        val macro = PhysicalMacro(trigger = setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_A))
        val split = mapOf("pad1" to setOf(KeyEvent.KEYCODE_BUTTON_L1), "pad2" to setOf(KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(macro.isTriggerHeld(split))
        val both = mapOf("pad2" to setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(macro.isTriggerHeld(both))
        assertFalse(macro.copy(deviceDescriptor = "pad1").isTriggerHeld(both))
        assertTrue(macro.copy(deviceDescriptor = "pad2").isTriggerHeld(both))
        assertFalse(macro.takesTriggerKey)
    }

    @Test
    fun onlyOneKeyTriggersTakeTheKey() {
        val macro = PhysicalMacro(trigger = setOf(KeyEvent.KEYCODE_BUTTON_C), deviceDescriptor = "pad1")
        assertTrue(macro.triggerMatches("pad1", KeyEvent.KEYCODE_BUTTON_C))
        assertFalse(macro.triggerMatches("pad2", KeyEvent.KEYCODE_BUTTON_C))
        assertFalse(macro.triggerMatches("pad1", KeyEvent.KEYCODE_BUTTON_A))
        val comboTrigger = PhysicalMacro(trigger = setOf(KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_A))
        assertFalse(comboTrigger.triggerMatches("pad1", KeyEvent.KEYCODE_BUTTON_C))
    }

    @Test
    fun usableNeedsTriggerAndSomethingToDo() {
        assertFalse(PhysicalMacro(buttons = listOf(a)).isUsable)
        assertFalse(PhysicalMacro(trigger = setOf(1)).isUsable)
        assertTrue(PhysicalMacro(trigger = setOf(1), buttons = listOf(a)).isUsable)
        assertFalse(PhysicalMacro(trigger = setOf(1), buttons = listOf(a), enabled = false).isUsable)
        assertTrue(PhysicalMacro(trigger = setOf(1), kind = MacroKind.SEQUENCE, steps = listOf(MacroStep())).isUsable)
    }

    @Test
    fun recorderReturnsTheCombinationOnceAllKeysAreUp() {
        val recorder = TriggerRecorder()
        assertNull(recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_L1, true))
        assertNull(recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_A, true))
        // another controller is ignored once recording started
        assertNull(recorder.onKey("pad2", KeyEvent.KEYCODE_BUTTON_B, true))
        assertNull(recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_A, false))
        assertEquals(
            setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_A),
            recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_L1, false),
        )
        assertEquals("pad1", recorder.deviceDescriptor)
    }

    @Test
    fun recorderIgnoresAReleaseBeforeAnyPress() {
        val recorder = TriggerRecorder()
        assertNull(recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_A, false))
        assertNull(recorder.deviceDescriptor)
        assertNull(recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_B, true))
        assertEquals(setOf(KeyEvent.KEYCODE_BUTTON_B), recorder.onKey("pad1", KeyEvent.KEYCODE_BUTTON_B, false))
    }

    @Test
    fun analogTriggersAndHatCountAsKeys() {
        assertEquals(emptySet<Int>(), analogVirtualKeys(0.2f, 0f, 0f, 0f))
        assertEquals(
            setOf(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP),
            analogVirtualKeys(0.9f, 0.1f, 1f, -1f),
        )
        assertEquals(setOf(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_DPAD_DOWN), analogVirtualKeys(0f, 1f, 0f, 1f))
    }

    @Test
    fun macroButtonsMapToEachControllerType() {
        assertEquals(NativeInput.ProButton.HOME, macroButtonToNative(EmulatedControllerType.PRO, OverlayMacroInput.HOME))
        assertEquals(NativeInput.VPADButton.MIC, macroButtonToNative(EmulatedControllerType.VPAD, OverlayMacroInput.BLOW_MIC))
        assertEquals(-1, macroButtonToNative(EmulatedControllerType.PRO, OverlayMacroInput.BLOW_MIC))
        assertEquals(NativeInput.WiimoteButton.NUNCHUCK_Z, macroButtonToNative(EmulatedControllerType.WIIMOTE, OverlayMacroInput.Z))
        assertEquals(-1, macroButtonToNative(EmulatedControllerType.WIIMOTE, OverlayMacroInput.X))
        assertEquals(-1, macroButtonToNative(EmulatedControllerType.CLASSIC, OverlayMacroInput.L_STICK_CLICK))
        assertEquals(-1, macroButtonToNative(EmulatedControllerType.DISABLED, OverlayMacroInput.A))
        assertTrue(OverlayMacroInput.ONE in macroButtonsFor(EmulatedControllerType.WIIMOTE))
        assertFalse(OverlayMacroInput.ONE in macroButtonsFor(EmulatedControllerType.PRO))
    }
}
