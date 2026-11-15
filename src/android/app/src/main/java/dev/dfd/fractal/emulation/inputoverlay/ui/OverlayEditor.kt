package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.ButtonBehavior
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.MacroKind
import dev.dfd.fractal.common.settings.NormalizedRect
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayInputStyle
import dev.dfd.fractal.common.settings.OverlayLayoutProfile
import dev.dfd.fractal.common.settings.OverlayMacro
import dev.dfd.fractal.common.settings.OverlayShortcut
import dev.dfd.fractal.common.settings.TURBO_MAX_HZ
import dev.dfd.fractal.common.settings.TURBO_MIN_HZ
import dev.dfd.fractal.common.settings.activeProfile
import dev.dfd.fractal.common.settings.createProfile
import dev.dfd.fractal.common.settings.deleteMacro
import dev.dfd.fractal.common.settings.deleteProfile
import dev.dfd.fractal.common.settings.deleteShortcut
import dev.dfd.fractal.common.settings.duplicateExtra
import dev.dfd.fractal.common.settings.isInputVisible
import dev.dfd.fractal.common.settings.overlayInputsFor
import dev.dfd.fractal.common.settings.renameProfile
import dev.dfd.fractal.common.settings.resetActiveLayout
import dev.dfd.fractal.common.settings.selectProfile
import dev.dfd.fractal.common.settings.setBehavior
import dev.dfd.fractal.common.settings.setInputRect
import dev.dfd.fractal.common.settings.setInputStyle
import dev.dfd.fractal.common.settings.setInputVisible
import dev.dfd.fractal.common.settings.toMacroInput
import dev.dfd.fractal.common.settings.upsertMacro
import dev.dfd.fractal.common.settings.upsertShortcut
import dev.dfd.fractal.common.settings.usesLegacyLayout
import dev.dfd.fractal.common.settings.withProfile
import dev.dfd.fractal.common.settings.withRect
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.emulation.input.shortLabel
import dev.dfd.fractal.emulation.inputoverlay.InputOverlayController
import dev.dfd.fractal.emulation.inputoverlay.OverlayTarget
import dev.dfd.fractal.emulation.inputoverlay.displayName
import dev.dfd.fractal.emulation.inputoverlay.macroInputsFor
import dev.dfd.fractal.emulation.inputoverlay.styleKey
import kotlin.math.roundToInt

/**
 * One editing session of the on-screen controls: a working copy of the overlay settings that the
 * overlay draws while editing, with undo and redo. Nothing is saved until [working] is handed back.
 */
@Stable
class OverlayEditorSession(val original: InputOverlaySettings) {
    var working by mutableStateOf(original)
        private set

    private val undoStack = mutableStateListOf<InputOverlaySettings>()
    private val redoStack = mutableStateListOf<InputOverlaySettings>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isDirty: Boolean get() = working != original

    fun update(transform: (InputOverlaySettings) -> InputOverlaySettings) {
        val next = transform(working)
        if (next == working) return
        undoStack.add(working)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
        working = next
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(working)
        working = previous
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(working)
        working = next
    }

    private companion object {
        const val MAX_UNDO = 100
    }
}

private sealed interface EditorDialog {
    data class Macro(val macro: OverlayMacro?, val kind: MacroKind = MacroKind.COMBO) : EditorDialog
    data class QuickButton(val turbo: Boolean) : EditorDialog
    data class Shortcut(val shortcut: OverlayShortcut?) : EditorDialog
    data object Profiles : EditorDialog
    data object Look : EditorDialog
    data object ConfirmReset : EditorDialog
    data object ConfirmDiscard : EditorDialog
}

/**
 * Editor of the on-screen controls, drawn above the overlay (which must be in edit mode and draw
 * [OverlayEditorSession.working]). Touches that miss the toolbar and the side panel reach the
 * overlay, where inputs are selected, dragged, pinched and resized by their corner handles.
 *
 * [onTestingChange] and [onPausedChange] add the "try the controls" and pause buttons (in game).
 */
@Composable
fun OverlayEditor(
    session: OverlayEditorSession,
    controller: InputOverlayController,
    onSave: (InputOverlaySettings) -> Unit,
    onCancel: () -> Unit,
    testing: Boolean = false,
    onTestingChange: ((Boolean) -> Unit)? = null,
    paused: Boolean = false,
    onPausedChange: ((Boolean) -> Unit)? = null,
) {
    val type = controller.controllerType
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var barAtTop by rememberSaveable { mutableStateOf(true) }
    val settings = session.working

    fun requestCancel() {
        if (session.isDirty) dialog = EditorDialog.ConfirmDiscard else onCancel()
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
        if (type == null) {
            EditorCard(Modifier.align(Alignment.Center)) {
                Text(
                    tr("The emulated controller is disabled. Pick a controller type in Controller mapping to edit its on-screen controls."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FractalTheme.colors.text,
                )
                ToolButton(FractalIcons.Close, tr("Close"), onClick = onCancel)
            }
            return@Box
        }
        val profile = settings.activeProfile(type)
        val selected = controller.selected

        Column(
            Modifier.align(if (barAtTop) Alignment.TopCenter else Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!barAtTop) EditorHints(profile, selected, testing, session, type)
            EditorToolbar(
                profileName = profile.displayName(),
                session = session,
                settings = settings,
                type = type,
                testing = testing,
                paused = paused,
                onTestingChange = onTestingChange,
                onPausedChange = onPausedChange,
                onProfiles = { dialog = EditorDialog.Profiles },
                onAdd = { dialog = it },
                onLook = { dialog = EditorDialog.Look },
                onReset = { dialog = EditorDialog.ConfirmReset },
                onMoveBar = { barAtTop = !barAtTop },
                onCancel = ::requestCancel,
                onSave = { onSave(session.working) },
            )
            if (barAtTop) EditorHints(profile, selected, testing, session, type)
        }

        if (selected != null && !testing) {
            InputPanel(
                target = selected,
                session = session,
                controller = controller,
                type = type,
                profile = profile,
                onEditMacro = { dialog = EditorDialog.Macro(it, it.kind) },
                onEditShortcut = { dialog = EditorDialog.Shortcut(it) },
                modifier = Modifier
                    .align(if (controller.selectedOnLeft) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(top = if (barAtTop) 76.dp else 0.dp, bottom = if (barAtTop) 0.dp else 76.dp)
                    .fillMaxHeight(),
            )
        }
    }

    if (type == null) return
    val dismiss = { dialog = null }
    when (val current = dialog) {
        null -> {}
        is EditorDialog.Macro -> MacroEditorDialog(
            initial = current.macro,
            initialKind = current.kind,
            controllerType = type,
            onSave = { macro ->
                session.update { it.upsertMacro(type, macro) }
                controller.select(OverlayTarget.Macro(macro.id))
                dismiss()
            },
            onDismiss = dismiss,
        )

        is EditorDialog.QuickButton -> QuickButtonDialog(
            turbo = current.turbo,
            controllerType = type,
            onSave = { macro ->
                session.update { it.upsertMacro(type, macro) }
                controller.select(OverlayTarget.Macro(macro.id))
                dismiss()
            },
            onDismiss = dismiss,
        )

        is EditorDialog.Shortcut -> ShortcutEditorDialog(
            initial = current.shortcut,
            onSave = { shortcut ->
                session.update { it.upsertShortcut(type, shortcut) }
                controller.select(OverlayTarget.Shortcut(shortcut.id))
                dismiss()
            },
            onDismiss = dismiss,
        )

        EditorDialog.Profiles -> ProfilesDialog(
            settings = settings,
            controllerType = type,
            onSelect = { profile ->
                controller.select(null)
                session.update { it.selectProfile(type, profile.id) }
            },
            onCreate = { name, copyFrom ->
                controller.select(null)
                session.update { it.createProfile(type, name, copyFrom) }
            },
            onRename = { profile, name -> session.update { it.renameProfile(profile.id, name) } },
            onDelete = { profile -> session.update { it.deleteProfile(type, profile.id) } },
            onDismiss = dismiss,
        )

        EditorDialog.Look -> OverlayAppearanceDialog(
            settings = settings,
            onUpdate = session::update,
            onPreview = controller::previewAppearance,
            onDismiss = dismiss,
        )

        EditorDialog.ConfirmReset -> ConfirmDialog(
            title = tr("Back to the automatic layout"),
            message = tr(
                "Put every input of the profile \"{0}\" back on the layout made for this screen? Button options, macros and shortcuts are kept.",
                settings.activeProfile(type).displayName(),
            ),
            confirmText = tr("Reset"),
            onConfirm = {
                session.update { it.resetActiveLayout(type) }
                dismiss()
            },
            onDismiss = dismiss,
        )

        EditorDialog.ConfirmDiscard -> ConfirmDialog(
            title = tr("Discard changes"),
            message = tr("Leave the editor without saving the changes?"),
            confirmText = tr("Discard"),
            onConfirm = {
                dismiss()
                onCancel()
            },
            onDismiss = dismiss,
        )
    }
}

/** Banner for old layouts and the hint shown while nothing is selected. */
@Composable
private fun EditorHints(
    profile: OverlayLayoutProfile,
    selected: OverlayTarget?,
    testing: Boolean,
    session: OverlayEditorSession,
    type: OverlayControllerType,
) {
    if (profile.usesLegacyLayout && !testing) {
        EditorCard {
            Text(
                tr("This profile keeps the layout of an older version. The new layout adapts to the screen and has bigger controls."),
                style = MaterialTheme.typography.bodySmall,
                color = FractalTheme.colors.text,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            ToolButton(FractalIcons.Refresh, tr("Use the new layout"), primary = true, wide = true, onClick = {
                session.update { it.resetActiveLayout(type) }
            })
        }
    }
    val hint = when {
        testing -> tr("Try the controls. Tap Test again to go back to editing.")
        selected == null -> tr("Tap a control to edit it. Drag to move it; pinch or drag a corner to resize it.")
        else -> null
    }
    if (hint != null) {
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(FractalTheme.shapes.chip)
                .background(FractalTheme.colors.background.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EditorCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, FractalTheme.colors.background.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun EditorToolbar(
    profileName: String,
    session: OverlayEditorSession,
    settings: InputOverlaySettings,
    type: OverlayControllerType,
    testing: Boolean,
    paused: Boolean,
    onTestingChange: ((Boolean) -> Unit)?,
    onPausedChange: ((Boolean) -> Unit)?,
    onProfiles: () -> Unit,
    onAdd: (EditorDialog) -> Unit,
    onLook: () -> Unit,
    onReset: () -> Unit,
    onMoveBar: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var addMenu by remember { mutableStateOf(false) }
    val hidden = overlayInputsFor(type).filter { !settings.isInputVisible(it) }
    Row(
        Modifier
            .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, FractalTheme.colors.background.copy(alpha = 0.94f))
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton(FractalIcons.Layers, profileName, onClick = onProfiles, wide = true)
        ToolDivider()
        ToolButton(OverlayEditorIcons.Undo, tr("Undo"), onClick = session::undo, enabled = session.canUndo)
        ToolButton(OverlayEditorIcons.Redo, tr("Redo"), onClick = session::redo, enabled = session.canRedo)
        ToolDivider()
        Box {
            ToolButton(OverlayEditorIcons.Add, tr("Add"), onClick = { addMenu = true })
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                fun item(text: String, dialog: EditorDialog) = Pair(text, dialog)
                val items = listOf(
                    item(tr("Combo macro: buttons pressed together"), EditorDialog.Macro(null, MacroKind.COMBO)),
                    item(tr("Sequence macro: buttons pressed one after another"), EditorDialog.Macro(null, MacroKind.SEQUENCE)),
                    item(tr("Turbo button"), EditorDialog.QuickButton(turbo = true)),
                    item(tr("Toggle button"), EditorDialog.QuickButton(turbo = false)),
                    item(tr("Shortcut: an emulator action"), EditorDialog.Shortcut(null)),
                )
                for ((text, dialog) in items) {
                    DropdownMenuItem(text = { Text(text) }, onClick = {
                        addMenu = false
                        onAdd(dialog)
                    })
                }
                if (hidden.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(tr("Show hidden inputs ({0})", hidden.size)) },
                        onClick = {
                            addMenu = false
                            session.update { s -> hidden.fold(s) { acc, input -> acc.setInputVisible(input, true) } }
                        },
                    )
                }
            }
        }
        ToolButton(FractalIcons.Grid, tr("Grid"), selected = settings.snapToGrid, onClick = {
            session.update { it.copy(snapToGrid = !it.snapToGrid) }
        })
        ToolButton(OverlayEditorIcons.Guides, tr("Guides"), selected = settings.snapToGuides, onClick = {
            session.update { it.copy(snapToGuides = !it.snapToGuides) }
        })
        ToolButton(FractalIcons.Palette, tr("Look"), onClick = onLook)
        ToolButton(FractalIcons.Refresh, tr("Reset"), onClick = onReset)
        if (onTestingChange != null || onPausedChange != null) ToolDivider()
        if (onTestingChange != null) {
            ToolButton(OverlayEditorIcons.Test, tr("Test"), selected = testing, onClick = { onTestingChange(!testing) })
        }
        if (onPausedChange != null) {
            ToolButton(
                if (paused) FractalIcons.Play else OverlayEditorIcons.Pause,
                if (paused) tr("Resume") else tr("Pause"),
                onClick = { onPausedChange(!paused) },
            )
        }
        ToolDivider()
        ToolButton(OverlayEditorIcons.Swap, tr("Move bar"), onClick = onMoveBar)
        ToolButton(FractalIcons.Close, tr("Cancel"), onClick = onCancel)
        ToolButton(FractalIcons.Check, tr("Save"), onClick = onSave, primary = true)
    }
}

@Composable
private fun ToolDivider() {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(30.dp)
            .background(FractalTheme.colors.border)
    )
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    primary: Boolean = false,
    wide: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = FractalTheme.colors
    val background = when {
        primary -> colors.accent
        selected -> colors.selected
        else -> Color.Transparent
    }
    val content = when {
        primary -> colors.onAccent
        selected -> colors.accent
        else -> colors.text
    }
    Column(
        Modifier
            .clip(FractalTheme.shapes.button)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .widthIn(min = 52.dp, max = if (wide) 132.dp else 80.dp)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Name shown for [target] in the panel. */
@Composable
private fun targetName(target: OverlayTarget, profile: OverlayLayoutProfile): String = when (target) {
    is OverlayTarget.Builtin -> target.config.displayName()
    is OverlayTarget.Macro -> profile.macros.firstOrNull { it.id == target.id }?.label ?: ""
    is OverlayTarget.Shortcut -> profile.shortcuts.firstOrNull { it.id == target.id }
        ?.let { it.label.ifBlank { it.action.shortLabel() } } ?: ""
}

/** Spot next to [rect] for a copy of it. */
private fun besideOf(rect: NormalizedRect): NormalizedRect {
    val shifted = rect.centerX + COPY_OFFSET
    return rect.copy(centerX = if (shifted > 0.95f) rect.centerX - COPY_OFFSET else shifted)
}

private const val COPY_OFFSET = 0.08f

/** Options of the selected input, docked on the side away from it. */
@Composable
private fun InputPanel(
    target: OverlayTarget,
    session: OverlayEditorSession,
    controller: InputOverlayController,
    type: OverlayControllerType,
    profile: OverlayLayoutProfile,
    onEditMacro: (OverlayMacro) -> Unit,
    onEditShortcut: (OverlayShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = session.working
    val portrait = controller.isPortrait
    val key = target.styleKey
    val style = profile.styles[key] ?: OverlayInputStyle()
    val macro = (target as? OverlayTarget.Macro)?.let { t -> profile.macros.firstOrNull { it.id == t.id } }
    val shortcut = (target as? OverlayTarget.Shortcut)?.let { t -> profile.shortcuts.firstOrNull { it.id == t.id } }
    val config = (target as? OverlayTarget.Builtin)?.config
    if (config == null && macro == null && shortcut == null) {
        // deleted or undone meanwhile
        return
    }
    val visible = config?.let { settings.isInputVisible(it) } ?: true
    val isButton = config?.isButton ?: true
    var confirmDelete by remember { mutableStateOf(false) }

    fun duplicate() {
        val rect = controller.currentLayoutOf(target)?.let(::besideOf)
        if (config != null) {
            val input = config.toMacroInput() ?: return
            val copy = OverlayMacro(
                label = input.displayName(),
                buttons = listOf(input),
                behavior = profile.behaviors[config] ?: ButtonBehavior(),
            )
            session.update { s ->
                val withMacro = s.upsertMacro(type, copy)
                val p = withMacro.activeProfile(type)
                var updated = if (rect != null) p.withRect(copy.id, rect, portrait) else p
                profile.styles[key]?.let { updated = updated.copy(styles = updated.styles + (copy.id to it)) }
                withMacro.withProfile(updated)
            }
            controller.select(OverlayTarget.Macro(copy.id))
            return
        }
        val (next, newId) = settings.duplicateExtra(type, key, rect, portrait)
        if (newId == null) return
        session.update { next }
        controller.select(if (macro != null) OverlayTarget.Macro(newId) else OverlayTarget.Shortcut(newId))
    }

    Column(
        modifier
            .width(PANEL_WIDTH)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier
                .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, FractalTheme.colors.background.copy(alpha = 0.95f))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        targetName(target, profile),
                        style = MaterialTheme.typography.titleMedium,
                        color = FractalTheme.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            macro != null -> macro.summary()
                            shortcut != null -> tr("Shortcut")
                            !visible -> tr("Hidden while playing")
                            else -> tr("Controller input")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FractalTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FractalIconButton(FractalIcons.Close, tr("Close"), { controller.select(null) })
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isButton) PanelAction(OverlayEditorIcons.Copy, tr("Duplicate"), ::duplicate)
                if (config != null) {
                    if (visible) {
                        PanelAction(OverlayEditorIcons.EyeOff, tr("Hide")) { session.update { it.setInputVisible(config, false) } }
                    } else {
                        PanelAction(OverlayEditorIcons.Eye, tr("Show")) { session.update { it.setInputVisible(config, true) } }
                    }
                } else {
                    PanelAction(FractalIcons.Tune, tr("Edit")) {
                        macro?.let(onEditMacro)
                        shortcut?.let(onEditShortcut)
                    }
                    PanelAction(FractalIcons.Trash, tr("Delete")) { confirmDelete = true }
                }
                PanelAction(OverlayEditorIcons.Recenter, tr("Default spot")) {
                    session.update { s ->
                        s.setInputRect(type, key, null, portrait).setInputStyle(type, key, style.copy(scale = 1f))
                    }
                }
            }

            BehaviorSection(config, macro, session, type)

            Text(tr("Look"), style = MaterialTheme.typography.labelLarge, color = FractalTheme.colors.accent)
            InputStyleEditor(
                style = style,
                globalAlpha = settings.alpha,
                canChangeShape = isButton,
                onPreview = { controller.previewInputStyle(target, it) },
                onChange = { new -> session.update { it.setInputStyle(type, key, new) } },
            )
        }
    }

    if (confirmDelete) {
        val name = targetName(target, profile)
        ConfirmDialog(
            title = tr("Delete button"),
            message = tr("Delete \"{0}\"?", name),
            confirmText = tr("Delete"),
            onConfirm = {
                confirmDelete = false
                controller.select(null)
                session.update { s ->
                    when {
                        macro != null -> s.deleteMacro(type, macro.id)
                        shortcut != null -> s.deleteShortcut(type, shortcut.id)
                        else -> s
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BehaviorSection(
    config: OverlayInputConfig?,
    macro: OverlayMacro?,
    session: OverlayEditorSession,
    type: OverlayControllerType,
) {
    val profile = session.working.activeProfile(type)
    when {
        config != null && config.isButton -> BehaviorEditor(
            behavior = profile.behaviors[config] ?: ButtonBehavior(),
            onChange = { behavior -> session.update { it.setBehavior(type, config, behavior) } },
        )

        macro != null -> BehaviorEditor(
            behavior = macro.behavior,
            isSequence = macro.kind == MacroKind.SEQUENCE,
            onChange = { behavior -> session.update { it.upsertMacro(type, macro.copy(behavior = behavior)) } },
        )
    }
}

@Composable
private fun PanelAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = FractalTheme.colors
    Row(
        Modifier
            .clip(FractalTheme.shapes.chip)
            .background(colors.surfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.text)
    }
}

private val PANEL_WIDTH = 300.dp

/** Creates a one button macro with turbo or toggle: pick the button it presses. */
@Composable
fun QuickButtonDialog(
    turbo: Boolean,
    controllerType: OverlayControllerType,
    onSave: (OverlayMacro) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = remember(controllerType) { macroInputsFor(controllerType) }
    var chosen by remember { mutableStateOf(available.firstOrNull()) }
    var rate by remember { mutableStateOf(ButtonBehavior().turboHz.toFloat()) }
    OverlayDialog(
        title = if (turbo) tr("New turbo button") else tr("New toggle button"),
        onDismiss = onDismiss,
        actions = {
            DialogTextButton(tr("Cancel"), onDismiss)
            DialogTextButton(tr("Add"), {
                val input = chosen ?: return@DialogTextButton
                val behavior = if (turbo) ButtonBehavior(turbo = true, turboHz = rate.roundToInt()) else ButtonBehavior(toggle = true)
                onSave(OverlayMacro(label = input.displayName(), buttons = listOf(input), behavior = behavior))
            }, enabled = chosen != null)
        },
    ) {
        Text(
            if (turbo) {
                tr("An extra button that presses and releases the button you pick over and over while you hold it.")
            } else {
                tr("An extra button that keeps the button you pick pressed until you tap it again.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (input in available) {
                OverlayChip(input.displayName(), chosen == input, { chosen = input })
            }
        }
        if (turbo) {
            OverlaySliderRow(
                title = tr("Turbo rate"),
                value = rate,
                valueRange = TURBO_MIN_HZ.toFloat()..TURBO_MAX_HZ.toFloat(),
                steps = TURBO_MAX_HZ - TURBO_MIN_HZ - 1,
                valueText = { tr("{0} per second", it.roundToInt()) },
                onValueChangeFinished = { rate = it },
            )
        }
        Spacer(Modifier.fillMaxWidth().height(2.dp))
        Text(
            tr("New buttons appear in a free spot; drag them where you want."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
    }
}
