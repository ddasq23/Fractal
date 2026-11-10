package dev.dfd.fractal.emulation.menu

import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.emulation.inputoverlay.ui.DialogTextButton
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayDialog
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.settings.input.controller.ControllerInputSettingsScreen
import dev.dfd.fractal.settings.input.hotkeys.HotkeySettingsScreen

/** Holds the SurfaceView the TV image is drawn on, for screenshots. */
class SurfaceRef {
    var view: SurfaceView? = null
}

/** Settings screens opened over the game from the menu. */
sealed interface EmulationPanel {
    data object ControllerPicker : EmulationPanel
    data class Controller(val index: Int) : EmulationPanel
    data object Hotkeys : EmulationPanel
}

/**
 * Shows [panel]: the app's controller mapping and hotkey screens in a full screen window
 * over the game, each with view models of its own that go away when it closes.
 */
@Composable
fun EmulationPanels(
    panel: EmulationPanel?,
    onPanelChange: (EmulationPanel?) -> Unit,
    onClosed: () -> Unit,
) {
    val close = {
        onPanelChange(null)
        onClosed()
    }
    when (panel) {
        null -> {}
        EmulationPanel.ControllerPicker -> ControllerPickerDialog(
            onPick = { onPanelChange(EmulationPanel.Controller(it)) },
            onDismiss = close,
        )

        is EmulationPanel.Controller -> FullScreenPanel(key = "controller-${panel.index}", onClose = close) { back ->
            ControllerInputSettingsScreen(navigateBack = back, controllerIndex = panel.index)
        }

        EmulationPanel.Hotkeys -> FullScreenPanel(key = "hotkeys", onClose = close) { back ->
            HotkeySettingsScreen(navigateBack = back)
        }
    }
}

@Composable
private fun ControllerPickerDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    OverlayDialog(
        title = tr("Controller mapping"),
        onDismiss = onDismiss,
        actions = { DialogTextButton(tr("Close"), onDismiss) },
    ) {
        Text(
            tr("Changes apply to the running game right away."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        for (index in 0 until NativeInput.MAX_CONTROLLERS) {
            val description = if (NativeInput.isControllerDisabled(index)) {
                tr("Disabled")
            } else {
                runCatching { controllerTypeToString(NativeInput.getControllerType(index)) }.getOrDefault(tr("Disabled"))
            }
            ControllerChoiceRow(tr("Controller {0}", index + 1), description) { onPick(index) }
        }
    }
}

@Composable
private fun FullScreenPanel(
    key: String,
    onClose: () -> Unit,
    content: @Composable (back: () -> Unit) -> Unit,
) {
    val owner = remember(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content(onClose)
            }
        }
    }
}
