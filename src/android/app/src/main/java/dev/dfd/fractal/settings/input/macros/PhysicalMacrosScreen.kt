package dev.dfd.fractal.settings.input.macros

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.android.inputevent.isFromPhysicalController
import dev.dfd.fractal.common.android.keyevent.keyCodeToDisplayName
import dev.dfd.fractal.common.input.GamepadInputSource
import dev.dfd.fractal.common.input.PhysicalMacro
import dev.dfd.fractal.common.input.TriggerRecorder
import dev.dfd.fractal.common.input.analogVirtualKeys
import dev.dfd.fractal.common.input.macroButtonsFor
import dev.dfd.fractal.common.input.shortName
import dev.dfd.fractal.common.settings.ButtonBehavior
import dev.dfd.fractal.common.settings.DEFAULT_TURBO_HZ
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.MacroStep
import dev.dfd.fractal.common.settings.OverlayMacroInput
import dev.dfd.fractal.common.settings.TURBO_MAX_HZ
import dev.dfd.fractal.common.settings.TURBO_MIN_HZ
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Slider
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.fractal.SettingsRowText
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.nativeinterface.NativeInput
import dev.dfd.fractal.nativeinterface.NativeInput.EmulatedControllerType
import dev.dfd.fractal.settings.input.controller.slotControllerName

private const val MAX_EDITOR_STEP_MS = 2000

/**
 * Macros and turbo on buttons of the physical controllers: a trigger (one button or a combination)
 * runs a combo, a sequence or turbo on an emulated controller while a game runs.
 * [controllerIndex] is the slot new macros press their buttons on.
 */
@Composable
fun PhysicalMacrosScreen(
    navigateBack: () -> Unit,
    controllerIndex: Int = 0,
    viewModel: PhysicalMacrosViewModel = viewModel(),
) {
    val context = LocalContext.current
    val macros by viewModel.macros.collectAsState()
    var editing by remember { mutableStateOf<PhysicalMacro?>(null) }

    // the editor is part of this screen, not a dialog window: trigger capture needs the activity's key events
    editing?.let { macro ->
        BackHandler { editing = null }
        MacroEditor(
            initial = macro,
            isNew = macros.none { it.id == macro.id },
            onDismiss = { editing = null },
            onSave = {
                viewModel.upsert(it)
                editing = null
            },
            onDelete = {
                viewModel.delete(macro.id)
                editing = null
            },
        )
        return
    }

    ScreenContent(
        appBarText = tr("Macros and turbo"),
        navigateBack = navigateBack,
    ) {
        Text(
            text = tr("Pick a button or a combination of your physical controller and what it does in the game. A single button only runs the macro; the buttons of a combination still do their normal job too."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Button(
            label = tr("Add turbo"),
            description = tr("Holding the trigger presses a button many times a second"),
            onClick = {
                editing = PhysicalMacro(
                    controllerIndex = controllerIndex,
                    kind = MacroKind.COMBO,
                    buttons = listOf(OverlayMacroInput.A),
                    behavior = ButtonBehavior(turbo = true, turboHz = DEFAULT_TURBO_HZ),
                )
            },
        )
        Button(
            label = tr("Add combo"),
            description = tr("The trigger presses several buttons together"),
            onClick = { editing = PhysicalMacro(controllerIndex = controllerIndex, kind = MacroKind.COMBO) },
        )
        Button(
            label = tr("Add sequence"),
            description = tr("The trigger plays steps one after the other"),
            onClick = {
                editing = PhysicalMacro(
                    controllerIndex = controllerIndex,
                    kind = MacroKind.SEQUENCE,
                    steps = listOf(MacroStep()),
                )
            },
        )

        Header(tr("Your macros"))
        if (macros.isEmpty()) {
            Text(
                text = tr("No macros yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = FractalTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        macros.forEach { macro ->
            SettingsCard(onClick = { editing = macro }, role = Role.Button) {
                SettingsRowText(
                    title = macroTitle(macro),
                    description = macroSummary(macro, slotControllerName(context, macro.controllerIndex)),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = macro.enabled, onCheckedChange = { viewModel.setEnabled(macro.id, it) })
            }
        }
    }
}

private fun triggerText(trigger: Set<Int>): String =
    if (trigger.isEmpty()) tr("No trigger") else trigger.joinToString(" + ") { keyCodeToDisplayName(it) }

private fun macroTitle(macro: PhysicalMacro): String = macro.label.ifBlank {
    when {
        macro.kind == MacroKind.SEQUENCE -> tr("Sequence")
        macro.behavior.turbo -> tr("Turbo {0}", macro.buttons.joinToString(" + ") { it.shortName() })
        else -> macro.buttons.joinToString(" + ") { it.shortName() }.ifEmpty { tr("Combo") }
    }
}

private fun macroSummary(macro: PhysicalMacro, slotName: String): String = buildList {
    add(triggerText(macro.trigger))
    add(tr("Controller {0} ({1})", macro.controllerIndex + 1, slotName))
    when (macro.kind) {
        MacroKind.COMBO -> if (macro.behavior.turbo) add(tr("{0} presses a second", macro.behavior.turboHz))
        MacroKind.SEQUENCE -> add(tr("{0} steps", macro.steps.size) + if (macro.behavior.turbo) " · " + tr("repeats") else "")
    }
    if (macro.behavior.toggle) add(tr("toggle"))
    if (macro.deviceDescriptor != null) macro.deviceName?.let { add(it) }
}.joinToString(" · ")

@Composable
private fun MacroEditor(
    initial: PhysicalMacro,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (PhysicalMacro) -> Unit,
    onDelete: () -> Unit,
) {
    var macro by remember(initial.id) { mutableStateOf(initial) }
    // controller the trigger was recorded on, for "Only on"
    var recordedDescriptor by remember(initial.id) { mutableStateOf(initial.deviceDescriptor) }
    var capturing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val slots = remember {
        (0..<NativeInput.MAX_CONTROLLERS).filter {
            NativeInput.getControllerType(it) != EmulatedControllerType.DISABLED || it == initial.controllerIndex
        }
    }
    val controllerType = remember(macro.controllerIndex) { NativeInput.getControllerType(macro.controllerIndex) }
    val availableButtons = remember(controllerType) { macroButtonsFor(controllerType) }

    ScreenContent(
        appBarText = if (isNew) tr("New macro") else tr("Edit macro"),
        navigateBack = onDismiss,
        actions = {
            if (!isNew) {
                TextButton(onClick = onDelete) { Text(tr("Delete")) }
            }
            TextButton(onClick = { onSave(macro) }, enabled = macro.trigger.isNotEmpty()) { Text(tr("Save")) }
        },
    ) {
        OutlinedTextField(
            value = macro.label,
            onValueChange = { macro = macro.copy(label = it) },
            singleLine = true,
            label = { Text(tr("Name (optional)")) },
            modifier = Modifier.fillMaxWidth(),
        )

        Header(tr("Trigger"))
        Button(
            label = triggerText(macro.trigger),
            description = tr("Tap, then press the button or buttons on your controller and let go"),
            onClick = { capturing = true },
        )
        recordedDescriptor?.let { descriptor ->
            Toggle(
                label = tr("Only on {0}", macro.deviceName ?: ""),
                description = tr("Off: the trigger works on any connected controller"),
                checked = macro.deviceDescriptor != null,
                onCheckedChanged = { only -> macro = macro.copy(deviceDescriptor = if (only) descriptor else null) },
            )
        }

        Header(tr("Does"))
        SingleSelection(
            label = tr("Emulated controller"),
            choice = macro.controllerIndex,
            choices = slots,
            choiceToString = { tr("Controller {0} ({1})", it + 1, slotControllerName(context, it)) },
            onChoiceChanged = { index ->
                val allowed = macroButtonsFor(NativeInput.getControllerType(index)).toSet()
                macro = macro.copy(
                    controllerIndex = index,
                    buttons = macro.buttons.filter { it in allowed },
                    steps = macro.steps.map { step -> step.copy(buttons = step.buttons.filter { it in allowed }) },
                )
            },
        )
        SingleSelection(
            label = tr("Kind"),
            choice = macro.kind,
            choices = MacroKind.entries,
            choiceToString = {
                when (it) {
                    MacroKind.COMBO -> tr("Combo: buttons together")
                    MacroKind.SEQUENCE -> tr("Sequence: steps in order")
                }
            },
            onChoiceChanged = { kind ->
                val steps = if (kind == MacroKind.SEQUENCE && macro.steps.isEmpty()) listOf(MacroStep(buttons = macro.buttons)) else macro.steps
                macro = macro.copy(kind = kind, steps = steps)
            },
        )

        when (macro.kind) {
            MacroKind.COMBO -> {
                Text(
                    text = tr("Buttons pressed together"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ButtonChips(
                    available = availableButtons,
                    selected = macro.buttons,
                    onChange = { macro = macro.copy(buttons = it) },
                )
            }

            MacroKind.SEQUENCE -> {
                macro.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tr("Step {0}", index + 1),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                        )
                        TextButton(onClick = {
                            macro = macro.copy(steps = macro.steps.toMutableList().apply { removeAt(index) })
                        }) { Text(tr("Remove")) }
                    }
                    ButtonChips(
                        available = availableButtons,
                        selected = step.buttons,
                        onChange = { buttons ->
                            macro = macro.copy(steps = macro.steps.toMutableList().apply { set(index, step.copy(buttons = buttons)) })
                        },
                    )
                    Slider(
                        label = tr("Duration"),
                        value = step.durationMs.coerceIn(MacroStep.MIN_STEP_MS, MAX_EDITOR_STEP_MS),
                        valueFrom = MacroStep.MIN_STEP_MS,
                        valueTo = MAX_EDITOR_STEP_MS,
                        labelFormatter = { "$it ms" },
                        onValueChange = { ms ->
                            macro = macro.copy(steps = macro.steps.toMutableList().apply { set(index, step.copy(durationMs = ms)) })
                        },
                    )
                }
                Button(
                    label = tr("Add step"),
                    description = tr("A step with no buttons is a pause that releases everything"),
                    onClick = { macro = macro.copy(steps = macro.steps + MacroStep()) },
                )
            }
        }

        Header(tr("Behavior"))
        Toggle(
            label = if (macro.kind == MacroKind.SEQUENCE) tr("Repeat") else tr("Turbo"),
            description = if (macro.kind == MacroKind.SEQUENCE) tr("Plays the sequence again and again while the trigger is held")
            else tr("Presses and releases the buttons quickly while the trigger is held"),
            checked = macro.behavior.turbo,
            onCheckedChanged = { macro = macro.copy(behavior = macro.behavior.copy(turbo = it)) },
        )
        if (macro.kind == MacroKind.COMBO && macro.behavior.turbo) {
            Slider(
                label = tr("Turbo speed"),
                value = macro.behavior.turboHz.coerceIn(TURBO_MIN_HZ, TURBO_MAX_HZ),
                valueFrom = TURBO_MIN_HZ,
                valueTo = TURBO_MAX_HZ,
                labelFormatter = { tr("{0}/s", it) },
                onValueChange = { macro = macro.copy(behavior = macro.behavior.copy(turboHz = it)) },
            )
        }
        Toggle(
            label = tr("Toggle"),
            description = tr("One press turns the macro on, the next one off"),
            checked = macro.behavior.toggle,
            onCheckedChanged = { macro = macro.copy(behavior = macro.behavior.copy(toggle = it)) },
        )
        Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (capturing) {
        TriggerCapturePopup(
            onDismiss = { capturing = false },
            onCaptured = { keys, descriptor, name ->
                capturing = false
                recordedDescriptor = descriptor
                macro = macro.copy(
                    trigger = keys,
                    deviceName = name,
                    deviceDescriptor = if (macro.deviceDescriptor != null) descriptor else null,
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonChips(
    available: List<OverlayMacroInput>,
    selected: List<OverlayMacroInput>,
    onChange: (List<OverlayMacroInput>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        available.forEach { button ->
            val isSelected = button in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - button else selected + button) },
                label = { Text(button.shortName()) },
            )
        }
    }
}

/** Records a trigger from the activity's controller events; a popup keeps those events in the activity. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriggerCapturePopup(
    onDismiss: () -> Unit,
    onCaptured: (keys: Set<Int>, descriptor: String, deviceName: String) -> Unit,
) {
    val recorder = remember { TriggerRecorder() }
    val held = remember { mutableStateMapOf<Int, Boolean>() }
    val analogByDevice = remember { HashMap<String, Set<Int>>() }

    fun feed(descriptor: String, name: String, keyCode: Int, down: Boolean) {
        if (down && (recorder.deviceDescriptor == null || recorder.deviceDescriptor == descriptor)) held[keyCode] = true
        val finished = recorder.onKey(descriptor, keyCode, down)
        if (finished != null) onCaptured(finished, descriptor, name)
    }

    LaunchedEffect(Unit) {
        GamepadInputSource.events.collect { event ->
            when (event) {
                is GamepadInputSource.InputEvent.Key -> {
                    val keyEvent = event.keyEvent
                    val device = keyEvent.device
                    if (!keyEvent.isFromPhysicalController() || device == null || keyEvent.repeatCount > 0) return@collect
                    when (keyEvent.action) {
                        KeyEvent.ACTION_DOWN -> feed(device.descriptor, device.name, keyEvent.keyCode, true)
                        KeyEvent.ACTION_UP -> feed(device.descriptor, device.name, keyEvent.keyCode, false)
                    }
                }

                is GamepadInputSource.InputEvent.Motion -> {
                    val motion = event.motionEvent
                    val device = motion.device
                    if (!motion.isFromPhysicalController() || device == null) return@collect
                    val keys = analogVirtualKeys(
                        leftTrigger = maxOf(motion.getAxisValue(MotionEvent.AXIS_LTRIGGER), motion.getAxisValue(MotionEvent.AXIS_BRAKE)),
                        rightTrigger = maxOf(motion.getAxisValue(MotionEvent.AXIS_RTRIGGER), motion.getAxisValue(MotionEvent.AXIS_GAS)),
                        hatX = motion.getAxisValue(MotionEvent.AXIS_HAT_X),
                        hatY = motion.getAxisValue(MotionEvent.AXIS_HAT_Y),
                    )
                    val before = analogByDevice[device.descriptor].orEmpty()
                    if (keys == before) return@collect
                    analogByDevice[device.descriptor] = keys
                    (keys - before).forEach { feed(device.descriptor, device.name, it, true) }
                    (before - keys).forEach { feed(device.descriptor, device.name, it, false) }
                }
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
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .sizeIn(maxWidth = 560.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(tr("Press the trigger"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(tr("Press one button, or hold several together, on your controller, then let go."))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        held.keys.forEach { key ->
                            FilterChip(selected = true, onClick = {}, label = { Text(keyCodeToDisplayName(key)) })
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(tr("Cancel")) }
                }
            }
        }
    }
}
