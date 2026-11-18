package dev.dfd.fractal.settings.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.dfd.fractal.common.settings.HudAnchor
import dev.dfd.fractal.common.settings.HudItem
import dev.dfd.fractal.common.settings.PerformanceHudSettings
import dev.dfd.fractal.common.settings.performanceHudSettings
import dev.dfd.fractal.common.settings.updatePerformanceHud
import dev.dfd.fractal.common.ui.localization.hudAnchorName
import dev.dfd.fractal.common.ui.localization.hudItemDescription
import dev.dfd.fractal.common.ui.localization.hudItemName
import kotlinx.coroutines.launch
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Slider
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.BuildConfig
import dev.dfd.fractal.nativeinterface.NativeSettings

private val OverlayPositionChoices = listOf(
    NativeSettings.OverlayScreenPosition.DISABLED,
    NativeSettings.OverlayScreenPosition.TOP_LEFT,
    NativeSettings.OverlayScreenPosition.TOP_CENTER,
    NativeSettings.OverlayScreenPosition.TOP_RIGHT,
    NativeSettings.OverlayScreenPosition.BOTTOM_LEFT,
    NativeSettings.OverlayScreenPosition.BOTTOM_CENTER,
    NativeSettings.OverlayScreenPosition.BOTTOM_RIGHT
)

private const val OVERLAY_TEXT_SCALE_STEPS = 9

@Composable
fun OverlaySettingsScreen(navigateBack: () -> Unit) {
    var overlayPosition by rememberSaveable { mutableIntStateOf(NativeSettings.getOverlayPosition()) }
    var notificationsPosition by rememberSaveable { mutableIntStateOf(NativeSettings.getNotificationsPosition()) }
    ScreenContent(
        appBarText = tr("Overlay settings"),
        navigateBack = navigateBack,
    ) {
        PerformanceHudSettingsSection()
        Header(tr("Classic overlay"))
        SingleSelection(
            label = tr("Position"),
            description = tr("Cemu's statistics box, without the app theme. The performance HUD above shows the same statistics"),
            choice = overlayPosition,
            choices = OverlayPositionChoices,
            choiceToString = { overlayScreenPositionToString(it) },
            onChoiceChanged = {
                overlayPosition = it
                NativeSettings.setOverlayPosition(it)
            },
        )
        if (overlayPosition != NativeSettings.OverlayScreenPosition.DISABLED)
            OverlaySettings()
        Header(tr("Notifications"))
        SingleSelection(
            label = tr("Position"),
            description = tr("Short messages shown while playing"),
            choice = notificationsPosition,
            choices = OverlayPositionChoices,
            choiceToString = { overlayScreenPositionToString(it) },
            onChoiceChanged = {
                notificationsPosition = it
                NativeSettings.setNotificationsPosition(it)
            },
        )
        if (notificationsPosition != NativeSettings.OverlayScreenPosition.DISABLED)
            NotificationSettings()
        Header(tr("Performance log"))
        Toggle(
            label = tr("Record full performance log"),
            description = tr("Every game session already keeps a light log (FPS, frametimes, problems found) for the debug report. This adds GPU timings and recompiler data, which cost a little performance. Folders in Android/data/{0}/files/perf", BuildConfig.APPLICATION_ID),
            initialCheckedState = NativeSettings::getPerformanceLog,
            onCheckedChanged = NativeSettings::setPerformanceLog,
        )
    }
}

/** The performance HUD drawn by the app over the game; the in-game menu changes the same settings. */
@Composable
private fun PerformanceHudSettingsSection() {
    val scope = rememberCoroutineScope()
    val stored by remember { performanceHudSettings() }.collectAsState(initial = null)
    val settings = stored ?: return
    fun update(transform: (PerformanceHudSettings) -> PerformanceHudSettings) {
        scope.launch { updatePerformanceHud(transform) }
    }

    Header(tr("Performance HUD"))
    Toggle(
        label = tr("Show performance HUD"),
        description = tr("FPS, frame time, CPU, GPU, RAM, temperatures, power and Fractal's own statistics over the game, in the look of the app theme. Drag it with a finger to move it; the game menu has these settings too"),
        checked = settings.enabled,
        onCheckedChanged = { on -> update { it.copy(enabled = on) } },
    )
    if (!settings.enabled) return
    val anchor = settings.anchor
    SingleSelection(
        label = tr("Position"),
        description = tr("Or drag the HUD over the game"),
        choice = anchor,
        // a place it was dragged to shows as such until another one is picked
        choices = (if (anchor == null) listOf(null) else emptyList()) + HudAnchor.entries,
        choiceToString = { it?.let(::hudAnchorName) ?: tr("Where it was dragged") },
        onChoiceChanged = { picked -> if (picked != null) update { it.at(picked) } },
    )
    Toggle(
        label = tr("One item per line"),
        description = tr("A column instead of a single row"),
        checked = settings.vertical,
        onCheckedChanged = { on -> update { it.copy(vertical = on) } },
    )
    Slider(
        label = tr("Size"),
        value = settings.sizePercent,
        valueFrom = PerformanceHudSettings.SIZE_MIN,
        valueTo = PerformanceHudSettings.SIZE_MAX,
        steps = HUD_SIZE_STEPS,
        labelFormatter = { "$it%" },
        onValueChange = { value -> update { it.copy(sizePercent = value) } },
    )
    Slider(
        label = tr("Background opacity"),
        value = settings.opacityPercent,
        valueFrom = PerformanceHudSettings.OPACITY_MIN,
        valueTo = PerformanceHudSettings.OPACITY_MAX,
        steps = HUD_OPACITY_STEPS,
        labelFormatter = { "$it%" },
        onValueChange = { value -> update { it.copy(opacityPercent = value) } },
    )
    Header(tr("Shown on the HUD"))
    for (item in HudItem.entries) {
        Toggle(
            label = hudItemName(item),
            description = hudItemDescription(item),
            checked = settings.shows(item),
            onCheckedChanged = { on -> update { it.withItem(item, on) } },
        )
    }
}

/** 50% to 200% in steps of 10. */
private const val HUD_SIZE_STEPS = 14

/** 0% to 100% in steps of 5. */
private const val HUD_OPACITY_STEPS = 19

@Composable
private fun OverlaySettings() {
    Slider(
        label = tr("Scale"),
        initialValue = NativeSettings::getOverlayTextScalePercentage,
        steps = OVERLAY_TEXT_SCALE_STEPS,
        valueFrom = NativeSettings.OVERLAY_TEXT_SCALE_MIN,
        valueTo = NativeSettings.OVERLAY_TEXT_SCALE_MAX,
        onValueChange = NativeSettings::setOverlayTextScalePercentage,
        labelFormatter = { "${it}%" }
    )
    Header(tr("Shown statistics"))
    Toggle(
        label = tr("FPS"),
        description = tr("The number of frames per second. Average over last 5 seconds"),
        initialCheckedState = NativeSettings::isOverlayFPSEnabled,
        onCheckedChanged = NativeSettings::setOverlayFPSEnabled,
    )
    Toggle(
        label = tr("Draw calls per frame"),
        description = tr("The number of draw calls per frame. Average over last 5 seconds"),
        initialCheckedState = NativeSettings::isOverlayDrawCallsPerFrameEnabled,
        onCheckedChanged = NativeSettings::setOverlayDrawCallsPerFrameEnabled,
    )
    Toggle(
        label = tr("CPU usage"),
        description = tr("CPU usage of Fractal in percent"),
        initialCheckedState = NativeSettings::isOverlayCPUUsageEnabled,
        onCheckedChanged = NativeSettings::setOverlayCPUUsageEnabled,
    )
    Toggle(
        label = tr("RAM usage"),
        description = tr("Fractal RAM usage in MB"),
        initialCheckedState = NativeSettings::isOverlayRAMUsageEnabled,
        onCheckedChanged = NativeSettings::setOverlayRAMUsageEnabled,
    )
    Toggle(
        label = tr("Debug"),
        description = tr("Displays internal debug information (Vulkan only)"),
        initialCheckedState = NativeSettings::isOverlayDebugEnabled,
        onCheckedChanged = NativeSettings::setOverlayDebugEnabled,
    )
}

@Composable
private fun NotificationSettings() {
    Slider(
        label = tr("Scale"),
        initialValue = NativeSettings::getNotificationsTextScalePercentage,
        steps = OVERLAY_TEXT_SCALE_STEPS,
        valueFrom = NativeSettings.OVERLAY_TEXT_SCALE_MIN,
        valueTo = NativeSettings.OVERLAY_TEXT_SCALE_MAX,
        onValueChange = NativeSettings::setNotificationsTextScalePercentage,
        labelFormatter = { "$it%" }
    )
    Header(tr("Shown notifications"))
    Toggle(
        label = tr("Controller profiles"),
        description = tr("Displays the active controller profile when starting a game"),
        initialCheckedState = NativeSettings::isNotificationControllerProfilesEnabled,
        onCheckedChanged = NativeSettings::setNotificationControllerProfilesEnabled,
    )
    Toggle(
        label = tr("Shader compiler"),
        description = tr("Shows a notification after shaders have been compiled"),
        initialCheckedState = NativeSettings::isNotificationShaderCompilerEnabled,
        onCheckedChanged = NativeSettings::setNotificationShaderCompilerEnabled,
    )
    Toggle(
        label = tr("Friend list"),
        description = tr("Shows friend list related data if online"),
        initialCheckedState = NativeSettings::isNotificationFriendListEnabled,
        onCheckedChanged = NativeSettings::setNotificationFriendListEnabled,
    )
}

private fun overlayScreenPositionToString(overlayScreenPosition: Int) = when (overlayScreenPosition) {
    NativeSettings.OverlayScreenPosition.DISABLED -> tr("Disabled")
    NativeSettings.OverlayScreenPosition.TOP_LEFT -> tr("Top left")
    NativeSettings.OverlayScreenPosition.TOP_CENTER -> tr("Top center")
    NativeSettings.OverlayScreenPosition.TOP_RIGHT -> tr("Top right")
    NativeSettings.OverlayScreenPosition.BOTTOM_LEFT -> tr("Bottom left")
    NativeSettings.OverlayScreenPosition.BOTTOM_CENTER -> tr("Bottom center")
    NativeSettings.OverlayScreenPosition.BOTTOM_RIGHT -> tr("Bottom right")
    else -> throw IllegalArgumentException("Invalid overlay position: $overlayScreenPosition")
}