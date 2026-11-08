package dev.dfd.fractal.emulation.input

import android.view.KeyEvent
import android.view.MotionEvent
import dev.dfd.fractal.common.android.inputevent.isFromPhysicalController
import dev.dfd.fractal.nativeinterface.NativeInput

object InputHandler {
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!event.isFromPhysicalController()) {
            return false
        }

        val device = event.device

        NativeInput.onControllerKey(
            device.descriptor,
            event.keyCode,
            event.action == KeyEvent.ACTION_DOWN
        )

        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromPhysicalController()) {
            return false
        }

        val device = event.device
        val actionPointerIndex = event.actionIndex
        for (motionRange in device.motionRanges) {
            val axisValue = event.getAxisValue(motionRange.axis, actionPointerIndex)
            val axis = motionRange.axis
            NativeInput.onControllerAxis(device.descriptor, axis, axisValue)
        }

        return true
    }
}
