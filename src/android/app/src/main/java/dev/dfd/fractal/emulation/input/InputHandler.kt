package dev.dfd.fractal.emulation.input

import android.view.InputDevice
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
            val axis = triggerAxisFor(device, motionRange.axis)
            NativeInput.onControllerAxis(device.descriptor, axis, axisValue)
        }

        return true
    }

    /**
     * Some controllers report their analog triggers only as brake/gas; they then drive the
     * trigger axes the mappings use. With real trigger axes too, brake/gas keep their own codes.
     */
    private fun triggerAxisFor(device: InputDevice, axis: Int): Int = when (axis) {
        MotionEvent.AXIS_BRAKE ->
            if (device.getMotionRange(MotionEvent.AXIS_LTRIGGER) == null) MotionEvent.AXIS_LTRIGGER else axis
        MotionEvent.AXIS_GAS ->
            if (device.getMotionRange(MotionEvent.AXIS_RTRIGGER) == null) MotionEvent.AXIS_RTRIGGER else axis
        else -> axis
    }
}
