package dev.dfd.fractal.settings.inputoverlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayMacro
import dev.dfd.fractal.common.settings.activeProfile
import dev.dfd.fractal.common.settings.isOverlayShown
import dev.dfd.fractal.common.settings.createProfile
import dev.dfd.fractal.common.settings.deleteMacro
import dev.dfd.fractal.common.settings.deleteProfile
import dev.dfd.fractal.common.settings.renameProfile
import dev.dfd.fractal.common.settings.selectProfile
import dev.dfd.fractal.common.settings.upsertMacro
import dev.dfd.fractal.common.settings.OverlayShortcut
import dev.dfd.fractal.common.settings.deleteShortcut
import dev.dfd.fractal.common.settings.upsertShortcut
import dev.dfd.fractal.emulation.inputoverlay.ui.ShortcutEditorDialog
import dev.dfd.fractal.emulation.inputoverlay.ui.ShortcutList
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.emulation.inputoverlay.ui.MacroEditorDialog
import dev.dfd.fractal.emulation.inputoverlay.ui.MacroList
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayAppearanceOptions
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayChip
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayToggleRow
import dev.dfd.fractal.emulation.inputoverlay.ui.ProfileList
import dev.dfd.fractal.emulation.inputoverlay.ui.displayName
import dev.dfd.fractal.nativeinterface.NativeInput

private val ControllerIndexChoices = (0..<NativeInput.MAX_CONTROLLERS).toList()

/** Order of the visibility switches. */
private val VisibleInputsOrder = listOf(
    OverlayInputConfig.BUTTON_A,
    OverlayInputConfig.BUTTON_B,
    OverlayInputConfig.BUTTON_X,
    OverlayInputConfig.BUTTON_Y,
    OverlayInputConfig.BUTTON_ONE,
    OverlayInputConfig.BUTTON_TWO,
    OverlayInputConfig.BUTTON_C,
    OverlayInputConfig.BUTTON_Z,
    OverlayInputConfig.BUTTON_HOME,
    OverlayInputConfig.BUTTON_L,
    OverlayInputConfig.BUTTON_R,
    OverlayInputConfig.BUTTON_L_STICK_CLICK,
    OverlayInputConfig.BUTTON_R_STICK_CLICK,
    OverlayInputConfig.BUTTON_PLUS,
    OverlayInputConfig.BUTTON_MINUS,
    OverlayInputConfig.BUTTON_ZL,
    OverlayInputConfig.BUTTON_ZR,
    OverlayInputConfig.BUTTON_BLOW_MIC,
    OverlayInputConfig.JOYSTICK_LEFT,
    OverlayInputConfig.JOYSTICK_RIGHT,
    OverlayInputConfig.DPAD,
)

private sealed interface MacroEditing {
    data object New : MacroEditing
    data class Existing(val macro: OverlayMacro) : MacroEditing
}

@Composable
fun InputOverlaySettingsScreen(
    viewModel: InputOverlaySettingsViewModel = viewModel(),
    navigateBack: () -> Unit,
) {
    val overlaySettings by viewModel.overlaySettings.collectAsState()
    var layoutType by rememberSaveable { mutableStateOf(OverlayControllerType.VPAD) }
    var macroEditing by remember { mutableStateOf<MacroEditing?>(null) }
    var shortcutEditing by remember { mutableStateOf<OverlayShortcut?>(null) }
    var creatingShortcut by remember { mutableStateOf(false) }

    // follow the type of the controller the overlay drives
    LaunchedEffect(overlaySettings.controllerIndex) {
        OverlayControllerType.fromNative(NativeInput.getControllerType(overlaySettings.controllerIndex))
            ?.let { layoutType = it }
    }

    FractalScreen(title = tr("Input overlay settings"), onBack = navigateBack) {
        OverlayToggleRow(
            title = tr("Input overlay"),
            description = tr("Enable input overlay"),
            checked = overlaySettings.isOverlayShown(),
            onCheckedChange = { viewModel.setOverlayEnabled(it) },
        )
        OverlayAppearanceOptions(overlaySettings, viewModel::update)

        SectionTitle(tr("Overlay controller"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (index in ControllerIndexChoices) {
                OverlayChip(
                    tr("Controller {0}", index + 1),
                    selected = overlaySettings.controllerIndex == index,
                    onClick = { viewModel.setControllerIndex(index) },
                )
            }
        }

        SectionTitle(tr("Layout profiles"))
        Text(
            tr("Each controller type has its own profiles. Positions, sizes and button options (toggle, turbo) are edited on the overlay: open the menu while playing and choose Edit inputs, then long press an input."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (type in OverlayControllerType.entries) {
                OverlayChip(type.displayName(), selected = layoutType == type, onClick = { layoutType = type })
            }
        }
        ProfileList(
            settings = overlaySettings,
            controllerType = layoutType,
            onSelect = { profile -> viewModel.update { it.selectProfile(layoutType, profile.id) } },
            onCreate = { name, copyFrom -> viewModel.update { it.createProfile(layoutType, name, copyFrom) } },
            onRename = { profile, name -> viewModel.update { it.renameProfile(profile.id, name) } },
            onDelete = { profile -> viewModel.update { it.deleteProfile(layoutType, profile.id) } },
        )

        val activeProfile = overlaySettings.activeProfile(layoutType)
        SectionTitle(tr("Macros"))
        Text(
            tr("Macros of the profile \"{0}\". A macro is an extra overlay button that presses several buttons together or plays a timed sequence.", activeProfile.displayName()),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        MacroList(
            macros = activeProfile.macros,
            onEdit = { macroEditing = MacroEditing.Existing(it) },
            onDelete = { macro -> viewModel.update { it.deleteMacro(layoutType, macro.id) } },
        )
        FractalButton(tr("New macro"), onClick = { macroEditing = MacroEditing.New }, icon = FractalIcons.FolderAdd)

        SectionTitle(tr("Shortcuts"))
        Text(
            tr("Shortcuts of the profile \"{0}\". A shortcut is an extra overlay button that runs an emulator action, like opening the menu, pausing or taking a screenshot.", activeProfile.displayName()),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        ShortcutList(
            shortcuts = activeProfile.shortcuts,
            onEdit = { shortcutEditing = it },
            onDelete = { shortcut -> viewModel.update { it.deleteShortcut(layoutType, shortcut.id) } },
        )
        FractalButton(tr("New shortcut"), onClick = { creatingShortcut = true }, icon = FractalIcons.Shortcut)

        SectionTitle(tr("Visible Inputs"))
        for (input in VisibleInputsOrder) {
            OverlayToggleRow(
                title = input.displayName(),
                checked = overlaySettings.inputVisibilityMap[input] ?: true,
                onCheckedChange = { viewModel.setInputVisibility(input, it) },
            )
        }
    }

    if (creatingShortcut || shortcutEditing != null) {
        ShortcutEditorDialog(
            initial = shortcutEditing,
            onSave = { shortcut ->
                viewModel.update { it.upsertShortcut(layoutType, shortcut) }
                creatingShortcut = false
                shortcutEditing = null
            },
            onDismiss = {
                creatingShortcut = false
                shortcutEditing = null
            },
        )
    }

    macroEditing?.let { editing ->
        MacroEditorDialog(
            initial = (editing as? MacroEditing.Existing)?.macro,
            controllerType = layoutType,
            onSave = { macro ->
                viewModel.update { it.upsertMacro(layoutType, macro) }
                macroEditing = null
            },
            onDismiss = { macroEditing = null },
        )
    }
}
