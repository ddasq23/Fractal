package dev.dfd.fractal.common.settings

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Emulated controller types that have their own overlay layouts. */
@Serializable
enum class OverlayControllerType {
    VPAD,
    PRO,
    CLASSIC,
    WIIMOTE;

    companion object {
        /** Maps NativeInput.EmulatedControllerType values; null for a disabled controller. */
        fun fromNative(type: Int): OverlayControllerType? = when (type) {
            0 -> VPAD
            1 -> PRO
            2 -> CLASSIC
            3 -> WIIMOTE
            else -> null
        }
    }
}

/**
 * Position and size of an overlay input, independent of the surface resolution.
 * The center is a fraction of the surface width/height; the size is a fraction of the
 * surface's shorter side, so inputs keep their aspect ratio when the surface changes.
 */
@Serializable
data class NormalizedRect(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    companion object {
        fun fromPixels(left: Int, top: Int, right: Int, bottom: Int, surfaceWidth: Int, surfaceHeight: Int): NormalizedRect {
            val w = max(surfaceWidth, 1).toFloat()
            val h = max(surfaceHeight, 1).toFloat()
            val shortSide = min(w, h)
            return NormalizedRect(
                centerX = (left + right) * 0.5f / w,
                centerY = (top + bottom) * 0.5f / h,
                width = (right - left) / shortSide,
                height = (bottom - top) / shortSide,
            )
        }
    }
}

const val TURBO_MIN_HZ = 5
const val TURBO_MAX_HZ = 30
const val DEFAULT_TURBO_HZ = 12

/**
 * What a touch on an overlay button does.
 * [toggle]: one tap latches the button pressed, the next tap releases it.
 * [turbo]: while held (or latched) the button is pressed and released [turboHz] times a second.
 */
@Serializable
data class ButtonBehavior(
    val toggle: Boolean = false,
    val turbo: Boolean = false,
    val turboHz: Int = DEFAULT_TURBO_HZ,
) {
    val isDefault: Boolean get() = !toggle && !turbo
}

/** Buttons a macro can press. Stick axes are left out: they belong to the joysticks. */
@Serializable
enum class OverlayMacroInput {
    A, B, X, Y, ONE, TWO, C, Z, HOME, L, R, ZL, ZR, PLUS, MINUS,
    L_STICK_CLICK, R_STICK_CLICK, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, BLOW_MIC,
}

@Serializable
enum class MacroKind {
    /** Presses all [OverlayMacro.buttons] together while the macro button is active. */
    COMBO,

    /** Plays [OverlayMacro.steps] one after the other. */
    SEQUENCE,
}

@Serializable
data class MacroStep(
    val buttons: List<OverlayMacroInput> = emptyList(),
    val durationMs: Int = DEFAULT_STEP_MS,
) {
    companion object {
        const val DEFAULT_STEP_MS = 100
        const val MIN_STEP_MS = 16
        const val MAX_STEP_MS = 10_000
    }
}

/**
 * A user made overlay button. For [MacroKind.SEQUENCE], [ButtonBehavior.turbo] means
 * "repeat the sequence while active"; without it the sequence plays once per press.
 */
@Serializable
data class OverlayMacro(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val kind: MacroKind = MacroKind.COMBO,
    val buttons: List<OverlayMacroInput> = emptyList(),
    val steps: List<MacroStep> = emptyList(),
    /** Null until the user moves it; it is then placed at a default spot. */
    val rect: NormalizedRect? = null,
    val behavior: ButtonBehavior = ButtonBehavior(),
)

@Serializable
data class OverlayLayoutProfile(
    val id: String = UUID.randomUUID().toString(),
    /** Blank for the built in default profile, shown as "Default". */
    val name: String = "",
    val controllerType: OverlayControllerType,
    val rects: Map<OverlayInputConfig, NormalizedRect> = emptyMap(),
    val behaviors: Map<OverlayInputConfig, ButtonBehavior> = emptyMap(),
    val macros: List<OverlayMacro> = emptyList(),
)

fun defaultProfileId(type: OverlayControllerType) = "default-${type.name.lowercase()}"

private fun defaultProfile(type: OverlayControllerType) =
    OverlayLayoutProfile(id = defaultProfileId(type), controllerType = type)

/** Profiles of [type], never empty: a blank default profile stands in when none was saved. */
fun InputOverlaySettings.profilesFor(type: OverlayControllerType): List<OverlayLayoutProfile> =
    profiles.filter { it.controllerType == type }.ifEmpty { listOf(defaultProfile(type)) }

fun InputOverlaySettings.activeProfile(type: OverlayControllerType): OverlayLayoutProfile {
    val list = profilesFor(type)
    val id = activeProfileIds[type]
    return list.firstOrNull { it.id == id } ?: list.first()
}

/** Inserts [profile], or replaces the saved profile with the same id. */
fun InputOverlaySettings.withProfile(profile: OverlayLayoutProfile): InputOverlaySettings {
    val index = profiles.indexOfFirst { it.id == profile.id }
    val newProfiles = if (index >= 0) {
        profiles.toMutableList().apply { this[index] = profile }
    } else {
        profiles + profile
    }
    return copy(profiles = newProfiles)
}

fun InputOverlaySettings.updateActiveProfile(
    type: OverlayControllerType,
    transform: (OverlayLayoutProfile) -> OverlayLayoutProfile,
): InputOverlaySettings = withProfile(transform(activeProfile(type)))

fun InputOverlaySettings.selectProfile(type: OverlayControllerType, id: String): InputOverlaySettings =
    copy(activeProfileIds = activeProfileIds + (type to id))

/** Creates a profile named [name], copying [copyFrom] when given, and makes it active. */
fun InputOverlaySettings.createProfile(
    type: OverlayControllerType,
    name: String,
    copyFrom: OverlayLayoutProfile? = null,
): InputOverlaySettings {
    // the stand-in default profile must be saved too, or it would vanish once another exists
    val base = if (profiles.none { it.controllerType == type }) withProfile(defaultProfile(type)) else this
    val profile = (copyFrom ?: OverlayLayoutProfile(controllerType = type)).copy(
        id = UUID.randomUUID().toString(),
        name = name,
        controllerType = type,
        // macros get new ids so the copy can be edited on its own
        macros = copyFrom?.macros?.map { it.copy(id = UUID.randomUUID().toString()) } ?: emptyList(),
    )
    return base.withProfile(profile).selectProfile(type, profile.id)
}

fun InputOverlaySettings.renameProfile(id: String, name: String): InputOverlaySettings {
    val profile = profiles.firstOrNull { it.id == id }
        ?: OverlayControllerType.entries.firstOrNull { defaultProfileId(it) == id }?.let { defaultProfile(it) }
        ?: return this
    return withProfile(profile.copy(name = name))
}

/** Deletes a profile; deleting the last one of a type leaves a fresh default profile. */
fun InputOverlaySettings.deleteProfile(type: OverlayControllerType, id: String): InputOverlaySettings {
    val remaining = profiles.filterNot { it.id == id }
    val newActive = if (activeProfile(type).id == id) activeProfileIds - type else activeProfileIds
    return copy(profiles = remaining, activeProfileIds = newActive)
}

/** Clears positions and sizes of the active profile; behaviors and macros are kept. */
fun InputOverlaySettings.resetActiveLayout(type: OverlayControllerType): InputOverlaySettings =
    updateActiveProfile(type) { profile ->
        profile.copy(rects = emptyMap(), macros = profile.macros.map { it.copy(rect = null) })
    }

fun InputOverlaySettings.setBehavior(
    type: OverlayControllerType,
    input: OverlayInputConfig,
    behavior: ButtonBehavior,
): InputOverlaySettings = updateActiveProfile(type) { profile ->
    profile.copy(
        behaviors = if (behavior.isDefault) profile.behaviors - input else profile.behaviors + (input to behavior)
    )
}

fun InputOverlaySettings.upsertMacro(type: OverlayControllerType, macro: OverlayMacro): InputOverlaySettings =
    updateActiveProfile(type) { profile ->
        val index = profile.macros.indexOfFirst { it.id == macro.id }
        profile.copy(
            macros = if (index >= 0) {
                profile.macros.toMutableList().apply { this[index] = macro }
            } else {
                profile.macros + macro
            }
        )
    }

fun InputOverlaySettings.deleteMacro(type: OverlayControllerType, macroId: String): InputOverlaySettings =
    updateActiveProfile(type) { profile -> profile.copy(macros = profile.macros.filterNot { it.id == macroId }) }

/** Merges positions edited on the overlay into the active profile of [type]. */
fun InputOverlaySettings.applyEditedRects(
    type: OverlayControllerType,
    rects: Map<OverlayInputConfig, NormalizedRect>,
    macroRects: Map<String, NormalizedRect>,
): InputOverlaySettings = updateActiveProfile(type) { profile ->
    profile.copy(
        rects = profile.rects + rects,
        macros = profile.macros.map { macro -> macroRects[macro.id]?.let { macro.copy(rect = it) } ?: macro },
    )
}

/**
 * Moves the absolute pixel rectangles saved by older versions into the default profile of
 * every controller type (they were shared by all types) and drops them.
 * [surfaceWidth]/[surfaceHeight] must be the size of the overlay they were saved on.
 */
fun InputOverlaySettings.migrateLegacyRects(surfaceWidth: Int, surfaceHeight: Int): InputOverlaySettings {
    if (inputOverlayRectMap.isEmpty()) return this
    val converted = inputOverlayRectMap.mapValues { (_, r) ->
        NormalizedRect.fromPixels(r.left, r.top, r.right, r.bottom, surfaceWidth, surfaceHeight)
    }
    var result = copy(inputOverlayRectMap = emptyMap())
    for (type in OverlayControllerType.entries) {
        val profile = result.activeProfile(type)
        // rects saved by a newer layout win over the legacy ones
        result = result.withProfile(profile.copy(rects = converted + profile.rects))
    }
    return result
}
