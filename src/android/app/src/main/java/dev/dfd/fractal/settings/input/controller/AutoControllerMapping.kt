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
 * Rule: a connected gamepad that drives no emulated controller is mapped (with [InputMapper.mapAllInputs])
 * to the first slot that has no physical controller mapped. Slot 0 is created as a Wii U GamePad if it
 * does not exist, unless the user turned it off; other slots are only used when the user already
 * picked their emulated controller type. A slot with any physical mapping is never touched, even if the
 * device it uses is not connected: that is the user's (or an earlier automatic) choice, and
 * "Reset to automatic mapping" on the controller screen redoes it for the connected pad.
 *
 * The switch and the "slot 0 turned off by the user" mark live in their own SharedPreferences, read
 * synchronously from the input device callbacks of both activities (and both processes in release).
 */
object AutoControllerMapping {
    private const val PREFS_NAME = "input_auto_mapping"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_USER_DISABLED_SLOTS = "user_disabled_slots"

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

    /** First slot without a physical controller; may create slot 0 as a Wii U GamePad. */
    private fun claimFreeSlot(context: Context): Pair<Int, Boolean>? {
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

    /** Maps every connected gamepad that drives no emulated controller yet and saves those slots. */
    fun mapConnectedControllers(context: Context): List<MappedController> {
        if (suspendCount > 0 || !isEnabled(context)) {
            return emptyList()
        }
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
