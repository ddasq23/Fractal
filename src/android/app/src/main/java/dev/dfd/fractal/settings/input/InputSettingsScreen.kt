package dev.dfd.fractal.settings.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.dropUnlessResumed
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput

/** Emulated controllers and this device's own sensors; on-screen controls and hotkeys are in the settings home. */
@Composable
fun InputSettingsScreen(
    navigateBack: () -> Unit,
    goToControllerSettings: (controllerIndex: Int) -> Unit,
    goToHostInputSettings: () -> Unit,
) {
    val controllers = remember {
        (0..<NativeInput.MAX_CONTROLLERS).map { it to NativeInput.getControllerType(it) }
    }
    ScreenContent(
        appBarText = tr("Input settings"),
        navigateBack = navigateBack,
    ) {
        Header(tr("Emulated controllers"))
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
