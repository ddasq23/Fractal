package dev.dfd.fractal.settings.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.dropUnlessResumed
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.games.profile.HintText
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.settings.input.controller.AutoControllerMapping
import dev.dfd.fractal.settings.input.controller.slotControllerName
import kotlinx.coroutines.flow.map

/** Emulated controllers and this device's own sensors; on-screen controls and hotkeys are in the settings home. */
@Composable
fun InputSettingsScreen(
    navigateBack: () -> Unit,
    goToControllerSettings: (controllerIndex: Int) -> Unit,
    goToHostInputSettings: () -> Unit,
    goToMacros: () -> Unit = {},
) {
    val context = LocalContext.current
    fun loadControllers() =
        (0..<NativeInput.MAX_CONTROLLERS).map { index ->
            Triple(index, slotControllerName(context, index), NativeInput.getControllerProfileName(index))
        }
    var controllers by remember { mutableStateOf(loadControllers()) }
    var autoMapping by remember { mutableStateOf(AutoControllerMapping.isEnabled(context)) }
    var defaultType by remember { mutableStateOf(AutoControllerMapping.defaultType(context)) }
    var nintendoLayout by remember { mutableStateOf(AutoControllerMapping.isNintendoLayout(context)) }
    val overlaySettings by remember { AppSettingsStore.dataStore.data.map { it.inputOverlaySettings } }
        .collectAsState(initial = null)
    val touchSlot = overlaySettings?.takeIf { it.isOverlayEnabled != false }?.controllerIndex

    // back from a controller screen: its type or profile may have changed
    LifecycleResumeEffect(Unit) {
        controllers = loadControllers()
        onPauseOrDispose { }
    }

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
        SingleSelection(
            label = tr("New gamepads become"),
            choice = defaultType,
            choices = AutoControllerMapping.DEFAULT_TYPE_CHOICES,
            choiceToString = ::controllerTypeToString,
            enabled = autoMapping,
            description = tr("Pro Controller: each gamepad is its own player and the on-screen controls stay on the GamePad. Wii U GamePad: the first gamepad drives the GamePad, for games that only accept it. Mappings you already have don't change."),
            onChoiceChanged = {
                defaultType = it
                AutoControllerMapping.setDefaultType(context, it)
            },
        )
        Toggle(
            label = tr("Nintendo button layout"),
            description = tr("Map the face buttons by position, as on a Nintendo controller: A on the right, B at the bottom. Off: by label, so on an Xbox-style gamepad A is at the bottom. Used when a gamepad is mapped automatically or reset."),
            checked = nintendoLayout,
            onCheckedChanged = {
                nintendoLayout = it
                AutoControllerMapping.setNintendoLayout(context, it)
            },
        )
        HintText(tr("Some games only read the Wii U GamePad, others only a Pro Controller or Wii Remote. To change it for one game, open the game's settings > Player 1 controller."))
        controllers.forEach { (controllerIndex, typeName, profile) ->
            val details = listOfNotNull(
                profile?.let { tr("Profile: {0}", it) },
                tr("Touch controls").takeIf { controllerIndex == touchSlot },
            )
            Button(
                label = tr("Player {0} · {1}", controllerIndex + 1, typeName),
                description = details.joinToString(" · ").ifEmpty { null },
                onClick = dropUnlessResumed { goToControllerSettings(controllerIndex) },
            )
        }
        Header(tr("Macros and turbo"))
        Button(
            label = tr("Macros and turbo"),
            description = tr("Buttons of the physical controller that play a combo, a sequence or turbo"),
            onClick = dropUnlessResumed { goToMacros() },
        )
        Header(tr("This device"))
        Button(
            label = tr("Device settings"),
            description = tr("Motion sensors and rumble of this device"),
            onClick = dropUnlessResumed { goToHostInputSettings() },
        )
    }
}
