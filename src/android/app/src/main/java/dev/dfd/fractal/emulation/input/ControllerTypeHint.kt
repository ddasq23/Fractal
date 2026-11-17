/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.emulation.input

import android.content.Context
import android.util.Log
import android.view.InputDevice
import dev.dfd.fractal.common.android.inputdevice.listGameControllers
import dev.dfd.fractal.common.android.inputdevice.toControllerInfo
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.EmulatedControllerType
import dev.dfd.fractal.settings.input.controller.AutoControllerMapping
import dev.dfd.fractal.settings.input.controller.GameControllerType
import dev.dfd.fractal.settings.input.controller.InputMapper

/** How often the running game polled each input API since it started (NativeInput.getInputApiUsage). */
data class InputApiUsage(
    /** VPADRead calls for GamePad 1 and 2. */
    val vpadReads: List<Long>,
    /** KPADRead/KPADReadEx/WPADRead calls for WPAD channels 0..6 (Pro, Classic, Wiimote). */
    val wpadReads: List<Long>,
    /** Last WPADSetDataFormat per WPAD channel, -1 when none. */
    val wpadFormats: List<Long>,
) {
    val readsGamePad get() = vpadReads[0] > 0
    val readsWpad get() = wpadReads.any { it > 0 }

    override fun toString() =
        "VPAD ${vpadReads.joinToString("/")} · WPAD ${wpadReads.joinToString("/")} · formats ${wpadFormats.joinToString("/")}"

    companion object {
        fun read(): InputApiUsage? = runCatching {
            val v = NativeInput.getInputApiUsage()
            InputApiUsage(v.slice(0..1), v.slice(2..8), v.slice(9..15))
        }.getOrNull()
    }
}

/**
 * Points out, once per game, a controller setup the running game ignores:
 * - the game reads the Wii U GamePad, but the player's input goes only to a Pro Controller or Wiimote (a gamepad
 *   mapped automatically as a Pro Controller, the usual case, or the touch controls on such a slot). Games that only
 *   take the GamePad still poll KPAD every frame (Captain Toad: channels 0-3), so the polling can't tell them from
 *   games that take both: the hint says "if the game doesn't respond";
 * - the game never reads the GamePad (it only takes Wii Remotes or Pro Controllers), but the input goes to the GamePad.
 *
 * The fix makes controller 1 the type the game reads, with the player's gamepad mapped to it, and saves it as a
 * controller profile of this game only: the input settings of other games don't change.
 */
object ControllerTypeHint {
    private const val TAG = "ControllerTypeHint"
    private const val PREFS = "controller_type_hint"
    private const val KEY_SHOWN = "shown_titles"


    /** First check after this much time with the game running, then again later in case the game was in its menus. */
    val CHECK_DELAYS_MS = listOf(20_000L, 45_000L, 90_000L)

    enum class Fix(val controllerType: Int) {
        USE_GAMEPAD(EmulatedControllerType.VPAD),
        USE_PRO(EmulatedControllerType.PRO),
    }

    data class Suggestion(val fix: Fix, val message: String, val action: String, val device: InputDevice?)

    fun wasShown(context: Context, titleId: Long): Boolean =
        prefs(context).getStringSet(KEY_SHOWN, emptySet())!!.contains("%016x".format(titleId))

    fun markShown(context: Context, titleId: Long) {
        val set = prefs(context).getStringSet(KEY_SHOWN, emptySet())!!.toMutableSet()
        if (set.add("%016x".format(titleId))) prefs(context).edit().putStringSet(KEY_SHOWN, set).apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isWpadType(type: Int) =
        type == EmulatedControllerType.PRO || type == EmulatedControllerType.CLASSIC || type == EmulatedControllerType.WIIMOTE

    /**
     * What to suggest now, or null when the setup fits the game (or it is too early to tell).
     * [overlaySlot] is the controller the touch controls drive, null when they are hidden.
     */
    fun check(usage: InputApiUsage, overlaySlot: Int?): Suggestion? {
        Log.i(TAG, "input API usage: $usage")
        // the game's profile already says what player 1 is: the user's choice
        if (NativeInput.isControllerSetByGameProfile(0)) return null
        val types = (0..<NativeInput.MAX_CONTROLLERS).map {
            if (NativeInput.isControllerDisabled(it)) EmulatedControllerType.DISABLED else NativeInput.getControllerType(it)
        }
        val connected = AutoControllerMapping.listAutoMappableControllers()
        // the slots each connected gamepad drives
        val padSlots = connected.associateWith { device ->
            (0..<NativeInput.MAX_CONTROLLERS).filter { device.descriptor in NativeInput.getMappedControllerDescriptors(it) }
        }
        val padOnGamePad = padSlots.values.any { slots -> slots.any { types[it] == EmulatedControllerType.VPAD } }
        val padOnWpad = padSlots.values.any { slots -> slots.any { isWpadType(types[it]) } }
        val overlayOnGamePad = overlaySlot != null && types.getOrNull(overlaySlot) == EmulatedControllerType.VPAD
        val overlayOnWpad = overlaySlot != null && isWpadType(types.getOrElse(overlaySlot) { EmulatedControllerType.DISABLED })

        if (usage.readsGamePad && !padOnGamePad && (padOnWpad || overlayOnWpad)) {
            val device = padSlots.entries.firstOrNull { (_, slots) -> slots.any { isWpadType(types[it]) } }?.key
            return Suggestion(
                Fix.USE_GAMEPAD,
                if (device != null) tr("This game reads the Wii U GamePad, and {0} is set up as a Pro Controller. If the game doesn't respond to it, use it as the GamePad.", device.name)
                else tr("This game reads the Wii U GamePad, and the touch controls drive a Pro Controller. If the game doesn't respond to them, use them as the GamePad."),
                tr("Use as GamePad"),
                device,
            )
        }
        if (!usage.readsGamePad && usage.readsWpad && !padOnWpad && (padOnGamePad || overlayOnGamePad)) {
            val device = padSlots.entries.firstOrNull { (_, slots) -> 0 in slots }?.key
            return Suggestion(
                Fix.USE_PRO,
                tr("This game doesn't read the Wii U GamePad: it takes a Pro Controller or Wii Remote."),
                tr("Use Pro Controller"),
                device,
            )
        }
        return null
    }

    /**
     * Makes controller 1 the type of [suggestion] with its gamepad mapped (the touch controls follow the type), saves
     * it as a profile and sets it in this game's profile, also for the running game. Returns false when it failed.
     */
    fun apply(context: Context, suggestion: Suggestion, titleId: Long): Boolean = try {
        val slot = 0
        val profile = GameControllerType.set(titleId, suggestion.fix.controllerType).getOrThrow()!!
        check(NativeInput.useControllerProfileInRunningGame(slot, profile)) { "can't load $profile" }
        // the running game gets the gamepad now; at the next start the automatic mapping puts it there
        val device = suggestion.device ?: AutoControllerMapping.listAutoMappableControllers().firstOrNull()
        if (device != null) {
            InputMapper.mapAllInputs(device.id, slot, AutoControllerMapping.defaultLayout(context))
            // the connected-device list again, so the newly mapped device counts as connected (like the automatic mapping)
            NativeInput.setControllers(listGameControllers().map { it.toControllerInfo() }.toTypedArray())
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "can't apply ${suggestion.fix}", e)
        false
    }
}
