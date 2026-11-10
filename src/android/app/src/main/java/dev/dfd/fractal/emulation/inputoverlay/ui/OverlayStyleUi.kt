package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.icons.FractalIcons
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.settings.HotkeyAction
import dev.dfd.fractal.common.settings.INPUT_SCALE_MAX
import dev.dfd.fractal.common.settings.INPUT_SCALE_MIN
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.OverlayButtonShape
import dev.dfd.fractal.common.settings.OverlayCustomColors
import dev.dfd.fractal.common.settings.OverlayInputStyle
import dev.dfd.fractal.common.settings.OverlayShortcut
import dev.dfd.fractal.common.settings.OverlayStyle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.emulation.input.displayName
import dev.dfd.fractal.emulation.input.shortLabel
import dev.dfd.fractal.emulation.inputoverlay.OverlayPalette
import kotlin.math.roundToInt

/** Colors the overlay draws with: the chosen style, or the current app theme. */
@Composable
fun overlayPaletteFor(settings: InputOverlaySettings): OverlayPalette {
    val colors = FractalTheme.colors
    return remember(settings.style, settings.customColor, colors) {
        OverlayPalette.forStyle(settings.style, settings.customColor) ?: OverlayPalette.fromTheme(
            accent = colors.accent.toArgb(),
            onAccent = colors.onAccent.toArgb(),
            background = colors.background.toArgb(),
            text = colors.text.toArgb(),
        )
    }
}

fun OverlayStyle.displayName(): String = when (this) {
    OverlayStyle.THEME -> tr("Follow app theme")
    OverlayStyle.CLASSIC -> tr("Classic")
    OverlayStyle.DARK -> tr("Dark")
    OverlayStyle.LIGHT -> tr("Light")
    OverlayStyle.CUSTOM -> tr("Custom color")
}

/** Style chips, color swatches for the custom style and a small preview. */
@Composable
fun OverlayStyleOptions(
    settings: InputOverlaySettings,
    onUpdate: ((InputOverlaySettings) -> InputOverlaySettings) -> Unit,
) {
    val shape = FractalTheme.shapes.card
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(shape, FractalTheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Style"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
        Text(
            tr("Colors of the on-screen controls. Follow app theme uses the accent of the theme you picked in Appearance."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (style in OverlayStyle.entries) {
                OverlayChip(style.displayName(), settings.style == style, { onUpdate { it.copy(style = style) } })
            }
        }
        if (settings.style == OverlayStyle.CUSTOM) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (color in OverlayCustomColors) {
                    val selected = settings.customColor == color
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(if (selected) 3.dp else 1.dp, if (selected) FractalTheme.colors.text else FractalTheme.colors.border, CircleShape)
                            .clickable(role = Role.RadioButton) { onUpdate { it.copy(customColor = color) } },
                    )
                }
            }
        }
        OverlayStylePreview(overlayPaletteFor(settings), settings.alpha)
    }
}

/** An idle button, a pressed one and a stick, drawn the way the overlay draws them. */
@Composable
fun OverlayStylePreview(palette: OverlayPalette, alpha: Int) {
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        val r = size.height * 0.36f
        val cy = size.height / 2f
        fun c(color: Int, a: Int) = Color(color).copy(alpha = (a.coerceIn(0, 255)) / 255f)
        val boosted = (alpha * palette.activeAlphaBoost).toInt()
        val baseAlpha = alpha.coerceAtLeast(40)
        val boostedAlpha = boosted.coerceAtLeast(70)
        // stick: base and knob
        val stickX = r * 1.4f
        drawCircle(c(palette.backgroundFill, baseAlpha), r * 1.25f, Offset(stickX, cy))
        drawButton(c(palette.inactiveFill, baseAlpha), c(palette.inactiveStroke, baseAlpha), Offset(stickX + r * 0.3f, cy - r * 0.2f), r * 0.8f)
        // idle and pressed buttons
        drawButton(c(palette.inactiveFill, baseAlpha), c(palette.inactiveStroke, baseAlpha), Offset(stickX + r * 3.4f, cy), r)
        drawButton(c(palette.activeFill, boostedAlpha), c(palette.activeStroke, boostedAlpha), Offset(stickX + r * 5.8f, cy), r)
        // shoulder button
        val left = stickX + r * 7.6f
        drawRoundRect(c(palette.inactiveFill, baseAlpha), Offset(left, cy - r * 0.6f), Size(r * 3f, r * 1.2f), CornerRadius(8f))
        drawRoundRect(c(palette.inactiveStroke, baseAlpha), Offset(left, cy - r * 0.6f), Size(r * 3f, r * 1.2f), CornerRadius(8f), style = Stroke(3f))
    }
}

private fun DrawScope.drawButton(fill: Color, stroke: Color, center: Offset, radius: Float) {
    drawCircle(fill, radius, center)
    drawCircle(stroke, radius, center, style = Stroke(3f))
}

/**
 * Opacity, size and shape of one input. Sliders call [onPreview] while dragged and
 * [onChange] when released.
 */
@Composable
fun InputStyleEditor(
    style: OverlayInputStyle,
    globalAlpha: Int,
    canChangeShape: Boolean,
    onPreview: (OverlayInputStyle) -> Unit,
    onChange: (OverlayInputStyle) -> Unit,
) {
    OverlayToggleRow(
        title = tr("Own opacity"),
        description = tr("Off follows the opacity of the whole overlay"),
        checked = style.opacity != null,
        onCheckedChange = { on -> onChange(style.copy(opacity = if (on) globalAlpha else null)) },
    )
    style.opacity?.let { opacity ->
        OverlaySliderRow(
            title = tr("Opacity"),
            value = opacity.toFloat(),
            valueRange = 0f..255f,
            valueText = { "${(it / 255f * 100).roundToInt()}%" },
            onValueChange = { onPreview(style.copy(opacity = it.roundToInt())) },
            onValueChangeFinished = { onChange(style.copy(opacity = it.roundToInt())) },
        )
    }
    OverlaySliderRow(
        title = tr("Size"),
        value = style.scale,
        valueRange = INPUT_SCALE_MIN..INPUT_SCALE_MAX,
        valueText = { "${(it * 100).roundToInt()}%" },
        onValueChange = { onPreview(style.copy(scale = it)) },
        onValueChangeFinished = { onChange(style.copy(scale = (it * 20).roundToInt() / 20f)) },
    )
    if (canChangeShape) {
        Text(tr("Shape"), style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OverlayChip(tr("Default"), style.shape == null, { onChange(style.copy(shape = null)) })
            OverlayChip(tr("Round"), style.shape == OverlayButtonShape.ROUND, { onChange(style.copy(shape = OverlayButtonShape.ROUND)) })
            OverlayChip(tr("Square"), style.shape == OverlayButtonShape.SQUARE, { onChange(style.copy(shape = OverlayButtonShape.SQUARE)) })
        }
    }
}

/** Shortcuts of a profile with edit/delete, for the settings screen. */
@Composable
fun ShortcutList(
    shortcuts: List<OverlayShortcut>,
    onEdit: (OverlayShortcut) -> Unit,
    onDelete: (OverlayShortcut) -> Unit,
) {
    var deleting by remember { mutableStateOf<OverlayShortcut?>(null) }
    val shape = FractalTheme.shapes.card
    for (shortcut in shortcuts) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { onEdit(shortcut) }
                .fractalSurface(shape, FractalTheme.surface)
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    shortcut.label.ifBlank { shortcut.action.shortLabel() },
                    style = MaterialTheme.typography.titleSmall,
                    color = FractalTheme.colors.text,
                )
                Text(shortcut.action.displayName(), style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary)
            }
            FractalIconButton(FractalIcons.Trash, tr("Delete"), { deleting = shortcut })
        }
    }
    deleting?.let { shortcut ->
        ConfirmDialog(
            title = tr("Delete shortcut"),
            message = tr("Delete the shortcut \"{0}\"?", shortcut.label.ifBlank { shortcut.action.shortLabel() }),
            confirmText = tr("Delete"),
            onConfirm = {
                deleting = null
                onDelete(shortcut)
            },
            onDismiss = { deleting = null },
        )
    }
}

private const val MAX_SHORTCUT_LABEL_LENGTH = 8

/** Creates ([initial] null) or edits an on-screen shortcut button. */
@Composable
fun ShortcutEditorDialog(
    initial: OverlayShortcut?,
    onSave: (OverlayShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    var action by remember(initial?.id) { mutableStateOf(initial?.action) }
    var label by remember(initial?.id) { mutableStateOf(initial?.label ?: "") }
    OverlayDialog(
        title = if (initial == null) tr("New shortcut") else tr("Edit shortcut"),
        onDismiss = onDismiss,
        actions = {
            DialogTextButton(tr("Cancel"), onDismiss)
            DialogTextButton(
                tr("Save"),
                {
                    val chosen = action ?: return@DialogTextButton
                    val base = initial ?: OverlayShortcut(action = chosen)
                    onSave(base.copy(action = chosen, label = label.trim()))
                },
                enabled = action != null,
            )
        },
    ) {
        Text(
            tr("A shortcut is an on-screen button that runs an emulator action, the same ones controller hotkeys can run."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (entry in HotkeyAction.entries) {
                OverlayChip(entry.displayName(), action == entry, { action = entry })
            }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(MAX_SHORTCUT_LABEL_LENGTH) },
            singleLine = true,
            label = { Text(tr("Label on the button")) },
            placeholder = { action?.let { Text(it.shortLabel()) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            tr("New shortcuts appear at the top of the screen; move them in edit mode."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
    }
}
