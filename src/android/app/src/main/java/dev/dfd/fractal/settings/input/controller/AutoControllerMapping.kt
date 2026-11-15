package dev.dfd.fractal.settings.input.controller

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import dev.dfd.fractal.common.android.inputdevice.listGameControllers
import dev.dfd.fractal.common.android.inputdevice.toControllerInfo
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.EmulatedControllerType

/**
 * Default mapping for gamepads nobody mapped yet, so the built-in controls of a handheld (or a
 * freshly paired pad) work without a visit to the input settings.
 *
 * Slot 0 is a Wii U GamePad unless the user or a game profile says otherwise: the on-screen controls
 * and the touch screen drive it. A connected gamepad that drives no emulated controller is mapped
 * (with [InputMapper.mapAllInputs]) as the [defaultType] the user picked:
 * - Pro Controller (the default): the first slot that is a Pro, Classic or Wiimote without physical
 *   mapping, else the first unused slot from 1 on, created as a Pro Controller. Each pad gets its own
 *   slot, and the on-screen GamePad in slot 0 stays as it is.
 * - Wii U GamePad (the behavior before the choice existed): the first slot without physical mapping,
 *   slot 0 included; other unused slots are only taken when the user already picked their type.
 * A slot with any physical mapping is never touched, even if the device it uses is not connected:
 * that is the user's (or an earlier automatic) choice, and "Reset to automatic mapping" on the
 * controller screen redoes it for the connected pad. Slots the user turned off are never used.
 *
 * The switch and the "slot 0 turned off by the user" mark live in their own SharedPreferences, read
 * synchronously from the input device callbacks of both activities (and both processes in release).
 */
object AutoControllerMapping {
    private const val PREFS_NAME = "input_auto_mapping"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_USER_DISABLED_SLOTS = "user_disabled_slots"
    private const val KEY_DEFAULT_TYPE = "default_type"
    private const val KEY_GAMECUBE_SLOTS = "gamecube_layout_slots"

    /** Emulated controller types a new gamepad can become. */
    val DEFAULT_TYPE_CHOICES = listOf(EmulatedControllerType.PRO, EmulatedControllerType.VPAD)

    data class MappedController(val controllerIndex: Int, val deviceName: String)

    /** Screens that edit mappings by hand; no automatic mapping while one is open. */
    @Volatile
    private var suspendCount = 0

    fun beginManualEditing() {
        suspendCount++
    }

    fun endManualEditing() {
        if (suspendCount > 0) suspendCount--
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Emulated controller a newly connected gamepad becomes: Pro Controller unless the user picked the GamePad. */
    fun defaultType(context: Context): Int =
        prefs(context).getInt(KEY_DEFAULT_TYPE, EmulatedControllerType.PRO)
            .takeIf { it in DEFAULT_TYPE_CHOICES } ?: EmulatedControllerType.PRO

    fun setDefaultType(context: Context, type: Int) {
        if (type in DEFAULT_TYPE_CHOICES) prefs(context).edit().putInt(KEY_DEFAULT_TYPE, type).apply()
    }

    /** Slot [controllerIndex] is a Classic Controller laid out as a GameCube controller (only how it is shown and mapped). */
    fun isGameCubeLayout(context: Context, controllerIndex: Int): Boolean =
        prefs(context).getStringSet(KEY_GAMECUBE_SLOTS, emptySet())!!.contains(controllerIndex.toString())

    fun setGameCubeLayout(context: Context, controllerIndex: Int, enabled: Boolean) {
        val slots = prefs(context).getStringSet(KEY_GAMECUBE_SLOTS, emptySet())!!.toMutableSet()
        val changed = if (enabled) slots.add(controllerIndex.toString()) else slots.remove(controllerIndex.toString())
        if (changed) prefs(context).edit().putStringSet(KEY_GAMECUBE_SLOTS, slots).apply()
    }

    private fun isSlotDisabledByUser(context: Context, controllerIndex: Int) =
        prefs(context).getStringSet(KEY_USER_DISABLED_SLOTS, emptySet())!!
            .contains(controllerIndex.toString())

    /** Called when the user picks the emulated controller type of a slot by hand. */
    fun onControllerTypeChosen(context: Context, controllerIndex: Int, controllerType: Int) {
        val slots = prefs(context).getStringSet(KEY_USER_DISABLED_SLOTS, emptySet())!!.toMutableSet()
        val key = controllerIndex.toString()
        val changed = if (controllerType == EmulatedControllerType.DISABLED) slots.add(key) else slots.remove(key)
        if (changed) {
            prefs(context).edit().putStringSet(KEY_USER_DISABLED_SLOTS, slots).apply()
        }
    }

    /** A real gamepad or joystick with buttons or sticks: not a virtual, keyboard, mouse or touch device. */
    fun isAutoMappable(device: InputDevice): Boolean {
        if (device.isVirtual || device.id < 0) {
            return false
        }
        val sources = device.sources
        val isGamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val isJoystick = sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!isGamepad && !isJoystick) {
            return false
        }
        val hasFaceButtons = device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
        val stickAxes = device.motionRanges
            .filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
            .map { it.axis }
        val hasStick = MotionEvent.AXIS_X in stickAxes && MotionEvent.AXIS_Y in stickAxes
        return hasFaceButtons || hasStick
    }

    fun listAutoMappableControllers(): List<InputDevice> =
        listGameControllers().filter(::isAutoMappable).sortedBy { it.id }

    private fun mappedDescriptors(controllerIndex: Int): Set<String> =
        NativeInput.getMappedControllerDescriptors(controllerIndex).toSet()

    /** Slot for a new gamepad and whether it was created for it; null when there is none. */
    private fun claimFreeSlot(context: Context): Pair<Int, Boolean>? =
        if (defaultType(context) == EmulatedControllerType.PRO) claimProSlot(context) else claimGamePadSlot(context)

    /** A Pro, Classic or Wiimote slot without physical controller, else a new Pro Controller from slot 1 on. */
    private fun claimProSlot(context: Context): Pair<Int, Boolean>? {
        val usable = (0..<NativeInput.MAX_CONTROLLERS).filter { !NativeInput.isControllerSetByGameProfile(it) }
        usable.firstOrNull {
            val type = NativeInput.getControllerType(it)
            type != EmulatedControllerType.DISABLED && type != EmulatedControllerType.VPAD && mappedDescriptors(it).isEmpty()
        }?.let { return it to false }
        for (index in usable) {
            if (index == 0 || isSlotDisabledByUser(context, index)) {
                continue
            }
            if (NativeInput.getControllerType(index) != EmulatedControllerType.DISABLED) {
                continue
            }
            if (NativeInput.getControllersCount().wpadCount >= NativeInput.MAX_WPAD_CONTROLLERS) {
                return null
            }
            NativeInput.setControllerType(index, EmulatedControllerType.PRO)
            if (!NativeInput.isControllerDisabled(index)) {
                return index to true
            }
        }
        return null
    }

    /** First slot without a physical controller; may create slot 0 as a Wii U GamePad. */
    private fun claimGamePadSlot(context: Context): Pair<Int, Boolean>? {
        for (index in 0..<NativeInput.MAX_CONTROLLERS) {
            if (NativeInput.isControllerSetByGameProfile(index)) {
                continue
            }
            if (NativeInput.getControllerType(index) != EmulatedControllerType.DISABLED) {
                if (mappedDescriptors(index).isEmpty()) {
                    return index to false
                }
                continue
            }
            if (index != 0 || isSlotDisabledByUser(context, index)) {
                continue
            }
            if (NativeInput.getControllersCount().vpadCount >= NativeInput.MAX_VPAD_CONTROLLERS) {
                continue
            }
            NativeInput.setControllerType(index, EmulatedControllerType.VPAD)
            if (!NativeInput.isControllerDisabled(index)) {
                return index to true
            }
        }
        return null
    }

    /**
     * Slot 0 is a Wii U GamePad unless the user or a game profile says otherwise, also with no physical
     * controller: the on-screen controls drive it on phones, and with no emulated controller the game gets no input.
     */
    private fun ensureGamePadSlot(context: Context) {
        if (NativeInput.isControllerSetByGameProfile(0) || isSlotDisabledByUser(context, 0)) {
            return
        }
        if (NativeInput.getControllerType(0) != EmulatedControllerType.DISABLED) {
            return
        }
        NativeInput.setControllerType(0, EmulatedControllerType.VPAD)
        NativeInput.saveInput(0)
    }

    /** Maps every connected gamepad that drives no emulated controller yet and saves those slots. */
    fun mapConnectedControllers(context: Context): List<MappedController> {
        if (suspendCount > 0 || !isEnabled(context)) {
            return emptyList()
        }
        ensureGamePadSlot(context)
        val devices = listAutoMappableControllers()
        if (devices.isEmpty()) {
            return emptyList()
        }

        val usedDescriptors = (0..<NativeInput.MAX_CONTROLLERS)
            .flatMap { mappedDescriptors(it) }
            .toMutableSet()
        val mapped = mutableListOf<MappedController>()

        for (device in devices) {
            if (device.descriptor in usedDescriptors) {
                continue
            }
            val (index, created) = claimFreeSlot(context) ?: break
            if (InputMapper.mapAllInputs(device.id, index) == 0) {
                if (created) {
                    NativeInput.setControllerType(index, EmulatedControllerType.DISABLED)
                }
                continue
            }
            NativeInput.saveInput(index)
            usedDescriptors += device.descriptor
            mapped += MappedController(index, device.name)
        }
        if (mapped.isNotEmpty()) {
            // the native state of a newly mapped device only becomes "connected" (and gets calibrated
            // against a resting pad) once the connected-device list is sent again
            NativeInput.setControllers(listGameControllers().map { it.toControllerInfo() }.toTypedArray())
        }
        return mapped
    }

    /**
     * Device for "Reset to automatic mapping" of [controllerIndex]: the connected pad it already uses,
     * else a connected pad no other slot uses, else the first connected pad.
     */
    fun pickDeviceForReset(controllerIndex: Int): InputDevice? {
        val devices = listAutoMappableControllers()
        val own = mappedDescriptors(controllerIndex)
        val others = (0..<NativeInput.MAX_CONTROLLERS)
            .filter { it != controllerIndex }
            .flatMap { mappedDescriptors(it) }
            .toSet()
        return devices.firstOrNull { it.descriptor in own }
            ?: devices.firstOrNull { it.descriptor !in others }
            ?: devices.firstOrNull()
    }

    fun showMappedToast(context: Context, mapped: List<MappedController>) {
        for (controller in mapped) {
            Toast.makeText(
                context,
                tr("Controller mapped automatically: {0}", controller.deviceName),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/**
 * Runs [AutoControllerMapping] while an activity is in the foreground: once on [register] (app start,
 * return from another app or game) and whenever an input device is added or changes.
 */
class AutoControllerMappingListener(private val context: Context) : InputManager.InputDeviceListener {
    private val inputManager
        get() = context.getSystemService(Context.INPUT_SERVICE) as InputManager?

    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        inputManager?.registerInputDeviceListener(this, null)
        mapNow()
    }

    fun unregister() {
        if (!registered) return
        registered = false
        inputManager?.unregisterInputDeviceListener(this)
    }

    private fun mapNow() {
        val mapped = try {
            AutoControllerMapping.mapConnectedControllers(context)
        } catch (_: Exception) {
            emptyList()
        }
        AutoControllerMapping.showMappedToast(context, mapped)
    }

    override fun onInputDeviceAdded(deviceId: Int) = mapNow()

    override fun onInputDeviceChanged(deviceId: Int) = mapNow()

    override fun onInputDeviceRemoved(deviceId: Int) {}
}
