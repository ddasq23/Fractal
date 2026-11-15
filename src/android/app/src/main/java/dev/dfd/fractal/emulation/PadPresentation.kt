package dev.dfd.fractal.emulation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// GamePad screen on a second display: the bottom screen of dual screen handhelds (AYN Thor) or a monitor/TV plugged in.
// Based on SapphireRhodonite's android-port-dual (PadPresentation, DisplayUtils)

/** The display the game is not on, or null when there is only one. Follows displays being added and removed. */
@Composable
fun rememberSecondDisplay(): Display? {
    val context = LocalContext.current
    val activity = context as? Activity ?: return null
    val displayManager = remember(activity) { activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    var secondDisplay by remember { mutableStateOf<Display?>(null) }

    DisposableEffect(displayManager, activity) {
        fun update() {
            val gameDisplayId = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
            secondDisplay = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != gameDisplayId && it.state != Display.STATE_OFF }
                ?: displayManager.displays.firstOrNull { it.displayId != gameDisplayId && it.state != Display.STATE_OFF }
        }

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = update()
            override fun onDisplayRemoved(displayId: Int) = update()
            override fun onDisplayChanged(displayId: Int) = update()
        }
        update()
        displayManager.registerDisplayListener(listener, null)
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    return secondDisplay
}

/** Full screen surface for the GamePad image on [display]; touches go to the GamePad touch screen. */
class PadPresentation(
    context: Context,
    display: Display,
    private val holderCallback: SurfaceHolder.Callback,
) : Presentation(context, display) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the game keeps the focus and the controller; the second screen only shows and takes touches
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val surfaceView = SurfaceView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            holder.addCallback(holderCallback)
            setOnTouchListener(CanvasOnTouchListener(isTV = false))
        }
        setContentView(surfaceView)
    }
}
