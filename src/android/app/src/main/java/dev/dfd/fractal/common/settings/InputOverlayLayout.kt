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

/** The macro button that presses [this] input, null for sticks and the d-pad. */
fun OverlayInputConfig.toMacroInput(): OverlayMacroInput? = when (this) {
    OverlayInputConfig.BUTTON_A -> OverlayMacroInput.A
    OverlayInputConfig.BUTTON_B -> OverlayMacroInput.B
    OverlayInputConfig.BUTTON_X -> OverlayMacroInput.X
    OverlayInputConfig.BUTTON_Y -> OverlayMacroInput.Y
    OverlayInputConfig.BUTTON_ONE -> OverlayMacroInput.ONE
    OverlayInputConfig.BUTTON_TWO -> OverlayMacroInput.TWO
    OverlayInputConfig.BUTTON_C -> OverlayMacroInput.C
    OverlayInputConfig.BUTTON_Z -> OverlayMacroInput.Z
    OverlayInputConfig.BUTTON_HOME -> OverlayMacroInput.HOME
    OverlayInputConfig.BUTTON_L -> OverlayMacroInput.L
    OverlayInputConfig.BUTTON_R -> OverlayMacroInput.R
    OverlayInputConfig.BUTTON_ZL -> OverlayMacroInput.ZL
    OverlayInputConfig.BUTTON_ZR -> OverlayMacroInput.ZR
    OverlayInputConfig.BUTTON_PLUS -> OverlayMacroInput.PLUS
    OverlayInputConfig.BUTTON_MINUS -> OverlayMacroInput.MINUS
    OverlayInputConfig.BUTTON_L_STICK_CLICK -> OverlayMacroInput.L_STICK_CLICK
    OverlayInputConfig.BUTTON_R_STICK_CLICK -> OverlayMacroInput.R_STICK_CLICK
    OverlayInputConfig.BUTTON_BLOW_MIC -> OverlayMacroInput.BLOW_MIC
    OverlayInputConfig.JOYSTICK_LEFT, OverlayInputConfig.JOYSTICK_RIGHT, OverlayInputConfig.DPAD -> null
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
    val shortcuts: List<OverlayShortcut> = emptyList(),
    /** Per input look, keyed by [styleKey] for built in inputs and by id for macros and shortcuts. */
    val styles: Map<String, OverlayInputStyle> = emptyMap(),
    /**
     * Version of the default layout the profile was made with. Profiles saved before the adaptive
     * layout read as 0: if the user moved inputs on them, the inputs they didn't move keep the
     * old default spots so the layout looks the way it did ([usesLegacyLayout]).
     */
    val layoutVersion: Int = 0,
    /** Positions used while the screen is taller than wide, keyed like [styles]. */
    val portraitRects: Map<String, NormalizedRect> = emptyMap(),
)

/** Version of the adaptive default layout; see [OverlayLayoutProfile.layoutVersion]. */
const val OVERLAY_LAYOUT_VERSION = 2

/** True when the user moved something on this profile (in either orientation). */
val OverlayLayoutProfile.hasCustomPositions: Boolean
    get() = rects.isNotEmpty() || portraitRects.isNotEmpty() ||
        macros.any { it.rect != null } || shortcuts.any { it.rect != null }

/** An older profile with moved inputs: the rest stays on the old default spots. */
val OverlayLayoutProfile.usesLegacyLayout: Boolean
    get() = layoutVersion < OVERLAY_LAYOUT_VERSION && hasCustomPositions

/** Saved position of the input with [key] (see [styleKey]), or null when it sits on its default spot. */
fun OverlayLayoutProfile.savedRect(key: String, portrait: Boolean): NormalizedRect? {
    if (portrait) return portraitRects[key]
    macros.firstOrNull { it.id == key }?.let { return it.rect }
    shortcuts.firstOrNull { it.id == key }?.let { return it.rect }
    return builtinConfigOf(key)?.let { rects[it] }
}

/** Sets ([rect] non null) or clears the saved position of the input with [key]. */
fun OverlayLayoutProfile.withRect(key: String, rect: NormalizedRect?, portrait: Boolean): OverlayLayoutProfile {
    if (portrait) {
        return copy(portraitRects = if (rect == null) portraitRects - key else portraitRects + (key to rect))
    }
    if (macros.any { it.id == key }) {
        return copy(macros = macros.map { if (it.id == key) it.copy(rect = rect) else it })
    }
    if (shortcuts.any { it.id == key }) {
        return copy(shortcuts = shortcuts.map { if (it.id == key) it.copy(rect = rect) else it })
    }
    val config = builtinConfigOf(key) ?: return this
    return copy(rects = if (rect == null) rects - config else rects + (config to rect))
}

private fun builtinConfigOf(key: String): OverlayInputConfig? =
    OverlayInputConfig.entries.firstOrNull { styleKey(it) == key }

fun defaultProfileId(type: OverlayControllerType) = "default-${type.name.lowercase()}"

private fun defaultProfile(type: OverlayControllerType) =
    OverlayLayoutProfile(id = defaultProfileId(type), controllerType = type, layoutVersion = OVERLAY_LAYOUT_VERSION)

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
    // macros and shortcuts get new ids so the copy can be edited on its own
    val newIds = HashMap<String, String>()
    fun newId(old: String) = UUID.randomUUID().toString().also { newIds[old] = it }
    val macros = copyFrom?.macros?.map { it.copy(id = newId(it.id)) } ?: emptyList()
    val shortcuts = copyFrom?.shortcuts?.map { it.copy(id = newId(it.id)) } ?: emptyList()
    val profile = (copyFrom ?: OverlayLayoutProfile(controllerType = type, layoutVersion = OVERLAY_LAYOUT_VERSION)).copy(
        id = UUID.randomUUID().toString(),
        name = name,
        controllerType = type,
        macros = macros,
        shortcuts = shortcuts,
        styles = copyFrom?.styles?.mapKeys { (key, _) -> newIds[key] ?: key } ?: emptyMap(),
        portraitRects = copyFrom?.portraitRects?.mapKeys { (key, _) -> newIds[key] ?: key } ?: emptyMap(),
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

/**
 * Puts the active profile back on the adaptive default layout: positions and sizes are cleared;
 * behaviors, macros, shortcuts, opacity and shapes are kept.
 */
fun InputOverlaySettings.resetActiveLayout(type: OverlayControllerType): InputOverlaySettings =
    updateActiveProfile(type) { it.resetLayout() }

fun OverlayLayoutProfile.resetLayout(): OverlayLayoutProfile = copy(
    rects = emptyMap(),
    portraitRects = emptyMap(),
    macros = macros.map { it.copy(rect = null) },
    shortcuts = shortcuts.map { it.copy(rect = null) },
    styles = styles.mapValues { (_, style) -> style.copy(scale = 1f) }.filterValues { !it.isDefault },
    layoutVersion = OVERLAY_LAYOUT_VERSION,
)

/** Moves (or with [rect] null, puts back on its default spot) one input of the active profile. */
fun InputOverlaySettings.setInputRect(
    type: OverlayControllerType,
    key: String,
    rect: NormalizedRect?,
    portrait: Boolean,
): InputOverlaySettings = updateActiveProfile(type) { it.withRect(key, rect, portrait) }

fun InputOverlaySettings.setInputVisible(input: OverlayInputConfig, visible: Boolean): InputOverlaySettings =
    copy(inputVisibilityMap = if (visible) inputVisibilityMap - input else inputVisibilityMap + (input to false))

fun InputOverlaySettings.isInputVisible(input: OverlayInputConfig): Boolean = inputVisibilityMap[input] != false

/**
 * Copy of a macro or shortcut with a new id, placed at [rect] (null: a free default spot),
 * with the same look. Returns the settings and the new id.
 */
fun InputOverlaySettings.duplicateExtra(
    type: OverlayControllerType,
    id: String,
    rect: NormalizedRect?,
    portrait: Boolean,
): Pair<InputOverlaySettings, String?> {
    val profile = activeProfile(type)
    val newId = UUID.randomUUID().toString()
    val macro = profile.macros.firstOrNull { it.id == id }
    val shortcut = profile.shortcuts.firstOrNull { it.id == id }
    var updated = when {
        macro != null -> profile.copy(macros = profile.macros + macro.copy(id = newId, rect = null))
        shortcut != null -> profile.copy(shortcuts = profile.shortcuts + shortcut.copy(id = newId, rect = null))
        else -> return this to null
    }
    profile.styles[id]?.let { updated = updated.copy(styles = updated.styles + (newId to it)) }
    if (rect != null) updated = updated.withRect(newId, rect, portrait)
    return withProfile(updated) to newId
}

/** Sets the look of one input of the active profile; [key] comes from [styleKey] or is a macro/shortcut id. */
fun InputOverlaySettings.setInputStyle(
    type: OverlayControllerType,
    key: String,
    style: OverlayInputStyle,
): InputOverlaySettings = updateActiveProfile(type) { profile ->
    profile.copy(styles = if (style.isDefault) profile.styles - key else profile.styles + (key to style))
}

fun InputOverlaySettings.upsertShortcut(type: OverlayControllerType, shortcut: OverlayShortcut): InputOverlaySettings =
    updateActiveProfile(type) { profile ->
        val index = profile.shortcuts.indexOfFirst { it.id == shortcut.id }
        profile.copy(
            shortcuts = if (index >= 0) {
                profile.shortcuts.toMutableList().apply { this[index] = shortcut }
            } else {
                profile.shortcuts + shortcut
            }
        )
    }

fun InputOverlaySettings.deleteShortcut(type: OverlayControllerType, shortcutId: String): InputOverlaySettings =
    updateActiveProfile(type) { profile ->
        profile.copy(shortcuts = profile.shortcuts.filterNot { it.id == shortcutId }, styles = profile.styles - shortcutId)
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
    updateActiveProfile(type) { profile ->
        profile.copy(macros = profile.macros.filterNot { it.id == macroId }, styles = profile.styles - macroId)
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
