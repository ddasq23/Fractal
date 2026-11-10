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

/** Key of the target in [dev.dfd.fractal.common.settings.OverlayLayoutProfile.styles]. */
val OverlayTarget.styleKey: String
    get() = when (this) {
        is OverlayTarget.Builtin -> styleKey(config)
        is OverlayTarget.Macro -> id
        is OverlayTarget.Shortcut -> id
    }

/** Positions changed in one edit session, to be merged into the active profile of [controllerType]. */
data class OverlayEditResult(
    val controllerType: OverlayControllerType,
    val rects: Map<OverlayInputConfig, NormalizedRect>,
    val macroRects: Map<String, NormalizedRect>,
) {
    val isEmpty: Boolean get() = rects.isEmpty() && macroRects.isEmpty()
}

/** Lets Compose UI talk to the overlay view: edit commands, live previews, controller type. */
@Stable
class InputOverlayController {
    internal var view: InputOverlaySurfaceView? = null

    /** Emulated controller type the overlay shows, null while unknown or disabled. */
    var controllerType by mutableStateOf<OverlayControllerType?>(null)
        internal set

    /** Saves the moves and resizes made so far, e.g. before switching profiles. */
    fun commitEdits() {
        view?.commitEdits()
    }

    /** Throws away moves and resizes not saved yet. */
    fun discardEdits() {
        view?.discardEdits()
    }

    /** Shows the default layout right away; the caller resets the saved one. */
    fun showDefaultLayout() {
        view?.showDefaultLayout()
    }

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
