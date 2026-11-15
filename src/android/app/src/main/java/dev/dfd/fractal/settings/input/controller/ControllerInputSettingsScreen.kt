package dev.dfd.fractal.settings.input.controller

import android.app.Application
import android.content.Context
import android.hardware.input.InputManager
import android.os.VibrationEffect
import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.R
import dev.dfd.fractal.common.android.inputdevice.tryUseVibrator
import dev.dfd.fractal.common.input.GamepadInputSource
import dev.dfd.fractal.common.input.InputDeviceListener
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Slider
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.extensions.showMessage
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.LocalBackdrop
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.EmulatedControllerType
import dev.dfd.fractal.nativeinterface.NativeInput.WiimoteExtension
import dev.dfd.fractal.settings.input.controller.emulatedcontroller.InputGroups
import dev.dfd.fractal.settings.input.controller.emulatedcontroller.inputFullName
import dev.dfd.fractal.settings.input.controller.emulatedcontroller.inputGroupsFor

@Composable
fun ControllerInputSettingsScreen(
    navigateBack: () -> Unit,
    controllerIndex: Int,
    /** Null where the macros screen can't be opened (the in-game panel). */
    goToMacros: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val viewModel: ControllersViewModel = viewModel(
        key = "controller$controllerIndex",
        factory = ControllersViewModel.Factory,
        extras = MutableCreationExtras().apply {
            set(APPLICATION_KEY, context.applicationContext as Application)
            set(ControllersViewModel.CONTROLLER_INDEX_KEY, controllerIndex)
        },
    )

    DisposableEffect(Unit) {
        // hand-made mappings: no automatic mapping may land on top of them while this screen is open
        AutoControllerMapping.beginManualEditing()
        onDispose {
            AutoControllerMapping.endManualEditing()
            viewModel.save()
        }
    }

    // the connected controllers follow plugging and unplugging while the screen is open
    DisposableEffect(Unit) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager?
        val listener = object : InputDeviceListener {
            override fun onInputDeviceChanged() {
                viewModel.refreshAvailableControllers()
            }
        }
        viewModel.refreshAvailableControllers()
        inputManager?.registerInputDeviceListener(listener, null)
        onDispose { inputManager?.unregisterInputDeviceListener(listener) }
    }

    val controllers by viewModel.controllers.collectAsState()
    val buttonToBind by viewModel.buttonToBind.collectAsState()
    val bindQueueSize by viewModel.bindQueueSize.collectAsState()
    val choice by viewModel.choice.collectAsState()
    val controls by viewModel.controls.collectAsState()
    val activeController by viewModel.activeController.collectAsState()
    val wiimoteExtension by viewModel.wiimoteExtension.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val profileName by viewModel.profileName.collectAsState()

    var showMapAllInputsDialog by rememberSaveable { mutableStateOf(false) }
    var showControllerSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showSaveProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showLoadProfileDialog by rememberSaveable { mutableStateOf(false) }
    var profileToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteProfileDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun refreshControllers(onControllersAvailable: () -> Unit) {
        if (viewModel.refreshAvailableControllers()) {
            onControllersAvailable()
        } else {
            snackbarHostState.showMessage(coroutineScope, tr("No controllers available"))
        }
    }

    ScreenContent(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        appBarText = tr("Controller {0}", controllerIndex + 1),
        navigateBack = navigateBack,
    ) {
        Header(tr("Emulated controller"))
        ControllerChoicePicker(
            selected = choice,
            isAllowed = viewModel::isChoiceAllowed,
            onSelect = { newChoice ->
                val changed = viewModel.setChoice(newChoice)
                if (changed && newChoice != ControllerChoice.DISABLED && viewModel.controls.value.isEmpty()) {
                    snackbarHostState.showMessage(coroutineScope, tr("Connect a controller and use \"Map all, one by one\""))
                }
            },
        )
        ChoiceDescription(choice)

        val style = choice.style ?: return@ScreenContent

        if (choice == ControllerChoice.WIIMOTE) {
            SingleSelection(
                label = tr("Extension"),
                choice = wiimoteExtension,
                choices = listOf(
                    WiimoteExtension.NONE,
                    WiimoteExtension.NUNCHUK,
                    WiimoteExtension.MOTION_PLUS,
                    WiimoteExtension.MOTION_PLUS_NUNCHUK,
                ),
                choiceToString = ::wiimoteExtensionToString,
                description = tr("The game sees this extension plugged into the Wii Remote. The pointer needs a real Wii Remote, which Android can't use."),
                onChoiceChanged = viewModel::setWiimoteExtension,
            )
        }

        val nunchuk = WiimoteExtension.hasNunchuk(wiimoteExtension)
        val motionPlus = wiimoteExtension == WiimoteExtension.MOTION_PLUS || wiimoteExtension == WiimoteExtension.MOTION_PLUS_NUNCHUK
        ControllerIllustration(
            style = style,
            mappedIds = controls.keys,
            highlightedId = buttonToBind?.id,
            nunchuk = nunchuk,
            motionPlus = motionPlus,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally),
            onPartClick = { ids, _ -> viewModel.startBinding(ids) },
        )
        Text(
            text = tr("Tap a part of the drawing or a row below, then press the button or move the stick on your controller. Long press a row to clear it."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )

        Header(tr("Physical controller"))
        Button(
            label = if (controllers.isEmpty()) tr("No controller connected") else controllers.joinToString { it.name },
            description = if (controllers.isEmpty()) tr("Connect a controller by USB or Bluetooth; it is detected right away")
            else tr("Detected automatically. Tap to map every input from one of them"),
            onClick = { refreshControllers { showMapAllInputsDialog = true } },
        )
        Button(
            label = tr("Map all, one by one"),
            description = tr("Asks for each input in turn; skip the ones you don't need"),
            onClick = {
                viewModel.refreshAvailableControllers()
                viewModel.startBindingAll()
            },
        )
        Button(
            label = tr("Reset to automatic mapping"),
            description = if (choice == ControllerChoice.GAMECUBE) tr("GameCube layout on the connected controller")
            else tr("Same buttons as the connected controller"),
            onClick = {
                viewModel.refreshAvailableControllers()
                val name = viewModel.resetToAutomaticMapping()
                snackbarHostState.showMessage(
                    coroutineScope,
                    if (name != null) tr("Controller mapped automatically: {0}", name)
                    else tr("No controllers available"),
                )
            },
        )
        Button(
            label = tr("Controller settings"),
            description = tr("Dead zones, analog range, rumble and motion"),
            onClick = { refreshControllers { showControllerSettingsDialog = true } },
        )
        if (goToMacros != null) {
            Button(
                label = tr("Macros and turbo"),
                description = tr("Buttons of the physical controller that play a combo, a sequence or turbo"),
                onClick = goToMacros,
            )
        }

        Header(tr("Profiles"))
        Button(
            label = tr("Save as profile"),
            description = profileName?.let { tr("Current profile: {0}", it) }
                ?: tr("Keep this setup to load it later or to use it in a game's settings"),
            onClick = { showSaveProfileDialog = true },
        )
        Button(
            label = tr("Load profile"),
            description = if (profiles.isEmpty()) tr("No saved profiles yet") else tr("{0} saved profiles", profiles.size),
            onClick = {
                viewModel.refreshProfiles()
                showLoadProfileDialog = true
            },
        )
        if (profiles.isNotEmpty()) {
            Button(
                label = tr("Delete profile"),
                onClick = {
                    viewModel.refreshProfiles()
                    showDeleteProfileDialog = true
                },
            )
        }

        InputGroups(
            groups = inputGroupsFor(style, nunchuk),
            controlsMapping = controls,
            onInputClick = { id -> viewModel.startBinding(listOf(id)) },
            onInputLongClick = viewModel::clearButtonMapping,
        )

        if (choice == ControllerChoice.GAMEPAD) {
            Toggle(
                label = tr("Toggle screen"),
                description = tr("Makes the \"show screen\" button toggle between the TV and gamepad screens"),
                initialCheckedState = { NativeInput.getVPADScreenToggle(controllerIndex) },
                onCheckedChanged = { NativeInput.setVPADScreenToggle(controllerIndex, it) }
            )
        }
    }

    buttonToBind?.let {
        InputBindingPopup(
            buttonName = it.name,
            remaining = bindQueueSize,
            viewModel = viewModel,
            onClear = {
                viewModel.clearButtonMapping(it.id)
                viewModel.skipButtonToBind()
            },
            onSkip = viewModel::skipButtonToBind,
            onDismiss = viewModel::clearButtonToBind,
        )
    }

    if (showMapAllInputsDialog) {
        ControllerSelectDialog(
            controllers = controllers,
            onDismissRequest = { showMapAllInputsDialog = false },
            onSelect = { viewModel.mapAllInputs(it.id) },
        )
    }

    if (showControllerSettingsDialog) {
        ControllerSettingsDialog(
            controllers = controllers,
            controllerType = choice.nativeType,
            activeController = activeController,
            onSetActiveController = viewModel::setActiveController,
            onSetControllerSettings = viewModel::setControllerSettings,
            onDismissRequest = { showControllerSettingsDialog = false })
    }

    if (showSaveProfileDialog) {
        ProfileNameDialog(
            initialName = profileName ?: "",
            existing = profiles,
            isValid = viewModel::isValidProfileName,
            onDismiss = { showSaveProfileDialog = false },
            onSave = { name ->
                showSaveProfileDialog = false
                viewModel.save()
                snackbarHostState.showMessage(
                    coroutineScope,
                    if (viewModel.saveProfile(name)) tr("Profile saved: {0}", name.trim())
                    else tr("Could not save the profile"),
                )
            },
        )
    }

    if (showLoadProfileDialog) {
        ProfileListDialog(
            title = tr("Load profile"),
            profiles = profiles,
            onDismiss = { showLoadProfileDialog = false },
            onSelect = { name ->
                showLoadProfileDialog = false
                snackbarHostState.showMessage(
                    coroutineScope,
                    if (viewModel.loadProfile(name)) tr("Profile loaded: {0}", name)
                    else tr("Could not load the profile"),
                )
            },
        )
    }

    if (showDeleteProfileDialog) {
        ProfileListDialog(
            title = tr("Delete profile"),
            profiles = profiles,
            onDismiss = { showDeleteProfileDialog = false },
            onSelect = { name ->
                showDeleteProfileDialog = false
                profileToDelete = name
            },
        )
    }

    profileToDelete?.let { name ->
        FractalAlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(tr("Delete profile")) },
            text = { Text(tr("Delete the profile {0}? Games set to use it go back to the normal setup.", name)) },
            dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text(tr("No")) } },
            confirmButton = {
                TextButton(onClick = {
                    profileToDelete = null
                    viewModel.deleteProfile(name)
                }) { Text(tr("Yes")) }
            },
        )
    }
}

private fun wiimoteExtensionToString(extension: Int) = when (extension) {
    WiimoteExtension.NUNCHUK -> tr("Nunchuk")
    WiimoteExtension.MOTION_PLUS -> tr("MotionPlus")
    WiimoteExtension.MOTION_PLUS_NUNCHUK -> tr("MotionPlus and Nunchuk")
    else -> tr("None")
}

fun controllerChoiceName(choice: ControllerChoice) = when (choice) {
    ControllerChoice.DISABLED -> tr("Disabled")
    ControllerChoice.GAMEPAD -> tr("Wii U GamePad")
    ControllerChoice.PRO -> tr("Wii U Pro Controller")
    ControllerChoice.CLASSIC -> tr("Classic Controller")
    ControllerChoice.GAMECUBE -> tr("GameCube layout")
    ControllerChoice.WIIMOTE -> tr("Wii Remote")
}

private fun controllerChoiceShortDescription(choice: ControllerChoice) = when (choice) {
    ControllerChoice.DISABLED -> tr("Not connected")
    ControllerChoice.GAMEPAD -> tr("Screen, touch, motion")
    ControllerChoice.PRO -> tr("Default for gamepads")
    ControllerChoice.CLASSIC -> tr("Wii Remote + Classic")
    ControllerChoice.GAMECUBE -> tr("On a Classic Controller")
    ControllerChoice.WIIMOTE -> tr("Nunchuk, MotionPlus")
}

@Composable
private fun ChoiceDescription(choice: ControllerChoice) {
    val text = when (choice) {
        ControllerChoice.DISABLED -> tr("This player has no controller.")
        ControllerChoice.GAMEPAD -> tr("The Wii U GamePad: every game supports it, and some only work with it. The on-screen controls and the touch screen drive it.")
        ControllerChoice.PRO -> tr("The Wii U Pro Controller, the default for physical gamepads. Many games support it, but some only accept the Wii U GamePad or the Wii Remote: switch to those here or with a profile in the game's settings.")
        ControllerChoice.CLASSIC -> tr("Classic Controller Pro, connected to a Wii Remote. Used by Wii U games that support it and by Virtual Console games.")
        ControllerChoice.GAMECUBE -> tr("The Wii U only takes GameCube controllers through the USB adapter, which Cemu does not emulate. This option uses a Classic Controller (supported by Smash Bros. and others) laid out like a GameCube pad: A at the bottom, B left, X right, Y top, Z on the right bumper, L and R on the triggers.")
        ControllerChoice.WIIMOTE -> tr("Wii Remote with optional Nunchuk or MotionPlus, for games that need it. Hold the controller sideways or use the Nunchuk as you would on the Wii U.")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = FractalTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

/** Cards for every emulated controller, the selected one highlighted. */
@Composable
private fun ControllerChoicePicker(
    selected: ControllerChoice,
    isAllowed: (ControllerChoice) -> Boolean,
    onSelect: (ControllerChoice) -> Unit,
) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ControllerChoice.entries.forEach { choice ->
            val isSelected = choice == selected
            val allowed = isAllowed(choice)
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(shape)
                    .background(if (isSelected) colors.accent.copy(alpha = 0.18f) else colors.surface)
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.accent else colors.border, shape)
                    .clickable(enabled = allowed) { onSelect(choice) }
                    .alpha(if (allowed) 1f else 0.4f)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val style = choice.style
                if (style != null) {
                    ControllerIllustration(
                        style = style,
                        mappedIds = emptySet(),
                        highlightedId = null,
                        nunchuk = true,
                    )
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                        Text("—", color = colors.textSecondary, fontSize = 24.sp)
                    }
                }
                Text(
                    controllerChoiceName(choice),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) colors.accent else colors.text,
                    maxLines = 1,
                )
                Text(
                    controllerChoiceShortDescription(choice),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ControllerSettingsDialog(
    onDismissRequest: () -> Unit,
    controllerType: Int,
    activeController: ActiveController?,
    onSetActiveController: (InputController) -> Unit,
    onSetControllerSettings: (InputController, NativeInput.ControllerSettings) -> Unit,
    controllers: List<InputController>,
) {
    fun updateSettings(settings: NativeInput.ControllerSettings) {
        val (controller, _) = activeController ?: return
        onSetControllerSettings(controller, settings)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = null,
                    )
                }
                Text(
                    text = tr("Controller settings"),
                    fontSize = 18.sp,
                )
            }

            HorizontalDivider()

            // the dialog is its own window: its settings rows must not blur the main window's backdrop
            CompositionLocalProvider(LocalBackdrop provides null) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    SingleSelection(
                        label = tr("Controller"),
                        choice = activeController?.controller,
                        choices = controllers,
                        choiceToString = { it?.name ?: "" },
                        onChoiceChanged = { onSetActiveController(it!!) },
                    )

                    val (controller, settings) = activeController ?: return@Column

                    // the GamePad and the Wii Remote (MotionPlus) take motion; the other controllers have none
                    val typeHasMotion = controllerType == EmulatedControllerType.VPAD || controllerType == EmulatedControllerType.WIIMOTE
                    val canUseMotion = typeHasMotion && controller.hasMotion

                    Toggle(
                        label = tr("Use motion"),
                        checked = settings.motion && canUseMotion,
                        onCheckedChanged = { updateSettings(settings.copy(motion = it)) },
                        enabled = canUseMotion,
                        description = when {
                            !typeHasMotion -> tr("Requires Wii U GamePad or Wii Remote")
                            !controller.hasMotion -> tr("Controller has no motion sensors")
                            else -> null
                        },
                    )

                    Slider(
                        label = tr("Rumble"),
                        enabled = controller.hasRumble,
                        value = (settings.rumble * 100f).fastRoundToInt(),
                        valueFrom = 0,
                        steps = 19,
                        valueTo = 100,
                        labelFormatter = { "$it%" },
                        onValueChange = {
                            val rumble = it / 100f

                            updateSettings(settings.copy(rumble = rumble))

                            val amplitude = (255 * rumble).toInt()
                            InputDevice.getDevice(controller.id)?.tryUseVibrator {
                                cancel()
                                if (amplitude in 1..255) {
                                    vibrate(VibrationEffect.createOneShot(500L, amplitude))
                                }
                            }
                        },
                    )

                    AxisGroup(
                        label = tr("Left stick"),
                        values = settings.axis,
                        onValuesChanged = { updateSettings(settings.copy(axis = it)) },
                    )

                    AxisGroup(
                        label = tr("Right stick"),
                        values = settings.rotation,
                        onValuesChanged = { updateSettings(settings.copy(rotation = it)) },
                    )

                    AxisGroup(
                        label = tr("Analog triggers"),
                        values = settings.trigger,
                        onValuesChanged = { updateSettings(settings.copy(trigger = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisGroup(
    label: String,
    values: NativeInput.AxisSetting,
    onValuesChanged: (NativeInput.AxisSetting) -> Unit
) {
    Header(label)
    Slider(
        label = tr("Deadzone"),
        value = (values.deadzone * 100f).fastRoundToInt(),
        valueFrom = 0,
        valueTo = 100,
        labelFormatter = { "$it%" },
        onValueChange = { onValuesChanged(values.copy(deadzone = it / 100f)) },
    )
    Slider(
        label = tr("Range"),
        value = (values.range * 100f).fastRoundToInt(),
        valueFrom = 50,
        valueTo = 200,
        labelFormatter = { "$it%" },
        onValueChange = { onValuesChanged(values.copy(range = it / 100f)) },
    )
}

@Composable
private fun ControllerSelectDialog(
    controllers: List<InputController>,
    onDismissRequest: () -> Unit,
    onSelect: (InputController) -> Unit,
) {
    PickListDialog(
        title = tr("Select a controller"),
        items = controllers,
        label = { it.name },
        onDismiss = onDismissRequest,
        onSelect = {
            onSelect(it)
            onDismissRequest()
        },
    )
}

@Composable
private fun ProfileListDialog(
    title: String,
    profiles: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PickListDialog(title = title, items = profiles, label = { it }, onDismiss = onDismiss, onSelect = onSelect)
}

@Composable
private fun <T> PickListDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp)
                    .weight(weight = 1.0f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (items.isEmpty()) {
                    Text(tr("Nothing here yet"), modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp))
                }
                items.forEach {
                    Text(text = label(it), modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(it) }
                        .padding(vertical = 16.dp, horizontal = 8.dp))
                }
            }

            HorizontalDivider()

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.End),
            ) {
                Text(tr("Cancel"))
            }
        }
    }
}

@Composable
private fun ProfileNameDialog(
    initialName: String,
    existing: List<String>,
    isValid: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val valid = isValid(name)
    val overwrites = existing.any { it.equals(name.trim(), ignoreCase = true) }
    FractalAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Save as profile")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(tr("Profile name")) },
                    isError = name.isNotEmpty() && !valid,
                )
                when {
                    name.isNotEmpty() && !valid -> Text(tr("Use letters, numbers and spaces; \"controller1\" style names are reserved."))
                    overwrites -> Text(tr("A profile with this name exists and will be replaced."))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = valid) { Text(tr("Save")) } },
    )
}

@Composable
private fun InputBindingPopup(
    buttonName: String,
    remaining: Int,
    viewModel: ControllersViewModel,
    onClear: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        GamepadInputSource.events.collect { event ->
            when (event) {
                is GamepadInputSource.InputEvent.Key -> viewModel.mapKeyEvent(event.keyEvent)
                is GamepadInputSource.InputEvent.Motion -> viewModel.tryMapMotionEvent(event.motionEvent)
            }
        }
    }

    Popup(alignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = tr("Input binding"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = tr("Trigger an input to bind it to {0}", buttonName),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (remaining > 0) {
                        Text(
                            text = tr("{0} more after this one", remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = FractalTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClear) { Text(tr("Clear")) }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (remaining > 0) {
                            TextButton(onClick = onSkip) { Text(tr("Skip")) }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
                    }
                }
            }
        }
    }
}

/** Display name of slot [index]'s emulated controller for lists ("Wii U Pro Controller", "GameCube layout"...). */
fun slotControllerName(context: Context, index: Int): String = controllerChoiceName(
    ControllerChoice.of(NativeInput.getControllerType(index), AutoControllerMapping.isGameCubeLayout(context, index))
)
