package dev.dfd.fractal.emulation.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.VibrationEffect
import android.view.InputDevice
import dev.dfd.fractal.common.android.inputdevice.listGameControllers
import dev.dfd.fractal.common.android.inputdevice.tryUseVibrator
import dev.dfd.fractal.common.input.InputDeviceListener
import dev.dfd.fractal.nativeinterface.NativeInput

class ControllerCallbacks(private val context: Context) : NativeInput.ControllerCallbacks {
    private val inputManager
        get() = context.getSystemService(Context.INPUT_SERVICE) as InputManager?

    private var inputs = mapOf<String, InputDevice>()

    private fun refreshControllers() {
        synchronized(inputs) {
            inputs = listGameControllers().associateBy { it.descriptor }
        }
    }

    private val inputDeviceListener = object : InputDeviceListener {
        override fun onInputDeviceChanged() = refreshControllers()
    }

    fun register() {
        inputManager?.registerInputDeviceListener(inputDeviceListener, null)
        refreshControllers()
        NativeInput.setControllerCallbacks(this)
    }

    fun unregister() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        NativeInput.setControllerCallbacks(null)
    }


    override fun vibrateController(
        descriptor: String, milliseconds: Long, amplitude: Int
    ) {
        synchronized(inputs) {
            inputs[descriptor]?.tryUseVibrator {
                vibrate(
                    VibrationEffect.createOneShot(
                        milliseconds, amplitude.coerceIn(1, 255)
                    )
                )
            }
        }
    }

    override fun cancelControllerVibration(descriptor: String) {
        synchronized(inputs) {
            inputs[descriptor]?.tryUseVibrator { cancel() }
        }
    }
}