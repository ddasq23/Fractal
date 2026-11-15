package dev.dfd.fractal.emulation.inputoverlay

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayInputStyle
import dev.dfd.fractal.common.settings.styleKey

/** An input of the overlay as the edit UI refers to it. */
sealed interface OverlayTarget {
    data class Builtin(val config: OverlayInputConfig) : OverlayTarget
    data class Macro(val id: String) : OverlayTarget
    data class Shortcut(val id: String) : OverlayTarget
}

/** Key of the target in [dev.dfd.fractal.common.settings.OverlayLayoutProfile.styles] and saved positions. */
val OverlayTarget.styleKey: String
    get() = when (this) {
        is OverlayTarget.Builtin -> styleKey(config)
        is OverlayTarget.Macro -> id
        is OverlayTarget.Shortcut -> id
    }

/** Lets Compose UI talk to the overlay view: selection, live previews, controller type. */
@Stable
class InputOverlayController {
    internal var view: InputOverlayView? = null

    /** Emulated controller type the overlay shows, null while unknown or disabled. */
    var controllerType by mutableStateOf<OverlayControllerType?>(null)
        internal set

    /** The overlay is taller than wide: edits go to the portrait layout. */
    var isPortrait by mutableStateOf(false)
        internal set

    /** Input selected in edit mode. */
    var selected by mutableStateOf<OverlayTarget?>(null)
        private set

    /** The selected input is on the left half: panels go to the right. */
    var selectedOnLeft by mutableStateOf(false)
        private set

    internal fun onSelection(target: OverlayTarget?, onLeft: Boolean) {
        selected = target
        if (target != null) selectedOnLeft = onLeft
    }

    /** Selects [target] on the overlay (null clears the selection). */
    fun select(target: OverlayTarget?) {
        val view = view
        if (view == null) {
            selected = target
        } else {
            view.selectTarget(target)
        }
    }

    /** Where [target] is now, in the saved form. */
    fun currentLayoutOf(target: OverlayTarget): NormalizedRect? = view?.currentLayoutOf(target)

    /** Applies opacity/size without saving, while a slider is dragged. Null keeps the saved value. */
    fun previewAppearance(alpha: Int? = null, scale: Float? = null) {
        view?.previewAppearance(alpha, scale)
    }

    /** Applies opacity/size of one input without saving, while its sliders are dragged. */
    fun previewInputStyle(target: OverlayTarget, style: OverlayInputStyle) {
        view?.previewInputStyle(target.styleKey, style)
    }

    /** Rebuilds the inputs, e.g. after the emulated controller type changed. */
    fun rebuild() {
        view?.rebuild()
    }
}
