package dev.dfd.fractal.settings.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.settings.input.controller.AutoControllerMapping

/** Emulated controllers and this device's own sensors; on-screen controls and hotkeys are in the settings home. */
@Composable
fun InputSettingsScreen(
    navigateBack: () -> Unit,
    goToControllerSettings: (controllerIndex: Int) -> Unit,
    goToHostInputSettings: () -> Unit,
) {
    val context = LocalContext.current
    fun loadControllers() =
        (0..<NativeInput.MAX_CONTROLLERS).map { it to NativeInput.getControllerType(it) }
    var controllers by remember { mutableStateOf(loadControllers()) }
    var autoMapping by remember { mutableStateOf(AutoControllerMapping.isEnabled(context)) }
    ScreenContent(
        appBarText = tr("Input settings"),
        navigateBack = navigateBack,
    ) {
        Header(tr("Emulated controllers"))
        Toggle(
            label = tr("Automatic controller mapping"),
            description = tr("Map a connected gamepad to the first controller that has none, without changing your own mappings"),
            checked = autoMapping,
            onCheckedChanged = { enabled ->
                autoMapping = enabled
                AutoControllerMapping.setEnabled(context, enabled)
                if (enabled) {
                    AutoControllerMapping.showMappedToast(
                        context,
                        AutoControllerMapping.mapConnectedControllers(context),
                    )
                    controllers = loadControllers()
                }
            },
        )
        controllers.forEach { (controllerIndex, controllerEmulatedType) ->
            Button(
                label = tr("Controller {0}", controllerIndex + 1),
                description = tr(
                    "Emulated controller: {0}",
                    controllerTypeToString(controllerEmulatedType),
                ),
                onClick = dropUnlessResumed { goToControllerSettings(controllerIndex) },
            )
        }
        Header(tr("This device"))
        Button(
            label = tr("Device settings"),
            description = tr("Motion sensors and rumble of this device"),
            onClick = dropUnlessResumed { goToHostInputSettings() },
        )
    }
}
