package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.ButtonBehavior
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.MacroStep
import dev.dfd.fractal.common.settings.OVERLAY_SCALE_MAX
import dev.dfd.fractal.common.settings.OVERLAY_SCALE_MIN
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayLayoutProfile
import dev.dfd.fractal.common.settings.OverlayMacro
import dev.dfd.fractal.common.settings.OverlayMacroInput
import dev.dfd.fractal.common.settings.TURBO_MAX_HZ
import dev.dfd.fractal.common.settings.TURBO_MIN_HZ
import dev.dfd.fractal.common.settings.activeProfile
import dev.dfd.fractal.common.settings.profilesFor
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.StatusChip
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.common.settings.OverlayInputStyle
import dev.dfd.fractal.common.settings.OverlayShortcut
import dev.dfd.fractal.emulation.input.shortLabel
import dev.dfd.fractal.emulation.inputoverlay.OverlayTarget
import dev.dfd.fractal.emulation.inputoverlay.displayName
import dev.dfd.fractal.emulation.inputoverlay.styleKey
import dev.dfd.fractal.emulation.input.displayName as hotkeyDisplayName
import dev.dfd.fractal.emulation.inputoverlay.macroInputsFor
import kotlin.math.roundToInt

/** Toggle/turbo switches shared by buttons and macros. */
@Composable
fun BehaviorEditor(
    behavior: ButtonBehavior,
    onChange: (ButtonBehavior) -> Unit,
    isSequence: Boolean = false,
) {
    OverlayToggleRow(
        title = tr("Toggle"),
        description = tr("One tap keeps it pressed, the next tap releases it"),
        checked = behavior.toggle,
        onCheckedChange = { onChange(behavior.copy(toggle = it)) },
    )
    if (isSequence) {
        OverlayToggleRow(
            title = tr("Repeat"),
            description = tr("Plays the sequence again and again while held or toggled on"),
            checked = behavior.turbo,
            onCheckedChange = { onChange(behavior.copy(turbo = it)) },
        )
        return
    }
    OverlayToggleRow(
        title = tr("Turbo"),
        description = tr("While held or toggled on, presses and releases repeatedly"),
        checked = behavior.turbo,
        onCheckedChange = { onChange(behavior.copy(turbo = it)) },
    )
    if (behavior.turbo) {
        OverlaySliderRow(
            title = tr("Turbo rate"),
            value = behavior.turboHz.toFloat(),
            valueRange = TURBO_MIN_HZ.toFloat()..TURBO_MAX_HZ.toFloat(),
            steps = TURBO_MAX_HZ - TURBO_MIN_HZ - 1,
            valueText = { tr("{0} per second", it.roundToInt()) },
            onValueChangeFinished = { onChange(behavior.copy(turboHz = it.roundToInt())) },
        )
    }
}

/** Options of one overlay input, opened by a long press in edit mode. */
@Composable
fun InputOptionsDialog(
    target: OverlayTarget,
    settings: InputOverlaySettings,
    controllerType: OverlayControllerType,
    onBehaviorChange: (ButtonBehavior) -> Unit,
    onHide: () -> Unit,
    onEditMacro: (OverlayMacro) -> Unit,
    onDeleteMacro: (OverlayMacro) -> Unit,
    onDismiss: () -> Unit,
    onStyleChange: (OverlayInputStyle) -> Unit = {},
    onStylePreview: (OverlayInputStyle) -> Unit = {},
    onEditShortcut: (OverlayShortcut) -> Unit = {},
    onDeleteShortcut: (OverlayShortcut) -> Unit = {},
) {
    val profile = settings.activeProfile(controllerType)
    var confirmDelete by remember { mutableStateOf(false) }
    val inputStyle = profile.styles[target.styleKey] ?: OverlayInputStyle()

    @Composable
    fun StyleSection(canChangeShape: Boolean) {
        SectionLabel(tr("Look"))
        InputStyleEditor(
            style = inputStyle,
            globalAlpha = settings.alpha,
            canChangeShape = canChangeShape,
            onPreview = onStylePreview,
            onChange = onStyleChange,
        )
    }
    when (target) {
        is OverlayTarget.Builtin -> {
            val config = target.config
            OverlayDialog(
                title = config.displayName(),
                onDismiss = onDismiss,
                actions = {
                    DialogTextButton(tr("Hide"), onHide)
                    DialogTextButton(tr("Close"), onDismiss)
                },
            ) {
                if (config.isButton) {
                    BehaviorEditor(
                        behavior = profile.behaviors[config] ?: ButtonBehavior(),
                        onChange = onBehaviorChange,
                    )
                } else {
                    Text(
                        tr("Sticks and the D-pad have no button options. Drag to move, pinch or use Resize to change the size."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FractalTheme.colors.textSecondary,
                    )
                }
                StyleSection(canChangeShape = config.isButton)
                Text(
                    tr("Hidden inputs can be shown again in the input overlay settings."),
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                )
            }
        }

        is OverlayTarget.Macro -> {
            val macro = profile.macros.firstOrNull { it.id == target.id }
            if (macro == null) {
                // deleted meanwhile
                LaunchedEffect(Unit) { onDismiss() }
                return
            }
            OverlayDialog(
                title = tr("Macro {0}", macro.label),
                onDismiss = onDismiss,
                actions = {
                    DialogTextButton(tr("Delete"), { confirmDelete = true }, destructive = true)
                    DialogTextButton(tr("Edit"), { onEditMacro(macro) })
                    DialogTextButton(tr("Close"), onDismiss)
                },
            ) {
                Text(macro.summary(), style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.textSecondary)
                BehaviorEditor(
                    behavior = macro.behavior,
                    onChange = onBehaviorChange,
                    isSequence = macro.kind == MacroKind.SEQUENCE,
                )
                StyleSection(canChangeShape = true)
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = tr("Delete macro"),
                    message = tr("Delete the macro \"{0}\"?", macro.label),
                    confirmText = tr("Delete"),
                    onConfirm = {
                        confirmDelete = false
                        onDeleteMacro(macro)
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }

        is OverlayTarget.Shortcut -> {
            val shortcut = profile.shortcuts.firstOrNull { it.id == target.id }
            if (shortcut == null) {
                LaunchedEffect(Unit) { onDismiss() }
                return
            }
            val name = shortcut.label.ifBlank { shortcut.action.shortLabel() }
            OverlayDialog(
                title = tr("Shortcut {0}", name),
                onDismiss = onDismiss,
                actions = {
                    DialogTextButton(tr("Delete"), { confirmDelete = true }, destructive = true)
                    DialogTextButton(tr("Edit"), { onEditShortcut(shortcut) })
                    DialogTextButton(tr("Close"), onDismiss)
                },
            ) {
                Text(
                    tr("Runs: {0}", shortcut.action.hotkeyDisplayName()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FractalTheme.colors.textSecondary,
                )
                StyleSection(canChangeShape = true)
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = tr("Delete shortcut"),
                    message = tr("Delete the shortcut \"{0}\"?", name),
                    confirmText = tr("Delete"),
                    onConfirm = {
                        confirmDelete = false
                        onDeleteShortcut(shortcut)
                    },
                    onDismiss = { confirmDelete = false },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = FractalTheme.colors.accent,
        modifier = Modifier.padding(top = 6.dp),
    )
}

fun OverlayMacro.summary(): String = when (kind) {
    MacroKind.COMBO -> tr("Presses together: {0}", buttons.joinToString(" + ") { it.displayName() })
    MacroKind.SEQUENCE -> tr("Sequence of {0} steps", steps.size)
}

private fun defaultMacroLabel(kind: MacroKind, buttons: List<OverlayMacroInput>, steps: List<MacroStep>): String =
    when (kind) {
        MacroKind.COMBO -> buttons.joinToString("+") { it.displayName() }
        MacroKind.SEQUENCE -> steps.firstOrNull { it.buttons.isNotEmpty() }
            ?.let { first -> first.buttons.joinToString("+") { it.displayName() } + "…" } ?: "SEQ"
    }.take(MAX_LABEL_LENGTH)

private const val MAX_LABEL_LENGTH = 12

/** Creates ([initial] null) or edits a macro. */
@Composable
fun MacroEditorDialog(
    initial: OverlayMacro?,
    controllerType: OverlayControllerType,
    onSave: (OverlayMacro) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = remember(controllerType) { macroInputsFor(controllerType) }
    val key = initial?.id
    var label by remember(key) { mutableStateOf(initial?.label ?: "") }
    var kind by remember(key) { mutableStateOf(initial?.kind ?: MacroKind.COMBO) }
    val comboButtons = remember(key) { (initial?.buttons ?: emptyList()).toMutableStateList() }
    val steps = remember(key) {
        (initial?.steps?.takeIf { it.isNotEmpty() } ?: listOf(MacroStep())).toMutableStateList()
    }
    // durations are edited as text so the field can be empty while typing
    val durationTexts = remember(key) { steps.map { it.durationMs.toString() }.toMutableStateList() }
    var behavior by remember(key) { mutableStateOf(initial?.behavior ?: ButtonBehavior()) }

    val canSave = when (kind) {
        MacroKind.COMBO -> comboButtons.isNotEmpty()
        MacroKind.SEQUENCE -> steps.any { it.buttons.isNotEmpty() }
    }

    fun save() {
        val finalSteps = steps.mapIndexed { i, step ->
            step.copy(
                durationMs = (durationTexts[i].toIntOrNull() ?: MacroStep.DEFAULT_STEP_MS)
                    .coerceIn(MacroStep.MIN_STEP_MS, MacroStep.MAX_STEP_MS)
            )
        }
        val finalLabel = label.trim().ifBlank { defaultMacroLabel(kind, comboButtons, finalSteps) }
        val base = initial ?: OverlayMacro(label = finalLabel)
        onSave(
            base.copy(
                label = finalLabel,
                kind = kind,
                buttons = if (kind == MacroKind.COMBO) comboButtons.toList() else emptyList(),
                steps = if (kind == MacroKind.SEQUENCE) finalSteps else emptyList(),
                behavior = behavior,
            )
        )
    }

    OverlayDialog(
        title = if (initial == null) tr("New macro") else tr("Edit macro"),
        onDismiss = onDismiss,
        actions = {
            DialogTextButton(tr("Cancel"), onDismiss)
            DialogTextButton(tr("Save"), ::save, enabled = canSave)
        },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
            singleLine = true,
            label = { Text(tr("Label on the button")) },
            placeholder = { Text(defaultMacroLabel(kind, comboButtons, steps)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OverlayChip(tr("Combo"), kind == MacroKind.COMBO, { kind = MacroKind.COMBO })
            OverlayChip(tr("Sequence"), kind == MacroKind.SEQUENCE, { kind = MacroKind.SEQUENCE })
        }
        when (kind) {
            MacroKind.COMBO -> {
                Text(
                    tr("Buttons pressed together while the macro is held"),
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                )
                ButtonPicker(available, comboButtons) { button ->
                    if (button in comboButtons) comboButtons.remove(button) else comboButtons.add(button)
                }
            }

            MacroKind.SEQUENCE -> {
                Text(
                    tr("Steps play one after the other on each press. A step without buttons is a pause."),
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                )
                steps.forEachIndexed { index, step ->
                    SequenceStepCard(
                        index = index,
                        step = step,
                        durationText = durationTexts[index],
                        available = available,
                        canMoveUp = index > 0,
                        canMoveDown = index < steps.size - 1,
                        canDelete = steps.size > 1,
                        onToggleButton = { button ->
                            val buttons = step.buttons
                            steps[index] = step.copy(
                                buttons = if (button in buttons) buttons - button else buttons + button
                            )
                        },
                        onDurationChange = { text -> durationTexts[index] = text.filter { it.isDigit() }.take(5) },
                        onMove = { delta ->
                            val other = index + delta
                            val s = steps[index]
                            steps[index] = steps[other]
                            steps[other] = s
                            val d = durationTexts[index]
                            durationTexts[index] = durationTexts[other]
                            durationTexts[other] = d
                        },
                        onDelete = {
                            steps.removeAt(index)
                            durationTexts.removeAt(index)
                        },
                    )
                }
                FractalButton(
                    tr("Add step"),
                    onClick = {
                        steps.add(MacroStep())
                        durationTexts.add(MacroStep.DEFAULT_STEP_MS.toString())
                    },
                    icon = FractalIcons.FolderAdd,
                )
            }
        }
        BehaviorEditor(behavior, { behavior = it }, isSequence = kind == MacroKind.SEQUENCE)
        Text(
            tr("New macros appear in the middle of the screen; move them in edit mode."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ButtonPicker(
    available: List<OverlayMacroInput>,
    selected: List<OverlayMacroInput>,
    onToggle: (OverlayMacroInput) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (button in available) {
            OverlayChip(button.displayName(), button in selected, { onToggle(button) })
        }
    }
}

@Composable
private fun SequenceStepCard(
    index: Int,
    step: MacroStep,
    durationText: String,
    available: List<OverlayMacroInput>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onToggleButton: (OverlayMacroInput) -> Unit,
    onDurationChange: (String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val shape = FractalTheme.shapes.card
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(shape, FractalTheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Step {0}", index + 1),
                style = MaterialTheme.typography.titleSmall,
                color = FractalTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = onDurationChange,
                singleLine = true,
                suffix = { Text("ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            if (canMoveUp) {
                FractalIconButton(FractalIcons.Back, tr("Move up"), { onMove(-1) }, Modifier.rotate(90f))
            }
            if (canMoveDown) {
                FractalIconButton(FractalIcons.Next, tr("Move down"), { onMove(1) }, Modifier.rotate(90f))
            }
            if (canDelete) {
                FractalIconButton(FractalIcons.Trash, tr("Delete step"), onDelete)
            }
        }
        ButtonPicker(available, step.buttons, onToggleButton)
    }
}

/** Profiles of one controller type: select, create, rename, duplicate, delete. */
@Composable
fun ProfileList(
    settings: InputOverlaySettings,
    controllerType: OverlayControllerType,
    onSelect: (OverlayLayoutProfile) -> Unit,
    onCreate: (name: String, copyFrom: OverlayLayoutProfile?) -> Unit,
    onRename: (OverlayLayoutProfile, String) -> Unit,
    onDelete: (OverlayLayoutProfile) -> Unit,
) {
    val profiles = settings.profilesFor(controllerType)
    val active = settings.activeProfile(controllerType)
    var renaming by remember { mutableStateOf<OverlayLayoutProfile?>(null) }
    var duplicating by remember { mutableStateOf<OverlayLayoutProfile?>(null) }
    var deleting by remember { mutableStateOf<OverlayLayoutProfile?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (profile in profiles) {
            ProfileRow(
                profile = profile,
                isActive = profile.id == active.id,
                onSelect = { onSelect(profile) },
                onRename = { renaming = profile },
                onDuplicate = { duplicating = profile },
                onDelete = { deleting = profile },
            )
        }
        FractalButton(tr("New profile"), onClick = { creating = true }, icon = FractalIcons.FolderAdd)
    }

    if (creating) {
        NamePromptDialog(
            title = tr("New profile"),
            initial = tr("Profile {0}", profiles.size + 1),
            onConfirm = {
                creating = false
                onCreate(it, null)
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { profile ->
        NamePromptDialog(
            title = tr("Rename profile"),
            initial = profile.displayName(),
            onConfirm = {
                renaming = null
                onRename(profile, it)
            },
            onDismiss = { renaming = null },
        )
    }
    duplicating?.let { profile ->
        NamePromptDialog(
            title = tr("Duplicate profile"),
            initial = tr("{0} (copy)", profile.displayName()),
            onConfirm = {
                duplicating = null
                onCreate(it, profile)
            },
            onDismiss = { duplicating = null },
        )
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = tr("Delete profile"),
            message = tr("Delete the profile \"{0}\" with its layout and macros?", profile.displayName()),
            confirmText = tr("Delete"),
            onConfirm = {
                deleting = null
                onDelete(profile)
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: OverlayLayoutProfile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = FractalTheme.shapes.card
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onSelect)
            .fractalSurface(shape, FractalTheme.surface, if (isActive) FractalTheme.colors.selected else null)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.displayName(), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
            Text(
                tr("{0} macros", profile.macros.size),
                style = MaterialTheme.typography.bodySmall,
                color = FractalTheme.colors.textSecondary,
            )
        }
        if (isActive) StatusChip(tr("In use"), icon = FractalIcons.Check, highlight = true)
        Box {
            FractalIconButton(FractalIcons.More, tr("Profile options"), { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(tr("Rename")) }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text(tr("Duplicate")) }, onClick = { menuOpen = false; onDuplicate() })
                DropdownMenuItem(text = { Text(tr("Delete")) }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

@Composable
fun ProfilesDialog(
    settings: InputOverlaySettings,
    controllerType: OverlayControllerType,
    onSelect: (OverlayLayoutProfile) -> Unit,
    onCreate: (name: String, copyFrom: OverlayLayoutProfile?) -> Unit,
    onRename: (OverlayLayoutProfile, String) -> Unit,
    onDelete: (OverlayLayoutProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = tr("Layout profiles: {0}", controllerType.displayName()),
        onDismiss = onDismiss,
        actions = { DialogTextButton(tr("Close"), onDismiss) },
    ) {
        Text(
            tr("Each profile has its own positions, sizes, button options and macros."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        ProfileList(settings, controllerType, onSelect, onCreate, onRename, onDelete)
    }
}

/** Overlay wide options; sliders preview live through [onPreview] and save when released. */
@Composable
fun OverlayAppearanceOptions(
    settings: InputOverlaySettings,
    onUpdate: ((InputOverlaySettings) -> InputOverlaySettings) -> Unit,
    onPreview: (alpha: Int?, scale: Float?) -> Unit = { _, _ -> },
) {
    OverlayStyleOptions(settings, onUpdate)
    OverlaySliderRow(
        title = tr("Inputs opacity"),
        value = settings.alpha.toFloat(),
        valueRange = 0f..255f,
        valueText = { "${(it / 255f * 100).roundToInt()}%" },
        onValueChange = { onPreview(it.roundToInt(), null) },
        onValueChangeFinished = { value -> onUpdate { it.copy(alpha = value.roundToInt()) } },
    )
    OverlaySliderRow(
        title = tr("Inputs size"),
        value = settings.scale,
        valueRange = OVERLAY_SCALE_MIN..OVERLAY_SCALE_MAX,
        valueText = { "${(it * 100).roundToInt()}%" },
        onValueChange = { onPreview(null, it) },
        onValueChangeFinished = { value ->
            // 5% steps
            val scale = (value * 20).roundToInt() / 20f
            onUpdate { it.copy(scale = scale) }
        },
    )
    OverlayToggleRow(
        title = tr("Slide between buttons"),
        description = tr("Moving a finger from one button onto another releases the first and presses the second"),
        checked = settings.slideBetweenButtons,
        onCheckedChange = { on -> onUpdate { it.copy(slideBetweenButtons = on) } },
    )
    OverlayToggleRow(
        title = tr("Vibrate"),
        description = tr("Enable vibrate on touch"),
        checked = settings.isVibrateOnTouchEnabled,
        onCheckedChange = { on -> onUpdate { it.copy(isVibrateOnTouchEnabled = on) } },
    )
    OverlayToggleRow(
        title = tr("Snap to grid"),
        description = tr("Inputs moved or resized in edit mode line up on a grid"),
        checked = settings.snapToGrid,
        onCheckedChange = { on -> onUpdate { it.copy(snapToGrid = on) } },
    )
}

@Composable
fun OverlayAppearanceDialog(
    settings: InputOverlaySettings,
    onUpdate: ((InputOverlaySettings) -> InputOverlaySettings) -> Unit,
    onPreview: (alpha: Int?, scale: Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = tr("Overlay options"),
        onDismiss = onDismiss,
        actions = { DialogTextButton(tr("Close"), onDismiss) },
    ) {
        OverlayAppearanceOptions(settings, onUpdate, onPreview)
    }
}

/** Macros of a profile with edit/delete, for the settings screen. */
@Composable
fun MacroList(
    macros: List<OverlayMacro>,
    onEdit: (OverlayMacro) -> Unit,
    onDelete: (OverlayMacro) -> Unit,
) {
    var deleting by remember { mutableStateOf<OverlayMacro?>(null) }
    val shape = FractalTheme.shapes.card
    for (macro in macros) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { onEdit(macro) }
                .fractalSurface(shape, FractalTheme.surface)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(macro.label, style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
                Text(macro.summary(), style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary)
            }
            FractalIconButton(FractalIcons.Trash, tr("Delete"), { deleting = macro })
        }
    }
    deleting?.let { macro ->
        ConfirmDialog(
            title = tr("Delete macro"),
            message = tr("Delete the macro \"{0}\"?", macro.label),
            confirmText = tr("Delete"),
            onConfirm = {
                deleting = null
                onDelete(macro)
            },
            onDismiss = { deleting = null },
        )
    }
}
